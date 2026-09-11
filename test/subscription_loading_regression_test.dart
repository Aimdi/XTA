import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/subscriptions/users_model.dart';

UserSubscription person(String id) => UserSubscription(
  id: id,
  name: id,
  screenName: id,
  profileImageUrlHttps: null,
  verified: false,
  createdAt: DateTime(2026),
  inFeed: true,
);

void main() {
  late GroupsModel groups;
  late SubscriptionsModel subscriptions;

  setUpAll(() async {
    sqfliteFfiInit();
    databaseFactory = databaseFactoryFfi;
    final directory = await Directory.systemTemp.createTemp('xta_loading_regression');
    await databaseFactory.setDatabasesPath(directory.path);
    await Repository().migrate();
  });

  setUp(() {
    final prefs = PrefServiceCache(
      cache: {
        optionSubscriptionGroupsOrderByField: 'name',
        optionSubscriptionGroupsOrderByAscending: true,
        optionSubscriptionOrderByField: 'name',
        optionSubscriptionOrderByAscending: true,
        optionSubscriptionOrderCustom: '',
        optionPluginPixivGroupSubscriptions: '[]',
        optionPluginHnFollows: '[]',
      },
    );
    groups = GroupsModel(prefs);
    subscriptions = SubscriptionsModel(prefs, groups);
  });

  tearDown(() async {
    await subscriptions.destroy();
    await groups.destroy();
  });

  test('following completes with the person visible and both stores idle', () async {
    await subscriptions.toggleSubscribe(person('follow-once'), false);
    expect(subscriptions.state.map((item) => item.id), contains('follow-once'));
    expect(subscriptions.isLoading, isFalse);
    expect(groups.isLoading, isFalse);
    expect(subscriptions.error, isNull);
  });

  test('rapid follows and a reload do not cancel a local write', () async {
    await Future.wait([
      subscriptions.toggleSubscribe(person('rapid-first'), false),
      subscriptions.toggleSubscribe(person('rapid-second'), false),
      subscriptions.reloadSubscriptions(),
    ]);
    expect(subscriptions.state.map((item) => item.id), containsAll(['rapid-first', 'rapid-second']));
    final database = await Repository.readOnly();
    final rows = await database.query(tableSubscription);
    expect(rows.map((row) => row['id']), containsAll(['rapid-first', 'rapid-second']));
    expect(subscriptions.isLoading, isFalse);
    expect(groups.isLoading, isFalse);
  });

  test('saving a group publishes the group before the save future completes', () async {
    await groups.saveGroup(null, 'Ready immediately', defaultGroupIcon, null, {'follow-once'});
    expect(groups.state.map((item) => item.name), contains('Ready immediately'));
    expect(groups.isLoading, isFalse);
    final saved = groups.state.singleWhere((item) => item.name == 'Ready immediately');
    expect((await groups.loadGroupEdit(saved.id)).members, {'follow-once'});
  });

  test('rapid group saves preserve both groups', () async {
    await Future.wait([
      groups.saveGroup(null, 'Rapid group one', defaultGroupIcon, null, {}),
      groups.saveGroup(null, 'Rapid group two', defaultGroupIcon, null, {}),
    ]);
    expect(groups.state.map((item) => item.name), containsAll(['Rapid group one', 'Rapid group two']));
    expect(groups.isLoading, isFalse);
  });

  test('repeated follow keeps existing feed settings and memberships', () async {
    final user = person('already-followed');
    await subscriptions.toggleSubscribe(user, false);
    await subscriptions.toggleInFeed(user, true);
    await groups.saveUserGroupMembership(user.id, ['existing-group']);
    await subscriptions.toggleSubscribe(user, false);
    expect(subscriptions.error, isNull);
    expect(subscriptions.state.singleWhere((item) => item.id == user.id).inFeed, isFalse);
    expect(await groups.listGroupsForUser(user.id), ['existing-group']);
    expect(subscriptions.isLoading, isFalse);
  });

  test('a failed write stops loading and does not poison the next follow', () async {
    final database = await Repository.writable();
    await database.execute(
      "CREATE TRIGGER fail_test_follow BEFORE INSERT ON $tableSubscription "
      "WHEN NEW.id = 'failed-follow' BEGIN SELECT RAISE(ABORT, 'test failure'); END",
    );
    try {
      await subscriptions.toggleSubscribe(person('failed-follow'), false);
      expect(subscriptions.isLoading, isFalse);
      expect(subscriptions.error, isNotNull);
      expect(subscriptions.state.map((item) => item.id), isNot(contains('failed-follow')));
      await subscriptions.toggleSubscribe(person('recovered-follow'), false);
      expect(subscriptions.isLoading, isFalse);
      expect(subscriptions.error, isNull);
      expect(subscriptions.state.map((item) => item.id), contains('recovered-follow'));
    } finally {
      await database.execute('DROP TRIGGER fail_test_follow');
    }
  });
}
