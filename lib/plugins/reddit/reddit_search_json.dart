/// Reading search results out of Reddit's listing JSON.
///
/// Guest HTML search is often refused; the same children a listing already
/// knows how to read still arrive on `/search.json` when a host answers.
library;

import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_search_html.dart';
import 'package:xta/utils/json.dart';

/// Posts from a `Listing` of `t3` children.
List<RedditPost> parseSearchPostsJson(Object? raw) {
  final posts = <RedditPost>[];
  for (final child in Json(raw)['data']['children'].list) {
    final value = child.raw;
    if (value is! Map) {
      continue;
    }
    final post = RedditPost.fromChild(Map<String, dynamic>.from(value));
    if (post != null) {
      posts.add(post);
    }
  }
  return posts;
}

/// Communities from a `Listing` of `t5` children.
List<RedditSubredditResult> parseSubredditResultsJson(Object? raw) {
  final results = <RedditSubredditResult>[];
  final seen = <String>{};
  for (final child in Json(raw)['data']['children'].list) {
    if (child['kind'].string != 't5') {
      continue;
    }
    final data = child['data'];
    final name = _communityName(data);
    if (name == null || !seen.add(name.toLowerCase())) {
      continue;
    }
    final description = data['public_description'].string?.trim();
    results.add((
      name: name,
      subscribers: data['subscribers'].integer,
      description: description == null || description.isEmpty
          ? null
          : description,
    ));
  }
  return results;
}

/// Accounts from a `Listing` of `t2` children.
List<RedditUserResult> parseUserResultsJson(Object? raw) {
  final results = <RedditUserResult>[];
  final seen = <String>{};
  for (final child in Json(raw)['data']['children'].list) {
    if (child['kind'].string != 't2') {
      continue;
    }
    final data = child['data'];
    final name = data['name'].string?.trim();
    if (name == null || name.isEmpty || !seen.add(name.toLowerCase())) {
      continue;
    }
    results.add((
      name: name,
      karma: data['total_karma'].integer ?? data['link_karma'].integer,
    ));
  }
  return results;
}

String? _communityName(Json data) {
  final prefixed = data['display_name_prefixed'].string;
  final raw = data['display_name'].string ?? prefixed;
  return raw == null ? null : normaliseSubreddit(raw);
}
