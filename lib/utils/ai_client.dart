import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';

/// Device-local AI connection. Empty fields mean AI features stay off.
class AiConfig {
  final String baseUrl;
  final String apiKey;
  final String model;

  const AiConfig({
    required this.baseUrl,
    required this.apiKey,
    required this.model,
  });

  bool get isConfigured =>
      baseUrl.isNotEmpty && apiKey.isNotEmpty && model.isNotEmpty;

  bool get isGrok =>
      baseUrl.toLowerCase().contains('api.x.ai') ||
      model.toLowerCase().startsWith('grok');

  static AiConfig fromPrefs(BasePrefService prefs) => AiConfig(
    baseUrl: (prefs.get<String>(optionAiBaseUrl) ?? '').trim(),
    apiKey: (prefs.get<String>(optionAiApiKey) ?? '').trim(),
    model: (prefs.get<String>(optionAiModel) ?? '').trim(),
  );
}

/// One chat turn against an OpenAI-compatible `/chat/completions` root.
Future<String> aiChatCompletion(
  AiConfig config,
  String prompt, {
  http.Client? client,
}) async {
  if (!config.isConfigured) {
    throw const AiException('not configured');
  }
  final uri = Uri.parse(
    '${config.baseUrl.replaceAll(RegExp(r'/+$'), '')}/chat/completions',
  );
  final owned = client == null;
  final httpClient = client ?? http.Client();
  try {
    final response = await httpClient.post(
      uri,
      headers: {
        'Authorization': 'Bearer ${config.apiKey}',
        'Content-Type': 'application/json',
      },
      body: jsonEncode({
        'model': config.model,
        'messages': [
          {'role': 'user', 'content': prompt},
        ],
      }),
    );
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw AiException('HTTP ${response.statusCode}');
    }
    return aiCompletionText(response.body);
  } finally {
    if (owned) httpClient.close();
  }
}

/// Reads `choices[0].message.content` without throwing on a reshaped body.
String aiCompletionText(String body) {
  final json = jsonDecode(body);
  if (json is! Map) return '';
  final choices = json['choices'];
  if (choices is! List || choices.isEmpty) return '';
  final first = choices.first;
  if (first is! Map) return '';
  final message = first['message'];
  if (message is Map) {
    return (message['content'] as String?)?.trim() ?? '';
  }
  return (first['text'] as String?)?.trim() ?? '';
}

class AiException implements Exception {
  final String message;
  const AiException(this.message);

  @override
  String toString() => 'AiException: $message';
}
