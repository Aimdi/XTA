import 'dart:io';

import 'package:dart_twitter_api/twitter_api.dart' show User;
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:xta/client/client.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/group/feed_cache.dart';
import 'package:xta/group/feed_chunk_hash.dart';
import 'package:xta/group/feed_chunk_plan.dart';
import 'package:xta/group/group_discovery.dart';
import 'package:xta/group/group_discovery_feed.dart';
import 'package:xta/group/group_model.dart';

UserSubscription _member(String id, int day) => UserSubscription(
  id: id,
  screenName: id,
  name: id,
  profileImageUrlHttps: null,
  verified: false,
  createdAt: DateTime(2026, 1, day),
  inFeed: true,
);

TweetWithCard _tweet(String id, String by) => TweetWithCard()
  ..idStr = id
  ..fullText = 'post $id'
  ..createdAt = DateTime(2026, 10, 1)
  ..user = (User()
    ..idStr = by
    ..screenName = by
    ..name = by);

SubscriptionGroupGet _group(List<Subscription> members, {bool? includeReplies}) => SubscriptionGroupGet(
  id: 'g',
  name: '🍫',
  icon: defaultGroupIcon,
  subscriptions: members,
  includeReplies: includeReplies,
  includeRetweets: null,
  popular: false,
);

void main() {
  setUpAll(() async {
    sqfliteFfiInit();
    databaseFactory = databaseFactoryFfiNoIsolate;
    final dir = await Directory.systemTemp.createTemp('xta-discover-mine');
    await databaseFactory.setDatabasesPath(dir.path);
    await Repository().migrate();
  });

  test('the plan resolves flags, orders members oldest first and hashes chunks like the live feed', () {
    final prefs = PrefServiceCache(defaults: {optionGlobalIncludeReplies: false, optionGlobalIncludeRetweets: true});
    final members = [for (var i = 20; i >= 1; i--) _member('m$i', i)];
    final plan = planGroupFeed(_group(members), prefs: prefs);
    expect(plan.includeReplies, isFalse);
    expect(plan.includeRetweets, isTrue);
    expect(plan.members.first.id, 'm1');
    expect(plan.chunks.map((chunk) => chunk.users.length), [feedChunkSize, 20 - feedChunkSize]);
    expect(
      plan.chunks.map((chunk) => chunk.hash),
      feedChunkHashesFor(
        [for (final member in members) FeedChunkMember(id: member.id, createdAt: member.createdAt)],
        includeReplies: false,
        includeRetweets: true,
      ),
    );
    expect(planGroupFeed(_group(members, includeReplies: true), prefs: prefs).includeReplies, isTrue);
  });

  test('mining reads the chunks the feed stored and reports which members they covered', () async {
    final prefs = PrefServiceCache(defaults: {optionGlobalIncludeReplies: true, optionGlobalIncludeRetweets: true});
    final members = [for (var i = 1; i <= 18; i++) _member('m$i', i)];
    final plan = planGroupFeed(_group(members), prefs: prefs);
    final db = await Repository.writable();
    final chains = [
      TweetChain(id: 't1', isPinned: false, tweets: [_tweet('t1', 'm1')..retweetedStatusWithCard = _tweet('r1', 'c1')]),
      TweetChain(id: 't2', isPinned: false, tweets: [_tweet('t2', 'm3')..quotedStatusWithCard = _tweet('q1', 'c1')]),
    ];
    await db.insert(tableFeedGroupChunk, {
      'cursor_id': 1,
      'hash': plan.chunks.first.hash,
      'response': await encodeChunkBlob(chains.map((chain) => chain.toJson()).toList()),
    });

    final mined = await mineFeedDiscovery(await Repository.readOnly(), plan);
    expect(mined.covered, {for (final member in plan.chunks.first.users) member.id});
    expect(mined.covered, isNot(contains('m17')));
    final ranked = rankDiscoveryAccounts(mined.accounts, followed: {}, groupName: '🍫');
    expect(ranked.single.id, 'c1');
    expect(ranked.single.memberCount, 2);
    expect(
      ranked.single.supporters.map((s) => s.kind),
      unorderedEquals([DiscoverySignal.reposted, DiscoverySignal.quoted]),
    );
  });
}
