import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/ehviewer/eh_gallery_store.dart';
import 'package:xta/plugins/ehviewer/eh_grid.dart';
import 'package:xta/plugins/ehviewer/eh_models.dart';

/// Pages are portrait; their tiles keep the site's preview proportions.
const ehPreviewAspect = 0.7;
const _ehPreviewMaxTileWidth = 120.0;
const _ehPreviewSpacing = 6.0;

/// Columns for a preview grid [width] wide: three or four on a phone, more on
/// a tablet, never a tile wider than [_ehPreviewMaxTileWidth].
int ehPreviewColumns(double width) => math.max(3, (width / _ehPreviewMaxTileWidth).ceil());

SliverGridDelegate ehPreviewGridDelegate(double width) => SliverGridDelegateWithFixedCrossAxisCount(
  crossAxisCount: ehPreviewColumns(width),
  mainAxisSpacing: _ehPreviewSpacing,
  crossAxisSpacing: _ehPreviewSpacing,
  childAspectRatio: ehPreviewAspect,
);

/// The preview tiles read so far, each numbered and opening its page.
class EhPreviewGrid extends StatelessWidget {
  final List<EhPreview> previews;
  final int? pageCount;
  final ValueChanged<int> onOpen;

  const EhPreviewGrid({super.key, required this.previews, required this.onOpen, this.pageCount});

  @override
  Widget build(BuildContext context) {
    final total = pageCount ?? (previews.isEmpty ? 0 : previews.last.page);
    return SliverPadding(
      padding: const EdgeInsets.symmetric(horizontal: 12),
      sliver: SliverLayoutBuilder(
        builder: (context, constraints) => SliverGrid(
          gridDelegate: ehPreviewGridDelegate(constraints.crossAxisExtent),
          delegate: SliverChildBuilderDelegate(
            (context, index) => EhPreviewTile(preview: previews[index], total: total, onOpen: onOpen),
            childCount: previews.length,
          ),
        ),
      ),
    );
  }
}

class EhPreviewTile extends StatelessWidget {
  final EhPreview preview;
  final int total;
  final ValueChanged<int> onOpen;

  const EhPreviewTile({super.key, required this.preview, required this.total, required this.onOpen});

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final url = preview.thumbUrl;
    return Semantics(
      button: true,
      label: L10n.of(context).plugin_eh_page_of(preview.page, total),
      onTap: () => onOpen(preview.page),
      excludeSemantics: true,
      child: Material(
        key: ValueKey('eh-preview-${preview.page}'),
        color: scheme.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(6),
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: () => onOpen(preview.page),
          child: Stack(
            fit: StackFit.expand,
            children: [
              if (url != null)
                RepaintBoundary(
                  child: EhSpriteThumb(
                    url: url,
                    offsetX: preview.thumbOffsetX ?? 0,
                    tileWidth: preview.thumbWidth ?? ehPreviewTileWidth,
                    tileHeight: preview.thumbHeight,
                  ),
                ),
              Align(alignment: Alignment.bottomCenter, child: _number(context)),
            ],
          ),
        ),
      ),
    );
  }

  Widget _number(BuildContext context) {
    return DecoratedBox(
      decoration: const BoxDecoration(
        color: Colors.black54,
        borderRadius: BorderRadius.vertical(top: Radius.circular(4)),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 1, horizontal: 6),
        child: Text(
          '${preview.page}',
          style: Theme.of(
            context,
          ).textTheme.labelSmall?.copyWith(color: Colors.white, fontFeatures: const [FontFeature.tabularFigures()]),
        ),
      ),
    );
  }
}

/// Under the grid: a spinner while the next sheet loads, else the button that
/// reads it (and retries one that failed).
class EhPreviewFooter extends StatelessWidget {
  final EhGalleryState state;
  final VoidCallback onMore;

  const EhPreviewFooter({super.key, required this.state, required this.onMore});

  @override
  Widget build(BuildContext context) {
    if (state.loadingMore) {
      return const Padding(
        padding: EdgeInsets.all(16),
        child: Center(child: SizedBox.square(dimension: 24, child: CircularProgressIndicator(strokeWidth: 2))),
      );
    }
    if (!state.hasMorePreviews) return const SizedBox(height: 24);
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
      child: OutlinedButton.icon(
        key: const ValueKey('eh-gallery-more-previews'),
        style: OutlinedButton.styleFrom(minimumSize: const Size.fromHeight(48)),
        onPressed: onMore,
        icon: Icon(state.moreFailed ? Icons.refresh : Icons.expand_more),
        label: Text(L10n.of(context).plugin_eh_load_more_previews),
      ),
    );
  }
}
