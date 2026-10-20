/// Reading www.reddit.com — the one part of Reddit still served to a reader
/// without an account.
///
/// Through 2026 Reddit closed every other door: the `.json` endpoints answer
/// 403 (May), old.reddit.com sends a logged-out visitor to its login page
/// (July), and the RSS feeds allow one request a minute until they end on
/// November 13. What it still serves is its own website — not the pages
/// themselves, which now open with a script that checks for a browser, but the
/// pieces of them the site loads as it scrolls (`/svc/shreddit/…`), which carry
/// no such check.
///
/// A post arrives as a `<shreddit-post>` whose attributes hold its id, title,
/// author, score, comment count, timestamp, link and kind, so — as with
/// old.reddit — the parser reads attributes rather than layout, and skips what
/// it cannot read instead of guessing.
library;

import 'dart:convert';

import 'package:html/dom.dart';
import 'package:html/parser.dart' as html;
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_comments.dart';
import 'package:xta/utils/json.dart';

/// What www.reddit.com answered with.
enum RedditWwwPageKind {
  /// Something to read. It may hold nothing: an empty community, a missing or
  /// banned one, and the end of a feed all look alike here.
  read,

  /// The script Reddit puts in front of a full page to check for a browser.
  browserCheck,

  /// "You've been blocked by network security."
  blocked,

  /// Not a page of Reddit's site this parser knows: an endpoint that moved.
  unreadable,
}

/// One page of a feed — a community, a profile: what it was, its posts, and
/// the fullname the next page starts after.
typedef RedditWwwListing = ({RedditWwwPageKind kind, List<RedditPost> posts, String? after});

/// What [body] is, before anything is read out of it.
///
/// A page with posts or comments on it is read whatever else it says: a
/// title that happens to mention a block is not one.
RedditWwwPageKind redditWwwPageKind(String body) {
  if (_content.hasMatch(body)) {
    return RedditWwwPageKind.read;
  }
  if (body.toLowerCase().contains('blocked by network security')) {
    return RedditWwwPageKind.blocked;
  }
  if (body.contains('name="solution"')) {
    return RedditWwwPageKind.browserCheck;
  }
  return body.contains('<shreddit-app') && !body.contains('Page not found')
      ? RedditWwwPageKind.read
      : RedditWwwPageKind.unreadable;
}

final _content = RegExp(r'<shreddit-(?:post|comment)[\s>]|view-events="search/view/');

/// The posts of a feed page and where the next one starts.
RedditWwwListing readWwwListing(String body) {
  final kind = redditWwwPageKind(body);
  if (kind != RedditWwwPageKind.read) {
    return (kind: kind, posts: const [], after: null);
  }

  final document = parseWwwBody(body);
  final posts = [
    for (final element in document.querySelectorAll('shreddit-post'))
      if (_isOwnPost(element)) ?wwwPostFrom(element),
  ];
  return (kind: kind, posts: posts, after: _afterOf(document, posts));
}

/// A post quoted inside another (a crosspost's original) is not a post of
/// the feed.
bool _isOwnPost(Element element) {
  for (var parent = element.parent; parent != null; parent = parent.parent) {
    if (parent.localName == 'shreddit-post') {
      return false;
    }
  }
  return true;
}

/// One post, from its `<shreddit-post>`. Null when the element lacks what
/// every post has.
RedditPost? wwwPostFrom(Element post) {
  final a = post.attributes;
  final id = wwwBareId(a['id'], 't3_');
  final permalink = a['permalink'];
  final title = a['post-title']?.trim();
  if (id == null || permalink == null || title == null || title.isEmpty) {
    return null;
  }

  final href = wwwAbsoluteUrl(a['content-href']);
  final self = _isSelf(a['domain'], href, permalink);
  final telemetry = _telemetryOf(post);
  return RedditPost(
    id: id,
    title: title,
    subreddit: a['subreddit-name'] ?? a['subreddit-prefixed-name']?.replaceFirst(RegExp('^r/'), '') ?? '',
    permalink: permalink,
    author: a['author'],
    score: wwwInt(a['score']) ?? 0,
    commentCount: wwwInt(a['comment-count']) ?? 0,
    createdAt: wwwDate(a['created-timestamp']),
    url: self ? null : _linkOf(post, href),
    isSelf: self,
    selfText: wwwText(post.querySelector('shreddit-post-text-body .md')),
    over18:
        a.containsKey('nsfw') ||
        telemetry['post']['nsfw'].boolean == true ||
        telemetry['subreddit']['nsfw'].boolean == true,
    spoiler: a.containsKey('spoiler'),
    stickied: telemetry['post']['pinned'].boolean ?? false,
    thumbnail: post.querySelector('[data-testid="post-thumbnail"] img')?.attributes['src'],
    flair: _flairOf(post),
    domain: a['domain'],
    galleryImages: _galleryOf(post),
    previewImage: _previewOf(post),
    videoDashUrl: post.querySelector('shreddit-player')?.attributes['src'],
  );
}

/// What a post's media tells Reddit's analytics about it — whether it is NSFW
/// or pinned, and its community's NSFW mark — which a post with pictures
/// carries and its attributes may not.
Json _telemetryOf(Element post) {
  final raw = post.querySelector('[telemetry]')?.attributes['telemetry'];
  if (raw == null) {
    return const Json(null);
  }
  try {
    return Json(jsonDecode(raw));
  } on FormatException {
    return const Json(null);
  }
}

/// A self post's domain reads `self.<subreddit>`, and its link is its own page.
bool _isSelf(String? domain, String? href, String permalink) =>
    (domain?.startsWith('self.') ?? false) || href == null || Uri.tryParse(href)?.path == permalink;

/// What the post points at. A crosspost points at the original's page; when
/// that original is one picture, the picture is the better link.
String? _linkOf(Element post, String? href) {
  final uri = href == null ? null : Uri.tryParse(href);
  final isPostPage = uri != null && uri.host.endsWith('reddit.com') && uri.path.contains('/comments/');
  if (!isPostPage || post.querySelector('gallery-carousel') != null) {
    return href;
  }
  return _originalPicture(post) ?? href;
}

/// The full-size file the lightbox opens, when the post has a single picture.
String? _originalPicture(Element post) {
  for (final img in post.querySelectorAll('shreddit-media-lightbox-listener img')) {
    final src = img.attributes['src'];
    if (src != null && Uri.tryParse(src)?.host == 'i.redd.it') {
      return src;
    }
  }
  return null;
}

String? _flairOf(Element post) {
  final text = post.querySelector('shreddit-post-flair .flair-content')?.text.trim();
  return text == null || text.isEmpty ? null : text;
}

/// A gallery's pictures in the author's order: Reddit's 640px copy of each,
/// which is what its own feed shows, rather than files uploaded at 4000px.
List<String> _galleryOf(Element post) {
  final pages = post.querySelectorAll('gallery-carousel li[slot^="page-"]');
  return [
    for (final page in pages)
      if (page.querySelector('img') case final img?) ?(img.attributes['src'] ?? img.attributes['data-lazy-src']),
  ];
}

/// Reddit's own preview of what the post shows: a video's poster frame, or the
/// scaled copy of its picture.
String? _previewOf(Element post) =>
    post.querySelector('shreddit-player')?.attributes['poster'] ??
    post.querySelector('img[data-post-media-primary]')?.attributes['src'] ??
    post.querySelector('img[slot="poster"]')?.attributes['src'];

/// The fullname the next page starts after.
///
/// A community names it in the URL of the piece that loads the next page; a
/// profile puts it on its posts instead.
String? _afterOf(Document document, List<RedditPost> posts) {
  final next = document.querySelector('faceplate-partial[slot="load-after"]')?.attributes['src'];
  final after = next == null ? null : Uri.tryParse(next)?.queryParameters['after'];
  if (after != null) {
    return wwwCursorName(after);
  }
  final cursors = document.querySelectorAll('shreddit-post[more-posts-cursor]');
  return cursors.isEmpty || posts.isEmpty ? null : wwwCursorName(cursors.last.attributes['more-posts-cursor']);
}

/// The fullname in one of Reddit's paging cursors: `dDNfMXg3OXFvdA==` is
/// `t3_1x79qot`, written in base64 with or without its padding.
String? wwwCursorName(String? cursor) {
  if (cursor == null || cursor.isEmpty) {
    return null;
  }
  try {
    final name = utf8.decode(base64.decode(cursor.padRight((cursor.length + 3) ~/ 4 * 4, '=')));
    return RegExp(r'^t[0-9]_[a-z0-9]+$').hasMatch(name) ? name : null;
  } on FormatException {
    return null;
  }
}

/// [after] as the cursor Reddit's paging URLs carry.
String wwwCursorParam(String after) => base64.encode(utf8.encode(after));

/// [body] parsed, without what a reader never needs.
Document parseWwwBody(String body) => html.parse(wwwStrip(body));

/// [body] without its head and its inline styles, scripts and icons.
///
/// A piece of the site arrives as a whole document of which those are most of
/// the weight; they are cut before a parse rather than walked by it. Cutting
/// twice cuts nothing more.
String wwwStrip(String body) {
  final start = body.indexOf('<body');
  return (start < 0 ? body : body.substring(start)).replaceAll(_noise, '');
}

final _noise = RegExp(
  r'<script\b[\s\S]*?</script>|<style\b[\s\S]*?</style>|<svg\b[\s\S]*?</svg>|<template\b[\s\S]*?</template>',
);

/// A post's or comment's words: its rendered markdown as text, links kept as
/// `[label](url)`, without the indentation the markup carries between
/// paragraphs.
String? wwwText(Element? markdown) {
  if (markdown == null) {
    return null;
  }
  final text = wwwTidy(redditCommentContent(markdown).body);
  return text.isEmpty ? null : text;
}

/// [text] with whitespace-only lines emptied and runs of blank lines folded to
/// one.
String wwwTidy(String text) =>
    text.split('\n').map((line) => line.trimRight()).join('\n').replaceAll(RegExp(r'\n{3,}'), '\n\n').trim();

/// The id in a fullname: `abc123` from `t3_abc123`.
String? wwwBareId(String? fullname, String prefix) {
  if (fullname == null || !fullname.startsWith(prefix)) {
    return null;
  }
  final id = fullname.substring(prefix.length);
  return id.isEmpty ? null : id;
}

/// A link as the app can open it: Reddit writes its own pages relative.
String? wwwAbsoluteUrl(String? href) {
  if (href == null || href.isEmpty) {
    return null;
  }
  return href.startsWith('/') ? 'https://www.reddit.com$href' : href;
}

int? wwwInt(String? value) => value == null ? null : int.tryParse(value.trim());

/// Reddit writes `2026-10-20T14:40:51.849000+0000`.
DateTime? wwwDate(String? value) => value == null ? null : DateTime.tryParse(value)?.toLocal();
