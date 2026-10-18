import 'dart:async';
import 'dart:math';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:scroll_to_index/scroll_to_index.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_fetch_store.dart';
import 'package:xta/plugins/pixiv/pixiv_history_store.dart';
import 'package:xta/plugins/pixiv/pixiv_link_open.dart';
import 'package:xta/plugins/pixiv/pixiv_loads.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_api.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_bookmark_button.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_export.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_models.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_reader_blocks.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_reader_header.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_reader_menu.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_reader_store.dart';
import 'package:xta/plugins/pixiv/pixiv_settings.dart';
import 'package:xta/plugins/pixiv/pixiv_share_link.dart';
import 'package:xta/reading/article_block_reading.dart';
import 'package:xta/reading/article_reader_controls.dart';
import 'package:xta/reading/article_reading_store.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/ui/motion.dart';
import 'package:xta/utils/number_locale.dart';
import 'package:xta/utils/urls.dart';

/// The widest the text runs; wider screens centre it.
const _measure = 680.0;

/// How the reading journal knows a novel.
String pixivNovelReadingId(int novelId) => 'pixiv-novel:$novelId';

/// Novels keep their place as the article readers do, except while the Pixiv
/// history is paused: where a novel was left says that it was read.
bool pixivNovelRemembersPlace(BasePrefService prefs) => articleRemembersPosition(prefs) && !pixivHistoryPaused(prefs);

/// The one route into a novel's reader. [novel] is the novel as its opener
/// had it; one opened by its id alone fetches its detail too.
Route<void> pixivNovelReaderRoute(int novelId, {PixivNovel? novel}) => MaterialPageRoute<void>(
  builder: (_) => PixivNovelReaderScreen(novelId: novelId, novel: novel),
);

/// A novel to read: its header, its text laid out from Pixiv's markup, then
/// its comments and the chapters beside it. Text size, line spacing and the
/// place reached are the article readers' own.
class PixivNovelReaderScreen extends StatefulWidget {
  final int novelId;
  final PixivNovel? novel;

  const PixivNovelReaderScreen({super.key, required this.novelId, this.novel});

  @override
  State<PixivNovelReaderScreen> createState() => _PixivNovelReaderScreenState();
}

class _PixivNovelReaderScreenState extends State<PixivNovelReaderScreen> with WidgetsBindingObserver {
  late final PixivFetchStore<PixivNovelReading?> _novel;
  late final ArticleReadingStore _reading;
  late final PixivEmbeddedWorksStore _works;
  final _loads = PixivLoads();
  bool _opened = false;
  late final _place = ArticleBlockReading(_reading, blocks: () => _novel.state?.blocks.length ?? 0);

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    final api = PixivNovelApi.of(context);
    _novel = PixivFetchStore(() => loadPixivNovelReading(api, widget.novelId, seed: widget.novel), null);
    _works = PixivEmbeddedWorksStore(context.read<PixivClient>().illustDetail);
    _reading = ArticleReadingStore(
      prefs: PrefService.of(context, listen: false),
      articleId: pixivNovelReadingId(widget.novelId),
      onCompleted: () async {},
      journalKey: optionPluginPixivNovelReading,
      remembers: pixivNovelRemembersPlace,
      journalOnOpen: false,
    );
    _load();
  }

  /// Loads the novel; the first time it arrives it joins the history and the
  /// reader's place comes back.
  Future<void> _load() async {
    await _loads.track(_novel.load());
    final reading = _novel.state;
    if (!mounted || reading == null || _opened) return;
    _opened = true;
    recordPixivNovelVisit(context, reading.novel);
    _reading.setActive(true);
    await _place.open(_reading.state.point);
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) =>
      _reading.setActive(state == AppLifecycleState.resumed && _novel.state != null);

  @override
  void deactivate() {
    _place.deactivate();
    super.deactivate();
  }

  @override
  void activate() {
    super.activate();
    _place.activate();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _place.close(then: _reading.destroy);
    _works.destroyWhenSettled();
    _loads.destroyAfter([_novel]);
    super.dispose();
  }

  void _jumpToPage(int page) {
    final index = _novel.state?.pageStarts[page];
    if (index == null) return;
    unawaited(_place.showBlock(index, duration: xtaMotionDuration(context, kXtaMotionNavigation)));
  }

  void _openChapter(int novelId) => Navigator.pushReplacement(context, pixivNovelReaderRoute(novelId));

  Future<void> _openMenu(PixivNovelReading reading, Rect? origin) async {
    final action = await showPixivNovelReaderMenu(context, reading);
    if (action != null && mounted) _runMenuAction(action, reading, origin);
  }

  void _runMenuAction(PixivNovelMenuAction action, PixivNovelReading reading, Rect? origin) {
    final novel = reading.novel;
    switch (action) {
      case PixivNovelMenuAction.author:
        unawaited(openPixivUser(context, novel.user.id, initialTab: pixivNovelReaderAuthorTab));
      case PixivNovelMenuAction.shareAuthor:
        unawaited(sharePixivLink(pixivUserUrl(novel.user.id), origin: origin));
      case PixivNovelMenuAction.previous:
        if (reading.content.previous case final chapter?) _openChapter(chapter.id);
      case PixivNovelMenuAction.next:
        if (reading.content.next case final chapter?) _openChapter(chapter.id);
      case PixivNovelMenuAction.appearance:
        unawaited(showArticleAppearanceSheet(context, _reading, onChanged: _place.keepPlace));
      case PixivNovelMenuAction.export:
        unawaited(exportPixivNovel(context, reading));
      case PixivNovelMenuAction.share:
        unawaited(sharePixivLink(novel.url, origin: origin));
      case PixivNovelMenuAction.shareSeries:
        if (novel.series case final series?) unawaited(sharePixivLink(pixivNovelSeriesUrl(series.id), origin: origin));
      case PixivNovelMenuAction.openOnPixiv:
        unawaited(openUri(context, novel.url));
    }
  }

  @override
  Widget build(BuildContext context) => TripleBuilder<PixivFetchStore<PixivNovelReading?>, PixivNovelReading?>(
    store: _novel,
    builder: (context, triple) => switch (triple.state) {
      final reading? => _reader(context, reading),
      null => _pending(context, triple.isLoading ? null : triple.error),
    },
  );

  Widget _pending(BuildContext context, Object? error) {
    final l10n = L10n.of(context);
    final title = widget.novel?.title ?? '';
    return Scaffold(
      appBar: AppBar(title: Text(title.isEmpty ? l10n.plugin_pixiv_novel : title, maxLines: 1)),
      body: error == null
          ? const Center(child: CircularProgressIndicator())
          : Padding(
              padding: const EdgeInsets.all(24),
              child: FullPageErrorWidget(
                error: error,
                stackTrace: null,
                prefix: pixivErrorMessage(l10n, error),
                onRetry: _load,
              ),
            ),
    );
  }

  Widget _reader(BuildContext context, PixivNovelReading reading) {
    final scaler = MediaQuery.textScalerOf(context);
    return Scaffold(
      appBar: AppBar(
        toolbarHeight: max(kToolbarHeight, scaler.scale(28) + scaler.scale(16) + 12),
        title: _title(context, reading.novel),
        actions: [
          PixivNovelBookmarkButton(novel: reading.novel),
          Builder(
            builder: (button) => IconButton(
              key: const ValueKey('pixiv-novel-menu'),
              tooltip: MaterialLocalizations.of(context).showMenuTooltip,
              icon: const Icon(Icons.more_vert),
              onPressed: () => _openMenu(reading, pixivShareOrigin(button)),
            ),
          ),
        ],
      ),
      body: Column(
        children: [
          ArticleReaderControls(store: _reading, onAppearanceChanged: _place.keepPlace, onStartOver: _place.startOver),
          Expanded(child: _text(reading)),
        ],
      ),
    );
  }

  /// The title over the length, as Pixiv counts characters.
  Widget _title(BuildContext context, PixivNovel novel) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    final length = decimalCount(context, novel.textLength);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(novel.title.isEmpty ? l10n.plugin_pixiv_novel : novel.title, maxLines: 1, overflow: TextOverflow.ellipsis),
        Text(
          l10n.plugin_pixiv_novel_characters(novel.textLength, length),
          key: const ValueKey('pixiv-novel-length'),
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: theme.textTheme.labelMedium!.copyWith(color: theme.colorScheme.onSurfaceVariant),
        ),
      ],
    );
  }

  Widget _text(PixivNovelReading reading) => ScopedBuilder<ArticleReadingStore, ArticleReadingState>(
    store: _reading,
    distinct: (appearance) => (appearance.fontSize, appearance.lineHeight),
    onState: (context, appearance) => LayoutBuilder(
      builder: (context, constraints) {
        final side = EdgeInsets.symmetric(horizontal: max(20, (constraints.maxWidth - _measure) / 2));
        final scope = _scope(context, reading, appearance);
        return SelectionArea(
          child: NotificationListener<ScrollNotification>(
            onNotification: _place.onScroll,
            child: CustomScrollView(
              key: _place.viewport,
              controller: _place.controller,
              slivers: [
                SliverPadding(
                  padding: side,
                  sliver: SliverToBoxAdapter(child: PixivNovelReaderHeader(novel: reading.novel)),
                ),
                SliverPadding(padding: side, sliver: _blocks(reading, scope)),
                SliverSafeArea(
                  top: false,
                  sliver: SliverPadding(
                    padding: side,
                    sliver: SliverToBoxAdapter(
                      child: PixivNovelReaderFooter(
                        novel: reading.novel,
                        content: reading.content,
                        onOpenChapter: _openChapter,
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
        );
      },
    ),
  );

  Widget _blocks(PixivNovelReading reading, PixivNovelBlockScope scope) => SliverList.builder(
    itemCount: reading.blocks.length,
    itemBuilder: (context, index) => AutoScrollTag(
      key: ValueKey(index),
      controller: _place.controller,
      index: index,
      child: PixivNovelBlockView(block: reading.blocks[index], scope: scope),
    ),
  );

  PixivNovelBlockScope _scope(BuildContext context, PixivNovelReading reading, ArticleReadingState appearance) {
    final theme = Theme.of(context);
    return PixivNovelBlockScope(
      style: theme.textTheme.bodyLarge!.copyWith(
        fontSize: appearance.fontSize,
        height: appearance.lineHeight,
        color: theme.colorScheme.onSurface,
      ),
      content: reading.content,
      pageStarts: reading.pageStarts,
      works: _works,
      onJump: _jumpToPage,
    );
  }
}
