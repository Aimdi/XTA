import 'package:flutter/material.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/plugin_marks.dart';
import 'package:xta/plugins/plugin_registry.dart';

/// The Mastodon & Bluesky section's mark: Bluesky's butterfly at the top left with Mastodon's mark over it at the
/// bottom right.
///
/// A gap is cut out of the butterfly around Mastodon's mark rather than painting a ring in a background colour, so the
/// two stay apart on every surface this is drawn on.
///
/// Together the two glyphs span the same live area a single [PluginBrandMark] of [size] does.
class AltMicrobloggingMark extends StatelessWidget {
  final double size;

  const AltMicrobloggingMark({super.key, this.size = 24});

  static const _glyphShare = 0.66;
  static const _gapShare = 0.07;

  @override
  Widget build(BuildContext context) {
    final glyph = size * _glyphShare;
    // Each mark pads its own glyph; this lines the glyphs' outer edges up with the live area.
    final inset = (size - glyph) * (1 - pluginMarkLiveShare) / 2;
    final mastodonCenter = size - 2 * inset - glyph / 2;
    final bluesky = pluginById(pluginIdBluesky);
    final mastodon = pluginById(pluginIdMastodon);
    return SizedBox.square(
      dimension: size,
      child: Stack(
        children: [
          if (bluesky != null)
            Positioned(
              left: inset,
              top: inset,
              child: ClipPath(
                clipper: _CutOut(
                  center: Offset(mastodonCenter, mastodonCenter),
                  radius: glyph * pluginMarkLiveShare / 2 + size * _gapShare,
                ),
                child: pluginMark(bluesky, size: glyph),
              ),
            ),
          if (mastodon != null)
            Positioned(
              right: inset,
              bottom: inset,
              child: pluginMark(mastodon, size: glyph),
            ),
        ],
      ),
    );
  }
}

/// Everything except a circle, in the clipped child's own coordinates (its top left is the mark's top left).
class _CutOut extends CustomClipper<Path> {
  final Offset center;
  final double radius;

  const _CutOut({required this.center, required this.radius});

  @override
  Path getClip(Size size) => Path.combine(
    PathOperation.difference,
    Path()..addRect(Offset.zero & size),
    Path()..addOval(Rect.fromCircle(center: center, radius: radius)),
  );

  @override
  bool shouldReclip(_CutOut oldClipper) => oldClipper.center != center || oldClipper.radius != radius;
}
