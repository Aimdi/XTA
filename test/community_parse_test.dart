import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:xta/client/client.dart';
import 'package:xta/utils/json.dart';

Json _fixture(String name) => Json(jsonDecode(File('test/fixtures/Community/$name').readAsStringSync()));

void main() {
  const flutterCommunity = '1472249315724771329';

  group('CommunityQuery', () {
    test('reads the header, rules and member faces', () {
      final community = Community.fromQuery(_fixture('community.json'))!;
      expect(community.id, flutterCommunity);
      expect(community.name, 'Flutter');
      expect(community.memberCount, 20730);
      expect(community.topic, 'Software');
      expect(community.description, isNotEmpty);
      expect(community.createdAt, DateTime.fromMillisecondsSinceEpoch(1639846564596));
      expect(community.rules, hasLength(4));
      expect(community.rules.first, 'Be kind and respectful.');
      expect(community.memberAvatarUrls, hasLength(5));
      // The community's own banner wins over X's default one.
      expect(community.bannerUrl, contains('community_banner_img'));
    });

    test('a body without a community is null, not a throw', () {
      expect(Community.fromQuery(const Json({'data': {}})), isNull);
      expect(Community.fromResult(const Json({'__typename': 'User', 'rest_id': '1'})), isNull);
    });
  });

  group('CommunityTweetsTimeline', () {
    test('reads the Top posts, each marked with its community, and the next cursor', () {
      final timeline = communityTimelineOf(_fixture('timeline.json'))!;
      final page = TimelineParser.createUnconversationedChainsGraphql(timeline, 'tweet', [], true, true);
      expect(page.chains, hasLength(3));
      expect(page.chains.expand((chain) => chain.tweets).map((tweet) => tweet.communityId).toSet(), {flutterCommunity});
      expect(page.cursorBottom, isNotNull);
    });

    test('a community id survives the cache round trip', () {
      final timeline = communityTimelineOf(_fixture('timeline.json'))!;
      final tweet = TimelineParser.createUnconversationedChainsGraphql(
        timeline,
        'tweet',
        [],
        true,
        true,
      ).chains.first.tweets.first;
      expect(TweetWithCard.fromJson(jsonDecode(jsonEncode(tweet.toJson()))).communityId, flutterCommunity);
    });

    test('a body without a timeline is null', () {
      expect(communityTimelineOf(const Json({'data': {}})), isNull);
    });
  });

  group('membersSliceTimeline_Query', () {
    test('reads a page of members and the next cursor', () {
      final page = parseCommunityMembers(_fixture('members.json'));
      expect(page.users, hasLength(3));
      expect(page.users.first.screenName, 'tadaspetra');
      expect(page.users.every((user) => user.idStr != null && user.name != null), isTrue);
      expect(page.cursorBottom, isNotNull);
    });

    test('an empty slice is an empty last page', () {
      final page = parseCommunityMembers(const Json({}));
      expect(page.users, isEmpty);
      expect(page.cursorBottom, isNull);
    });
  });

  group('CommunitiesSearchQuery', () {
    test('reads a page of communities and the next cursor', () {
      final page = parseCommunitySearch(_fixture('search.json'));
      expect(page.communities, hasLength(3));
      expect(page.communities.every((community) => community.id.isNotEmpty && community.name.isNotEmpty), isTrue);
      expect(page.cursor, isNotNull);
    });

    test('an empty slice is an empty last page', () {
      final page = parseCommunitySearch(const Json({}));
      expect(page.communities, isEmpty);
      expect(page.cursor, isNull);
    });
  });
}
