import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:xta/client/client.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/database/timeline_cache.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';

TweetChain _chain(String id, {String text = 'hello'}) => TweetChain(
  id: id,
  tweets: [
    TweetWithCard()
      ..idStr = id
      ..fullText = text,
  ],
  isPinned: false,
);

TweetStatus _page(List<TweetChain> chains, {String? cursorBottom, String? cursorShowMore}) =>
    TweetStatus(chains: chains, cursorBottom: cursorBottom, cursorTop: null, cursorShowMore: cursorShowMore);

void main() {
  setUpAll(() {
    sqfliteFfiInit();
    databaseFactory = databaseFactoryFfi;
  });

  late Database db;
  late DateTime clock;

  setUp(() async {
    final path = '${Directory.systemTemp.path}/xta_cache_${DateTime.now().microsecondsSinceEpoch}.db';
    final plan = buildMigrationPlan();
    db = await openDatabase(path, version: databaseVersion, onCreate: plan.call, onUpgrade: plan.call);
    clock = DateTime.utc(2026, 7, 25, 12);

    addTearDown(() async {
      await db.close();
      final file = File(path);
      if (await file.exists()) await file.delete();
    });
  });

  TimelineCache cache() => TimelineCache(db, now: () => clock);

  test('migration 38 creates the cache table', () async {
    final tables = (await db.query(
      'sqlite_master',
      columns: ['name'],
      where: "type = 'table'",
    )).map((row) => row['name'] as String).toSet();

    expect(tables, contains(tableTimelineCache));
  });

  test('a written thread reads back', () async {
    final key = TimelineCache.threadKey('123');
    await cache().write(key, _page([_chain('123', text: 'the post')]));

    final read = await cache().read(key, maxAge: const Duration(hours: 1));

    expect(read!.chains, hasLength(1));
    expect(read.chains.first.id, '123');
    expect(read.chains.first.tweets.first.fullText, 'the post');
  });

  test('an entry older than maxAge is a miss', () async {
    final key = TimelineCache.threadKey('123');
    await cache().write(key, _page([_chain('123')]));

    clock = clock.add(const Duration(hours: 2));

    expect(await cache().read(key, maxAge: const Duration(hours: 1)), isNull);
  });

  // The offline case: something the reader saw before beats an error screen.
  test('a stale entry is still readable through readStale', () async {
    final key = TimelineCache.threadKey('123');
    await cache().write(key, _page([_chain('123')]));

    clock = clock.add(const Duration(days: 30));

    expect(await cache().read(key, maxAge: const Duration(hours: 1)), isNull);
    expect((await cache().readStale(key))!.chains, hasLength(1));
  });

  test('writing the same key twice keeps one row, the newer one', () async {
    final key = TimelineCache.threadKey('123');
    await cache().write(key, _page([_chain('123', text: 'first')]));
    clock = clock.add(const Duration(minutes: 1));
    await cache().write(key, _page([_chain('123', text: 'second')]));

    expect((await db.query(tableTimelineCache)).length, 1);
    final read = await cache().read(key, maxAge: const Duration(hours: 1));
    expect(read!.chains.first.tweets.first.fullText, 'second');
  });

  test('an empty result never overwrites what is cached', () async {
    final key = TimelineCache.threadKey('123');
    await cache().write(key, _page([_chain('123', text: 'kept')]));

    await cache().write(key, _page(const []));

    final read = await cache().read(key, maxAge: const Duration(hours: 1));
    expect(read!.chains.first.tweets.first.fullText, 'kept');
  });

  // Without the cursor a cache hit would return a page that cannot be paged
  // past, silently truncating the timeline at whatever was cached.
  test('the cursor survives the round trip so pagination continues', () async {
    final key = TimelineCache.profileKey('7', 'tweets', includeReplies: false);
    await cache().write(key, _page([_chain('7')], cursorBottom: 'CURSOR-2'));

    final read = await cache().read(key, maxAge: const Duration(hours: 1));

    expect(read!.cursorBottom, 'CURSOR-2');
  });

  // Threads often land with only the focal post plus X's show-more cursor; if
  // that cursor is dropped on cache hit the reader never gets a prompt.
  test('the show-more cursor survives the round trip', () async {
    final key = TimelineCache.threadKey('99');
    await cache().write(key, _page([_chain('99')], cursorShowMore: 'SHOWMORE-Z'));

    final read = await cache().read(key, maxAge: const Duration(hours: 1));

    expect(read!.cursorShowMore, 'SHOWMORE-Z');
  });

  test('remove drops a key so the next read misses', () async {
    final key = TimelineCache.threadKey('1');
    await cache().write(key, _page([_chain('1')]));
    await cache().remove(key);

    expect(await cache().read(key, maxAge: const Duration(hours: 1)), isNull);
  });

  test('a missing key is a miss, not an error', () async {
    expect(await cache().read('thread:nope', maxAge: const Duration(hours: 1)), isNull);
    expect(await cache().readStale('thread:nope'), isNull);
  });

  // Written by an older build whose model has since changed.
  test('an entry that no longer decodes is a miss, not a crash', () async {
    await db.insert(tableTimelineCache, {
      'key': 'thread:bad',
      'response': 'not json at all',
      'created_at': clock.toIso8601String(),
    });

    expect(await cache().read('thread:bad', maxAge: const Duration(hours: 1)), isNull);
    expect(await cache().readStale('thread:bad'), isNull);
  });

  test('threads and profile variants never share a key', () {
    expect(TimelineCache.threadKey('1'), isNot(TimelineCache.profileKey('1', 'tweets', includeReplies: false)));
    expect(
      TimelineCache.profileKey('1', 'tweets', includeReplies: true),
      isNot(TimelineCache.profileKey('1', 'tweets', includeReplies: false)),
    );
    expect(
      TimelineCache.profileKey('1', 'media', includeReplies: false),
      isNot(TimelineCache.profileKey('1', 'tweets', includeReplies: false)),
    );
  });

  test('clear empties the table', () async {
    await cache().write(TimelineCache.threadKey('1'), _page([_chain('1')]));
    await cache().clear();

    expect(await db.query(tableTimelineCache), isEmpty);
  });
}
