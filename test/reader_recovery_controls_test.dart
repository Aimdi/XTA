import 'dart:async';
import 'dart:io';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/tweet/feed_snapshot_cache.dart';
import 'package:xta/tweet/interleaved_items.dart';
import 'package:xta/tweet/progressive_feed_store.dart';
import 'package:xta/utils/local_json_store.dart';
import 'reader_feed_test.dart' show post;

class _Cache extends FeedSnapshotCache {
  final Future<({List<InterleavedItem> items, DateTime? at})> Function() load;
  _Cache(this.load);
  @override
  Future<({List<InterleavedItem> items, DateTime? at})> read(String key) => load();
  @override
  Future<void> write(String key, List<InterleavedItem> items) async {}
}

void main() {
  for (final stalled in [false, true]) {
    test('network proceeds when cache ${stalled ? 'stalls' : 'throws'}', () async {
      final cache = _Cache(
        () => stalled
            ? Completer<({List<InterleavedItem> items, DateTime? at})>().future
            : Future.error(StateError('damaged cache')),
      );
      final store = ProgressiveFeedStore(cache: cache, cacheTimeout: const Duration(milliseconds: 10));
      addTearDown(store.destroy);
      await store.load(
        {
          'rss': () async => [post('network')],
        },
        {'rss': 'feed:rss'},
      );
      expect(store.state.items.single.id, 'network');
      expect(store.state.sources['rss']!.loading, isFalse);
      expect(store.state.sources['rss']!.error, isNull);
    });
  }

  test('deadline covers cache time and cancelled cache cannot publish or start network', () async {
    final pending = Completer<({List<InterleavedItem> items, DateTime? at})>();
    final store = ProgressiveFeedStore(
      cache: _Cache(() => pending.future),
      timeout: const Duration(milliseconds: 10),
      cacheTimeout: const Duration(seconds: 1),
    );
    addTearDown(store.destroy);
    var fetches = 0;
    await store.load(
      {
        'rss': () async {
          fetches++;
          return [post('network')];
        },
      },
      {'rss': 'feed:rss'},
    );
    expect(store.state.sources['rss']!.error, isA<TimeoutException>());
    expect(store.state.sources['rss']!.loading, isFalse);
    pending.complete((items: [post('late-cache')], at: DateTime.now()));
    await Future<void>.delayed(Duration.zero);
    expect(fetches, 0);
    expect(store.state.items, isEmpty);
  });

  test('concurrent retries coalesce and status changes reuse the sorted list', () async {
    final store = ProgressiveFeedStore(cache: _Cache(() async => (items: <InterleavedItem>[], at: null)));
    addTearDown(store.destroy);
    var fetches = 0;
    final pending = Completer<List<InterleavedItem>>();
    await store.load(
      {
        'rss': () {
          fetches++;
          return fetches == 1 ? Future.value([post('old')]) : pending.future;
        },
      },
      {'rss': 'feed:rss'},
    );
    final items = store.state.items;
    final first = store.retry('rss');
    final second = store.retry('rss');
    expect(identical(first, second), isTrue);
    expect(identical(store.state.items, items), isTrue);
    pending.completeError(TimeoutException('still unavailable'));
    await Future.wait([first, second]);
    expect(fetches, 2);
    expect(identical(store.state.items, items), isTrue);
    expect(store.state.items.single.id, 'old');
  });

  test('late network result cannot replace a successful manual retry', () async {
    final pending = Completer<List<InterleavedItem>>();
    var fetches = 0;
    final store = ProgressiveFeedStore(
      cache: _Cache(() async => (items: <InterleavedItem>[], at: null)),
      timeout: const Duration(milliseconds: 10),
    );
    addTearDown(store.destroy);
    await store.load(
      {
        'rss': () => ++fetches == 1 ? pending.future : Future.value([post('fresh')]),
      },
      {'rss': 'feed:rss'},
    );
    await store.retry('rss');
    pending.complete([post('late')]);
    await Future<void>.delayed(Duration.zero);
    expect(store.state.items.single.id, 'fresh');
  });

  test('prefix index discovers existing rows and does not await an unrelated write', () async {
    final dir = await Directory.systemTemp.createTemp('xta-prefix');
    addTearDown(() => dir.delete(recursive: true));
    await LocalJsonStore(directory: () async => dir).write('feed:old', {'at': '2026-01-01'});
    final blocked = Completer<Directory>();
    var delayDirectory = false;
    final store = LocalJsonStore(directory: () => delayDirectory ? blocked.future : Future.value(dir));
    expect((await store.readPrefix('feed:')).keys, ['feed:old']);
    delayDirectory = true;
    final unrelated = store.write('draft:pending', 'draft');
    await Future<void>.delayed(Duration.zero);
    delayDirectory = false;
    final feeds = await store.readPrefix('feed:').timeout(const Duration(seconds: 1));
    expect(feeds.keys, ['feed:old']);
    blocked.complete(dir);
    await unrelated;
    await store.remove('feed:old');
    expect(await store.readPrefix('feed:'), isEmpty);
  });

  test('coalesced cache trimming preserves newest rows and unrelated data', () async {
    final dir = await Directory.systemTemp.createTemp('xta-trim');
    addTearDown(() => dir.delete(recursive: true));
    final store = LocalJsonStore(directory: () async => dir);
    await store.write('draft:keep', 'content');
    await Future.wait([
      for (var i = 0; i < 8; i++) store.write('feed:$i', {'at': '2026-01-0${i + 1}'}),
    ]);
    final first = store.trimCache('feed:', 2);
    final second = store.trimCache('feed:', 2);
    expect(identical(first, second), isTrue);
    await Future.wait([first, second]);
    expect((await store.readPrefix('feed:')).keys.toSet(), {'feed:6', 'feed:7'});
    expect(await store.read('draft:keep'), 'content');
  });
}
