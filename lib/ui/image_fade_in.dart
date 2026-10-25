import 'package:extended_image/extended_image.dart';
import 'package:flutter/widgets.dart';
import 'package:xta/ui/motion.dart';

/// The load states of a network image in a post or a card, shown as feed
/// photos show theirs: it waits on [placeholder] rather than a spinner, and
/// fades in over it once fetched. One already in memory is drawn at once. A
/// failure shows [failed], or the image's own failure without one.
///
/// For [ExtendedImage.loadStateChanged].
Widget? fadeInImageLoadState(ExtendedImageState state, {required Widget placeholder, Widget? failed}) =>
    switch (state.extendedImageLoadState) {
      LoadState.loading => placeholder,
      LoadState.completed when !state.wasSynchronouslyLoaded => ImageFadeIn(
        placeholder: placeholder,
        image: state.completedWidget,
      ),
      LoadState.completed => null,
      LoadState.failed => failed,
    };

/// [image] fading in over [placeholder], which goes once it has: a monogram
/// gives way to the photo without a gap between them, and an image with
/// transparent parts ends with nothing behind it.
class ImageFadeIn extends StatelessWidget {
  final Widget placeholder;
  final Widget image;

  const ImageFadeIn({super.key, required this.placeholder, required this.image});

  @override
  Widget build(BuildContext context) {
    return TweenAnimationBuilder<double>(
      tween: Tween(begin: 0, end: 1),
      duration: xtaMotionDuration(context, kXtaMotionStandard),
      curve: Curves.easeOutCubic,
      builder: (context, t, image) => Stack(
        fit: StackFit.passthrough,
        children: [
          if (t < 1) placeholder,
          Opacity(opacity: t, child: image),
        ],
      ),
      child: image,
    );
  }
}
