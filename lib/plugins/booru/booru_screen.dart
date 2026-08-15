import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/plugins/booru/booru_client.dart';
import 'package:xta/plugins/booru/booru_errors.dart';
import 'package:xta/plugins/booru/booru_grid.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_search_screen.dart';
import 'package:xta/plugins/booru/booru_settings.dart';
import 'package:xta/plugins/booru/booru_store.dart';
import 'package:xta/ui/empty_pane.dart';
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
  Disposer? _tagsDisposer;
  var _followingBootstrapped = false;
  List<String> _lastFollowingTags = const [];

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
      final tags = context.read<BooruTagsStore>();
      await tags.load();
      if (!mounted) return;
      await context.read<BooruMuteStore>().load();
      if (!mounted) return;
      _tagsDisposer = tags.observer(
        onState: (next) {
          if (!_listEquals(next, _lastFollowingTags)) {
            _lastFollowingTags = List.of(next);
            if (_followingBootstrapped || _tabs.index == 1) {
              _following.refresh();
            }
          }
        },
      );
      await _latest.refresh();
    });
  }

  Future<BooruPostPage> _followingLoader({required int page}) async {
    final tags = context.read<BooruTagsStore>().state;
    if (tags.isEmpty) {
      return const BooruPostPage(posts: [], page: 1, hasMore: false);
    }
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

  bool _listEquals(List<String> a, List<String> b) {
    if (identical(a, b)) return true;
    if (a.length != b.length) return false;
    for (var i = 0; i < a.length; i++) {
      if (a[i] != b[i]) return false;
    }
    return true;
  }

  @override
  void dispose() {
    final disposeObserver = _tagsDisposer;
    _tagsDisposer = null;
    if (disposeObserver != null) {
      // ignore: discarded_futures
      disposeObserver();
    }
    _tabs.dispose();
    _latest.destroy();
    _following.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);

    return Scaffold(
      primary: !PluginEmbedded.maybeOf(context),
      appBar: pluginHomeTabAppBar(
        tabs: TabBar(
          controller: _tabs,
          isScrollable: true,
          tabAlignment: TabAlignment.start,
          tabs: [
            Tab(text: l10n.plugin_booru_tab_latest),
            Tab(text: l10n.plugin_booru_tab_following),
          ],
        ),
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
              MaterialPageRoute(builder: (_) => const BooruSettingsScreen()),
            ),
          ),
        ],
      ),
      body: TabBarView(
        controller: _tabs,
        children: [
          _FeedTab(
            store: _latest,
            emptyLabel: l10n.plugin_booru_empty_latest,
            scrollController: widget.scrollController,
          ),
          _FollowingTab(store: _following),
        ],
      ),
    );
  }
}

class _FeedTab extends StatelessWidget {
  final BooruFeedStore store;
  final String emptyLabel;
  final ScrollController? scrollController;

  const _FeedTab({
    required this.store,
    required this.emptyLabel,
    this.scrollController,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return ScopedBuilder<BooruFeedStore, List<BooruPost>>(
      store: store,
      onLoading: (_) => store.state.isNotEmpty
          ? BooruPostGrid(
              posts: store.state,
              scrollController: scrollController,
              onRefresh: store.refresh,
              loadingMore: store.loadingMore,
              onNearEnd: store.loadMore,
            )
          : const Center(child: CircularProgressIndicator()),
      onError: (_, error) => FullPageErrorWidget(
        error: error,
        stackTrace: null,
        prefix: booruErrorMessage(l10n, error),
        onRetry: () => store.refresh(),
      ),
      onState: (context, posts) {
        if (posts.isEmpty) {
          return EmptyPane(
            icon: Icons.photo_outlined,
            message: emptyLabel,
            scrollController: scrollController,
            onRefresh: store.refresh,
            action: FilledButton.icon(
              onPressed: store.refresh,
              icon: const Icon(Icons.refresh),
              label: Text(l10n.retry),
            ),
          );
        }
        return BooruPostGrid(
          posts: posts,
          scrollController: scrollController,
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
          return EmptyPane(
            icon: Icons.sell_outlined,
            message: l10n.plugin_booru_empty_following,
            action: FilledButton.icon(
              onPressed: () => Navigator.push(
                context,
                MaterialPageRoute(builder: (_) => const BooruSearchScreen()),
              ),
              icon: const Icon(Icons.search),
              label: Text(l10n.search),
            ),
          );
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
                        onPressed: () => Navigator.push(
                          context,
                          MaterialPageRoute(
                            builder: (_) =>
                                BooruSearchScreen(initialQuery: tag),
                          ),
                        ),
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
