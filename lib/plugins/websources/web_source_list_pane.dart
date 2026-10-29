import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_feed_insets.dart';
import 'package:xta/plugins/plugin_feed_skeleton.dart';
import 'package:xta/plugins/plugin_gallery_layout.dart';
import 'package:xta/plugins/plugin_post_media.dart';
import 'package:xta/plugins/websources/web_source_definition.dart';
import 'package:xta/plugins/websources/web_source_dialogs.dart';
import 'package:xta/plugins/websources/web_source_feed_store.dart';
import 'package:xta/plugins/websources/web_source_item_card.dart';
import 'package:xta/plugins/websources/web_source_items.dart';
import 'package:xta/ui/empty_pane.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/ui/feed_list.dart';

/// One list of a source: posts or a picture grid, loading the next page as
/// the reader nears the end.
class WebSourceListPane extends StatefulWidget {
  final WebSourceFeedStore store;
  final ScrollController scrollController;

  const WebSourceListPane({super.key, required this.store, required this.scrollController});

  @override
  State<WebSourceListPane> createState() => _WebSourceListPaneState();
}

class _WebSourceListPaneState extends State<WebSourceListPane> {
  @override
  void initState() {
    super.initState();
    if (widget.store.state.isEmpty && !widget.store.isLoading) widget.store.refresh();
  }

  @override
  Widget build(BuildContext context) {
    final store = widget.store;
    final gallery = store.source.layout == WebSourceLayout.gallery;
    Widget items(List<WebSourceItem> items) =>
        _Items(store: store, items: items, scrollController: widget.scrollController);
    return ScopedBuilder<WebSourceFeedStore, List<WebSourceItem>>(
      store: store,
      onLoading: (_) => store.state.isNotEmpty
          ? items(store.state)
          : gallery
          ? const PluginGridSkeleton()
          : const PluginFeedSkeleton(),
      onError: (_, error) => store.state.isNotEmpty
          ? items(store.state)
          : FullPageErrorWidget(
              error: error,
              stackTrace: null,
              prefix: webSourceFailureMessage(L10n.of(context), error),
              onRetry: store.refresh,
            ),
      onState: (_, posts) => posts.isEmpty
          ? EmptyPane(
              icon: Icons.inbox_outlined,
              message: L10n.of(context).plugin_websources_list_empty,
              scrollController: widget.scrollController,
              onRefresh: store.refresh,
            )
          : items(posts),
    );
  }
}

class _Items extends StatelessWidget {
  final WebSourceFeedStore store;
  final List<WebSourceItem> items;
  final ScrollController scrollController;

  const _Items({required this.store, required this.items, required this.scrollController});

  @override
  Widget build(BuildContext context) => RefreshIndicator(
    onRefresh: store.refresh,
    child: NotificationListener<ScrollNotification>(
      onNotification: (notification) {
        if (notification is ScrollUpdateNotification && notification.metrics.extentAfter < 600) store.loadMore();
        return false;
      },
      child: store.source.layout == WebSourceLayout.gallery ? _grid(context) : _list(context),
    ),
  );

  Widget _list(BuildContext context) => FeedListView(
    controller: pluginInnerScrollController(context, scrollController),
    padding: pluginFeedPadding(context),
    physics: const AlwaysScrollableScrollPhysics(),
    itemCount: items.length,
    itemBuilder: (context, index) => WebSourceItemCard(source: store.source, item: items[index]),
  );

  Widget _grid(BuildContext context) => LayoutBuilder(
    builder: (context, constraints) => GridView.builder(
      controller: pluginInnerScrollController(context, scrollController),
      padding: pluginFeedPadding(context),
      physics: const AlwaysScrollableScrollPhysics(),
      gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: pluginGalleryColumns(constraints.maxWidth, MediaQuery.textScalerOf(context)),
      ),
      itemCount: items.length,
      itemBuilder: (context, index) =>
          WebSourcePictureTile(source: store.source, item: items[index], onTap: () => _openPictures(context, index)),
    ),
  );

  void _openPictures(BuildContext context, int index) {
    final pictured = [
      for (final item in items)
        if (item.pictures.isNotEmpty) item,
    ];
    final start = pictured.indexOf(items[index]);
    if (start < 0) return;
    openPluginImageViewer(
      context,
      items: [
        for (final item in pictured) PluginMediaItem(url: item.pictures.first, alt: item.title, shareUrl: item.link),
      ],
      initialIndex: start,
      sourceName: store.source.name,
    );
  }
}
