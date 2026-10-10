import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:share_plus/share_plus.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_avatar.dart';
import 'package:xta/plugins/pixiv/pixiv_discovery_api.dart';
import 'package:xta/plugins/pixiv/pixiv_discovery_models.dart';
import 'package:xta/plugins/pixiv/pixiv_grid.dart';
import 'package:xta/plugins/pixiv/pixiv_image.dart';
import 'package:xta/plugins/pixiv/pixiv_link_open.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_settings.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:xta/plugins/plugin_counts.dart';
import 'package:xta/utils/urls.dart';

/// Opens an illustration or manga series by its id.
Future<void> openPixivSeries(BuildContext context, int seriesId) =>
    Navigator.push(context, MaterialPageRoute<void>(builder: (_) => PixivSeriesScreen(seriesId: seriesId)));

/// A series' header and whether its watchlist change is still on its way.
typedef PixivSeriesView = ({PixivIllustSeries? series, bool busy});

/// A series' header, learnt from whichever page of its works arrives, and its
/// place on the reader's watchlist.
class PixivSeriesStore extends Store<PixivSeriesView> {
  final PixivDiscoveryApi api;
  final int seriesId;

  PixivSeriesStore(this.api, this.seriesId) : super((series: null, busy: false));

  /// One page of the series' works; the header it carries replaces the shown one.
  Future<PixivPage<PixivIllust>> loadPage({String? nextUrl}) async {
    final page = await api.illustSeries(seriesId, nextUrl: nextUrl);
    final series = page.series;
    if (series != null && !state.busy) update((series: series, busy: false));
    return page.works;
  }

  /// Adds the series to the watchlist or takes it off; a failure leaves it as
  /// it was and rethrows.
  Future<void> toggleWatchlist() async {
    final series = state.series;
    if (series == null || state.busy) return;
    final adding = !series.watchlistAdded;
    update((series: series, busy: true));
    try {
      await (adding ? api.addToWatchlist(series.id) : api.removeFromWatchlist(series.id));
      update((series: series.copyWith(watchlistAdded: adding), busy: false));
    } catch (_) {
      update((series: series, busy: false));
      rethrow;
    }
  }
}

/// One series: cover, title, author, caption, size, a watchlist toggle and its works.
class PixivSeriesScreen extends StatefulWidget {
  final int seriesId;

  const PixivSeriesScreen({super.key, required this.seriesId});

  @override
  State<PixivSeriesScreen> createState() => _PixivSeriesScreenState();
}

class _PixivSeriesScreenState extends State<PixivSeriesScreen> {
  late final PixivSeriesStore _series;
  late final PixivIllustListStore _works;

  @override
  void initState() {
    super.initState();
    _series = PixivSeriesStore(PixivDiscoveryApi.of(context), widget.seriesId);
    _works = PixivIllustListStore(_series.loadPage, filter: context.read<PixivMuteStore>().filter);
    _works.refresh();
  }

  @override
  void dispose() {
    _works.destroy();
    _series.destroy();
    super.dispose();
  }

  Future<void> _toggleWatchlist() async {
    final messenger = ScaffoldMessenger.of(context);
    final l10n = L10n.of(context);
    try {
      await _series.toggleWatchlist();
    } catch (error) {
      messenger.showSnackBar(SnackBar(content: Text(pixivErrorMessage(l10n, error))));
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return ScopedBuilder<PixivSeriesStore, PixivSeriesView>(
      store: _series,
      onState: (context, view) => Scaffold(
        appBar: AppBar(
          title: Text(view.series?.title ?? l10n.plugin_pixiv_series, maxLines: 1, overflow: TextOverflow.ellipsis),
          actions: [if (view.series case final series?) ..._actions(l10n, series)],
        ),
        body: PixivIllustFeed(
          store: _works,
          emptyMessage: l10n.plugin_pixiv_series_empty,
          leadingSlivers: [
            if (view.series case final series?)
              SliverToBoxAdapter(
                child: PixivSeriesHeader(series: series, busy: view.busy, onToggleWatchlist: _toggleWatchlist),
              ),
          ],
        ),
      ),
    );
  }

  List<Widget> _actions(L10n l10n, PixivIllustSeries series) => [
    IconButton(
      tooltip: l10n.share_link,
      icon: const Icon(Icons.share_outlined),
      onPressed: () => SharePlus.instance.share(ShareParams(text: series.url)),
    ),
    IconButton(
      tooltip: l10n.plugin_pixiv_open_on_pixiv,
      icon: const Icon(Icons.open_in_new),
      onPressed: () => openUri(context, series.url),
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
      _watchlistButton(l10n),
    ];
  }

  Widget _author(BuildContext context) {
    final user = series.user;
    return InkWell(
      borderRadius: BorderRadius.circular(8),
      onTap: user.id == 0 ? null : () => openPixivUser(context, user.id),
      child: ConstrainedBox(
        constraints: const BoxConstraints(minHeight: kMinInteractiveDimension),
        child: Row(
          spacing: 10,
          children: [
            PixivAvatar.user(user, size: 32),
            Flexible(
              child: Text(
                user.name,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.titleSmall,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _watchlistButton(L10n l10n) {
    final added = series.watchlistAdded;
    final icon = busy
        ? const SizedBox.square(dimension: 18, child: CircularProgressIndicator(strokeWidth: 2))
        : Icon(added ? Icons.bookmark_remove_outlined : Icons.bookmark_add_outlined);
    return FilledButton.tonalIcon(
      key: const ValueKey('pixiv-series-watchlist'),
      style: FilledButton.styleFrom(minimumSize: const Size(kMinInteractiveDimension, kMinInteractiveDimension)),
      onPressed: busy ? null : onToggleWatchlist,
      icon: icon,
      label: Text(added ? l10n.plugin_pixiv_watchlist_remove : l10n.plugin_pixiv_watchlist_add),
    );
  }
}
