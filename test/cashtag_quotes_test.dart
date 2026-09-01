import 'package:dart_twitter_api/twitter_api.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/client/client.dart';
import 'package:xta/tweet/cashtag_quotes.dart';

void main() {
  test('cashtags on a post are uppercased and unique', () {
    final tweet = TweetWithCard()
      ..entities = Entities.fromJson({
        'symbols': [
          {
            'text': 'aapl',
            'indices': [0, 5],
          },
          {
            'text': 'AAPL',
            'indices': [6, 11],
          },
          {
            'text': 'TSLA',
            'indices': [12, 17],
          },
        ],
      });

    expect(tweetCashtags(tweet), ['AAPL', 'TSLA']);
  });

  test('a post with no symbols has none', () {
    expect(tweetCashtags(TweetWithCard()), isEmpty);
  });
}
