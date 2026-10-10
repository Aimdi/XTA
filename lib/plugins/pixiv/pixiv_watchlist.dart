import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_discovery_api.dart';
import 'package:xta/plugins/pixiv/pixiv_discovery_models.dart';
import 'package:xta/plugins/pixiv/pixiv_image.dart';
import 'package:xta/plugins/pixiv/pixiv_link_open.dart';
import 'package:xta/plugins/pixiv/pixiv_paged_feed.dart';
import 'package:xta/plugins/pixiv/pixiv_series_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_settings.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:xta/plugins/plugin_counts.dart';
import 'package:xta/plugins/plugin_feed_skeleton.dart';
import 'package:xta/ui/dates.dart';

typedef PixivWatchlistStore = PixivPagedListStore<PixivWatchlistSeries>;

PixivWatchlistStore pixivMangaWatchlistStore(PixivDiscoveryApi api) =>
    PixivPagedListStore(({nextUrl}) => api.mangaWatchlist(nextUrl: nextUrl), keyOf: (series) => series.id);

/// Opens the newest work of a watched manga series.
Future<void> openPixivWatchlistLatest(BuildContext context, PixivWatchlistSeries series) async {
  final id = series.latestContentId;
  if (id == null) return openPixivSeries(context, series.id);
  final messenger = ScaffoldMessenger.of(context);
  final l10n = L10n.of(context);
  try {
    final illust = await context.read<PixivClient>().illustDetail(id);
    if (context.mounted) await openPixivIllust(context, illust);
  } catch (error) {
    messenger.showSnackBar(SnackBar(content: Text(pixivErrorMessage(l10n, error))));
  }
}

/// Home's Watchlist: the manga series the reader watches, newest update first.
class PixivMangaWatchlistFeed extends StatelessWidget {
  final PixivWatchlistStore store;

  const PixivMangaWatchlistFeed({super.key, required this.store});

  @override
  Widget build(BuildContext context) => PixivWatchlistFeed(
    store: store,
    emptyMessage: L10n.of(context).plugin_pixiv_watchlist_empty,
    onOpen: (context, series) => openPixivSeries(context, series.id),
    onViewLatest: openPixivWatchlistLatest,
  );
}

/// A paged watchlist of series rows; the novel watchlist passes its own openers.
class PixivWatchlistFeed extends StatelessWidget {
  final PixivWatchlistStore store;
  final String emptyMessage;
  final void Function(BuildContext context, PixivWatchlistSeries series) onOpen;
  final void Function(BuildContext context, PixivWatchlistSeries series) onViewLatest;

  const PixivWatchlistFeed({
    super.key,
    required this.store,
    required this.emptyMessage,
    required this.onOpen,
    required this.onViewLatest,
  });

  @override
  Widget build(BuildContext context) => PixivPagedFeed<PixivWatchlistSeries>(
    store: store,
    emptyMessage: emptyMessage,
    emptyIcon: Icons.bookmarks_outlined,
    placeholder: const PluginFeedSkeleton(count: 4),
    padding: const EdgeInsets.symmetric(vertical: 4),
    sliver: (context, items) => SliverList.separated(
      itemCount: items.length,
      separatorBuilder: (_, _) => const Divider(height: 1),
      itemBuilder: (context, index) => PixivWatchlistRow(
        series: items[index],
        onOpen: () => onOpen(context, items[index]),
        onViewLatest: () => onViewLatest(context, items[index]),
      ),
    ),
  );
}

/// One watched series: cover with its published count, title, author, when it
/// last updated, and a way straight to the newest part.
class PixivWatchlistRow extends StatelessWidget {
  final PixivWatchlistSeries series;
  final VoidCallback onOpen;
  final VoidCallback onViewLatest;

  const PixivWatchlistRow({super.key, required this.series, required this.onOpen, required this.onViewLatest});

  @override
  Widget build(BuildContext context) => InkWell(
    key: ValueKey('pixiv-watchlist-${series.id}'),
    onTap: onOpen,
    child: Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _PixivWatchlistCover(series: series),
          const SizedBox(width: 12),
          Expanded(child: _details(context)),
        ],
      ),
    ),
  );

  Widget _details(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    final muted = theme.textTheme.bodySmall!.copyWith(color: theme.colorScheme.onSurfaceVariant);
    final updated = series.lastPublishedAt;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          series.title,
          maxLines: 2,
          overflow: TextOverflow.ellipsis,
          style: theme.textTheme.titleSmall!.copyWith(fontWeight: FontWeight.w700),
        ),
        if (series.userName.isNotEmpty)
          Text(series.userName, maxLines: 1, overflow: TextOverflow.ellipsis, style: muted),
        if (series.maskText case final mask?) Text(mask, style: muted),
        if (updated != null) Text(l10n.plugin_pixiv_watchlist_updated(createRelativeDate(updated)), style: muted),
        const SizedBox(height: 4),
        TextButton.icon(
          key: ValueKey('pixiv-watchlist-latest-${series.id}'),
          style: TextButton.styleFrom(minimumSize: const Size(kMinInteractiveDimension, kMinInteractiveDimension)),
          onPressed: onViewLatest,
          icon: const Icon(Icons.auto_stories_outlined, size: 18),
          label: Text(l10n.plugin_pixiv_watchlist_view_latest),
        ),
      ],
    );
  }
}

class _PixivWatchlistCover extends StatelessWidget {
  final PixivWatchlistSeries series;

  const _PixivWatchlistCover({required this.series});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final cover = series.coverUrl;
    return Semantics(
      label: L10n.of(context).plugin_pixiv_works_count(series.publishedCount, compactCount(series.publishedCount)),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(8),
        child: SizedBox(
          width: 72,
          height: 96,
          child: Stack(
            fit: StackFit.expand,
            children: [
              ColoredBox(color: theme.colorScheme.surfaceContainerHighest),
              if (cover != null) PixivNetworkImage(url: cover, fit: BoxFit.cover),
              Positioned(left: 4, top: 4, child: _countBadge(theme)),
            ],
          ),
        ),
      ),
    );
  }

  Widget _countBadge(ThemeData theme) => ExcludeSemantics(
    child: DecoratedBox(
      decoration: BoxDecoration(color: Colors.black.withValues(alpha: 0.65), borderRadius: BorderRadius.circular(6)),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 1),
        child: Text(
          compactCount(series.publishedCount),
          style: theme.textTheme.labelSmall!.copyWith(color: Colors.white, fontWeight: FontWeight.w700),
        ),
      ),
    ),
  );
}
