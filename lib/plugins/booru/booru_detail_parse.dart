/// Pure parsers for what the post screen loads on demand: tag kinds, wiki
/// pages and comments.
library;

import 'package:xta/plugins/booru/booru_engines.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_parse.dart';
import 'package:xta/plugins/booru/booru_text.dart';
import 'package:xta/utils/json.dart';

/// Moebooru answers `include_tags` with a name → kind map; the others with
/// a tag list carrying kinds and counts.
Map<String, BooruTagInfo> parseBooruTagInfo(Object? raw, {required BooruEngine engine}) {
  final kinds = Json(raw)['tags'].raw;
  if (engine == BooruEngine.moebooru && kinds is Map) {
    return Map.fromEntries([
      for (final MapEntry(:key, :value) in kinds.entries)
        if (key is String) ?_moebooruKind(key, value),
    ]);
  }
  return {
    for (final tag in parseBooruTagSuggestions(raw, engine: engine))
      tag.name: BooruTagInfo(category: tag.category, postCount: tag.postCount),
  };
}

MapEntry<String, BooruTagInfo>? _moebooruKind(String name, Object? kind) {
  final category = kind is int
      ? BooruTagCategory.fromWire(kind, BooruEngine.moebooru)
      : BooruTagCategory.named(kind is String ? kind : null);
  return category == null ? null : MapEntry(name, BooruTagInfo(category: category));
}

/// The body of the wiki page for [tag], or null when there is none. Hosts
/// answer a title search with a list; a single page is taken as it is.
String? parseBooruWiki(Object? raw, {required String tag}) {
  final root = Json(raw);
  final page = root.raw is List ? root.list.where((page) => _sameTitle(page['title'].string, tag)).firstOrNull : root;
  if (page == null || page['is_deleted'].boolean == true) return null;
  final body = page['body'].string?.trim();
  return body == null || body.isEmpty ? null : body;
}

bool _sameTitle(String? title, String tag) =>
    title != null && title.toLowerCase().replaceAll(' ', '_') == tag.toLowerCase();

/// Visible comments, oldest first.
List<BooruComment> parseBooruComments(Object? raw) {
  final root = Json(raw);
  final list = root.raw is List ? root.list : root['comments'].list;
  final comments = [for (final item in list) ?_comment(item)];
  return comments..sort(_byDate);
}

BooruComment? _comment(Json json) {
  final id = json['id'].integer;
  final body = json['body'].string?.trim();
  final hidden = json['is_deleted'].boolean == true || json['is_hidden'].boolean == true;
  if (id == null || body == null || body.isEmpty || hidden) return null;
  return BooruComment(
    id: '$id',
    author: json['creator']['name'].string ?? json['creator_name'].string ?? json['creator'].string,
    body: body,
    createdAt: DateTime.tryParse(json['created_at'].string ?? ''),
    score: json['score'].integer,
  );
}

int _byDate(BooruComment a, BooruComment b) {
  final ad = a.createdAt;
  final bd = b.createdAt;
  if (ad != null && bd != null) return ad.compareTo(bd);
  return (int.tryParse(a.id) ?? 0).compareTo(int.tryParse(b.id) ?? 0);
}

/// Related tag names in the host's order: Danbooru's `related_tags`
/// entries, or e621's tag record, whose `related_tags` alternates names and
/// weights in one string.
List<String> parseBooruRelatedTags(Object? raw, BooruEngine engine) => switch (engine) {
  BooruEngine.danbooru => [for (final related in Json(raw)['related_tags'].list) ?related['tag']['name'].string],
  BooruEngine.e621 => _e621RelatedTags(Json(raw).list.firstOrNull?['related_tags'].string),
  _ => const [],
};

List<String> _e621RelatedTags(String? pairs) {
  final words = (pairs ?? '').split(' ').where((word) => word.isNotEmpty).toList();
  return [
    for (var i = 0; i + 1 < words.length; i += 2)
      if (double.tryParse(words[i + 1]) != null) words[i],
  ];
}

/// The artist entry for [name] from the host's list: the one with that
/// name, else the first. Deleted entries are skipped, and links the host
/// marks inactive are left out.
BooruArtist? parseBooruArtist(Object? raw, String name) {
  final entries = [
    for (final entry in Json(raw).list)
      if (entry['is_deleted'].boolean != true && entry['name'].string != null) entry,
  ];
  final entry =
      entries.where((entry) => entry['name'].string!.toLowerCase() == name.toLowerCase()).firstOrNull ??
      entries.firstOrNull;
  if (entry == null) return null;
  return BooruArtist(
    name: entry['name'].string!,
    otherNames: [for (final other in entry['other_names'].list) ?other.string],
    urls: [
      for (final url in entry['urls'].list)
        if (url['is_active'].boolean != false) ?url['url'].string,
    ],
  );
}

/// The pools in a `/pools.json` answer, deleted ones left out.
List<BooruPool> parseBooruPools(Object? raw) => [
  for (final pool in Json(raw).list)
    if (pool['is_deleted'].boolean != true)
      if ((pool['id'].integer?.toString() ?? pool['id'].string, pool['name'].string) case (
        final String id,
        final String name,
      ))
        BooruPool(id: id, name: name, postCount: pool['post_count'].integer),
];

/// The active notes in a `/notes.json` answer, their HTML read as plain
/// text; notes without a box or text are left out.
List<BooruNote> parseBooruNotes(Object? raw) => [
  for (final note in Json(raw).list)
    if (note['is_active'].boolean != false)
      if ((
            note['x'].integer,
            note['y'].integer,
            note['width'].integer,
            note['height'].integer,
            booruPlainText(note['body'].string ?? ''),
          )
          case (final int x, final int y, final int width, final int height, final String text) when text.isNotEmpty)
        BooruNote(x: x, y: y, width: width, height: height, text: text),
];
