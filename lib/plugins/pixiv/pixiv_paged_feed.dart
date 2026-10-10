import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart' show ScrollCacheExtent;
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_settings.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:xta/plugins/plugin_feed_insets.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/ui/empty_pane.dart';
import 'package:xta/ui/errors.dart';

/// The vertical scroll view a Pixiv list draws its slivers in: always
/// scrollable, on the Home shell's inner controller when embedded there.
Widget pixivScrollView(
  BuildContext context, {
  required List<Widget> slivers,
  ScrollController? controller,
  ScrollCacheExtent? cacheExtent,
}) => CustomScrollView(
  controller: pluginInnerScrollController(context, controller),
  primary: PluginEmbedded.maybeOf(context) ? false : null,
  scrollCacheExtent: cacheExtent,
  physics: const AlwaysScrollableScrollPhysics(),
  slivers: slivers,
);

/// Any paged Pixiv list drawn as one sliver: works grids, creators, articles,
/// series rows. A placeholder first, the list kept through soft refreshes and
/// failed appends, a retry when empty or failed, and the next page asked for
/// [loadAhead] pixels before the end of its own scroll.
class PixivPagedFeed<T> extends StatelessWidget {
  final PixivPagedListStore<T> store;
  final Widget Function(BuildContext context, List<T> items) sliver;
  final String emptyMessage;
  final IconData emptyIcon;
  final Widget placeholder;
  final EdgeInsets padding;
  final ScrollController? scrollController;

  /// Slivers scrolled above the items, such as a carousel or a header row.
  final List<Widget> leadingSlivers;

  /// How far before the end the next page is asked for.
  final double loadAhead;

  /// How far past the screen items are built ahead; the scroll view's own default when null.
  final ScrollCacheExtent? cacheExtent;

  const PixivPagedFeed({
    super.key,
    required this.store,
    required this.sliver,
    required this.emptyMessage,
    this.emptyIcon = Icons.inbox_outlined,
    this.placeholder = const Center(child: CircularProgressIndicator()),
    this.padding = EdgeInsets.zero,
    this.scrollController,
    this.leadingSlivers = const [],
    this.loadAhead = 800,
    this.cacheExtent,
  });

  @override
  Widget build(BuildContext context) => ScopedBuilder<PixivPagedListStore<T>, List<T>>(
    store: store,
    // A soft refresh keeps what is shown; only the first load blanks the list.
    onLoading: (context) => store.state.isEmpty ? placeholder : _list(context, store.state),
    onError: (context, error) => store.state.isNotEmpty ? _list(context, store.state) : _error(context, error),
    onState: (context, items) => items.isEmpty ? _empty(context) : _list(context, items),
  );

  Widget _error(BuildContext context, Object? error) => Padding(
    padding: const EdgeInsets.all(24),
    child: FullPageErrorWidget(
      error: error,
      stackTrace: null,
      prefix: pixivErrorMessage(L10n.of(context), error ?? Exception()),
      onRetry: store.refresh,
    ),
  );

  /// Refreshable even when empty: re-selecting a tab does not reload, so a
  /// transient empty page must still offer a gesture that asks again.
  Widget _empty(BuildContext context) => EmptyPane(
    icon: emptyIcon,
    message: emptyMessage,
    scrollController: scrollController,
    onRefresh: store.refresh,
    action: FilledButton.icon(
      onPressed: store.refresh,
      icon: const Icon(Icons.refresh),
      label: Text(L10n.of(context).retry),
    ),
  );

  Widget _list(BuildContext context, List<T> items) => NotificationListener<ScrollNotification>(
    onNotification: (notification) {
      // Only this list's own scrolls page it, not a sideways strip inside it.
      final metrics = notification.metrics;
      if (notification.depth == 0 && metrics.pixels > metrics.maxScrollExtent - loadAhead) store.loadMore();
      return false;
    },
    child: RefreshIndicator(
      onRefresh: store.refresh,
      child: pixivScrollView(
        context,
        controller: scrollController,
        cacheExtent: cacheExtent,
        slivers: [
          ...leadingSlivers,
          SliverPadding(
            padding: pluginFeedPadding(context, extra: padding),
            sliver: sliver(context, items),
          ),
          if (store.loadingMore)
            const SliverToBoxAdapter(
              child: Padding(
                padding: EdgeInsets.all(16),
                child: Center(child: CircularProgressIndicator()),
              ),
            ),
        ],
      ),
    ),
  );
}
