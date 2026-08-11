import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/import_data_model.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';

void main() {
  setUpAll(() async {
    sqfliteFfiInit();
    databaseFactory = databaseFactoryFfi;
    final dir = await Directory.systemTemp.createTemp('xta_repro_test');
    await databaseFactory.setDatabasesPath(dir.path);
    await Repository().migrate();
  });

  test('create group then reload works on a fresh migrated DB', () async {
    final prefs = PrefServiceCache(
      cache: {
        optionSubscriptionGroupsOrderByField: 'name',
        optionSubscriptionGroupsOrderByAscending: true,
      },
    );
    final model = GroupsModel(prefs);

    await model.saveGroup(
      null,
      'Test Group',
      defaultGroupIcon,
      null,
      <String>{},
    );
    await model.reloadGroups();

    expect(model.state.map((g) => g.name), contains('Test Group'));
  });

  test('importing an old-format backup brings groups back', () async {
    final prefs = PrefServiceCache(
      cache: {
        optionSubscriptionGroupsOrderByField: 'name',
        optionSubscriptionGroupsOrderByAscending: true,
      },
    );
    final model = GroupsModel(prefs);

    // Old exports have no pinned/position keys.
    final oldGroup = SubscriptionGroup.fromMap({
      'id': 'legacy-1',
      'name': 'Legacy Group',
      'icon': 'rss',
      'color': null,
      'created_at': '2024-01-01T00:00:00.000',
    });

    await ImportDataModel().importData({
      tableSubscriptionGroup: [oldGroup],
      tableSubscriptionGroupMember: [
        SubscriptionGroupMember(group: 'legacy-1', profile: 'user-1'),
      ],
    });
    await model.reloadGroups();

    expect(model.state.map((g) => g.name), contains('Legacy Group'));
  });

  test('NSFW groups sink below safe ones after toggle', () async {
    final prefs = PrefServiceCache(
      cache: {
        optionSubscriptionGroupsOrderByField: 'name',
        optionSubscriptionGroupsOrderByAscending: true,
      },
    );
    final model = GroupsModel(prefs);

    await model.saveGroup(null, 'Alpha', defaultGroupIcon, null, <String>{});
    await model.saveGroup(null, 'Zulu', defaultGroupIcon, null, <String>{});
    await model.reloadGroups();

    final alpha = model.state.firstWhere((g) => g.name == 'Alpha');
    await model.toggleGroupNsfw(alpha.id, true);

    final names = model.state.map((g) => g.name).toList();
    expect(names.last, 'Alpha');
    expect(model.state.firstWhere((g) => g.name == 'Alpha').nsfw, isTrue);
    expect(model.state.firstWhere((g) => g.name == 'Zulu').nsfw, isFalse);
  });
}
