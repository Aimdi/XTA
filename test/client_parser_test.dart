import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:xta/client/client.dart';
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
}
