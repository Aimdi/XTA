import 'package:flutter/material.dart';
import 'package:xta/plugins/plugin_post_media.dart';
import 'package:xta/plugins/threads/threads_models.dart';
import 'package:xta/tweet/_video.dart';

/// A Threads post's pictures and videos, videos playing in place, several
/// along one row as the Threads app and X lay them out.
///
/// Meta hands guests the video file itself (`video_versions`), so a video is
/// played with the same player X videos use — its autoplay, mute and data
/// preferences included — instead of a poster that went nowhere. Pictures
/// go through the shared tile, which decodes them at the size they are
/// shown and opens X's viewer.
class ThreadsPostMedia extends StatelessWidget {
  final ThreadsPost post;
  final VoidCallback? onOpenPost;

  const ThreadsPostMedia({super.key, required this.post, this.onOpenPost});

  @override
  Widget build(BuildContext context) {
    return PluginPostMedia(
      items: post.mediaItems,
      videoBuilder: (context, item, index) => threadsVideoPlayer(post, item, index),
      sourceName: 'threads',
      onOpenPost: onOpenPost,
      strip: true,
    );
  }
}

/// The player for one video of [post], or null when Meta sent no file for it.
Widget? threadsVideoPlayer(ThreadsPost post, PluginMediaItem item, int index) {
  final stream = item.videoUrl?.trim();
  if (stream == null || stream.isEmpty) {
    return null;
  }
  return TweetVideo(
    key: ValueKey('threads-video-${post.id}-$index'),
    username: post.handle,
    loop: false,
    tweetId: 'threads-${post.id}',
    mediaIndex: index,
    metadata: TweetVideoMetadata(
      clampPluginMediaAspect(item.aspectRatio),
      item.url,
      () async => TweetVideoUrls(stream, stream),
    ),
  );
}
