import 'dart:convert';
import 'package:crypto/crypto.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/reading/reader_preference_writes.dart';
import 'package:xta/utils/ai_client.dart';

const readerTranslationConfigKey = 'reading.translation.config.v1';
const readerTranslationCacheKey = 'reading.translation.cache.v1';
enum ReaderTranslationProvider { disabled, deepl, libre, ai }

class ReaderTranslationConfig {
  final ReaderTranslationProvider provider;
  final String endpoint;
  final String apiKey;
  final String target;
  final AiConfig ai;
  const ReaderTranslationConfig({this.provider = ReaderTranslationProvider.disabled, this.endpoint = '',
    this.apiKey = '', this.target = 'en', this.ai = const AiConfig(baseUrl: '', apiKey: '', model: '')});
  bool get enabled => provider != ReaderTranslationProvider.disabled;
  Map<String, Object> toJson() => {'provider': provider.name, 'endpoint': endpoint, 'apiKey': apiKey, 'target': target};
  String fingerprint(String text) => sha256.convert(utf8.encode(jsonEncode([
    toJson(), ai.baseUrl, ai.apiKey, ai.model, text,
  ]))).toString();
}

Uri readerTranslationUri(ReaderTranslationConfig config) {
  final fallback = config.provider == ReaderTranslationProvider.deepl ? 'https://api-free.deepl.com' : '';
  final uri = Uri.tryParse(config.endpoint.trim().isEmpty ? fallback : config.endpoint.trim());
  if (uri == null || !{'http', 'https'}.contains(uri.scheme) || uri.host.isEmpty ||
      uri.userInfo.isNotEmpty || uri.hasFragment || uri.hasQuery) throw const FormatException('invalid endpoint');
  var path = uri.path.replaceAll(RegExp(r'/+$'), '');
  final suffix = config.provider == ReaderTranslationProvider.deepl ? '/v2/translate' : '/translate';
  if (!path.endsWith(suffix)) path += suffix;
  return uri.replace(path: path);
}

bool readerTranslationConfigValid(ReaderTranslationConfig config) {
  if (config.endpoint.length > 2048 || config.apiKey.length > 8192 ||
      !RegExp(r'^[A-Za-z]{2,3}([_-][A-Za-z0-9]{2,8})*$').hasMatch(config.target)) return false;
  if (!config.enabled) return true;
  if (config.provider == ReaderTranslationProvider.ai) {
    try { aiChatUri(config.ai.baseUrl); return config.ai.isConfigured; } catch (_) { return false; }
  }
  if (config.provider == ReaderTranslationProvider.deepl && config.apiKey.trim().isEmpty) return false;
  try { readerTranslationUri(config); return true; } catch (_) { return false; }
}

ReaderTranslationConfig _readConfig(BasePrefService prefs) {
  prefs.makeSecret(readerTranslationConfigKey);
  for (final key in [optionAiBaseUrl, optionAiApiKey, optionAiModel]) { prefs.makeSecret(key); }
  final ai = AiConfig.fromPrefs(prefs);
  try {
    final raw = prefs.get<String>(readerTranslationConfigKey);
    if (raw != null && raw.length <= 15000) {
      final map = jsonDecode(raw);
      if (map is Map && map['version'] == 1 && map['endpoint'] is String && map['apiKey'] is String && map['target'] is String) {
        final provider = ReaderTranslationProvider.values.where((p) => p.name == map['provider']).firstOrNull;
        if (provider != null) {
          final config = ReaderTranslationConfig(provider: provider, endpoint: map['endpoint'], apiKey: map['apiKey'], target: map['target'], ai: ai);
          // Retain an AI selection even when the separate AI configuration is temporarily incomplete.
          if (config.provider == ReaderTranslationProvider.ai || readerTranslationConfigValid(config)) return config;
        }
      }
    }
  } catch (_) {}
  return ReaderTranslationConfig(ai: ai);
}

/// Shared configuration belongs to prefs; native controls and settings never dispose it.
class ReaderTranslationConfigStore extends Store<ReaderTranslationConfig> {
  static final _instances = Expando<ReaderTranslationConfigStore>();
  final BasePrefService prefs;
  bool _closed = false;
  int _intent = 0;
  ReaderTranslationConfigStore(this.prefs) : super(_readConfig(prefs)) {
    for (final key in [optionAiBaseUrl, optionAiApiKey, optionAiModel]) { prefs.addKeyListener(key, _aiChanged); }
  }
  static ReaderTranslationConfigStore forPrefs(BasePrefService prefs) => _instances[prefs] ??= ReaderTranslationConfigStore(prefs);
  void _aiChanged() {
    if (_closed) return;
    final old = state;
    update(ReaderTranslationConfig(provider: old.provider, endpoint: old.endpoint, apiKey: old.apiKey, target: old.target, ai: AiConfig.fromPrefs(prefs)));
  }
  Future<bool> save(ReaderTranslationConfig config) {
    final intent = ++_intent;
    return ReaderPreferenceWrites.enqueue(prefs, () async {
      if (_closed || intent != _intent || !readerTranslationConfigValid(config)) return false;
      final ok = await ReaderPreferenceWrites.putString(prefs, readerTranslationConfigKey, jsonEncode({'version': 1, ...config.toJson()}));
      if (!ok || _closed || intent != _intent) return false;
      update(config);
      return true;
    });
  }
  @override
  Future<void> destroy() async {
    _closed = true; ++_intent;
    for (final key in [optionAiBaseUrl, optionAiApiKey, optionAiModel]) { prefs.removeKeyListener(key, _aiChanged); }
    await ReaderPreferenceWrites.drain(prefs);
    await super.destroy();
  }
}
