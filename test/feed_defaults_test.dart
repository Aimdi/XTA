import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/group/group_model.dart';
import 'package:sqflite/sqflite.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';

GroupsModel _model({bool globalReplies = true, bool globalRetweets = true}) => GroupsModel(PrefServiceCache(cache: {
      optionSubscriptionGroupsOrderByField: 'name',
      optionSubscriptionGroupsOrderByAscending: true,
      optionGlobalIncludeReplies: globalReplies,
      optionGlobalIncludeRetweets: globalRetweets,
    }));

/// Reads a group's stored choice; null means "follow the global default".
Future<({Object? replies, Object? retweets})> _stored(String id) async {
  final db = await Repository.readOnly();
  final rows = await db.query(tableSubscriptionGroup,
      columns: ['include_replies', 'include_retweets'], where: 'id = ?', whereArgs: [id]);
  return (replies: rows.first['include_replies'], retweets: rows.first['include_retweets']);
}

Future<void> _insertLegacyGroup(String id, String name) async {
  // Groups created before the global defaults existed hold an explicit value,
  // because the columns were added with DEFAULT true.
  final db = await Repository.writable();
  await db.insert(
    tableSubscriptionGroup,
    {'id': id, 'name': name, 'icon': defaultGroupIcon, 'include_replies': 1, 'include_retweets': 1},
    conflictAlgorithm: ConflictAlgorithm.replace,
  );
}

void main() {
  setUpAll(() async {
    sqfliteFfiInit();
    databaseFactory = databaseFactoryFfi;
    final dir = await Directory.systemTemp.createTemp('xta_feed_defaults_test');
    await databaseFactory.setDatabasesPath(dir.path);
    await Repository().migrate();
  });

  test('a new group follows the global default instead of pinning a value', () async {
    final model = _model();
    await model.saveGroup(null, 'Follows default', defaultGroupIcon, null, <String>{});
    await model.reloadGroups();

    final id = model.state.firstWhere((g) => g.name == 'Follows default').id;
    final stored = await _stored(id);

    expect(stored.replies, isNull);
    expect(stored.retweets, isNull);
  });

  test('countIncludeOverrides counts only feeds that pinned a choice', () async {
    final model = _model();
    await _insertLegacyGroup('legacy-a', 'Legacy A');
    await model.reloadGroups();

    final before = await model.countIncludeOverrides();
    expect(before.replies, greaterThanOrEqualTo(1));
    expect(before.retweets, greaterThanOrEqualTo(1));

    await model.clearIncludeOverrides(replies: true);
    await model.clearIncludeOverrides(replies: false);

    final after = await model.countIncludeOverrides();
    expect(after.replies, 0);
    expect(after.retweets, 0);
  });

  test('a group keeps its own choice when only the replies overrides are cleared', () async {
    final model = _model();
    await _insertLegacyGroup('legacy-b', 'Legacy B');
    await model.reloadGroups();

    await model.clearIncludeOverrides(replies: true);

    final stored = await _stored('legacy-b');
    expect(stored.replies, isNull, reason: 'the replies choice was the one being cleared');
    expect(stored.retweets, isNotNull, reason: 'the reposts choice must be left alone');
  });

  test('the All group is not counted as a feed with its own choice', () async {
    final model = _model();
    await model.clearIncludeOverrides(replies: true);
    await model.clearIncludeOverrides(replies: false);

    final db = await Repository.writable();
    await db.update(tableSubscriptionGroup, {'include_replies': 1}, where: "id = '-1'");

    final counts = await model.countIncludeOverrides();
    expect(counts.replies, 0, reason: 'the All pseudo-group is excluded from the Groups tab and from this count');
  });
}
