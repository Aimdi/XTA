import 'dart:async';
import 'dart:convert';

import 'package:crypto/crypto.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/reading/reader_translation_config.dart';
import 'package:xta/reading/reader_translation_service.dart';
import 'package:xta/utils/local_json_store.dart';

const readerTranslationCacheKey = 'reader-translation-cache';
const readerTranslationCacheEntries = 32;
const readerTranslationCacheChars = 500000;

/// Recent translations, bounded by entries and characters, and the texts shown translated this session.
class ReaderTranslationCache extends Store<Set<String>> {
  static final _instances = Expando<ReaderTranslationCache>();
  final JsonStore storage;
  final int maxEntries;
  final int maxChars;
  final _entries = <String, String>{};
  final _mounted = <String, int>{};
  final _inflight = <String, Future<String>>{};
  Future<void>? _loaded;
  Timer? _flush;
  bool _closed = false;

  ReaderTranslationCache(
    this.storage, {
    this.maxEntries = readerTranslationCacheEntries,
    this.maxChars = readerTranslationCacheChars,
  }) : super(const {});

  static ReaderTranslationCache forStorage(JsonStore storage) =>
      _instances[storage] ??= ReaderTranslationCache(storage);

  static ReaderTranslationCache get shared => forStorage(LocalJsonStore.shared);

  static String textKey(String text) => sha1.convert(utf8.encode(text)).toString();

  Future<void> load() => _loaded ??= _read();

  Future<void> _read() async {
    try {
      final raw = await storage.read(readerTranslationCacheKey);
      if (raw is! Map || raw['version'] != 1 || raw['entries'] is! Map) return;
      for (final entry in (raw['entries'] as Map).entries) {
        final key = entry.key;
        if (key is String && entry.value is String && !_entries.containsKey(key)) {
          _entries[key] = entry.value as String;
        }
      }
      _trim();
    } catch (_) {
      // A damaged cache only costs a new request.
    }
  }

  String? lookup(String fingerprint) {
    final value = _entries.remove(fingerprint);
    if (value != null) _entries[fingerprint] = value;
    return value;
  }

  void put(String fingerprint, String translation) {
    if (_closed) return;
    _entries.remove(fingerprint);
    _entries[fingerprint] = translation;
    _trim();
    _flush?.cancel();
    _flush = Timer(const Duration(seconds: 2), persist);
  }

  void _trim() {
    var chars = _entries.entries.fold<int>(0, (sum, entry) => sum + entry.key.length + entry.value.length);
    while (_entries.isNotEmpty && (_entries.length > maxEntries || chars > maxChars)) {
      final oldest = _entries.entries.first;
      chars -= oldest.key.length + oldest.value.length;
      _entries.remove(oldest.key);
    }
  }

  Future<void> persist() async {
    _flush?.cancel();
    try {
      await storage.write(readerTranslationCacheKey, {'version': 1, 'entries': Map.of(_entries)});
    } catch (_) {
      // Best effort: the translation on screen does not depend on it.
    }
  }

  Future<void> clear() async {
    _entries.clear();
    if (!_closed) update(const {});
    await persist();
  }

  bool requested(String text) => state.contains(textKey(text));

  /// One request per translation, however many places show the same text.
  Future<String> shareRequest(String fingerprint, Future<String> Function() run) {
    final running = _inflight[fingerprint];
    if (running != null) return running;
    late final Future<String> request;
    request = run().whenComplete(() {
      if (identical(_inflight[fingerprint], request)) _inflight.remove(fingerprint);
    });
    return _inflight[fingerprint] = request;
  }

  /// Whether [text] is currently shown by an in-place translation somewhere.
  bool shownInPlace(String text) => (_mounted[textKey(text)] ?? 0) > 0;

  void attach(String text) => _mounted.update(textKey(text), (count) => count + 1, ifAbsent: () => 1);

  void detach(String text) {
    final key = textKey(text);
    final count = (_mounted[key] ?? 1) - 1;
    if (count > 0) {
      _mounted[key] = count;
    } else {
      _mounted.remove(key);
    }
  }

  /// Asks every surface showing [text] to show it translated; [release] returns them to the original.
  void request(String text) {
    if (!_closed && !requested(text)) update({...state, textKey(text)});
  }

  void release(String text) {
    final key = textKey(text);
    if (!_closed && state.contains(key)) update({...state}..remove(key));
  }

  @override
  Future<void> destroy() async {
    _closed = true;
    if (identical(_instances[storage], this)) _instances[storage] = null;
    await persist();
    await super.destroy();
  }
}

enum ReaderTranslationStatus { original, loading, translated, failed }

class ReaderTranslationState {
  final ReaderTranslationStatus status;
  final String? translated;
  final ReaderTranslationFailure? failure;

  const ReaderTranslationState({this.status = ReaderTranslationStatus.original, this.translated, this.failure});

  bool get loading => status == ReaderTranslationStatus.loading;
  bool get failed => status == ReaderTranslationStatus.failed;
  bool get showsTranslation => status == ReaderTranslationStatus.translated;
}

/// One text's translation. The original is never changed; showing it again only drops the translation.
///
/// A [shared] store follows the session's requests, so every place showing the same text switches together.
class ReaderTranslationStore extends Store<ReaderTranslationState> {
  final String text;
  final ReaderTranslationConfigStore config;
  final ReaderTranslationService service;
  final ReaderTranslationCache cache;
  final bool shared;
  String _appLanguage;
  int _generation = 0;
  bool _closed = false;
  bool _attached = false;
  late final Disposer _disposeConfig;
  Disposer? _disposeRequests;

  ReaderTranslationStore({
    required this.text,
    required this.config,
    required this.service,
    required this.cache,
    required this._appLanguage,
    this.shared = true,
  }) : super(const ReaderTranslationState()) {
    _disposeConfig = config.observer(onState: (_) => _settingsChanged());
    if (shared) {
      _disposeRequests = cache.observer(onState: (_) => _requestsChanged());
      if (cache.requested(text)) translate();
    }
  }

  bool get enabled => config.state.enabled;

  /// The configured target, or the app language when the reader left it on the default.
  String get language => config.state.target.isNotEmpty ? config.state.target : _appLanguage;

  set appLanguage(String value) {
    if (value == _appLanguage) return;
    final before = language;
    _appLanguage = value;
    if (language != before) _settingsChanged();
  }

  String get _fingerprint => config.state.fingerprint(text, language);

  bool _current(int generation, String fingerprint) =>
      !_closed && generation == _generation && fingerprint == _fingerprint;

  void attach() {
    if (_attached) return;
    _attached = true;
    cache.attach(text);
  }

  void detach() {
    if (!_attached) return;
    _attached = false;
    cache.detach(text);
  }

  void _settingsChanged() {
    ++_generation;
    if (_closed) return;
    if (shared && cache.requested(text) && enabled) {
      translate();
    } else if (state.status != ReaderTranslationStatus.original) {
      update(const ReaderTranslationState());
    }
  }

  void _requestsChanged() {
    if (_closed) return;
    final wanted = cache.requested(text);
    if (wanted && state.status == ReaderTranslationStatus.original) translate();
    if (!wanted && state.status != ReaderTranslationStatus.original) {
      ++_generation;
      update(const ReaderTranslationState());
    }
  }

  /// Uses a remembered translation when one exists; otherwise asks the provider.
  Future<void> translate() async {
    if (_closed || state.loading || text.trim().isEmpty) return;
    if (!enabled) {
      update(
        const ReaderTranslationState(
          status: ReaderTranslationStatus.failed,
          failure: ReaderTranslationFailure.notConfigured,
        ),
      );
      return;
    }
    final generation = ++_generation;
    final fingerprint = _fingerprint;
    update(const ReaderTranslationState(status: ReaderTranslationStatus.loading));
    if (shared) cache.request(text);
    await cache.load();
    if (!_current(generation, fingerprint)) return;
    final cached = cache.lookup(fingerprint);
    if (cached != null) {
      update(ReaderTranslationState(status: ReaderTranslationStatus.translated, translated: cached));
      return;
    }
    try {
      final translated = await _request(generation, fingerprint);
      if (!_current(generation, fingerprint)) return;
      cache.put(fingerprint, translated);
      update(ReaderTranslationState(status: ReaderTranslationStatus.translated, translated: translated));
    } on ReaderTranslationException catch (error) {
      _failed(generation, fingerprint, error.reason);
    } catch (_) {
      _failed(generation, fingerprint, ReaderTranslationFailure.network);
    }
  }

  Future<String> _request(int generation, String fingerprint) {
    final snapshot = config.state;
    final target = language;
    if (!shared) {
      return service.translate(text, snapshot, language: target, current: () => _current(generation, fingerprint));
    }
    return cache.shareRequest(
      fingerprint,
      () => service.translate(text, snapshot, language: target, current: () => cache.requested(text)),
    );
  }

  void _failed(int generation, String fingerprint, ReaderTranslationFailure reason) {
    if (!_current(generation, fingerprint)) return;
    if (reason != ReaderTranslationFailure.cancelled) {
      update(ReaderTranslationState(status: ReaderTranslationStatus.failed, failure: reason));
    } else if (shared && cache.requested(text)) {
      // A shared request was released and asked for again before it noticed; ask once more.
      update(const ReaderTranslationState());
      translate();
    }
  }

  /// Stops any request and shows the original everywhere this text appears.
  void showOriginal() {
    ++_generation;
    if (shared) cache.release(text);
    if (!_closed) update(const ReaderTranslationState());
  }

  @override
  Future<void> destroy() async {
    _closed = true;
    ++_generation;
    detach();
    await _disposeConfig();
    await _disposeRequests?.call();
    await super.destroy();
  }
}
