import 'dart:convert';

const readingHistoryTextLimit = 2000;
const readingHistoryTitleLimit = 200;
const readingHistoryIdLimit = 2048;
const readingHistoryExtraLimit = 8;

enum ReadingHistoryKind { post, article, profile }

/// Query parameters that carry credentials rather than content; never kept in history.
const _credentialParameters = {
  'access_token',
  'api_key',
  'apikey',
  'auth',
  'authorization',
  'code',
  'id_token',
  'key',
  'password',
  'refresh_token',
  'secret',
  'session',
  'sessionid',
  'sig',
  'signature',
  'token',
};

String _bounded(String value, int limit) {
  if (value.length <= limit) return value;
  var end = limit;
  final last = value.codeUnitAt(end - 1);
  if (last >= 0xd800 && last <= 0xdbff) end--;
  return value.substring(0, end);
}

/// A public link to what was read: userinfo, fragments and credential parameters removed, other
/// parameters kept with their duplicates and order. Null when it is not a usable web link.
String? readingHistoryUrl(String? raw) {
  final uri = Uri.tryParse(raw?.trim() ?? '');
  if (uri == null || !{'http', 'https'}.contains(uri.scheme) || uri.host.isEmpty) return null;
  final query = uri.query
      .split('&')
      .where((pair) => pair.isNotEmpty)
      .where((pair) => !_credentialParameters.contains(Uri.decodeQueryComponent(pair.split('=').first).toLowerCase()))
      .join('&');
  final clean = Uri(
    scheme: uri.scheme,
    host: uri.host,
    port: uri.hasPort ? uri.port : null,
    path: uri.path,
    query: query.isEmpty ? null : query,
  ).toString();
  return clean.length > readingHistoryIdLimit ? null : clean;
}

/// One thing the reader actually looked at. Only these explicit fields are kept: no models, media or credentials.
class ReadingHistoryEntry {
  final String source;
  final ReadingHistoryKind kind;
  final String nativeId;
  final String? url;
  final String author;
  final String title;
  final String text;
  final DateTime viewedAt;

  /// What the source needs to reopen the item natively, e.g. a handle or a feed id.
  final Map<String, String> extra;

  ReadingHistoryEntry({
    required this.source,
    required this.kind,
    required String nativeId,
    String? url,
    String author = '',
    String title = '',
    String text = '',
    DateTime? viewedAt,
    Map<String, String> extra = const {},
  }) : nativeId = _bounded(nativeId, readingHistoryIdLimit),
       url = readingHistoryUrl(url),
       author = _bounded(author.trim(), readingHistoryTitleLimit),
       title = _bounded(title.trim(), readingHistoryTitleLimit),
       text = _bounded(text.trim(), readingHistoryTextLimit),
       viewedAt = viewedAt ?? DateTime.now(),
       extra = Map.unmodifiable({
         for (final entry in extra.entries.take(readingHistoryExtraLimit))
           _bounded(entry.key, 64): _bounded(entry.value, readingHistoryIdLimit),
       });

  /// Identifies the item across views; a second view replaces the first.
  String get key => jsonEncode([source, nativeId]);

  bool get valid => source.isNotEmpty && source.length <= 64 && nativeId.isNotEmpty;

  /// Title, author and text, lower-cased, for searching.
  String get haystack => '$title\n$author\n$text\n${url ?? ''}'.toLowerCase();

  bool matches(String query) {
    final terms = query.toLowerCase().trim().split(RegExp(r'\s+')).where((term) => term.isNotEmpty);
    final text = haystack;
    return terms.every(text.contains);
  }

  ReadingHistoryEntry viewedAgain(DateTime at) => ReadingHistoryEntry(
    source: source,
    kind: kind,
    nativeId: nativeId,
    url: url,
    author: author,
    title: title,
    text: text,
    viewedAt: at,
    extra: extra,
  );

  Map<String, Object> toJson() => {
    'source': source,
    'kind': kind.name,
    'id': nativeId,
    'url': ?url,
    if (author.isNotEmpty) 'author': author,
    if (title.isNotEmpty) 'title': title,
    if (text.isNotEmpty) 'text': text,
    'at': viewedAt.toUtc().toIso8601String(),
    if (extra.isNotEmpty) 'extra': extra,
  };

  static ReadingHistoryEntry? fromJson(Object? raw) {
    if (raw is! Map) return null;
    final kind = ReadingHistoryKind.values.where((value) => value.name == raw['kind']).firstOrNull;
    final at = raw['at'] is String ? DateTime.tryParse(raw['at'] as String) : null;
    final source = raw['source'];
    final id = raw['id'];
    if (kind == null || at == null || source is! String || id is! String) return null;
    final extra = raw['extra'];
    final entry = ReadingHistoryEntry(
      source: source,
      kind: kind,
      nativeId: id,
      url: raw['url'] is String ? raw['url'] as String : null,
      author: raw['author'] is String ? raw['author'] as String : '',
      title: raw['title'] is String ? raw['title'] as String : '',
      text: raw['text'] is String ? raw['text'] as String : '',
      viewedAt: at.toLocal(),
      extra: {
        if (extra is Map)
          for (final entry in extra.entries)
            if (entry.key is String && entry.value is String) entry.key as String: entry.value as String,
      },
    );
    return entry.valid ? entry : null;
  }
}
