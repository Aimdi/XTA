import 'dart:convert';
import 'package:flutter/widgets.dart';
import 'package:http/http.dart' as http;
import 'package:xta/reading/reader_translation_config.dart';
import 'package:xta/utils/ai_client.dart';
import 'package:xta/plugins/substack/substack_html.dart';

typedef ReaderAiTranslation = Future<String> Function(AiConfig config, String prompt);
String readerArticlePlainText(String html) => substackHtmlToPlainText(html);

class ReaderTranslationServiceScope extends InheritedWidget {
  final ReaderTranslationService service;
  const ReaderTranslationServiceScope({super.key, required this.service, required super.child});
  static ReaderTranslationService? of(BuildContext context) => context.dependOnInheritedWidgetOfExactType<ReaderTranslationServiceScope>()?.service;
  @override
  bool updateShouldNotify(ReaderTranslationServiceScope oldWidget) => service != oldWidget.service;
}

/// URLs use collision-free atomic tokens, restored only after every request succeeds.
class ReaderTranslationService {
  final http.Client? client;
  final ReaderAiTranslation? ai;
  final Duration timeout;
  ReaderTranslationService({this.client, this.ai, this.timeout = const Duration(seconds: 30)});
  Future<String> translate(String text, ReaderTranslationConfig config, {bool Function()? current}) async {
    if (!config.enabled || text.trim().isEmpty) return text;
    if (text.length > 400000 || !readerTranslationConfigValid(config)) throw const FormatException('invalid input');
    final urls = <String, String>{};
    var prefix = 'XTA_URL_';
    while (text.contains(prefix)) { prefix = '${prefix}_'; }
    final protected = text.replaceAllMapped(RegExp(r'https?://[^\s<>]+', caseSensitive: false), (match) {
      final token = '${prefix}${urls.length}_END'; urls[token] = match[0]!; return token;
    });
    final result = StringBuffer();
    for (final part in _parts(protected, urls.keys)) {
      if (current?.call() == false) throw const FormatException('cancelled');
      if (part.trim().isEmpty) { result.write(part); continue; }
      final leading = RegExp(r'^\s*').stringMatch(part)!;
      final trailing = RegExp(r'\s*$').firstMatch(part)![0]!;
      final body = part.substring(leading.length, part.length - trailing.length);
      final translated = await _request(body, config).timeout(timeout);
      if (translated.trim().isEmpty) throw const FormatException('empty translation');
      result.write('$leading${translated.trim()}$trailing');
    }
    if (current?.call() == false) throw const FormatException('cancelled');
    var restored = result.toString();
    for (final entry in urls.entries) {
      if (entry.key.allMatches(restored).length != 1) throw const FormatException('invalid URL token');
      restored = restored.replaceAll(entry.key, entry.value);
    }
    return restored;
  }
  Future<String> _request(String text, ReaderTranslationConfig config) async {
    if (config.provider == ReaderTranslationProvider.ai) {
      final prompt = 'Translate the following text into ${config.target}. Return only the translation. '
          'Keep all XTA_URL tokens unchanged and retain paragraph breaks.\n\n$text';
      return ai != null ? ai!(config.ai, prompt) : aiChatCompletion(config.ai, prompt, client: client, timeout: timeout);
    }
    final owned = client == null;
    final transport = client ?? http.Client();
    final deepl = config.provider == ReaderTranslationProvider.deepl;
    try {
      final response = await transport.post(readerTranslationUri(config), headers: {
        'Content-Type': 'application/json', if (deepl) 'Authorization': 'DeepL-Auth-Key ${config.apiKey.trim()}',
      }, body: jsonEncode(deepl ? {'text': [text], 'target_lang': config.target.replaceAll('_', '-').toUpperCase()} : {
        'q': text, 'source': 'auto', 'target': config.target, 'format': 'text',
        if (config.apiKey.trim().isNotEmpty) 'api_key': config.apiKey.trim(),
      })).timeout(timeout);
      if (response.statusCode < 200 || response.statusCode >= 300) throw const FormatException('provider failed');
      final json = jsonDecode(response.body);
      final translations = json is Map ? json['translations'] : null;
      final value = deepl ? (translations is List && translations.isNotEmpty && translations.first is Map ? translations.first['text'] : null)
          : (json is Map ? json['translatedText'] : null);
      if (value is! String) throw const FormatException('invalid response'); return value;
    } finally { if (owned) transport.close(); }
  }
}

Iterable<String> _parts(String text, Iterable<String> tokens) sync* {
  var start = 0;
  for (final newline in RegExp(r'\r?\n').allMatches(text)) {
    yield* _chunks(text.substring(start, newline.start), tokens); yield newline[0]!; start = newline.end;
  }
  yield* _chunks(text.substring(start), tokens);
}
Iterable<String> _chunks(String text, Iterable<String> tokens) sync* {
  var start = 0;
  while (start < text.length) {
    var end = (start + 4000).clamp(0, text.length);
    if (end < text.length) {
      if (text.codeUnitAt(end - 1) >= 0xd800 && text.codeUnitAt(end - 1) <= 0xdbff) --end;
      for (final token in tokens) {
        final at = text.lastIndexOf(token, end);
        if (at >= start && at < end && at + token.length > end) end = at;
      }
      final space = RegExp(r'\s+').allMatches(text.substring(start, end)).lastOrNull;
      if (space != null && space.start > 0) end = start + space.end;
    }
    if (end <= start) throw const FormatException('invalid boundary');
    yield text.substring(start, end); start = end;
  }
}
