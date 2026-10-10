import 'dart:convert';
import 'dart:io';

import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/client/client.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/user.dart';

Map<String, dynamic> _loadFixture(String relativePath) {
  final file = File('test/fixtures/$relativePath');
  expect(file.existsSync(), isTrue, reason: 'missing fixture $relativePath');
  return jsonDecode(file.readAsStringSync()) as Map<String, dynamic>;
}

void main() {
  group('UserByScreenName fixture', () {
    test('fromNonLegacyJson reads live @X profile shape', () {
      final root = _loadFixture('UserByScreenName/ok.json');
      final result = root['data']?['user']?['result'] as Map<String, dynamic>?;
      expect(result, isNotNull);
      expect(result!['__typename'], 'User');

      final user = UserWithExtra.fromNonLegacyJson(result);
      expect(user.idStr, '783214');
      expect(user.screenName, 'X');
      expect(user.name, 'X');
      expect(user.profileImageUrlHttps, isNotNull);
      expect(user.followersCount, greaterThan(0));
    });
  });

  group('Tweet result fixture', () {
    test('fromGraphqlJson reads a live Tweet node', () {
      final result = _loadFixture('TweetDetail/tweet_result.json');
      expect(result['__typename'], 'Tweet');

      final tweet = TweetWithCard.fromGraphqlJson(result);
      expect(tweet.idStr, result['rest_id']);
      expect(tweet.fullText ?? tweet.text, isNotEmpty);
      expect(tweet.user?.screenName, isNotNull);
    });
  });

  group('UserTweets add_entries fixture', () {
    test('createTweetChains parses live TimelineAddEntries tweets', () {
      final fixture = _loadFixture('UserTweets/add_entries.json');
      final entries = fixture['entries'] as List<dynamic>;
      expect(entries, isNotEmpty);

      final chains = TimelineParser.createTweetChains(entries);
      expect(chains, isNotEmpty);
      expect(chains.first.id, isNotEmpty);
      expect(chains.first.tweets, isNotEmpty);
      expect(chains.first.tweets.first.idStr, chains.first.id);
      expect(chains.first.tweets.first.fullText ?? chains.first.tweets.first.text, isNotEmpty);
    });
  });

  // Captured from x.com by QuaX (UserByScreenName KybxDj9RrADIITXlGG8kpw and
  // UserOriginalsTimeline qtvmQffnepvr0oPe4A8MqQ). Current queries return users
  // with no `legacy` at all: every profile field lives in its own container.
  group('UserByScreenName without legacy', () {
    Map<String, dynamic> modern() =>
        _loadFixture('UserByScreenName/modern.json')['data']['user']['result'] as Map<String, dynamic>;

    test('fromNonLegacyJson reads the profile from the new containers', () {
      final result = modern();
      expect(result.containsKey('legacy'), isFalse, reason: 'the capture is the shape without legacy');

      final user = UserWithExtra.fromNonLegacyJson(result);
      expect(user.idStr, '2095909295913103360');
      expect(user.screenName, 'quax_tests');
      expect(user.name, 'QuaX Tests');
      expect(user.createdAt, isNotNull);
      expect(user.description, startsWith('🧪 Throwaway account'), reason: 'bio comes from profile_bio');
      expect(user.entities?.description?.urls, hasLength(2), reason: 'bio links come from profile_bio.entities');
      expect(user.location, 'India');
      expect(user.url, isNull, reason: 'an empty website is no website');
      expect(user.statusesCount, 41);
      expect(user.followersCount, 0);
      expect(user.friendsCount, 0);
      expect(user.protected, isFalse);
      expect(user.profileBannerUrl, contains('profile_banners'));
      expect(user.profileImageUrlHttps, contains('profile_images'));
    });

    test('pinned posts come from pinned_items, else from legacy', () {
      final result = modern();
      final pinned = {
        ...result,
        'pinned_items': {
          'tweet_ids_str': ['2095951992489152745'],
        },
      };
      expect(UserWithExtra.pinnedTweetIdsOf(pinned), ['2095951992489152745']);
      expect(UserWithExtra.pinnedTweetIdsOf(result), isEmpty, reason: 'pinned_items is {} when nothing is pinned');

      final legacy = _loadFixture('UserByScreenName/ok.json')['data']['user']['result'] as Map<String, dynamic>;
      (legacy['legacy'] as Map<String, dynamic>)['pinned_tweet_ids_str'] = ['1'];
      expect(UserWithExtra.pinnedTweetIdsOf(legacy), ['1'], reason: 'older results pin from legacy');
    });

    test('legacy values survive when the new containers are absent', () {
      final legacy = _loadFixture('UserByScreenName/ok.json')['data']['user']['result'] as Map<String, dynamic>;

      final user = UserWithExtra.fromNonLegacyJson(legacy);
      expect(user.description, legacy['legacy']['description']);
      expect(user.followersCount, legacy['legacy']['followers_count']);
    });
  });

  group('UserOriginalsTimeline page', () {
    setUpAll(() async {
      TestWidgetsFlutterBinding.ensureInitialized();
      await L10n.load(const Locale('en'));
    });

    test('posts keep their author although it has no legacy', () {
      final status = TimelineParser.createUnconversationedChains(
        _loadFixture('UserOriginalsTimeline/page.json'),
        'tweet',
        const [],
        true,
        false,
        true,
        () => 0,
        () {},
      );

      expect(status.chains.map((c) => c.id), ['2095951992489152745', '2095934584533680451']);
      for (final tweet in status.chains.expand((c) => c.tweets)) {
        expect(tweet.user?.screenName, 'quax_tests_2', reason: 'the author lives in core.user_results.result');
        expect(tweet.user?.idStr, '2095914123905118211');
      }
      expect(status.cursorBottom, isNotNull);
    });
  });
}
