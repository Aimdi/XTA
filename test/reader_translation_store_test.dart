import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/reading/reader_translation_config.dart';
import 'package:xta/reading/reader_translation_service.dart';
import 'package:xta/reading/reader_translation_store.dart';
import 'package:xta/utils/ai_client.dart';

import 'support/reader_tools_harness.dart';

const _deepl = ReaderTranslationConfig(provider: ReaderTranslationProvider.deepl, apiKey: 'key:fx');

class _FakeService extends ReaderTranslationService {
  final requests = <({String text, String language})>[];
  Completer<String>? hold;
  Object? failure;
  _FakeService();

  @override
  Future<String> translate(
    String text,
    ReaderTranslationConfig config, {
    required String language,
    bool Function()? current,
  }) async {
    requests.add((text: text, language: language));
    final pending = hold;
    if (pending != null) await pending.future;
    if (current?.call() == false) throw const ReaderTranslationException(ReaderTranslationFailure.cancelled);
    final error = failure;
    if (error != null) throw error;
    return '[$language] $text';
  }
}

ReaderTranslationStore _store(
  ReaderTranslationConfigStore config,
  _FakeService service,
  ReaderTranslationCache cache, {
  String text = 'Hello',
  String appLanguage = 'de',
  bool shared = true,
}) => ReaderTranslationStore(
  text: text,
  config: config,
  service: service,
  cache: cache,
  appLanguage: appLanguage,
  shared: shared,
);

void main() {
  group('config store', () {
    test('saves settings and the key separately, surviving a restart', () async {
      final prefs = PrefServiceCache();
      final config = ReaderTranslationConfigStore(prefs);
      expect(config.state.enabled, isFalse);
      expect(await config.save(_deepl), isTrue);
      expect(prefs.get<String>(readerTranslationApiKeyKey), 'key:fx');
      expect(prefs.get<String>(readerTranslationConfigKey), isNot(contains('key:fx')));
      final restarted = ReaderTranslationConfigStore(prefs);
      expect(restarted.state.provider, ReaderTranslationProvider.deepl);
      expect(restarted.state.apiKey, 'key:fx');
      await config.destroy();
      await restarted.destroy();
    });

    test('refused, throwing and invalid saves keep the last good settings', () async {
      final prefs = GatedPrefs();
      final config = ReaderTranslationConfigStore(prefs);
      expect(await config.save(_deepl), isTrue);
      prefs.rejectWrites = true;
      expect(await config.save(const ReaderTranslationConfig()), isFalse);
      prefs.rejectWrites = false;
      prefs.throwWrites = true;
      expect(await config.save(const ReaderTranslationConfig()), isFalse);
      prefs.throwWrites = false;
      expect(await config.save(const ReaderTranslationConfig(provider: ReaderTranslationProvider.deepl)), isFalse);
      expect(config.state, _deepl.withAi(config.state.ai));
      expect(prefs.get<String>(readerTranslationApiKeyKey), 'key:fx');
      await config.destroy();
    });

    test('an AI choice follows later changes to the AI settings', () async {
      final prefs = PrefServiceCache();
      final config = ReaderTranslationConfigStore(prefs);
      expect(await config.save(const ReaderTranslationConfig(provider: ReaderTranslationProvider.ai)), isFalse);
      await prefs.set(optionAiBaseUrl, 'https://ai.example/v1');
      await prefs.set(optionAiApiKey, 'k');
      await prefs.set(optionAiModel, 'm');
      expect(config.state.ai.isConfigured, isTrue);
      expect(
        await config.save(ReaderTranslationConfig(provider: ReaderTranslationProvider.ai, ai: config.state.ai)),
        isTrue,
      );
      final restarted = ReaderTranslationConfigStore(prefs);
      expect(restarted.state.provider, ReaderTranslationProvider.ai);
      expect(restarted.state.ai, isA<AiConfig>());
      await config.destroy();
      await restarted.destroy();
    });
  });

  group('translation store', () {
    late PrefServiceCache prefs;
    late ReaderTranslationConfigStore config;
    late _FakeService service;
    late MemoryJsonStore storage;
    late ReaderTranslationCache cache;

    setUp(() async {
      prefs = PrefServiceCache();
      config = ReaderTranslationConfigStore(prefs);
      await config.save(_deepl);
      service = _FakeService();
      storage = MemoryJsonStore();
      cache = ReaderTranslationCache(storage);
    });

    tearDown(() async {
      await config.destroy();
      await cache.destroy();
    });

    test('translates into the app language, remembers it and shows the original again', () async {
      final store = _store(config, service, cache);
      await store.translate();
      expect(store.state.translated, '[de] Hello');
      store.showOriginal();
      expect(store.state.status, ReaderTranslationStatus.original);
      await store.translate();
      expect(store.state.translated, '[de] Hello');
      expect(service.requests, hasLength(1));
      await store.destroy();
    });

    test('a failure offers a retry that can succeed', () async {
      final store = _store(config, service, cache);
      service.failure = const ReaderTranslationException(ReaderTranslationFailure.network);
      await store.translate();
      expect(store.state.failed, isTrue);
      expect(store.state.failure, ReaderTranslationFailure.network);
      service.failure = null;
      await store.translate();
      expect(store.state.showsTranslation, isTrue);
      await store.destroy();
    });

    test('a response that arrives after Show original is dropped', () async {
      final store = _store(config, service, cache);
      service.hold = Completer();
      final pending = store.translate();
      await Future<void>.delayed(Duration.zero);
      expect(store.state.loading, isTrue);
      store.showOriginal();
      service.hold!.complete('late');
      await pending;
      expect(store.state.status, ReaderTranslationStatus.original);
      await store.destroy();
    });

    test('every place showing the same text switches together, and a new one follows', () async {
      final first = _store(config, service, cache);
      final second = _store(config, service, cache);
      final other = _store(config, service, cache, text: 'Another');
      await first.translate();
      await Future<void>.delayed(Duration.zero);
      expect(second.state.showsTranslation, isTrue);
      expect(other.state.status, ReaderTranslationStatus.original);
      final later = _store(config, service, cache);
      await Future<void>.delayed(Duration.zero);
      expect(later.state.showsTranslation, isTrue);
      second.showOriginal();
      await Future<void>.delayed(Duration.zero);
      expect(first.state.status, ReaderTranslationStatus.original);
      expect(service.requests, hasLength(1));
      for (final store in [first, second, other, later]) {
        await store.destroy();
      }
    });

    test('a sheet store neither follows nor changes the in-place requests', () async {
      final sheet = _store(config, service, cache, shared: false);
      await sheet.translate();
      expect(cache.requested('Hello'), isFalse);
      await sheet.destroy();
    });

    test('changing the service or target language drops a shown translation', () async {
      final store = _store(config, service, cache);
      await store.translate();
      expect(
        await config.save(ReaderTranslationConfig(provider: _deepl.provider, apiKey: _deepl.apiKey, target: 'fr')),
        isTrue,
      );
      await Future<void>.delayed(Duration.zero);
      expect(store.state.translated, '[fr] Hello');
      store.showOriginal();
      store.appLanguage = 'it';
      expect(store.state.status, ReaderTranslationStatus.original);
      expect(await config.save(const ReaderTranslationConfig()), isTrue);
      await store.translate();
      expect(store.state.failure, ReaderTranslationFailure.notConfigured);
      await store.destroy();
    });

    test('the cache is bounded and survives a restart', () async {
      final small = ReaderTranslationCache(storage, maxEntries: 2, maxChars: 1000);
      small.put('a', '1');
      small.put('b', '2');
      small.put('c', '3');
      expect(small.lookup('a'), isNull);
      await small.persist();
      final reloaded = ReaderTranslationCache(storage);
      await reloaded.load();
      expect(reloaded.lookup('b'), '2');
      expect(reloaded.lookup('c'), '3');
      final bounded = ReaderTranslationCache(storage, maxChars: 10);
      bounded.put('k', 'x' * 20);
      expect(bounded.lookup('k'), isNull);
      storage.failWrites = true;
      await small.persist();
      expect(jsonEncode(storage.values), contains('"c":"3"'));
      await small.destroy();
      await reloaded.destroy();
      await bounded.destroy();
    });
  });
}
