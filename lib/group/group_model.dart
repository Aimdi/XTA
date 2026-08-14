import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_iconpicker/flutter_iconpicker.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/group/custom_feed_rules.dart';
import 'package:xta/group/group_tree.dart';
import 'package:xta/subscriptions/group_mark_style.dart';
import 'package:xta/subscriptions/group_ungrouped.dart';
import 'package:logging/logging.dart';
import 'package:pref/pref.dart';
import 'package:sqflite/sqflite.dart';
import 'package:uuid/uuid.dart';

var defaultGroupIcon = '{"pack":"custom","key":"rss_feed"}';

IconData deserializeIconData(String iconData) {
  try {
    var icon = deserializeIcon(jsonDecode(iconData));
    if (icon != null) {
      return icon.data;
    }
  } catch (e) {
    // Simply ignore this exception (failed to deserialize icon)
  }

  // Use this as a default;
  return Icons.rss_feed;
}

/// Every group's parent, keyed by group id, for the nesting helpers in
/// `group_tree.dart`. A group that stands on its own maps to null.
Future<Map<String, String?>> readGroupParents(DatabaseExecutor database) async {
  try {
    final rows = await database.query(
      tableSubscriptionGroup,
      columns: ['id', 'parent_id'],
    );

    return {
      for (final row in rows) row['id'] as String: row['parent_id'] as String?,
    };
  } catch (e) {
    // The column is added by a migration that is allowed to fail on a damaged
    // database. Without it nothing nests, which is the old behaviour — far
    // better than every group feed refusing to load.
    return const {};
  }
}

class GroupModel extends Store<SubscriptionGroupGet> {
  final String id;

  /// Other groups being read alongside this one, for as long as the reader
  /// wants them together. Their members join this group's feed; nothing about
  /// either group is changed.
  final Set<String> alsoRead;

  GroupModel(this.id, {this.alsoRead = const {}})
    : super(
        SubscriptionGroupGet(
          id: '',
          name: '',
          icon: defaultGroupIcon,
          subscriptions: [],
          includeRetweets: false,
          includeReplies: false,
          popular: false,
          custom: false,
          contentFilter: contentFilterDefault,
        ),
      );

  Future<void> loadGroup({bool showLoading = true}) async {
    // Soft reloads (membership change while the feed is open) must not flip
    // Triple into loading — ScopedBuilder.transition would swap the timeline
    // for a skeleton and wipe scroll. First open still uses execute().
    if (!showLoading) {
      update(await _readGroup());
      return;
    }
    await execute(_readGroup);
  }

  Future<SubscriptionGroupGet> _readGroup() async {
    var database = await Repository.readOnly();

    var group = (await database.query(
      tableSubscriptionGroup,
      where: 'id = ?',
      whereArgs: [id],
    )).first;

    if (id == '-1') {
      var subscriptions = (await database.query(
        tableSubscription,
      )).map((e) => UserSubscription.fromMap(e)).toList(growable: false);

      return SubscriptionGroupGet(
        id: '-1',
        name: 'All',
        icon: group['icon'] as String,
        subscriptions: subscriptions,
        includeReplies: _includeOverride(group['include_replies']),
        includeRetweets: _includeOverride(group['include_retweets']),
        popular: group['popular'] == 1,
        custom: group['custom'] == 1,
        contentFilter:
            group['content_filter'] as String? ?? contentFilterDefault,
        minLikes: (group['min_likes'] as int?) ?? 0,
        minRetweets: (group['min_retweets'] as int?) ?? 0,
        mutedKeywords: parseMutedKeywordsStored(
          group['muted_keywords'] as String?,
        ),
      );
    }

    // A group's feed is its own members plus everything nested inside it, so
    // the membership queries ask for a set of group ids rather than one — and
    // reading several groups together is the same question asked of more
    // roots, which is why it costs nothing here.
    final parents = await readGroupParents(database);
    final ids = {
      ...groupAndDescendants(id, parents),
      for (final other in alsoRead) ...groupAndDescendants(other, parents),
    }.toList(growable: false);
    final placeholders = List.filled(ids.length, '?').join(', ');

    // The membership queries are independent of each other; issued together
    // instead of one after another, since this runs on every shell mount and
    // every debounced reload.
    String membership(String table) =>
        'SELECT DISTINCT s.* FROM $table s LEFT JOIN $tableSubscriptionGroupMember sgm ON sgm.profile_id = s.id WHERE sgm.group_id IN ($placeholders) ORDER BY s.id';

    // The X tables, then every plugin that says its followed accounts are
    // subscriptions — read from the registry rather than named here.
    final sources = subscriptionSources;
    final rows = await Future.wait([
      database.rawQuery(membership(tableSearchSubscription), ids),
      database.rawQuery(membership(tableSubscription), ids),
      for (final source in sources)
        database.rawQuery(membership(source.subscriptionTable), ids),
    ]);

    final members = <Subscription>[
      ...rows[1].map(UserSubscription.fromMap),
      ...rows[0].map(SearchSubscription.fromMap),
      for (final (index, source) in sources.indexed)
        ...rows[index + 2].map(source.subscriptionFromMap),
    ];

    // TODO: Factory
    return SubscriptionGroupGet(
      id: group['id'] as String,
      name: group['name'] as String,
      icon: group['icon'] as String,
      subscriptions: members,
      includeReplies: _includeOverride(group['include_replies']),
      includeRetweets: _includeOverride(group['include_retweets']),
      popular: group['popular'] == 1,
      custom: group['custom'] == 1,
      contentFilter: group['content_filter'] as String? ?? contentFilterDefault,
      minLikes: (group['min_likes'] as int?) ?? 0,
      minRetweets: (group['min_retweets'] as int?) ?? 0,
      mutedKeywords: parseMutedKeywordsStored(
        group['muted_keywords'] as String?,
      ),
    );
  }

  // Reads the stored per-group override: null (unset) means "follow the global
  // default", otherwise the explicit on/off the user chose for this feed.
  static bool? _includeOverride(Object? value) =>
      value == null ? null : value == 1;

  Future<void> toggleSubscriptionGroupIncludeReplies(bool? value) async {
    await execute(() async {
      (await Repository.writable()).rawUpdate(
        'UPDATE $tableSubscriptionGroup SET include_replies = ? WHERE id = ?',
        [value, state.id],
      );
      return state.copyWith(includeReplies: value);
    });
  }

  Future<void> toggleSubscriptionGroupIncludeRetweets(bool? value) async {
    await execute(() async {
      (await Repository.writable()).rawUpdate(
        'UPDATE $tableSubscriptionGroup SET include_retweets = ? WHERE id = ?',
        [value, state.id],
      );
      return state.copyWith(includeRetweets: value);
    });
  }

  Future<void> toggleSubscriptionGroupPopular(bool value) async {
    await execute(() async {
      (await Repository.writable()).rawUpdate(
        'UPDATE $tableSubscriptionGroup SET popular = ?, custom = 0 WHERE id = ?',
        [value, state.id],
      );
      return state.copyWith(popular: value, custom: false);
    });
  }

  Future<void> toggleSubscriptionGroupCustom(bool value) async {
    await execute(() async {
      (await Repository.writable()).rawUpdate(
        'UPDATE $tableSubscriptionGroup SET custom = ?, popular = 0 WHERE id = ?',
        [value, state.id],
      );
      return state.copyWith(custom: value, popular: false);
    });
  }

  /// Custom-feed engagement thresholds. 0 turns a threshold off.
  Future<void> setSubscriptionGroupMinLikes(int value) async {
    await _updateCustomRule('min_likes', value < 0 ? 0 : value);
    update(state.copyWith(minLikes: value < 0 ? 0 : value));
  }

  Future<void> setSubscriptionGroupMinRetweets(int value) async {
    await _updateCustomRule('min_retweets', value < 0 ? 0 : value);
    update(state.copyWith(minRetweets: value < 0 ? 0 : value));
  }

  Future<void> setSubscriptionGroupMutedKeywords(
    List<MutedKeyword> keywords,
  ) async {
    await _updateCustomRule(
      'muted_keywords',
      keywords.isEmpty ? null : encodeMutedKeywordsStored(keywords),
    );
    update(state.copyWith(mutedKeywords: keywords));
  }

  Future<void> _updateCustomRule(String column, Object? value) async {
    final database = await Repository.writable();
    await database.update(
      tableSubscriptionGroup,
      {column: value},
      where: 'id = ?',
      whereArgs: [state.id],
    );
  }

  Future<void> setSubscriptionGroupContentFilter(String value) async {
    await execute(() async {
      (await Repository.writable()).rawUpdate(
        'UPDATE $tableSubscriptionGroup SET content_filter = ? WHERE id = ?',
        [value, state.id],
      );
      return state.copyWith(contentFilter: value);
    });
  }
}

class GroupsModel extends Store<List<SubscriptionGroup>> {
  static final log = Logger('GroupModel');

  final BasePrefService prefs;
  final Map<String, VoidCallback> _onGroupsReloaded = {};

  GroupsModel(this.prefs) : super([]);

  void addReloadListener(String key, VoidCallback callback) {
    _onGroupsReloaded[key] = callback;
  }

  void removeReloadListener(String key) {
    _onGroupsReloaded.remove(key);
  }

  bool get orderGroupsAscending =>
      prefs.get(optionSubscriptionGroupsOrderByAscending);
  String get orderGroupsBy => prefs.get(optionSubscriptionGroupsOrderByField);

  Future<void> deleteGroup(String id) async {
    log.info('Deleting the group $id');

    await execute(() async {
      var database = await Repository.writable();

      await database.delete(
        tableSubscriptionGroupMember,
        where: 'group_id = ?',
        whereArgs: [id],
      );
      await database.delete(
        tableSubscriptionGroup,
        where: 'id = ?',
        whereArgs: [id],
      );

      return state.where((e) => e.id != id).toList();
    });
  }

  /// [notifyReload] is false when only the groups' own order or pin state
  /// changed. The listeners rebuild feeds whose membership moved; reordering
  /// the groups board used to drop every cached feed in the session.
  Future reloadGroups({bool notifyReload = true}) async {
    log.info('Listing subscriptions groups');

    await execute(() async {
      var database = await Repository.readOnly();

      var orderByDirection = orderGroupsAscending
          ? 'COLLATE NOCASE ASC'
          : 'COLLATE NOCASE DESC';

      // NSFW groups sink to the bottom; pinned still float within each block.
      // Manual order sorts on the persisted position column.
      var orderBy = orderGroupsBy == 'position'
          ? 'g.position ${orderGroupsAscending ? 'ASC' : 'DESC'}'
          : 'g.$orderGroupsBy $orderByDirection';

      var query =
          "SELECT g.id, g.name, g.icon, g.color, g.created_at, g.pinned, g.nsfw, g.emoji, g.mark_style, g.parent_id, COUNT(gm.profile_id) AS number_of_members FROM $tableSubscriptionGroup g LEFT JOIN $tableSubscriptionGroupMember gm ON gm.group_id = g.id WHERE g.id != '-1' GROUP BY g.id ORDER BY g.nsfw ASC, g.pinned DESC, $orderBy";

      var groups = (await database.rawQuery(
        query,
      )).map((e) => SubscriptionGroup.fromMap(e)).toList(growable: false);
      var previews = await _loadMemberPreviews(database);

      return groups
          .map((g) => g.withMemberPreviews(previews[g.id] ?? const []))
          .toList(growable: false);
    });

    if (notifyReload) {
      for (final callback in _onGroupsReloaded.values) {
        callback();
      }
    }
  }

  /// How many members each group tile previews.
  static const _avatarPreviewCount = 4;

  /// The first few members of every group, for the tile avatar mosaic.
  ///
  /// One query for all groups (never N+1). Members without a picture are
  /// deliberately included — they render as a deterministic monogram, which is
  /// far better than a group of avatar-less accounts showing nothing at all.
  ///
  /// Deliberately NOT a `ROW_NUMBER() OVER (PARTITION BY ...)` single query:
  /// window functions need SQLite >= 3.25, and this app's minSdk 24 reaches
  /// Android 7 devices whose bundled SQLite predates that. The per-group cut is
  /// therefore taken in Dart.
  Future<Map<String, List<GroupMemberPreview>>> _loadMemberPreviews(
    DatabaseExecutor database,
  ) async {
    final previews = <String, List<GroupMemberPreview>>{};

    void add(String groupId, GroupMemberPreview preview) {
      final list = previews.putIfAbsent(groupId, () => <GroupMemberPreview>[]);
      if (list.length < _avatarPreviewCount) {
        list.add(preview);
      }
    }

    final rows = await database.rawQuery(
      'SELECT gm.group_id, s.id, s.name, s.screen_name, s.profile_image_url_https FROM $tableSubscriptionGroupMember gm '
      'JOIN $tableSubscription s ON s.id = gm.profile_id '
      'ORDER BY gm.group_id, s.screen_name COLLATE NOCASE',
    );

    for (final row in rows) {
      final screenName = row['screen_name'] as String?;
      add(
        row['group_id'] as String,
        GroupMemberPreview(
          id: row['id'] as String,
          name: (row['name'] as String?) ?? screenName ?? '',
          avatarUrl: row['profile_image_url_https'] as String?,
        ),
      );
    }

    // Plugin members are members too, and a group made only of them used to have
    // no cover at all — the join named the Reddit table and nothing else, so a
    // group of Threads, Bluesky or Fediverse accounts came up blank despite
    // every one of them storing an avatar. They come after the X accounts so a
    // mixed group still leads with faces.
    for (final source in subscriptionSources) {
      final rows = await database.rawQuery(
        'SELECT gm.group_id, s.* FROM $tableSubscriptionGroupMember gm '
        'JOIN ${source.subscriptionTable} s ON s.id = gm.profile_id '
        'ORDER BY gm.group_id, s.name COLLATE NOCASE',
      );

      for (final row in rows) {
        add(
          row['group_id'] as String,
          source.previewOf(source.subscriptionFromMap(row)),
        );
      }
    }

    return previews;
  }

  /// Makes the global replies/reposts default apply to every group again by
  /// clearing each group's own choice.
  ///
  /// Deliberately not called when the global switches change: a default that
  /// overwrites explicit per-feed choices is not a default. This runs only from
  /// the "apply to all feeds" action.
  Future<void> clearIncludeOverrides({required bool replies}) async {
    final database = await Repository.writable();
    final column = replies ? 'include_replies' : 'include_retweets';

    await database.rawUpdate(
      'UPDATE $tableSubscriptionGroup SET $column = NULL',
    );
    await reloadGroups();
  }

  /// How many groups keep their own replies / reposts choice instead of
  /// following the global default.
  ///
  /// Worth surfacing because the columns were originally created with
  /// `DEFAULT true`: groups made before the default existed hold an explicit
  /// value, so the global switch would appear to do nothing for them.
  Future<({int replies, int retweets})> countIncludeOverrides() async {
    final database = await Repository.readOnly();
    final rows = await database.rawQuery(
      'SELECT COUNT(include_replies) AS replies, COUNT(include_retweets) AS retweets '
      "FROM $tableSubscriptionGroup WHERE id != '-1'",
    );

    final row = rows.isEmpty ? const <String, Object?>{} : rows.first;
    return (
      replies: (row['replies'] as int?) ?? 0,
      retweets: (row['retweets'] as int?) ?? 0,
    );
  }

  Future<List<SubscriptionGroupMember>> listGroupMembers() async {
    var database = await Repository.readOnly();

    return (await database.query(tableSubscriptionGroupMember))
        .map(
          (e) => SubscriptionGroupMember(
            group: e['group_id'] as String,
            profile: e['profile_id'] as String,
          ),
        )
        .toList(growable: false);
  }

  Future<List<String>> listGroupsForUser(String user) async {
    var database = await Repository.readOnly();

    return (await database.query(
      tableSubscriptionGroupMember,
      columns: ['group_id'],
      where: 'profile_id = ?',
      whereArgs: [user],
    )).map((e) => e['group_id'] as String).toList(growable: false);
  }

  /// Places ungrouped accounts: new groups for suggestions, inserts for
  /// existing ones. One reload. Does not replace anyone already in a group.
  Future<int> applyUngroupedPlan(GroupUngroupedPlan plan) async {
    final database = await Repository.writable();
    var placed = 0;
    placed += await _insertSuggestedGroups(database, plan.suggest);
    placed += await _insertAssignments(database, plan.assign);
    await reloadGroups();
    return placed;
  }

  Future<int> _insertSuggestedGroups(
    Database database,
    List<SuggestedGroup> groups,
  ) async {
    var placed = 0;
    for (final group in groups) {
      if (group.accountIds.length < 2) continue;
      final id = const Uuid().v4();
      await database.insert(tableSubscriptionGroup, {
        'id': id,
        'name': group.name,
        'icon': defaultGroupIcon,
        'include_replies': null,
        'include_retweets': null,
        'mark_style': GroupMarkStyle.auto,
      });
      final batch = database.batch();
      for (final profile in group.accountIds) {
        batch.insert(tableSubscriptionGroupMember, {
          'group_id': id,
          'profile_id': profile,
        });
        placed++;
      }
      await batch.commit(noResult: true);
    }
    return placed;
  }

  Future<int> _insertAssignments(
    Database database,
    List<GroupAssignment> assign,
  ) async {
    if (assign.isEmpty) return 0;
    final batch = database.batch();
    for (final row in assign) {
      batch.insert(tableSubscriptionGroupMember, {
        'group_id': row.groupId,
        'profile_id': row.accountId,
      });
    }
    await batch.commit(noResult: true);
    return assign.length;
  }

  Future saveUserGroupMembership(String user, List<String> memberships) async {
    var database = await Repository.writable();

    var batch = database.batch();

    // First, clear all the memberships for the user
    batch.delete(
      tableSubscriptionGroupMember,
      where: 'profile_id = ?',
      whereArgs: [user],
    );

    // Then add all the new memberships
    for (var group in memberships) {
      batch.insert(tableSubscriptionGroupMember, {
        'group_id': group,
        'profile_id': user,
      });
    }

    await batch.commit();
    await reloadGroups();
  }

  Future<SubscriptionGroupEdit> loadGroupEdit(String? id) async {
    var database = await Repository.readOnly();

    if (id == null) {
      return SubscriptionGroupEdit(
        id: null,
        name: '',
        icon: defaultGroupIcon,
        color: null,
        members: <String>{},
        emoji: null,
        markStyle: GroupMarkStyle.auto,
      );
    }

    var group = await database.query(
      tableSubscriptionGroup,
      where: 'id = ?',
      whereArgs: [id],
    );
    if (group.isEmpty) {
      return SubscriptionGroupEdit(
        id: null,
        name: '',
        icon: defaultGroupIcon,
        color: null,
        members: <String>{},
        emoji: null,
        markStyle: GroupMarkStyle.auto,
      );
    }

    var members = (await database.query(
      tableSubscriptionGroupMember,
      where: 'group_id = ?',
      whereArgs: [id],
    )).map((e) => e['profile_id'] as String).toSet();

    return SubscriptionGroupEdit(
      id: group.first['id'] as String,
      name: group.first['name'] as String,
      icon: group.first['icon'] as String,
      color: group.first['color'] == null
          ? null
          : Color(group.first['color'] as int),
      members: members,
      emoji: group.first['emoji'] as String?,
      markStyle: GroupMarkStyle.coerce(group.first['mark_style']),
    );
  }

  Future saveGroup(
    String? id,
    String name,
    String icon,
    Color? color,
    Set<String> subscriptions, {
    String? emoji,
    int markStyle = GroupMarkStyle.auto,
  }) async {
    await execute(() async {
      var database = await Repository.writable();

      // First insert or update the subscription group details
      if (id == null) {
        id = const Uuid().v4();

        // Leave the reply/retweet filters null so a new group follows the
        // global default instead of the column's own "on" default.
        await database.insert(tableSubscriptionGroup, {
          'id': id,
          'name': name,
          'color': color?.toARGB32(),
          'icon': icon,
          'include_replies': null,
          'include_retweets': null,
          'emoji': emoji,
          'mark_style': markStyle,
        });
      } else {
        await database.update(
          tableSubscriptionGroup,
          {
            'name': name,
            'color': color?.toARGB32(),
            'icon': icon,
            'emoji': emoji,
            'mark_style': markStyle,
          },
          where: 'id = ?',
          whereArgs: [id],
        );
      }

      // Then clear out any existing subscriptions for the group and add our new set
      await database.delete(
        tableSubscriptionGroupMember,
        where: 'group_id = ?',
        whereArgs: [id],
      );

      var batch = database.batch();
      for (var subscription in subscriptions) {
        batch.insert(tableSubscriptionGroupMember, {
          'group_id': id,
          'profile_id': subscription,
        });
      }

      await batch.commit(noResult: true);
      await reloadGroups();

      // TODO: Replace the group in the state instead
      return state;
    });
  }

  Future<void> toggleGroupPinned(String id, bool pinned) async {
    var database = await Repository.writable();
    await database.update(
      tableSubscriptionGroup,
      {'pinned': pinned ? 1 : 0},
      where: 'id = ?',
      whereArgs: [id],
    );
    await reloadGroups(notifyReload: false);
  }

  Future<void> toggleGroupNsfw(String id, bool nsfw) async {
    var database = await Repository.writable();
    await database.update(
      tableSubscriptionGroup,
      {'nsfw': nsfw ? 1 : 0},
      where: 'id = ?',
      whereArgs: [id],
    );
    await reloadGroups(notifyReload: false);
  }

  /// Nests [id] inside [parentId], or lifts it back to the top with null.
  ///
  /// A nesting that would put a group inside itself — directly or round a chain
  /// of parents — is refused rather than stored, because the feed that resolved
  /// it would never finish.
  Future<bool> setGroupParent(String id, String? parentId) async {
    var database = await Repository.writable();

    if (parentId != null) {
      final parents = await readGroupParents(database);
      if (wouldNestInsideItself(id, parentId, parents)) {
        return false;
      }
    }

    await database.update(
      tableSubscriptionGroup,
      {'parent_id': parentId},
      where: 'id = ?',
      whereArgs: [id],
    );
    await reloadGroups();
    return true;
  }

  /// Persists a manual order: each group's position becomes its index in [ids].
  Future<void> saveGroupPositions(List<String> ids) async {
    var database = await Repository.writable();
    var batch = database.batch();
    for (var (i, id) in ids.indexed) {
      batch.update(
        tableSubscriptionGroup,
        {'position': i},
        where: 'id = ?',
        whereArgs: [id],
      );
    }
    await batch.commit(noResult: true);
    await reloadGroups(notifyReload: false);
  }

  /// Moves every member of [sourceId] into [targetId] (skipping duplicates),
  /// then deletes the now-empty source group.
  Future<void> mergeGroups(String sourceId, String targetId) async {
    var database = await Repository.writable();
    await database.rawInsert(
      'INSERT OR IGNORE INTO $tableSubscriptionGroupMember (group_id, profile_id) '
      'SELECT ?, profile_id FROM $tableSubscriptionGroupMember WHERE group_id = ?',
      [targetId, sourceId],
    );
    await database.delete(
      tableSubscriptionGroupMember,
      where: 'group_id = ?',
      whereArgs: [sourceId],
    );
    await database.delete(
      tableSubscriptionGroup,
      where: 'id = ?',
      whereArgs: [sourceId],
    );
    await reloadGroups();
  }

  void changeOrderSubscriptionGroupsBy(String? value) async {
    await prefs.set(optionSubscriptionGroupsOrderByField, value ?? 'name');
    await reloadGroups(notifyReload: false);
  }

  void toggleOrderSubscriptionGroupsAscending() async {
    await prefs.set(
      optionSubscriptionGroupsOrderByAscending,
      !orderGroupsAscending,
    );
    await reloadGroups(notifyReload: false);
  }
}
