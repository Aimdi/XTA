/// Pure parsers for booru JSON — unit-tested without HTTP.
library;

import 'dart:convert';

import 'package:xta/plugins/booru/booru_engines.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_query.dart';
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
    BooruEngine.e621 => root['posts'].exists ? root['posts'] : root,
  };

  if (list.raw is! List) return const [];

  return [
    for (final item in list.raw as List)
      ?_parseOne(Json(item), engine: engine, host: host),
  ];
}

List<BooruTagSuggestion> parseBooruTagSuggestions(
  Object? raw, {
  required BooruEngine engine,
}) {
  final root = Json(raw);
  final list = switch (engine) {
    BooruEngine.gelbooruV2 => root['tag'].exists ? root['tag'] : root,
    BooruEngine.e621 => root['tags'].exists ? root['tags'] : root,
    _ => root,
  };
  if (list.raw is! List) return const [];

  return [
    for (final item in list.raw as List) ?_suggestionOf(Json(item), engine),
  ];
}

BooruTagSuggestion? _suggestionOf(Json json, BooruEngine engine) {
  final name = json['name'].string ?? json['tag'].string;
  if (name == null || name.isEmpty) return null;
  final count =
      json['post_count'].integer ??
      json['count'].integer ??
      json['posts'].integer;
  return BooruTagSuggestion(
    name: name,
    postCount: count,
    category: _categoryOf(json, engine),
  );
}

BooruTagCategory? _categoryOf(Json json, BooruEngine engine) {
  final code = json['category'].integer ?? json['type'].integer;
  if (code != null) return BooruTagCategory.fromWire(code, engine);
  return BooruTagCategory.named(json['type'].string);
}

BooruPost? _parseOne(
  Json json, {
  required BooruEngine engine,
  required String host,
}) {
  if (engine == BooruEngine.e621) {
    return _parseE621(json, host: host);
  }

  final id = _idOf(json);
  if (id == null) return null;

  final tags = _tagsOf(json, engine);
  final rating = BooruRating.parseWire(json['rating'].string, engine);
  final score = json['score'].integer;
  final width = json['image_width'].integer ?? json['width'].integer ?? 0;
  final height = json['image_height'].integer ?? json['height'].integer ?? 0;

  final preview = json['preview_file_url'].string ?? json['preview_url'].string;
  final sample = json['large_file_url'].string ?? json['sample_url'].string;
  final file = json['file_url'].string ?? _composedFileUrl(json);
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
    tagCategories: _danbooruCategories(json),
    fileSize: _positive(json['file_size']),
    md5: _nonEmpty(json['md5'].string ?? json['hash'].string),
    favCount: json['fav_count'].integer,
    upScore: json['up_score'].integer,
    downScore: json['down_score'].integer?.abs(),
    parentId: _parentId(json['parent_id']),
    hasChildren: _flag(json['has_children']),
    uploader: _nonEmpty(
      json['author'].string ??
          json['owner'].string ??
          json['uploader_name'].string,
    ),
  );
}

const _danbooruCategoryFields = {
  'tag_string_artist': BooruTagCategory.artist,
  'tag_string_copyright': BooruTagCategory.copyright,
  'tag_string_character': BooruTagCategory.character,
  'tag_string_general': BooruTagCategory.general,
  'tag_string_meta': BooruTagCategory.meta,
};

Map<String, BooruTagCategory> _danbooruCategories(Json json) => {
  for (final MapEntry(key: field, value: category)
      in _danbooruCategoryFields.entries)
    for (final tag in _splitTags(json[field].string)) tag: category,
};

const _e621CategoryGroups = {
  'artist': BooruTagCategory.artist,
  'copyright': BooruTagCategory.copyright,
  'character': BooruTagCategory.character,
  'species': BooruTagCategory.species,
  'general': BooruTagCategory.general,
  'meta': BooruTagCategory.meta,
  'lore': BooruTagCategory.meta,
};

Map<String, BooruTagCategory> _e621Categories(Json tags) => {
  for (final MapEntry(key: group, value: category)
      in _e621CategoryGroups.entries)
    for (final tag in tags[group].list) ?tag.string: category,
};

List<String> _splitTags(String? raw) => (raw ?? '')
    .split(RegExp(r'\s+'))
    .where((t) => t.isNotEmpty)
    .toList(growable: false);

String? _nonEmpty(String? value) =>
    value == null || value.trim().isEmpty ? null : value.trim();

int? _positive(Json value) {
  final number = value.integer;
  return number == null || number <= 0 ? null : number;
}

/// Hosts send no parent as null, 0 or an empty string.
String? _parentId(Json value) {
  final id = _positive(value);
  return id == null ? null : '$id';
}

/// Gelbooru sends booleans as the strings "true" and "false".
bool _flag(Json value) =>
    value.boolean ?? (value.string?.toLowerCase() == 'true');

BooruPost? _parseE621(Json json, {required String host}) {
  final id = _idOf(json);
  if (id == null) return null;

  final file = json['file'];
  final preview = json['preview'];
  final sample = json['sample'];
  final tagsJson = json['tags'];
  final tags = <String>[
    for (final key in [
      'artist',
      'character',
      'copyright',
      'species',
      'general',
      'meta',
      'lore',
    ])
      for (final tag in tagsJson[key].list) ?tag.string,
  ];

  final score = json['score']['total'].integer ?? json['score'].integer;
  final relationships = json['relationships'];

  return BooruPost(
    id: id,
    host: host,
    engine: BooruEngine.e621.id,
    tags: tags,
    rating: BooruRating.parseWire(json['rating'].string, BooruEngine.e621),
    score: score,
    width: file['width'].integer ?? 0,
    height: file['height'].integer ?? 0,
    previewUrl: _absolute(host, preview['url'].string),
    sampleUrl: _absolute(host, sample['url'].string),
    fileUrl: _absolute(host, file['url'].string),
    fileExt: file['ext'].string,
    source: json['sources'][0].string ?? json['source'].string,
    createdAt: _createdAt(json),
    tagCategories: _e621Categories(tagsJson),
    fileSize: _positive(file['size']),
    md5: _nonEmpty(file['md5'].string),
    favCount: json['fav_count'].integer,
    upScore: json['score']['up'].integer,
    downScore: json['score']['down'].integer?.abs(),
    parentId: _parentId(relationships['parent_id']),
    hasChildren:
        _flag(relationships['has_children']) ||
        relationships['children'].list.isNotEmpty,
    uploader: _nonEmpty(json['uploader_name'].string),
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
  final parsed = asString == null
      ? null
      : DateTime.tryParse(asString) ?? parseGelbooruDate(asString);
  if (parsed != null) return parsed;
  final asInt = json['created_at'].integer;
  if (asInt != null && asInt > 0) {
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

const _months = {
  'jan': 1,
  'feb': 2,
  'mar': 3,
  'apr': 4,
  'may': 5,
  'jun': 6,
  'jul': 7,
  'aug': 8,
  'sep': 9,
  'oct': 10,
  'nov': 11,
  'dec': 12,
};

/// Gelbooru writes dates like Ruby's `to_s`: `Sat Oct 05 13:52:20 -0500 2024`.
DateTime? parseGelbooruDate(String raw) {
  final match = RegExp(
    r'^\w{3} (\w{3}) (\d{1,2}) (\d{2}):(\d{2}):(\d{2}) ([+-])(\d{2})(\d{2}) (\d{4})$',
  ).firstMatch(raw.trim());
  final month = _months[match?.group(1)?.toLowerCase()];
  if (match == null || month == null) return null;
  int part(int group) => int.parse(match.group(group)!);
  final offset = Duration(hours: part(7), minutes: part(8));
  final local = DateTime.utc(
    part(9),
    month,
    part(2),
    part(3),
    part(4),
    part(5),
  );
  return match.group(6) == '-' ? local.add(offset) : local.subtract(offset);
}

/// Rule34 / Xbooru omit `file_url` for guests and only send directory + image.
String? _composedFileUrl(Json json) {
  final directory = json['directory'].string;
  final image = json['image'].string;
  if (directory == null ||
      directory.isEmpty ||
      image == null ||
      image.isEmpty) {
    return null;
  }
  return '/images/$directory/$image';
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

/// Whether any blacklist entry hides [post]. An entry is one tag or several;
/// several hide a post only when all of them match. `-tag` matches a post
/// without the tag, `~a ~b` matches a post with either, and `rating:x` uses
/// the host's own rating letters.
bool booruPostMuted(BooruPost post, Set<String> entries) {
  if (entries.isEmpty) return false;
  final tags = post.tags.map((tag) => tag.toLowerCase()).toSet();
  return entries.any((entry) => _entryMatches(post, tags, entry));
}

bool _entryMatches(BooruPost post, Set<String> tags, String entry) {
  final tokens = booruQueryTokens(entry).map(BooruQueryToken.parse).toList();
  if (tokens.isEmpty) return false;
  final either = tokens.where((t) => t.operator == BooruTagOperator.either);
  final rest = tokens.where((t) => t.operator != BooruTagOperator.either);
  return rest.every((t) => _tokenMatches(post, tags, t)) &&
      (either.isEmpty || either.any((t) => _tokenMatches(post, tags, t)));
}

bool _tokenMatches(BooruPost post, Set<String> tags, BooruQueryToken token) {
  final present = switch (token.metatag) {
    null => tags.contains(token.value.toLowerCase()),
    'rating' => _ratingMatches(post, token.value),
    // Other metatags need the host; an entry using one never hides a post.
    _ => null,
  };
  if (present == null) return false;
  return token.operator == BooruTagOperator.exclude ? !present : present;
}

bool _ratingMatches(BooruPost post, String value) {
  final engine = BooruEngine.tryParse(post.engine) ?? BooruEngine.danbooru;
  final rating = BooruRating.parseWire(value, engine);
  return rating != null && rating == post.rating;
}

/// Stored blacklist entries, normalised and de-duplicated.
Set<String> parseBooruBlacklist(String? raw) {
  try {
    final decoded = jsonDecode(raw ?? '[]');
    if (decoded is! List) return const {};
    return {
      for (final entry in decoded.whereType<String>())
        ?normaliseBooruQuery(entry),
    };
  } catch (_) {
    return const {};
  }
}
