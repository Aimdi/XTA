import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_discovery_api.dart';
import 'package:xta/plugins/pixiv/pixiv_discovery_models.dart';
import 'package:xta/plugins/pixiv/pixiv_grid.dart';
import 'package:xta/plugins/pixiv/pixiv_image.dart';
import 'package:xta/plugins/pixiv/pixiv_in_flight.dart';
import 'package:xta/plugins/pixiv/pixiv_loads.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_settings.dart';
import 'package:xta/plugins/pixiv/pixiv_share_link.dart';
import 'package:xta/plugins/pixiv/pixiv_user_link.dart';
import 'package:xta/plugins/plugin_counts.dart';
import 'package:xta/utils/urls.dart';

/// Opens an illustration or manga series by its id. [onWatchlistChanged] runs
/// after the reader adds it to or removes it from the watchlist there; [webUrl]
/// is the link it was opened from.
Future<void> openPixivSeries(BuildContext context, int seriesId, {VoidCallback? onWatchlistChanged, String? webUrl}) =>
    Navigator.push(
      context,
      MaterialPageRoute<void>(
        builder: (_) => PixivSeriesScreen(seriesId: seriesId, onWatchlistChanged: onWatchlistChanged, webUrl: webUrl),
      ),
    );

/// A series' header and whether its watchlist change is still on its way.
typedef PixivSeriesView = ({PixivIllustSeries? series, bool busy});

/// A series' header, learnt from whichever page of it arrives, and its place
/// on the reader's watchlist. Illustration and novel series say how [S] is
/// read and written.
abstract class PixivWatchedSeriesStore<S> extends Store<({S? series, bool busy})> {
  PixivWatchedSeriesStore() : super((series: null, busy: false));

  final _inFlight = PixivInFlight();

  /// Watchlist writes that have landed, so a page asked for before the last
  /// one is known to carry an outdated watchlist flag.
  int _writes = 0;

  bool isWatched(S series);
  S withWatched(S series, bool watched);
  Future<void> writeWatched(S series, bool watched);

  /// Fetches a page with [fetch] and shows the header [headerOf] finds in it.
  Future<P> fetchPage<P>(Future<P> Function() fetch, S? Function(P page) headerOf) => _inFlight.track(() async {
    final writes = _writes;
    final page = await fetch();
    _show(headerOf(page), outdated: writes != _writes);
    return page;
  }());

  /// Shows [series] unless a watchlist change is on its way; a page asked for
  /// before the last change keeps the watchlist flag that change left.
  void _show(S? series, {required bool outdated}) {
    final shown = state.series;
    if (series == null || state.busy) return;
    update((series: outdated && shown != null ? withWatched(series, isWatched(shown)) : series, busy: false));
  }

  /// Adds the series to the watchlist or takes it off; a failure leaves it as
  /// it was and rethrows.
  Future<void> toggleWatchlist() => _inFlight.track(_toggleWatchlist());

  Future<void> _toggleWatchlist() async {
    final series = state.series;
    if (series == null || state.busy) return;
    final adding = !isWatched(series);
    update((series: series, busy: true));
    try {
      await writeWatched(series, adding);
      _writes++;
      update((series: withWatched(series, adding), busy: false));
    } catch (_) {
      update((series: series, busy: false));
      rethrow;
    }
  }

  /// Destroys the store once the pages and writes it has running land, so
  /// none of them writes to it afterwards.
  void destroyWhenSettled() => unawaited(_inFlight.whenSettled(destroy));
}

/// The watchlist toggle of a series page: [onChanged] after it lands, the
/// reason in a snack bar when it fails.
Future<void> togglePixivSeriesWatchlist(
  BuildContext context,
  PixivWatchedSeriesStore<Object> store, {
  VoidCallback? onChanged,
}) async {
  final messenger = ScaffoldMessenger.of(context);
  final l10n = L10n.of(context);
  try {
    await store.toggleWatchlist();
    onChanged?.call();
  } catch (error) {
    messenger.showSnackBar(SnackBar(content: Text(pixivErrorMessage(l10n, error))));
  }
}

/// An illustration or manga series' header and watchlist place.
class PixivSeriesStore extends PixivWatchedSeriesStore<PixivIllustSeries> {
  final PixivDiscoveryApi api;
  final int seriesId;

  PixivSeriesStore(this.api, this.seriesId);

  /// One page of the series' works; the header it carries replaces the shown one.
  Future<PixivPage<PixivIllust>> loadPage({String? nextUrl}) async {
    final page = await fetchPage(() => api.illustSeries(seriesId, nextUrl: nextUrl), (page) => page.series);
    return page.works;
  }

  @override
  bool isWatched(PixivIllustSeries series) => series.watchlistAdded;

  @override
  PixivIllustSeries withWatched(PixivIllustSeries series, bool watched) => series.copyWith(watchlistAdded: watched);

  @override
  Future<void> writeWatched(PixivIllustSeries series, bool watched) =>
      watched ? api.addToWatchlist(series.id) : api.removeFromWatchlist(series.id);
}

/// One series: cover, title, author, caption, size, a watchlist toggle and its works.
class PixivSeriesScreen extends StatefulWidget {
  final int seriesId;
  final VoidCallback? onWatchlistChanged;

  /// The series' page on pixiv.net before its header loads, so a link that
  /// cannot be read here still has its way to the browser.
  final String? webUrl;

  const PixivSeriesScreen({super.key, required this.seriesId, this.onWatchlistChanged, this.webUrl});

  @override
  State<PixivSeriesScreen> createState() => _PixivSeriesScreenState();
}

class _PixivSeriesScreenState extends State<PixivSeriesScreen> {
  late final PixivSeriesStore _series;
  late final PixivTrackedIllustStore _works;

  @override
  void initState() {
    super.initState();
    _series = PixivSeriesStore(PixivDiscoveryApi.of(context), widget.seriesId);
    _works = PixivTrackedIllustStore(_series.loadPage, filter: context.read<PixivMuteStore>().filter);
    _works.refresh();
  }

  @override
  void dispose() {
    _works.destroyWhenSettled();
    _series.destroyWhenSettled();
    super.dispose();
  }

  Future<void> _toggleWatchlist() => togglePixivSeriesWatchlist(context, _series, onChanged: widget.onWatchlistChanged);

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return ScopedBuilder<PixivSeriesStore, PixivSeriesView>(
      store: _series,
      onState: (context, view) => Scaffold(
        appBar: AppBar(
          title: Text(view.series?.title ?? l10n.plugin_pixiv_series, maxLines: 1, overflow: TextOverflow.ellipsis),
          actions: [if (view.series?.url ?? widget.webUrl case final url?) ...pixivSeriesPageActions(context, url)],
        ),
        // The header sits outside the works list, so it and its watchlist
        // toggle stay when every work is filtered out or a page fails.
        body: NestedScrollView(
          headerSliverBuilder: (context, _) => [
            if (view.series case final series?)
              SliverToBoxAdapter(
                child: PixivSeriesHeader(series: series, busy: view.busy, onToggleWatchlist: _toggleWatchlist),
              ),
          ],
          body: PixivIllustFeed(store: _works, emptyMessage: l10n.plugin_pixiv_series_empty),
        ),
      ),
    );
  }
}

/// Share and Open on Pixiv for a series page at [url], in its app bar.
List<Widget> pixivSeriesPageActions(BuildContext context, String url) {
  final l10n = L10n.of(context);
  return [
    PixivShareLinkButton(url: url),
    IconButton(
      tooltip: l10n.plugin_pixiv_open_on_pixiv,
      icon: const Icon(Icons.open_in_new),
      onPressed: () => openUri(context, url),
    ),
  ];
}

/// The series' cover, title, author, caption, work count and start date, and
/// the watchlist toggle.
class PixivSeriesHeader extends StatelessWidget {
  final PixivIllustSeries series;
  final bool busy;
  final VoidCallback onToggleWatchlist;

  const PixivSeriesHeader({super.key, required this.series, required this.busy, required this.onToggleWatchlist});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final cover = series.coverUrl;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (cover != null)
          AspectRatio(
            aspectRatio: 16 / 9,
            child: ColoredBox(
              color: theme.colorScheme.surfaceContainerHighest,
              child: PixivNetworkImage(url: cover, fit: BoxFit.cover),
            ),
          ),
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, spacing: 8, children: _details(context)),
        ),
      ],
    );
  }

  List<Widget> _details(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    final muted = theme.textTheme.bodySmall!.copyWith(color: theme.colorScheme.onSurfaceVariant);
    final created = series.createdAt;
    return [
      if (series.title.isNotEmpty)
        Text(series.title, style: theme.textTheme.titleLarge!.copyWith(fontWeight: FontWeight.w800)),
      _author(context),
      Text(
        [
          l10n.plugin_pixiv_works_count(series.workCount, compactCount(series.workCount)),
          if (created != null)
            l10n.plugin_pixiv_series_started(MaterialLocalizations.of(context).formatMediumDate(created)),
        ].join(' · '),
        style: muted,
      ),
      if (series.caption.isNotEmpty) SelectableText(series.caption, style: theme.textTheme.bodyMedium),
      PixivWatchlistButton(added: series.watchlistAdded, busy: busy, onPressed: onToggleWatchlist),
    ];
  }

  Widget _author(BuildContext context) => PixivUserLink(
    userId: series.user.id,
    name: series.user.name,
    avatarUrl: series.user.avatarUrl,
    avatarSize: 32,
    style: Theme.of(context).textTheme.titleSmall,
    borderRadius: BorderRadius.circular(8),
  );
}

/// Add to watchlist or Remove from watchlist, with a spinner while the change is on its way.
class PixivWatchlistButton extends StatelessWidget {
  final bool added;
  final bool busy;
  final VoidCallback onPressed;

  const PixivWatchlistButton({super.key, required this.added, required this.busy, required this.onPressed});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final icon = busy
        ? const SizedBox.square(dimension: 18, child: CircularProgressIndicator(strokeWidth: 2))
        : Icon(added ? Icons.bookmark_remove_outlined : Icons.bookmark_add_outlined);
    return FilledButton.tonalIcon(
      key: const ValueKey('pixiv-series-watchlist'),
      style: FilledButton.styleFrom(minimumSize: const Size(kMinInteractiveDimension, kMinInteractiveDimension)),
      onPressed: busy ? null : onPressed,
      icon: icon,
      label: Text(added ? l10n.plugin_pixiv_watchlist_remove : l10n.plugin_pixiv_watchlist_add),
    );
  }
}
