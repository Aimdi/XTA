import 'dart:async';
import 'dart:convert';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/reading/feed_appearance_store.dart';
import 'package:xta/reading/reader_preference_writes.dart';

void main() {
  const a = FeedIdentity('a:b', 'c');
  const b = FeedIdentity('a', 'b:c');
  test('JSON pair identity survives restart; missing feeds inherit', () async {
    final prefs = PrefServiceCache();
    final store = FeedAppearanceStore(prefs);
    expect(a.encoded, jsonEncode(['a:b', 'c']));
    expect(store.appearance(b).inherits, isTrue);
    expect(await store.save(a, const FeedAppearance(counts: false, preset: FeedPreset.gallery)), isTrue);
    final restarted = FeedAppearanceStore(prefs);
    expect(restarted.appearance(a).counts, isFalse);
    expect(restarted.appearance(a).preset, FeedPreset.gallery);
    expect(restarted.appearance(b).inherits, isTrue);
    await store.destroy();
    await restarted.destroy();
  });
  test('queued modifications derive from latest successful state', () async {
    final gate = Completer<bool>();
    var calls = 0;
    final store = FeedAppearanceStore(PrefServiceCache(), write: (_, _) async => ++calls == 1 ? gate.future : true);
    final first = store.modify(a, (value) => value.copyWith(counts: false));
    final second = store.modify(a, (value) => value.copyWith(media: false));
    await Future<void>.delayed(Duration.zero);
    expect(calls, 1);
    gate.complete(true);
    expect(await first, isTrue);
    expect(await second, isTrue);
    expect(store.appearance(a).counts, isFalse);
    expect(store.appearance(a).media, isFalse);
    await store.destroy();
  });
  test('false and throw keep successful state; reset affects only target', () async {
    var outcome = 0;
    final store = FeedAppearanceStore(
      PrefServiceCache(),
      write: (_, _) async {
        if (outcome == 1) return false;
        if (outcome == 2) throw StateError('write');
        return true;
      },
    );
    expect(await store.save(a, const FeedAppearance(counts: false)), isTrue);
    expect(await store.save(b, const FeedAppearance(media: false)), isTrue);
    outcome = 1;
    expect(await store.save(a, const FeedAppearance(counts: true)), isFalse);
    outcome = 2;
    expect(await store.save(a, const FeedAppearance()), isFalse);
    expect(store.appearance(a).counts, isFalse);
    outcome = 0;
    expect(await store.save(a, const FeedAppearance()), isTrue);
    expect(store.appearance(a).inherits, isTrue);
    expect(store.appearance(b).media, isFalse);
    await store.destroy();
  });
  test('1000 identities and 2MB bounds; malformed siblings are isolated', () async {
    final feeds = <String, Object?>{
      '["broken"]': {'counts': true},
      FeedIdentity('bad', 'fields').encoded: {'counts': 'false'},
      FeedIdentity('bad', 'preset').encoded: {'preset': 'future'},
      FeedIdentity('bad', 'x' * 3000).encoded: {'preset': 'unknown'},
      for (var i = 0; i < 1000; i++) FeedIdentity('rss', '$i').encoded: {'counts': false},
    };
    final prefs = PrefServiceCache(
      cache: {
        feedAppearancePreferenceKey: jsonEncode({'v': 1, 'feeds': feeds}),
      },
    );
    final store = FeedAppearanceStore(prefs);
    expect(store.state.length, 1000);
    expect(await store.save(const FeedIdentity('rss', 'overflow'), const FeedAppearance(media: false)), isFalse);
    expect(await store.save(const FeedIdentity('rss', '0'), const FeedAppearance(counts: true)), isTrue);
    final huge = FeedAppearanceStore(
      PrefServiceCache(cache: {feedAppearancePreferenceKey: ' ' * (feedAppearanceMaxBytes + 1)}),
    );
    expect(huge.state, isEmpty);
    expect(
      await huge.save(FeedIdentity('rss', 'z' * feedAppearanceMaxBytes), const FeedAppearance(media: false)),
      isFalse,
    );
    await store.destroy();
    await huge.destroy();
  });
  test('shared per-preference queue drains and releases completed futures', () async {
    final prefs = PrefServiceCache();
    final gate = Completer<void>();
    var secondRan = false;
    final first = ReaderPreferenceWrites.enqueue(prefs, () => gate.future);
    final second = ReaderPreferenceWrites.enqueue(prefs, () async {
      secondRan = true;
    });
    await Future<void>.delayed(Duration.zero);
    expect(ReaderPreferenceWrites.isPending(prefs), isTrue);
    expect(secondRan, isFalse);
    gate.complete();
    await first;
    await second;
    await ReaderPreferenceWrites.drain(prefs);
    expect(secondRan, isTrue);
    expect(ReaderPreferenceWrites.isPending(prefs), isFalse);
  });
  test('write bytes bound preserves successful state', () async {
    final store = FeedAppearanceStore(PrefServiceCache());
    store.update({
      for (var i = 0; i < 999; i++) FeedIdentity('rss', '$i${'界' * 1000}'): const FeedAppearance(counts: false),
    });
    expect(await store.save(const FeedIdentity('rss', 'more'), const FeedAppearance(media: false)), isFalse);
    expect(store.state.length, 999);
    await store.destroy();
  });
  test('shared store ownership belongs to preferences', () async {
    final prefs = PrefServiceCache();
    final first = FeedAppearanceStore.forPrefs(prefs);
    expect(FeedAppearanceStore.forPrefs(prefs), same(first));
    await first.destroy();
  });

  test('destroy guards pending state delivery and queued modifications', () async {
    final gate = Completer<bool>();
    var calls = 0;
    final store = FeedAppearanceStore(
      PrefServiceCache(),
      write: (_, _) async {
        calls++;
        return gate.future;
      },
    );
    final first = store.save(a, const FeedAppearance(counts: false));
    final second = store.modify(a, (value) => value.copyWith(media: false));
    await Future<void>.delayed(Duration.zero);
    final closing = store.destroy();
    gate.complete(true);
    expect(await first, isFalse);
    expect(await second, isFalse);
    await closing;
    expect(calls, 1);
    expect(store.state, isEmpty);
  });
}
