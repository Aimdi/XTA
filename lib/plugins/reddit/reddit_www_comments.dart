/// Reading a comment thread from www.reddit.com: the piece of a post's page
/// that loads its comments (`/svc/shreddit/comments/…`), which a full post page
/// keeps behind its browser check.
///
/// Each comment is a `<shreddit-comment>` holding its replies as nested
/// `<shreddit-comment>`s, with the body in its `slot="comment"` block and
/// everything else — author, score, timestamp, depth, permalink — in its
/// attributes.
library;

import 'package:html/dom.dart';
import 'package:xta/plugins/reddit/reddit_comments.dart';
import 'package:xta/plugins/reddit/reddit_www.dart';

/// A thread as www.reddit.com served it: what the page was, its comments,
/// whether Reddit held back top-level comments the page does not carry, and
/// where the next of them are served.
typedef RedditWwwThread = ({
  RedditWwwPageKind kind,
  List<RedditComment> comments,
  bool moreAtRoot,
  RedditCommentsCursor? next,
});

/// The comments of a thread page.
///
/// A comment's own page (`…/comment/<id>/`) answers with the whole thread,
/// the comment's ancestors first; only that comment and its replies are what
/// the page was opened for, so only they are kept.
RedditWwwThread readWwwThread(String body) {
  final kind = redditWwwPageKind(body);
  final tree = kind == RedditWwwPageKind.read ? parseWwwBody(body).querySelector('shreddit-comment-tree') : null;
  if (tree == null) {
    return (kind: kind, comments: const [], moreAtRoot: false, next: null);
  }

  final focus = tree.attributes['thingid'];
  final roots = focus == null
      ? _childComments(tree, null)
      : [?tree.querySelector('shreddit-comment[thingid="$focus"]')];
  final more = focus == null ? tree.querySelector(_topLevelMore) : null;
  return (
    kind: kind,
    comments: [for (final root in roots) ?_commentFrom(root)],
    moreAtRoot: more != null,
    next: _cursorOf(more),
  );
}

/// The next comments at the top of a thread, as the piece that serves them
/// answers: those comments, and where the ones after them are.
RedditWwwThread readWwwMoreComments(String body) {
  final kind = redditWwwPageKind(body);
  if (kind != RedditWwwPageKind.read) {
    return (kind: kind, comments: const [], moreAtRoot: false, next: null);
  }

  final page = parseWwwBody(body);
  final more = page.querySelector(_topLevelMore);
  return (
    kind: kind,
    comments: [
      for (final element in page.querySelectorAll('shreddit-comment'))
        if (_ownerOf(element) == null) ?_commentFrom(element),
    ],
    moreAtRoot: more != null,
    next: _cursorOf(more),
  );
}

const _topLevelMore = '#top-level-more-comments-partial';

/// Where a "more comments" partial loads from, and the cursor it sends.
RedditCommentsCursor? _cursorOf(Element? partial) {
  final path = partial?.attributes['src'];
  final cursor = partial?.querySelector('input[name="cursor"]')?.attributes['value'];
  if (path == null || !path.startsWith('/svc/shreddit/') || cursor == null || cursor.isEmpty) {
    return null;
  }
  return (path: path, cursor: cursor);
}

RedditComment? _commentFrom(Element element) {
  final a = element.attributes;
  final id = wwwBareId(a['thingid'], 't1_');
  if (id == null) {
    return null;
  }

  final content = redditCommentContent(_ownBody(element));
  final replies = [for (final child in _childComments(element, element)) ?_commentFrom(child), ?_heldBack(element)];
  final body = wwwTidy(content.body);
  if (body.isEmpty && content.mediaUrls.isEmpty && replies.isEmpty) {
    return null;
  }

  return RedditComment(
    id: id,
    author: a['author'],
    // A comment whose words are gone keeps its row for the replies under it.
    body: body.isEmpty && content.mediaUrls.isEmpty ? '[deleted]' : body,
    score: wwwInt(a['score']),
    createdAt: wwwDate(a['created']),
    isSubmitter: a.containsKey('is-op'),
    mediaUrls: content.mediaUrls,
    replies: replies,
    permalink: a['permalink'],
  );
}

/// The comments whose nearest enclosing comment is [owner] — null for the
/// top level of [root].
List<Element> _childComments(Element root, Element? owner) => [
  for (final element in root.querySelectorAll('shreddit-comment'))
    if (_ownerOf(element) == owner) element,
];

/// The block holding [comment]'s own words, not a reply's.
Element? _ownBody(Element comment) {
  for (final block in comment.querySelectorAll('[slot="comment"]')) {
    if (_ownerOf(block) == comment) {
      return block;
    }
  }
  return null;
}

/// Replies Reddit held back under [comment], as the stub the thread shows for
/// them; its page is where they are.
RedditComment? _heldBack(Element comment) {
  for (final partial in comment.querySelectorAll('faceplate-partial[slot="children"]')) {
    if (_ownerOf(partial) == comment && (partial.attributes['src'] ?? '').contains('more-comments')) {
      final count = wwwInt(partial.querySelector('faceplate-number')?.attributes['number']);
      final permalink = comment.attributes['permalink'];
      return permalink == null
          ? null
          : RedditComment(id: 'more-$permalink', body: '', permalink: permalink, moreCount: count ?? -1);
    }
  }
  return null;
}

/// The nearest `<shreddit-comment>` around [element], or null at the top.
Element? _ownerOf(Element element) {
  for (var parent = element.parent; parent != null; parent = parent.parent) {
    if (parent.localName == 'shreddit-comment') {
      return parent;
    }
  }
  return null;
}
