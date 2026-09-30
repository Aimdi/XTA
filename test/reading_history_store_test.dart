import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/reading/reading_history_entry.dart';
import 'package:xta/reading/reading_history_store.dart';

import 'support/reader_tools_harness.dart';

ReadingHistoryEntry _post(String id, {String text = 'hello', String source = 'mastodon'}) =>
    ReadingHistoryEntry(source: source, kind: ReadingHistoryKind.post, nativeId: id, author: 'Ada', text: text);

class _SlowStorage extends MemoryJsonStore {
  Completer<void>? gate;
  @override
  Future<void> write(String key, Object? value) async {
    await gate?.future;
    return super.write(key, value);
  }
}

void main() {
  group('entry', () {
    test('keeps public links and strips credentials, userinfo and fragments', () {
      expect(
        readingHistoryUrl('https://user:pw@news.example/a/b?x=1&token=s3&x=2&Api_Key=k&q=%20a#frag'),
        'https://news.example/a/b?x=1&x=2&q=%20a',
      );
      expect(readingHistoryUrl('javascript:alert(1)'), isNull);
      expect(readingHistoryUrl('https://x.example/${'a' * 3000}'), isNull);
    });

    test('bounds text without splitting a surrogate pair and survives JSON', () {
      final entry = ReadingHistoryEntry(
        source: 'x',
        kind: ReadingHistoryKind.post,
        nativeId: '1',
        text: '${'a' * (readingHistoryTextLimit - 1)}😀 tail',
        title: 't' * 500,
      );
      expect(entry.text.length, readingHistoryTextLimit - 1);
      expect(entry.title.length, readingHistoryTitleLimit);
      final copy = ReadingHistoryEntry.fromJson(entry.toJson())!;
      expect(copy.key, entry.key);
      expect(copy.text, entry.text);
      expect(ReadingHistoryEntry.fromJson({'source': 'x', 'kind': 'post'}), isNull);
      expect(ReadingHistoryEntry.fromJson({'source': 'x', 'kind': 'nope', 'id': '1', 'at': '2026-01-01'}), isNull);
    });
  });

  group('store', () {
    late MemoryJsonStore storage;
    late PrefServiceCache prefs;
    var now = DateTime(2026, 9, 30, 12);

    ReadingHistoryStore make({int limit = readingHistoryLimit}) =>
        ReadingHistoryStore(storage, prefs, limit: limit, clock: () => now);

    setUp(() {
      storage = MemoryJsonStore();
      prefs = PrefServiceCache();
    });

    test('a second view moves an item to the top instead of duplicating it, within the limit', () async {
      final store = make(limit: 3);
      await store.load();
      for (final id in ['a', 'b', 'c', 'd']) {
        store.record(_post(id));
      }
      now = now.add(const Duration(minutes: 1));
      store.record(_post('b'));
      expect(store.state.entries.map((entry) => entry.nativeId), ['b', 'd', 'c']);
      expect(store.state.entries.first.viewedAt, now);
      await store.flush();
      final restarted = make();
      await restarted.load();
      expect(restarted.state.entries.map((entry) => entry.nativeId), ['b', 'd', 'c']);
      await store.destroy();
      await restarted.destroy();
    });

    test('a burst of views is written at most twice', () async {
      final slow = _SlowStorage()..gate = Completer();
      final store = ReadingHistoryStore(slow, prefs);
      await store.load();
      for (var i = 0; i < 20; i++) {
        store.record(_post('$i'));
      }
      slow.gate!.complete();
      await store.flush();
      expect(slow.writes, lessThanOrEqualTo(2));
      expect((slow.values[readingHistoryStorageKey] as Map)['entries'], hasLength(20));
      await store.destroy();
    });

    test('pausing keeps what is stored and drops views that began before the pause', () async {
      final store = make();
      await store.load();
      store.record(_post('a'));
      final epoch = store.epoch;
      expect(await store.setEnabled(false), isTrue);
      store.record(_post('b'));
      expect(await store.setEnabled(true), isTrue);
      store.record(_post('c'), epoch: epoch);
      expect(store.state.entries.map((entry) => entry.nativeId), ['a']);
      expect(ReadingHistoryStore(storage, prefs).state.enabled, isTrue);
      await store.destroy();
    });

    test('clearing and removing do not let a card on screen record again at once', () async {
      final store = make();
      await store.load();
      store.record(_post('a'));
      store.record(_post('b'));
      final epoch = store.epoch;
      expect(await store.remove(_post('a').key), isTrue);
      store.record(_post('a'));
      expect(store.state.entries.map((entry) => entry.nativeId), ['b']);
      expect(await store.clear(), isTrue);
      store.record(_post('c'), epoch: epoch);
      expect(store.state.entries, isEmpty);
      expect((storage.values[readingHistoryStorageKey] as Map)['entries'], isEmpty);
      store.record(_post('d'), epoch: store.epoch);
      expect(store.state.entries.single.nativeId, 'd');
      await store.destroy();
    });

    test('a refused erase is reported, never claimed', () async {
      final store = make();
      await store.load();
      store.record(_post('a'));
      await store.flush();
      storage.failWrites = true;
      expect(await store.clear(), isFalse);
      final prefsWrite = GatedPrefs()..rejectWrites = true;
      final paused = ReadingHistoryStore(storage, prefsWrite);
      expect(await paused.setEnabled(false), isFalse);
      expect(paused.state.enabled, isTrue);
      await store.destroy();
      await paused.destroy();
    });

    test('searches every term across title, author, text and link, optionally by source', () async {
      final store = make();
      await store.load();
      store.record(_post('a', text: 'Quiet coastal walks'));
      store.record(_post('b', text: 'Busy city streets', source: 'bluesky'));
      expect(store.state.search('coastal ada').single.nativeId, 'a');
      expect(store.state.search('').length, 2);
      expect(store.state.search('', source: 'bluesky').single.nativeId, 'b');
      expect(store.state.sources, {'mastodon', 'bluesky'});
      await store.destroy();
    });

    test('views recorded before loading finishes are kept alongside stored ones', () async {
      final first = make();
      await first.load();
      first.record(_post('old'));
      await first.flush();
      final second = make();
      second.record(_post('new'));
      await second.load();
      expect(second.state.entries.map((entry) => entry.nativeId), ['new', 'old']);
      await first.destroy();
      await second.destroy();
    });
  });
}
