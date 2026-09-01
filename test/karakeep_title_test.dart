import 'package:dart_twitter_api/twitter_api.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/client/client.dart';
import 'package:xta/plugins/karakeep/karakeep_title.dart';

TweetWithCard _tweet({String? name, String? screenName}) {
  final tweet = TweetWithCard();
  if (name != null || screenName != null) {
    tweet.user = User()
      ..name = name
      ..screenName = screenName;
  }
  return tweet;
}

void main() {
  group('karakeepTitleFor', () {
    test('names the author, then the post', () {
      expect(
        karakeepTitleFor(_tweet(name: 'Jack', screenName: 'jack'), 'just setting up my twttr'),
        'Jack (@jack): just setting up my twttr',
      );
    });

    test('falls back to the handle when there is no display name', () {
      expect(karakeepTitleFor(_tweet(screenName: 'jack'), 'hello'), '@jack: hello');
    });

    test('is just the post when the author is unknown', () {
      expect(karakeepTitleFor(_tweet(), 'hello'), 'hello');
    });

    test('is just the byline for a post with no text, such as a photo', () {
      expect(karakeepTitleFor(_tweet(name: 'Jack', screenName: 'jack'), '   '), 'Jack (@jack)');
    });

    test('collapses newlines so the bookmark list stays readable', () {
      expect(
        karakeepTitleFor(_tweet(name: 'Jack', screenName: 'jack'), 'line one\n\nline two\ttabbed'),
        'Jack (@jack): line one line two tabbed',
      );
    });
  });
}
