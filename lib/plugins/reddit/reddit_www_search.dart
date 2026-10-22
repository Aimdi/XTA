/// Reading search results from www.reddit.com's search (`/svc/shreddit/search/`).
///
/// Every result sits in a `<search-telemetry-tracker>` whose tracking JSON
/// names what it is — the post's id, title and flags, its author and community,
/// or the community or account a people or community search found — so that
/// JSON is read first and the markup only for what it leaves out: a post's
/// link, counts, age and thumbnail, a community's picture and description, an
/// account's karma.
library;

import 'dart:convert';

import 'package:html/dom.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_search_html.dart';
import 'package:xta/plugins/reddit/reddit_www.dart';
import 'package:xta/utils/json.dart';

/// Posts a search found. Like old.reddit's results they name no link target,
/// so each is its own page, which is where tapping it goes anyway.
List<RedditPost> readWwwSearchPosts(String body) => [for (final unit in _results(body, 'post')) ?_postFrom(unit)];

/// Communities a search found.
List<RedditSubredditResult> readWwwSearchCommunities(String body) => [
  for (final unit in _results(body, 'subreddit')) ?_communityFrom(unit),
];

/// Accounts a search found.
List<RedditUserResult> readWwwSearchPeople(String body) => [
  for (final unit in _results(body, 'people')) ?_personFrom(unit),
];

/// The result units of [kind] — `post`, `subreddit`, `people` — in order.
List<Element> _results(String body, String kind) => redditWwwPageKind(body) == RedditWwwPageKind.read
    ? parseWwwBody(body).querySelectorAll('search-telemetry-tracker[view-events="search/view/$kind"]')
    : const [];

Json _context(Element unit) {
  try {
    return Json(jsonDecode(unit.attributes['data-faceplate-tracking-context'] ?? ''));
  } on FormatException {
    return const Json(null);
  }
}

RedditPost? _postFrom(Element unit) {
  final context = _context(unit);
  final id = wwwBareId(context['post']['id'].string, 't3_');
  final title = context['post']['title'].string?.trim();
  final permalink = _postPath(unit.querySelector('a[data-testid="post-title"]')?.attributes['href']);
  if (id == null || title == null || title.isEmpty || permalink == null) {
    return null;
  }

  final counts = unit.querySelectorAll('[data-testid="search-counter-row"] faceplate-number');
  return RedditPost(
    id: id,
    title: title,
    subreddit: context['subreddit']['name'].string ?? '',
    permalink: permalink,
    author: context['profile']['name'].string,
    score: wwwInt(counts.firstOrNull?.attributes['number']) ?? 0,
    commentCount: wwwInt(counts.elementAtOrNull(1)?.attributes['number']) ?? 0,
    createdAt: wwwDate(unit.querySelector('faceplate-timeago')?.attributes['ts']),
    isSelf: true,
    over18: context['post']['nsfw'].boolean ?? false,
    spoiler: context['post']['spoiler'].boolean ?? false,
    thumbnail: unit.querySelector('[data-testid="search_post_thumbnail"]')?.attributes['src'],
  );
}

/// The post's own path, whichever host the link was written against.
String? _postPath(String? href) {
  final path = href == null ? null : Uri.tryParse(href)?.path;
  return path != null && path.contains('/comments/') ? path : null;
}

RedditSubredditResult? _communityFrom(Element unit) {
  final name = _context(unit)['subreddit']['name'].string;
  if (name == null || name.isEmpty) {
    return null;
  }

  final description = unit.querySelector('[data-testid="search-subreddit-desc-text"]')?.text.trim();
  return (
    name: name,
    // Reddit shows weekly visitors here now, which are not subscribers.
    subscribers: null,
    description: description == null || description.isEmpty ? null : description,
    iconUrl: redditCommunityIconUrl(communityIcon: unit.querySelector('img')?.attributes['src']),
  );
}

RedditUserResult? _personFrom(Element unit) {
  final name = _context(unit)['profile']['name'].string;
  if (name == null || name.isEmpty) {
    return null;
  }
  return (name: name, karma: wwwInt(unit.querySelector('faceplate-number')?.attributes['number']));
}
