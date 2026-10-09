import 'dart:convert';
import 'dart:io';

import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/client/client.dart';
import 'package:xta/generated/l10n.dart';

/// A post its author reserves to paid subscribers comes back as a
/// `TweetPreviewDisplay` (its first line, no `legacy`), and a reply X hides for
/// the same reason as `TweetUnavailable` with `reason: ExclusiveTweet`.
/// Opening such a post threw while parsing and showed an error instead of the
/// post (QuaX #188). Both fixtures are recordings of x.com/Osemka8.
Map<String, dynamic> _fixture(String relativePath) =>
    jsonDecode(File('test/fixtures/$relativePath').readAsStringSync()) as Map<String, dynamic>;

List<dynamic> _tweetDetailEntries() {
  final instructions =
      _fixture(
            'TweetDetail/subscriber_preview.json',
          )['data']['threaded_conversation_with_injections_v2']['instructions']
          as List<dynamic>;
  return instructions.firstWhere((i) => i['type'] == 'TimelineAddEntries')['entries'] as List<dynamic>;
}

const _previewId = '2105589900078641579';

void main() {
  setUpAll(() async {
    TestWidgetsFlutterBinding.ensureInitialized();
    await L10n.load(const Locale('en'));
  });

  group('opening a subscriber-only post', () {
    test('reads the preview X sends as the post itself', () {
      final chains = TimelineParser.createTweetChains(_tweetDetailEntries());

      final focal = chains.firstWhere((chain) => chain.id == _previewId).tweets.single;
      expect(focal.isTombstone, isNot(isTrue), reason: 'a preview is not a deleted post');
      expect(focal.isSubscriberPreview, isTrue);
      expect(focal.idStr, _previewId);
      expect(focal.fullText, "I know I've written about this for you guys, but c…");
      expect(focal.user?.screenName, 'Osemka8', reason: 'the author only comes in the newer, legacy-free shape');
      expect(focal.user?.name, 'Osemka');
      expect(focal.createdAt, DateTime.utc(2026, 10, 1, 9, 25, 26));
      expect(focal.favoriteCount, 8);
      expect(focal.replyCount, 1);
      expect(focal.viewCount, 250);
    });

    test('a reply hidden as ExclusiveTweet says so rather than calling it deleted', () {
      final chains = TimelineParser.createTweetChains(_tweetDetailEntries());

      final hidden = chains.firstWhere((chain) => chain.id == '2105590666927513924').tweets.single;
      expect(hidden.isTombstone, isTrue);
      expect(hidden.idStr, '2105590666927513924');
      expect(hidden.text, L10n.current.subscribers_only_post_of_author);
    });

    test('the preview survives the feed cache', () {
      final focal = TimelineParser.createTweetChains(
        _tweetDetailEntries(),
      ).firstWhere((chain) => chain.id == _previewId).tweets.single;

      final restored = TweetWithCard.fromJson(jsonDecode(jsonEncode(focal.toJson())) as Map<String, dynamic>);
      expect(restored.isSubscriberPreview, isTrue);
      expect(restored.fullText, focal.fullText);
    });

    test('survives the preview losing or nulling any single field', () {
      final result =
          _tweetDetailEntries().first['content']['itemContent']['tweet_results']['result'] as Map<String, dynamic>;
      final preview = result['tweet'] as Map<String, dynamic>;
      for (final key in preview.keys) {
        for (final value in [null, 'reshaped']) {
          final copy = jsonDecode(jsonEncode(result)) as Map<String, dynamic>;
          if (value == null) {
            (copy['tweet'] as Map).remove(key);
          } else {
            (copy['tweet'] as Map)[key] = value;
          }
          expect(
            () => TweetWithCard.fromGraphqlJson(copy),
            returnsNormally,
            reason: 'preview field "$key" ${value == null ? 'dropped' : 'reshaped'}',
          );
        }
      }
    });

    test('a tombstone whose text is a plain string no longer throws', () {
      expect(TweetWithCard.tombstone({'text': 'not an object'}).text, L10n.current.this_tweet_is_unavailable);
    });
  });

  group('a profile mixing previews into its posts', () {
    List<TweetChain> profileChains() => TimelineParser.createUnconversationedChains(
      _fixture('UserTweets/subscriber_previews.json'),
      'tweet',
      const [],
      false,
      true,
      true,
      () => 0,
      () {},
    ).chains;

    test('keeps every post, previews included', () {
      final chains = profileChains();

      expect(chains.map((chain) => chain.id), ['2105651449673761205', '2105664464454942722', _previewId]);
      expect(chains.expand((chain) => chain.tweets).where((tweet) => tweet.isSubscriberPreview), hasLength(3));
    });

    test('keeps the threads X names profile-originals-conversation', () {
      final thread = profileChains().firstWhere((chain) => chain.id == '2105664464454942722');

      expect(thread.tweets, hasLength(2));
      expect(thread.tweets.every((tweet) => tweet.isSubscriberPreview && tweet.user?.screenName == 'Osemka8'), isTrue);
    });
  });
}
