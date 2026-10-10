import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/ehviewer/eh_client.dart';
import 'package:xta/plugins/ehviewer/eh_errors.dart';
import 'package:xta/plugins/ehviewer/eh_gallery_actions.dart';
import 'package:xta/plugins/ehviewer/eh_gallery_comments.dart';
import 'package:xta/plugins/ehviewer/eh_gallery_header.dart';
import 'package:xta/plugins/ehviewer/eh_gallery_previews.dart';
import 'package:xta/plugins/ehviewer/eh_gallery_skeleton.dart';
import 'package:xta/plugins/ehviewer/eh_gallery_store.dart';
import 'package:xta/plugins/ehviewer/eh_gallery_tags.dart';
import 'package:xta/plugins/ehviewer/eh_models.dart';
import 'package:xta/plugins/ehviewer/eh_reader_screen.dart';
import 'package:xta/plugins/ehviewer/eh_search_screen.dart';
import 'package:xta/plugins/ehviewer/eh_store.dart';
import 'package:xta/ui/errors.dart';

/// How close to the last preview the reader gets before the next sheet loads.
const _ehPreviewPrefetchExtent = 600.0;

class EhGalleryScreen extends StatefulWidget {
  final EhGallery gallery;

  const EhGalleryScreen({super.key, required this.gallery});

  @override
  State<EhGalleryScreen> createState() => _EhGalleryScreenState();
}

class _EhGalleryScreenState extends State<EhGalleryScreen> {
  late final EhGalleryStore _store = EhGalleryStore(
    client: context.read<EhClient>(),
    history: context.read<EhHistoryStore>(),
    gallery: widget.gallery,
  );

  @override
  void initState() {
    super.initState();
    _store.load();
  }

  @override
  void dispose() {
    _store.destroy();
    super.dispose();
  }

  void _push(Widget screen) {
    Navigator.push(context, MaterialPageRoute(builder: (_) => screen));
  }

  void _openReader(int page) => _push(
    EhReaderScreen(
      gallery: _store.shown,
      initialPage: page,
      previews: _store.state.previews,
    ),
  );

  void _searchUploader(String name) =>
      _push(EhSearchScreen(initialQuery: 'uploader:"$name"'));

  Future<void> _refresh() async {
    await _store.load();
    if (!mounted) return;
    final error = _store.triple.error;
    if (error == null || _store.state.detail == null) return;
    showSnackBar(
      context,
      icon: '⚠️',
      message: ehErrorMessage(L10n.of(context), error),
    );
  }

  bool _onScroll(ScrollNotification notification) {
    final metrics = notification.metrics;
    if (notification.depth == 0 &&
        metrics.axis == Axis.vertical &&
        metrics.extentAfter < _ehPreviewPrefetchExtent) {
      _store.nearEnd();
    }
    return false;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(actions: _appBarActions(context)),
      body: TripleBuilder<EhGalleryStore, EhGalleryState>(
        store: _store,
        builder: (context, triple) => _body(context, triple),
      ),
    );
  }

  Widget _body(BuildContext context, Triple<EhGalleryState> triple) {
    final detail = triple.state.detail;
    final error = triple.error;
    if (detail == null && error != null && !triple.isLoading) {
      return FullPageErrorWidget(
        error: error,
        stackTrace: null,
        prefix: ehErrorMessage(L10n.of(context), error),
        onRetry: _store.load,
      );
    }
    return RefreshIndicator(
      onRefresh: _refresh,
      child: NotificationListener<ScrollNotification>(
        onNotification: _onScroll,
        child: CustomScrollView(
          key: const ValueKey('eh-gallery-scroll'),
          physics: const AlwaysScrollableScrollPhysics(),
          slivers: [
            SliverToBoxAdapter(
              child: EhGalleryHeader(
                gallery: _store.shown,
                listCover: widget.gallery.thumbUrl,
                onUploader: _searchUploader,
              ),
            ),
            if (detail == null)
              const SliverToBoxAdapter(child: EhGallerySkeleton())
            else
              ..._content(triple.state, detail),
          ],
        ),
      ),
    );
  }

  List<Widget> _content(EhGalleryState state, EhGalleryDetail detail) => [
    SliverToBoxAdapter(child: EhGalleryFacts(detail: detail)),
    SliverToBoxAdapter(
      child: EhReadActions(store: _store, onRead: _openReader),
    ),
    SliverToBoxAdapter(
      child: EhGalleryTags(tags: detail.tags, weakTags: detail.weakTags),
    ),
    SliverToBoxAdapter(child: EhGalleryComments(comments: detail.comments)),
    if (state.previews.isNotEmpty) ...[
      SliverToBoxAdapter(
        child: EhSectionTitle(L10n.of(context).plugin_eh_previews),
      ),
      EhPreviewGrid(
        previews: state.previews,
        pageCount: detail.pageCount,
        onOpen: _openReader,
      ),
    ],
    SliverToBoxAdapter(
      child: EhPreviewFooter(state: state, onMore: _store.loadMorePreviews),
    ),
  ];

  List<Widget> _appBarActions(BuildContext context) {
    final l10n = L10n.of(context);
    final favorites = context.read<EhFavoritesStore>();
    return [
      ScopedBuilder<EhFavoritesStore, List<EhGallery>>(
        store: favorites,
        onState: (context, _) {
          final saved = favorites.contains(widget.gallery.gid);
          return IconButton(
            key: const ValueKey('eh-gallery-favorite'),
            tooltip: saved
                ? l10n.plugin_eh_unfavorite
                : l10n.plugin_eh_favorite,
            isSelected: saved,
            icon: Icon(saved ? Icons.favorite : Icons.favorite_border),
            onPressed: () => favorites.toggle(_store.shown),
          );
        },
      ),
      IconButton(
        tooltip: l10n.plugin_eh_copy_link,
        icon: const Icon(Icons.link),
        onPressed: _copyLink,
      ),
      IconButton(
        tooltip: l10n.plugin_eh_open_on_site,
        icon: const Icon(Icons.public),
        onPressed: _openOnSite,
      ),
    ];
  }

  Uri get _galleryUri => _store.shown.galleryUri(context.read<EhClient>().host);

  void _copyLink() {
    Clipboard.setData(ClipboardData(text: _galleryUri.toString()));
    showSnackBar(
      context,
      icon: '📋',
      message: L10n.of(context).plugin_eh_link_copied,
    );
  }

  void _openOnSite() =>
      launchUrl(_galleryUri, mode: LaunchMode.externalApplication);
}
