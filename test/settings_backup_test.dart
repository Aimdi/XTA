import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/settings/backup_data.dart';
import 'package:xta/settings/backup_rows.dart';

const _icon = '{"pack":"custom","key":"rss_feed"}';
const _seenAt = '2026-08-01T00:00:00.000Z';

final _createdAt = DateTime.utc(2026, 1, 2, 3, 4, 5);

/// One row in every section a backup covers, so a section that stops being
/// written fails the round-trip instead of quietly disappearing again.
SettingsData _full() {
  return SettingsData(
    exportedAt: DateTime.utc(2026, 8, 4, 9, 30),
    appVersion: '4.12.0+400001040',
    settings: {'theme': 'dark'},
    searchSubscriptions: [SearchSubscription(id: 'dart', createdAt: _createdAt)],
    userSubscriptions: [
      UserSubscription(
        id: '1',
        screenName: 'reader',
        name: 'Reader',
        profileImageUrlHttps: null,
        verified: false,
        createdAt: _createdAt,
        inFeed: true,
      ),
    ],
    pluginRows: {
      'substackSubscriptions': [
        SubstackSubscription(
          id: 'astral',
          baseUrl: 'https://astral.substack.com',
          name: 'Astral',
          logoUrl: null,
          createdAt: _createdAt,
          inFeed: true,
        ),
      ],
      'redditSubscriptions': [RedditSubscription(id: 'dartlang', name: 'dartlang', createdAt: _createdAt, inFeed: true)],
      'stockSubscriptions': [StockSubscription(id: 'AAPL', symbol: 'AAPL', createdAt: _createdAt, inFeed: true)],
      'threadsSubscriptions': [
        ThreadsSubscription(id: 'reader', name: 'Reader', avatarUrl: null, createdAt: _createdAt, inFeed: true),
      ],
      'blueskySubscriptions': [
        BlueskySubscription(
          id: 'reader.bsky.social',
          name: 'Reader',
          avatarUrl: null,
          createdAt: _createdAt,
          inFeed: true,
        ),
      ],
      'mastodonSubscriptions': [
        MastodonSubscription(
          id: 'reader@example.social',
          name: 'Reader',
          avatarUrl: null,
          createdAt: _createdAt,
          inFeed: true,
        ),
      ],
      'redditLocalVotes': [RedditLocalVote(id: 'abc123')],
      'threadsLocalLikes': [ThreadsLocalLike(id: 't_like_1')],
      'blueskyLocalLikes': [BlueskyLocalLike(id: 'at://did:plc:a/app.bsky.feed.post/1')],
    },
    subscriptionGroups: [
      SubscriptionGroup(id: 'g1', name: 'Feeds', icon: _icon, color: null, numberOfMembers: 1, createdAt: _createdAt),
    ],
    subscriptionGroupMembers: [SubscriptionGroupMember(group: 'g1', profile: '1')],
    searchGroupMembers: [SearchGroupMember(group: 'g1', search: 'dart')],
    tweets: [SavedTweet(id: 't1', user: '1', content: '{}')],
    savedTweetFolders: [SavedTweetFolder(id: 'f1', name: 'Later', createdAt: _createdAt)],
    likedTweets: [LikedTweet(id: 't2', user: '1', content: '{}')],
    retweetFilters: [UserFeedFilter(userId: '1', screenName: 'reader')],
    replyFilters: [UserFeedFilter(userId: '2', screenName: 'other')],
    feedReadPositions: [FeedReadPositionRow(groupId: 'g1', chainId: 'c1', chainCreatedAt: _seenAt)],
    accounts: [Account(id: 'a1', authHeader: '{"cookie":"x"}', screenName: 'reader')],
  );
}

/// Through the file and back, exactly as an export followed by an import.
SettingsData _reparse(SettingsData data) {
  return SettingsData.fromJson(jsonDecode(jsonEncode(data.toJson())) as Map<String, dynamic>);
}

/// A backup as it was written before the header existed: no version, and none
/// of the sections added since.
final _legacy = <String, dynamic>{
  'settings': {'theme': 'dark'},
  'subscriptions': [
    {
      'id': '1',
      'screen_name': 'reader',
      'name': 'Reader',
      'verified': 1,
      'created_at': '2026-01-02 03:04:05',
      'in_feed': 1,
    },
  ],
  'tweets': [
    {'id': 't1', 'content': '{}', 'user_id': '1'},
  ],
};

void main() {
  group('round trip', () {
    test('carries every section the backup covers', () {
      final data = _reparse(_full());

      expect(data.settings?['theme'], 'dark');
      expect(data.searchSubscriptions?.single.id, 'dart');
      expect(data.userSubscriptions?.single.screenName, 'reader');
      expect((data.pluginRows?['substackSubscriptions']?.single as SubstackSubscription?)?.baseUrl, 'https://astral.substack.com');
      expect((data.pluginRows?['redditSubscriptions']?.single as RedditSubscription?)?.name, 'dartlang');
      expect((data.pluginRows?['stockSubscriptions']?.single as StockSubscription?)?.symbol, 'AAPL');
      expect((data.pluginRows?['threadsSubscriptions']?.single as ThreadsSubscription?)?.id, 'reader');
      expect((data.pluginRows?['blueskySubscriptions']?.single as BlueskySubscription?)?.id, 'reader.bsky.social');
      expect((data.pluginRows?['mastodonSubscriptions']?.single as MastodonSubscription?)?.id, 'reader@example.social');
      expect((data.pluginRows?['redditLocalVotes']?.single as RedditLocalVote?)?.id, 'abc123');
      expect((data.pluginRows?['threadsLocalLikes']?.single as ThreadsLocalLike?)?.id, 't_like_1');
      expect((data.pluginRows?['blueskyLocalLikes']?.single as BlueskyLocalLike?)?.id, 'at://did:plc:a/app.bsky.feed.post/1');
      expect(data.subscriptionGroups?.single.name, 'Feeds');
      expect(data.subscriptionGroupMembers?.single.profile, '1');
      expect(data.searchGroupMembers?.single.search, 'dart');
      expect(data.tweets?.single.id, 't1');
      expect(data.savedTweetFolders?.single.name, 'Later');
      expect(data.likedTweets?.single.id, 't2');
      expect(data.retweetFilters?.single.screenName, 'reader');
      expect(data.replyFilters?.single.userId, '2');
      expect(data.feedReadPositions?.single.chainCreatedAt, _seenAt);
      expect(data.accounts?.single.id, 'a1');
    });

    test('says which format wrote it, from which build and when', () {
      final data = _reparse(_full());

      expect(data.formatVersion, backupFormatVersion);
      expect(data.appVersion, '4.12.0+400001040');
      expect(data.exportedAt, DateTime.utc(2026, 8, 4, 9, 30));
    });
  });

  group('format version', () {
    test('accepts what it knows and refuses what came after it', () {
      expect(isSupportedBackupVersion(legacyBackupFormatVersion), isTrue);
      expect(isSupportedBackupVersion(backupFormatVersion), isTrue);
      expect(isSupportedBackupVersion(backupFormatVersion + 1), isFalse);
    });

    test('reads a file without a header as the version before the header', () {
      final data = SettingsData.fromJson(_legacy);

      expect(data.formatVersion, legacyBackupFormatVersion);
      expect(isSupportedBackupVersion(data.formatVersion), isTrue);
      expect(data.appVersion, isNull);
      expect(data.exportedAt, isNull);
    });
  });

  group('legacy file', () {
    test('imports the sections it does have', () {
      final data = SettingsData.fromJson(_legacy);

      expect(data.settings?['theme'], 'dark');
      expect(data.userSubscriptions?.single.screenName, 'reader');
      expect(data.tweets?.single.id, 't1');
    });

    test('restores nothing for the sections it never had', () {
      final data = SettingsData.fromJson(_legacy);
      final tables = backupTables(data, includeReadPositions: true);

      expect(data.pluginRows?['substackSubscriptions'], isNull);
      expect(data.pluginRows?['redditSubscriptions'], isNull);
      expect(data.retweetFilters, isNull);
      expect(data.feedReadPositions, isNull);
      expect(tables.keys, containsAll([tableSubscription, tableSavedTweet]));
      expect(tables, hasLength(2));
    });
  });

  group('tables', () {
    test('writes each section to its own table', () {
      final tables = backupTables(_full(), includeReadPositions: true);

      expect(tables.keys, containsAll(_expectedTables));
      expect(tables, hasLength(_expectedTables.length));
      expect(tables[tableRetweetFilter]?.single.toMap()['screen_name'], 'reader');
      expect(tables[tableSearchSubscriptionGroupMember]?.single.toMap()['search_id'], 'dart');
    });

    test('never carries a cache', () {
      final tables = backupTables(_full(), includeReadPositions: true);

      expect(tables.containsKey(tableTimelineCache), isFalse);
      expect(tables.containsKey(tableFeedGroupChunk), isFalse);
      expect(tables.containsKey(tableFeedGroupCursor), isFalse);
      expect(tables.containsKey(tablePostNotification), isFalse);
    });

    test('leaves reading positions out unless they were asked for', () {
      final refused = backupTables(_full(), includeReadPositions: false);
      final accepted = backupTables(_full(), includeReadPositions: true);

      expect(refused.containsKey(tableFeedReadPosition), isFalse);
      expect(accepted.containsKey(tableFeedReadPosition), isTrue);
    });
  });

  group('counts', () {
    test('adds up the sections behind one category', () {
      final counts = backupCounts(_full());

      expect(counts[BackupCategory.subscriptions], 2);
      expect(counts[BackupCategory.groupMembers], 2);
      expect(counts[BackupCategory.filters], 2);
      expect(counts[BackupCategory.readPositions], 1);
      expect(counts[BackupCategory.upvotes], 1);
      expect(counts[BackupCategory.threadsLikes], 1);
      expect(counts[BackupCategory.blueskyLikes], 1);
      expect(counts[BackupCategory.accounts], 1);
    });

    test('is empty when there is nothing to restore', () {
      expect(backupCounts(SettingsData()), isEmpty);
      expect(backupCounts(SettingsData(tweets: [], accounts: [])), isEmpty);
    });
  });
}

const _expectedTables = [
  tableSearchSubscription,
  tableSubscription,
  tableSubstackSubscription,
  tableRedditSubscription,
  tableStockSubscription,
  tableThreadsSubscription,
  tableBlueskySubscription,
  tableMastodonSubscription,
  tableRedditLocalVote,
  tableThreadsLocalLike,
  tableBlueskyLocalLike,
  tableSubscriptionGroup,
  tableSubscriptionGroupMember,
  tableSearchSubscriptionGroupMember,
  tableSavedTweet,
  tableSavedTweetFolder,
  tableLikedTweet,
  tableRetweetFilter,
  tableReplyFilter,
  tableAccounts,
  tableFeedReadPosition,
];
