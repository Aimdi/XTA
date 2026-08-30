import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/tweet/_video.dart';
import 'package:xta/tweet/broadcasts.dart';
import 'package:xta/utils/urls.dart';

/// Fullscreen in-app player for an X broadcast or Space.
///
/// VODs with MP4 variants still go through the media lightbox. This screen is
/// for live rooms and Spaces: it resolves HLS the same way the tweet card
/// already does (`/live_video_stream/status`) and plays it here. A browser
/// is only offered if that fetch fails.
class LivePlayerScreen extends StatelessWidget {
  final LivePlayRequest request;

  const LivePlayerScreen({super.key, required this.request});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        backgroundColor: Colors.black,
        foregroundColor: Colors.white,
        title: Text(request.isSpace ? l10n.spaces : l10n.broadcasts),
        actions: [
          if (request.watchUrl != null)
            IconButton(
              icon: const Icon(Icons.open_in_browser),
              tooltip: l10n.open_in_browser,
              onPressed: () => openLiveUrl(context, request.watchUrl!),
            ),
        ],
      ),
      body: Center(
        child: TweetVideo(
          username: request.isSpace ? 'space' : 'broadcast',
          loop: false,
          alwaysPlay: true,
          tweetId: request.spaceId ?? request.broadcastId,
          metadata: TweetVideoMetadata.live(
            aspectRatio: request.aspectRatio,
            imageUrl: request.imageUrl,
            playbackUrl: () => livePlaybackUrl(request),
          ),
        ),
      ),
    );
  }
}

Future<void> openLivePlayer(BuildContext context, LivePlayRequest request) {
  if (!request.canResolve) {
    final url = request.watchUrl;
    if (url != null) {
      return openLiveUrl(context, url);
    }
    return Future.value();
  }
  return Navigator.of(context).push(
    MaterialPageRoute<void>(
      builder: (_) => LivePlayerScreen(request: request),
    ),
  );
}

Future<void> openLivePlayerFromUrl(BuildContext context, String url) {
  return openLivePlayer(context, LivePlayRequest.fromUrl(url));
}
