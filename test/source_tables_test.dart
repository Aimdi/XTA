import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/plugins/rss/rss_plugin.dart';
import 'package:xta/plugins/source_tables.dart';

void main() {
  setUpAll(() {
    sqfliteFfiInit();
    databaseFactory = databaseFactoryFfi;
  });

  test('RSS is a subscription source whose table is rss_subscription', () {
    expect(subscriptionSources.whereType<RssPlugin>(), isNotEmpty);
    expect(
      subscriptionSources.whereType<RssPlugin>().single.subscriptionTable,
      tableRssSubscription,
    );
  });

  test('querySourceTable returns nothing when the table is missing', () async {
    final path =
        '${Directory.systemTemp.path}/xta_missing_rss_${DateTime.now().microsecondsSinceEpoch}.db';
    final db = await openDatabase(path);
    addTearDown(() async {
      await db.close();
      final f = File(path);
      if (await f.exists()) await f.delete();
    });

    expect(await querySourceTable(db, tableRssSubscription), isEmpty);
    await expectLater(db.query(tableRssSubscription), throwsA(isA<Object>()));
  });

  test('querySourceTable reads rows once the table exists', () async {
    final path =
        '${Directory.systemTemp.path}/xta_rss_rows_${DateTime.now().microsecondsSinceEpoch}.db';
    final db = await openDatabase(path);
    addTearDown(() async {
      await db.close();
      final f = File(path);
      if (await f.exists()) await f.delete();
    });

    await db.execute(
      'CREATE TABLE $tableRssSubscription ('
      'id VARCHAR PRIMARY KEY, feed_url VARCHAR NOT NULL, name VARCHAR NOT NULL, '
      'site_url VARCHAR, icon_url VARCHAR, in_feed INTEGER NOT NULL DEFAULT 1, '
      'created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)',
    );
    await db.insert(tableRssSubscription, {
      'id': 'https://example.com/feed',
      'feed_url': 'https://example.com/feed',
      'name': 'Example',
    });

    final rows = await querySourceTable(db, tableRssSubscription);
    expect(rows, hasLength(1));
    expect(rows.single['name'], 'Example');
  });
}
