import 'dart:io';

import 'package:dart_twitter_api/twitter_api.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/client/client.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/profile/profile_feed_settings.dart';
import 'package:xta/user.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';

TweetWithCard _tweet({
  required String authorId,
  required String authorName,
  String? inReplyToUserId,
  String? inReplyToScreenName,
  String? inReplyToStatusId,
  bool retweet = false,
}) {
  final tweet = TweetWithCard()
    ..idStr = 't1'
    ..user = (User()
      ..idStr = authorId
      ..screenName = authorName)
    ..inReplyToUserIdStr = inReplyToUserId
    ..inReplyToScreenName = inReplyToScreenName
    ..inReplyToStatusIdStr = inReplyToStatusId;

  if (retweet) {
    tweet.retweetedStatusWithCard = TweetWithCard()..idStr = 't0';
  }
  return tweet;
}

TweetChain _chain(TweetWithCard tweet) => TweetChain(id: tweet.idStr!, tweets: [tweet], isPinned: false);

UserWithExtra _user(String id, String screenName) =>
    UserWithExtra.fromArguments(idStr: id, screenName: screenName, possiblySensitive: false);

void main() {
  group('isReplyToSomeoneElse', () {
    test('a plain post is not a reply', () {
      expect(isReplyToSomeoneElse(_tweet(authorId: '1', authorName: 'tommy')), isFalse);
    });

    test('answering another account is a reply', () {
      expect(
        isReplyToSomeoneElse(_tweet(
          authorId: '1',
          authorName: 'tommy',
          inReplyToUserId: '2',
          inReplyToScreenName: 'someone',
          inReplyToStatusId: 't0',
        )),
        isTrue,
      );
    });

    test('answering yourself is a thread, not a reply', () {
      // The whole point: hiding replies must not cut someone's own threads.
      expect(
        isReplyToSomeoneElse(_tweet(
          authorId: '1',
          authorName: 'tommy',
          inReplyToUserId: '1',
          inReplyToScreenName: 'tommy',
          inReplyToStatusId: 't0',
        )),
        isFalse,
      );
    });

    test('falls back to screen names when ids are missing', () {
      expect(
        isReplyToSomeoneElse(_tweet(authorId: '1', authorName: 'tommy', inReplyToScreenName: 'tommy')),
        isFalse,
      );
      expect(
        isReplyToSomeoneElse(_tweet(authorId: '1', authorName: 'tommy', inReplyToScreenName: 'someone')),
        isTrue,
      );
    });

    test('an unidentifiable reply target counts as a reply', () {
      // Better to hide something the reader asked to hide than to leak it.
      expect(
        isReplyToSomeoneElse(TweetWithCard()
          ..idStr = 't1'
          ..inReplyToStatusIdStr = 't0'),
        isTrue,
      );
    });

    test('null is not a reply', () {
      expect(isReplyToSomeoneElse(null), isFalse);
    });
  });

  group('filterHiddenReplies', () {
    final reply = _chain(_tweet(
      authorId: '1',
      authorName: 'Tommy',
      inReplyToUserId: '2',
      inReplyToScreenName: 'someone',
      inReplyToStatusId: 't0',
    ));
    final post = _chain(_tweet(authorId: '1', authorName: 'Tommy'));
    final selfThread = _chain(_tweet(
      authorId: '1',
      authorName: 'Tommy',
      inReplyToUserId: '1',
      inReplyToScreenName: 'Tommy',
      inReplyToStatusId: 't0',
    ));

    test('keeps everything when nobody is filtered', () {
      final chains = [reply, post];
      expect(filterHiddenReplies(chains, const {}), chains);
    });

    test('drops that user\'s replies to others', () {
      expect(filterHiddenReplies([reply, post], {'tommy'}), [post]);
    });

    test('matches the screen name case-insensitively', () {
      expect(filterHiddenReplies([reply], {'TOMMY'.toLowerCase()}), isEmpty);
    });

    test('keeps their own threads', () {
      expect(filterHiddenReplies([selfThread], {'tommy'}), [selfThread]);
    });

    test('leaves other people\'s replies alone', () {
      final other = _chain(_tweet(
        authorId: '9',
        authorName: 'someoneelse',
        inReplyToUserId: '2',
        inReplyToScreenName: 'x',
        inReplyToStatusId: 't0',
      ));

      expect(filterHiddenReplies([other], {'tommy'}), [other]);
    });

    test('is independent of the retweet filter', () {
      final retweet = _chain(_tweet(authorId: '1', authorName: 'Tommy', retweet: true));

      expect(filterHiddenReplies([retweet], {'tommy'}), [retweet], reason: 'a repost is not a reply');
      expect(filterHiddenRetweets([reply], {'tommy'}), [reply], reason: 'a reply is not a repost');
    });
  });

  group('storage', () {
    setUpAll(() async {
      sqfliteFfiInit();
      databaseFactory = databaseFactoryFfi;
      final dir = await Directory.systemTemp.createTemp('xta_reply_filter_test');
      await databaseFactory.setDatabasesPath(dir.path);
      await Repository().migrate();
    });

    test('the two filters are stored separately', () async {
      final user = _user('42', 'Tommy');

      await setRepliesHidden(user, true);
      expect(await isRepliesHidden('42'), isTrue);
      expect(await isRetweetsHidden('42'), isFalse, reason: 'hiding replies must not also hide reposts');

      expect(await hiddenReplyScreenNames(), contains('tommy'));
      expect(await hiddenRetweetScreenNames(), isNot(contains('tommy')));

      await setRepliesHidden(user, false);
      expect(await isRepliesHidden('42'), isFalse);
      expect(await hiddenReplyScreenNames(), isEmpty);
    });
  });
}
