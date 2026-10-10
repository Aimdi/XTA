import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_detail_series.dart';
import 'package:xta/plugins/pixiv/pixiv_image.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_actions.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_bookmark_button.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_models.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_open.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_series_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_viewing_prefs.dart';
import 'package:xta/plugins/plugin_counts.dart';

const _coverWidth = 80.0;

/// One novel in a list: cover, title, author, length, ratings, series, tags
/// and its heart with the bookmark count. A tap opens the novel, a long press
/// its actions.
class PixivNovelCard extends StatelessWidget {
  final PixivNovel novel;

  /// The chapter's place in its series, shown above the title on a series page.
  final int? order;

  /// Off where the list is the series itself.
  final bool showSeries;

  /// What a long press does instead of opening the actions, such as forgetting a history entry.
  final VoidCallback? onLongPress;

  const PixivNovelCard({super.key, required this.novel, this.order, this.showSeries = true, this.onLongPress});

  @override
  Widget build(BuildContext context) => InkWell(
    key: ValueKey('pixiv-novel-${novel.id}'),
    onTap: () => openPixivNovel(context, novel),
    onLongPress: onLongPress ?? () => showPixivNovelActions(context, novel),
    child: Padding(
      padding: const EdgeInsetsDirectional.fromSTEB(16, 12, 4, 12),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        spacing: 12,
        children: [
          PixivNovelCover(url: novel.coverUrl),
          Expanded(child: _details(context)),
          PixivNovelBookmarkButton(novel: novel),
        ],
      ),
    ),
  );

  Widget _details(BuildContext context) {
    final theme = Theme.of(context);
    final muted = theme.textTheme.bodySmall!.copyWith(color: theme.colorScheme.onSurfaceVariant);
    final series = novel.series;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      spacing: 2,
      children: [
        if (order case final order?)
          Text(
            L10n.of(context).plugin_pixiv_novel_chapter_number(order),
            style: theme.textTheme.labelMedium!.copyWith(color: theme.colorScheme.primary, fontWeight: FontWeight.w700),
          ),
        if (novel.title.isNotEmpty)
          Text(
            novel.title,
            maxLines: 3,
            overflow: TextOverflow.ellipsis,
            style: theme.textTheme.titleSmall!.copyWith(fontWeight: FontWeight.w700, height: 1.25),
          ),
        if (novel.user.name.isNotEmpty)
          Text(
            novel.user.name,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: theme.textTheme.labelMedium!.copyWith(color: theme.colorScheme.primary),
          ),
        _lengthAndRatings(context, muted),
        if (showSeries && series != null && series.title.isNotEmpty)
          PixivSeriesLink(series: series, dense: true, onOpen: openPixivNovelSeries),
        if (novel.tags.isNotEmpty)
          Text(
            novel.tags.map((tag) => '#${tag.displayName}').join('  '),
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: muted,
          ),
      ],
    );
  }

  Widget _lengthAndRatings(BuildContext context, TextStyle muted) {
    final l10n = L10n.of(context);
    final ai = novel.isAi && pixivShowsAiBadge(pixivPrefsOf(context));
    return Wrap(
      spacing: 6,
      runSpacing: 2,
      crossAxisAlignment: WrapCrossAlignment.center,
      children: [
        if (novel.textLength > 0)
          Text(l10n.plugin_pixiv_novel_characters(novel.textLength, compactCount(novel.textLength)), style: muted),
        if (novel.isR18) PixivNovelRating(label: novel.isR18G ? l10n.plugin_pixiv_r18g : l10n.plugin_pixiv_r18),
        if (ai) PixivNovelRating(label: l10n.plugin_pixiv_ai, ai: true),
      ],
    );
  }
}

/// A novel's cover at card size, decoded at the size it is painted, or a
/// book on a plain ground when Pixiv sent none.
class PixivNovelCover extends StatelessWidget {
  final String? url;
  final double width;

  const PixivNovelCover({super.key, required this.url, this.width = _coverWidth});

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final cover = url;
    return ExcludeSemantics(
      child: ClipRRect(
        borderRadius: BorderRadius.circular(8),
        child: SizedBox(
          width: width,
          height: width * 4 / 3,
          child: ColoredBox(
            color: scheme.surfaceContainerHighest,
            child: cover == null
                ? Icon(Icons.menu_book_outlined, color: scheme.onSurfaceVariant)
                : PixivNetworkImage(
                    url: cover,
                    fit: BoxFit.cover,
                    cacheWidth: (width * MediaQuery.devicePixelRatioOf(context)).round(),
                  ),
          ),
        ),
      ),
    );
  }
}

/// A rating label in the card's text: R-18 and R-18G in the error colours, AI in the tertiary ones.
class PixivNovelRating extends StatelessWidget {
  final String label;
  final bool ai;

  const PixivNovelRating({super.key, required this.label, this.ai = false});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    return DecoratedBox(
      decoration: BoxDecoration(
        color: ai ? scheme.tertiaryContainer : scheme.errorContainer,
        borderRadius: BorderRadius.circular(4),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 1),
        child: Text(
          label,
          style: theme.textTheme.labelSmall!.copyWith(
            color: ai ? scheme.onTertiaryContainer : scheme.onErrorContainer,
            fontWeight: FontWeight.w700,
          ),
        ),
      ),
    );
  }
}
