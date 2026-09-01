import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/client/client.dart';
import 'package:xta/generated/l10n.dart';

/// A reply X will not return is not the same as a reply that does not exist.
/// Dropping it silently loses a link in the chain, so the position has to
/// survive as a tombstone — carrying X's own wording when it sent one.
Map<String, dynamic> _tweetItem(String threadId, String tweetId, Map<String, dynamic> result) => {
      'entryId': 'conversationthread-$threadId-tweet-$tweetId',
      'item': {
        'itemContent': {'itemType': 'TimelineTweet', 'tweet_results': result},
      },
    };

Map<String, dynamic> _readable(String id) => {
      'result': {
        'rest_id': id,
        'legacy': {'id_str': id, 'full_text': 'reply $id'},
        'core': {
          'user_results': {
            'result': {
              'rest_id': '1',
              'legacy': {'screen_name': 'someone', 'name': 'Someone'},
            },
          },
        },
      },
    };

Map<String, dynamic> _thread(String threadId, List<Map<String, dynamic>> items) => {
      'entryId': 'conversationthread-$threadId',
      'content': {'items': items},
    };

void main() {
  setUpAll(() async {
    TestWidgetsFlutterBinding.ensureInitialized();
    await L10n.load(const Locale('en'));
  });

  group('TimelineParser conversation tombstones', () {
    test('keeps a deleted reply as a tombstone instead of dropping it', () {
      final chains = TimelineParser.createTweetChains([
        _thread('100', [
          _tweetItem('100', '101', _readable('101')),
          _tweetItem('100', '102', {
            'result': {
              '__typename': 'TweetTombstone',
              'tombstone': {
                'text': {'text': 'This Post was deleted by the Post author. Learn more'},
              },
            },
          }),
        ]),
      ]);

      expect(chains, hasLength(1));
      expect(chains.single.tweets, hasLength(2), reason: 'the unavailable reply must still occupy its position');

      final tombstone = chains.single.tweets.last;
      expect(tombstone.isTombstone, isTrue);
      expect(
        tombstone.text,
        'This Post was deleted by the Post author.',
        reason: "X's own wording, without 'Learn more'",
      );
    });

    test('gives the tombstone the missing reply id so it sorts into place', () {
      final chains = TimelineParser.createTweetChains([
        _thread('200', [
          _tweetItem('200', '202', {
            'result': {
              'tombstone': {
                'text': {'text': 'unavailable'},
              },
            },
          }),
        ]),
      ]);

      // conversation.dart orders a chain by idStr; the empty default would pull
      // every tombstone to the top of the thread.
      expect(chains.single.tweets.single.idStr, '202');
    });

    test('falls back to the translated string when X explains nothing', () {
      final chains = TimelineParser.createTweetChains([
        _thread('300', [
          _tweetItem('300', '301', {
            'result': {'__typename': 'TweetUnavailable'},
          }),
        ]),
      ]);

      expect(chains.single.tweets.single.isTombstone, isTrue);
      expect(chains.single.tweets.single.text, L10n.current.this_tweet_is_unavailable);
    });

    test('still skips an item carrying no result at all', () {
      final chains = TimelineParser.createTweetChains([
        _thread('400', [
          _tweetItem('400', '401', const {}),
          _tweetItem('400', '402', _readable('402')),
        ]),
      ]);

      expect(
        chains.single.tweets,
        hasLength(1),
        reason: 'an absent result is a shape we do not recognise, not a tombstone',
      );
      expect(chains.single.tweets.single.idStr, '402');
    });

    test('applies to profile conversations too', () {
      final chains = TimelineParser.createTweets([
        {
          'entryId': 'profile-conversation-500',
          'content': {
            'items': [
              _tweetItem('500', '501', {
                'result': {
                  'tombstone': {
                    'text': {'text': 'gone'},
                  },
                },
              }),
            ],
          },
        },
      ]);

      expect(chains.single.tweets.single.isTombstone, isTrue);
      expect(chains.single.tweets.single.text, 'gone');
    });
  });
}
