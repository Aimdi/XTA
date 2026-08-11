/// Pure parsers for booru JSON — unit-tested without HTTP.
library;

import 'package:xta/plugins/booru/booru_engines.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/utils/json.dart';

List<BooruPost> parseBooruPosts(
  Object? raw, {
  required BooruEngine engine,
  required String host,
}) {
  final root = Json(raw);
  final list = switch (engine) {
    BooruEngine.danbooru || BooruEngine.moebooru => root,
    BooruEngine.gelbooruV2 => root['post'].exists ? root['post'] : root,
  };

  if (list.raw is! List) return const [];

  return [
    for (final item in list.raw as List)
      ?_parseOne(Json(item), engine: engine, host: host),
  ];
}

BooruPost? _parseOne(
  Json json, {
  required BooruEngine engine,
  required String host,
}) {
  final id = _idOf(json);
  if (id == null) return null;

  final tags = _tagsOf(json, engine);
  final rating = BooruRating.tryParse(json['rating'].string);
  final score = json['score'].integer;
  final width = json['image_width'].integer ?? json['width'].integer ?? 0;
  final height = json['image_height'].integer ?? json['height'].integer ?? 0;

  final preview = json['preview_file_url'].string ?? json['preview_url'].string;
  final sample = json['large_file_url'].string ?? json['sample_url'].string;
  final file = json['file_url'].string;
  final ext = json['file_ext'].string ?? _extFromUrl(file ?? sample ?? preview);

  return BooruPost(
    id: id,
    host: host,
    engine: engine.id,
    tags: tags,
    rating: rating,
    score: score,
    width: width,
    height: height,
    previewUrl: _absolute(host, preview),
    sampleUrl: _absolute(host, sample),
    fileUrl: _absolute(host, file),
    fileExt: ext,
    source: json['source'].string,
    createdAt: _createdAt(json),
  );
}

String? _idOf(Json json) {
  final asInt = json['id'].integer;
  if (asInt != null) return '$asInt';
  final asString = json['id'].string;
  if (asString != null && asString.isNotEmpty) return asString;
  return null;
}

List<String> _tagsOf(Json json, BooruEngine engine) {
  final tagString = json['tag_string'].string ?? json['tags'].string;
  if (tagString != null && tagString.isNotEmpty) {
    return tagString
        .split(RegExp(r'\s+'))
        .where((t) => t.isNotEmpty)
        .toList(growable: false);
  }

  // Danbooru sometimes splits tags by category.
  if (engine == BooruEngine.danbooru) {
    final parts = [
      json['tag_string_artist'].string,
      json['tag_string_character'].string,
      json['tag_string_copyright'].string,
      json['tag_string_general'].string,
      json['tag_string_meta'].string,
    ].whereType<String>().where((s) => s.isNotEmpty);
    if (parts.isNotEmpty) {
      return parts
          .expand((s) => s.split(RegExp(r'\s+')))
          .where((t) => t.isNotEmpty)
          .toList(growable: false);
    }
  }

  return const [];
}

DateTime? _createdAt(Json json) {
  final asString = json['created_at'].string;
  if (asString != null && asString.isNotEmpty) {
    return DateTime.tryParse(asString);
  }
  final asInt = json['created_at'].integer;
  if (asInt != null && asInt > 0) {
    // Moebooru uses unix seconds; Gelbooru "change" is also unix-ish.
    if (asInt > 1e12) {
      return DateTime.fromMillisecondsSinceEpoch(asInt);
    }
    return DateTime.fromMillisecondsSinceEpoch(asInt * 1000);
  }
  final change = json['change'].integer;
  if (change != null && change > 0) {
    return DateTime.fromMillisecondsSinceEpoch(change * 1000);
  }
  return null;
}

String? _extFromUrl(String? url) {
  if (url == null || url.isEmpty) return null;
  final path = Uri.tryParse(url)?.path ?? url;
  final dot = path.lastIndexOf('.');
  if (dot < 0 || dot == path.length - 1) return null;
  return path.substring(dot + 1).toLowerCase();
}

String? _absolute(String host, String? url) {
  if (url == null || url.isEmpty) return null;
  if (url.startsWith('http://') || url.startsWith('https://')) return url;
  if (url.startsWith('//')) return 'https:$url';
  final base = Uri.tryParse(host);
  if (base == null) return url;
  return base.resolve(url).toString();
}

/// Whether [post] is allowed under the reader's maximum rating.
bool booruPostAllowed(BooruPost post, BooruRating maxRating) {
  final rating = post.rating;
  if (rating == null) return true;
  return !rating.exceeds(maxRating);
}
