import 'package:flutter/material.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/booru/booru_display.dart';
import 'package:xta/plugins/booru/booru_engines.dart';
import 'package:xta/plugins/booru/booru_grid.dart';
import 'package:xta/plugins/booru/booru_image.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_post_actions.dart';
import 'package:xta/plugins/plugin_post_media.dart';
import 'package:xta/tweet/_video.dart';

/// The post's picture, or its video in the app's player. A picture opens
/// full screen, where it zooms.
class BooruPostMedia extends StatelessWidget {
  final BooruPost post;

  const BooruPostMedia({super.key, required this.post});

  @override
  Widget build(BuildContext context) {
    final video = post.videoUrl;
    final ratio = post.aspectRatio.clamp(0.4, 2.2);
    if (video != null) {
      return AspectRatio(aspectRatio: ratio, child: _video(video));
    }
    return Hero(
      tag: booruPostHeroTag(post),
      child: AspectRatio(
        aspectRatio: ratio,
        child: Semantics(
          image: true,
          button: true,
          label: L10n.of(context).plugin_booru_full_screen,
          child: GestureDetector(
            onTap: () => _openFullScreen(context),
            child: BooruNetworkImage(url: post.isVideo ? post.thumbnailUrl : post.displayUrl, fit: BoxFit.contain),
          ),
        ),
      ),
    );
  }

  Widget _video(String url) => TweetVideo(
    username: displayNameForBooruHost(post.host),
    loop: true,
    tweetId: 'booru-${post.host}-${post.id}',
    metadata: TweetVideoMetadata(post.aspectRatio, post.thumbnailUrl, () async => TweetVideoUrls(url, url)),
  );

  void _openFullScreen(BuildContext context) {
    if (post.isVideo) {
      openBooruLink(post.originalUrl);
      return;
    }
    openPluginImageViewer(
      context,
      items: [
        PluginMediaItem(
          url: BooruDisplay.of(context).viewerUrl(post),
          aspectRatio: post.aspectRatio,
          downloadUrl: post.originalUrl,
          shareUrl: booruShareUrl(post),
        ),
      ],
      sourceName: pluginIdBooru,
    );
  }
}
