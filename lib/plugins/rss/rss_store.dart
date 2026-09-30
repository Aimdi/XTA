import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:sqflite/sqflite.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/group/future_pool.dart';
import 'package:xta/plugins/account_posts.dart';
import 'package:xta/plugins/plugin_feed_fresh.dart';
import 'package:xta/plugins/rss/rss_client.dart';
import 'package:xta/plugins/rss/rss_models.dart';
import 'package:xta/plugins/rss/rss_opml.dart';
import 'package:xta/plugins/source_tables.dart';
import 'package:xta/reading/reader_preference_writes.dart';
import 'package:xta/utils/read_retry.dart';

/// How many feeds are read at once, so a long imported list cannot open a request per feed together.
const rssFetchConcurrency = 6;

class RssFeedSnapshot {
  final List<RssItem> items;
  final int failedCount;

  const RssFeedSnapshot({this.items = const [], this.failedCount = 0});
}

/// What an OPML import did. [persisted] is false when the follows could not be saved; [tableSynced] is false when
/// they were saved but groups could not see them yet, which [RssFeedsStore.retryTableSync] repairs. [tags] holds the
/// folders and categories of the feeds just added, by feed id.
class RssImportResult {
  final int imported;
  final int duplicates;
  final int skipped;
  final bool persisted;
  final bool tableSynced;
  final Map<String, List<String>> tags;
  const RssImportResult({
    required this.imported,
    required this.duplicates,
    required this.skipped,
    required this.persisted,
    required this.tableSynced,
    this.tags = const {},
  });

  /// Nothing was saved, because the followed feeds could not be read.
  RssImportResult.unsaved(RssOpmlDocument document)
    : this(
        imported: 0,
        duplicates: document.duplicates,
        skipped: document.skipped,
        persisted: false,
        tableSynced: false,
      );
}

String _addressOf(RssFeed feed) => rssFeedAddressKey(feed.feedUrl);

/// Where groups find followed feeds: the `rss_subscription` table.
class RssFeedsTable {
  const RssFeedsTable();

  Future<List<RssFeed>> read() async => readRssFeedsTable(await Repository.readOnly());

  /// All or nothing: a failure leaves the table and group memberships as they were.
  Future<void> sync(List<RssFeed> feeds) async {
    final database = await Repository.writable();
    await database.transaction((transaction) => syncRssFeedsTable(transaction, feeds));
  }
}

/// Followed feeds. Preferences are the copy the plugin tab reads; the table
/// exists so a group can join the same rows.
///
/// Loading, following, unfollowing and importing run one at a time, each from the latest saved follows, so an
/// import during the first load cannot drop a follow. A refused save changes nothing, and nothing is written while
/// the saved follows cannot be read.
class RssFeedsStore extends Store<List<RssFeed>> {
  final BasePrefService prefs;
  final RssFeedsTable table;
  Future<void> _tail = Future.value();
  bool _loaded = false;
  bool _tableSyncPending = false;

  RssFeedsStore(this.prefs, {this.table = const RssFeedsTable()}) : super(const []);

  /// The follows are saved but groups could not be updated with them yet.
  bool get tableSyncPending => _tableSyncPending;

  Future<T> _serial<T>(Future<T> Function() work) {
    final result = _tail.then((_) => work());
    _tail = result.then<void>((_) {}, onError: (Object _) {});
    return result;
  }

  Future<void> load() => _serial(() => execute(_readFollows));

  Future<List<RssFeed>> _readFollows() async {
    final fromPrefs = RssFeed.listFromPrefs(prefs.get(optionPluginRssFeeds));
    if (fromPrefs.isNotEmpty) {
      await _syncTable(fromPrefs);
      _loaded = true;
      return fromPrefs;
    }
    final fromTable = await table.read();
    if (fromTable.isNotEmpty) {
      await prefs.set(optionPluginRssFeeds, RssFeed.listToPrefs(fromTable));
    }
    _loaded = true;
    return fromTable;
  }

  /// Follows as saved, reading them first when that has not happened yet; null when they cannot be read.
  Future<List<RssFeed>?> _current() async {
    try {
      if (!_loaded && state.isEmpty) update(await _readFollows());
      return state;
    } catch (_) {
      return null;
    }
  }

  /// The saved follows, once every change asked for before has finished.
  Future<List<RssFeed>> saved() =>
      _serial(() async => await _current() ?? (throw StateError('The followed feeds could not be read')));

  Future<bool> _syncTable(List<RssFeed> feeds) async {
    try {
      await table.sync(feeds);
      _tableSyncPending = false;
      return true;
    } catch (_) {
      _tableSyncPending = true;
      return false;
    }
  }

  Future<bool> _save(List<RssFeed> next) async {
    if (!await ReaderPreferenceWrites.putString(prefs, optionPluginRssFeeds, RssFeed.listToPrefs(next))) return false;
    update(List.unmodifiable(next));
    await _syncTable(next);
    return true;
  }

  /// [feed] as it will be stored: under its old id when its address is followed already, else under a free id.
  RssFeed _storedFor(RssFeed feed, List<RssFeed> current) {
    final same = current.where((other) => _addressOf(other) == _addressOf(feed)).firstOrNull;
    if (same == null) return allocateRssFeedIdentity(feed, rssFeedIdsInUse(current));
    return RssFeed(
      id: same.id,
      feedUrl: same.feedUrl,
      name: feed.name,
      siteUrl: feed.siteUrl ?? same.siteUrl,
      iconUrl: feed.iconUrl ?? same.iconUrl,
      description: feed.description ?? same.description,
    );
  }

  /// Follows [feed] and returns it as stored, or null when it could not be saved. Following an address again
  /// refreshes its details under its old id; another address that shares its usual id gets an id of its own.
  Future<RssFeed?> add(RssFeed feed) => _serial(() async {
    final current = await _current();
    if (current == null) return null;
    final stored = _storedFor(feed, current);
    return await _save([stored, ...current.where((other) => other.id != stored.id)]) ? stored : null;
  });

  /// Unfollows the feed with [id]; false when that could not be saved.
  Future<bool> remove(String id) => _serial(() async {
    final current = await _current();
    return current != null &&
        await _save([
          for (final feed in current)
            if (feed.id != id) feed,
        ]);
  });

  /// [current] followed by every feed in [document] that is not followed yet, and the tags the new ones bring.
  ({List<RssFeed> next, Map<String, List<String>> tags}) _merged(RssOpmlDocument document, List<RssFeed> current) {
    final known = {for (final feed in current) _addressOf(feed)};
    final taken = rssFeedIdsInUse(current);
    final next = [...current];
    final tags = <String, List<String>>{};
    for (final entry in document.feeds) {
      final key = rssFeedAddressKey(entry.url);
      if (!known.add(key)) continue;
      final feed = allocateRssFeedIdentity(
        RssFeed(id: rssFeedId(entry.url), feedUrl: entry.url, name: entry.title, siteUrl: entry.siteUrl),
        taken,
      );
      taken[feed.id] = key;
      next.add(feed);
      if (entry.tags.isNotEmpty) tags[feed.id] = entry.tags;
    }
    return (next: next, tags: tags);
  }

  /// Adds every feed in [document] that is not followed yet, in one save; followed feeds are left as they are.
  Future<RssImportResult> importOpml(RssOpmlDocument document) => _serial(() async {
    final current = await _current();
    if (current == null) return RssImportResult.unsaved(document);
    final (:next, :tags) = _merged(document, current);
    final imported = next.length - current.length;
    final persisted = imported == 0 || await _save(next);
    return RssImportResult(
      imported: persisted ? imported : 0,
      duplicates: document.duplicates + document.feeds.length - imported,
      skipped: document.skipped,
      persisted: persisted,
      tableSynced: persisted && !_tableSyncPending,
      tags: persisted ? tags : const {},
    );
  });

  /// Tries again to show saved follows to groups.
  Future<bool> retryTableSync() => _serial(() async {
    final current = await _current();
    return current != null && await _syncTable(current);
  });

  /// The followed feed at [url], matched the way duplicates are.
  RssFeed? followedFeed(String url) {
    final address = rssFeedAddressKey(url);
    return state.where((feed) => _addressOf(feed) == address).firstOrNull;
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

  /// Gives each feed in [tags] those tags unless it already has some, in one save; false when that could not be
  /// saved. Reads the saved tags first, so it is safe before [load].
  Future<bool> adoptTags(Map<String, List<String>> tags) async {
    final saved = rssTagsFromPrefs(prefs.get(optionPluginRssTags));
    final next = {
      ...saved,
      for (final MapEntry(key: id, value: list) in tags.entries)
        if (!saved.containsKey(id) && list.isNotEmpty) id: list,
    };
    if (next.length == saved.length) return true;
    if (!await ReaderPreferenceWrites.putString(prefs, optionPluginRssTags, rssTagsToPrefs(next))) return false;
    update(next);
    return true;
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

  Future<RssFeedSnapshot> _fetch() => withReadRetryBudget(() async {
    final followed = feeds.state;
    if (followed.isEmpty) {
      _allItems = const [];
      return const RssFeedSnapshot();
    }

    final results = await mapWithConcurrency(followed, rssFetchConcurrency, (feed) async {
      try {
        return (items: await client.fetchItems(feed), failed: false);
      } catch (_) {
        return (items: const <RssItem>[], failed: true);
      }
    });

    _allItems = mergeRssItems(const [], results.expand((e) => e.items));
    _fetchedAt = DateTime.now();
    return _snapshot(failedCount: results.where((e) => e.failed).length);
  });

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
