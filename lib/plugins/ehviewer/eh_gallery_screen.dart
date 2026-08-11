import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/ehviewer/eh_client.dart';
import 'package:xta/plugins/ehviewer/eh_errors.dart';
import 'package:xta/plugins/ehviewer/eh_grid.dart';
import 'package:xta/plugins/ehviewer/eh_models.dart';
import 'package:xta/plugins/ehviewer/eh_reader_screen.dart';
import 'package:xta/plugins/ehviewer/eh_search_screen.dart';
import 'package:xta/plugins/ehviewer/eh_store.dart';
import 'package:xta/ui/errors.dart';

class EhGalleryScreen extends StatefulWidget {
  final EhGallery gallery;

  const EhGalleryScreen({super.key, required this.gallery});

  @override
  State<EhGalleryScreen> createState() => _EhGalleryScreenState();
}

class _EhGalleryScreenState extends State<EhGalleryScreen> {
  EhGalleryDetail? _detail;
  final _previews = <EhPreview>[];
  Object? _error;
  var _loading = true;
  var _loadingMorePreviews = false;
  var _nextPreviewSheet = 1;
  var _previewSheetCount = 1;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
      _previews.clear();
      _nextPreviewSheet = 1;
    });
    try {
      final detail = await context.read<EhClient>().galleryDetail(
        gid: widget.gallery.gid,
        token: widget.gallery.token,
      );
      if (!mounted) return;
      setState(() {
        _detail = detail;
        _previews
          ..clear()
          ..addAll(detail.previews);
        _previewSheetCount = detail.previewSheetCount;
        _nextPreviewSheet = 1;
        _loading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e;
        _loading = false;
      });
    }
  }

  bool get _hasMorePreviews => _nextPreviewSheet < _previewSheetCount;

  Future<void> _loadMorePreviews() async {
    if (_loadingMorePreviews || !_hasMorePreviews) return;
    setState(() => _loadingMorePreviews = true);
    try {
      final sheet = await context.read<EhClient>().galleryPreviewSheet(
        gid: widget.gallery.gid,
        token: widget.gallery.token,
        previewSheet: _nextPreviewSheet,
      );
      if (!mounted) return;
      final seen = {for (final p in _previews) p.page};
      setState(() {
        for (final preview in sheet) {
          if (seen.add(preview.page)) _previews.add(preview);
        }
        _previews.sort((a, b) => a.page.compareTo(b.page));
        _nextPreviewSheet++;
        _loadingMorePreviews = false;
      });
    } catch (_) {
      if (mounted) setState(() => _loadingMorePreviews = false);
    }
  }

  void _openReader(EhPreview preview) {
    final gallery = _detail ?? widget.gallery;
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => EhReaderScreen(
          gallery: gallery,
          initialPreview: preview,
          previews: List.of(_previews),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final gallery = _detail ?? widget.gallery;
    final favorites = context.read<EhFavoritesStore>();

    return Scaffold(
      appBar: AppBar(
        title: Text(
          gallery.title,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
        ),
        actions: [
          ScopedBuilder<EhFavoritesStore, List<EhGallery>>(
            store: favorites,
            onState: (context, _) {
              final saved = favorites.contains(gallery.gid);
              return IconButton(
                tooltip: saved
                    ? l10n.plugin_eh_unfavorite
                    : l10n.plugin_eh_favorite,
                icon: Icon(saved ? Icons.favorite : Icons.favorite_border),
                onPressed: () => favorites.toggle(gallery),
              );
            },
          ),
          IconButton(
            tooltip: l10n.plugin_eh_open_on_site,
            icon: const Icon(Icons.public),
            onPressed: () {
              final host = context.read<EhClient>().host;
              launchUrl(
                gallery.galleryUri(host),
                mode: LaunchMode.externalApplication,
              );
            },
          ),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
          ? FullPageErrorWidget(
              error: _error,
              stackTrace: null,
              prefix: ehErrorMessage(l10n, _error),
              onRetry: _load,
            )
          : ListView(
              padding: const EdgeInsets.only(bottom: 24),
              children: [
                AspectRatio(
                  aspectRatio: 0.7,
                  child: EhNetworkImage(
                    url: gallery.thumbUrl ?? '',
                    fit: BoxFit.contain,
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
                  child: Text(
                    gallery.title,
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                ),
                if (gallery.titleJpn != null && gallery.titleJpn!.isNotEmpty)
                  Padding(
                    padding: const EdgeInsets.fromLTRB(16, 4, 16, 0),
                    child: Text(
                      gallery.titleJpn!,
                      style: Theme.of(context).textTheme.bodyMedium,
                    ),
                  ),
                Padding(
                  padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
                  child: Wrap(
                    spacing: 12,
                    children: [
                      if (gallery.category != null)
                        Text(gallery.category!.label),
                      if (gallery.pageCount != null)
                        Text(l10n.plugin_eh_pages(gallery.pageCount!)),
                      if (gallery.rating != null)
                        Text(
                          l10n.plugin_eh_rating(
                            gallery.rating!.toStringAsFixed(2),
                          ),
                        ),
                      if (gallery.uploader != null)
                        Text(l10n.plugin_eh_uploader(gallery.uploader!)),
                    ],
                  ),
                ),
                if (_previews.isNotEmpty)
                  Padding(
                    padding: const EdgeInsets.all(16),
                    child: FilledButton.icon(
                      onPressed: () => _openReader(_previews.first),
                      icon: const Icon(Icons.menu_book_outlined),
                      label: Text(l10n.plugin_eh_read),
                    ),
                  ),
                if (gallery.tags.isNotEmpty) ...[
                  Padding(
                    padding: const EdgeInsets.fromLTRB(16, 8, 16, 4),
                    child: Text(l10n.plugin_eh_tags),
                  ),
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 12),
                    child: Wrap(
                      spacing: 6,
                      runSpacing: 6,
                      children: [
                        for (final tag in gallery.tags)
                          ActionChip(
                            label: Text(tag),
                            onPressed: () => Navigator.push(
                              context,
                              MaterialPageRoute(
                                builder: (_) =>
                                    EhSearchScreen(initialQuery: tag),
                              ),
                            ),
                          ),
                      ],
                    ),
                  ),
                ],
                if (_previews.isNotEmpty) ...[
                  Padding(
                    padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
                    child: Text(
                      l10n.plugin_eh_previews,
                      style: Theme.of(context).textTheme.titleSmall,
                    ),
                  ),
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 12),
                    child: GridView.builder(
                      shrinkWrap: true,
                      physics: const NeverScrollableScrollPhysics(),
                      gridDelegate:
                          const SliverGridDelegateWithFixedCrossAxisCount(
                            crossAxisCount: 4,
                            mainAxisSpacing: 6,
                            crossAxisSpacing: 6,
                            childAspectRatio: 0.7,
                          ),
                      itemCount: _previews.length,
                      itemBuilder: (context, index) {
                        final preview = _previews[index];
                        return Material(
                          color: Theme.of(
                            context,
                          ).colorScheme.surfaceContainerHighest,
                          borderRadius: BorderRadius.circular(6),
                          clipBehavior: Clip.antiAlias,
                          child: InkWell(
                            onTap: () => _openReader(preview),
                            child: Stack(
                              fit: StackFit.expand,
                              children: [
                                if (preview.thumbUrl != null)
                                  EhSpriteThumb(
                                    url: preview.thumbUrl!,
                                    offsetX: preview.thumbOffsetX ?? 0,
                                  )
                                else
                                  const ColoredBox(color: Colors.black12),
                                Align(
                                  alignment: Alignment.bottomCenter,
                                  child: ColoredBox(
                                    color: Colors.black54,
                                    child: Padding(
                                      padding: const EdgeInsets.symmetric(
                                        vertical: 2,
                                        horizontal: 4,
                                      ),
                                      child: Text(
                                        '${preview.page}',
                                        style: const TextStyle(
                                          color: Colors.white,
                                          fontSize: 11,
                                        ),
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
                  ),
                  if (_hasMorePreviews)
                    Padding(
                      padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
                      child: OutlinedButton(
                        onPressed: _loadingMorePreviews
                            ? null
                            : _loadMorePreviews,
                        child: _loadingMorePreviews
                            ? const SizedBox(
                                width: 18,
                                height: 18,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                ),
                              )
                            : Text(l10n.plugin_eh_load_more_previews),
                      ),
                    ),
                ],
              ],
            ),
    );
  }
}
