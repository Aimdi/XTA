/// Pure helpers for search fields that take several space-separated words,
/// such as booru tag queries and Pixiv multi-tag searches.
library;

final _whitespace = RegExp(r'\s+');

List<String> queryWords(String raw) =>
    raw.trim().split(_whitespace).where((word) => word.isNotEmpty).toList(growable: false);

String queryText(Iterable<String> words) => words.join(' ');

/// Text typed into a search field. Every word followed by whitespace is
/// finished; whatever follows the last whitespace is still being typed.
({List<String> done, String rest}) splitQueryInput(String text) {
  final match = RegExp(r'\s(?=\S*$)').firstMatch(text);
  if (match == null) return (done: const [], rest: text);
  return (done: queryWords(text.substring(0, match.start)), rest: text.substring(match.end));
}
