import 'dart:convert';
import 'package:crypto/crypto.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:html/parser.dart' as html;
import 'package:xta/utils/local_json_store.dart';

String archivePlainText(String body) {
  final document = html.parse(body);
  for (final node in document.querySelectorAll('script,style,noscript')) {
    node.remove();
  }
  return document.body?.text.replaceAll(RegExp(r'\s+'), ' ').trim() ?? '';
}

List<String> suggestedArchiveTags(String text) {
  final counts = <String, int>{};
  const stop = {
    'this',
    'that',
    'with',
    'from',
    'have',
    'your',
    'about',
    'https',
    'there',
    'their',
    'would',
    'which',
    'these',
    'them',
    'then',
    'than',
    'were',
    'been',
    'also',
    'into',
    'what',
    'when',
    'will',
    'und',
    'oder',
    'eine',
    'einer',
    'einen',
    'nicht',
    'auch',
    'sich',
    'para',
    'pour',
    'avec',
    'dans',
  };
  for (final match in RegExp(r'[\p{L}\p{N}]{4,}', unicode: true).allMatches(text.toLowerCase())) {
    final token = match.group(0)!;
    if (!stop.contains(token)) counts[token] = (counts[token] ?? 0) + 1;
  }
  final tags = counts.keys.toList()..sort((a, b) => counts[b]!.compareTo(counts[a]!));
  return tags.take(8).toList();
}

class ArchiveAnnotation {
  final Map<String, String> highlights;
  final Set<String> tags;
  final String extracted;
  const ArchiveAnnotation({this.highlights = const {}, this.tags = const {}, this.extracted = ''});
  String get searchable =>
      '${highlights.entries.map((e) => '${e.key} ${e.value}').join(' ')} ${tags.join(' ')} $extracted';
  Map<String, dynamic> toJson() => {'highlights': highlights, 'tags': tags.toList(), 'extracted': extracted};
  static ArchiveAnnotation parse(Object? raw) {
    if (raw is! Map) return const ArchiveAnnotation();
    return ArchiveAnnotation(
      highlights: {
        if (raw['highlights'] is Map)
          for (final e in (raw['highlights'] as Map).entries)
            if (e.key is String && e.value is String) e.key as String: e.value as String,
      },
      tags: (raw['tags'] is List ? raw['tags'] as List : []).whereType<String>().toSet(),
      extracted: raw['extracted'] is String ? raw['extracted'] as String : '',
    );
  }
}

class ArchiveNotesStore extends Store<ArchiveAnnotation> {
  final String id;
  final JsonStore storage;
  bool _closed = false;
  ArchiveNotesStore(this.id, {JsonStore? storage})
    : storage = storage ?? LocalJsonStore.shared,
      super(const ArchiveAnnotation());
  String get key => 'archive-note:$id';
  Future<void> load() async {
    final data = await storage.read(key);
    if (!_closed) update(ArchiveAnnotation.parse(data));
  }

  Future<void> _pending = Future.value();
  Future<void> _mutate(ArchiveAnnotation Function(ArchiveAnnotation) change) {
    final next = _pending.then((_) async {
      final value = change(state);
      await storage.write(key, value.toJson());
      if (!_closed) update(value);
    });
    _pending = next.catchError((Object _) {});
    return next;
  }

  Future<void> highlight(String quote, String? note) => _mutate(
    (current) => ArchiveAnnotation(
      highlights: {...current.highlights, quote: note ?? ''},
      tags: current.tags,
      extracted: current.extracted,
    ),
  );
  Future<void> removeHighlight(String quote) => _mutate(
    (current) => ArchiveAnnotation(
      highlights: {...current.highlights}..remove(quote),
      tags: current.tags,
      extracted: current.extracted,
    ),
  );
  Future<void> toggleTag(String tag) => _mutate(
    (current) => ArchiveAnnotation(
      highlights: current.highlights,
      tags: current.tags.contains(tag) ? (Set.of(current.tags)..remove(tag)) : {...current.tags, tag},
      extracted: current.extracted,
    ),
  );
  Future<void> extracted(String value) =>
      _mutate((current) => ArchiveAnnotation(highlights: current.highlights, tags: current.tags, extracted: value));
  String draftKey(String quote) => 'highlight:$id:${sha256.convert(utf8.encode(quote))}';
  @override
  Future<void> destroy() {
    _closed = true;
    return super.destroy();
  }
}
