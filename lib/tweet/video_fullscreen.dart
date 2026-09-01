import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:media_kit_video/media_kit_video.dart';
import 'package:xta/tweet/_video_controls.dart';
import 'package:xta/tweet/video_controller_pool.dart';
import 'package:xta/tweet/video_pip.dart';

/// Inherited marker so controls know they are already on the fullscreen route
/// and should pop instead of pushing again.
class TweetVideoFullscreenScope extends InheritedWidget {
  const TweetVideoFullscreenScope({super.key, required super.child});

  static bool activeOf(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<TweetVideoFullscreenScope>() != null;

  @override
  bool updateShouldNotify(covariant InheritedWidget oldWidget) => false;
}

/// Picks orientation from the video's shape so a portrait video isn't forced
/// into landscape like media_kit's `defaultEnterNativeFullscreen` does.
Future<void> enterTweetVideoFullscreenUi(double aspectRatio) async {
  if (!Platform.isAndroid && !Platform.isIOS) {
    return defaultEnterNativeFullscreen();
  }
  try {
    final portrait = aspectRatio < 1.0;
    await Future.wait([
      SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky, overlays: []),
      SystemChrome.setPreferredOrientations(
        portrait
            ? [DeviceOrientation.portraitUp, DeviceOrientation.portraitDown]
            : [DeviceOrientation.landscapeLeft, DeviceOrientation.landscapeRight],
      ),
    ]);
  } catch (_) {}
}

/// Self-contained fullscreen route that owns its own [Video] / notifiers.
///
/// media_kit's built-in `enterFullscreen` reuses the inline tile's [VideoState]
/// and `VideoViewParameters` notifier. Off-screen reclaim (and list recycle on
/// orientation change) disposes that parent [Video], which leaves the route
/// holding disposed notifiers — controls vanish and there is no way to exit.
/// Pushing an independent [Video] on the same [VideoController] survives that.
Future<void> pushTweetVideoFullscreen({
  required NavigatorState navigator,
  required PooledVideo pooled,
  required String username,
  required Color accentColor,
  required bool subtitlesEnabled,
  required VoidCallback onToggleSubtitles,
  required bool pauseUponEnteringBackgroundMode,
  double aspectRatio = 16 / 9,
}) {
  return navigator.push<void>(
    PageRouteBuilder<void>(
      pageBuilder: (_, _, _) => TweetVideoFullscreenScope(
        child: _TweetVideoFullscreenPage(
          pooled: pooled,
          username: username,
          accentColor: accentColor,
          subtitlesEnabled: subtitlesEnabled,
          onToggleSubtitles: onToggleSubtitles,
          pauseUponEnteringBackgroundMode: pauseUponEnteringBackgroundMode,
          aspectRatio: aspectRatio,
        ),
      ),
      transitionDuration: Duration.zero,
      reverseTransitionDuration: Duration.zero,
    ),
  );
}

class _TweetVideoFullscreenPage extends StatefulWidget {
  final PooledVideo pooled;
  final String username;
  final Color accentColor;
  final bool subtitlesEnabled;
  final VoidCallback onToggleSubtitles;
  final bool pauseUponEnteringBackgroundMode;
  final double aspectRatio;

  const _TweetVideoFullscreenPage({
    required this.pooled,
    required this.username,
    required this.accentColor,
    required this.subtitlesEnabled,
    required this.onToggleSubtitles,
    required this.pauseUponEnteringBackgroundMode,
    required this.aspectRatio,
  });

  @override
  State<_TweetVideoFullscreenPage> createState() => _TweetVideoFullscreenPageState();
}

class _TweetVideoFullscreenPageState extends State<_TweetVideoFullscreenPage> {
  late bool _subtitlesEnabled = widget.subtitlesEnabled;

  /// Whether the video fills the screen rather than fitting inside it.
  ///
  /// A 16:9 clip on a 20:9 phone leaves a band of black at each end; filling
  /// crops the sides instead. Which one is wanted depends on the video, so it
  /// is a control rather than a setting.
  bool _zoomedToFill = false;

  void _toggleSubtitles() {
    widget.onToggleSubtitles();
    setState(() => _subtitlesEnabled = !_subtitlesEnabled);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: Video(
        controller: widget.pooled.videoController,
        // Own notifiers — do not inherit the inline tile's VideoState.
        fit: _zoomedToFill ? BoxFit.cover : BoxFit.contain,
        controls: (_) => XtaControls(
          pooled: widget.pooled,
          username: widget.username,
          allowMuting: true,
          accentColor: widget.accentColor,
          subtitlesEnabled: _subtitlesEnabled,
          onToggleSubtitles: _toggleSubtitles,
          onToggleFullscreen: () => Navigator.of(context, rootNavigator: true).maybePop(),
          zoomedToFill: _zoomedToFill,
          onToggleZoom: () => setState(() => _zoomedToFill = !_zoomedToFill),
          // Only offered where the platform has it. Picture-in-picture keeps
          // the video playing in a floating window while the rest of the phone
          // is used, which is the one thing fullscreen cannot do.
          onPictureInPicture:
              VideoPictureInPicture.isSupported ? () => VideoPictureInPicture.enter(aspectRatio: widget.aspectRatio) : null,
        ),
        wakelock: true,
        pauseUponEnteringBackgroundMode: widget.pauseUponEnteringBackgroundMode,
        subtitleViewConfiguration: SubtitleViewConfiguration(visible: _subtitlesEnabled),
      ),
    );
  }
}
