import 'package:flutter/material.dart';

/// What flies between a photo's tile and the fullscreen viewer, as X opens
/// one: the picture the tile has already decoded, its crop opening from the
/// tile's [from] fit to the whole picture as it grows into the viewer, and
/// closing again on the way back.
///
/// [animation] is the viewer's: 0 at the tile, 1 in the viewer, whichever way
/// the photo is flying.
class TweetPhotoFlight extends StatelessWidget {
  final Animation<double> animation;
  final ImageProvider image;

  /// The picture's width over its height; without it the photo flies whole.
  final double? aspectRatio;
  final BoxFit from;

  const TweetPhotoFlight({
    super.key,
    required this.animation,
    required this.image,
    required this.aspectRatio,
    required this.from,
  });

  @override
  Widget build(BuildContext context) {
    final ratio = aspectRatio;
    if (ratio == null || ratio <= 0 || from != BoxFit.cover) {
      return Image(image: image, fit: BoxFit.contain, gaplessPlayback: true);
    }
    return LayoutBuilder(
      builder: (context, constraints) => AnimatedBuilder(
        animation: animation,
        builder: (context, _) {
          final size = photoFlightSize(constraints.biggest, ratio, animation.value);
          return ClipRect(
            child: OverflowBox(
              minWidth: size.width,
              maxWidth: size.width,
              minHeight: size.height,
              maxHeight: size.height,
              child: Image(image: image, fit: BoxFit.fill, gaplessPlayback: true),
            ),
          );
        },
      ),
    );
  }
}

/// The picture's size inside [box] at [t] of the flight: covering the box at
/// 0, as a tile crops it, and contained in it at 1, as the viewer shows it.
Size photoFlightSize(Size box, double ratio, double t) {
  final byWidth = Size(box.width, box.width / ratio);
  final byHeight = Size(box.height * ratio, box.height);
  final wider = byWidth.height <= box.height;
  final cover = wider ? byHeight : byWidth;
  final contain = wider ? byWidth : byHeight;
  return Size.lerp(cover, contain, t.clamp(0.0, 1.0))!;
}
