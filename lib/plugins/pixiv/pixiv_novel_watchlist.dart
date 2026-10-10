import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_discovery_models.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_api.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_open.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_series_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:xta/plugins/pixiv/pixiv_watchlist.dart';

PixivWatchlistStore pixivNovelWatchlistStore(PixivNovelApi api) =>
    PixivPagedListStore(({nextUrl}) => api.watchlist(nextUrl: nextUrl), keyOf: (series) => series.id);

/// Opens the newest chapter of a watched novel series, or the series when
/// Pixiv named none.
Future<void> openPixivNovelWatchlistLatest(BuildContext context, PixivWatchlistSeries series) {
  final id = series.latestContentId;
  return id == null ? openPixivNovelSeries(context, series.id) : openPixivNovelById(context, id);
}

/// Home's Watchlist in Novel mode: the novel series the reader watches. A
/// change made on a series page opened from a row shows here at once.
class PixivNovelWatchlistFeed extends StatelessWidget {
  final PixivWatchlistStore store;
  final ScrollController? scrollController;

  const PixivNovelWatchlistFeed({super.key, required this.store, this.scrollController});

  @override
  Widget build(BuildContext context) => PixivWatchlistFeed(
    store: store,
    emptyMessage: L10n.of(context).plugin_pixiv_novel_watchlist_empty,
    scrollController: scrollController,
    onOpen: (context, series) => openPixivNovelSeries(context, series.id, onWatchlistChanged: store.refresh),
    onViewLatest: openPixivNovelWatchlistLatest,
  );
}
