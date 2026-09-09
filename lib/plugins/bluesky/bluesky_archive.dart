import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/utils/json.dart';

String blueskyArchiveId(BlueskyPost post) => 'bluesky:${post.uri}';
Map<String, dynamic> blueskyArchiveBlob(BlueskyPost post) => {'xtaPlugin': 'bluesky', 'post': post.toJson()};

BlueskyPost? blueskyPostFromArchive(Object? value) {
  final json = Json(value);
  if (json['xtaPlugin'].string != 'bluesky' || json['post']['uri'].string?.startsWith('at://') != true) return null;
  try {
    return BlueskyPost.fromSnapshot(json['post'].raw);
  } catch (_) {
    return null;
  }
}

String blueskyArchiveHaystack(BlueskyPost post) => [
  post.text,
  post.authorName,
  post.handle,
  post.quotedPost?.text ?? '',
  post.linkCard?.title ?? '',
].join('\n').toLowerCase();
