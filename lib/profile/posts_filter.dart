import 'package:xta/client/client.dart';

/// Which posts a profile's Posts tab shows.
enum PostsFilter {
  all,
  posts,
  retweets;

  /// A retweet is a chain whose first tweet carries another post.
  /// Quotes are original posts — they stay under [posts].
  bool accepts(TweetChain chain) {
    final isRetweet =
        chain.tweets.isNotEmpty &&
        chain.tweets.first.retweetedStatusWithCard != null;
    return switch (this) {
      PostsFilter.all => true,
      PostsFilter.posts => !isRetweet,
      PostsFilter.retweets => isRetweet,
    };
  }
}
