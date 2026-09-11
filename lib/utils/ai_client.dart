import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';

/// Device-local AI connection. Empty fields mean AI features stay off.
class AiConfig {
  final String baseUrl;
  final String apiKey;
  final String model;

  const AiConfig({required this.baseUrl, required this.apiKey, required this.model});

  bool get isConfigured => baseUrl.trim().isNotEmpty && apiKey.trim().isNotEmpty && model.trim().isNotEmpty;

  bool get isGrok => Uri.tryParse(baseUrl)?.host.toLowerCase() == 'api.x.ai' || model.toLowerCase().startsWith('grok');

  static AiConfig fromPrefs(BasePrefService prefs) => AiConfig(
    baseUrl: (prefs.get<String>(optionAiBaseUrl) ?? '').trim(),
    apiKey: (prefs.get<String>(optionAiApiKey) ?? '').trim(),
    model: (prefs.get<String>(optionAiModel) ?? '').trim(),
  );
}

/// Accepts either a compatible API base URL or a complete chat endpoint.
Uri aiChatUri(String address) {
  final uri = Uri.tryParse(address.trim());
  if (uri == null || !{'http', 'https'}.contains(uri.scheme) || uri.host.isEmpty || uri.userInfo.isNotEmpty) {
    throw const AiException('invalid server address');
  }
  var path = uri.path.replaceAll(RegExp(r'/+$'), '');
  if (uri.host.toLowerCase() == 'openrouter.ai' && (path.isEmpty || path == '/api')) {
    path = '/api/v1';
  }
  if (!path.endsWith('/chat/completions')) path = '$path/chat/completions';
  return uri.replace(path: path).removeFragment();
}

/// One chat turn against OpenRouter or another OpenAI-compatible provider.
Future<String> aiChatCompletion(
  AiConfig config,
  String prompt, {
  http.Client? client,
  Duration timeout = const Duration(seconds: 30),
}) async {
  if (!config.isConfigured) throw const AiException('not configured');
  final uri = aiChatUri(config.baseUrl);
  final owned = client == null;
  final httpClient = client ?? http.Client();
  try {
    final response = await httpClient
        .post(
          uri,
          headers: {'Authorization': 'Bearer ${config.apiKey.trim()}', 'Content-Type': 'application/json'},
          body: jsonEncode({
            'model': config.model.trim(),
            'messages': [
              {'role': 'user', 'content': prompt},
            ],
          }),
        )
        .timeout(timeout);
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw AiException('HTTP ${response.statusCode}');
    }
    return aiCompletionText(response.body);
  } finally {
    if (owned) httpClient.close();
  }
}

/// Reads completion text without throwing on a missing or reshaped body.
String aiCompletionText(String body) {
  dynamic json;
  try {
    json = jsonDecode(body);
  } on FormatException {
    return '';
  }
  if (json is! Map) return '';
  final choices = json['choices'];
  if (choices is! List || choices.isEmpty) return '';
  final first = choices.first;
  if (first is! Map) return '';
  final message = first['message'];
  final content = message is Map ? message['content'] : first['text'];
  if (content is String) return content.trim();
  if (content is List) {
    return content
        .whereType<Map>()
        .where((part) => part['type'] == 'text')
        .map((part) => part['text'])
        .whereType<String>()
        .join('\n')
        .trim();
  }
  return '';
}

class AiException implements Exception {
  final String message;
  const AiException(this.message);

  @override
  String toString() => 'AiException: $message';
}
