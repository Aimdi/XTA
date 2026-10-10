import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:share_plus/share_plus.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_detail_caption.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_api.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_card.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_list.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_models.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_open.dart';
import 'package:xta/plugins/pixiv/pixiv_paged_feed.dart';
import 'package:xta/plugins/pixiv/pixiv_series_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:xta/plugins/pixiv/pixiv_user_link.dart';
import 'package:xta/plugins/plugin_counts.dart';
import 'package:xta/plugins/plugin_feed_skeleton.dart';
import 'package:xta/utils/urls.dart';

/// Opens a novel series by its id. [onWatchlistChanged] runs after the reader
/// adds it to or removes it from the watchlist there.
Future<void> openPixivNovelSeries(BuildContext context, int seriesId, {VoidCallback? onWatchlistChanged}) =>
    Navigator.push(
      context,
      MaterialPageRoute<void>(
        builder: (_) => PixivNovelSeriesScreen(seriesId: seriesId, onWatchlistChanged: onWatchlistChanged),
      ),
    );

/// A novel series with its first and newest chapters, as its pages describe it.
class PixivNovelSeriesInfo {
  final PixivNovelSeries series;
  final PixivNovel? first;
  final PixivNovel? latest;

  const PixivNovelSeriesInfo({required this.series, this.first, this.latest});

  PixivNovelSeriesInfo withSeries(PixivNovelSeries series) =>
      PixivNovelSeriesInfo(series: series, first: first, latest: latest);
}

typedef PixivNovelSeriesView = ({PixivNovelSeriesInfo? series, bool busy});

/// A novel series' header, its numbered chapters page by page, and its place
/// on the reader's watchlist.
class PixivNovelSeriesStore extends PixivWatchedSeriesStore<PixivNovelSeriesInfo> {
  final PixivNovelApi api;
  final int seriesId;

  /// How many chapters Pixiv listed before each page, by the URL that asks for it.
  final _listedBefore = <String, int>{};

  PixivNovelSeriesStore(this.api, this.seriesId);

  /// One page of chapters, numbered across the whole series.
  Future<PixivPage<PixivNovelChapter>> loadPage({String? nextUrl}) async {
    final page = await api.series(seriesId, nextUrl: nextUrl);
    final before = nextUrl == null ? 0 : _listedBefore[nextUrl] ?? 0;
    if (page.nextUrl case final next?) _listedBefore[next] = before + page.listed;
    if (page.series case final series?) {
      final known = state.series;
      show(
        PixivNovelSeriesInfo(series: series, first: page.first ?? known?.first, latest: page.latest ?? known?.latest),
      );
    }
    return PixivPage([
      for (final chapter in page.chapters) PixivNovelChapter(order: before + chapter.order, novel: chapter.novel),
    ], nextUrl: page.nextUrl);
  }

  @override
  bool isWatched(PixivNovelSeriesInfo series) => series.series.watchlistAdded;

  @override
  PixivNovelSeriesInfo withWatched(PixivNovelSeriesInfo series, bool watched) =>
      series.withSeries(series.series.copyWith(watchlistAdded: watched));

  @override
  Future<void> writeWatched(PixivNovelSeriesInfo series, bool watched) =>
      watched ? api.addToWatchlist(series.series.id) : api.removeFromWatchlist(series.series.id);
}

/// One novel series: its header with the watchlist toggle, then its chapters in order.
class PixivNovelSeriesScreen extends StatefulWidget {
  final int seriesId;
  final VoidCallback? onWatchlistChanged;

  const PixivNovelSeriesScreen({super.key, required this.seriesId, this.onWatchlistChanged});

  @override
  State<PixivNovelSeriesScreen> createState() => _PixivNovelSeriesScreenState();
}

class _PixivNovelSeriesScreenState extends State<PixivNovelSeriesScreen> {
  late final PixivNovelSeriesStore _series;
  late final PixivPagedListStore<PixivNovelChapter> _chapters;

  @override
  void initState() {
    super.initState();
    final mute = context.read<PixivMuteStore>();
    _series = PixivNovelSeriesStore(PixivNovelApi.of(context), widget.seriesId);
    _chapters = PixivPagedListStore(
      _series.loadPage,
      keyOf: (chapter) => chapter.novel.id,
      filter: (chapters) => mute.state.filterNovelsOf(chapters, _novelOf),
    )..refresh();
  }

  @override
  void dispose() {
    _chapters.destroy();
    _series.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return ScopedBuilder<PixivNovelSeriesStore, PixivNovelSeriesView>(
      store: _series,
      onState: (context, view) => Scaffold(
        appBar: AppBar(
          title: Text(
            view.series?.series.title ?? l10n.plugin_pixiv_novel_series,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
          actions: [if (view.series case final info?) ..._actions(l10n, info.series)],
        ),
        // The header sits outside the chapter list, so it and its watchlist
        // toggle stay when every chapter is filtered out or a page fails.
        body: NestedScrollView(
          headerSliverBuilder: (context, _) => [
            if (view.series case final info?)
              SliverToBoxAdapter(
                child: PixivNovelSeriesHeader(
                  info: info,
                  busy: view.busy,
                  onToggleWatchlist: () =>
                      togglePixivSeriesWatchlist(context, _series, onChanged: widget.onWatchlistChanged),
                ),
              ),
          ],
          body: _chapterList(l10n),
        ),
      ),
    );
  }

  Widget _chapterList(L10n l10n) => PixivPagedFeed<PixivNovelChapter>(
    store: _chapters,
    emptyMessage: l10n.plugin_pixiv_novel_series_empty,
    emptyIcon: Icons.menu_book_outlined,
    placeholder: const PluginFeedSkeleton(count: 4, applyFeedInsets: false),
    sliver: (context, chapters) => PixivNovelSliver<PixivNovelChapter>(
      items: chapters,
      novelOf: _novelOf,
      card: (chapter) => PixivNovelCard(novel: chapter.novel, order: chapter.order, showSeries: false),
    ),
  );

  List<Widget> _actions(L10n l10n, PixivNovelSeries series) => [
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

PixivNovel _novelOf(PixivNovelChapter chapter) => chapter.novel;

/// The series' title, author, state and size, caption, the ways into it and
/// the watchlist toggle.
class PixivNovelSeriesHeader extends StatelessWidget {
  final PixivNovelSeriesInfo info;
  final bool busy;
  final VoidCallback onToggleWatchlist;

  const PixivNovelSeriesHeader({super.key, required this.info, required this.busy, required this.onToggleWatchlist});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final series = info.series;
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        spacing: 8,
        children: [
          if (series.title.isNotEmpty)
            Text(series.title, style: theme.textTheme.titleLarge!.copyWith(fontWeight: FontWeight.w800)),
          PixivUserLink(
            userId: series.user.id,
            name: series.user.name,
            avatarUrl: series.user.avatarUrl,
            style: theme.textTheme.titleSmall,
            borderRadius: BorderRadius.circular(8),
          ),
          Text(
            pixivNovelSeriesSummary(L10n.of(context), series),
            key: const ValueKey('pixiv-novel-series-summary'),
            style: theme.textTheme.bodySmall!.copyWith(color: theme.colorScheme.onSurfaceVariant),
          ),
          if (series.captionHtml.isNotEmpty || series.caption.isNotEmpty)
            PixivHtmlText(html: series.captionHtml, plainText: series.caption),
          Wrap(spacing: 8, runSpacing: 8, children: _buttons(context)),
        ],
      ),
    );
  }

  List<Widget> _buttons(BuildContext context) {
    final l10n = L10n.of(context);
    const size = Size(kMinInteractiveDimension, kMinInteractiveDimension);
    final first = info.first;
    final latest = info.latest;
    return [
      FilledButton.icon(
        key: const ValueKey('pixiv-novel-series-start'),
        style: FilledButton.styleFrom(minimumSize: size),
        onPressed: first == null ? null : () => openPixivNovel(context, first),
        icon: const Icon(Icons.play_arrow_outlined),
        label: Text(l10n.plugin_pixiv_novel_series_start),
      ),
      FilledButton.tonalIcon(
        key: const ValueKey('pixiv-novel-series-latest'),
        style: FilledButton.styleFrom(minimumSize: size),
        onPressed: latest == null ? null : () => openPixivNovel(context, latest),
        icon: const Icon(Icons.auto_stories_outlined),
        label: Text(l10n.plugin_pixiv_watchlist_view_latest),
      ),
      PixivWatchlistButton(added: info.series.watchlistAdded, busy: busy, onPressed: onToggleWatchlist),
    ];
  }
}

/// "Completed · 12 chapters · 240K characters".
String pixivNovelSeriesSummary(L10n l10n, PixivNovelSeries series) => [
  series.isConcluded ? l10n.plugin_pixiv_novel_series_concluded : l10n.plugin_pixiv_novel_series_ongoing,
  l10n.plugin_pixiv_novel_chapters_count(series.contentCount, compactCount(series.contentCount)),
  l10n.plugin_pixiv_novel_characters(series.totalCharacterCount, compactCount(series.totalCharacterCount)),
].join(' · ');
