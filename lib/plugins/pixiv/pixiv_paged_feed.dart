import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_settings.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:xta/plugins/plugin_feed_insets.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/ui/empty_pane.dart';
import 'package:xta/ui/errors.dart';

/// Any paged Pixiv list drawn as one sliver: creators, articles, series rows.
/// A placeholder first, the list kept through soft refreshes and failed
/// appends, a retry when empty or failed, and the next page asked for well
/// before the end.
class PixivPagedFeed<T> extends StatelessWidget {
  final PixivPagedListStore<T> store;
  final Widget Function(BuildContext context, List<T> items) sliver;
  final String emptyMessage;
  final IconData emptyIcon;
  final Widget placeholder;
  final EdgeInsets padding;
  final ScrollController? scrollController;

  const PixivPagedFeed({
    super.key,
    required this.store,
    required this.sliver,
    required this.emptyMessage,
    this.emptyIcon = Icons.inbox_outlined,
    this.placeholder = const Center(child: CircularProgressIndicator()),
    this.padding = EdgeInsets.zero,
    this.scrollController,
  });

  @override
  Widget build(BuildContext context) => ScopedBuilder<PixivPagedListStore<T>, List<T>>(
    store: store,
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
      if (notification.depth == 0 && metrics.pixels > metrics.maxScrollExtent - 800) store.loadMore();
      return false;
    },
    child: RefreshIndicator(
      onRefresh: store.refresh,
      child: CustomScrollView(
        controller: pluginInnerScrollController(context, scrollController),
        primary: PluginEmbedded.maybeOf(context) ? false : null,
        physics: const AlwaysScrollableScrollPhysics(),
        slivers: [
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
