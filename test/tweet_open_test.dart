import 'package:dart_twitter_api/twitter_api.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/client/client.dart';
import 'package:xta/tweet/tweet_open.dart';

TweetWithCard _post({String? id = '1', User? author}) {
  final tweet = TweetWithCard()..idStr = id;
  if (author != null) {
    tweet.user = author;
  }
  return tweet;
}

User _author({String? screenName = 'reader', String? id = '9'}) {
  return User()
    ..screenName = screenName
    ..idStr = id;
}

void main() {
  group('what tapping a post opens', () {
    // The bug: these asserted their way through fields the rest of the card
    // treats as optional, inside a gesture callback. Flutter swallows the
    // exception, so the tap did nothing — and doing it again did nothing again.
    test('a post whose author has no handle still opens', () {
      final target = openablePost(_post(author: _author(screenName: null)));

      expect(target, isNotNull);
      expect(target!.id, '1');
      expect(target.username, isNull);
    });

    test('a post with no author at all still opens', () {
      expect(openablePost(_post())?.id, '1');
    });

    test('an ordinary post carries its author along', () {
      expect(openablePost(_post(author: _author()))?.username, 'reader');
    });

    // The one case where a dead tap is right: there is nothing to open.
    test('a post with no id opens nothing', () {
      expect(openablePost(_post(id: null)), isNull);
    });

    test('a post with an empty id opens nothing', () {
      expect(openablePost(_post(id: '')), isNull);
    });
  });

  group('what tapping an author opens', () {
    test('an author with a handle opens their profile', () {
      final target = openableProfile(_author());

      expect(target, isNotNull);
      expect(target!.screenName, 'reader');
      expect(target.id, '9');
    });

    test('an author with no handle is not tappable', () {
      expect(openableProfile(_author(screenName: null)), isNull);
    });

    test('a missing author is not tappable', () {
      expect(openableProfile(null), isNull);
    });

    // Tapping the name of the profile you are already reading would push the
    // same profile onto itself.
    test('the profile already on screen is deliberately inert', () {
      expect(openableProfile(_author(screenName: 'reader'), currentUsername: 'reader'), isNull);
    });

    test('a different author on that same profile is still tappable', () {
      expect(openableProfile(_author(screenName: 'someone'), currentUsername: 'reader')?.screenName, 'someone');
    });
  });
}
