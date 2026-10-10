import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/double_tap_zoom.dart';

class TweetPhoto extends StatefulWidget {
  final String uri;
  final BoxFit fit;
  final String? size;
  final bool pullToClose;
  final bool inPageView;

  const TweetPhoto({
    super.key,
    required this.uri,
    this.fit = BoxFit.fitWidth,
    required this.size,
    required this.pullToClose,
    required this.inPageView,
  });

  @override
  State<TweetPhoto> createState() => _TweetPhotoState();
}

class _TweetPhotoState extends State<TweetPhoto>
    with SingleTickerProviderStateMixin, DoubleTapZoom {
  @override
  Widget build(BuildContext context) {
    final url = widget.size != null ? '${widget.uri}:${widget.size}' : widget.uri;

    // Fullscreen viewer: full resolution, so pinch-zoom stays sharp.
    if (widget.inPageView) {
      return _gestureImage(context, url, cacheWidth: null);
    }

    // Timeline tile: decode at layout width × DPR, and none of the gesture
    // stack. The tap that opens the viewer is handled a level up, in _media,
    // so a scale/pan recogniser, a slide-out page and a double-tap recogniser
    // here buy a feed tile nothing -- and the double-tap recogniser delays
    // every single tap while it waits to see if a second one follows.
    return LayoutBuilder(builder: (context, constraints) {
      final maxW = constraints.maxWidth;
      final cacheWidth = maxW.isFinite && maxW > 0
          ? (maxW * MediaQuery.devicePixelRatioOf(context)).ceil()
          : null;

      return ExtendedImage.network(
        url,
        cache: true,
        fit: widget.fit,
        cacheWidth: cacheWidth,
        loadStateChanged: (state) => _loadState(context, state),
      );
    });
  }

  Widget? _loadState(BuildContext context, ExtendedImageState state) {
    return switch (state.extendedImageLoadState) {
      LoadState.loading => const ColoredBox(
        color: Colors.black,
        child: Center(
          child: SizedBox.square(
            dimension: kTweetTouchTarget,
            child: Padding(
              padding: EdgeInsets.all(kTweetSpace3),
              child: CircularProgressIndicator(
                color: Colors.white,
                strokeWidth: 2,
              ),
            ),
          ),
        ),
      ),
      LoadState.failed => Semantics(
        image: true,
        label: L10n.of(context).media,
        child: const ColoredBox(
          color: Colors.black,
          child: Center(
            child: Icon(
              Icons.broken_image_outlined,
              color: Colors.white70,
              size: 32,
            ),
          ),
        ),
      ),
      LoadState.completed => null,
    };
  }

  Widget _gestureImage(
    BuildContext context,
    String url, {
    required int? cacheWidth,
  }) {
    return ExtendedImageSlidePage(
      slideAxis: SlideAxis.vertical,
      child: ExtendedImage.network(
        url,
        cache: true,
        fit: widget.fit,
        cacheWidth: cacheWidth,
        mode: ExtendedImageMode.gesture,
        loadStateChanged: (state) => _loadState(context, state),
        enableSlideOutPage: widget.pullToClose,
        initGestureConfigHandler: (state) {
          return GestureConfig(
            inPageView: widget.inPageView,
            minScale: 0.9,
            animationMinScale: 0.7,
            maxScale: 4.0,
            animationMaxScale: 4.0,
            speed: 1.0,
            inertialSpeed: 100.0,
            initialScale: 1.0,
            initialAlignment: InitialAlignment.center,
          );
        },
        onDoubleTap: zoomOnDoubleTap,
      ),
    );
  }
}
