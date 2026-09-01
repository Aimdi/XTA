/// Reading search results out of old.reddit.com's HTML.
///
/// The search page does not render like a listing: results come back as
/// `.search-result-*` blocks rather than the `div.thing` a subreddit page uses,
/// and old.reddit has shipped both shapes over the years. Both are therefore
/// tried, newest markup first, and anything unreadable is skipped rather than
/// guessed at.
library;

import 'package:html/dom.dart';
import 'package:html/parser.dart' as html;
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_html.dart';

/// A subreddit a search turned up.
typedef RedditSubredditResult = ({
  String name,
  int? subscribers,
  String? description,
  String? iconUrl,
});

/// An account a search turned up.
typedef RedditUserResult = ({String name, int? karma});

/// The first run of digits in a string like `1,234 subscribers`.
int? _number(String? text) {
  if (text == null) {
    return null;
  }
  final match = RegExp(
    r'-?\d+',
  ).firstMatch(text.replaceAll(RegExp(r'[,.\s]'), ''));
  return match == null ? null : int.tryParse(match.group(0)!);
}

/// The `name` in `/r/name` or `/user/name`, wherever it sits in the path.
String? _nameAfter(String? href, String segment) {
  if (href == null) {
    return null;
  }
  final segments = Uri.tryParse(
    href,
  )?.pathSegments.where((s) => s.isNotEmpty).toList();
  if (segments == null) {
    return null;
  }
  final index = segments.indexOf(segment);
  return index == -1 || index + 1 >= segments.length
      ? null
      : segments[index + 1];
}

/// Posts from a search page.
///
/// A subreddit listing and a search share nothing but the post; when the page
/// happens to be rendered as a listing the existing parser is exactly right, so
/// that is tried before the search-specific markup.
List<RedditPost> parseSearchPosts(String body) {
  final listing = parseListing(body).posts;
  if (listing.isNotEmpty) {
    return listing;
  }

  final document = html.parse(body);
  final posts = <RedditPost>[];
  for (final result in document.querySelectorAll('.search-result-link')) {
    final post = _searchPostFrom(result);
    if (post != null) {
      posts.add(post);
    }
  }
  return posts;
}

RedditPost? _searchPostFrom(Element result) {
  final title = result.querySelector('.search-title');
  final href = title?.attributes['href'];
  if (title == null || href == null) {
    return null;
  }

  final permalink = Uri.tryParse(href)?.path;
  if (permalink == null || permalink.isEmpty) {
    return null;
  }

  final fullname = result.attributes['data-fullname'];
  final id = fullname != null && fullname.startsWith('t3_')
      ? fullname.substring(3)
      // The comments path is `/r/x/comments/<id>/slug/`, so the id is there
      // even when the attribute is not.
      : _nameAfter(href, 'comments');
  if (id == null || id.isEmpty) {
    return null;
  }

  final raw = result.querySelector('time')?.attributes['datetime'];

  return RedditPost(
    id: id,
    title: title.text.trim(),
    subreddit:
        _nameAfter(
          result.querySelector('.search-subreddit-link')?.attributes['href'],
          'r',
        ) ??
        _nameAfter(href, 'r') ??
        '',
    permalink: permalink,
    author: result.querySelector('.author')?.text.trim(),
    score: _number(result.querySelector('.search-score')?.text) ?? 0,
    commentCount: _number(result.querySelector('.search-comments')?.text) ?? 0,
    createdAt: raw == null ? null : DateTime.tryParse(raw)?.toLocal(),
    // A search result carries no link target of its own, so the post is treated
    // as its own page — which is where tapping it should go anyway.
    isSelf: true,
    over18:
        result.classes.contains('over18') ||
        result.querySelector('.nsfw-stamp') != null,
    spoiler:
        result.classes.contains('spoiler') ||
        result.querySelector('.spoiler-stamp') != null,
  );
}

/// Subreddits from `/subreddits/search` or the search page's subreddit block.
List<RedditSubredditResult> parseSubredditResults(String body) {
  final document = html.parse(body);
  final results = <RedditSubredditResult>[];
  final seen = <String>{};

  for (final element in document.querySelectorAll(
    '.search-result-subreddit, div.subreddit.thing',
  )) {
    final result = _subredditFrom(element);
    if (result != null && seen.add(result.name.toLowerCase())) {
      results.add(result);
    }
  }

  return results;
}

RedditSubredditResult? _subredditFrom(Element element) {
  final link =
      element.querySelector('a.search-subreddit-link') ??
      element.querySelector('a.title') ??
      element.querySelector('a[href*="/r/"]');

  final name =
      _nameAfter(link?.attributes['href'], 'r') ??
      normaliseSubreddit(link?.text ?? '');
  if (name == null || name.isEmpty) {
    return null;
  }

  final description =
      element.querySelector('.search-result-body')?.text.trim() ??
      element.querySelector('.description')?.text.trim();

  return (
    name: name,
    subscribers: _number(
      element.querySelector('.search-subscribers')?.text ??
          element.querySelector('.subscribers')?.text,
    ),
    description: description == null || description.isEmpty
        ? null
        : description,
    iconUrl: null,
  );
}

/// Accounts from `/search?q=…&type=user`.
List<RedditUserResult> parseUserResults(String body) {
  final document = html.parse(body);
  final results = <RedditUserResult>[];
  final seen = <String>{};

  for (final element in document.querySelectorAll('.search-result-user')) {
    final name = _nameAfter(
      element.querySelector('a[href*="/user/"]')?.attributes['href'],
      'user',
    );
    if (name == null || name.isEmpty || !seen.add(name.toLowerCase())) {
      continue;
    }

    results.add((
      name: name,
      karma: _number(element.querySelector('.search-result-user-karma')?.text),
    ));
  }

  return results;
}
