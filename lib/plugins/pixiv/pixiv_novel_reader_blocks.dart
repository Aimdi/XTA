import 'package:extended_image/extended_image.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_confirm.dart';
import 'package:xta/plugins/pixiv/pixiv_detail_caption.dart';
import 'package:xta/plugins/pixiv/pixiv_download.dart';
import 'package:xta/plugins/pixiv/pixiv_image.dart';
import 'package:xta/plugins/pixiv/pixiv_links.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_content.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_open.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_parser.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_reader_store.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_series_screen.dart';
import 'package:xta/plugins/plugin_post_media.dart';
import 'package:xta/plugins/plugin_links.dart';

/// What every block of one reading shares: the text style, the pictures the
/// text names, and where its pages start.
class PixivNovelBlockScope {
  final TextStyle style;
  final PixivNovelContent content;
  final Map<int, int> pageStarts;
  final PixivEmbeddedWorksStore works;

  /// Scrolls to the start of a page, for `[jump:N]`.
  final ValueChanged<int> onJump;

  const PixivNovelBlockScope({
    required this.style,
    required this.content,
    required this.pageStarts,
    required this.works,
    required this.onJump,
  });
}

/// One block of a novel's text.
class PixivNovelBlockView extends StatelessWidget {
  final PixivNovelBlock block;
  final PixivNovelBlockScope scope;

  const PixivNovelBlockView({super.key, required this.block, required this.scope});

  @override
  Widget build(BuildContext context) => switch (block) {
    PixivNovelParagraph(:final spans) when spans.isEmpty => SizedBox(
      height: MediaQuery.textScalerOf(context).scale(scope.style.fontSize ?? 16) * (scope.style.height ?? 1),
    ),
    PixivNovelParagraph(:final spans) => PixivNovelLine(spans: spans, style: scope.style, scope: scope),
    PixivNovelHeading(:final spans) => _heading(spans),
    PixivNovelPageBreak(:final page) => PixivNovelPageDivider(page: page),
    PixivNovelIllustBlock() || PixivNovelUploadBlock() => PixivNovelPictureView(block: block, scope: scope),
  };

  Widget _heading(List<PixivNovelSpan> spans) => Padding(
    padding: const EdgeInsets.only(top: 24, bottom: 12),
    child: Semantics(
      header: true,
      child: PixivNovelLine(
        spans: spans,
        scope: scope,
        style: scope.style.copyWith(
          fontSize: (scope.style.fontSize ?? 16) * 1.3,
          fontWeight: FontWeight.w700,
          height: 1.4,
        ),
      ),
    ),
  );
}

/// A line of text: ruby over its base, links and page jumps tappable.
class PixivNovelLine extends StatefulWidget {
  final List<PixivNovelSpan> spans;
  final TextStyle style;
  final PixivNovelBlockScope scope;

  const PixivNovelLine({super.key, required this.spans, required this.style, required this.scope});

  @override
  State<PixivNovelLine> createState() => _PixivNovelLineState();
}

class _PixivNovelLineState extends State<PixivNovelLine> {
  final _recognizers = <TapGestureRecognizer>[];

  @override
  void dispose() {
    _clearRecognizers();
    super.dispose();
  }

  void _clearRecognizers() {
    for (final recognizer in _recognizers) {
      recognizer.dispose();
    }
    _recognizers.clear();
  }

  @override
  Widget build(BuildContext context) {
    _clearRecognizers();
    final link = widget.style.copyWith(
      color: Theme.of(context).colorScheme.primary,
      decoration: TextDecoration.underline,
    );
    return Text.rich(
      TextSpan(style: widget.style, children: [for (final span in widget.spans) _span(context, span, link)]),
    );
  }

  InlineSpan _span(BuildContext context, PixivNovelSpan span, TextStyle link) => switch (span) {
    PixivNovelPlain(:final text) => TextSpan(text: text),
    PixivNovelRuby(:final base, :final ruby) => WidgetSpan(
      alignment: PlaceholderAlignment.baseline,
      baseline: TextBaseline.alphabetic,
      child: PixivNovelRubyText(base: base, ruby: ruby, style: widget.style),
    ),
    PixivNovelLink(:final label, :final url) => _tappable(label, link, () => openPixivNovelLink(context, url)),
    PixivNovelPageJump(:final page) when widget.scope.pageStarts.containsKey(page) => _tappable(
      L10n.of(context).plugin_pixiv_novel_jump_to_page(page),
      link,
      () => widget.scope.onJump(page),
    ),
    PixivNovelPageJump(:final source) => TextSpan(text: source),
  };

  TextSpan _tappable(String text, TextStyle style, VoidCallback onTap) {
    final recognizer = TapGestureRecognizer()..onTap = onTap;
    _recognizers.add(recognizer);
    return TextSpan(text: text, style: style, recognizer: recognizer);
  }
}

/// A link in a novel: Pixiv's own pages open in XTA (another novel or a
/// novel series too, in the reader and the series page), anything else only
/// once the reader agrees to leave.
Future<void> openPixivNovelLink(BuildContext context, String url) async {
  switch (parsePixivLink(url)) {
    case PixivNovelLinkRef(:final id):
      return openPixivNovelById(context, id);
    case PixivNovelSeriesLinkRef(:final id):
      return openPixivNovelSeries(context, id);
    case _?:
      return openPixivHref(context, url);
    case null:
  }
  final l10n = L10n.of(context);
  if (await confirmPixivAction(context, l10n.plugin_pixiv_novel_link_confirm(url), l10n.plugin_pixiv_novel_link_open) &&
      context.mounted) {
    await openLink(context, url);
  }
}

/// [ruby] in small type over [base]. The base comes first, so the pair sits on
/// the line by the base's baseline.
class PixivNovelRubyText extends StatelessWidget {
  final String base;
  final String ruby;
  final TextStyle style;

  const PixivNovelRubyText({super.key, required this.base, required this.ruby, required this.style});

  @override
  Widget build(BuildContext context) => Column(
    mainAxisSize: MainAxisSize.min,
    verticalDirection: VerticalDirection.up,
    children: [
      Text(base, style: style.copyWith(height: 1)),
      Text(ruby, style: style.copyWith(fontSize: (style.fontSize ?? 16) / 2, height: 1.1)),
    ],
  );
}

/// `[newpage]`: a rule with the number of the page it starts.
class PixivNovelPageDivider extends StatelessWidget {
  final int page;

  const PixivNovelPageDivider({super.key, required this.page});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      key: ValueKey('pixiv-novel-page-$page'),
      padding: const EdgeInsets.symmetric(vertical: 24),
      child: Row(
        spacing: 12,
        children: [
          const Expanded(child: Divider()),
          Flexible(
            child: Text(
              L10n.of(context).plugin_pixiv_novel_page(page),
              textAlign: TextAlign.center,
              style: theme.textTheme.labelMedium!.copyWith(color: theme.colorScheme.onSurfaceVariant),
            ),
          ),
          const Expanded(child: Divider()),
        ],
      ),
    );
  }
}

/// How tall a picture's place is while it loads or after it failed.
const _pictureSlot = 200.0;

/// A picture the text names. A work's picture opens the work; an uploaded one
/// opens full screen. A long press on either saves it.
class PixivNovelPictureView extends StatelessWidget {
  final PixivNovelBlock block;
  final PixivNovelBlockScope scope;

  const PixivNovelPictureView({super.key, required this.block, required this.scope});

  @override
  Widget build(BuildContext context) => switch (block) {
    final PixivNovelUploadBlock upload => _upload(context, scope.content.upload(upload)),
    final PixivNovelIllustBlock illust => switch (scope.content.illust(illust)) {
      final picture? => _work(context, illust, picture.url, picture.saveUrl),
      null => _fetchedWork(illust),
    },
    _ => const SizedBox.shrink(),
  };

  Widget _upload(BuildContext context, PixivNovelPicture? picture) => PixivNovelPictureFrame(
    url: picture?.url,
    openHint: L10n.of(context).plugin_pixiv_novel_image_view,
    onOpen: picture == null ? null : () => _viewFullScreen(context, picture),
    onSave: picture == null ? null : () => savePixivImage(context, picture.saveUrl),
  );

  Future<void> _viewFullScreen(BuildContext context, PixivNovelPicture picture) => openPluginImageViewer(
    context,
    items: [PluginMediaItem(url: picture.url, downloadUrl: picture.originalUrl)],
    sourceName: pixivDownloadSource,
    imageBuilder: (context, item, fit) => PixivNetworkImage(url: item.url, fit: fit, fullResolution: true),
  );

  Widget _work(BuildContext context, PixivNovelIllustBlock block, String? url, String? saveUrl) =>
      PixivNovelPictureFrame(
        url: url,
        openHint: L10n.of(context).plugin_pixiv_novel_image_open,
        onOpen: () => openPixivHref(context, pixivArtworkUrl(block.illustId)),
        onSave: saveUrl == null ? null : () => savePixivImage(context, saveUrl),
      );

  /// A work the page carried no picture of, fetched on its own.
  Widget _fetchedWork(PixivNovelIllustBlock block) {
    scope.works.need(block.illustId);
    return ScopedBuilder<PixivEmbeddedWorksStore, Map<int, PixivIllust?>>(
      store: scope.works,
      distinct: (works) => (works.containsKey(block.illustId), works[block.illustId]),
      onState: (context, works) {
        final illust = works[block.illustId];
        final pages = illust?.viewerUrls ?? const <String>[];
        if (illust == null || pages.isEmpty) return _work(context, block, null, null);
        final page = (block.page - 1).clamp(0, pages.length - 1);
        return _work(context, block, pages[page], illust.downloadUrlAt(page));
      },
    );
  }
}

/// A picture across the text column with its hints for screen readers; an
/// empty slot while [url] is unknown, and a retry when it fails.
class PixivNovelPictureFrame extends StatelessWidget {
  final String? url;
  final String openHint;
  final VoidCallback? onOpen;
  final VoidCallback? onSave;

  const PixivNovelPictureFrame({super.key, required this.url, required this.openHint, this.onOpen, this.onSave});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final scheme = Theme.of(context).colorScheme;
    final picture = url;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 12),
      child: Semantics(
        image: true,
        label: l10n.plugin_pixiv_novel_image,
        onTapHint: onOpen == null ? null : openHint,
        onLongPressHint: onSave == null ? null : l10n.plugin_pixiv_novel_image_save,
        child: InkWell(
          onTap: onOpen,
          onLongPress: onSave,
          borderRadius: BorderRadius.circular(8),
          child: ClipRRect(
            borderRadius: BorderRadius.circular(8),
            child: ColoredBox(
              color: scheme.surfaceContainerHighest,
              child: picture == null
                  ? SizedBox(
                      height: _pictureSlot,
                      child: Icon(Icons.image_outlined, color: scheme.onSurfaceVariant),
                    )
                  : PixivNetworkImage(url: picture, fit: BoxFit.contain, loadStateChanged: _slot),
            ),
          ),
        ),
      ),
    );
  }

  Widget? _slot(ExtendedImageState state) => switch (state.extendedImageLoadState) {
    LoadState.loading => const SizedBox(height: _pictureSlot),
    LoadState.failed => SizedBox(height: _pictureSlot, child: pixivRetryLoadState(state)),
    LoadState.completed => null,
  };
}
