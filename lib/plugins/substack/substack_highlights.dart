import 'dart:convert';

import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/plugins/substack/substack_blocks.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/utils/json.dart';

const substackHighlightsPreference = 'plugin.substack.highlights';
const substackHighlightsLimit = 2000;

/// A selection as the quotes it is saved as: one per block it spans, its
/// spaces collapsed; a stray letter is not a quote.
List<String> substackSelectionQuotes(String selected) => [
  for (final line in selected.split('\n'))
    if (line.replaceAll(RegExp(r'\s+'), ' ').trim() case final quote when quote.length >= 2) quote,
];

/// [runs] split where [quotes] occur in their text, those parts highlighted.
List<SubstackRun> substackHighlightRuns(List<SubstackRun> runs, List<String> quotes) {
  if (quotes.isEmpty) return runs;
  final text = runs.map((run) => run.text).join();
  final marked = List.filled(text.length, false);
  for (final quote in quotes) {
    final at = [for (var i = text.indexOf(quote); i >= 0; i = text.indexOf(quote, i + 1)) i];
    final words = at.where((i) => _wholeWords(text, i, quote)).toList();
    // A selection dragged from inside a word still shows where it was made.
    for (final i in words.isEmpty ? at : words) {
      marked.fillRange(i, i + quote.length, true);
    }
  }
  if (!marked.contains(true)) return runs;
  final out = <SubstackRun>[];
  var offset = 0;
  for (final run in runs) {
    var start = 0;
    while (start < run.text.length) {
      final on = marked[offset + start];
      var end = start + 1;
      while (end < run.text.length && marked[offset + end] == on) {
        end++;
      }
      out.add(run.copy(text: run.text.substring(start, end), highlighted: on));
      start = end;
    }
    offset += run.text.length;
  }
  return out;
}

/// Letters of scripts that put spaces between words, where a quote must
/// not begin or end inside a word ("an" is not highlighted in "plan").
/// Scripts without spaces, such as Chinese, match anywhere.
final _spacedWordChar = RegExp(
  r'[\p{Script=Latin}\p{Script=Greek}\p{Script=Cyrillic}\p{Script=Armenian}\p{N}]',
  unicode: true,
);

bool _wordChar(String text, int at) => at >= 0 && at < text.length && _spacedWordChar.hasMatch(text[at]);

bool _wholeWords(String text, int at, String quote) {
  final end = at + quote.length;
  final startsInWord = _wordChar(quote, 0) && _wordChar(text, at - 1);
  final endsInWord = _wordChar(quote, quote.length - 1) && _wordChar(text, end);
  return !startsInWord && !endsInWord;
}

/// [quotes] of [post] as Markdown: the title, where it is from, then each quote.
String substackHighlightsMarkdown(SubstackPost post, List<String> quotes) {
  final source = [post.publicationName, ?post.authorName].where((part) => part.trim().isNotEmpty).join(' · ');
  return [
    '# ${post.title}',
    if (source.isNotEmpty) source,
    ?post.canonicalUrl,
    for (final quote in quotes) '> ${quote.replaceAll(RegExp(r'^[#>\s]+'), '')}',
  ].join('\n\n');
}

/// The reader's highlights, by post, kept on this device and in backups with
/// the plugin's other preferences. At most [substackHighlightsLimit] quotes
/// are kept; the oldest posts' go first.
class SubstackHighlightsStore extends Store<Map<String, List<String>>> {
  final BasePrefService prefs;

  SubstackHighlightsStore(this.prefs) : super(const {}) {
    update(_read());
  }

  List<String> of(SubstackPost post) => state[substackPostKey(post)] ?? const [];

  /// Each change starts from what is saved now, so two readers open on two
  /// posts never write one another's highlights away.
  Future<void> add(SubstackPost post, Iterable<String> quotes) {
    final saved = _read();
    final key = substackPostKey(post);
    final kept = saved[key] ?? const [];
    final added = [...kept, ...quotes.where((quote) => !kept.contains(quote))];
    return _save({...Map.of(saved)..remove(key), key: added});
  }

  Future<void> remove(SubstackPost post, String quote) {
    final saved = _read();
    final key = substackPostKey(post);
    final left = (saved[key] ?? const []).where((kept) => kept != quote).toList();
    return _save(left.isEmpty ? (Map.of(saved)..remove(key)) : {...saved, key: left});
  }

  Future<void> _save(Map<String, List<String>> highlights) async {
    final bounded = _bounded(highlights);
    update(bounded);
    await prefs.set(substackHighlightsPreference, jsonEncode(bounded));
  }

  Map<String, List<String>> _read() {
    final raw = prefs.getKeys().contains(substackHighlightsPreference)
        ? prefs.get<String>(substackHighlightsPreference)
        : null;
    if (raw == null || raw.isEmpty) return const {};
    try {
      final decoded = jsonDecode(raw);
      if (decoded is! Map) return const {};
      return {
        for (final MapEntry(:key, :value) in decoded.entries)
          if (key is String) key: [for (final quote in Json(value).list) ?quote.string],
      };
    } catch (_) {
      return const {};
    }
  }
}

/// [highlights] with the oldest posts dropped until the newest fit the limit.
Map<String, List<String>> _bounded(Map<String, List<String>> highlights) {
  var total = 0;
  final kept = <MapEntry<String, List<String>>>[];
  for (final entry in highlights.entries.toList().reversed) {
    if (entry.value.isEmpty) continue;
    total += entry.value.length;
    if (total > substackHighlightsLimit && kept.isNotEmpty) break;
    kept.add(entry);
  }
  return Map.fromEntries(kept.reversed);
}
