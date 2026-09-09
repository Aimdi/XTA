import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/group/group_members.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/plugins/hackernews/hn_group.dart';
import 'package:xta/plugins/pixiv/pixiv_group_store.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/plugin_account_subscription.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/plugins/plugin_storage.dart';
import 'package:xta/subscriptions/group_membership_sheet.dart';
import 'package:xta/subscriptions/users_model.dart';

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

void main() {
  setUpAll(() async {
    sqfliteFfiInit();
    databaseFactory = databaseFactoryFfi;
    final directory = await Directory.systemTemp.createTemp('xta_account_groups');
    await databaseFactory.setDatabasesPath(directory.path);
    await Repository().migrate();
  });

  test('same handle stays separate across all newly groupable sources', () {
    final members = [
      for (final source in [pluginIdInstagram, pluginIdTiktok, pluginIdHackerNews, pluginIdPixiv])
        PluginAccountSubscription(source, {'id': '123', 'name': 'Artist'}),
    ];
    expect(members.map((member) => member.id).toSet(), hasLength(4));
    final split = splitGroupMembers(members);
    expect(split.xMembers, isEmpty);
    expect(split.pluginMembers, hasLength(4));
    for (final member in members) {
      final source = subscriptionSources.singleWhere((source) => source.owns(member));
      expect(source.subscriptionFromMap(member.toMap()).id, member.id);
      expect(source.destinationFor(member), isNotNull);
    }
  });

  test('Pixiv selection survives settings round-trip and a real group reload', () async {
    final prefs = _prefs();
    final follows = PixivGroupSubscriptionsStore(prefs);
    await follows.add(const PixivUser(id: 42, name: 'Artist', account: 'artist', comment: ''));
    await follows.add(const PixivUser(id: 42, name: 'Artist renamed', account: 'artist', comment: ''));
    expect(follows.state, hasLength(1));
    final saved = jsonDecode(jsonEncode(prefs.toMap())) as Map<String, dynamic>;
    final restoredPrefs = PrefServiceCache(cache: saved);
    final groups = GroupsModel(restoredPrefs);
    await groups.saveGroup(null, 'Pixiv artists', defaultGroupIcon, null, {'$pluginIdPixiv:42'});
    await groups.reloadGroups();
    expect(groups.error, isNull);
    final group = groups.state.singleWhere((item) => item.name == 'Pixiv artists');
    final model = GroupModel(group.id, prefs: restoredPrefs);
    await model.loadGroup();
    expect(model.error, isNull);
    expect(model.state.subscriptions.single.id, '$pluginIdPixiv:42');
    expect(model.state.subscriptions.single.name, 'Artist renamed');
    final all = SubscriptionsModel(restoredPrefs, groups);
    await all.reloadSubscriptions();
    expect(all.state.map((item) => item.id), contains('$pluginIdPixiv:42'));
    await follows.remove('$pluginIdPixiv:42');
    expect(await groups.listGroupsForUser('$pluginIdPixiv:42'), isEmpty);
    follows.destroy();
    groups.destroy();
    model.destroy();
    all.destroy();
  });

  test('removing plugin data preserves an equal handle in another source', () async {
    final db = await Repository.writable();
    await db.insert(tableInstagramSubscription, {'id': 'same', 'pk': '123', 'name': 'Same'});
    for (final id in ['same', '$pluginIdInstagram:same']) {
      await db.insert(tableSubscriptionGroupMember, {'group_id': 'identity', 'profile_id': id});
    }
    await erasePluginStorage(tables: [tableInstagramSubscription], caches: [], membershipPrefix: '$pluginIdInstagram:');
    final rows = await db.query(tableSubscriptionGroupMember, where: 'group_id = ?', whereArgs: ['identity']);
    expect(rows.map((row) => row['profile_id']), ['same']);
  });

  test('malformed local data does not prevent loading other subscriptions', () async {
    final prefs = _prefs();
    await prefs.set(optionPluginPixivGroupSubscriptions, 'broken');
    await prefs.set(optionPluginHnFollows, 'broken');
    expect(readPixivGroupSubscriptions(prefs), isEmpty);
    expect(readHnSubscriptions(prefs), isEmpty);
    await prefs.set(
      optionPluginPixivGroupSubscriptions,
      jsonEncode([
        {'id': '42', 'name': 2, 'screen_name': [], 'avatar_url': false},
      ]),
    );
    final author = readPixivGroupSubscriptions(prefs).single;
    expect(author.id, '$pluginIdPixiv:42');
    expect(author.name, '42');
    expect(author.profileImageUrlHttps, isNull);
  });

  test('membership toggles never mutate the original selection', () {
    final initial = ['group-a'];
    final store = GroupMembershipStore(initial);
    store.toggle('group-a');
    store.toggle('group-b');
    store.search(' ART ');
    expect(initial, ['group-a']);
    expect(store.state.chosen, {'group-b'});
    expect(store.state.query, 'art');
    store.destroy();
  });
}
