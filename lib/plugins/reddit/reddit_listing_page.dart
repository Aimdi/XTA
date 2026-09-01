import 'package:xta/plugins/reddit/reddit_client.dart';

/// Appends [next] onto [existing], skipping any post whose id is already present.
List<RedditPost> appendRedditPosts(List<RedditPost> existing, List<RedditPost> next) {
  final seen = {for (final post in existing) post.id};
  return [...existing, ...next.where((post) => seen.add(post.id))];
}
