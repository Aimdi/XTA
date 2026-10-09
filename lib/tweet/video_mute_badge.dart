import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:media_kit/media_kit.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/tweet/_video.dart';
import 'package:xta/tweet/tweet_chrome.dart';

const double kVideoMuteDiscSize = 30;
const double kVideoMuteCornerInset = 12;

/// How far from the end edge the inline controls stop, so the seek bar never
/// runs under the mute badge's touch target.
const double kVideoMuteCornerReserve = kVideoMuteCornerInset + kVideoMuteDiscSize + kVideoMuteCornerInset + 2;

const double _kTargetEdge = kVideoMuteCornerInset - (kTweetTouchTarget - kVideoMuteDiscSize) / 2;

/// The always-visible sound toggle in the bottom-end corner of an inline video.
///
/// A direct child of the video's [Stack].
class InlineVideoMuteCorner extends StatelessWidget {
  const InlineVideoMuteCorner({super.key, this.player});

  /// Null while the player is still being created.
  final Player? player;

  @override
  Widget build(BuildContext context) {
    return PositionedDirectional(
      end: _kTargetEdge,
      bottom: _kTargetEdge,
      child: _InlineVideoMuteButton(player: player),
    );
  }
}

/// Before the player exists the badge shows, and sets, the app-wide mute the
/// player will start with; once attached it follows that player's volume,
/// whose listener carries the change back to the app-wide state.
class _InlineVideoMuteButton extends StatelessWidget {
  const _InlineVideoMuteButton({this.player});

  final Player? player;

  @override
  Widget build(BuildContext context) {
    final player = this.player;
    if (player == null) {
      final model = context.watch<VideoContextState?>();
      if (model == null) return const SizedBox.shrink();
      final muted = model.isMuted;
      return VideoMuteBadge(muted: muted, onToggle: () => model.setIsMuted(muted ? 100.0 : 0.0));
    }
    return StreamBuilder<double>(
      stream: player.stream.volume,
      initialData: player.state.volume,
      builder: (context, snapshot) {
        final muted = (snapshot.data ?? 0) == 0;
        return VideoMuteBadge(muted: muted, onToggle: () => player.setVolume(muted ? 100.0 : 0.0));
      },
    );
  }
}

/// A small frosted-glass disc with a speaker glyph, inside a full touch target.
///
/// The blur is clipped to the disc, so only a few hundred pixels of the video
/// behind it are re-sampled each frame.
class VideoMuteBadge extends StatelessWidget {
  const VideoMuteBadge({super.key, required this.muted, required this.onToggle});

  final bool muted;
  final VoidCallback onToggle;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      button: true,
      toggled: muted,
      label: L10n.of(context).mute_videos,
      onTap: onToggle,
      child: ExcludeSemantics(
        child: GestureDetector(
          behavior: HitTestBehavior.opaque,
          onTap: onToggle,
          child: SizedBox.square(
            dimension: kTweetTouchTarget,
            child: Center(child: _GlassDisc(muted: muted)),
          ),
        ),
      ),
    );
  }
}

class _GlassDisc extends StatelessWidget {
  const _GlassDisc({required this.muted});

  final bool muted;

  @override
  Widget build(BuildContext context) {
    return SizedBox.square(
      dimension: kVideoMuteDiscSize,
      child: ClipOval(
        child: BackdropFilter(
          filter: ui.ImageFilter.blur(sigmaX: 10, sigmaY: 10),
          child: DecoratedBox(
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: Colors.black.withValues(alpha: 0.4),
              border: Border.all(color: Colors.white.withValues(alpha: 0.24), width: 0.75),
            ),
            child: DecoratedBox(
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                gradient: LinearGradient(
                  begin: Alignment.topCenter,
                  end: Alignment.center,
                  colors: [Colors.white.withValues(alpha: 0.16), Colors.white.withValues(alpha: 0)],
                ),
              ),
              child: Icon(muted ? Icons.volume_off : Icons.volume_up, size: 16, color: Colors.white),
            ),
          ),
        ),
      ),
    );
  }
}
