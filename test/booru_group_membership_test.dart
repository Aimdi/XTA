import 'dart:io';

import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/plugins/booru/booru_plugin.dart';
import 'package:xta/plugins/booru/booru_store.dart';
import 'package:xta/plugins/plugin_account_subscription.dart';

PrefServiceCache _prefs() => PrefServiceCache(
  cache: {
    optionPluginPixivGroupSubscriptions: '[]',
    optionPluginHnFollows: '[]',
    optionSubscriptionGroupsOrderByField: 'name',
    optionSubscriptionGroupsOrderByAscending: true,
    optionSubscriptionOrderByField: 'name',
    optionSubscriptionOrderByAscending: true,
    optionSubscriptionOrderCustom: '',
  },
);

Future<List<String>> _members(String group) async {
  final rows = await (await Repository.readOnly()).query(
    tableSubscriptionGroupMember,
    where: 'group_id = ?',
    whereArgs: [group],
    orderBy: 'profile_id',
  );
  return rows.map((row) => row['profile_id'] as String).toList();
}

void main() {
  setUpAll(() async {
    sqfliteFfiInit();
    databaseFactory = databaseFactoryFfi;
    final directory = await Directory.systemTemp.createTemp('xta_booru_members');
    await databaseFactory.setDatabasesPath(directory.path);
    await Repository().migrate();
  });

  test('unfollowing a tag leaves a subreddit of the same name in its group', () async {
    final db = await Repository.writable();
    await db.insert(tableRedditSubscription, {'id': 'art', 'name': 'art'});
    final tags = BooruTagsStore();
    await tags.add('art');
    for (final member in ['art', '$pluginIdBooru:art']) {
      await db.insert(tableSubscriptionGroupMember, {'group_id': 'mixed', 'profile_id': member});
    }

    await tags.remove('art');

    expect(await _members('mixed'), ['art']);
    tags.destroy();
  });

  test('upgrading moves tag memberships under the prefix and keeps one another source follows', () async {
    final path = '${Directory.systemTemp.path}/xta_booru_members_${DateTime.now().microsecondsSinceEpoch}.db';
    final plan = buildMigrationPlan();
    var db = await openDatabase(path, version: 61, onCreate: plan.call, onUpgrade: plan.call);
    for (final tag in ['art', 'solo']) {
      await db.insert(tableBooruSubscription, {'id': tag, 'name': tag});
    }
    await db.insert(tableRedditSubscription, {'id': 'art', 'name': 'art'});
    for (final (group, member) in [('g', 'art'), ('g', 'solo'), ('h', 'solo'), ('g', '783214')]) {
      await db.insert(tableSubscriptionGroupMember, {'group_id': group, 'profile_id': member});
    }
    await db.close();

    db = await openDatabase(path, version: databaseVersion, onCreate: plan.call, onUpgrade: plan.call);
    addTearDown(() async {
      await db.close();
      await File(path).delete();
    });

    final rows = await db.query(tableSubscriptionGroupMember, orderBy: 'group_id, profile_id');
    expect(rows.map((row) => '${row['group_id']}/${row['profile_id']}'), [
      'g/783214',
      'g/art',
      'g/booru:art',
      'g/booru:solo',
      'h/booru:solo',
    ]);
  });

  testWidgets('a restored older backup is moved under the prefix when the plugin reloads', (tester) async {
    final tags = BooruTagsStore();
    late BuildContext context;
    await tester.pumpWidget(
      Provider<BooruTagsStore>.value(
        value: tags,
        child: Builder(
          builder: (built) {
            context = built;
            return const SizedBox();
          },
        ),
      ),
    );

    await tester.runAsync(() async {
      final db = await Repository.writable();
      await db.insert(tableBooruSubscription, {'id': 'restored_tag', 'name': 'restored_tag'});
      await db.insert(tableSubscriptionGroupMember, {'group_id': 'restored', 'profile_id': 'restored_tag'});

      await BooruPlugin().reloadFromDatabase(context);
      await Future<void>.delayed(const Duration(milliseconds: 120));

      expect(await _members('restored'), ['$pluginIdBooru:restored_tag']);
      expect(tags.state, contains('restored_tag'));
    });
    tags.destroy();
  });

  test('a followed tag reads back as a member of a real group', () async {
    final prefs = _prefs();
    final tags = BooruTagsStore();
    await tags.add('blue_eyes solo');
    final groups = GroupsModel(prefs);
    await groups.saveGroup(null, 'Tags', defaultGroupIcon, null, {booruMemberId('blue_eyes solo')});
    await groups.reloadGroups();
    final group = groups.state.singleWhere((item) => item.name == 'Tags');
    final model = GroupModel(group.id, prefs: prefs);

    await model.loadGroup();

    expect(model.error, isNull);
    final member = model.state.subscriptions.single;
    expect(member.id, '$pluginIdBooru:blue_eyes solo');
    expect(member.name, 'blue_eyes solo');
    expect(member.screenName, 'blue_eyes solo');
    final plugin = BooruPlugin();
    expect(plugin.owns(member), isTrue);
    expect(plugin.owns(PluginAccountSubscription(pluginIdTiktok, {'id': 'blue_eyes solo'})), isFalse);
    expect(plugin.subtitleFor(member), 'tag:blue_eyes solo');
    tags.destroy();
    groups.destroy();
    model.destroy();
  });

  test('member ids and tags convert both ways, and unprefixed ids are tags', () {
    expect(booruMemberId('order:rank solo'), 'booru:order:rank solo');
    expect(booruMemberTag('booru:order:rank solo'), 'order:rank solo');
    expect(booruMemberTag('art'), 'art');
  });
}
