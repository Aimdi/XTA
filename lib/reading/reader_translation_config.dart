import 'dart:convert';

import 'package:crypto/crypto.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/reading/reader_preference_writes.dart';
import 'package:xta/utils/ai_client.dart';

const readerTranslationConfigKey = 'reading.translation.config.v1';

/// Kept apart from [readerTranslationConfigKey] so exports strip it: [isSecretPrefKey] matches the suffix.
const readerTranslationApiKeyKey = 'reading.translation.api_key';

const readerDeeplFreeBaseUrl = 'https://api-free.deepl.com';
const readerDeeplProBaseUrl = 'https://api.deepl.com';

enum ReaderTranslationProvider { disabled, deepl, libre, ai }

class ReaderTranslationConfig {
  final ReaderTranslationProvider provider;
  final String endpoint;
  final String apiKey;

  /// A locale code such as `de` or `pt_BR`; empty follows the app language.
  final String target;
  final AiConfig ai;

  const ReaderTranslationConfig({
    this.provider = ReaderTranslationProvider.disabled,
    this.endpoint = '',
    this.apiKey = '',
    this.target = '',
    this.ai = const AiConfig(baseUrl: '', apiKey: '', model: ''),
  });

  bool get enabled => provider != ReaderTranslationProvider.disabled;

  ReaderTranslationConfig withAi(AiConfig value) =>
      ReaderTranslationConfig(provider: provider, endpoint: endpoint, apiKey: apiKey, target: target, ai: value);

  Map<String, Object> toJson() => {'version': 1, 'provider': provider.name, 'endpoint': endpoint, 'target': target};

  /// Identifies one translation of [text] into [language] under every setting that could change it.
  String fingerprint(String text, String language) => sha256
      .convert(
        utf8.encode(
          jsonEncode([provider.name, endpoint.trim(), apiKey.trim(), language, ai.baseUrl, ai.apiKey, ai.model, text]),
        ),
      )
      .toString();

  @override
  bool operator ==(Object other) =>
      other is ReaderTranslationConfig &&
      provider == other.provider &&
      endpoint == other.endpoint &&
      apiKey == other.apiKey &&
      target == other.target &&
      ai.baseUrl == other.ai.baseUrl &&
      ai.apiKey == other.ai.apiKey &&
      ai.model == other.ai.model;

  @override
  int get hashCode => Object.hash(provider, endpoint, apiKey, target, ai.baseUrl, ai.apiKey, ai.model);
}

String _defaultEndpoint(ReaderTranslationConfig config) => switch (config.provider) {
  ReaderTranslationProvider.deepl =>
    config.apiKey.trim().endsWith(':fx') ? readerDeeplFreeBaseUrl : readerDeeplProBaseUrl,
  _ => '',
};

/// The provider's translate endpoint; a configured base path is kept.
Uri readerTranslationUri(ReaderTranslationConfig config, {String action = 'translate'}) {
  final address = config.endpoint.trim().isEmpty ? _defaultEndpoint(config) : config.endpoint.trim();
  final uri = Uri.tryParse(address);
  if (uri == null ||
      !{'http', 'https'}.contains(uri.scheme) ||
      uri.host.isEmpty ||
      uri.userInfo.isNotEmpty ||
      uri.hasQuery ||
      uri.hasFragment) {
    throw const FormatException('invalid endpoint');
  }
  final deepl = config.provider == ReaderTranslationProvider.deepl;
  var path = uri.path.replaceAll(RegExp(r'/+$'), '');
  for (final known in ['/v2/translate', '/translate', '/languages']) {
    if (path.endsWith(known)) path = path.substring(0, path.length - known.length);
  }
  if (deepl && path.endsWith('/v2')) path = path.substring(0, path.length - 3);
  return uri.replace(path: deepl ? '$path/v2/$action' : '$path/$action');
}

final _targetPattern = RegExp(r'^[A-Za-z]{2,3}([_-][A-Za-z0-9]{2,8})*$');

bool readerTranslationConfigValid(ReaderTranslationConfig config) {
  if (config.endpoint.length > 2048 || config.apiKey.length > 8192) return false;
  if (config.target.isNotEmpty && !_targetPattern.hasMatch(config.target)) return false;
  switch (config.provider) {
    case ReaderTranslationProvider.disabled:
      return true;
    case ReaderTranslationProvider.ai:
      try {
        aiChatUri(config.ai.baseUrl);
        return config.ai.isConfigured;
      } on AiException {
        return false;
      }
    case ReaderTranslationProvider.deepl || ReaderTranslationProvider.libre:
      if (config.provider == ReaderTranslationProvider.deepl && config.apiKey.trim().isEmpty) return false;
      try {
        readerTranslationUri(config);
        return true;
      } on FormatException {
        return false;
      }
  }
}

List<String> _localeParts(String code) =>
    code.replaceAll('-', '_').split('_').map((part) => part.toLowerCase()).toList(growable: false);

/// DeepL's `target_lang` for an app locale such as `pt_BR` or `zh_Hant`.
String deeplTargetCode(String code) {
  final parts = _localeParts(code);
  final rest = parts.skip(1).toSet();
  return switch (parts.first) {
    'en' => rest.contains('gb') ? 'EN-GB' : 'EN-US',
    'pt' => rest.contains('br') ? 'PT-BR' : 'PT-PT',
    'zh' => rest.intersection({'hant', 'tw', 'hk', 'mo'}).isEmpty ? 'ZH-HANS' : 'ZH-HANT',
    'nb' || 'no' || 'nn' => 'NB',
    final language => language.toUpperCase(),
  };
}

/// LibreTranslate codes to try for an app locale, best first; instances differ in naming.
List<String> libreTargetCandidates(String code) {
  final parts = _localeParts(code);
  final rest = parts.skip(1).toSet();
  return switch (parts.first) {
    'zh' when rest.intersection({'hant', 'tw', 'hk', 'mo'}).isNotEmpty => ['zh-Hant', 'zt', 'zh-TW', 'zh'],
    'zh' => ['zh-Hans', 'zh', 'zh-CN'],
    'pt' when rest.contains('br') => ['pt-BR', 'pb', 'pt'],
    'nb' || 'no' || 'nn' => ['nb', 'no'],
    final language => [language],
  };
}

ReaderTranslationConfig _readConfig(BasePrefService prefs) {
  final ai = AiConfig.fromPrefs(prefs);
  final apiKey = prefs.get<String>(readerTranslationApiKeyKey) ?? '';
  try {
    final raw = prefs.get<String>(readerTranslationConfigKey);
    if (raw == null || raw.length > 15000) return ReaderTranslationConfig(ai: ai, apiKey: apiKey);
    final map = jsonDecode(raw);
    if (map is! Map || map['version'] != 1) return ReaderTranslationConfig(ai: ai, apiKey: apiKey);
    final provider = ReaderTranslationProvider.values.where((value) => value.name == map['provider']).firstOrNull;
    final config = ReaderTranslationConfig(
      provider: provider ?? ReaderTranslationProvider.disabled,
      endpoint: map['endpoint'] is String ? map['endpoint'] : '',
      apiKey: apiKey,
      target: map['target'] is String ? map['target'] : '',
      ai: ai,
    );
    // An AI choice survives an incomplete AI setup; translating reports it instead.
    if (config.provider == ReaderTranslationProvider.ai || readerTranslationConfigValid(config)) return config;
    return ReaderTranslationConfig(target: config.target, ai: ai, apiKey: apiKey);
  } catch (_) {
    return ReaderTranslationConfig(ai: ai, apiKey: apiKey);
  }
}

/// Shared by every reader surface; owned by the preference service, never by a screen.
class ReaderTranslationConfigStore extends Store<ReaderTranslationConfig> {
  static final _instances = Expando<ReaderTranslationConfigStore>();
  static const _aiKeys = [optionAiBaseUrl, optionAiApiKey, optionAiModel];
  final BasePrefService prefs;
  bool _closed = false;
  int _intent = 0;

  ReaderTranslationConfigStore(this.prefs) : super(_readConfig(prefs)) {
    for (final key in [..._aiKeys, readerTranslationApiKeyKey, readerTranslationConfigKey]) {
      prefs.makeSecret(key);
    }
    for (final key in _aiKeys) {
      prefs.addKeyListener(key, _aiChanged);
    }
  }

  static ReaderTranslationConfigStore forPrefs(BasePrefService prefs) =>
      _instances[prefs] ??= ReaderTranslationConfigStore(prefs);

  void _aiChanged() {
    if (!_closed) update(state.withAi(AiConfig.fromPrefs(prefs)));
  }

  /// Saves [config] unless a later save supersedes it; the key is written before the settings naming it.
  Future<bool> save(ReaderTranslationConfig config) {
    final intent = ++_intent;
    return ReaderPreferenceWrites.enqueue(prefs, () async {
      if (_closed || intent != _intent || !readerTranslationConfigValid(config)) return false;
      final previousKey = prefs.get<String>(readerTranslationApiKeyKey) ?? '';
      final keySaved = await ReaderPreferenceWrites.putString(prefs, readerTranslationApiKeyKey, config.apiKey.trim());
      if (!keySaved) return false;
      final saved = await ReaderPreferenceWrites.putString(
        prefs,
        readerTranslationConfigKey,
        jsonEncode(config.toJson()),
      );
      if (!saved) {
        await ReaderPreferenceWrites.putString(prefs, readerTranslationApiKeyKey, previousKey);
        return false;
      }
      if (_closed || intent != _intent) return false;
      update(config.withAi(AiConfig.fromPrefs(prefs)));
      return true;
    });
  }

  @override
  Future<void> destroy() async {
    _closed = true;
    ++_intent;
    if (identical(_instances[prefs], this)) _instances[prefs] = null;
    for (final key in _aiKeys) {
      prefs.removeKeyListener(key, _aiChanged);
    }
    await ReaderPreferenceWrites.drain(prefs);
    await super.destroy();
  }
}
