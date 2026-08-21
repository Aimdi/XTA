import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/rss/rss_card.dart';
import 'package:xta/plugins/rss/rss_client.dart';
import 'package:xta/plugins/rss/rss_group.dart';
import 'package:xta/plugins/rss/rss_models.dart';
import 'package:xta/ui/empty_pane.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/ui/feed_list.dart';

class RssFeedScreen extends StatefulWidget {
  final RssFeed feed;

  const RssFeedScreen({super.key, required this.feed});

  @override
  State<RssFeedScreen> createState() => _RssFeedScreenState();
}

class _RssFeedScreenState extends State<RssFeedScreen> {
  late final _RssOneFeedStore _store;

  @override
  void initState() {
    super.initState();
    _store = _RssOneFeedStore(context.read<RssClient>(), widget.feed);
    WidgetsBinding.instance.addPostFrameCallback((_) => _store.refresh());
  }

  @override
  void dispose() {
    _store.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.feed.name),
        actions: [
          IconButton(
            tooltip: l10n.plugin_rss_add_to_group,
            icon: const Icon(Icons.group_add_outlined),
            onPressed: () => addRssFeedToGroup(context, widget.feed),
          ),
        ],
      ),
      body: ScopedBuilder<_RssOneFeedStore, List<RssItem>>(
        store: _store,
        onLoading: (_) => const Center(child: CircularProgressIndicator()),
        onError: (_, error) => FullPageErrorWidget(
          error: error,
          stackTrace: null,
          prefix: l10n.plugin_rss_load_error,
          onRetry: _store.refresh,
        ),
        onState: (_, items) {
          if (items.isEmpty) {
            return EmptyPane(
              icon: Icons.rss_feed,
              message: l10n.plugin_rss_empty,
              onRefresh: _store.refresh,
            );
          }
          return RefreshIndicator(
            onRefresh: _store.refresh,
            child: FeedListView(
              itemCount: items.length,
              itemBuilder: (_, index) =>
                  RssItemCard(item: items[index], showSourceBadge: false),
            ),
          );
        },
      ),
    );
  }
}

class _RssOneFeedStore extends Store<List<RssItem>> {
  final RssClient client;
  final RssFeed feed;

  _RssOneFeedStore(this.client, this.feed) : super(const []);

  Future<void> refresh() => execute(() => client.fetchItems(feed));
}
