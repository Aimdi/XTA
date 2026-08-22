import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:sqflite/sqflite.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/plugins/account_posts.dart';
import 'package:xta/plugins/plugin_feed_fresh.dart';
import 'package:xta/plugins/rss/rss_client.dart';
import 'package:xta/plugins/rss/rss_models.dart';
import 'package:xta/plugins/source_tables.dart';

class RssFeedSnapshot {
  final List<RssItem> items;
  final int failedCount;

  const RssFeedSnapshot({this.items = const [], this.failedCount = 0});
}

/// Followed feeds. Preferences are the copy the plugin tab reads; the table
/// exists so a group can join the same rows.
class RssFeedsStore extends Store<List<RssFeed>> {
  final BasePrefService prefs;

  RssFeedsStore(this.prefs) : super(const []);

  Future<void> load() async {
    await execute(() async {
      final fromPrefs = RssFeed.listFromPrefs(prefs.get(optionPluginRssFeeds));
      if (fromPrefs.isNotEmpty) {
        await _syncTable(fromPrefs);
        return fromPrefs;
      }
      final fromTable = await _readTable();
      if (fromTable.isNotEmpty) {
        await prefs.set(optionPluginRssFeeds, RssFeed.listToPrefs(fromTable));
      }
      return fromTable;
    });
  }

  Future<List<RssFeed>> _readTable() async {
    final database = await Repository.readOnly();
    return readRssFeedsTable(database);
  }

  Future<void> _syncTable(List<RssFeed> feeds) async {
    final database = await Repository.writable();
    await syncRssFeedsTable(database, feeds);
  }

  Future<void> add(RssFeed feed) async {
    await execute(() async {
      final next = [feed, ...state.where((e) => e.id != feed.id)];
      await prefs.set(optionPluginRssFeeds, RssFeed.listToPrefs(next));
      await _syncTable(next);
      return next;
    });
  }

  Future<void> remove(String id) async {
    await execute(() async {
      final next = [
        for (final feed in state)
          if (feed.id != id) feed,
      ];
      await prefs.set(optionPluginRssFeeds, RssFeed.listToPrefs(next));
      await _syncTable(next);
      return next;
    });
  }

  bool isFollowing(String id) => state.any((feed) => feed.id == id);
}

/// Prefs are the copy the plugin tab reads; the table is for groups.
/// A missing `rss_subscription` (58 never applied) must not take enable
/// or the first RSS frame down — prefs still have the follows.
Future<List<RssFeed>> readRssFeedsTable(DatabaseExecutor database) async {
  final rows = await querySourceTable(
    database,
    tableRssSubscription,
    sql: 'SELECT * FROM $tableRssSubscription ORDER BY name COLLATE NOCASE',
  );
  return [for (final row in rows) feedOf(RssSubscription.fromMap(row))];
}

Future<void> syncRssFeedsTable(
  DatabaseExecutor database,
  List<RssFeed> feeds,
) async {
  await mutateSourceTable(tableRssSubscription, () async {
    final keep = feeds.map((e) => e.id).toSet();
    final existing = await database.query(
      tableRssSubscription,
      columns: ['id'],
    );
    for (final row in existing) {
      final id = row['id'] as String?;
      if (id != null && !keep.contains(id)) {
        await database.delete(
          tableRssSubscription,
          where: 'id = ?',
          whereArgs: [id],
        );
        await database.delete(
          tableSubscriptionGroupMember,
          where: 'profile_id = ?',
          whereArgs: [id],
        );
      }
    }
    for (final feed in feeds) {
      await database.insert(
        tableRssSubscription,
        subscriptionOf(feed).toMap(),
        conflictAlgorithm: ConflictAlgorithm.replace,
      );
    }
  });
}

RssSubscription subscriptionOf(RssFeed feed) => RssSubscription(
  id: feed.id,
  feedUrl: feed.feedUrl,
  name: feed.name,
  siteUrl: feed.siteUrl,
  iconUrl: feed.iconUrl,
  createdAt: DateTime.now(),
  inFeed: true,
);

RssFeed feedOf(RssSubscription subscription) => RssFeed(
  id: subscription.id,
  feedUrl: subscription.feedUrl,
  name: subscription.name,
  siteUrl: subscription.siteUrl,
  iconUrl: subscription.iconUrl,
);

class RssReadStore extends Store<Set<String>> {
  final BasePrefService prefs;

  RssReadStore(this.prefs) : super(const {});

  Future<void> load() async {
    await execute(
      () async => readIdsFromPrefs(prefs.get(optionPluginRssReadIds)).toSet(),
    );
  }

  bool isRead(String id) => state.contains(id);

  Future<void> markRead(String id) async {
    if (id.isEmpty || state.contains(id)) return;
    await execute(() async {
      final next = [id, ...state].take(rssReadIdsCap).toList();
      await prefs.set(optionPluginRssReadIds, readIdsToPrefs(next));
      return next.toSet();
    });
  }

  Future<void> markAllRead(Iterable<String> ids) async {
    final fresh = ids
        .where((id) => id.isNotEmpty && !state.contains(id))
        .toList();
    if (fresh.isEmpty) return;
    await execute(() async {
      final next = [...fresh, ...state].take(rssReadIdsCap).toList();
      await prefs.set(optionPluginRssReadIds, readIdsToPrefs(next));
      return next.toSet();
    });
  }
}

class RssTagsStore extends Store<Map<String, List<String>>> {
  final BasePrefService prefs;

  RssTagsStore(this.prefs) : super(const {});

  Future<void> load() async {
    await execute(() async => rssTagsFromPrefs(prefs.get(optionPluginRssTags)));
  }

  List<String> tagsFor(String feedId) => state[feedId] ?? const [];

  List<String> get allTags {
    final tags = <String>{};
    for (final list in state.values) {
      tags.addAll(list);
    }
    final sorted = tags.toList()..sort();
    return sorted;
  }

  Future<void> setTags(String feedId, List<String> tags) async {
    await execute(() async {
      final next = Map<String, List<String>>.from(state);
      final cleaned = [
        for (final tag in tags)
          if (tag.trim().isNotEmpty) tag.trim(),
      ];
      if (cleaned.isEmpty) {
        next.remove(feedId);
      } else {
        next[feedId] = cleaned;
      }
      await prefs.set(optionPluginRssTags, rssTagsToPrefs(next));
      return next;
    });
  }
}

class RssTimelineStore extends Store<RssFeedSnapshot> {
  final RssClient client;
  final RssFeedsStore feeds;

  var _allItems = const <RssItem>[];
  var _filter = RssFeedFilter.all;
  Set<String> _readIds = const {};
  String? _tag;
  Map<String, List<String>> _tagsByFeed = const {};
  DateTime? _fetchedAt;

  RssTimelineStore(this.client, this.feeds) : super(const RssFeedSnapshot());

  RssFeedFilter get filter => _filter;
  String? get tag => _tag;
  List<RssItem> get allItems => _allItems;
  DateTime? get fetchedAt => _fetchedAt;

  Future<void> refresh({bool force = false}) async {
    if (feeds.state.isEmpty) {
      if (_allItems.isNotEmpty || state.items.isNotEmpty) {
        _allItems = const [];
        update(const RssFeedSnapshot());
      }
      _fetchedAt ??= DateTime.now();
      return;
    }
    if (!force &&
        _allItems.isNotEmpty &&
        pluginFeedIsFresh(_fetchedAt, ttl: kAccountPostsCacheTtl)) {
      return;
    }
    if (_allItems.isNotEmpty) {
      try {
        update(await _fetch());
      } catch (_) {
        update(state);
      }
      return;
    }
    await execute(_fetch);
  }

  void setFilter(RssFeedFilter filter, Set<String> readIds) {
    _filter = filter;
    _readIds = readIds;
    update(_snapshot(failedCount: state.failedCount));
  }

  void setTag(String? tag) {
    _tag = tag;
    update(_snapshot(failedCount: state.failedCount));
  }

  void syncReadIds(Set<String> readIds) {
    _readIds = readIds;
    if (_filter == RssFeedFilter.unread) {
      update(_snapshot(failedCount: state.failedCount));
    }
  }

  void syncTags(Map<String, List<String>> tags) {
    _tagsByFeed = tags;
    update(_snapshot(failedCount: state.failedCount));
  }

  Future<RssFeedSnapshot> _fetch() async {
    final followed = feeds.state;
    if (followed.isEmpty) {
      _allItems = const [];
      return const RssFeedSnapshot();
    }

    final results = await Future.wait(
      followed.map((feed) async {
        try {
          return (items: await client.fetchItems(feed), failed: false);
        } catch (_) {
          return (items: const <RssItem>[], failed: true);
        }
      }),
    );

    _allItems = mergeRssItems(const [], results.expand((e) => e.items));
    _fetchedAt = DateTime.now();
    return _snapshot(failedCount: results.where((e) => e.failed).length);
  }

  RssFeedSnapshot _snapshot({required int failedCount}) {
    return RssFeedSnapshot(
      items: [
        for (final item in _allItems)
          if (itemMatchesRssFilter(
            item,
            _filter,
            _readIds,
            tag: _tag,
            tagsByFeed: _tagsByFeed,
          ))
            item,
      ],
      failedCount: failedCount,
    );
  }
}

class RssAddFeedStore extends Store<RssFeed?> {
  final RssClient client;

  RssAddFeedStore(this.client) : super(null);

  Future<RssFeed> lookup(String input) async {
    late final RssFeed feed;
    await execute(() async {
      feed = await client.lookup(input);
      return feed;
    });
    return feed;
  }
}
