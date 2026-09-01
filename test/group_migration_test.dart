import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/group/group_model.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';

/// Creates a database matching the schema at version 33 (only the tables the
/// migration path from 33 to current touches), with a few groups to backfill.
Future<void> _createV33Fixture() async {
  final db = await databaseFactory.openDatabase(
    databaseName,
    options: OpenDatabaseOptions(
      version: 33,
      onCreate: (db, version) async {
        await db.execute(
          'CREATE TABLE $tableSubscriptionGroup ('
          'id VARCHAR PRIMARY KEY, name VARCHAR NOT NULL, icon VARCHAR NOT NULL, '
          'created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, include_replies BOOLEAN, '
          'include_retweets BOOLEAN, color INT, popular BOOLEAN DEFAULT 0, '
          "custom BOOLEAN DEFAULT 0, content_filter VARCHAR DEFAULT 'default')",
        );
        await db.execute(
          'CREATE TABLE $tableFeedGroupCursor '
          '(id INTEGER PRIMARY KEY, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)',
        );
        await db.execute(
          'CREATE TABLE $tableFeedGroupChunk '
          '(cursor_id INTEGER NOT NULL, hash VARCHAR NOT NULL, cursor_top VARCHAR, '
          'cursor_bottom VARCHAR, response VARCHAR, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)',
        );

        await db.insert(tableSubscriptionGroup, {
          'id': '-1',
          'name': 'All',
          'icon': 'rss_feed',
        });
        await db.insert(tableSubscriptionGroup, {
          'id': 'b',
          'name': 'beta',
          'icon': 'x',
        });
        await db.insert(tableSubscriptionGroup, {
          'id': 'a',
          'name': 'Alpha',
          'icon': 'x',
        });
      },
    ),
  );
  await db.close();
}

/// Schema at v34 (after pinned/position) so we can exercise only migration 35.
Future<void> _createV34Fixture() async {
  final db = await databaseFactory.openDatabase(
    databaseName,
    options: OpenDatabaseOptions(
      version: 34,
      onCreate: (db, version) async {
        await db.execute(
          'CREATE TABLE $tableSubscriptionGroup ('
          'id VARCHAR PRIMARY KEY, name VARCHAR NOT NULL, icon VARCHAR NOT NULL, '
          'created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, include_replies BOOLEAN, '
          'include_retweets BOOLEAN, color INT, popular BOOLEAN DEFAULT 0, '
          "custom BOOLEAN DEFAULT 0, content_filter VARCHAR DEFAULT 'default', "
          'pinned BOOLEAN DEFAULT 0, position INTEGER DEFAULT 0)',
        );
        await db.execute(
          'CREATE TABLE $tableFeedGroupCursor '
          '(id INTEGER PRIMARY KEY, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)',
        );
        await db.execute(
          'CREATE TABLE $tableFeedGroupChunk '
          '(cursor_id INTEGER NOT NULL, hash VARCHAR NOT NULL, cursor_top VARCHAR, '
          'cursor_bottom VARCHAR, response VARCHAR, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)',
        );

        await db.insert(tableSubscriptionGroup, {
          'id': '-1',
          'name': 'All',
          'icon': defaultGroupIcon,
          'position': 0,
        });
        await db.insert(tableSubscriptionGroup, {
          'id': 'legacy',
          'name': 'China',
          'icon': '{"pack":"material","key":"star"}',
          'position': 0,
        });
        await db.insert(tableSubscriptionGroup, {
          'id': 'fresh',
          'name': 'Defaulty',
          'icon': defaultGroupIcon,
          'position': 1,
        });
      },
    ),
  );
  await db.close();
}

void main() {
  setUpAll(() async {
    sqfliteFfiInit();
    databaseFactory = databaseFactoryFfi;
    final dir = await Directory.systemTemp.createTemp('xta_migration_test');
    await databaseFactory.setDatabasesPath(dir.path);
  });

  test(
    'migration 34 adds pinned and position, backfilling alphabetical order',
    () async {
      await _createV33Fixture();

      await Repository().migrate();

      final db = await databaseFactory.openDatabase(databaseName);
      final rows = await db.query(tableSubscriptionGroup);
      final byName = {for (final r in rows) r['name'] as String: r};

      expect(byName['Alpha']!['position'], 0);
      expect(byName['beta']!['position'], 1);
      expect(rows.every((r) => r['pinned'] == 0), isTrue);
      expect(byName['Alpha']!['mark_style'], 2); // non-default icon 'x'
      expect(byName['Alpha']!.containsKey('emoji'), isTrue);
      await db.close();
    },
  );

  test(
    'migration 35 adds emoji/mark_style and backfills symbol for custom icons',
    () async {
      await databaseFactory.deleteDatabase(databaseName);
      await _createV34Fixture();

      await Repository().migrate();

      final db = await databaseFactory.openDatabase(databaseName);
      final rows = await db.query(tableSubscriptionGroup);
      final byId = {for (final r in rows) r['id'] as String: r};

      expect(byId['legacy']!['mark_style'], 2);
      expect(byId['legacy']!['emoji'], isNull);
      expect(byId['fresh']!['mark_style'], 0);
      expect(byId['-1']!['mark_style'], 0);
      expect(byId['legacy']!['icon'], '{"pack":"material","key":"star"}');
      await db.close();
    },
  );

  test(
    'migration 36 adds the custom-feed rule columns with inert defaults',
    () async {
      await databaseFactory.deleteDatabase(databaseName);
      await _createV34Fixture();

      await Repository().migrate();

      final db = await databaseFactory.openDatabase(databaseName);
      final rows = await db.query(
        tableSubscriptionGroup,
        where: "id = 'legacy'",
      );
      final row = rows.first;

      // Existing feeds must behave exactly as before: no threshold, nothing muted.
      expect(row['min_likes'], 0);
      expect(row['min_retweets'], 0);
      expect(row['muted_keywords'], isNull);
      await db.close();
    },
  );

  test('migration 52 adds nsfw with default off', () async {
    await databaseFactory.deleteDatabase(databaseName);
    await _createV34Fixture();

    await Repository().migrate();

    final db = await databaseFactory.openDatabase(databaseName);
    final rows = await db.query(tableSubscriptionGroup);
    expect(rows.every((r) => r['nsfw'] == 0), isTrue);
    await db.close();
  });

  test('migration 53 creates booru_subscription', () async {
    await databaseFactory.deleteDatabase(databaseName);
    await _createV34Fixture();

    await Repository().migrate();

    final db = await databaseFactory.openDatabase(databaseName);
    await db.insert(tableBooruSubscription, {
      'id': '1girl',
      'name': '1girl',
      'in_feed': 1,
      'created_at': DateTime.now().toIso8601String(),
    });
    final rows = await db.query(tableBooruSubscription);
    expect(rows, hasLength(1));
    expect(rows.single['id'], '1girl');
    await db.close();
  });

  test('migration 54 creates eh_favorite', () async {
    await databaseFactory.deleteDatabase(databaseName);
    await _createV34Fixture();

    await Repository().migrate();

    final db = await databaseFactory.openDatabase(databaseName);
    await db.insert(tableEhFavorite, {
      'gid': 1,
      'token': 'abc',
      'title': 'Sample',
      'favorited_at': DateTime.now().toIso8601String(),
    });
    final rows = await db.query(tableEhFavorite);
    expect(rows, hasLength(1));
    expect(rows.single['token'], 'abc');
    await db.close();
  });

  test('migration 55 creates tiktok_subscription', () async {
    await databaseFactory.deleteDatabase(databaseName);
    await _createV34Fixture();

    await Repository().migrate();

    final db = await databaseFactory.openDatabase(databaseName);
    await db.insert(tableTiktokSubscription, {
      'id': 'tiktok',
      'sec_uid': 'MS4wLjABAAAA',
      'name': 'TikTok',
      'in_feed': 1,
      'created_at': DateTime.now().toIso8601String(),
    });
    final rows = await db.query(tableTiktokSubscription);
    expect(rows, hasLength(1));
    expect(rows.single['id'], 'tiktok');
    await db.close();
  });

  test('migration 57 creates instagram_subscription', () async {
    await databaseFactory.deleteDatabase(databaseName);
    await _createV34Fixture();

    await Repository().migrate();

    final db = await databaseFactory.openDatabase(databaseName);
    await db.insert(tableInstagramSubscription, {
      'id': 'instagram',
      'pk': '25025320',
      'name': 'Instagram',
      'in_feed': 1,
      'created_at': DateTime.now().toIso8601String(),
    });
    final rows = await db.query(tableInstagramSubscription);
    expect(rows, hasLength(1));
    expect(rows.single['id'], 'instagram');
    expect(rows.single['pk'], '25025320');
    await db.close();
  });
}
