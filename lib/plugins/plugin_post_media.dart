import 'package:async_button_builder/async_button_builder.dart';
import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:path/path.dart' as path;
import 'package:pref/pref.dart';
import 'package:share_plus/share_plus.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/tweet/_photo.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/motion.dart';
import 'package:xta/utils/downloads.dart';

/// One image (or video thumbnail) on a plugin post.
class PluginMediaItem {
  const PluginMediaItem({
    required this.url,
    this.aspectRatio,
    this.alt,
    this.isVideo = false,
    this.downloadUrl,
    this.shareUrl,
  });

  final String url;
  final double? aspectRatio;
  final String? alt;
  final bool isVideo;

  /// Original file when it differs from the display URL.
  final String? downloadUrl;

  /// Destination shared from the fullscreen viewer. Defaults to [url].
  final String? shareUrl;

  String get resolvedDownloadUrl => downloadUrl ?? url;
  String get resolvedShareUrl => shareUrl ?? url;
}

List<PluginMediaItem> pluginMediaItemsFrom({
  required List<String> urls,
  List<double?> aspects = const [],
  List<bool> videos = const [],
  List<String?> alts = const [],
}) {
  return [
    for (var i = 0; i < urls.length; i++)
      PluginMediaItem(
        url: urls[i],
        aspectRatio: i < aspects.length ? aspects[i] : null,
        isVideo: i < videos.length && videos[i],
        alt: i < alts.length ? alts[i] : null,
      ),
  ];
}

/// Official clients size the box to the image, then fill it.
/// Clamp so a 9:16 phone photo does not eat the feed, and a 4:1 banner
/// does not collapse to a sliver.
double clampPluginMediaAspect(double? ratio) {
  const min = 0.45;
  const max = 2.4;
  if (ratio == null || !ratio.isFinite || ratio <= 0) {
    return 16 / 9;
  }
  if (ratio < min) return min;
  if (ratio > max) return max;
  return ratio;
}

/// Width/height or a precomputed `aspect` field (Mastodon `meta.original`).
double? pluginMediaAspectFrom(Object? raw) {
  if (raw is! Map) {
    return null;
  }
  final aspect = raw['aspect'];
  if (aspect is num && aspect > 0 && aspect.isFinite) {
    return aspect.toDouble();
  }
  final w = raw['width'];
  final h = raw['height'];
  if (w is num && h is num && w > 0 && h > 0) {
    return w.toDouble() / h.toDouble();
  }
  return null;
}

typedef PluginMediaImageBuilder =
    Widget Function(BuildContext context, PluginMediaItem item, BoxFit fit);

typedef VisiblePluginMedia = ({
  List<PluginMediaItem> items,
  int initialIndex,
});

/// Removes malformed entries without making a tap jump to the wrong surviving
/// page when an empty URL appeared before the tapped item.
VisiblePluginMedia visiblePluginMedia(
  List<PluginMediaItem> items,
  int initialIndex,
) {
  if (items.isEmpty) {
    return (items: const <PluginMediaItem>[], initialIndex: 0);
  }
  final requested = initialIndex.clamp(0, items.length - 1);
  final selected = items[requested];
  final visible = items.where((item) => item.url.trim().isNotEmpty).toList(
    growable: false,
  );
  if (visible.isEmpty) {
    return (items: const <PluginMediaItem>[], initialIndex: 0);
  }
  final mapped = visible.indexOf(selected);
  return (
    items: visible,
    initialIndex: mapped < 0 ? 0 : mapped,
  );
}

/// Feed/profile media: real aspect, tap opens the shared full-screen pager.
class PluginPostMedia extends StatelessWidget {
  const PluginPostMedia({
    super.key,
    required this.items,
    this.imageBuilder,
    this.sourceName = 'xta',
    this.onOpenPost,
  });

  final List<PluginMediaItem> items;
  final PluginMediaImageBuilder? imageBuilder;
  final String sourceName;
  final VoidCallback? onOpenPost;

  @override
  Widget build(BuildContext context) {
    if (items.isEmpty) {
      return const SizedBox.shrink();
    }
    if (items.length == 1) {
      return _PluginMediaTile(
        item: items.first,
        index: 0,
        items: items,
        imageBuilder: imageBuilder,
        sourceName: sourceName,
        onOpenPost: onOpenPost,
      );
    }
    return _PluginMediaPager(
      items: items,
      imageBuilder: imageBuilder,
      sourceName: sourceName,
      onOpenPost: onOpenPost,
    );
  }
}

class _PluginMediaPager extends StatefulWidget {
  const _PluginMediaPager({
    required this.items,
    required this.sourceName,
    this.imageBuilder,
    this.onOpenPost,
  });

  final List<PluginMediaItem> items;
  final PluginMediaImageBuilder? imageBuilder;
  final String sourceName;
  final VoidCallback? onOpenPost;

  @override
  State<_PluginMediaPager> createState() => _PluginMediaPagerState();
}

class _PluginMediaPagerState extends State<_PluginMediaPager> {
  var _index = 0;

  @override
  Widget build(BuildContext context) {
    final items = widget.items;
    final ratio = clampPluginMediaAspect(items[_index].aspectRatio);
    final color = Theme.of(context).colorScheme.onSurfaceVariant;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        AspectRatio(
          aspectRatio: ratio,
          child: PageView.builder(
            itemCount: items.length,
            onPageChanged: (i) => setState(() => _index = i),
            itemBuilder: (context, i) => _PluginMediaTile(
              item: items[i],
              index: i,
              items: items,
              imageBuilder: widget.imageBuilder,
              sourceName: widget.sourceName,
              onOpenPost: widget.onOpenPost,
              fill: true,
            ),
          ),
        ),
        const SizedBox(height: 6),
        Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            for (var i = 0; i < items.length; i++)
              Container(
                width: 5,
                height: 5,
                margin: const EdgeInsets.symmetric(horizontal: 2),
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: color.withValues(alpha: i == _index ? 0.85 : 0.28),
                ),
              ),
          ],
        ),
      ],
    );
  }
}

class _PluginMediaTile extends StatelessWidget {
  const _PluginMediaTile({
    required this.item,
    required this.index,
    required this.items,
    required this.sourceName,
    this.imageBuilder,
    this.onOpenPost,
    this.fill = false,
  });

  final PluginMediaItem item;
  final int index;
  final List<PluginMediaItem> items;
  final PluginMediaImageBuilder? imageBuilder;
  final String sourceName;
  final VoidCallback? onOpenPost;
  final bool fill;

  @override
  Widget build(BuildContext context) {
    final radius = tweetMediaRadiusOf(context);
    final image =
        imageBuilder?.call(context, item, BoxFit.cover) ??
        LayoutBuilder(
          builder: (context, constraints) {
            final maxW = constraints.maxWidth;
            final cacheWidth = maxW.isFinite && maxW > 0
                ? (maxW * MediaQuery.devicePixelRatioOf(context)).ceil()
                : null;
            return ExtendedImage.network(
              item.url,
              fit: BoxFit.cover,
              cache: true,
              cacheWidth: cacheWidth,
              timeLimit: const Duration(seconds: 12),
              retries: 1,
              loadStateChanged: (state) {
                if (state.extendedImageLoadState == LoadState.failed) {
                  return ColoredBox(
                    color: Theme.of(
                      context,
                    ).colorScheme.surfaceContainerHighest,
                    child: Icon(
                      Icons.broken_image_outlined,
                      color: Theme.of(context).colorScheme.onSurfaceVariant,
                    ),
                  );
                }
                return null;
              },
            );
          },
        );

    final framed = ClipRRect(
      borderRadius: fill ? BorderRadius.zero : BorderRadius.circular(radius),
      child: Stack(
        fit: StackFit.expand,
        children: [
          image,
          if (item.isVideo)
            const Center(
              child: DecoratedBox(
                decoration: BoxDecoration(
                  color: Color(0x99000000),
                  shape: BoxShape.circle,
                ),
                child: Padding(
                  padding: EdgeInsets.all(10),
                  child: Icon(
                    Icons.play_arrow_rounded,
                    color: Colors.white,
                    size: 28,
                  ),
                ),
              ),
            ),
          if (_usableAlt(item.alt))
            PositionedDirectional(
              start: 8,
              bottom: 8,
              child: _AltBadge(alt: item.alt!),
            ),
        ],
      ),
    );

    final child = fill
        ? framed
        : AspectRatio(
            aspectRatio: clampPluginMediaAspect(item.aspectRatio),
            child: framed,
          );

    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: () => openPluginImageViewer(
          context,
          items: items,
          initialIndex: index,
          imageBuilder: imageBuilder,
          sourceName: sourceName,
          onOpenPost: onOpenPost,
        ),
        onLongPress: () {
          if (_usableAlt(item.alt)) {
            showPluginAltText(context, item.alt!);
          } else if (!item.isVideo) {
            downloadPluginMediaItem(
              context,
              item,
              sourceName: sourceName,
            );
          }
        },
        child: Semantics(
          image: true,
          button: true,
          label: item.alt?.trim().isNotEmpty == true
              ? item.alt!.trim()
              : L10n.of(context).media,
          child: child,
        ),
      ),
    );
  }
}

class _AltBadge extends StatelessWidget {
  const _AltBadge({required this.alt});

  final String alt;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      button: true,
      label: L10n.of(context).alt_text_title,
      child: Material(
        color: Colors.black54,
        borderRadius: BorderRadius.circular(5),
        child: InkWell(
          onTap: () => showPluginAltText(context, alt),
          borderRadius: BorderRadius.circular(5),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 4),
            child: Text(
              L10n.of(context).alt_text_badge,
              style: const TextStyle(
                color: Colors.white,
                fontSize: 11,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
        ),
      ),
    );
  }
}

bool _usableAlt(String? alt) => alt != null && alt.trim().isNotEmpty;

Future<void> showPluginAltText(BuildContext context, String alt) {
  return showDialog<void>(
    context: context,
    builder: (dialogContext) => AlertDialog(
      title: Text(L10n.of(dialogContext).alt_text_title),
      content: SelectableText(alt.trim()),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(dialogContext),
          child: Text(L10n.of(dialogContext).ok),
        ),
      ],
    ),
  );
}

String pluginMediaFileName(PluginMediaItem item, String sourceName) {
  final uri = Uri.tryParse(item.resolvedDownloadUrl);
  final base = uri == null ? '' : path.basename(uri.path);
  final safeSource = sourceName.trim().isEmpty ? 'xta' : sourceName.trim();
  return base.isEmpty ? '$safeSource-media' : '$safeSource-$base';
}

Future<void> downloadPluginMediaItem(
  BuildContext context,
  PluginMediaItem item, {
  String sourceName = 'xta',
}) async {
  if (!context.mounted || item.isVideo) return;
  final uri = Uri.tryParse(item.resolvedDownloadUrl);
  if (uri == null || (uri.scheme != 'https' && uri.scheme != 'http')) return;

  await downloadUriToPickedFile(
    context,
    uri,
    pluginMediaFileName(item, sourceName),
    prefs: PrefService.of(context, listen: false),
    onStart: () {
      showWorkingSnackBar(context, L10n.of(context).downloading_media);
    },
    onSuccess: () {
      ScaffoldMessenger.of(context).hideCurrentSnackBar(
        reason: SnackBarClosedReason.hide,
      );
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(L10n.of(context).successfully_saved_the_media)),
      );
    },
  );
}

Future<void> openPluginImageViewer(
  BuildContext context, {
  required List<PluginMediaItem> items,
  int initialIndex = 0,
  PluginMediaImageBuilder? imageBuilder,
  String sourceName = 'xta',
  VoidCallback? onOpenPost,
}) async {
  final selection = visiblePluginMedia(items, initialIndex);
  if (selection.items.isEmpty) return;

  final reduceMotion = xtaReduceMotion(context);
  await Navigator.of(context).push<void>(
    PageRouteBuilder<void>(
      opaque: true,
      barrierColor: Colors.black,
      transitionDuration: reduceMotion ? Duration.zero : kXtaMotionStandard,
      reverseTransitionDuration: reduceMotion ? Duration.zero : kXtaMotionFast,
      pageBuilder: (context, animation, secondary) => PluginImageViewer(
        items: selection.items,
        initialIndex: selection.initialIndex,
        imageBuilder: imageBuilder,
        sourceName: sourceName,
        onOpenPost: onOpenPost,
      ),
      transitionsBuilder: (context, animation, secondary, child) =>
          FadeTransition(opacity: animation, child: child),
    ),
  );
}

/// Fullscreen image pager shared by plugin feeds.
///
/// Video entries remain thumbnails here; their dedicated players still own
/// playback. The viewer never offers download for those thumbnails.
class PluginImageViewer extends StatefulWidget {
  const PluginImageViewer({
    super.key,
    required this.items,
    this.initialIndex = 0,
    this.imageBuilder,
    this.sourceName = 'xta',
    this.onOpenPost,
  });

  final List<PluginMediaItem> items;
  final int initialIndex;
  final PluginMediaImageBuilder? imageBuilder;
  final String sourceName;
  final VoidCallback? onOpenPost;

  @override
  State<PluginImageViewer> createState() => _PluginImageViewerState();
}

class _PluginImageViewerState extends State<PluginImageViewer> {
  late final ExtendedPageController _pages;
  late int _index;

  PluginMediaItem get _current => widget.items[_index];

  @override
  void initState() {
    super.initState();
    _index = widget.initialIndex.clamp(0, widget.items.length - 1);
    _pages = ExtendedPageController(initialPage: _index);
  }

  @override
  void dispose() {
    _pages.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: SystemUiOverlayStyle.light.copyWith(
        statusBarColor: Colors.transparent,
        systemNavigationBarColor: Colors.black,
        systemNavigationBarContrastEnforced: false,
      ),
      child: Scaffold(
        backgroundColor: Colors.black,
        appBar: AppBar(
          backgroundColor: Colors.black,
          foregroundColor: Colors.white,
          surfaceTintColor: Colors.transparent,
          title: widget.items.length > 1
              ? Text('${_index + 1} / ${widget.items.length}')
              : null,
          actions: [
            if (widget.onOpenPost != null)
              IconButton(
                key: const ValueKey('plugin-media-open-post'),
                tooltip: L10n.of(context).open_post,
                icon: const Icon(Icons.open_in_new),
                onPressed: widget.onOpenPost,
              ),
            if (_usableAlt(_current.alt))
              IconButton(
                key: const ValueKey('plugin-media-alt'),
                tooltip: L10n.of(context).alt_text_title,
                icon: const Icon(Icons.closed_caption_outlined),
                onPressed: () => showPluginAltText(context, _current.alt!),
              ),
            if (!_current.isVideo)
              AsyncButtonBuilder(
                onPressed: () => downloadPluginMediaItem(
                  context,
                  _current,
                  sourceName: widget.sourceName,
                ),
                builder: (context, child, callback, state) => IconButton(
                  key: const ValueKey('plugin-media-download'),
                  tooltip: L10n.of(context).download,
                  onPressed: callback,
                  icon: child,
                ),
                child: const Icon(Icons.download_outlined),
              ),
            IconButton(
              key: const ValueKey('plugin-media-share'),
              tooltip: L10n.of(context).share_link,
              icon: const Icon(Icons.share_outlined),
              onPressed: () => SharePlus.instance.share(
                ShareParams(text: _current.resolvedShareUrl),
              ),
            ),
          ],
        ),
        body: ExtendedImageGesturePageView.builder(
          controller: _pages,
          itemCount: widget.items.length,
          onPageChanged: (i) => setState(() => _index = i),
          itemBuilder: (context, i) {
            final item = widget.items[i];
            final custom = widget.imageBuilder;
            if (custom != null) {
              return InteractiveViewer(
                minScale: 1,
                maxScale: 4,
                child: Center(
                  child: custom(context, item, BoxFit.contain),
                ),
              );
            }
            return TweetPhoto(
              uri: item.url,
              size: null,
              fit: BoxFit.contain,
              pullToClose: true,
              inPageView: true,
            );
          },
        ),
      ),
    );
  }
}
