import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';

void main() {
  setUpAll(() {
    sqfliteFfiInit();
    databaseFactory = databaseFactoryFfi;
  });

  test('fresh onCreate reaches databaseVersion with core tables', () async {
    final path =
        '${Directory.systemTemp.path}/xta_migrate_fresh_${DateTime.now().microsecondsSinceEpoch}.db';
    final plan = buildMigrationPlan();
    final db = await openDatabase(
      path,
      version: databaseVersion,
      onCreate: plan.call,
      onUpgrade: plan.call,
    );
    addTearDown(() async {
      await db.close();
      final f = File(path);
      if (await f.exists()) await f.delete();
    });

    expect(await db.getVersion(), databaseVersion);

    final tables = (await db.query(
      'sqlite_master',
      columns: ['name'],
      where: "type = 'table'",
    )).map((row) => row['name'] as String).toSet();
    expect(
      tables,
      containsAll([
        tableAccounts,
        tableSubscription,
        tableSubscriptionGroup,
        tableSavedTweet,
        tableProfileNote,
        tableAntenna,
        tableThreadsLocalLike,
        tableBlueskyLocalLike,
        tableEhFavorite,
        tableEhHistory,
        tableRssSubscription,
      ]),
    );

    final savedCols = (await db.rawQuery(
      'PRAGMA table_info($tableSavedTweet)',
    )).map((row) => row['name'] as String).toSet();
    expect(savedCols, contains('note'));

    final subCols = (await db.rawQuery(
      'PRAGMA table_info($tableSubscription)',
    )).map((row) => row['name'] as String).toSet();
    expect(subCols, contains('max_posts_per_load'));

    final accountCols = (await db.rawQuery(
      'PRAGMA table_info($tableAccounts)',
    )).map((row) => row['name'] as String).toSet();
    expect(
      accountCols,
      containsAll([
        'id',
        'auth_header',
        'screen_name',
        'last_not_found_at',
        'consecutive_not_found',
      ]),
    );
  });

  test(
    'upgrade from v22 preserves account auth_header and subscription rows',
    () async {
      final path =
          '${Directory.systemTemp.path}/xta_migrate_v22_${DateTime.now().microsecondsSinceEpoch}.db';
      final plan = buildMigrationPlan();

      final authHeader =
          '{"authorization":"Bearer test-token","cookie":"auth_token=redacted"}';
      var db = await openDatabase(
        path,
        version: 22,
        onCreate: plan.call,
        onUpgrade: plan.call,
      );

      await db.insert(tableAccounts, {
        'id': '42',
        'auth_header': authHeader,
        'screen_name': 'xta_test',
      });
      await db.insert(tableSubscription, {
        'id': '783214',
        'screen_name': 'X',
        'name': 'X',
        'profile_image_url_https': 'https://example.com/x.png',
        'verified': 1,
      });
      await db.close();

      db = await openDatabase(
        path,
        version: databaseVersion,
        onCreate: plan.call,
        onUpgrade: plan.call,
      );
      addTearDown(() async {
        await db.close();
        final f = File(path);
        if (await f.exists()) await f.delete();
      });

      expect(await db.getVersion(), databaseVersion);

      final account = Account.fromMap(
        (await db.query(
          tableAccounts,
          where: 'id = ?',
          whereArgs: ['42'],
        )).single,
      );
      expect(account.authHeader, authHeader);
      expect(account.screenName, 'xta_test');
      expect(account.consecutiveNotFound, 0);
      expect(account.lastNotFoundAt, isNull);

      final subscription = UserSubscription.fromMap(
        (await db.query(
          tableSubscription,
          where: 'id = ?',
          whereArgs: ['783214'],
        )).single,
      );
      expect(subscription.screenName, 'X');
      expect(subscription.name, 'X');
      expect(subscription.profileImageUrlHttps, 'https://example.com/x.png');
      expect(subscription.verified, isTrue);
      expect(subscription.inFeed, isTrue);
    },
  );

  test('upgrade from v57 creates rss_subscription', () async {
    final path =
        '${Directory.systemTemp.path}/xta_migrate_v57_${DateTime.now().microsecondsSinceEpoch}.db';
    final plan = buildMigrationPlan();
    var db = await openDatabase(
      path,
      version: 57,
      onCreate: plan.call,
      onUpgrade: plan.call,
    );
    expect(
      (await db.query(
        'sqlite_master',
        columns: ['name'],
        where: "type = 'table' AND name = ?",
        whereArgs: [tableRssSubscription],
      )),
      isEmpty,
    );
    await db.close();

    db = await openDatabase(
      path,
      version: databaseVersion,
      onCreate: plan.call,
      onUpgrade: plan.call,
    );
    addTearDown(() async {
      await db.close();
      final f = File(path);
      if (await f.exists()) await f.delete();
    });

    expect(await db.getVersion(), databaseVersion);
    final tables = (await db.query(
      'sqlite_master',
      columns: ['name'],
      where: "type = 'table'",
    )).map((row) => row['name'] as String).toSet();
    expect(tables, contains(tableRssSubscription));
  });
}
