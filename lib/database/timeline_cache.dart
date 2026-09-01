import 'dart:convert';

import 'package:logging/logging.dart';
import 'package:xta/client/client.dart';
import 'package:xta/database/repository.dart';
import 'package:sqflite/sqflite.dart';

/// Last-known first page of a thread or a profile timeline.
///
/// Group feeds already persist their pages in `feed_group_chunk`; threads and
/// profile timelines did not, so opening the same post twice cost two
/// `TweetDetail` requests and showed nothing at all offline or while every
/// account was rate limited.
///
/// Only the *first* page is kept. It is the page every visit starts from, it
/// bounds what the cache can grow to, and later pages are worth far less
/// because a reader who has scrolled that far will happily wait.
class TimelineCache {
  static final log = Logger('TimelineCache');

  final Database database;
  final DateTime Function() now;

  TimelineCache(this.database, {DateTime Function()? now}) : now = now ?? DateTime.now;

  static String threadKey(String tweetId) => 'thread:$tweetId';

  static String profileKey(String userId, String type, {required bool includeReplies}) =>
      'profile:$userId:$type:${includeReplies ? 'replies' : 'noreplies'}';

  /// The cached page for [key], or null when nothing is stored, the entry is
  /// older than [maxAge], or it can no longer be decoded.
  ///
  /// A stale entry is treated as a miss rather than deleted: if the network is
  /// down the caller gets nothing new either way, and keeping the row means a
  /// later [readStale] can still show something.
  Future<TweetStatus?> read(String key, {required Duration maxAge}) async {
    final row = await _row(key);
    if (row == null) {
      return null;
    }

    final cachedAt = DateTime.tryParse(row['created_at'] as String? ?? '');
    if (cachedAt == null || now().difference(cachedAt) > maxAge) {
      return null;
    }

    return _decode(key, row['response'] as String?);
  }

  /// The cached page whatever its age. For the offline case: something the
  /// reader saw before beats an error screen.
  Future<TweetStatus?> readStale(String key) async {
    final row = await _row(key);
    return row == null ? null : _decode(key, row['response'] as String?);
  }

  /// The cursors are stored alongside the chains: without them a cache hit
  /// would return a page that cannot be paged past (or offer X's "show
  /// additional replies" prompt), silently truncating the conversation.
  Future<void> write(String key, TweetStatus status) async {
    if (status.chains.isEmpty) {
      return;
    }

    try {
      await database.insert(tableTimelineCache, {
        'key': key,
        'response': jsonEncode({
          'chains': status.chains.map((c) => c.toJson()).toList(),
          'cursorBottom': status.cursorBottom,
          'cursorTop': status.cursorTop,
          'cursorShowMore': status.cursorShowMore,
        }),
        'created_at': now().toIso8601String(),
      }, conflictAlgorithm: ConflictAlgorithm.replace);
    } catch (e) {
      // A cache write must never be the reason a timeline fails to display.
      log.warning('Could not cache $key: $e');
    }
  }

  /// Drops one entry so the next read misses and the network is tried again.
  Future<void> remove(String key) async {
    try {
      await database.delete(tableTimelineCache, where: 'key = ?', whereArgs: [key]);
    } catch (e) {
      log.warning('Could not remove $key: $e');
    }
  }

  Future<void> clear() async => database.delete(tableTimelineCache);

  Future<Map<String, Object?>?> _row(String key) async {
    try {
      final rows = await database.query(tableTimelineCache, where: 'key = ?', whereArgs: [key], limit: 1);
      return rows.isEmpty ? null : rows.first;
    } catch (e) {
      log.warning('Could not read $key: $e');
      return null;
    }
  }

  TweetStatus? _decode(String key, String? response) {
    if (response == null) {
      return null;
    }
    try {
      final decoded = jsonDecode(response);
      if (decoded is! Map<String, dynamic>) {
        return null;
      }
      final chains = decoded['chains'];
      if (chains is! List) {
        return null;
      }
      return TweetStatus(
        chains: chains.map((e) => TweetChain.fromJson(e as Map<String, dynamic>)).toList(),
        cursorBottom: decoded['cursorBottom'] as String?,
        cursorTop: decoded['cursorTop'] as String?,
        cursorShowMore: decoded['cursorShowMore'] as String?,
      );
    } catch (e) {
      // Written by an older build whose tweet model has since changed. Not
      // worth surfacing: the caller simply fetches.
      log.info('Dropping unreadable cache entry $key: $e');
      return null;
    }
  }
}
