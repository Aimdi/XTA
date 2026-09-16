import 'dart:async';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/profile/profile_model.dart';
import 'package:xta/user.dart';
import 'package:xta/utils/local_json_store.dart';

class _StallingSubscriptions extends SubscriptionsModel {
  final stalled = Completer<List<Subscription>>();
  bool stallNextRead = true;
  _StallingSubscriptions(super.prefs, super.groupModel);

  @override
  Duration get snapshotTimeout => const Duration(seconds: 1);

  @override
  Future<List<Subscription>> readSnapshot(Future<List<Subscription>> Function() read) {
    if (!stallNextRead) return super.readSnapshot(read);
    stallNextRead = false;
    return super.readSnapshot(() => stalled.future);
  }
}

class _StallingGroups extends GroupsModel {
  final stalled = Completer<List<SubscriptionGroup>>();
  bool stallNextRead = true;
  _StallingGroups(super.prefs);

  @override
  Duration get snapshotTimeout => const Duration(seconds: 1);

  @override
  Future<List<SubscriptionGroup>> readSnapshot(Future<List<SubscriptionGroup>> Function() read) {
    if (!stallNextRead) return super.readSnapshot(read);
    stallNextRead = false;
    return super.readSnapshot(() => stalled.future);
  }
}

class _EmptyCache implements JsonStore {
  @override
  Future<Object?> read(String key) async => null;
  @override
  Future<void> write(String key, Object? value) async {}
  @override
  Future<void> remove(String key) async {}
  @override
  Future<Map<String, Object?>> readPrefix(String prefix) async => {};
}

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
  TestWidgetsFlutterBinding.ensureInitialized();
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

  test('a stalled subscription reload releases queued follows and ignores its late snapshot', () async {
    final model = _StallingSubscriptions(subscriptions.prefs, groups);
    addTearDown(model.destroy);
    final reload = model.reloadSubscriptions();
    final follow = model.toggleSubscribe(person('after-stalled-reload'), false);
    await Future.wait([reload, follow]).timeout(const Duration(seconds: 5));
    expect(model.state.map((e) => e.id), contains('after-stalled-reload'));
    expect(model.isLoading, isFalse);
    expect(model.error, isNull);
    model.stalled.complete([]);
    await Future<void>.delayed(Duration.zero);
    expect(model.state.map((e) => e.id), contains('after-stalled-reload'));
  });

  test('a stalled group reload releases queued saves and membership changes', () async {
    final model = _StallingGroups(groups.prefs);
    addTearDown(model.destroy);
    final reload = model.reloadGroups();
    final save = model.saveGroup(null, 'After stalled reload', defaultGroupIcon, null, {'follow-once'});
    await Future.wait([reload, save]).timeout(const Duration(seconds: 5));
    final saved = model.state.singleWhere((e) => e.name == 'After stalled reload');
    expect((await model.loadGroupEdit(saved.id)).members, {'follow-once'});
    expect(model.isLoading, isFalse);
    expect(model.error, isNull);
    model.stalled.complete([]);
    await Future<void>.delayed(Duration.zero);
    expect(model.state.map((e) => e.id), contains(saved.id));
  });

  test('leaving a pending profile does not block local follows or groups', () async {
    final request = Completer<Profile>();
    final profile = ProfileModel(storage: _EmptyCache(), byId: (_) => request.future);
    final loading = profile.loadProfileById('stalled-profile');
    await Future<void>.delayed(Duration.zero);
    await profile.destroy();
    await subscriptions.toggleSubscribe(person('while-profile-stalled'), false).timeout(const Duration(seconds: 5));
    await groups.saveGroup(null, 'While profile stalled', defaultGroupIcon, null, {'while-profile-stalled'});
    expect(subscriptions.state.map((e) => e.id), contains('while-profile-stalled'));
    expect(groups.state.map((e) => e.name), contains('While profile stalled'));
    expect(subscriptions.isLoading, isFalse);
    expect(groups.isLoading, isFalse);
    request.complete(Profile(UserWithExtra.fromArguments(idStr: 'stalled-profile'), []));
    await loading;
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
