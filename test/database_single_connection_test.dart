import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
// The Android-like native layer below plugs into sqflite's own factory builder; sqflite ships with the app.
// ignore: implementation_imports, depend_on_referenced_packages
import 'package:sqflite_common/src/mixin/factory.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:xta/client/accounts.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/subscriptions/users_model.dart';

/// Android's busy timeout is 2500 ms; scaled down here so a stall shows in seconds, not minutes.
const _busyTimeout = Duration(milliseconds: 50);

/// sqflite on Android as far as locking goes: one worker runs every statement of every connection in order, a
/// transaction's BEGIN runs as BEGIN EXCLUSIVE in the rollback journal, and a busy query spins that worker for the
/// busy timeout and retries 50 times before giving up.
class _AndroidLikeNative {
  final SqfliteInvokeHandler ffi;
  Future<void> _tail = Future.value();
  int busySpins = 0;
  void Function()? onBegin;

  _AndroidLikeNative(this.ffi);

  Future<dynamic> invoke(String method, [Object? arguments]) {
    final result = _tail.then((_) => _run(method, _exclusive(method, arguments)));
    _tail = result.then((_) {}, onError: (Object _) {});
    return result;
  }

  Object? _exclusive(String method, Object? arguments) {
    if (method != 'execute' || arguments is! Map) return arguments;
    final sql = '${arguments['sql']}'.trim().toUpperCase();
    if (!sql.startsWith('BEGIN')) return arguments;
    final hook = onBegin;
    onBegin = null;
    hook?.call();
    return {...arguments, 'sql': 'BEGIN EXCLUSIVE'};
  }

  Future<dynamic> _run(String method, Object? arguments) async {
    for (var retries = 0; ; retries++) {
      try {
        return await ffi.invokeMethod<dynamic>(method, arguments);
      } catch (error) {
        if (!'$error'.contains('database is locked') || retries >= 50) rethrow;
        busySpins++;
        await Future<void>.delayed(_busyTimeout);
      }
    }
  }
}

UserSubscription _person(String id) => UserSubscription(
  id: id,
  name: id,
  screenName: id,
  profileImageUrlHttps: null,
  verified: false,
  createdAt: DateTime(2026),
  inFeed: true,
);

PrefServiceCache _prefs() => PrefServiceCache(
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

Future<void> _seed() async {
  final db = await Repository.writable();
  await db.insert(tableAccounts, Account(id: 'a', authHeader: '{}', screenName: 'a').toMap());
  await db.insert(tableSubscriptionGroup, {'id': 'g1', 'name': 'G1', 'icon': defaultGroupIcon});
}

/// What the timeline does while a group change is being saved: X asks for its accounts, the open group feed
/// re-reads its group, and the reader likes a post.
List<Future<Object?>> _meanwhile() => [
  getAccounts(),
  Repository.readOnly().then((db) => db.query(tableSubscriptionGroup, where: 'id = ?', whereArgs: ['g1'])),
  Repository.writable().then((db) => db.insert(tableLikedTweet, {'id': 'liked', 'content': '{}'})),
];

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late _AndroidLikeNative native;

  setUpAll(() async {
    sqfliteFfiInit();
    native = _AndroidLikeNative(databaseFactoryFfi as SqfliteInvokeHandler);
    databaseFactory = buildDatabaseFactory(tag: 'android-like', invokeMethod: native.invoke);
    final directory = await Directory.systemTemp.createTemp('xta_single_connection');
    await databaseFactory.setDatabasesPath(directory.path);
    await Repository().migrate();
    await _seed();
  });

  test('reads and writes share one connection', () async {
    expect(identical(await Repository.readOnly(), await Repository.writable()), isTrue);
  });

  test(
    'adding a timeline author to a group does not lock out every read and write',
    () async {
      final groups = GroupsModel(_prefs());
      final subscriptions = SubscriptionsModel(_prefs(), groups);
      addTearDown(subscriptions.destroy);
      addTearDown(groups.destroy);
      final others = <Future<Object?>>[];
      native.onBegin = () => others.addAll(_meanwhile());
      native.busySpins = 0;

      // pickUserGroups for an author not yet followed, which is what the avatar's + offers.
      await subscriptions.toggleSubscribe(_person('newbie'), false);
      await groups.saveUserGroupMembership('newbie', ['g1']);
      await Future.wait(others);

      expect(others, isNotEmpty);
      expect(native.busySpins, 0, reason: 'a read hit the save\'s exclusive lock and spun the only database thread');
    },
    timeout: const Timeout(Duration(minutes: 2)),
  );
}
