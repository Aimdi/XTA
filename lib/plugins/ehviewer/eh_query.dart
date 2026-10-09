/// Editing an EH search query as it is typed. Tag names have spaces in them
/// (`big breasts`), so the term being typed runs from the end of the last
/// finished term — one closed by `$` or a quote — to the end of the text.
library;

import 'package:xta/plugins/ehviewer/eh_models.dart';

/// Where the term being typed starts: after the last finished term.
int ehLastTermStart(String text) {
  var start = 0;
  var quoted = false;
  for (var i = 0; i < text.length; i++) {
    final char = text[i];
    if (char == '"') quoted = !quoted;
    if (char == ' ' && !quoted && i > 0 && (text[i - 1] == '"' || text[i - 1] == r'$')) start = i + 1;
  }
  return start;
}

/// A span of the text a suggestion would replace, from [start] to the end,
/// and what to ask the site to complete for it.
typedef EhSuggestionTerm = ({int start, String prefix});

/// The operator a term starts with: `-` excludes, `~` is either-of.
String _operatorOf(String term) => term.startsWith('-') || term.startsWith('~') ? term[0] : '';

/// What to ask the site for, longest first: the whole term, its last two
/// words, its last word. A finished (`$`) term asks for nothing.
List<EhSuggestionTerm> ehSuggestionTerms(String text) {
  final start = ehLastTermStart(text);
  final term = text.substring(start);
  if (term.contains(r'$')) return const [];
  final words = [
    0,
    for (var i = 0; i < term.length - 1; i++)
      if (term[i] == ' ') i + 1,
  ];
  final offsets = {0, if (words.length > 2) words[words.length - 2], words.last};
  return [
    for (final offset in offsets)
      if (_prefixAt(term.substring(offset)) case final prefix?) (start: start + offset, prefix: prefix),
  ];
}

String? _prefixAt(String span) {
  final prefix = span.substring(_operatorOf(span).length).replaceAll('"', '').trim();
  return prefix.length < 2 ? null : prefix;
}

/// [text] with [term] replaced by [tag]'s exact search, keeping a `-` or `~`
/// typed before it, ready for the next term.
String ehInsertSuggestion(String text, EhSuggestionTerm term, EhTag tag) =>
    '${text.substring(0, term.start)}${_operatorOf(text.substring(term.start))}${tag.query} ';
