import 'dart:io';
import 'dart:ui';

import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/subscriptions/group_ungrouped.dart';
import 'package:xta/utils/local_undo.dart';

Future<List<String>> _groupsOf(String profile) async => [
  for (final row in await (await Repository.readOnly()).query(
    tableSubscriptionGroupMember,
    where: 'profile_id = ?',
    whereArgs: [profile],
    orderBy: 'group_id',
  ))
    row['group_id'] as String,
];

Future<Map<String, Object?>> _groupNamed(String name) async =>
    (await (await Repository.readOnly()).query(tableSubscriptionGroup, where: 'name = ?', whereArgs: [name])).single;

void main() {
  late GroupsModel groups;

  setUpAll(() async {
    sqfliteFfiInit();
    databaseFactory = databaseFactoryFfiNoIsolate;
    final directory = await Directory.systemTemp.createTemp('xta-membership-writes');
    await databaseFactory.setDatabasesPath(directory.path);
    await Repository().migrate();
  });

  setUp(() async {
    groups = GroupsModel(
      PrefServiceCache(
        cache: {optionSubscriptionGroupsOrderByField: 'name', optionSubscriptionGroupsOrderByAscending: true},
      ),
    );
    final database = await Repository.writable();
    await database.delete(tableSubscriptionGroupMember);
    await database.delete(tableSubscriptionGroup, where: "id != '-1'");
    UndoStore.shared.clear();
  });

  tearDown(() => groups.destroy());

  test('adding keeps the groups an account is in and takes a repeat quietly', () async {
    final database = await Repository.writable();
    await database.insert(tableSubscriptionGroupMember, {'group_id': 'old', 'profile_id': 'p'});

    await groups.addToGroups('p', ['art', 'old']);

    expect(await _groupsOf('p'), ['art', 'old']);
    expect(UndoStore.shared.state, isNull);
  });

  test('removing takes the account out of the given groups only', () async {
    await groups.addToGroups('p', ['art', 'news', 'old']);
    await groups.addToGroups('q', ['art']);

    await groups.removeFromGroups('p', ['art', 'news']);

    expect(await _groupsOf('p'), ['old']);
    expect(await _groupsOf('q'), ['art']);
  });

  test('a created group comes back by its id, empty, with the default mark and no undo', () async {
    final id = await groups.createGroup('Japan');

    final row = await _groupNamed('Japan');
    expect(row['id'], id);
    expect(row['icon'], defaultGroupIcon);
    expect(row['include_replies'], isNull);
    expect(row['include_retweets'], isNull);
    expect(groups.state.map((group) => group.name), contains('Japan'));
    expect(UndoStore.shared.state, isNull);
  });

  test('saving a new group still writes its look and members', () async {
    await groups.saveGroup(null, 'Art', defaultGroupIcon, const Color(0xFF336699), {'p'}, emoji: '🎨');

    final row = await _groupNamed('Art');
    expect(row['color'], 0xFF336699);
    expect(row['emoji'], '🎨');
    expect(row['include_replies'], isNull);
    expect(await _groupsOf('p'), [row['id']]);
  });

  test('a suggested group is created with its accounts', () async {
    final placed = await groups.applyUngroupedPlan(
      const GroupUngroupedPlan(
        suggest: [
          SuggestedGroup(name: 'Tanaka', accountIds: ['p', 'q']),
        ],
      ),
    );

    final row = await _groupNamed('Tanaka');
    expect(placed, 2);
    expect(row['icon'], defaultGroupIcon);
    expect(row['include_retweets'], isNull);
    expect(await _groupsOf('q'), [row['id']]);
  });
}
