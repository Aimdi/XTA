import 'package:dart_twitter_api/twitter_api.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/client/client.dart';
import 'package:xta/tweet/broadcasts.dart';

TweetWithCard _tweet({
  String? broadcastUrl,
  Map<String, dynamic>? card,
  bool video = false,
}) {
  final tweet = TweetWithCard();
  tweet.idStr = '1';
  tweet.user = User.fromJson({'id_str': 'u1', 'screen_name': 'someone'});
  if (broadcastUrl != null) {
    tweet.entities = Entities.fromJson({
      'urls': [
        {
          'url': 'https://t.co/b',
          'expanded_url': broadcastUrl,
          'display_url': 'x.com/i/broadcasts/1…',
          'indices': [0, 23],
        },
      ],
    });
  }
  tweet.card = card;
  if (video) {
    tweet.extendedEntities = Entities.fromJson({
      'media': [
        {'type': 'video', 'media_url_https': 'https://pbs.example/1.jpg'},
      ],
    });
  }
  return tweet;
}

void main() {
  test('a broadcasts URL marks the tweet', () {
    expect(
      tweetHasBroadcast(
        _tweet(broadcastUrl: 'https://x.com/i/broadcasts/1abc'),
      ),
      isTrue,
    );
  });

  test('the broadcast card name marks the tweet', () {
    expect(
      tweetHasBroadcast(_tweet(card: {'name': '745291183405076480:broadcast'})),
      isTrue,
    );
    expect(isBroadcastCard({'name': 'player'}), isFalse);
  });

  test('an ordinary post is not a broadcast', () {
    expect(tweetHasBroadcast(_tweet()), isFalse);
  });

  test('video media is detected separately from the link', () {
    expect(tweetHasVideoMedia(_tweet(video: true)), isTrue);
    expect(tweetHasVideoMedia(_tweet()), isFalse);
  });
}
