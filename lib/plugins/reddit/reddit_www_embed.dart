/// Reading a post from its embed page (`embed.reddit.com/r/…/comments/…/?embed=true`):
/// the card another site shows when it embeds the post.
///
/// It is the one page of a single post Reddit still serves whole to a reader
/// without an account. The post's own page opens with the browser check, and
/// the piece that loads its comments carries no post, so a post that arrived
/// without its words or pictures — from search, or from a link to it — gets
/// them here: its text in full, its pictures, its video, its link, its score
/// and comment count. An NSFW post's embed is refused (400), which leaves the
/// post as it was.
library;

import 'dart:convert';

import 'package:html/dom.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_www.dart';
import 'package:xta/utils/json.dart';

/// The post on an embed page, or null when the page names none: a post that
/// is gone answers with an empty card.
///
/// [permalink] is the page the post was asked for, kept as the post's own: the
/// embed spells a profile post's page differently (`/r/u_<name>/…`).
RedditPost? readWwwEmbed(String body, {required String permalink}) {
  if (redditWwwPageKind(body) != RedditWwwPageKind.read) {
    return null;
  }

  final document = parseWwwBody(body);
  final screen = _screenOf(document);
  final id = wwwBareId(screen['post']['id'].string, 't3_');
  final title = wwwTidy(document.querySelector('#embed-title')?.text ?? '');
  if (id == null || title.isEmpty) {
    return null;
  }

  final community = wwwCommunityOf(permalink) ?? '';
  final link = _linkOf(screen['post']['url'].string, id);
  final header = document.querySelector('[data-testid="embed-header"]');
  final player = document.querySelector('shreddit-player');
  return RedditPost(
    id: id,
    title: title,
    subreddit: community,
    permalink: permalink,
    author: header?.querySelector('a[href*="/user/"]')?.text.trim(),
    score: wwwInt(document.querySelector('[data-testid="upvote"] faceplate-number')?.attributes['number']) ?? 0,
    commentCount: _countIn(document.querySelector('[data-testid="cta"]')?.text) ?? 0,
    createdAt: wwwDate(header?.querySelector('faceplate-timeago')?.attributes['ts']),
    url: link,
    isSelf: link == null,
    selfText: wwwText(document.querySelector('[id\$="-post-rtjson-content"]')),
    over18: screen['post']['nsfw'].boolean ?? false,
    domain: link == null ? 'self.$community' : Uri.tryParse(link)?.host.replaceFirst(RegExp(r'^www\.'), ''),
    galleryImages: wwwGalleryOf(document.documentElement),
    previewImage:
        player?.attributes['poster'] ?? document.querySelector('shreddit-aspect-ratio img')?.attributes['src'],
    videoDashUrl: player?.attributes['src'],
  );
}

/// [readWwwEmbed] for [permalink], as a parse another isolate can run: made
/// here, it holds nothing but the permalink.
RedditPost? Function(String body) wwwEmbedReader(String permalink) =>
    (body) => readWwwEmbed(body, permalink: permalink);

/// What the page reports to Reddit's analytics about the post it shows: its
/// id, its link, its NSFW mark.
Json _screenOf(Document document) {
  final raw = document.querySelector('shreddit-screenview-data')?.attributes['data'];
  if (raw == null) {
    return const Json(null);
  }
  try {
    return Json(jsonDecode(raw));
  } on FormatException {
    return const Json(null);
  }
}

/// Where the post points, or null for a post whose link is its own page.
String? _linkOf(String? url, String id) {
  final link = wwwAbsoluteUrl(url);
  final uri = link == null ? null : Uri.tryParse(link);
  if (uri == null) {
    return null;
  }
  final ownPage =
      uri.host.endsWith('reddit.com') && uri.pathSegments.contains('comments') && uri.pathSegments.contains(id);
  return ownPage ? null : link;
}

/// The number in "View 15 comments".
int? _countIn(String? text) {
  final digits = RegExp(r'\d[\d,]*').firstMatch(text ?? '')?.group(0);
  return digits == null ? null : int.tryParse(digits.replaceAll(',', ''));
}
