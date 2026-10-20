import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/utils/json.dart';

/// [post] as JSON, for the copy of a feed page kept on the device.
/// [booruPostFromJson] reads it back.
Map<String, Object?> booruPostToJson(BooruPost post) => {
  'id': post.id,
  'host': post.host,
  'engine': post.engine,
  'tags': post.tags,
  'rating': post.rating?.code,
  'score': post.score,
  'width': post.width,
  'height': post.height,
  'preview': post.previewUrl,
  'sample': post.sampleUrl,
  'file': post.fileUrl,
  'ext': post.fileExt,
  'source': post.source,
  'created': post.createdAt?.toIso8601String(),
  'updated': post.updatedAt?.toIso8601String(),
  'kinds': {for (final MapEntry(key: tag, value: kind) in post.tagCategories.entries) tag: kind.name},
  'size': post.fileSize,
  'md5': post.md5,
  'favs': post.favCount,
  'up': post.upScore,
  'down': post.downScore,
  'parent': post.parentId,
  'children': post.hasChildren,
  'uploader': post.uploader,
  'variants': [
    for (final variant in post.variants) {'url': variant.url, 'width': variant.width},
  ],
};

/// The post [booruPostToJson] wrote, or null when [json] is not one, such as
/// a damaged file.
BooruPost? booruPostFromJson(Json json) {
  final id = json['id'].string;
  final host = json['host'].string;
  final engine = json['engine'].string;
  if (id == null || host == null || engine == null) return null;
  return BooruPost(
    id: id,
    host: host,
    engine: engine,
    tags: [for (final tag in json['tags'].list) ?tag.string],
    rating: BooruRating.tryParse(json['rating'].string),
    score: json['score'].integer,
    width: json['width'].integer ?? 0,
    height: json['height'].integer ?? 0,
    previewUrl: json['preview'].string,
    sampleUrl: json['sample'].string,
    fileUrl: json['file'].string,
    fileExt: json['ext'].string,
    source: json['source'].string,
    createdAt: DateTime.tryParse(json['created'].string ?? ''),
    updatedAt: DateTime.tryParse(json['updated'].string ?? ''),
    tagCategories: _kinds(json['kinds']),
    fileSize: json['size'].integer,
    md5: json['md5'].string,
    favCount: json['favs'].integer,
    upScore: json['up'].integer,
    downScore: json['down'].integer,
    parentId: json['parent'].string,
    hasChildren: json['children'].boolean ?? false,
    uploader: json['uploader'].string,
    variants: [for (final variant in json['variants'].list) ?_variant(variant)],
  );
}

Map<String, BooruTagCategory> _kinds(Json json) => switch (json.raw) {
  final Map<Object?, Object?> kinds => {
    for (final MapEntry(key: tag, value: name) in kinds.entries)
      if ((tag, BooruTagCategory.values.asNameMap()[name]) case (final String tag, final BooruTagCategory kind))
        tag: kind,
  },
  _ => const {},
};

BooruImageVariant? _variant(Json json) => switch ((json['url'].string, json['width'].integer)) {
  (final String url, final int width) => BooruImageVariant(url: url, width: width),
  _ => null,
};
