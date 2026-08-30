import 'dart:convert';

import 'package:quax/group/group_model.dart';
import 'package:logging/logging.dart';
import 'package:sqflite/sqflite.dart';
import 'dart:async';

import 'package:sqflite_migration_plan/migration/sql.dart';
import 'package:sqflite_migration_plan/sqflite_migration_plan.dart';
import 'package:uuid/uuid.dart';

const String databaseName = 'quax.db';

const String tableFeedGroupChunk = 'feed_group_chunk';
const String tableTimelineCache = 'timeline_cache';
const String tableFeedGroupCursor = 'feed_group_cursor';

const String tableSavedTweet = 'saved_tweet';
const String tableSavedTweetFolder = 'saved_tweet_folder';
const String tableLikedTweet = 'liked_tweet';
const String tableSearchSubscription = 'search_subscription';
const String tableSubstackSubscription = 'substack_subscription';
const String tableRedditSubscription = 'reddit_subscription';
const String tableSearchSubscriptionGroupMember = 'search_subscription_group_member';
const String tableSubscription = 'subscription';
const String tableSubscriptionGroup = 'subscription_group';
const String tableSubscriptionGroupMember = 'subscription_group_member';

const String tableAccounts = 'accounts';
const String tablePostNotification = 'post_notification';
const String tableRetweetFilter = 'retweet_filter';
const String tableReplyFilter = 'reply_filter';
const String tableFeedReadPosition = 'feed_read_position';

const int databaseVersion = 42;

/// Schema migration plan from the earliest versions through [databaseVersion].
/// Extracted so characterization tests can open a DB at an intermediate version
/// and upgrade to current without going through [Repository.migrate].
MigrationPlan buildMigrationPlan() => MigrationPlan({
  2: [
    SqlMigration(
      'CREATE TABLE IF NOT EXISTS following (id INTEGER PRIMARY KEY, screen_name VARCHAR, name VARCHAR, profile_image_url_https VARCHAR, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)',
    ),
  ],
  3: [
    SqlMigration(
      'CREATE TABLE IF NOT EXISTS following_group (id INTEGER PRIMARY KEY, name VARCHAR NOT NULL, icon VARCHAR NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)',
    ),
    SqlMigration('CREATE TABLE IF NOT EXISTS following_group_profile (group_id INTEGER, profile_id INTEGER)'),
  ],
  4: [
    // Change the following table's "id" field to be a VARCHAR
    SqlMigration('ALTER TABLE following RENAME TO following_old'),
    SqlMigration(
      'CREATE TABLE following (id VARCHAR PRIMARY KEY, screen_name VARCHAR, name VARCHAR, profile_image_url_https VARCHAR, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)',
    ),
    SqlMigration(
      'INSERT INTO following (id, screen_name, name, profile_image_url_https, created_at) SELECT id, screen_name, name, profile_image_url_https, created_at FROM following_old',
    ),
    SqlMigration('DROP TABLE following_old'),
  ],
  5: [
    // Change the following_group_profile table's "profile_id" field to be a VARCHAR to match the referenced table
    SqlMigration('ALTER TABLE following_group_profile RENAME TO following_group_profile_old'),
    SqlMigration('CREATE TABLE following_group_profile (group_id INTEGER, profile_id VARCHAR)'),
    SqlMigration(
      'INSERT INTO following_group_profile (group_id, profile_id) SELECT group_id, profile_id FROM following_group_profile_old',
    ),
    SqlMigration('DROP TABLE following_group_profile_old'),
  ],
  6: [
    // Rename the old following tables to match the names in the UI
    SqlMigration('ALTER TABLE following RENAME TO $tableSubscription'),
    SqlMigration('ALTER TABLE following_group RENAME TO $tableSubscriptionGroup'),
    SqlMigration('ALTER TABLE following_group_profile RENAME TO $tableSubscriptionGroupMember'),
  ],
  7: [
    // Add the table for saved tweets
    SqlMigration(
      'CREATE TABLE IF NOT EXISTS $tableSavedTweet (id VARCHAR PRIMARY KEY, content TEXT NOT NULL, saved_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)',
      reverseSql: 'DROP TABLE $tableSavedTweet',
    ),
  ],
  8: [
    // Add a primary key to the $TABLE_SUBSCRIPTION_GROUP_MEMBER table to prevent duplicates
    SqlMigration('ALTER TABLE $tableSubscriptionGroupMember RENAME TO ${tableSubscriptionGroupMember}_old'),
    SqlMigration(
      'CREATE TABLE $tableSubscriptionGroupMember (group_id INTEGER, profile_id VARCHAR, CONSTRAINT pk_$tableSubscriptionGroupMember PRIMARY KEY (group_id, profile_id))',
    ),
    SqlMigration(
      'INSERT INTO $tableSubscriptionGroupMember (group_id, profile_id) SELECT group_id, profile_id FROM ${tableSubscriptionGroupMember}_old',
    ),
    SqlMigration('DROP TABLE ${tableSubscriptionGroupMember}_old'),
  ],
  9: [
    // Add a new ID field for subscription groups for a UUID to determine uniqueness across devices
    SqlMigration('ALTER TABLE $tableSubscriptionGroup ADD COLUMN uuid VARCHAR NULL'),
    SqlMigration('ALTER TABLE $tableSubscriptionGroupMember ADD COLUMN group_uuid VARCHAR NULL'),

    // Generate a UUID for each existing subscription group
    Migration(
      Operation((db) async {
        var uuid = const Uuid();

        // Update the existing subscription group and all of its members with the new ID
        var groups = await db.query(tableSubscriptionGroup);
        for (var group in groups) {
          var oldId = group['id'];
          var newId = uuid.v4();

          db.update(tableSubscriptionGroup, {'uuid': newId}, where: 'id = ?', whereArgs: [oldId]);

          db.update(tableSubscriptionGroupMember, {'group_uuid': newId}, where: 'group_id = ?', whereArgs: [oldId]);
        }
      }),
    ),

    // Replace the old ID fields with the new ones
    SqlMigration('ALTER TABLE $tableSubscriptionGroup RENAME TO ${tableSubscriptionGroup}_old'),
    SqlMigration(
      'CREATE TABLE $tableSubscriptionGroup (id VARCHAR PRIMARY KEY, name VARCHAR NOT NULL, icon VARCHAR NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)',
    ),
    SqlMigration(
      'INSERT INTO $tableSubscriptionGroup (id, name, icon, created_at) SELECT uuid, name, icon, created_at FROM ${tableSubscriptionGroup}_old',
    ),

    SqlMigration('ALTER TABLE $tableSubscriptionGroupMember RENAME TO ${tableSubscriptionGroupMember}_old'),
    SqlMigration(
      'CREATE TABLE $tableSubscriptionGroupMember (group_id VARCHAR, profile_id VARCHAR, CONSTRAINT pk_$tableSubscriptionGroupMember PRIMARY KEY (group_id, profile_id))',
    ),
    SqlMigration(
      'INSERT INTO $tableSubscriptionGroupMember (group_id, profile_id) SELECT group_uuid, profile_id FROM ${tableSubscriptionGroupMember}_old',
    ),
  ],
  10: [
    // Drop the old subscription group tables now that we've replaced the IDs
    SqlMigration('DROP TABLE ${tableSubscriptionGroup}_old'),
    SqlMigration('DROP TABLE ${tableSubscriptionGroupMember}_old'),
  ],
  11: [
    // Add columns for the subscription group settings
    SqlMigration('ALTER TABLE $tableSubscriptionGroup ADD COLUMN include_replies BOOLEAN DEFAULT true'),
    SqlMigration('ALTER TABLE $tableSubscriptionGroup ADD COLUMN include_retweets BOOLEAN DEFAULT true'),
  ],
  12: [
    // Insert a dummy record for the "All" subscription group
    Migration(
      Operation((db) async {
        await db.insert(tableSubscriptionGroup, {
          'id': '-1',
          'name': 'All',
          'icon': 'rss_feed',
        }, conflictAlgorithm: ConflictAlgorithm.replace);
      }),
      reverse: Operation((db) async {
        await db.delete(tableSubscriptionGroup, where: 'id = ?', whereArgs: ['-1']);
      }),
    ),
  ],
  13: [
    // Duplicate migration 12, as some people had deleted the "All" group when it displayed twice in the groups list
    Migration(
      Operation((db) async {
        await db.insert(tableSubscriptionGroup, {
          'id': '-1',
          'name': 'All',
          'icon': 'rss_feed',
        }, conflictAlgorithm: ConflictAlgorithm.replace);
      }),
      reverse: Operation((db) async {
        await db.delete(tableSubscriptionGroup, where: 'id = ?', whereArgs: ['-1']);
      }),
    ),
  ],
  14: [
    // Add a "verified" column to the subscriptions table
    SqlMigration(
      'ALTER TABLE $tableSubscription ADD COLUMN verified BOOLEAN DEFAULT 0',
      reverseSql: 'ALTER TABLE $tableSubscription DROP COLUMN verified',
    ),
  ],
  15: [
    // Re-apply migration 14 in a different way, as it looks like it didn't apply for some people
    SqlMigration('ALTER TABLE $tableSubscription RENAME TO ${tableSubscription}_old'),
    SqlMigration(
      'CREATE TABLE $tableSubscription (id VARCHAR PRIMARY KEY, screen_name VARCHAR, name VARCHAR, profile_image_url_https VARCHAR, verified BOOLEAN DEFAULT 0, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)',
    ),
    SqlMigration(
      'INSERT INTO $tableSubscription (id, screen_name, name, profile_image_url_https, created_at) SELECT id, screen_name, name, profile_image_url_https, created_at FROM ${tableSubscription}_old',
    ),
    SqlMigration('DROP TABLE ${tableSubscription}_old'),
  ],
  16: [
    // Add a "color" column to the subscription groups table, and set a default icon for existing groups
    SqlMigration(
      'ALTER TABLE $tableSubscriptionGroup ADD COLUMN color INT DEFAULT NULL',
      reverseSql: 'ALTER TABLE $tableSubscriptionGroup DROP COLUMN color',
    ),

    Migration(
      Operation((db) async {
        await db.update(
          tableSubscriptionGroup,
          {'icon': defaultGroupIcon},
          where: "icon IS NULL OR icon = '' OR icon = ?",
          whereArgs: ['rss_feed'],
        );
      }),
    ),
  ],
  17: [
    // Add some tables to temporarily store feed chunks, used for caching and pagination
    SqlMigration(
      'CREATE TABLE IF NOT EXISTS $tableFeedGroupCursor (id INTEGER PRIMARY KEY, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)',
      reverseSql: 'DROP TABLE $tableFeedGroupCursor',
    ),
    SqlMigration(
      'CREATE TABLE IF NOT EXISTS $tableFeedGroupChunk (cursor_id INTEGER NOT NULL, hash VARCHAR NOT NULL, cursor_top VARCHAR, cursor_bottom VARCHAR, response VARCHAR, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)',
      reverseSql: 'DROP TABLE $tableFeedGroupChunk',
    ),
  ],
  18: [
    // Add support for saving searches
    SqlMigration(
      'CREATE TABLE IF NOT EXISTS $tableSearchSubscription (id VARCHAR PRIMARY KEY, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)',
      reverseSql: 'DROP TABLE $tableSearchSubscription',
    ),
    SqlMigration(
      'CREATE TABLE IF NOT EXISTS $tableSearchSubscriptionGroupMember (group_id VARCHAR, search_id VARCHAR, CONSTRAINT pk_$tableSearchSubscription PRIMARY KEY (group_id, search_id))',
      reverseSql: 'DROP TABLE $tableSearchSubscriptionGroupMember',
    ),
  ],
  19: [
    // Add a new column for saved tweet user IDs, and extract them from all existing records
    SqlMigration(
      'ALTER TABLE $tableSavedTweet ADD COLUMN user_id VARCHAR DEFAULT NULL',
      reverseSql: 'ALTER TABLE $tableSavedTweet DROP COLUMN user_id',
    ),
    Migration(
      Operation((db) async {
        var tweets = await db.query(tableSavedTweet, columns: ['id', 'content']);
        var batch = db.batch();

        for (var tweet in tweets) {
          var content = tweet['content'] as String?;
          if (content == null) {
            continue;
          }

          var decodedTweet = jsonDecode(content);
          if (decodedTweet == null) {
            continue;
          }

          var userId = decodedTweet['user']?['id_str'] as String?;
          if (userId != null) {
            batch.update(tableSavedTweet, {'user_id': userId}, where: 'id = ?', whereArgs: [tweet['id']]);
          }
        }

        await batch.commit();
      }),
    ),
  ],
  20: [
    Migration(
      Operation((db) async {
        await db.update(
          tableSubscriptionGroup,
          {'icon': defaultGroupIcon},
          where: "icon IS NULL OR icon = '' OR icon = ?",
          whereArgs: ['rss'],
        );
      }),
    ),
  ],
  21: [
    // create table for storing twitter accounts
    SqlMigration(
      'CREATE TABLE IF NOT EXISTS $tableAccounts (id TEXT PRIMARY KEY, password TEXT, email TEXT, auth_header VARCHAR)',
    ),
  ],
  22: [
    // Add screen_name column and remove password/email columns from accounts table
    SqlMigration('ALTER TABLE $tableAccounts RENAME TO ${tableAccounts}_old'),
    SqlMigration(
      'CREATE TABLE $tableAccounts (id TEXT PRIMARY KEY, auth_header VARCHAR, screen_name VARCHAR DEFAULT NULL)',
    ),
    SqlMigration('INSERT INTO $tableAccounts (id, auth_header) SELECT id, auth_header FROM ${tableAccounts}_old'),
    SqlMigration('DROP TABLE ${tableAccounts}_old'),
  ],
  23: [SqlMigration('ALTER TABLE $tableSubscription ADD COLUMN in_feed BOOLEAN DEFAULT 1')],
  24: [
    // Account not-found health columns for the selection strategy (timestamp as ISO-8601 TEXT).
    // Rate-limit (429) state is tracked in memory per endpoint, not persisted.
    SqlMigration('ALTER TABLE $tableAccounts ADD COLUMN last_not_found_at TEXT DEFAULT NULL'),
    SqlMigration('ALTER TABLE $tableAccounts ADD COLUMN consecutive_not_found INTEGER DEFAULT 0'),
  ],
  25: [
    // Folders for saved posts: a folder table plus a nullable folder_id on saved tweets.
    // A saved post belongs to at most one folder (NULL means "unfiled").
    SqlMigration(
      'CREATE TABLE IF NOT EXISTS $tableSavedTweetFolder (id VARCHAR PRIMARY KEY, name VARCHAR NOT NULL, position INTEGER DEFAULT 0, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)',
      reverseSql: 'DROP TABLE $tableSavedTweetFolder',
    ),
    SqlMigration(
      'ALTER TABLE $tableSavedTweet ADD COLUMN folder_id VARCHAR DEFAULT NULL',
      reverseSql: 'ALTER TABLE $tableSavedTweet DROP COLUMN folder_id',
    ),
  ],
  26: [
    // Liked posts: a local-only table mirroring saved_tweet. A "like" never leaves the device.
    SqlMigration(
      'CREATE TABLE IF NOT EXISTS $tableLikedTweet (id VARCHAR PRIMARY KEY, content TEXT NOT NULL, user_id VARCHAR DEFAULT NULL, liked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)',
      reverseSql: 'DROP TABLE $tableLikedTweet',
    ),
  ],
  27: [
    // Per-group feed ordering: popular (Top search results) vs recent (Latest, the default).
    SqlMigration(
      'ALTER TABLE $tableSubscriptionGroup ADD COLUMN popular BOOLEAN DEFAULT 0',
      reverseSql: 'ALTER TABLE $tableSubscriptionGroup DROP COLUMN popular',
    ),
  ],
  28: [
    // Users watched for new-post notifications, with the newest post id
    // already handled (NULL = baseline not yet established).
    SqlMigration(
      'CREATE TABLE IF NOT EXISTS $tablePostNotification (user_id VARCHAR PRIMARY KEY, screen_name VARCHAR NOT NULL, name VARCHAR, last_tweet_id VARCHAR DEFAULT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)',
      reverseSql: 'DROP TABLE $tablePostNotification',
    ),
  ],
  29: [
    // Users whose retweets are hidden from all feeds ("turn off reposts").
    SqlMigration(
      'CREATE TABLE IF NOT EXISTS $tableRetweetFilter (user_id VARCHAR PRIMARY KEY, screen_name VARCHAR NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)',
      reverseSql: 'DROP TABLE $tableRetweetFilter',
    ),
  ],
  30: [
    // The new-post notification feature was removed; drop its table.
    SqlMigration('DROP TABLE IF EXISTS $tablePostNotification'),
  ],
  31: [
    // Custom feed mode with a per-group content filter (sfw/default/nsfw).
    SqlMigration(
      'ALTER TABLE $tableSubscriptionGroup ADD COLUMN custom BOOLEAN DEFAULT 0',
      reverseSql: 'ALTER TABLE $tableSubscriptionGroup DROP COLUMN custom',
    ),
    SqlMigration(
      "ALTER TABLE $tableSubscriptionGroup ADD COLUMN content_filter VARCHAR DEFAULT 'default'",
      reverseSql: 'ALTER TABLE $tableSubscriptionGroup DROP COLUMN content_filter',
    ),
  ],
  32: [
    // Last-read chain per group feed, for the "You're caught up" divider.
    // chain_created_at is ISO-8601 TEXT, like the account-health columns.
    SqlMigration(
      'CREATE TABLE IF NOT EXISTS $tableFeedReadPosition (group_id VARCHAR PRIMARY KEY, chain_id VARCHAR NOT NULL, chain_created_at TEXT, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)',
      reverseSql: 'DROP TABLE $tableFeedReadPosition',
    ),
  ],
  33: [
    // Per-folder toggle: download a post's images when it's filed here.
    SqlMigration(
      'ALTER TABLE $tableSavedTweetFolder ADD COLUMN auto_download BOOLEAN DEFAULT 0',
      reverseSql: 'ALTER TABLE $tableSavedTweetFolder DROP COLUMN auto_download',
    ),
  ],
  34: [
    // Pinned groups and manual ordering for the groups list. Positions are
    // backfilled from the default alphabetical order.
    SqlMigration(
      'ALTER TABLE $tableSubscriptionGroup ADD COLUMN pinned BOOLEAN DEFAULT 0',
      reverseSql: 'ALTER TABLE $tableSubscriptionGroup DROP COLUMN pinned',
    ),
    SqlMigration(
      'ALTER TABLE $tableSubscriptionGroup ADD COLUMN position INTEGER DEFAULT 0',
      reverseSql: 'ALTER TABLE $tableSubscriptionGroup DROP COLUMN position',
    ),
    Migration(
      Operation((db) async {
        var groups = await db.query(
          tableSubscriptionGroup,
          columns: ['id'],
          where: "id != '-1'",
          orderBy: 'name COLLATE NOCASE ASC',
        );
        var batch = db.batch();
        for (var (i, group) in groups.indexed) {
          batch.update(tableSubscriptionGroup, {'position': i}, where: 'id = ?', whereArgs: [group['id']]);
        }
        await batch.commit(noResult: true);
      }),
    ),
  ],
  35: [
    // Per-group identity mark: optional emoji + style override. Existing
    // non-default icons keep showing via mark_style=2 (symbol).
    SqlMigration(
      'ALTER TABLE $tableSubscriptionGroup ADD COLUMN emoji TEXT',
      reverseSql: 'ALTER TABLE $tableSubscriptionGroup DROP COLUMN emoji',
    ),
    SqlMigration(
      'ALTER TABLE $tableSubscriptionGroup ADD COLUMN mark_style INTEGER NOT NULL DEFAULT 0',
      reverseSql: 'ALTER TABLE $tableSubscriptionGroup DROP COLUMN mark_style',
    ),
    Migration(
      Operation((db) async {
        const defaultIcon = '{"pack":"custom","key":"rss_feed"}';
        final groups = await db.query(tableSubscriptionGroup, columns: ['id', 'icon']);
        final batch = db.batch();
        for (final group in groups) {
          final icon = group['icon'] as String?;
          final isCustom =
              icon != null && icon.isNotEmpty && icon != 'rss' && icon != 'rss_feed' && icon != defaultIcon;
          if (isCustom) {
            batch.update(tableSubscriptionGroup, {'mark_style': 2}, where: 'id = ?', whereArgs: [group['id']]);
          }
        }
        await batch.commit(noResult: true);
      }),
    ),
  ],
  36: [
    // Custom-feed rules beyond the content filter: engagement thresholds and
    // muted keywords. 0 / NULL mean "no threshold" and "nothing muted", so
    // existing feeds behave exactly as before.
    SqlMigration(
      'ALTER TABLE $tableSubscriptionGroup ADD COLUMN min_likes INTEGER NOT NULL DEFAULT 0',
      reverseSql: 'ALTER TABLE $tableSubscriptionGroup DROP COLUMN min_likes',
    ),
    SqlMigration(
      'ALTER TABLE $tableSubscriptionGroup ADD COLUMN min_retweets INTEGER NOT NULL DEFAULT 0',
      reverseSql: 'ALTER TABLE $tableSubscriptionGroup DROP COLUMN min_retweets',
    ),
    SqlMigration(
      'ALTER TABLE $tableSubscriptionGroup ADD COLUMN muted_keywords TEXT',
      reverseSql: 'ALTER TABLE $tableSubscriptionGroup DROP COLUMN muted_keywords',
    ),
  ],
  // The reply filter was written as 36 on its own branch, before the
  // custom-feed rules took that number. Two sets of changes cannot share a
  // version: whoever upgraded on one build would never receive the other.
  37: [
    // Per-user "hide replies", the sibling of retweet_filter.
    SqlMigration(
      'CREATE TABLE IF NOT EXISTS $tableReplyFilter (user_id VARCHAR PRIMARY KEY, screen_name VARCHAR NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)',
      reverseSql: 'DROP TABLE $tableReplyFilter',
    ),
  ],
  38: [
    // Threads and profile timelines are re-fetched from X on every visit.
    // This caches the first page of each so revisiting one paints instantly
    // and still works offline or while every account is rate limited.
    // Group feeds already have their own cache in $tableFeedGroupChunk.
    SqlMigration(
      'CREATE TABLE IF NOT EXISTS $tableTimelineCache (key VARCHAR PRIMARY KEY, response TEXT NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)',
      reverseSql: 'DROP TABLE $tableTimelineCache',
    ),
  ],
  39: [
    // The schema had no indexes at all. Most queries are by a declared PRIMARY
    // KEY and so get an implicit one, but these did not:
    // $tableFeedGroupChunk has no primary key and holds a week of feed JSON,
    // yet every feed load queries it by hash — a full scan over large TEXT rows
    // that grows with use. Both of its query shapes are covered: `hash = ?`
    // ordered by created_at, and `cursor_id = ? AND hash = ?`.
    // profile_id is the *second* column of the group-member composite key, so
    // looking a subscription up by it could not use that index.
    Migration(Operation(_createIndexes), reverse: Operation(_dropIndexes)),
  ],
  40: [
    // Substack publications lived in a preferences blob, which is why they
    // could not join a subscription group: group membership joins profile ids
    // against subscription tables, and a blob has no rows to join. They are a
    // third kind of subscription now, alongside users and saved searches.
    // The blob is left where it is; it is imported on first read rather than
    // migrated here, because the preferences are not reachable from a
    // migration.
    SqlMigration(
      'CREATE TABLE IF NOT EXISTS $tableSubstackSubscription ('
      'id VARCHAR PRIMARY KEY, base_url VARCHAR NOT NULL, name VARCHAR NOT NULL, '
      'logo_url VARCHAR, in_feed INTEGER NOT NULL DEFAULT 1, '
      'created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)',
      reverseSql: 'DROP TABLE $tableSubstackSubscription',
    ),
  ],
  41: [
    // Groups of groups. A group with a parent is shown inside it rather than
    // beside it on the board, and the parent's feed is the union of its own
    // members and its children's. NULL is the existing behaviour: a group that
    // stands on its own.
    // Applied as an operation rather than plain SQL for the same reason as the
    // indexes above: a database that lost a table to a partially restored
    // backup must still be able to finish upgrading. A bare ALTER TABLE would
    // fail on the missing table and block every later migration with it.
    Migration(Operation(_addGroupParentColumn), reverse: Operation(_dropGroupParentColumn)),
  ],
  42: [
    // Subreddits, for the same reason publications got a table in 40: group
    // membership joins profile ids against subscription tables, and a list in
    // preferences has no rows to join. The old preference is imported on first
    // read rather than here, since a migration cannot reach preferences.
    SqlMigration(
      'CREATE TABLE IF NOT EXISTS $tableRedditSubscription ('
      'id VARCHAR PRIMARY KEY, name VARCHAR NOT NULL, '
      'in_feed INTEGER NOT NULL DEFAULT 1, '
      'created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)',
      reverseSql: 'DROP TABLE $tableRedditSubscription',
    ),
  ],
});

/// Indexes added in migration 39, applied so that a failure cannot block the
/// upgrade.
///
/// `CREATE INDEX IF NOT EXISTS` tolerates the index already existing but *not*
/// the table being absent, so a database that lost a table to a partially
/// applied earlier migration would fail here on every launch — unable to start
/// because of a pure optimisation. An index is never worth bricking an upgrade
/// for, so each is attempted independently and a failure is logged and stepped
/// over.
const Map<String, String> _indexes = {
  'idx_feed_group_chunk_hash': '$tableFeedGroupChunk (hash, created_at)',
  'idx_feed_group_chunk_cursor': '$tableFeedGroupChunk (cursor_id, hash)',
  'idx_subscription_group_member_profile': '$tableSubscriptionGroupMember (profile_id)',
};

/// Adds the nesting column, tolerating a database whose group table is gone.
Future<void> _addGroupParentColumn(Database db) async {
  try {
    await db.execute('ALTER TABLE $tableSubscriptionGroup ADD COLUMN parent_id VARCHAR');
  } catch (e) {
    // Already present, or the table is missing entirely. Neither is worth
    // stopping the upgrade for: without the column groups simply do not nest.
    Repository.log.warning('Could not add parent_id to $tableSubscriptionGroup: $e');
  }
}

Future<void> _dropGroupParentColumn(Database db) async {
  try {
    await db.execute('ALTER TABLE $tableSubscriptionGroup DROP COLUMN parent_id');
  } catch (e) {
    Repository.log.warning('Could not drop parent_id from $tableSubscriptionGroup: $e');
  }
}

Future<void> _createIndexes(Database db) async {
  for (final entry in _indexes.entries) {
    try {
      await db.execute('CREATE INDEX IF NOT EXISTS ${entry.key} ON ${entry.value}');
    } catch (e) {
      Repository.log.warning('Could not create index ${entry.key}; queries still work, just slower: $e');
    }
  }
}

Future<void> _dropIndexes(Database db) async {
  for (final name in _indexes.keys) {
    try {
      await db.execute('DROP INDEX IF EXISTS $name');
    } catch (e) {
      Repository.log.warning('Could not drop index $name: $e');
    }
  }
}

class Repository {
  static final log = Logger('Repository');

  static Future<Database>? _readOnly;

  /// A read-only handle on the database, shared by every reader.
  /// Previously used `openDatabase(..., singleInstance: false)`, which caused each of the 59 call
  /// sites to open a new connection (never closed). This leaked file descriptors and added
  /// overhead to every request.
  ///
  /// The connection cannot simply switch to `singleInstance: true`, because
  /// sqflite caches by path: whichever of the read-only and writable opens
  /// happened first would win and hand the other the wrong mode. So the reuse
  /// is kept here instead.
  static Future<Database> readOnly() {
    final cached = _readOnly;
    if (cached != null) {
      return cached;
    }

    final opening = openDatabase(databaseName, readOnly: true, singleInstance: false);
    _readOnly = opening;

    // A failure must not latch: the first read can land before the file exists.
    // Caching a rejected future would fail every later read for the life of the
    // process, which is exactly the bug fixed in TwitterHeaders.
    unawaited(
      opening.then(
        (_) {},
        onError: (Object _) {
          if (identical(_readOnly, opening)) {
            _readOnly = null;
          }
        },
      ),
    );

    return opening;
  }

  static Future<Database> writable() async {
    return openDatabase(databaseName);
  }

  Future<bool> migrate() async {
    final myMigrationPlan = buildMigrationPlan();

    await openDatabase(
      databaseName,
      version: databaseVersion,
      onUpgrade: myMigrationPlan.call,
      onCreate: myMigrationPlan.call,
      onDowngrade: myMigrationPlan.call,
    );

    // Clean up any old feed chunks and cursors
    var repository = await writable();
    await repository.delete(tableFeedGroupChunk, where: "created_at <= date('now', '-7 day')");
    await repository.delete(tableFeedGroupCursor, where: "created_at <= date('now', '-7 day')");
    await repository.delete(tableTimelineCache, where: "created_at <= date('now', '-7 day')");

    log.info('Finished migrating database');

    return true;
  }
}
