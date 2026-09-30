import 'dart:collection';
import 'dart:convert';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/reading/feed_appearance_store.dart';

const _channel = MethodChannel('plugins.flutter.io/shared_preferences');
const _prefix = 'appearance-adapter.';
const _backendKey = 'flutter.$_prefix$feedAppearancePreferenceKey';
const _feed = FeedIdentity('bluesky', 'following');
const _other = FeedIdentity('rss', 'publication');

class _Backend {
  final Map<String, Object> durable;
  final Queue<Object> outcomes = Queue();
  final attempts = <({String method, Object? value, Object? cached})>[];
  late PrefServiceShared prefs;
  _Backend({String? raw}) : durable = {_backendKey: ?raw, 'flutter.unrelated': 'retained'};

  Future<void> connect() async {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(_channel, (call) async {
      if (call.method == 'getAll') return {...durable};
      final args = call.arguments as Map;
      final value = args['value'];
      attempts.add((method: call.method, value: value, cached: prefs.get<Object>(feedAppearancePreferenceKey)));
      final outcome = outcomes.isEmpty ? true : outcomes.removeFirst();
      if (outcome is Exception) throw outcome;
      if (outcome == true) {
        if (call.method == 'remove') {
          durable.remove(args['key']);
        } else {
          durable[args['key']] = value;
        }
      }
      return outcome;
    });
    prefs = await PrefServiceShared.init(prefix: _prefix);
    await prefs.sharedPreferences.reload();
  }

  void close() {
    prefs.dispose();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(_channel, null);
  }
}

String _original() => jsonEncode({
  'v': 1,
  'feeds': {
    _feed.encoded: {'counts': true},
    _other.encoded: {'media': false},
  },
});

Future<void> _checkRecreated(PrefServiceShared prefs, {required bool inherits}) async {
  final recreated = FeedAppearanceStore(prefs);
  expect(recreated.appearance(_feed).inherits, inherits);
  if (!inherits) {
    expect(recreated.appearance(_feed).counts, isTrue);
    expect(recreated.appearance(_other).media, isFalse);
  }
  await recreated.destroy();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  for (final failure in [false, PlatformException(code: 'write_failed')]) {
    final mode = failure == false ? 'false' : 'throw';
    test('production adapter $mode restores exact successful raw; identical retry persists', () async {
      final raw = _original();
      final backend = _Backend(raw: raw);
      await backend.connect();
      addTearDown(backend.close);
      final store = FeedAppearanceStore(backend.prefs);
      addTearDown(store.destroy);
      final notifications = <Object?>[];
      backend.prefs.addKeyListener(
        feedAppearancePreferenceKey,
        () => notifications.add(backend.prefs.get<Object>(feedAppearancePreferenceKey)),
      );
      backend.outcomes.addAll([failure, true]);
      expect(await store.save(_feed, const FeedAppearance(counts: false)), isFalse);
      expect(backend.attempts.length, 2);
      final target = backend.attempts.first.value;
      expect(backend.attempts.first.cached, target); // Actual SharedPreferences cache mutates before failure.
      expect(backend.attempts.last.value, raw);
      expect(backend.prefs.get<Object>(feedAppearancePreferenceKey), raw);
      expect(backend.durable[_backendKey], raw);
      expect(notifications, [raw]); // Failed primary emits no success notification; rollback does.
      expect(store.appearance(_feed).counts, isTrue);
      await _checkRecreated(backend.prefs, inherits: false);
      expect(await store.save(_feed, const FeedAppearance(counts: false)), isTrue);
      expect(backend.attempts.length, 3);
      expect(backend.attempts.last.value, target);
      expect(store.appearance(_feed).counts, isFalse);
      expect(store.appearance(_other).media, isFalse);
      expect(backend.durable[_backendKey], target);
      expect(notifications, [raw, target]);
      backend.outcomes.addAll([failure, true]);
      expect(await store.save(_feed, const FeedAppearance(counts: false)), isFalse);
      expect(backend.attempts.length, 5); // Equal-to-cache writes must still reach the backend.
      expect(backend.attempts[3].value, target);
      expect(backend.attempts[4].value, target);
      expect(notifications, [raw, target, target]);
      expect(store.appearance(_feed).counts, isFalse);
      await backend.prefs.sharedPreferences.reload();
      final reopened = FeedAppearanceStore(backend.prefs);
      expect(reopened.appearance(_feed).counts, isFalse);
      expect(reopened.appearance(_other).media, isFalse);
      await reopened.destroy();
    });

    test('production adapter repeated $mode and failed restoration never claim identical retry success', () async {
      final raw = _original();
      final backend = _Backend(raw: raw);
      await backend.connect();
      addTearDown(backend.close);
      final store = FeedAppearanceStore(backend.prefs);
      addTearDown(store.destroy);
      backend.outcomes.addAll([failure, failure, failure, failure]);
      expect(await store.save(_feed, const FeedAppearance(counts: false)), isFalse);
      expect(await store.save(_feed, const FeedAppearance(counts: false)), isFalse);
      expect(backend.attempts.length, 4);
      expect(backend.attempts[0].value, backend.attempts[2].value);
      expect(backend.attempts[1].value, raw);
      expect(backend.attempts[3].value, raw);
      expect(backend.attempts.every((attempt) => attempt.cached == attempt.value), isTrue);
      expect(store.appearance(_feed).counts, isTrue);
      expect(backend.prefs.get<Object>(feedAppearancePreferenceKey), raw);
      expect(backend.durable[_backendKey], raw);
      await _checkRecreated(backend.prefs, inherits: false);
    });

    test('production adapter absent key restores absence after repeated $mode including failed removes', () async {
      final backend = _Backend();
      await backend.connect();
      addTearDown(backend.close);
      final store = FeedAppearanceStore(backend.prefs);
      addTearDown(store.destroy);
      backend.outcomes.addAll([failure, failure, failure, failure]);
      expect(await store.save(_feed, const FeedAppearance(counts: false)), isFalse);
      expect(await store.save(_feed, const FeedAppearance(counts: false)), isFalse);
      expect(backend.attempts.map((attempt) => attempt.method), ['setString', 'remove', 'setString', 'remove']);
      expect(backend.attempts[0].value, backend.attempts[2].value);
      expect(backend.attempts[0].cached, backend.attempts[0].value);
      expect(backend.attempts[1].cached, isNull);
      expect(store.appearance(_feed).inherits, isTrue);
      expect(backend.prefs.getKeys(), isNot(contains(feedAppearancePreferenceKey)));
      expect(backend.durable.containsKey(_backendKey), isFalse);
      expect(backend.durable['flutter.unrelated'], 'retained');
      await _checkRecreated(backend.prefs, inherits: true);
      expect(await store.save(_feed, const FeedAppearance(counts: false)), isTrue);
      expect(backend.attempts.length, 5);
      expect(backend.attempts.last.value, backend.attempts.first.value);
      expect(backend.durable[_backendKey], backend.attempts.last.value);
    });
  }
}
