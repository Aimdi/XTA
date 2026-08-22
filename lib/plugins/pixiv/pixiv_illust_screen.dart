import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_file_dialog/flutter_file_dialog.dart';
import 'package:flutter_staggered_grid_view/flutter_staggered_grid_view.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:http/http.dart' as http;
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/utils/download_directory.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_button.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_store.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_grid.dart';
import 'package:xta/plugins/pixiv/pixiv_image.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_search_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_settings.dart';
import 'package:xta/plugins/pixiv/pixiv_user_screen.dart';
import 'package:xta/subscriptions/widgets/fallback_avatar.dart';
import 'package:xta/ui/dates.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/utils/urls.dart';
import 'package:xta/plugins/plugin_counts.dart';

/// In-app illust viewer — pages, caption, tags, stats, related works (Pixez-like).
class PixivIllustScreen extends StatefulWidget {
  final PixivIllust illust;

  const PixivIllustScreen({super.key, required this.illust});

  @override
  State<PixivIllustScreen> createState() => _PixivIllustScreenState();
}

class _PixivIllustScreenState extends State<PixivIllustScreen> {
  late PixivIllust _illust = widget.illust;
  List<PixivIllust> _related = const [];
  String? _relatedNext;
  Object? _error;
  var _loadingDetail = true;
  var _loadingMoreRelated = false;
  final _pageIndex = ValueNotifier(0);
  var _includeRelatedR18 = false;

  @override
  void dispose() {
    _pageIndex.dispose();
    super.dispose();
  }

  @override
  void initState() {
    super.initState();
    _load();
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
            tooltip: l10n.plugin_pixiv_bookmark_folder,
            onPressed: _bookmarkIntoFolder,
            icon: const Icon(Icons.create_new_folder_outlined),
          ),
          IconButton(
            tooltip: l10n.download,
            onPressed: pages.isEmpty
                ? null
                : () => _downloadPage(pages[_pageIndex.value]),
            icon: const Icon(Icons.download_outlined),
          ),
          IconButton(
            tooltip: l10n.plugin_pixiv_open_on_pixiv,
            onPressed: () => openUri(context, _illust.url),
            icon: const Icon(Icons.open_in_new),
          ),
          IconButton(
            tooltip: l10n.plugin_pixiv_mute_illust,
            onPressed: _showMuteSheet,
            icon: const Icon(Icons.volume_off_outlined),
          ),
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
              SliverToBoxAdapter(child: _meta(context)),
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
      child: Stack(
        children: [
          PageView.builder(
            itemCount: pages.length,
            // Only the counter needs the new page. Calling setState here
            // rebuilt the caption, the tag list and the whole related masonry
            // grid on every swipe of a multi-page work.
            onPageChanged: (i) {
              _pageIndex.value = i;
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

              final body = InteractiveViewer(
                minScale: 1,
                maxScale: 4,
                child: Center(child: image),
              );

              if (index == 0) {
                return Hero(tag: pixivIllustHeroTag(_illust.id), child: body);
              }
              return body;
            },
          ),
          if (pages.length > 1)
            Positioned(
              top: 8,
              right: 8,
              child: ValueListenableBuilder<int>(
                valueListenable: _pageIndex,
                builder: (context, index, _) =>
                    _pageCounter(context, index, pages.length),
              ),
            ),
        ],
      ),
    );
  }

  /// Which page of a multi-page work is showing, over the artwork.
  ///
  /// It used to be a line of grey text below the image, which said nothing
  /// until the reader had already scrolled past the picture — the one place a
  /// reader is not looking when deciding whether to swipe.
  static Widget _pageCounter(BuildContext context, int index, int total) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: Colors.black.withValues(alpha: 0.6),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
        child: Text(
          L10n.of(context).plugin_pixiv_page_of(index + 1, total),
          style: const TextStyle(
            color: Colors.white,
            fontSize: 12,
            fontWeight: FontWeight.w700,
          ),
        ),
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
    precacheImage(provider, context).catchError((_) {});
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
              runSpacing: 4,
              children: [
                for (final tag in _illust.tags)
                  ActionChip(
                    label: Text('#${tag.displayName}'),
                    visualDensity: VisualDensity.compact,
                    materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
                    onPressed: () => Navigator.push(
                      context,
                      MaterialPageRoute(
                        builder: (_) =>
                            PixivSearchScreen(initialQuery: tag.name),
                      ),
                    ),
                  ),
              ],
            ),
          ],
        ],
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

  Future<void> _downloadPage(String url) async {
    final l10n = L10n.of(context);
    final messenger = ScaffoldMessenger.of(context);
    final prefs = PrefService.of(context, listen: false);
    try {
      final response = await http.get(
        Uri.parse(url),
        headers: pixivImageHeaders,
      );
      if (response.statusCode != 200) {
        throw Exception(response.statusCode);
      }
      const ext = 'jpg';
      final name = 'pixiv_${_illust.id}_p$_pageIndex.$ext';
      final treeUri = prefs.get<String>(optionDownloadTreeUri) ?? '';
      final downloadType = prefs.get(optionDownloadType);
      if (downloadType == optionDownloadTypeAsk || treeUri.isEmpty) {
        await FlutterFileDialog.saveFile(
          params: SaveFileDialogParams(
            fileName: name,
            data: response.bodyBytes,
          ),
        );
      } else {
        await DownloadDirectory.save(
          treeUri: treeUri,
          fileName: name,
          bytes: response.bodyBytes,
        );
      }
      if (mounted) {
        messenger.showSnackBar(SnackBar(content: Text(l10n.download)));
      }
    } catch (e) {
      if (mounted) {
        messenger.showSnackBar(SnackBar(content: Text(e.toString())));
      }
    }
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
    final chosen = await showModalBottomSheet<String>(
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
                onTap: () => Navigator.pop(sheetContext, ''),
              ),
              for (final folder in folders)
                ListTile(
                  leading: const Icon(Icons.folder_outlined),
                  title: Text(folder),
                  onTap: () => Navigator.pop(sheetContext, folder),
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
        folder: chosen.isEmpty ? null : chosen,
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
                  _confirmMute(
                    l10n.plugin_pixiv_mute_tag(tag.displayName),
                    (store) => store.muteTag(tag.name),
                  );
                },
              ),
          ],
        ),
      ),
    );
  }

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
