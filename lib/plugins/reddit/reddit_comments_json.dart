/// Pure parsers for Reddit's OAuth/JSON comment thread response.
///
/// The endpoint returns `[postListing, commentsListing]`. Callers pull the
/// post from listing[0] via [RedditPost.fromChild]; this file turns listing[1]
/// into a [RedditComment] tree.
library;

import 'package:xta/plugins/reddit/reddit_comments.dart';
import 'package:xta/plugins/reddit/reddit_media_urls.dart';
import 'package:xta/utils/json.dart';

/// Comments from a Reddit JSON Listing (`kind` / `data.children`), or empty
/// when the shape is wrong.
List<RedditComment> commentsFromListing(Json listing, {String? parentPermalink}) {
  return [
    for (final child in listing['data']['children'].list)
      ...?_pickComment(commentFromChild(child, parentPermalink: parentPermalink)),
  ];
}

/// One `t1` comment or `more` stub from a Listing child. Null when the kind is
/// neither, or a `t1` has neither text nor media.
RedditComment? commentFromChild(Json child, {String? parentPermalink}) {
  final kind = child['kind'].string;
  if (kind == 'more') {
    return _moreStub(child['data'], parentPermalink: parentPermalink);
  }
  if (kind != 't1') {
    return null;
  }

  final data = child['data'];
  final id = data['id'].string;
  if (id == null || id.isEmpty) {
    return null;
  }

  final rawBody = data['body'].string ?? '';
  final media = mediaFromCommentBody(rawBody);
  if (media.body.isEmpty && media.urls.isEmpty) {
    return null;
  }

  final created = data['created_utc'].number;
  return RedditComment(
    id: id,
    author: data['author'].string,
    body: media.body,
    score: data['score'].integer,
    createdAt: created == null
        ? null
        : DateTime.fromMillisecondsSinceEpoch((created * 1000).round(), isUtc: true).toLocal(),
    isSubmitter: data['is_submitter'].boolean ?? false,
    mediaUrls: media.urls,
    replies: _repliesFromJson(data['replies'], parentPermalink: data['permalink'].string),
    permalink: data['permalink'].string,
  );
}

/// The held-back replies live on their parent's own page — without that
/// permalink the row rendered un-tappable, "more replies · 47" doing nothing.
RedditComment _moreStub(Json data, {String? parentPermalink}) {
  final children = data['children'].list;
  final id = data['id'].string ?? children.firstOrNull?.string ?? 'more';
  return RedditComment(id: id, body: '', permalink: parentPermalink, moreCount: data['count'].integer ?? -1);
}

List<RedditComment> _repliesFromJson(Json replies, {String? parentPermalink}) {
  // Leaf comments send `replies: ""` rather than an empty Listing.
  if (replies.raw is String || !replies.exists) {
    return const [];
  }
  return commentsFromListing(replies, parentPermalink: parentPermalink);
}

/// Pictures linked or tokenised in a markdown comment body, plus the words
/// that remain after media-only announcements are stripped.
({List<String> urls, String body}) mediaFromCommentBody(String raw) {
  final urls = <String>[];
  final tokens = <String>[];

  for (final match in redditMediaToken.allMatches(raw)) {
    tokens.add(match.group(0)!);
    final image = redditTokenImage(match);
    if (image != null && !urls.contains(image)) {
      urls.add(image);
    }
  }

  final linked = <String>[];
  for (final match in _httpUrl.allMatches(raw)) {
    final link = match.group(0)!;
    final image = redditEmbeddableImage(link);
    if (image != null) {
      linked.add(link);
      if (!urls.contains(image)) {
        urls.add(image);
      }
    }
  }

  var text = raw;
  for (final token in tokens) {
    text = text.replaceAll(token, '');
  }
  text = text.trim();

  if (text.isEmpty || urls.isEmpty) {
    return (urls: urls, body: text);
  }

  var withoutLinks = text;
  for (final link in linked) {
    withoutLinks = withoutLinks.replaceAll(link, '');
  }
  for (final url in urls) {
    withoutLinks = withoutLinks.replaceAll(url, '');
  }

  return (urls: urls, body: withoutLinks.trim().isEmpty ? '' : text);
}

final _httpUrl = RegExp(r'https?://[^\s\]\)>]+');

List<RedditComment>? _pickComment(RedditComment? comment) => comment == null ? null : [comment];
