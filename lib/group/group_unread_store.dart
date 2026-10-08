import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:sqflite/sqflite.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart'
    show SearchSubscription, UserSubscription;
import 'package:xta/database/repository.dart';
import 'package:xta/group/feed_cache.dart';
import 'package:xta/group/feed_catch_up.dart';
import 'package:xta/group/feed_chunk_hash.dart';
import 'package:xta/group/feed_read_position.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/group/group_screen.dart';
import 'package:xta/group/group_unread.dart';
import 'package:xta/home/home_feed_unread.dart';

/// Ids of groups whose cached X chunks are newer than the last-read mark.
class GroupUnreadStore extends Store<Set<String>> {
  GroupUnreadStore(this.prefs) : super(const {});

  final BasePrefService prefs;

  Future<void> reload() => execute(_load);

  Future<Set<String>> _load() async {
    try {
      return await _computeUnread();
    } catch (_) {
      return const {};
    }
  }

  Future<Set<String>> _computeUnread() async {
    final database = await Repository.readOnly();
    final flags = await _groupFlags(database);
    final lastRead = await _lastReadByGroup(database);
    final newestByHash = await _newestByHash(database);
    final tracking = prefs.get(optionFeedReadingPosition) == true;
    final replies = prefs.get(optionGlobalIncludeReplies) ?? true;
    final retweets = prefs.get(optionGlobalIncludeRetweets) ?? true;
    return {
      ...await _boardUnread(
        database,
        flags: flags,
        lastRead: lastRead,
        newestByHash: newestByHash,
        tracking: tracking,
        replies: replies,
        retweets: retweets,
      ),
      if (await _followingUnread(
        database,
        lastRead: lastRead,
        newestByHash: newestByHash,
        globalReadingPosition: tracking,
        globalIncludeReplies: replies,
        globalIncludeRetweets: retweets,
      ))
        feedKeyFollowing,
      if (groupHasUnread(
        tracksReadPosition: tracking,
        newestCachedAt: forYouNewestCachedAt(prefs),
        lastReadAt: lastRead[feedKeyForYou],
      ))
        feedKeyForYou,
    };
  }

  Future<Set<String>> _boardUnread(
    Database database, {
    required Map<String, _GroupFlags> flags,
    required Map<String, DateTime> lastRead,
    required Map<String, DateTime> newestByHash,
    required bool tracking,
    required bool replies,
    required bool retweets,
  }) async {
    return unreadGroupIds(
      groupIds: flags.keys,
      parentOf: await readGroupParents(database),
      members: await _memberRows(database),
      includeRepliesByGroup: {
        for (final e in flags.entries) e.key: e.value.includeReplies,
      },
      includeRetweetsByGroup: {
        for (final e in flags.entries) e.key: e.value.includeRetweets,
      },
      globalIncludeReplies: replies,
      globalIncludeRetweets: retweets,
      globalReadingPosition: tracking,
      catchUpGroupIds: {
        for (final id in flags.keys)
          if (prefs.get(feedCatchUpModeKey(id)) == true) id,
      },
      popularGroupIds: {
        for (final e in flags.entries)
          if (e.value.popular) e.key,
      },
      lastReadByGroup: lastRead,
      newestByHash: newestByHash,
    );
  }

  Future<bool> _followingUnread(
    Database database, {
    required Map<String, DateTime> lastRead,
    required Map<String, DateTime> newestByHash,
    required bool globalReadingPosition,
    required bool globalIncludeReplies,
    required bool globalIncludeRetweets,
  }) async {
    final flags = await _allGroupFlags(database);
    if (flags == null) {
      return false;
    }
    return followingHasUnread(
      inFeedUsers: await _followingMembers(database),
      includeReplies: flags.includeReplies ?? globalIncludeReplies,
      includeRetweets: flags.includeRetweets ?? globalIncludeRetweets,
      tracksReadPosition: tracksGroupReadPosition(
        popular: flags.popular,
        globalReadingPosition: globalReadingPosition,
        catchUp: prefs.get(feedCatchUpModeKey(legacyFeedKeyFollowing)) == true,
      ),
      lastReadAt: lastReadForFollowing(lastRead),
      newestByHash: newestByHash,
    );
  }
}

class _GroupFlags {
  final bool? includeReplies;
  final bool? includeRetweets;
  final bool popular;

  const _GroupFlags({
    required this.includeReplies,
    required this.includeRetweets,
    required this.popular,
  });
}

Future<Map<String, _GroupFlags>> _groupFlags(Database database) async {
  final rows = await database.query(
    tableSubscriptionGroup,
    columns: ['id', 'include_replies', 'include_retweets', 'popular'],
  );
  return {
    for (final row in rows)
      if (row['id'] != '-1')
        row['id'] as String: _GroupFlags(
          includeReplies: includeOverride(row['include_replies']),
          includeRetweets: includeOverride(row['include_retweets']),
          popular: row['popular'] == 1,
        ),
  };
}

Future<_GroupFlags?> _allGroupFlags(Database database) async {
  final rows = await database.query(
    tableSubscriptionGroup,
    columns: ['include_replies', 'include_retweets', 'popular'],
    where: 'id = ?',
    whereArgs: [legacyFeedKeyFollowing],
  );
  if (rows.isEmpty) {
    return null;
  }
  final row = rows.first;
  return _GroupFlags(
    includeReplies: includeOverride(row['include_replies']),
    includeRetweets: includeOverride(row['include_retweets']),
    popular: row['popular'] == 1,
  );
}

Future<List<FeedChunkMember>> _followingMembers(Database database) async {
  final rows = await database.query(tableSubscription);
  return followingChunkMembers([
    for (final row in rows)
      if (UserSubscription.fromMap(row) case final user)
        (id: user.id, createdAt: user.createdAt, inFeed: user.inFeed),
  ]);
}

Future<List<FeedMemberRow>> _memberRows(Database database) async {
  final users = await database.rawQuery(
    'SELECT sgm.group_id, s.id, s.created_at FROM $tableSubscription s '
    'INNER JOIN $tableSubscriptionGroupMember sgm ON sgm.profile_id = s.id',
  );
  final searches = await database.rawQuery(
    'SELECT sgm.group_id, s.id, s.created_at FROM $tableSearchSubscription s '
    'INNER JOIN $tableSubscriptionGroupMember sgm ON sgm.profile_id = s.id',
  );
  return [
    for (final row in users) _row(row, search: false),
    for (final row in searches) _row(row, search: true),
  ];
}

FeedMemberRow _row(Map<String, Object?> row, {required bool search}) {
  // Same `created_at` parse as the subscription entities the live feed sorts.
  final createdAt = search
      ? SearchSubscription.fromMap(row).createdAt
      : (row['created_at'] == null
            ? DateTime.now()
            : DateTime.parse(row['created_at'] as String));
  return FeedMemberRow(
    groupId: row['group_id'] as String,
    id: row['id'] as String,
    createdAt: createdAt,
    search: search,
  );
}

Future<Map<String, DateTime>> _lastReadByGroup(Database database) async {
  final rows = await database.query(
    tableFeedReadPosition,
    columns: ['group_id', 'updated_at'],
  );
  return _timesBy(rows, 'group_id', 'updated_at');
}

Future<Map<String, DateTime>> _newestByHash(Database database) async {
  final rows = await database.rawQuery(
    'SELECT hash, MAX(created_at) AS newest FROM $tableFeedGroupChunk '
    'GROUP BY hash',
  );
  return _timesBy(rows, 'hash', 'newest');
}

Map<String, DateTime> _timesBy(
  Iterable<Map<String, Object?>> rows,
  String key,
  String time,
) {
  final out = <String, DateTime>{};
  for (final row in rows) {
    final at = parseChunkTimestamp(row[time]);
    if (at != null) {
      out[row[key] as String] = at;
    }
  }
  return out;
}

/// Rebuilds [builder] when unread ids change. Missing store → no dots.
class GroupUnreadScope extends StatelessWidget {
  final Widget Function(BuildContext context, Set<String> unreadIds) builder;

  const GroupUnreadScope({super.key, required this.builder});

  @override
  Widget build(BuildContext context) {
    final store = maybeGroupUnreadStore(context);
    if (store == null) {
      return builder(context, const {});
    }
    return ScopedBuilder<GroupUnreadStore, Set<String>>(
      store: store,
      onState: builder,
    );
  }
}

/// [GroupUnreadStore] when the app provided one; null in widget tests.
GroupUnreadStore? maybeGroupUnreadStore(BuildContext context) {
  try {
    return Provider.of<GroupUnreadStore>(context, listen: false);
  } on ProviderNotFoundException {
    return null;
  }
}

/// Opens a group feed and refreshes unread marks when the reader comes back.
Future<void> openGroupAndRefreshUnread(
  BuildContext context, {
  required String id,
  required String name,
}) async {
  await Navigator.pushNamed(
    context,
    routeGroup,
    arguments: GroupScreenArguments(id: id, name: name),
  );
  if (!context.mounted) {
    return;
  }
  await maybeGroupUnreadStore(context)?.reload();
}
