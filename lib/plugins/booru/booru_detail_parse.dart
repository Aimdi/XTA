/// Pure parsers for what the post screen loads on demand: tag kinds, wiki
/// pages and comments.
library;

import 'package:xta/plugins/booru/booru_engines.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_parse.dart';
import 'package:xta/utils/json.dart';

/// Moebooru answers `include_tags` with a name → kind map; Gelbooru with its
/// tag list.
Map<String, BooruTagCategory> parseBooruTagKinds(Object? raw, {required BooruEngine engine}) {
  final kinds = Json(raw)['tags'].raw;
  if (engine == BooruEngine.moebooru && kinds is Map) {
    return Map.fromEntries([
      for (final MapEntry(:key, :value) in kinds.entries)
        if (key is String) ?_moebooruKind(key, value),
    ]);
  }
  return {for (final tag in parseBooruTagSuggestions(raw, engine: engine)) tag.name: ?tag.category};
}

MapEntry<String, BooruTagCategory>? _moebooruKind(String name, Object? kind) {
  final category = kind is int
      ? BooruTagCategory.fromWire(kind, BooruEngine.moebooru)
      : BooruTagCategory.named(kind is String ? kind : null);
  return category == null ? null : MapEntry(name, category);
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
