import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/import_data_model.dart';
import 'package:xta/settings/backup_data.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/settings/backup_rows.dart';

/// Tables a backup deliberately leaves out, and why.
///
/// Everything else in the schema has to be in the backup. Having no such
/// comparison is how tables of real user data — followed stocks, followed
/// Threads accounts, Reddit upvotes, and Threads likes that exist nowhere but
/// this device — went unbacked-up for as long as they did: adding a table and
/// forgetting the section cost nothing and said nothing.
const _deliberatelyNotBackedUp = {
  // Caches of what a server already served, dropped after a week anyway.
  tableFeedGroupChunk,
  tableFeedGroupCursor,
  tableTimelineCache,
  // Removed from the schema in migration 30; nothing has rows here.
  tablePostNotification,
  // "This media is already on Immich" — a note about a server, not about the
  // reader. Restoring it onto a phone pointed at a *different* Immich would
  // skip uploads that never happened there, and a skipped upload is a worse
  // failure than a repeated one: Immich dedupes by checksum, so the cost of
  // leaving this out is an upload the server discards.
  tableImmichUpload,
};

/// sqflite's own bookkeeping, which is not part of this app's schema.
bool _isSqliteInternal(String name) => name.startsWith('sqlite_') || name == 'android_metadata';

Future<Set<String>> _tablesInSchema() async {
  final database = await Repository.readOnly();
  final rows = await database.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'");

  return rows.map((row) => row['name'] as String).where((name) => !_isSqliteInternal(name)).toSet();
}

/// Plugin tables a backup deliberately skips.
const _pluginTablesNotBackedUp = {
  // "This media is already on Immich" — a note about a server the reader still
  // has, not about anything they made here.
  tableImmichUpload,
};

void main() {
  setUpAll(() async {
    sqfliteFfiInit();
    databaseFactory = databaseFactoryFfi;
    final dir = await Directory.systemTemp.createTemp('xta_backup_coverage_test');
    await databaseFactory.setDatabasesPath(dir.path);
    await Repository().migrate();
  });

  test('every table in the schema is either backed up or deliberately not', () async {
    // The sections are what the document *can* carry, so this asks the question
    // of the shape rather than of one export's contents.
    final everySection = backupTables(
      SettingsData(
        searchSubscriptions: const [],
        userSubscriptions: const [],
        // Derived from the plugin registry, not listed: that is the point of
        // the sections — a new plugin's table is covered by declaring it.
        pluginRows: {for (final section in pluginBackupSections()) section.jsonKey: const []},
        profileNotes: const [],
        antennas: const [],
        localPosts: const [],
        subscriptionGroups: const [],
        subscriptionGroupMembers: const [],
        searchGroupMembers: const [],
        tweets: const [],
        savedTweetFolders: const [],
        likedTweets: const [],
        retweetFilters: const [],
        replyFilters: const [],
        feedReadPositions: const [],
        accounts: const [],
      ),
      includeReadPositions: true,
    ).keys.toSet();

    final unaccountedFor = (await _tablesInSchema()).difference(everySection).difference(_deliberatelyNotBackedUp);

    expect(
      unaccountedFor,
      isEmpty,
      reason:
          'These tables are in the schema but no backup section writes them. Either add a section in '
          'backup_data.dart, or say why not in _deliberatelyNotBackedUp.',
    );
  });

  // The seam that stops the next plugin repeating the history above: a plugin
  // declares the tables it owns for uninstall and footprint, and the backup now
  // reads that same declaration. This asks that the two agree.
  test('every table a plugin owns is either backed up or excused', () {
    final backedUp = {for (final section in pluginBackupSections()) section.table};

    final missing = <String>{};
    for (final plugin in builtInPlugins) {
      for (final table in plugin.tables) {
        if (!backedUp.contains(table) && !_pluginTablesNotBackedUp.contains(table)) {
          missing.add('${plugin.id}: $table');
        }
      }
    }

    expect(
      missing,
      isEmpty,
      reason:
          'These plugin tables have no backup section. Add a PluginBackupSection to the plugin, or '
          'say why not in _pluginTablesNotBackedUp.',
    );
  });

  test('no two sections claim the same key or table', () {
    final sections = pluginBackupSections();

    expect(sections.map((e) => e.jsonKey).toSet(), hasLength(sections.length));
    expect(sections.map((e) => e.table).toSet(), hasLength(sections.length));
  });

  // `ImportDataModel` logs a rejected insert and carries on, so a section whose
  // `toMap()` does not match its columns restores nothing and says nothing. A
  // JSON round trip cannot see that — only a real insert can.
  test('the newly covered sections survive an actual import', () async {
    final createdAt = DateTime.utc(2026, 1, 2, 3, 4, 5);
    final rows = backupTables(
      SettingsData(
        pluginRows: {
          'stockSubscriptions': [StockSubscription(id: 'AAPL', symbol: 'AAPL', createdAt: createdAt, inFeed: true)],
          'threadsSubscriptions': [
            ThreadsSubscription(id: 'reader', name: 'Reader', avatarUrl: null, createdAt: createdAt, inFeed: true),
          ],
          'redditLocalVotes': [RedditLocalVote(id: 'abc123')],
          'threadsLocalLikes': [ThreadsLocalLike(id: 't_like_1')],
          'blueskyLocalLikes': [BlueskyLocalLike(id: 'at://did:plc:a/app.bsky.feed.post/1')],
        },
      ),
      includeReadPositions: false,
    );

    await ImportDataModel().importData(rows);

    final database = await Repository.writable();
    expect((await database.query(tableStockSubscription)).single['symbol'], 'AAPL');
    expect((await database.query(tableThreadsSubscription)).single['name'], 'Reader');
    expect((await database.query(tableRedditLocalVote)).single['id'], 'abc123');
    expect((await database.query(tableThreadsLocalLike)).single['id'], 't_like_1');
    expect((await database.query(tableBlueskyLocalLike)).single['id'], 'at://did:plc:a/app.bsky.feed.post/1');
  });

  test('nothing is excluded that no longer exists', () async {
    // An exclusion for a table that was dropped is a note about code that is
    // gone, and it hides the next real omission behind a stale name.
    final schema = await _tablesInSchema();

    expect(_deliberatelyNotBackedUp.difference(schema).difference({tablePostNotification}), isEmpty);
  });
}
