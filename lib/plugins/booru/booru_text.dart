/// Wiki pages and comments arrive as DText (Danbooru, e621) or light HTML
/// (Moebooru). Shown as plain text: links keep their label, markup goes.
library;

final _wikiLink = RegExp(r'\[\[([^\]|]+)\|([^\]]*)\]\]');
final _plainWikiLink = RegExp(r'\[\[([^\]]+)\]\]');
final _tagSearch = RegExp(r'\{\{([^}]+)\}\}');
final _namedLink = RegExp(r'"([^"]+)":\[?(?:https?://|/)[^\s\]]*\]?');
final _bracketMarkup = RegExp(r'\[/?[a-z]+(?:=[^\]]*)?\]', caseSensitive: false);
final _heading = RegExp(r'^h[1-6](?:#[\w-]+)?\.\s*', multiLine: true);
final _htmlBreak = RegExp(r'<br\s*/?>', caseSensitive: false);
final _htmlTag = RegExp(r'<[^>]+>');
final _blankRun = RegExp(r'\n{3,}');

/// `&amp;` last, so `&amp;lt;` stays the text `&lt;`.
const _entities = {'&lt;': '<', '&gt;': '>', '&quot;': '"', '&#39;': "'", '&nbsp;': ' ', '&amp;': '&'};

String booruPlainText(String raw) {
  final text = raw
      .replaceAll('\r\n', '\n')
      .replaceAllMapped(_wikiLink, (m) => m[2]!.isEmpty ? m[1]! : m[2]!)
      .replaceAllMapped(_plainWikiLink, (m) => m[1]!)
      .replaceAllMapped(_tagSearch, (m) => m[1]!)
      .replaceAllMapped(_namedLink, (m) => m[1]!)
      .replaceAll(_bracketMarkup, '')
      .replaceAll(_heading, '')
      .replaceAll(_htmlBreak, '\n')
      .replaceAll(_htmlTag, '');
  return _entities.entries
      .fold(text, (text, entity) => text.replaceAll(entity.key, entity.value))
      .replaceAll(_blankRun, '\n\n')
      .trim();
}
