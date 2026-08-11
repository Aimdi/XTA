import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/booru/booru_client.dart';
import 'package:xta/plugins/booru/booru_grid.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_search_screen.dart';
import 'package:xta/plugins/booru/booru_settings.dart';
import 'package:xta/plugins/booru/booru_store.dart';
import 'package:xta/ui/errors.dart';

/// Boorusama-inspired home: Latest / Following / Search entry.
class BooruScreen extends StatefulWidget {
  final ScrollController scrollController;

  const BooruScreen({super.key, required this.scrollController});

  @override
  State<BooruScreen> createState() => _BooruScreenState();
}

class _BooruScreenState extends State<BooruScreen>
    with SingleTickerProviderStateMixin {
  late final TabController _tabs;
  late final BooruFeedStore _latest;
  late final BooruFeedStore _following;
  var _followingBootstrapped = false;

  @override
  void initState() {
    super.initState();
    _tabs = TabController(length: 2, vsync: this);
    _tabs.addListener(() {
      if (_tabs.indexIsChanging) return;
      if (_tabs.index == 1) _ensureFollowing();
    });

    final client = context.read<BooruClient>();
    _latest = BooruFeedStore(
      client,
      ({required page}) => client.latest(page: page),
    );
    _following = BooruFeedStore(client, _followingLoader);

    WidgetsBinding.instance.addPostFrameCallback((_) async {
      if (!mounted) return;
      await context.read<BooruTagsStore>().load();
      if (!mounted) return;
      await _latest.refresh();
    });
  }

  Future<BooruPostPage> _followingLoader({required int page}) async {
    final tags = context.read<BooruTagsStore>().state;
    if (tags.isEmpty) {
      return const BooruPostPage(posts: [], page: 1, hasMore: false);
    }
    // Following is a merged first page of each tag; further pages would need
    // per-tag cursors — Phase 1 keeps one combined shot.
    if (page > 1) {
      return BooruPostPage(posts: const [], page: page, hasMore: false);
    }
    final posts = await context.read<BooruClient>().postsForTags(tags);
    return BooruPostPage(posts: posts, page: 1, hasMore: false);
  }

  Future<void> _ensureFollowing() async {
    if (_followingBootstrapped) return;
    _followingBootstrapped = true;
    await _following.refresh();
  }

  @override
  void dispose() {
    _tabs.dispose();
    _latest.destroy();
    _following.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);

    return Scaffold(
      body: NestedScrollView(
        controller: widget.scrollController,
        headerSliverBuilder: (context, _) => [
          SliverAppBar(
            floating: true,
            snap: true,
            title: Text(l10n.plugin_booru_title),
            actions: [
              IconButton(
                tooltip: l10n.search,
                icon: const Icon(Icons.search),
                onPressed: () => Navigator.push(
                  context,
                  MaterialPageRoute(builder: (_) => const BooruSearchScreen()),
                ),
              ),
              IconButton(
                tooltip: l10n.settings,
                icon: const Icon(Icons.settings_outlined),
                onPressed: () => Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (_) => const BooruSettingsScreen(),
                  ),
                ),
              ),
            ],
            bottom: TabBar(
              controller: _tabs,
              tabs: [
                Tab(text: l10n.plugin_booru_tab_latest),
                Tab(text: l10n.plugin_booru_tab_following),
              ],
            ),
          ),
        ],
        body: TabBarView(
          controller: _tabs,
          children: [
            _FeedTab(
              store: _latest,
              emptyLabel: l10n.plugin_booru_empty_latest,
            ),
            _FollowingTab(store: _following),
          ],
        ),
      ),
    );
  }
}

class _FeedTab extends StatelessWidget {
  final BooruFeedStore store;
  final String emptyLabel;

  const _FeedTab({required this.store, required this.emptyLabel});

  @override
  Widget build(BuildContext context) {
    return ScopedBuilder<BooruFeedStore, List<BooruPost>>.transition(
      store: store,
      onLoading: (_) => const Center(child: CircularProgressIndicator()),
      onError: (_, error) => FullPageErrorWidget(
        error: error,
        stackTrace: null,
        prefix: L10n.of(context).plugin_booru_load_error,
        onRetry: () => store.refresh(),
      ),
      onState: (context, posts) {
        if (posts.isEmpty) {
          return Center(child: Text(emptyLabel));
        }
        return BooruPostGrid(
          posts: posts,
          onRefresh: store.refresh,
          loadingMore: store.loadingMore,
          onNearEnd: store.loadMore,
        );
      },
    );
  }
}

class _FollowingTab extends StatelessWidget {
  final BooruFeedStore store;

  const _FollowingTab({required this.store});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);

    return ScopedBuilder<BooruTagsStore, List<String>>(
      store: context.read<BooruTagsStore>(),
      onState: (context, tags) {
        if (tags.isEmpty) {
          return Center(child: Text(l10n.plugin_booru_empty_following));
        }
        return Column(
          children: [
            SizedBox(
              height: 44,
              child: ListView(
                scrollDirection: Axis.horizontal,
                padding: const EdgeInsets.symmetric(horizontal: 8),
                children: [
                  for (final tag in tags)
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 4),
                      child: InputChip(
                        label: Text(tag),
                        onDeleted: () =>
                            context.read<BooruTagsStore>().remove(tag),
                      ),
                    ),
                ],
              ),
            ),
            Expanded(
              child: _FeedTab(
                store: store,
                emptyLabel: l10n.plugin_booru_empty_following_posts,
              ),
            ),
          ],
        );
      },
    );
  }
}
