import 'package:dart_twitter_api/twitter_api.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/client/client.dart';
import 'package:xta/profile/posts_filter.dart';

TweetWithCard _tweet({required String id, bool retweet = false}) {
  final tweet = TweetWithCard();
  tweet.idStr = id;
  tweet.user = User.fromJson({'id_str': 'u$id', 'screen_name': 'someone'});
  if (retweet) {
    final inner = TweetWithCard();
    inner.idStr = 'orig-$id';
    inner.user = User.fromJson({'id_str': 'u2', 'screen_name': 'other'});
    tweet.retweetedStatusWithCard = inner;
  }
  return tweet;
}

TweetChain _chain(TweetWithCard tweet) =>
    TweetChain(id: tweet.idStr!, tweets: [tweet], isPinned: false);

void main() {
  final original = _chain(_tweet(id: '1'));
  final repost = _chain(_tweet(id: '2', retweet: true));
  final quoted = _chain(_tweet(id: '3')); // quotes are original posts

  test('all keeps posts and retweets', () {
    expect(PostsFilter.all.accepts(original), isTrue);
    expect(PostsFilter.all.accepts(repost), isTrue);
  });

  test('posts drops retweets and keeps originals', () {
    expect(PostsFilter.posts.accepts(original), isTrue);
    expect(PostsFilter.posts.accepts(repost), isFalse);
    expect(PostsFilter.posts.accepts(quoted), isTrue);
  });

  test('retweets keeps only retweets', () {
    expect(PostsFilter.retweets.accepts(original), isFalse);
    expect(PostsFilter.retweets.accepts(repost), isTrue);
  });

  test('an empty chain is not a retweet', () {
    final empty = TweetChain(id: 'x', tweets: const [], isPinned: false);
    expect(PostsFilter.posts.accepts(empty), isTrue);
    expect(PostsFilter.retweets.accepts(empty), isFalse);
  });
}
