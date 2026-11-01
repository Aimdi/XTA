import 'package:xta/plugins/plugin_post_actions.dart';
import 'package:xta/plugins/threads/threads_models.dart';
import 'package:xta/utils/json.dart';

/// The id a saved Threads post is kept under: the one the post sheet saved it as when it kept only a link, so a
/// post saved then still shows as saved.
String threadsArchiveId(ThreadsPost post) => 'threads:${post.url ?? post.id}';

Map<String, dynamic> threadsArchiveBlob(ThreadsPost post) => {'xtaPlugin': 'threads', 'post': post.toJson()};

PluginPostArchive threadsArchive(ThreadsPost post) =>
    PluginPostArchive(id: threadsArchiveId(post), userId: post.handle, content: threadsArchiveBlob(post));

ThreadsPost? threadsPostFromArchive(Object? value) {
  final json = Json(value);
  if (json['xtaPlugin'].string != 'threads' || (json['post']['id'].string ?? '').isEmpty) return null;
  try {
    return ThreadsPost.fromSnapshot(json['post'].raw);
  } catch (_) {
    return null;
  }
}

String threadsArchiveHaystack(ThreadsPost post) => [
  post.text,
  post.authorName,
  post.handle,
  post.topicTag ?? '',
  post.quoted?.text ?? '',
  post.linkCard?.title ?? '',
].join('\n').toLowerCase();
