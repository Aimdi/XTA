import 'dart:async';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/tweet/feed_snapshot_cache.dart';
import 'package:xta/tweet/interleaved_items.dart';
import 'package:xta/tweet/link_identity.dart';
import 'package:xta/tweet/progressive_feed_store.dart';
import 'package:xta/utils/local_json_store.dart';

InterleavedItem post(String id) => InterleavedItem(
  date: DateTime.utc(2026),
  id: id,
  build: (_) => Text(id),
  snapshot: {'xtaPlugin': 'link', 'source': 'rss', 'url': 'https://example.org/$id', 'text': id},
);
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late Directory directory;
  late LocalJsonStore storage;
  setUp(() async {
    directory = await Directory.systemTemp.createTemp('reader-test');
    storage = LocalJsonStore(directory: () async => directory);
  });
  tearDown(() async {
    if (await directory.exists()) await directory.delete(recursive: true);
  });
  test('article identity removes tracking but preserves article parameters', () {
    expect(canonicalArticleLink('https://news.org/story?utm_source=x#section'), 'https://news.org/story');
    expect(canonicalArticleLink('https://news.org/story?id=2&utm_source=x'), 'https://news.org/story?id=2');
    for (final url in [
      'https://x.com/a/status/1',
      'https://news.org/',
      'https://news.org/login',
      'https://news.org/story#!/2',
    ]) {
      expect(canonicalArticleLink(url), isNull);
    }
  });
  test('one source completes while another waits, fails, and retries independently', () async {
    final slow = Completer<List<InterleavedItem>>();
    final store = ProgressiveFeedStore(cache: FeedSnapshotCache(storage: storage));
    final loading = store.load(
      {
        'fast': () async => [post('ready')],
        'slow': () => slow.future,
      },
      {'fast': 'feed:fast', 'slow': 'feed:slow'},
    );
    await Future<void>.delayed(const Duration(milliseconds: 40));
    expect(store.state.items.map((e) => e.id), ['ready']);
    slow.completeError(TimeoutException('slow'));
    await loading;
    expect(store.state.sources['slow']!.error, isA<TimeoutException>());
    expect(store.state.items.map((e) => e.id), ['ready']);
    await store.destroy();
  });
  test('new posts are held until requested while reading away from the top', () async {
    final store = ProgressiveFeedStore(cache: FeedSnapshotCache(storage: storage));
    var current = 'old';
    await store.load(
      {
        'rss': () async => [post(current)],
      },
      {'rss': 'feed:rss'},
    );
    store.setReadingAway(true);
    current = 'new';
    await store.retry('rss');
    expect(store.state.items.single.id, 'old');
    expect(store.state.hasPending, isTrue);
    store.reveal();
    expect(store.state.items.single.id, 'new');
    await store.destroy();
  });
  test('obsolete source requests cannot replace a newly selected group', () async {
    final old = Completer<List<InterleavedItem>>();
    final store = ProgressiveFeedStore(cache: FeedSnapshotCache(storage: storage));
    final previous = store.load({'rss': () => old.future}, {'rss': 'feed:old'});
    await Future<void>.delayed(const Duration(milliseconds: 20));
    await store.load(
      {
        'rss': () async => [post('new')],
      },
      {'rss': 'feed:new'},
    );
    old.complete([post('old')]);
    await previous;
    expect(store.state.items.single.id, 'new');
    await store.destroy();
  });
  test('snapshots survive new store instances and damaged rows are ignored', () async {
    final cache = FeedSnapshotCache(storage: storage);
    await cache.write('feed:a', [post('saved')]);
    final reopened = FeedSnapshotCache(storage: LocalJsonStore(directory: () async => directory));
    expect((await reopened.read('feed:a')).items.single.id, 'saved');
    await storage.write('feed:bad', {
      'posts': [false],
      'at': DateTime.now().toIso8601String(),
    });
    expect((await reopened.read('feed:bad')).items, isEmpty);
  });
  test('queued writes and deletion do not resurrect discarded state', () async {
    final writes = [storage.write('draft:a', 'first'), storage.write('draft:a', 'last'), storage.remove('draft:a')];
    await Future.wait(writes);
    expect(await storage.read('draft:a'), isNull);
  });
}
