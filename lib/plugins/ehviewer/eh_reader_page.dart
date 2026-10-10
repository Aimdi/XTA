import 'dart:async';

import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/ehviewer/eh_errors.dart';
import 'package:xta/plugins/ehviewer/eh_grid.dart' show ehImageTimeLimit;
import 'package:xta/plugins/ehviewer/eh_models.dart';
import 'package:xta/plugins/ehviewer/eh_page_resolver.dart';
import 'package:xta/ui/double_tap_zoom.dart';

/// Portrait paper: the height a vertical page holds until its image arrives.
const ehPagePlaceholderAspect = 1 / 1.4142;

/// How the reader loads page images. The preload builds its providers here too,
/// so a preloaded page and the page on screen share one cache entry.
class EhReaderImages {
  final Map<String, String> headers;
  final bool signedIn;

  /// Decode width for the vertical list; null keeps full resolution for zooming.
  final int? cacheWidth;

  const EhReaderImages({required this.headers, required this.signedIn, this.cacheWidth});

  /// What to show, then the resample to fall back on when an original fails.
  List<String> urlsOf(EhImagePage image) =>
      {image.displayUrl(signedIn: signedIn), image.imageUrl}.where((url) => url.isNotEmpty).toList();

  ImageProvider provider(String url) => ExtendedResizeImage.resizeIfNeeded(
    provider: ExtendedNetworkImageProvider(url, headers: headers, cache: true, timeLimit: ehImageTimeLimit, retries: 1),
    cacheWidth: cacheWidth,
  );
}

/// One reader page: its number while it loads, its error with ways out, then the image.
class EhReaderPage extends StatefulWidget {
  final EhPageResolver resolver;
  final EhReaderImages images;
  final int page;
  final bool vertical;

  const EhReaderPage({
    super.key,
    required this.resolver,
    required this.images,
    required this.page,
    required this.vertical,
  });

  @override
  State<EhReaderPage> createState() => _EhReaderPageState();
}

class _EhReaderPageState extends State<EhReaderPage> {
  @override
  void initState() {
    super.initState();
    unawaited(widget.resolver.resolve(widget.page));
  }

  @override
  void didUpdateWidget(EhReaderPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.page != widget.page || oldWidget.resolver != widget.resolver) {
      unawaited(widget.resolver.resolve(widget.page));
    }
  }

  @override
  Widget build(BuildContext context) {
    final page = widget.page;
    final total = widget.resolver.total;
    return ScopedBuilder<EhPageResolver, Map<int, EhPageSlot>>(
      store: widget.resolver,
      distinct: (slots) => slots[page],
      onState: (context, slots) => switch (slots[page]) {
        EhPageReady(:final image) => _EhPageImage(
          key: ValueKey(image),
          urls: widget.images.urlsOf(image),
          images: widget.images,
          page: page,
          total: total,
          vertical: widget.vertical,
          onOtherServer: () => widget.resolver.reload(page),
        ),
        EhPageFailed(:final error) => EhPageStatus(
          page: page,
          total: total,
          vertical: widget.vertical,
          error: ehErrorMessage(L10n.of(context), error),
          onRetry: () => widget.resolver.retry(page),
        ),
        _ => EhPageStatus(page: page, total: total, vertical: widget.vertical),
      },
    );
  }
}

GestureConfig _pageGestures(ExtendedImageState _) =>
    GestureConfig(inPageView: true, minScale: 1, animationMinScale: 0.8, maxScale: 5, initialScale: 1);

double? _progressOf(ExtendedImageState state) {
  final progress = state.loadingProgress;
  final expected = progress?.expectedTotalBytes;
  if (progress == null || expected == null || expected <= 0) return null;
  return progress.cumulativeBytesLoaded / expected;
}

/// Tries [urls] in turn: a failed original gives way to the resample before the error shows.
class _EhPageImage extends StatefulWidget {
  final List<String> urls;
  final EhReaderImages images;
  final int page;
  final int total;
  final bool vertical;
  final VoidCallback onOtherServer;

  const _EhPageImage({
    super.key,
    required this.urls,
    required this.images,
    required this.page,
    required this.total,
    required this.vertical,
    required this.onOtherServer,
  });

  @override
  State<_EhPageImage> createState() => _EhPageImageState();
}

class _EhPageImageState extends State<_EhPageImage> with SingleTickerProviderStateMixin, DoubleTapZoom {
  @override
  List<double> get doubleTapScales => const [1.0, 2.5];

  @override
  Widget build(BuildContext context) {
    return ExtendedImage(
      image: widget.images.provider(widget.urls.first),
      fit: widget.vertical ? BoxFit.fitWidth : BoxFit.contain,
      filterQuality: FilterQuality.high,
      handleLoadingProgress: true,
      mode: widget.vertical ? ExtendedImageMode.none : ExtendedImageMode.gesture,
      initGestureConfigHandler: _pageGestures,
      onDoubleTap: zoomOnDoubleTap,
      loadStateChanged: (state) => switch (state.extendedImageLoadState) {
        LoadState.completed => null,
        LoadState.loading => EhPageStatus(
          page: widget.page,
          total: widget.total,
          vertical: widget.vertical,
          progress: _progressOf(state),
        ),
        LoadState.failed => _fallbackOr(state),
      },
    );
  }

  Widget _fallbackOr(ExtendedImageState state) {
    if (widget.urls.length < 2) return _failed(retry: state.reLoadImage);
    return _EhPageImage(
      urls: widget.urls.sublist(1),
      images: widget.images,
      page: widget.page,
      total: widget.total,
      vertical: widget.vertical,
      onOtherServer: widget.onOtherServer,
    );
  }

  Widget _failed({required VoidCallback retry}) => EhPageStatus(
    page: widget.page,
    total: widget.total,
    vertical: widget.vertical,
    error: L10n.of(context).plugin_eh_reader_image_failed,
    onRetry: retry,
    onOtherServer: widget.onOtherServer,
  );
}

/// A page that is not on screen yet: loading, or failed with [error] and ways to try again.
class EhPageStatus extends StatelessWidget {
  final int page;
  final int total;
  final bool vertical;
  final double? progress;
  final String? error;
  final VoidCallback? onRetry;
  final VoidCallback? onOtherServer;

  const EhPageStatus({
    super.key,
    required this.page,
    required this.total,
    required this.vertical,
    this.progress,
    this.error,
    this.onRetry,
    this.onOtherServer,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final failure = error;
    final body = Center(
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (failure == null)
              CircularProgressIndicator(value: progress, strokeWidth: 2, color: Colors.white70)
            else
              const Icon(Icons.broken_image_outlined, color: Colors.white70, size: 32),
            const SizedBox(height: 12),
            Text(l10n.plugin_eh_page_of(page, total), style: const TextStyle(color: Colors.white70)),
            if (failure != null) ..._failure(l10n, failure),
          ],
        ),
      ),
    );
    return vertical ? AspectRatio(aspectRatio: ehPagePlaceholderAspect, child: body) : body;
  }

  List<Widget> _failure(L10n l10n, String message) => [
    const SizedBox(height: 4),
    Text(
      message,
      textAlign: TextAlign.center,
      style: const TextStyle(color: Colors.white),
    ),
    const SizedBox(height: 12),
    Wrap(
      alignment: WrapAlignment.center,
      spacing: 8,
      runSpacing: 8,
      children: [
        if (onRetry != null)
          OutlinedButton.icon(
            style: OutlinedButton.styleFrom(
              foregroundColor: Colors.white,
              side: const BorderSide(color: Colors.white38),
            ),
            onPressed: onRetry,
            icon: const Icon(Icons.refresh),
            label: Text(l10n.retry),
          ),
        if (onOtherServer != null)
          TextButton(
            style: TextButton.styleFrom(foregroundColor: Colors.white),
            onPressed: onOtherServer,
            child: Text(l10n.plugin_eh_reader_try_another_server),
          ),
      ],
    ),
  ];
}
