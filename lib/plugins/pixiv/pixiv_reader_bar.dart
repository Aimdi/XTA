import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_reader_store.dart';

/// How much of a page has arrived, or null while its size is unknown.
double? pixivLoadProgress(ImageChunkEvent? event) {
  final total = event?.expectedTotalBytes;
  if (event == null || total == null || total <= 0) return null;
  return (event.cumulativeBytesLoaded / total).clamp(0.0, 1.0);
}

/// The reader's bottom bar: share and original quality for the page on screen and, for a
/// work of many pages, a slider and the counter that opens every page.
class PixivReaderBar extends StatelessWidget {
  final PixivReaderState state;
  final int pages;

  /// Null until the page on screen has loaded.
  final VoidCallback? onShare;
  final VoidCallback onToggleHd;
  final ValueChanged<int> onScrub;
  final ValueChanged<double> onJump;
  final VoidCallback onOverview;

  const PixivReaderBar({
    super.key,
    required this.state,
    required this.pages,
    required this.onShare,
    required this.onToggleHd,
    required this.onScrub,
    required this.onJump,
    required this.onOverview,
  });

  /// Below this width in text-sized units the page controls get a line of their own.
  static const _oneRowWidth = 300.0;

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final commands = [
      IconButton(
        key: const ValueKey('pixiv-reader-share'),
        tooltip: l10n.plugin_pixiv_share_image,
        onPressed: onShare,
        icon: const Icon(Icons.share_outlined),
      ),
      IconButton(
        key: const ValueKey('pixiv-reader-hd'),
        tooltip: l10n.plugin_pixiv_original_quality,
        isSelected: state.hd,
        onPressed: onToggleHd,
        icon: const Icon(Icons.hd_outlined),
        selectedIcon: const Icon(Icons.hd),
      ),
    ];
    return SafeArea(
      top: false,
      child: Padding(
        padding: const EdgeInsetsDirectional.fromSTEB(4, 0, 12, 4),
        child: LayoutBuilder(
          builder: (context, constraints) {
            if (pages < 2) return Row(children: commands);
            final pager = [
              Expanded(
                child: SizedBox(height: kMinInteractiveDimension, child: _slider(l10n)),
              ),
              _counter(l10n),
            ];
            if (constraints.maxWidth / MediaQuery.textScalerOf(context).scale(1) >= _oneRowWidth) {
              return Row(children: [...commands, ...pager]);
            }
            return Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Row(children: pager),
                Row(children: commands),
              ],
            );
          },
        ),
      ),
    );
  }

  Widget _counter(L10n l10n) => Tooltip(
    message: l10n.plugin_pixiv_all_pages,
    child: OutlinedButton.icon(
      key: const ValueKey('pixiv-reader-counter'),
      onPressed: onOverview,
      icon: const Icon(Icons.grid_view_outlined, size: 18),
      label: Text(l10n.plugin_pixiv_page_of(state.shownIndex + 1, pages)),
    ),
  );

  Widget _slider(L10n l10n) {
    final last = pages - 1;
    return Builder(
      builder: (context) => SliderTheme(
        data: SliderTheme.of(context).copyWith(tickMarkShape: SliderTickMarkShape.noTickMark),
        child: Slider(
          key: const ValueKey('pixiv-reader-slider'),
          value: state.shownIndex.toDouble(),
          max: last.toDouble(),
          divisions: last,
          label: '${state.shownIndex + 1}',
          semanticFormatterCallback: (value) => l10n.plugin_pixiv_current_page(value.round() + 1, pages),
          onChanged: (value) => onScrub(value.round()),
          onChangeEnd: onJump,
        ),
      ),
    );
  }
}
