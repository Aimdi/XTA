import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/bluesky/bluesky_client.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/bluesky/bluesky_post_card.dart';
import 'package:xta/plugins/plugin_feed_insets.dart';
import 'package:xta/ui/empty_pane.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/ui/feed_list.dart';

/// Official bsky.app generators — Flux-style Discover / What's hot.
const kBlueskyWhatsHotFeed =
    'at://did:plc:z72i7hdynmk6r22z27h6tvur/app.bsky.feed.generator/whats-hot';
const kBlueskyDiscoverFeed =
    'at://did:plc:z72i7hdynmk6r22z27h6tvur/app.bsky.feed.generator/with-friends';

class BlueskyFeedsPane extends StatefulWidget {
  final ScrollController scrollController;

  const BlueskyFeedsPane({super.key, required this.scrollController});

  @override
  State<BlueskyFeedsPane> createState() => _BlueskyFeedsPaneState();
}

class _BlueskyFeedsPaneState extends State<BlueskyFeedsPane> {
  late final _BlueskyAlgoStore _store;
  var _source = kBlueskyWhatsHotFeed;

  @override
  void initState() {
    super.initState();
    _store = _BlueskyAlgoStore(context.read<BlueskyClient>());
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _store.load(_source);
    });
  }

  @override
  void dispose() {
    _store.destroy();
    super.dispose();
  }

  Future<void> _select(String source) async {
    if (source == _source) return;
    setState(() => _source = source);
    await _store.load(source);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Column(
      children: [
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          padding: const EdgeInsets.fromLTRB(8, 8, 8, 4),
          child: Row(
            children: [
              ChoiceChip(
                label: Text(l10n.plugin_bluesky_whats_hot),
                selected: _source == kBlueskyWhatsHotFeed,
                onSelected: (_) => _select(kBlueskyWhatsHotFeed),
              ),
              const SizedBox(width: 8),
              ChoiceChip(
                label: Text(l10n.plugin_bluesky_discover_feed),
                selected: _source == kBlueskyDiscoverFeed,
                onSelected: (_) => _select(kBlueskyDiscoverFeed),
              ),
            ],
          ),
        ),
        Expanded(
          child: ScopedBuilder<_BlueskyAlgoStore, List<BlueskyPost>>(
            store: _store,
            onLoading: (_) => _store.state.isNotEmpty
                ? _list(_store.state)
                : const Center(child: CircularProgressIndicator()),
            onError: (_, error) => FullPageErrorWidget(
              error: error,
              stackTrace: null,
              prefix: error.toString(),
              onRetry: () => _store.load(_source),
            ),
            onState: (_, posts) {
              if (posts.isEmpty) {
                return EmptyPane(
                  icon: Icons.auto_awesome_outlined,
                  message: l10n.plugin_bluesky_no_posts,
                  onRefresh: () => _store.load(_source),
                );
              }
              return RefreshIndicator(
                onRefresh: () => _store.load(_source),
                child: _list(posts),
              );
            },
          ),
        ),
      ],
    );
  }

  Widget _list(List<BlueskyPost> posts) {
    return FeedListView(
      controller: pluginInnerScrollController(context, widget.scrollController),
      padding: pluginFeedPadding(context),
      itemCount: posts.length + (_store.loadingMore ? 1 : 0),
      itemBuilder: (context, index) {
        if (index >= posts.length) {
          return const Padding(
            padding: EdgeInsets.all(16),
            child: Center(child: CircularProgressIndicator()),
          );
        }
        return BlueskyPostCard(
          key: ValueKey(posts[index].uri),
          post: posts[index],
          showSourceBadge: false,
        );
      },
    );
  }
}

class _BlueskyAlgoStore extends Store<List<BlueskyPost>> {
  final BlueskyClient client;
  var loadingMore = false;

  _BlueskyAlgoStore(this.client) : super(const []);

  Future<void> load(String source) async {
    await execute(() async {
      final page = await client.getFeed(source);
      return page.posts;
    });
  }
}
