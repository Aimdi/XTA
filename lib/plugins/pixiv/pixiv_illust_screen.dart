import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_staggered_grid_view/flutter_staggered_grid_view.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_author_works.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_button.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_store.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_grid.dart';
import 'package:xta/plugins/pixiv/pixiv_image.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_page_actions.dart';
import 'package:xta/plugins/pixiv/pixiv_page_surface.dart';
import 'package:xta/plugins/pixiv/pixiv_reader_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_search_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_ugoira_view.dart';
import 'package:xta/plugins/pixiv/pixiv_settings.dart';
import 'package:xta/plugins/pixiv/pixiv_tag_kinds.dart';
import 'package:xta/plugins/pixiv/pixiv_user_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_zoomable.dart';
import 'package:xta/subscriptions/widgets/fallback_avatar.dart';
import 'package:xta/ui/dates.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/utils/urls.dart';
import 'package:xta/plugins/plugin_counts.dart';
import 'package:xta/plugins/plugin_tag_chip.dart';

/// In-app illust viewer — pages, caption, tags, stats, related works (Pixez-like).
class PixivIllustScreen extends StatefulWidget {
  final PixivIllust illust;

  const PixivIllustScreen({super.key, required this.illust});

  @override
  State<PixivIllustScreen> createState() => _PixivIllustScreenState();
}

enum _IllustMenu { downloadAll, folder, copyLink, open, mute, more }

class _PixivIllustScreenState extends State<PixivIllustScreen> with PixivPageSurface {
  late PixivIllust _illust = widget.illust;
  final _pager = PageController();
  List<PixivIllust> _related = const [];
  String? _relatedNext;
  Object? _error;
  var _loadingDetail = true;
  var _loadingMoreRelated = false;
  var _pageIndex = 0;
  var _includeRelatedR18 = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _pager.dispose();
    super.dispose();
  }

  @override
  PixivIllust get pageIllust => _illust;

  @override
  int get currentPage => _pageIndex;

  @override
  bool get offersVertical => true;

  @override
  void showPage(int page) {
    if (_pager.hasClients) _pager.jumpToPage(page);
  }

  @override
  void changeDirection() => _openReader(_pageIndex, vertical: true);

  void _openReader(int page, {required bool vertical}) => Navigator.push(
    context,
    MaterialPageRoute<void>(
      builder: (_) => PixivReaderScreen(illust: _illust, initialPage: page, vertical: vertical),
    ),
  );

  void _onMenu(_IllustMenu item) {
    switch (item) {
      case _IllustMenu.downloadAll:
        runPageAction(PixivPageAction.downloadAll, _pageIndex);
      case _IllustMenu.folder:
        _bookmarkIntoFolder();
      case _IllustMenu.copyLink:
        runPageAction(PixivPageAction.copyLink, _pageIndex);
      case _IllustMenu.open:
        openUri(context, _illust.url);
      case _IllustMenu.mute:
        _showMuteSheet();
      case _IllustMenu.more:
        runPageAction(PixivPageAction.more, _pageIndex);
    }
  }

  Future<void> _load() async {
    setState(() {
      _loadingDetail = true;
      _error = null;
    });

    final client = context.read<PixivClient>();
    final mute = context.read<PixivMuteStore>();
    try {
      _includeRelatedR18 = pixivRelatedIncludeR18(
        seedIsR18: _illust.isR18,
        showR18: client.showR18,
      );
      // Parallel — don't wait on related before painting detail enrichment.
      final results = await Future.wait([
        client.illustDetail(_illust.id),
        client.related(_illust.id, includeR18: _includeRelatedR18),
      ]);
      if (!mounted) return;
      final detail = results[0] as PixivIllust;
      final related = results[1] as PixivIllustPage;
      setState(() {
        _illust = detail;
        _related = mute.filter(related.illusts);
        _relatedNext = related.nextUrl;
        _loadingDetail = false;
      });
    } catch (e) {
      if (!mounted) return;
      // Seed artwork stays visible; related/detail enrichment is best-effort.
      setState(() {
        _error = e;
        _loadingDetail = false;
      });
    }
  }

  Future<void> _loadMoreRelated() async {
    final next = _relatedNext;
    if (_loadingMoreRelated || next == null || next.isEmpty) {
      return;
    }
    _loadingMoreRelated = true;
    final client = context.read<PixivClient>();
    final mute = context.read<PixivMuteStore>();
    try {
      final page = await client.related(
        _illust.id,
        nextUrl: next,
        includeR18: _includeRelatedR18,
      );
      if (!mounted) return;
      final seen = {for (final illust in _related) illust.id};
      setState(() {
        _related = [
          ..._related,
          ...mute.filter(page.illusts).where((e) => seen.add(e.id)),
        ];
        _relatedNext = page.nextUrl;
        _loadingMoreRelated = false;
      });
    } catch (_) {
      if (mounted) setState(() => _loadingMoreRelated = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final pages = _illust.viewerUrls;

    return Scaffold(
      appBar: AppBar(
        title: Text(
          _illust.title.isEmpty ? l10n.plugin_pixiv_title : _illust.title,
        ),
        actions: [
          PixivBookmarkButton(illust: _illust),
          IconButton(
            key: const ValueKey('pixiv-illust-download'),
            tooltip: l10n.plugin_pixiv_download_page,
            onPressed: () => runPageAction(PixivPageAction.downloadPage, _pageIndex),
            icon: const Icon(Icons.download_outlined),
          ),
          _menu(l10n, pages.length),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: _load,
        child: NotificationListener<ScrollNotification>(
          onNotification: (n) {
            if (n.metrics.pixels > n.metrics.maxScrollExtent - 1400) {
              _loadMoreRelated();
            }
            return false;
          },
          child: CustomScrollView(
            physics: const AlwaysScrollableScrollPhysics(),
            slivers: [
              SliverToBoxAdapter(child: _viewer(pages)),
              if (pages.length > 1) SliverToBoxAdapter(child: _pageBar(l10n, pages.length)),
              SliverToBoxAdapter(child: _meta(context)),
              SliverToBoxAdapter(child: PixivAuthorWorks(key: ValueKey('pixiv-author-${_illust.userId}'), illust: _illust)),
              if (_loadingDetail)
                const SliverToBoxAdapter(
                  child: Padding(
                    padding: EdgeInsets.all(24),
                    child: Center(child: CircularProgressIndicator()),
                  ),
                )
              else if (_error != null && _related.isEmpty)
                SliverToBoxAdapter(
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: FullPageErrorWidget(
                      error: _error,
                      stackTrace: null,
                      prefix: pixivErrorMessage(l10n, _error!),
                      onRetry: _load,
                    ),
                  ),
                ),
              if (_related.isNotEmpty) ...[
                SliverToBoxAdapter(
                  child: Padding(
                    padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
                    child: Text(
                      l10n.plugin_pixiv_related,
                      style: Theme.of(context).textTheme.titleMedium!.copyWith(
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ),
                ),
                SliverPadding(
                  padding: const EdgeInsets.fromLTRB(4, 0, 4, 24),
                  sliver: SliverMasonryGrid.count(
                    crossAxisCount: 2,
                    mainAxisSpacing: 4,
                    crossAxisSpacing: 4,
                    childCount: _related.length,
                    itemBuilder: (context, index) =>
                        PixivIllustTile(illust: _related[index]),
                  ),
                ),
                if (_loadingMoreRelated)
                  const SliverToBoxAdapter(
                    child: Padding(
                      padding: EdgeInsets.all(16),
                      child: Center(child: CircularProgressIndicator()),
                    ),
                  ),
              ],
            ],
          ),
        ),
      ),
    );
  }

  Widget _viewer(List<String> pages) {
    final size = MediaQuery.sizeOf(context);
    final height = pixivDetailViewerHeight(
      screenWidth: size.width,
      screenHeight: size.height,
      width: _illust.width,
      height: _illust.height,
    );
    final width = size.width;
    final cacheWidth = (width * MediaQuery.devicePixelRatioOf(context)).ceil();

    return SizedBox(
      height: height,
      child: PageView.builder(
        controller: _pager,
        itemCount: pages.length,
        onPageChanged: (i) {
          setState(() => _pageIndex = i);
          _prefetchViewerPage(pages, i + 1, cacheWidth);
        },
        itemBuilder: (context, index) {
          final page = pages[index];
          final image = Stack(
            fit: StackFit.expand,
            alignment: Alignment.center,
            children: [
              // Instant paint from the grid thumb already on disk.
              if (index == 0)
                PixivNetworkImage(
                  url: _illust.thumbnailUrl,
                  fit: BoxFit.contain,
                  cacheWidth: cacheWidth,
                ),
              PixivNetworkImage(
                url: page,
                fit: BoxFit.contain,
                cacheWidth: cacheWidth,
              ),
            ],
          );

          final ugoira = index == 0 && _illust.isUgoira;
          final body = PixivZoomable(
            onTap: ugoira ? null : () => _openReader(index, vertical: false),
            doubleTapZoom: !ugoira,
            onLongPress: () => openPageActions(index),
            child: Center(child: ugoira ? PixivUgoiraView(illust: _illust, poster: image) : image),
          );

          if (index == 0) {
            return Hero(tag: pixivIllustHeroTag(_illust.id), child: body);
          }
          return body;
        },
      ),
    );
  }

  void _prefetchViewerPage(List<String> pages, int index, int cacheWidth) {
    if (index < 0 || index >= pages.length || !mounted) {
      return;
    }
    final provider = ExtendedNetworkImageProvider(
      pages[index],
      headers: pixivImageHeaders,
      cache: true,
    );
    precacheImage(provider, context, onError: (_, _) {});
  }

  Widget _meta(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = L10n.of(context);
    final muted = theme.colorScheme.onSurfaceVariant;
    final avatar = _illust.userAvatarUrl;

    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          InkWell(
            onTap: () => Navigator.push(
              context,
              MaterialPageRoute(
                builder: (_) => PixivUserScreen(userId: _illust.userId),
              ),
            ),
            child: Row(
              children: [
                ClipOval(
                  child: avatar == null
                      ? FallbackAvatar(
                          seed: '${_illust.userId}',
                          displayName: _illust.userName,
                          size: 40,
                          accent: theme.colorScheme.primary,
                        )
                      : SizedBox(
                          width: 40,
                          height: 40,
                          child: PixivNetworkImage(
                            url: avatar,
                            fit: BoxFit.cover,
                            cacheWidth:
                                (40 * MediaQuery.devicePixelRatioOf(context))
                                    .ceil(),
                            cacheHeight:
                                (40 * MediaQuery.devicePixelRatioOf(context))
                                    .ceil(),
                          ),
                        ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        _illust.userName,
                        style: theme.textTheme.titleSmall!.copyWith(
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                      Text(
                        '@${_illust.userAccount}',
                        style: theme.textTheme.bodySmall!.copyWith(
                          color: muted,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          if (_illust.title.isNotEmpty) ...[
            const SizedBox(height: 14),
            Text(
              _illust.title,
              style: theme.textTheme.titleLarge!.copyWith(
                fontWeight: FontWeight.w800,
              ),
            ),
          ],
          const SizedBox(height: 8),
          Wrap(
            spacing: 12,
            runSpacing: 4,
            children: [
              ScopedBuilder<PixivBookmarkStore, Map<int, bool>>(
                store: context.read<PixivBookmarkStore>(),
                distinct: (_) =>
                    context.read<PixivBookmarkStore>().isBookmarked(_illust),
                onState: (context, _) {
                  final bookmarks = context.read<PixivBookmarkStore>();
                  final bookmarked = bookmarks.isBookmarked(_illust);
                  return _stat(
                    bookmarked ? Icons.favorite : Icons.favorite_border,
                    compactCount(bookmarks.bookmarkCount(_illust)),
                  );
                },
              ),
              _stat(
                Icons.visibility_outlined,
                compactCount(_illust.totalViews),
              ),
              if (_illust.createdAt != null)
                Text(
                  createCompactDate(_illust.createdAt!),
                  style: theme.textTheme.bodySmall!.copyWith(color: muted),
                ),
              if (_illust.isR18)
                Text(
                  l10n.plugin_pixiv_r18,
                  style: theme.textTheme.labelMedium!.copyWith(
                    color: theme.colorScheme.error,
                  ),
                ),
              if (_illust.isAi) _stat(Icons.auto_awesome_outlined, l10n.plugin_pixiv_ai),
            ],
          ),
          if (_illust.caption.isNotEmpty) ...[
            const SizedBox(height: 12),
            Text(
              _illust.caption,
              style: theme.textTheme.bodyMedium!.copyWith(height: 1.35),
            ),
          ],
          if (_illust.tags.isNotEmpty) ...[
            const SizedBox(height: 12),
            Wrap(
              spacing: 6,
              children: [
                for (final entry in pixivKindedTags(_illust.tags))
                  _tagChip(l10n, entry),
              ],
            ),
          ],
        ],
      ),
    );
  }

  /// The tag as Pixiv spells it, tinted by its kind, with its translation
  /// beside it; a long press mutes it.
  Widget _tagChip(L10n l10n, PixivKindedTag entry) {
    final tag = entry.tag;
    return PluginTagChip(
      key: ValueKey('pixiv-tag-${tag.name}'),
      label: '#${tag.name}',
      detail: tag.translation,
      kind: entry.kind,
      longPressHint: l10n.plugin_pixiv_mute_tag(tag.displayName),
      onLongPress: () => _confirmMuteTag(l10n, tag),
      onPressed: () => Navigator.push(
        context,
        MaterialPageRoute(
          builder: (_) => PixivSearchScreen(initialQuery: tag.name),
        ),
      ),
    );
  }

  Widget _stat(IconData icon, String label) {
    final muted = Theme.of(context).colorScheme.onSurfaceVariant;
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, size: 16, color: muted),
        const SizedBox(width: 4),
        Text(
          label,
          style: Theme.of(context).textTheme.bodySmall!.copyWith(color: muted),
        ),
      ],
    );
  }

  Widget _menu(L10n l10n, int pages) => PopupMenuButton<_IllustMenu>(
    key: const ValueKey('pixiv-illust-menu'),
    onSelected: _onMenu,
    itemBuilder: (_) => [
      if (pages > 1) _menuItem(_IllustMenu.downloadAll, Icons.download_for_offline_outlined, l10n.plugin_pixiv_download_all),
      _menuItem(_IllustMenu.folder, Icons.create_new_folder_outlined, l10n.plugin_pixiv_bookmark_folder),
      _menuItem(_IllustMenu.copyLink, Icons.link, l10n.plugin_pixiv_copy_link),
      _menuItem(_IllustMenu.open, Icons.open_in_new, l10n.plugin_pixiv_open_on_pixiv),
      _menuItem(_IllustMenu.mute, Icons.volume_off_outlined, l10n.plugin_pixiv_mute_illust),
      _menuItem(_IllustMenu.more, Icons.more_horiz, l10n.plugin_pixiv_more_actions),
    ],
  );

  PopupMenuItem<_IllustMenu> _menuItem(_IllustMenu value, IconData icon, String label) => PopupMenuItem(
    key: ValueKey('pixiv-illust-menu-${value.name}'),
    value: value,
    child: Row(
      children: [
        Icon(icon, size: 20),
        const SizedBox(width: 12),
        Flexible(child: Text(label)),
      ],
    ),
  );

  /// Page counter that opens every page, beside a quiet way into the reader.
  Widget _pageBar(L10n l10n, int pages) {
    final scheme = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
      child: Wrap(
        alignment: WrapAlignment.center,
        crossAxisAlignment: WrapCrossAlignment.center,
        spacing: 8,
        runSpacing: 4,
        children: [
          Tooltip(
            message: l10n.plugin_pixiv_all_pages,
            child: TextButton.icon(
              key: const ValueKey('pixiv-illust-counter'),
              style: TextButton.styleFrom(foregroundColor: scheme.onSurface, iconColor: scheme.onSurfaceVariant),
              onPressed: openPageOverview,
              icon: const Icon(Icons.grid_view_outlined, size: 18),
              label: Text(l10n.plugin_pixiv_page_of(_pageIndex + 1, pages)),
            ),
          ),
          OutlinedButton.icon(
            key: const ValueKey('pixiv-illust-read-vertically'),
            style: OutlinedButton.styleFrom(iconColor: scheme.primary),
            onPressed: changeDirection,
            icon: const Icon(Icons.arrow_downward, size: 18),
            label: Text(l10n.plugin_pixiv_read_vertically),
          ),
        ],
      ),
    );
  }

  Future<void> _bookmarkIntoFolder() async {
    final l10n = L10n.of(context);
    final client = context.read<PixivClient>();
    final store = context.read<PixivBookmarkStore>();
    final messenger = ScaffoldMessenger.of(context);
    List<String> folders;
    try {
      folders = await client.bookmarkFolders();
    } catch (e) {
      messenger.showSnackBar(
        SnackBar(content: Text(pixivErrorMessage(l10n, e))),
      );
      return;
    }
    if (!mounted) return;
    final chosen = await showModalBottomSheet<({String restrict, String? folder})>(
      context: context,
      showDragHandle: true,
      builder: (sheetContext) {
        return SafeArea(
          child: ListView(
            shrinkWrap: true,
            children: [
              ListTile(title: Text(l10n.plugin_pixiv_bookmark_folder)),
              ListTile(
                leading: const Icon(Icons.bookmark_border),
                title: Text(l10n.plugin_pixiv_bookmarks_public),
                onTap: () => Navigator.pop(sheetContext, (restrict: 'public', folder: null)),
              ),
              ListTile(
                key: const ValueKey('pixiv-bookmark-private'),
                leading: const Icon(Icons.lock_outline),
                title: Text(l10n.plugin_pixiv_bookmarks_private),
                onTap: () => Navigator.pop(sheetContext, (restrict: 'private', folder: null)),
              ),
              for (final folder in folders)
                ListTile(
                  leading: const Icon(Icons.folder_outlined),
                  title: Text(folder),
                  onTap: () => Navigator.pop(sheetContext, (restrict: 'public', folder: folder)),
                ),
            ],
          ),
        );
      },
    );
    if (chosen == null || !mounted) return;
    try {
      await client.addBookmark(
        _illust.id,
        restrict: chosen.restrict,
        folder: chosen.folder,
      );
      store.update({...store.state, _illust.id: true});
    } catch (e) {
      if (mounted) {
        messenger.showSnackBar(
          SnackBar(content: Text(pixivErrorMessage(l10n, e))),
        );
      }
    }
  }

  Future<void> _showMuteSheet() {
    final l10n = L10n.of(context);
    return showModalBottomSheet<void>(
      context: context,
      builder: (sheetContext) => SafeArea(
        child: ListView(
          shrinkWrap: true,
          children: [
            ListTile(
              leading: const Icon(Icons.person_off_outlined),
              title: Text(l10n.plugin_pixiv_mute_author),
              onTap: () {
                Navigator.pop(sheetContext);
                _confirmMute(
                  l10n.plugin_pixiv_mute_author,
                  (store) => store.muteAuthor(_illust.userId),
                );
              },
            ),
            ListTile(
              leading: const Icon(Icons.hide_image_outlined),
              title: Text(l10n.plugin_pixiv_mute_illust),
              onTap: () {
                Navigator.pop(sheetContext);
                _confirmMute(
                  l10n.plugin_pixiv_mute_illust,
                  (store) => store.muteIllust(_illust.id),
                );
              },
            ),
            for (final tag in _illust.tags)
              ListTile(
                leading: const Icon(Icons.label_off_outlined),
                title: Text(l10n.plugin_pixiv_mute_tag(tag.displayName)),
                onTap: () {
                  Navigator.pop(sheetContext);
                  _confirmMuteTag(l10n, tag);
                },
              ),
          ],
        ),
      ),
    );
  }

  Future<void> _confirmMuteTag(L10n l10n, PixivTag tag) => _confirmMute(
    l10n.plugin_pixiv_mute_tag(tag.displayName),
    (store) => store.muteTag(tag.name),
  );

  Future<void> _confirmMute(
    String label,
    Future<void> Function(PixivMuteStore store) mute,
  ) async {
    final navigator = Navigator.of(context);
    final store = context.read<PixivMuteStore>();
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(label),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text(L10n.of(context).cancel),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: Text(label),
          ),
        ],
      ),
    );
    if (!mounted || confirmed != true) {
      return;
    }
    await mute(store);
    if (mounted) {
      navigator.pop();
    }
  }
}
