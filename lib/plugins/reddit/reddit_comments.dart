/// Reading a comment thread out of old.reddit.com's HTML.
///
/// Same reasoning as the listing scraper: the JSON is gone, the old site still
/// renders threads to anyone, and the `data-*` attributes are the stable part.
/// Nesting comes from the markup's own shape — each comment holds its replies
/// in a `.child` block — so the tree is read recursively rather than guessed at
/// from indentation.
library;

import 'package:html/dom.dart';
import 'package:html/parser.dart' as html;
import 'package:xta/plugins/reddit/reddit_media_urls.dart';

/// One comment, with whatever replies hang off it.
class RedditComment {
  final String id;
  final String? author;
  final String body;

  /// Null when Reddit is hiding the score, which it does for new comments.
  final int? score;

  /// Reddit's own wording — "4 hours ago" — rather than a parsed date. The page
  /// gives a machine-readable timestamp too, and that is preferred; this is the
  /// fallback when it is missing.
  final DateTime? createdAt;

  /// Marked by Reddit as the submitter of the post.
  final bool isSubmitter;

  /// Pictures and GIFs linked from the body, in the order they appear.
  ///
  /// Reddit has no comment-image markup on the old site — an image comment is a
  /// link to `i.redd.it` — so they are picked out of the body rather than
  /// arriving as their own field.
  final List<String> mediaUrls;

  final List<RedditComment> replies;

  /// This comment's own page, for opening its subtree when replies were held
  /// back.
  final String? permalink;

  /// Set on a "load more comments" / "continue this thread" row: how many
  /// replies Reddit held back (null when the page did not say), with
  /// [permalink] pointing at the page that carries them. Everything else on a
  /// stub is empty.
  final int? moreCount;

  const RedditComment({
    required this.id,
    required this.body,
    this.author,
    this.score,
    this.createdAt,
    this.isSubmitter = false,
    this.mediaUrls = const [],
    this.replies = const [],
    this.permalink,
    this.moreCount,
  });

  bool get isStub =>
      moreCount != null ||
      (body.isEmpty && permalink != null && replies.isEmpty);

  /// Reddit kept the row but took the words: the author deleted it, or a
  /// moderator removed it. The replies underneath are usually still there,
  /// which is why the row survives at all.
  ///
  /// Reddit says so in the body rather than in a class the old site is
  /// consistent about, so the body is what is read. A comment whose *author*
  /// is gone but whose text is intact is not this — it still says something.
  bool get isRemoved =>
      !isStub && redditRemovedBodies.contains(body.trim().toLowerCase());

  /// Nobody left to open a profile for: a deleted account, or a row Reddit
  /// rendered without one.
  bool get hasAuthor =>
      author != null && author != '[deleted]' && author!.isNotEmpty;

  /// This comment and everything under it, which is what a flat list needs.
  int get totalCount =>
      1 + replies.fold<int>(0, (sum, reply) => sum + reply.totalCount);
}

/// What Reddit puts in a comment's place once its text is gone.
const redditRemovedBodies = {
  '[deleted]',
  '[removed]',
  '[unavailable]',
  '[ removed by reddit ]',
};

/// A comment flattened for display, keeping how deep it sat.
typedef FlatComment = ({RedditComment comment, int depth});

/// Walks a tree into the list a `ListView` can build, depth carried alongside
/// so each row can be indented without nesting widgets inside widgets.
List<FlatComment> flattenComments(
  List<RedditComment> comments, {
  int depth = 0,
}) {
  final flat = <FlatComment>[];
  for (final comment in comments) {
    flat.add((comment: comment, depth: depth));
    flat.addAll(flattenComments(comment.replies, depth: depth + 1));
  }
  return flat;
}

int? _score(Element entry) {
  // "42 points", "1 point", or "" when Reddit is hiding it.
  final text =
      entry.querySelector('.score.unvoted')?.text ??
      entry.querySelector('.score')?.text;
  if (text == null) {
    return null;
  }
  final digits = RegExp(r'-?\d+').firstMatch(text.replaceAll(',', ''));
  return digits == null ? null : int.tryParse(digits.group(0)!);
}

DateTime? _createdAt(Element entry) {
  final raw = entry.querySelector('time')?.attributes['datetime'];
  return raw == null ? null : DateTime.tryParse(raw)?.toLocal();
}

RedditComment? _commentFrom(Element thing) {
  final fullname = thing.attributes['data-fullname'];
  // `more` rows ("load 40 more comments") and deep-thread continuations are
  // rendered as stubs so a long argument no longer ends mid-air with no sign
  // anything is missing.
  if (fullname == null || !fullname.startsWith('t1_')) {
    return _stubFrom(thing);
  }

  // Only this comment's own entry, never a reply's: `querySelector` searches
  // the whole subtree, so the child block has to be excluded explicitly.
  final entry = thing.children.firstWhere(
    (e) => e.classes.contains('entry'),
    orElse: () => Element.tag('div'),
  );

  final markdown = entry.querySelector('.usertext-body .md');
  final media = _mediaIn(markdown);
  final body = _bodyOf(markdown, media);

  // A comment that is nothing but a picture is still a comment; only one with
  // neither text nor media is a row worth skipping.
  if (body.isEmpty && media.urls.isEmpty) {
    return null;
  }

  return RedditComment(
    id: fullname.substring(3),
    author:
        thing.attributes['data-author'] ??
        entry.querySelector('a.author')?.text.trim(),
    body: body,
    score: _score(entry),
    createdAt: _createdAt(entry),
    isSubmitter: entry.querySelector('.author.submitter') != null,
    mediaUrls: media.urls,
    replies: _repliesOf(thing),
    permalink: thing.attributes['data-permalink'],
  );
}

/// A "load more comments (N replies)" or "continue this thread" control, kept
/// as a row rather than silently dropped.
///
/// old.reddit's morechildren anchor goes nowhere without its JavaScript, so
/// the stub points at [parentPermalink] — the held-back replies are on their
/// parent's own page. A continuation carries its target in its href.
RedditComment? _stubFrom(Element thing, {String? parentPermalink}) {
  if (thing.classes.contains('morechildren')) {
    final text = thing.text;
    final counted = RegExp(
      r'\((\d[\d,]*)',
    ).firstMatch(text.replaceAll(',', ''));
    if (parentPermalink == null) {
      return null;
    }
    return RedditComment(
      id: 'more-$parentPermalink',
      body: '',
      permalink: parentPermalink,
      moreCount: counted == null ? -1 : int.tryParse(counted.group(1)!) ?? -1,
    );
  }
  if (thing.classes.contains('morerecursion')) {
    final href = thing.querySelector('a')?.attributes['href'];
    if (href == null || href.isEmpty) {
      return null;
    }
    return RedditComment(
      id: 'continue-$href',
      body: '',
      permalink: href,
      moreCount: -1,
    );
  }
  return null;
}

/// The pictures a comment body carries, and the text that only announced them.
///
/// [tokens] are Reddit's own media markdown, which the old site prints raw.
/// They are never words anybody meant to write, so they come out of the text
/// whatever else it says.
typedef _CommentMedia = ({List<String> urls, List<String> tokens});

/// Every picture in a comment body, deduplicated, in order.
///
/// Three places carry one: an anchor (a link someone pasted), an `img` the old
/// site inlined itself, and Reddit's `![gif](giphy|…)` token, which the old
/// site renders as literal text and is otherwise the picture's only trace.
_CommentMedia _mediaIn(Element? markdown) {
  if (markdown == null) {
    return (urls: const [], tokens: const []);
  }

  final urls = <String>[];
  void add(String? url) {
    if (url != null && !urls.contains(url)) {
      urls.add(url);
    }
  }

  for (final anchor in markdown.querySelectorAll('a[href]')) {
    add(redditEmbeddableImage(anchor.attributes['href']));
  }
  for (final image in markdown.querySelectorAll('img[src]')) {
    add(redditEmbeddableImage(_absolute(image.attributes['src'])));
  }

  final tokens = <String>[];
  for (final match in redditMediaToken.allMatches(markdown.text)) {
    tokens.add(match.group(0)!);
    add(redditTokenImage(match));
  }

  return (urls: urls, tokens: tokens);
}

/// Reddit writes protocol-relative sources; the image loader needs a scheme.
String? _absolute(String? src) =>
    src != null && src.startsWith('//') ? 'https:$src' : src;

/// The comment's words, with anything that was only an announcement removed.
///
/// A media token is always noise. A bare URL is not: an image comment reads
/// `https://i.redd.it/x.gif` as its text and printing that above the picture is
/// pointless, but prose that merely happens to contain a link keeps every word.
/// Non-image `<a>` tags are rewritten as `[label](url)` so the thread can
/// paint them as tappable short names instead of dumping the href.
String _bodyOf(Element? markdown, _CommentMedia media) {
  var text = _htmlAsMarkdown(markdown);
  for (final token in media.tokens) {
    text = text.replaceAll(token, '');
  }
  text = text.trim();

  if (text.isEmpty || media.urls.isEmpty) {
    return stripRedditMediaPlaceholderTokens(text);
  }

  var withoutLinks = stripRedditMediaLinksFromText(text);
  for (final url in media.urls) {
    withoutLinks = withoutLinks.replaceAll(url, '');
  }
  withoutLinks = stripRedditMediaPlaceholderTokens(withoutLinks);

  return withoutLinks;
}

/// Visible words of a comment's `.md` block, with ordinary links kept as
/// markdown so a later pass can shorten and open them.
String _htmlAsMarkdown(Element? markdown) {
  if (markdown == null) {
    return '';
  }
  final out = StringBuffer();
  _writeHtml(out, markdown);
  return out.toString();
}

void _writeHtml(StringBuffer out, Node node) {
  if (node is Text) {
    out.write(node.text);
    return;
  }
  if (node is! Element) {
    return;
  }
  if (node.localName == 'a') {
    final href = node.attributes['href'] ?? '';
    final label = node.text.trim();
    if (redditEmbeddableImage(href) != null) {
      out.write(label);
      return;
    }
    if (href.startsWith('http')) {
      out.write(label.isEmpty || label == href ? href : '[$label]($href)');
      return;
    }
  }
  if (node.localName == 'br') {
    out.write('\n');
    return;
  }
  if (node.localName == 'p' && out.isNotEmpty) {
    out.write('\n');
  }
  for (final child in node.nodes) {
    _writeHtml(out, child);
  }
}

/// The comments nested directly inside [thing].
List<RedditComment> _repliesOf(Element thing) {
  final child = thing.children
      .where((e) => e.classes.contains('child'))
      .firstOrNull;
  if (child == null) {
    return const [];
  }

  final listing = child.children
      .where((e) => e.classes.contains('sitetable'))
      .firstOrNull;
  return listing == null
      ? const []
      : _commentsIn(
          listing,
          parentPermalink: thing.attributes['data-permalink'],
        );
}

/// Direct comment children of a listing block, in order.
List<RedditComment> _commentsIn(Element listing, {String? parentPermalink}) {
  final comments = <RedditComment>[];
  for (final thing in listing.children.where(
    (e) => e.classes.contains('thing'),
  )) {
    final comment =
        _commentFrom(thing) ??
        _stubFrom(thing, parentPermalink: parentPermalink);
    if (comment != null) {
      comments.add(comment);
    }
  }
  return comments;
}

/// The comment tree of a post page.
///
/// An unreadable page yields no comments rather than throwing — the post itself
/// is still worth showing.
List<RedditComment> parseComments(String body, {String? postPermalink}) {
  final document = html.parse(body);

  final area =
      document.querySelector('.commentarea .sitetable') ??
      document.querySelector('.nestedlisting');
  // The post's own permalink stands in as the root's parent, so a bottom-of-
  // page "load more comments (500)" keeps its row instead of vanishing.
  return area == null
      ? const []
      : _commentsIn(area, parentPermalink: postPermalink);
}

/// What the post itself points at, read off its own page.
///
/// A post that arrived through search carries only its title — old.reddit's
/// search results name no link and no media — so the thread page is where its
/// picture has to come from. The `.thing` row carries the outbound link in
/// `data-url`, and an expanded gallery or preview leaves its files as `img`
/// tags under the post's expando.
({String? url, List<String> images}) parsePostMedia(String body) {
  final document = html.parse(body);
  final thing = document.querySelector('#siteTable .thing');

  final url = thing?.attributes['data-url'];
  final absolute = url != null && url.startsWith('http') ? url : null;

  final collected = <String>[];
  for (final img in document.querySelectorAll(
    '#siteTable .expando img, #siteTable .media-gallery img',
  )) {
    final src = (img.attributes['src'] ?? img.attributes['data-lazy-src'])
        ?.replaceAll('&amp;', '&');
    final host = src == null ? null : Uri.tryParse(src)?.host;
    if (src != null && (host == 'preview.redd.it' || host == 'i.redd.it')) {
      collected.add(src);
    }
  }

  return (url: absolute, images: collapseRedditImageUrls(collected));
}

/// The post's own text on a comment page, for a self post whose body the
/// listing did not carry.
String? parseSelfText(String body) {
  final document = html.parse(body);
  final text =
      document
          .querySelector('#siteTable .expando .usertext-body .md')
          ?.text
          .trim() ??
      document.querySelector('#siteTable .usertext-body .md')?.text.trim();

  return text == null || text.isEmpty ? null : text;
}

/// One row of a thread: the comment, and how many replies its fold is holding.
typedef VisibleComment = ({FlatComment entry, int hidden});

/// The rows a thread screen should show, honouring [collapsed] subtrees.
///
/// A collapsed comment stays as its own row, carrying how many replies it is
/// hiding; everything under it is skipped. Pure, so the fold behaviour can be
/// tested without a widget tree.
List<VisibleComment> visibleComments(
  List<FlatComment> all,
  Set<String> collapsed,
) {
  final out = <VisibleComment>[];
  var i = 0;
  while (i < all.length) {
    final entry = all[i];
    if (collapsed.contains(entry.comment.id)) {
      var j = i + 1;
      var hidden = 0;
      while (j < all.length && all[j].depth > entry.depth) {
        j++;
        hidden++;
      }
      out.add((entry: entry, hidden: hidden));
      i = j;
    } else {
      out.add((entry: entry, hidden: 0));
      i++;
    }
  }
  return out;
}

/// The row a "next top-level comment" jump should land on, or null when [from]
/// is already in the last one.
///
/// A long thread is one argument after another, and scrolling past a hundred
/// replies to reach the next one is the reason people give up on threads.
int? nextTopLevelRow(List<VisibleComment> rows, int from) {
  for (var i = from + 1; i < rows.length; i++) {
    if (rows[i].entry.depth == 0) {
      return i;
    }
  }
  return null;
}

/// The row a "previous top-level comment" jump should land on.
///
/// From inside a subtree that is the top of the *current* argument, which is
/// what makes the two buttons a pair rather than a way to lose your place.
int? previousTopLevelRow(List<VisibleComment> rows, int from) {
  final start = from > rows.length ? rows.length : from;
  for (var i = start - 1; i >= 0; i--) {
    if (rows[i].entry.depth == 0) {
      return i;
    }
  }
  return null;
}

/// Every top-level comment that has replies to fold, for a collapse-everything
/// toggle: folding them turns a thousand-comment page into a list of the
/// arguments it is actually made of.
Set<String> foldableTopLevelIds(List<FlatComment> all) => {
  for (final e in all)
    if (e.depth == 0 && e.comment.replies.isNotEmpty) e.comment.id,
};
