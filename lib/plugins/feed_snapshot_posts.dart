import 'package:flutter/widgets.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/plugin_link_post.dart';
import 'package:xta/plugins/threads/threads_archive.dart';
import 'package:xta/plugins/threads/threads_post_card.dart';

/// A post a mixed feed kept for reading offline, as the card it was shown as.
typedef FeedSnapshotPost = ({String source, Map<String, dynamic> content, WidgetBuilder build});

/// [snapshot] as the post it holds: a Threads post as a Threads card, any
/// other as a link post. Null when it is neither.
FeedSnapshotPost? feedSnapshotPost(Object? snapshot) {
  final threads = threadsPostFromArchive(snapshot);
  if (threads != null) {
    return (
      source: pluginIdThreads,
      content: threadsArchiveBlob(threads),
      build: (_) => ThreadsPostCard(post: threads, showSourceBadge: true),
    );
  }
  final link = PluginLinkPost.fromArchive(snapshot);
  if (link == null) return null;
  return (source: link.source, content: link.archive.content, build: (_) => PluginLinkPostCard(post: link));
}
