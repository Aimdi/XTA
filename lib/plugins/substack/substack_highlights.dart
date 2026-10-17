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
    for (var at = text.indexOf(quote); at >= 0; at = text.indexOf(quote, at + quote.length)) {
      marked.fillRange(at, at + quote.length, true);
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

  Future<void> add(SubstackPost post, Iterable<String> quotes) {
    final key = substackPostKey(post);
    final kept = of(post);
    final added = [...kept, ...quotes.where((quote) => !kept.contains(quote))];
    return _save({...Map.of(state)..remove(key), key: added});
  }

  Future<void> remove(SubstackPost post, String quote) {
    final key = substackPostKey(post);
    final left = of(post).where((kept) => kept != quote).toList();
    return _save(left.isEmpty ? (Map.of(state)..remove(key)) : {...state, key: left});
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
