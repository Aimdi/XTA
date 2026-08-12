import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
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

final NumberFormat _pixivDetailCount = NumberFormat.compact(locale: 'en_US');

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
  Object? _error;
  var _loadingDetail = true;
  var _pageIndex = 0;

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
      // Parallel — don't wait on related before painting detail enrichment.
      final results = await Future.wait([
        client.illustDetail(_illust.id),
        client.related(_illust.id),
      ]);
      if (!mounted) return;
      final detail = results[0] as PixivIllust;
      final related = results[1] as PixivIllustPage;
      setState(() {
        _illust = detail;
        _related = mute.filter(related.illusts);
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
        child: CustomScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          slivers: [
            SliverToBoxAdapter(child: _viewer(pages)),
            if (pages.length > 1)
              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
                  child: Text(
                    l10n.plugin_pixiv_page_of(_pageIndex + 1, pages.length),
                    textAlign: TextAlign.center,
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
                ),
              ),
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
                sliver: SliverGrid(
                  gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                    crossAxisCount: 2,
                    mainAxisSpacing: 4,
                    crossAxisSpacing: 4,
                    childAspectRatio: 0.72,
                  ),
                  delegate: SliverChildBuilderDelegate(
                    (context, index) =>
                        PixivIllustTile(illust: _related[index]),
                    childCount: _related.length,
                  ),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _viewer(List<String> pages) {
    final height = MediaQuery.sizeOf(context).height * 0.55;
    final width = MediaQuery.sizeOf(context).width;
    final cacheWidth = (width * MediaQuery.devicePixelRatioOf(context)).ceil();

    return SizedBox(
      height: height,
      child: PageView.builder(
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
                    _pixivDetailCount.format(bookmarks.bookmarkCount(_illust)),
                  );
                },
              ),
              _stat(
                Icons.visibility_outlined,
                _pixivDetailCount.format(_illust.totalViews),
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
              runSpacing: 6,
              children: [
                for (final tag in _illust.tags)
                  ActionChip(
                    label: Text('#${tag.displayName}'),
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
