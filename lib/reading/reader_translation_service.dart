import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:xta/reading/reader_translation_config.dart';
import 'package:xta/utils/ai_client.dart';

const readerTranslationChunk = 4000;
const readerTranslationMaxInput = 400000;
const _batchTexts = 50;
const _batchUnits = 60000;
const _libreBatchUnits = readerTranslationChunk;

enum ReaderTranslationFailure { cancelled, notConfigured, tooLong, network, provider }

class ReaderTranslationException implements Exception {
  final ReaderTranslationFailure reason;
  const ReaderTranslationException(this.reason);
  @override
  String toString() => 'ReaderTranslationException(${reason.name})';
}

/// One run of the original text. Joining every piece's [text] gives the original back.
class ReaderTranslationPiece {
  final String text;
  final bool translate;
  const ReaderTranslationPiece(this.text, {required this.translate});
}

final _url = RegExp(r'''(?:https?://|www\.)[^\s<>"'“”«»]+''', caseSensitive: false);
final _lineBreak = RegExp(r'\r\n|\r|\n');
final _letter = RegExp(r'\p{L}', unicode: true);
final _sentenceEnd = RegExp(r'[.!?。！？…]\s+');
final _space = RegExp(r'\s+');

/// Splits [text] so URLs, line breaks and surrounding spaces stay verbatim and no translated run exceeds [max].
List<ReaderTranslationPiece> readerTranslationPieces(String text, {int max = readerTranslationChunk}) {
  final pieces = <ReaderTranslationPiece>[];
  var start = 0;
  for (final line in _lineBreak.allMatches(text)) {
    _addLine(pieces, text.substring(start, line.start), max);
    pieces.add(ReaderTranslationPiece(line[0]!, translate: false));
    start = line.end;
  }
  _addLine(pieces, text.substring(start), max);
  return pieces;
}

void _addLine(List<ReaderTranslationPiece> pieces, String line, int max) {
  var start = 0;
  for (final url in _url.allMatches(line)) {
    _addWords(pieces, line.substring(start, url.start), max);
    pieces.add(ReaderTranslationPiece(url[0]!, translate: false));
    start = url.end;
  }
  _addWords(pieces, line.substring(start), max);
}

void _addWords(List<ReaderTranslationPiece> pieces, String run, int max) {
  if (run.isEmpty) return;
  if (run.length > max) {
    var start = 0;
    while (start < run.length) {
      final end = run.length - start > max ? _chunkEnd(run, start, max) : run.length;
      _addWords(pieces, run.substring(start, end), max);
      start = end;
    }
    return;
  }
  final leading = RegExp(r'^\s*').stringMatch(run)!;
  if (leading.length == run.length) {
    pieces.add(ReaderTranslationPiece(run, translate: false));
    return;
  }
  final trailing = RegExp(r'\s*$').stringMatch(run)!;
  final core = run.substring(leading.length, run.length - trailing.length);
  if (leading.isNotEmpty) pieces.add(ReaderTranslationPiece(leading, translate: false));
  pieces.add(ReaderTranslationPiece(core, translate: _letter.hasMatch(core)));
  if (trailing.isNotEmpty) pieces.add(ReaderTranslationPiece(trailing, translate: false));
}

/// A boundary after a sentence, else after a space, else at [max] without splitting a surrogate pair.
int _chunkEnd(String text, int start, int max) {
  final window = text.substring(start, start + max);
  final sentence = _sentenceEnd.allMatches(window).lastOrNull;
  if (sentence != null && sentence.end > max ~/ 4) return start + sentence.end;
  final space = _space.allMatches(window).lastOrNull;
  if (space != null && space.end > max ~/ 4) return start + space.end;
  final end = start + max;
  final high = text.codeUnitAt(end - 1);
  return high >= 0xd800 && high <= 0xdbff ? end - 1 : end;
}

typedef ReaderAiTranslation = Future<String> Function(AiConfig config, String prompt);

/// Talks to the reader's chosen provider. Nothing is sent unless [translate] is called.
class ReaderTranslationService {
  final http.Client? client;
  final ReaderAiTranslation? ai;
  final Duration timeout;
  final _libreLanguages = <String, Future<Set<String>>>{};

  ReaderTranslationService({this.client, this.ai, this.timeout = const Duration(seconds: 30)});

  static final shared = ReaderTranslationService();

  /// Translates [text] into [language], a locale code such as `de` or `pt_BR`.
  Future<String> translate(
    String text,
    ReaderTranslationConfig config, {
    required String language,
    bool Function()? current,
  }) async {
    if (!config.enabled || !readerTranslationConfigValid(config)) {
      throw const ReaderTranslationException(ReaderTranslationFailure.notConfigured);
    }
    if (text.length > readerTranslationMaxInput) {
      throw const ReaderTranslationException(ReaderTranslationFailure.tooLong);
    }
    final pieces = readerTranslationPieces(text);
    final sources = [
      for (final piece in pieces)
        if (piece.translate) piece.text,
    ];
    if (sources.isEmpty) return text;
    final translated = await _translateAll(sources, config, language, current ?? () => true);
    var next = 0;
    return pieces.map((piece) => piece.translate ? translated[next++] : piece.text).join();
  }

  Future<List<String>> _translateAll(
    List<String> texts,
    ReaderTranslationConfig config,
    String language,
    bool Function() current,
  ) async {
    final result = <String>[];
    for (final batch in _batches(texts, config.provider == ReaderTranslationProvider.libre)) {
      if (!current()) throw const ReaderTranslationException(ReaderTranslationFailure.cancelled);
      final translated = await _request(batch, config, language);
      if (translated.length != batch.length || translated.any((text) => text.trim().isEmpty)) {
        throw const ReaderTranslationException(ReaderTranslationFailure.provider);
      }
      result.addAll(translated.map((text) => text.trim()));
    }
    if (!current()) throw const ReaderTranslationException(ReaderTranslationFailure.cancelled);
    return result;
  }

  Iterable<List<String>> _batches(List<String> texts, bool libre) sync* {
    final limit = libre ? _libreBatchUnits : _batchUnits;
    var batch = <String>[];
    var units = 0;
    for (final text in texts) {
      if (batch.isNotEmpty && (batch.length == _batchTexts || units + text.length > limit)) {
        yield batch;
        batch = [];
        units = 0;
      }
      batch.add(text);
      units += text.length;
    }
    if (batch.isNotEmpty) yield batch;
  }

  Future<List<String>> _request(List<String> texts, ReaderTranslationConfig config, String language) async {
    try {
      return switch (config.provider) {
        ReaderTranslationProvider.deepl => await _deepl(texts, config, language),
        ReaderTranslationProvider.libre => await _libre(texts, config, language),
        ReaderTranslationProvider.ai => await _ai(texts, config, language),
        ReaderTranslationProvider.disabled => throw const ReaderTranslationException(
          ReaderTranslationFailure.notConfigured,
        ),
      };
    } on ReaderTranslationException {
      rethrow;
    } on FormatException {
      throw const ReaderTranslationException(ReaderTranslationFailure.provider);
    } on AiException {
      throw const ReaderTranslationException(ReaderTranslationFailure.provider);
    } catch (_) {
      throw const ReaderTranslationException(ReaderTranslationFailure.network);
    }
  }

  Future<Object?> _postJson(Uri uri, Map<String, Object> body, {Map<String, String> headers = const {}}) async {
    final owned = client == null;
    final transport = client ?? http.Client();
    try {
      final response = await transport
          .post(uri, headers: {'Content-Type': 'application/json', ...headers}, body: jsonEncode(body))
          .timeout(timeout);
      if (response.statusCode < 200 || response.statusCode >= 300) {
        throw const ReaderTranslationException(ReaderTranslationFailure.provider);
      }
      return jsonDecode(utf8.decode(response.bodyBytes));
    } finally {
      if (owned) transport.close();
    }
  }

  Future<List<String>> _deepl(List<String> texts, ReaderTranslationConfig config, String language) async {
    final json = await _postJson(
      readerTranslationUri(config),
      {'text': texts, 'target_lang': deeplTargetCode(language), 'preserve_formatting': true},
      headers: {'Authorization': 'DeepL-Auth-Key ${config.apiKey.trim()}'},
    );
    final translations = json is Map ? json['translations'] : null;
    if (translations is! List) throw const FormatException('translations');
    return [for (final row in translations) row is Map && row['text'] is String ? row['text'] as String : ''];
  }

  Future<List<String>> _libre(List<String> texts, ReaderTranslationConfig config, String language) async {
    final json = await _postJson(readerTranslationUri(config), {
      'q': texts.length == 1 ? texts.single : texts,
      'source': 'auto',
      'target': await _libreTarget(config, language),
      'format': 'text',
      if (config.apiKey.trim().isNotEmpty) 'api_key': config.apiKey.trim(),
    });
    final value = json is Map ? json['translatedText'] : null;
    if (value is String) return [value];
    if (value is List) return [for (final text in value) text is String ? text : ''];
    throw const FormatException('translatedText');
  }

  /// The instance's own name for [language]; falls back to the usual code when it cannot say.
  Future<String> _libreTarget(ReaderTranslationConfig config, String language) async {
    final candidates = libreTargetCandidates(language);
    final uri = readerTranslationUri(config, action: 'languages');
    final known = await (_libreLanguages[uri.toString()] ??= _fetchLibreLanguages(uri));
    return candidates.where(known.contains).firstOrNull ?? candidates.first;
  }

  Future<Set<String>> _fetchLibreLanguages(Uri uri) async {
    final owned = client == null;
    final transport = client ?? http.Client();
    try {
      final response = await transport.get(uri).timeout(timeout);
      final json = jsonDecode(utf8.decode(response.bodyBytes));
      if (response.statusCode != 200 || json is! List) return const {};
      return {
        for (final row in json)
          if (row is Map && row['code'] is String) row['code'] as String,
      };
    } catch (_) {
      _libreLanguages.remove(uri.toString());
      return const {};
    } finally {
      if (owned) transport.close();
    }
  }

  Future<List<String>> _ai(List<String> texts, ReaderTranslationConfig config, String language) async {
    final name = readerTranslationLanguageName(language);
    if (texts.length == 1) {
      final prompt =
          'Translate the following text into $name. Reply with only the translation, keeping its meaning, '
          'tone and any names, mentions and hashtags.\n\n${texts.single}';
      return [await _complete(config, prompt)];
    }
    final prompt =
        'Translate every string of this JSON array into $name. Reply with only a JSON array of the translated '
        'strings, in the same order and with the same number of entries.\n\n${jsonEncode(texts)}';
    final reply = await _complete(config, prompt);
    final start = reply.indexOf('[');
    final end = reply.lastIndexOf(']');
    final json = start < 0 || end <= start ? null : jsonDecode(reply.substring(start, end + 1));
    if (json is List && json.length == texts.length && json.every((row) => row is String)) {
      return json.cast<String>();
    }
    return [
      for (final text in texts) ...await _ai([text], config, language),
    ];
  }

  Future<String> _complete(ReaderTranslationConfig config, String prompt) =>
      ai?.call(config.ai, prompt) ?? aiChatCompletion(config.ai, prompt, client: client, timeout: timeout);
}

/// How the AI prompt names a locale code.
String readerTranslationLanguageName(String code) => switch (code.replaceAll('-', '_')) {
  'ar' => 'Arabic',
  'be' => 'Belarusian',
  'be_Latn' => 'Belarusian written in the Latin alphabet (Łacinka)',
  'ca' => 'Catalan',
  'cs' => 'Czech',
  'de' => 'German',
  'en' => 'English',
  'eo' => 'Esperanto',
  'es' => 'Spanish',
  'et' => 'Estonian',
  'eu' => 'Basque',
  'fr' => 'French',
  'hi' => 'Hindi',
  'id' => 'Indonesian',
  'it' => 'Italian',
  'ja' => 'Japanese',
  'ko' => 'Korean',
  'nb_NO' || 'nb' => 'Norwegian Bokmål',
  'nl' => 'Dutch',
  'pl' => 'Polish',
  'pt' => 'European Portuguese',
  'pt_BR' => 'Brazilian Portuguese',
  'ro' => 'Romanian',
  'ru' => 'Russian',
  'tr' => 'Turkish',
  'uk' => 'Ukrainian',
  'vi' => 'Vietnamese',
  'zh_Hans' => 'Simplified Chinese',
  'zh_Hant' => 'Traditional Chinese',
  final other => other,
};
