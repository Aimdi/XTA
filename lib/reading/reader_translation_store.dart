import 'dart:convert';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/reading/reader_preference_writes.dart';
import 'package:xta/reading/reader_translation_config.dart';
import 'package:xta/reading/reader_translation_service.dart';

class ReaderTranslationState {
  final String? translated;
  final bool loading;
  final bool failed;
  final bool cacheFailed;
  const ReaderTranslationState({this.translated, this.loading = false, this.failed = false, this.cacheFailed = false});
}
class _TranslationCache {
  static final _instances = Expando<_TranslationCache>();
  Map<String, String> entries = {};
  _TranslationCache(BasePrefService prefs) {
    prefs.makeSecret(readerTranslationCacheKey);
    try {
      final raw = prefs.get<String>(readerTranslationCacheKey);
      if (raw == null || raw.length > 6000000) return;
      final json = jsonDecode(raw);
      if (json is! Map || json['version'] != 1 || json['entries'] is! Map) return;
      for (final entry in (json['entries'] as Map).entries) {
        if (entry.key is String && RegExp(r'^[a-f0-9]{64}$').hasMatch(entry.key) && entry.value is String) entries[entry.key] = entry.value;
      }
      entries = _bounded(entries, 32, 500000);
    } catch (_) {}
  }
  static _TranslationCache forPrefs(BasePrefService prefs) => _instances[prefs] ??= _TranslationCache(prefs);
}
Map<String, String> _bounded(Map<String, String> map, int maxCache, int maxChars) {
  final result = <String, String>{}; var chars = 0;
  for (final entry in map.entries.toList().reversed) {
    final size = entry.key.length + entry.value.length;
    if (result.length >= maxCache || chars + size > maxChars) continue;
    result[entry.key] = entry.value; chars += size;
  }
  return Map.fromEntries(result.entries.toList().reversed);
}
class ReaderTranslationStore extends Store<ReaderTranslationState> {
  final String text;
  final ReaderTranslationConfigStore config;
  final BasePrefService prefs;
  final ReaderTranslationService service;
  final int maxCache;
  final int maxChars;
  late final _TranslationCache _cache;
  late final Disposer _disposeConfig;
  int _generation = 0;
  bool _closed = false;
  ReaderTranslationStore({required this.text, required this.config, required this.prefs, required this.service,
    this.maxCache = 32, this.maxChars = 500000}) : super(const ReaderTranslationState()) {
    _cache = _TranslationCache.forPrefs(prefs);
    _disposeConfig = config.observer(onState: (_) => reset());
  }
  bool get enabled => config.state.enabled;
  String get target => config.state.target;
  bool _current(int generation, String key) => !_closed && generation == _generation && key == config.state.fingerprint(text);
  Future<void> translate() async {
    if (_closed || state.loading || !enabled || text.trim().isEmpty) return;
    final generation = ++_generation; final snapshot = config.state; final key = snapshot.fingerprint(text);
    final cached = _cache.entries[key];
    if (cached != null) { update(ReaderTranslationState(translated: cached)); return; }
    update(const ReaderTranslationState(loading: true));
    try {
      final translated = await service.translate(text, snapshot, current: () => _current(generation, key));
      if (!_current(generation, key)) return;
      final saved = await ReaderPreferenceWrites.enqueue(prefs, () async {
        if (!_current(generation, key)) return false;
        final updated = {..._cache.entries}..remove(key);
        final next = _bounded({...updated, key: translated}, maxCache.clamp(0, 32), maxChars.clamp(0, 500000));
        if (!next.containsKey(key)) return false;
        final ok = await ReaderPreferenceWrites.putString(prefs, readerTranslationCacheKey, jsonEncode({'version': 1, 'entries': next}));
        if (ok) _cache.entries = next;
        return ok;
      });
      if (_current(generation, key)) update(ReaderTranslationState(translated: translated, cacheFailed: !saved));
    } catch (_) { if (_current(generation, key)) update(const ReaderTranslationState(failed: true)); }
  }
  void showOriginal() => reset();
  void reset() { ++_generation; if (!_closed) update(const ReaderTranslationState()); }
  @override
  Future<void> destroy() async {
    _closed = true; ++_generation; _disposeConfig();
    await ReaderPreferenceWrites.drain(prefs); await super.destroy();
  }
}
