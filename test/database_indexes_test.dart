import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:xta/database/repository.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';

/// The schema had no indexes at all. Creating one proves nothing on its own —
/// what matters is whether SQLite's planner picks it — so these assert on
/// EXPLAIN QUERY PLAN for the exact query shapes the app issues.
void main() {
  setUpAll(() {
    sqfliteFfiInit();
    databaseFactory = databaseFactoryFfi;
  });

  late Database db;

  setUp(() async {
    final path = '${Directory.systemTemp.path}/xta_idx_${DateTime.now().microsecondsSinceEpoch}.db';
    final plan = buildMigrationPlan();
    db = await openDatabase(path, version: databaseVersion, onCreate: plan.call, onUpgrade: plan.call);

    addTearDown(() async {
      await db.close();
      final file = File(path);
      if (await file.exists()) await file.delete();
    });
  });

  Future<String> planFor(String sql) async {
    final rows = await db.rawQuery('EXPLAIN QUERY PLAN $sql');
    return rows.map((row) => row['detail']).join(' | ');
  }

  test('the feed cache is read by index, not by scanning a week of JSON', () async {
    final plan = await planFor("SELECT * FROM $tableFeedGroupChunk WHERE hash = 'x' ORDER BY created_at DESC");

    expect(plan, contains('idx_feed_group_chunk_hash'));
    expect(plan, isNot(contains('SCAN $tableFeedGroupChunk')));
  });

  test('the ordering comes from the index too, so there is no sort step', () async {
    final plan = await planFor("SELECT * FROM $tableFeedGroupChunk WHERE hash = 'x' ORDER BY created_at DESC");

    expect(plan, isNot(contains('USE TEMP B-TREE')));
  });

  test('the cursor lookup is indexed', () async {
    final plan = await planFor("SELECT * FROM $tableFeedGroupChunk WHERE cursor_id = 1 AND hash = 'x'");

    expect(plan, contains('idx_feed_group_chunk_cursor'));
  });

  // profile_id is the second column of the composite primary key, so this
  // lookup could not use it.
  test('a group member lookup by profile is indexed', () async {
    final plan = await planFor("SELECT * FROM $tableSubscriptionGroupMember WHERE profile_id = 'x'");

    expect(plan, contains('idx_subscription_group_member_profile'));
  });

  test('lookups by primary key still use the implicit index', () async {
    final plan = await planFor("SELECT * FROM $tableSubscription WHERE id = 'x'");

    expect(plan, isNot(contains('SCAN')));
  });

  // CREATE INDEX tolerates the index already existing but not the table being
  // absent. A database that lost a table to a partly applied earlier migration
  // would then fail to open forever — bricked by an optimisation.
  test('a missing table does not stop the upgrade', () async {
    final path = '${Directory.systemTemp.path}/xta_idx_partial_${DateTime.now().microsecondsSinceEpoch}.db';
    final partial = await openDatabase(
      path,
      version: 38,
      onCreate: (db, _) async {
        // Deliberately nothing: not one of the tables the indexes name exists.
      },
    );
    await partial.close();

    final plan = buildMigrationPlan();
    final upgraded = await openDatabase(path, version: databaseVersion, onUpgrade: plan.call);

    addTearDown(() async {
      await upgraded.close();
      final file = File(path);
      if (await file.exists()) await file.delete();
    });

    expect(await upgraded.getVersion(), databaseVersion);
  });

  test('migration 39 is reachable from an older database', () async {
    final indexes = (await db.query(
      'sqlite_master',
      columns: ['name'],
      where: "type = 'index' AND name LIKE 'idx_%'",
    )).map((row) => row['name'] as String).toSet();

    expect(indexes, {
      'idx_feed_group_chunk_hash',
      'idx_feed_group_chunk_cursor',
      'idx_subscription_group_member_profile',
      'idx_feed_group_chunk_created',
      'idx_saved_tweet_saved_at',
      'idx_saved_tweet_folder_id',
      'idx_liked_tweet_liked_at',
      'idx_feed_group_cursor_created_at',
      'idx_timeline_cache_created_at',
    });
  });

  test('the saved and liked lists read newest-first by index, not by sorting', () async {
    final saved = await planFor('SELECT * FROM $tableSavedTweet ORDER BY saved_at DESC');
    final liked = await planFor('SELECT * FROM $tableLikedTweet ORDER BY liked_at DESC');

    expect(saved, isNot(contains('USE TEMP B-TREE')));
    expect(liked, isNot(contains('USE TEMP B-TREE')));
  });

  test('the weekly cleanup finds old chunks by index', () async {
    final plan = await planFor("SELECT * FROM $tableFeedGroupChunk WHERE created_at <= date('now', '-7 day')");

    expect(plan, contains('idx_feed_group_chunk_created'));
  });
}
