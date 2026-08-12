import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/tiktok/tiktok_client.dart';
import 'package:xta/plugins/tiktok/tiktok_errors.dart';
import 'package:xta/plugins/tiktok/tiktok_models.dart';
import 'package:xta/plugins/tiktok/tiktok_post_card.dart';
import 'package:xta/plugins/tiktok/tiktok_profile_screen.dart';
import 'package:xta/plugins/tiktok/tiktok_search_sheet.dart';
import 'package:xta/plugins/tiktok/tiktok_settings.dart';
import 'package:xta/plugins/tiktok/tiktok_store.dart';
import 'package:xta/ui/errors.dart';

/// Guest TikTok home: Following feed + local accounts.
class TikTokScreen extends StatefulWidget {
  final ScrollController scrollController;

  const TikTokScreen({super.key, required this.scrollController});

  @override
  State<TikTokScreen> createState() => _TikTokScreenState();
}

class _TikTokScreenState extends State<TikTokScreen>
    with SingleTickerProviderStateMixin {
  late final TabController _tabs;
  late final TikTokFollowingStore _following;

  @override
  void initState() {
    super.initState();
    _tabs = TabController(length: 2, vsync: this);
    final client = context.read<TikTokClient>();
    final follows = context.read<TikTokFollowsStore>();
    _following = TikTokFollowingStore(client, follows);

    WidgetsBinding.instance.addPostFrameCallback((_) async {
      if (!mounted) return;
      await follows.load();
      if (!mounted) return;
      await context.read<TikTokLikesStore>().load();
      if (!mounted) return;
      await context.read<TikTokSearchHistoryStore>().load();
      if (!mounted) return;
      await _following.refresh();
    });

    _tabs.addListener(() {
      if (_tabs.indexIsChanging) return;
      if (_tabs.index == 1) context.read<TikTokFollowsStore>().load();
    });
  }

  @override
  void dispose() {
    _tabs.dispose();
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
            title: Text(l10n.plugin_tiktok_title),
            actions: [
              IconButton(
                tooltip: l10n.search,
                icon: const Icon(Icons.search),
                onPressed: () => showTikTokSearchSheet(context),
              ),
              IconButton(
                tooltip: l10n.settings,
                icon: const Icon(Icons.settings_outlined),
                onPressed: () => Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (_) => const TikTokSettingsScreen(),
                  ),
                ),
              ),
            ],
            bottom: TabBar(
              controller: _tabs,
              tabs: [
                Tab(text: l10n.plugin_tiktok_tab_following),
                Tab(text: l10n.plugin_tiktok_tab_accounts),
              ],
            ),
          ),
        ],
        body: TabBarView(
          controller: _tabs,
          children: [
            _FollowingTab(store: _following),
            const _AccountsTab(),
          ],
        ),
      ),
    );
  }
}

class _FollowingTab extends StatelessWidget {
  final TikTokFollowingStore store;

  const _FollowingTab({required this.store});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return ScopedBuilder<TikTokFollowingStore, List<TikTokPost>>.transition(
      store: store,
      onLoading: (_) => const Center(child: CircularProgressIndicator()),
      onError: (_, error) => FullPageErrorWidget(
        error: error,
        stackTrace: null,
        prefix: tiktokErrorMessage(l10n, error),
        onRetry: store.refresh,
      ),
      onState: (context, posts) {
        if (posts.isEmpty) {
          return RefreshIndicator(
            onRefresh: store.refresh,
            child: ListView(
              physics: const AlwaysScrollableScrollPhysics(),
              children: [
                SizedBox(
                  height: MediaQuery.sizeOf(context).height * 0.4,
                  child: Center(
                    child: Text(l10n.plugin_tiktok_empty_following),
                  ),
                ),
              ],
            ),
          );
        }
        return _PostList(
          posts: posts,
          onRefresh: store.refresh,
          onNearEnd: store.loadMore,
          loadingMore: store.loadingMore,
        );
      },
    );
  }
}

class _AccountsTab extends StatelessWidget {
  const _AccountsTab();

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return ScopedBuilder<TikTokFollowsStore, List<TikTokFollow>>(
      store: context.read<TikTokFollowsStore>(),
      onState: (context, follows) {
        if (follows.isEmpty) {
          return Center(child: Text(l10n.plugin_tiktok_empty_accounts));
        }
        return RefreshIndicator(
          onRefresh: () => context.read<TikTokFollowsStore>().load(),
          child: ListView.builder(
            itemCount: follows.length,
            itemBuilder: (context, index) {
              final follow = follows[index];
              return ListTile(
                leading: TikTokAvatar(
                  url: follow.avatarUrl,
                  seed: follow.id,
                  name: follow.name,
                ),
                title: Text(follow.name),
                subtitle: Text('@${follow.id}'),
                onTap: () => Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (_) => TikTokProfileScreen(handle: follow.id),
                  ),
                ),
              );
            },
          ),
        );
      },
    );
  }
}

class _PostList extends StatelessWidget {
  final List<TikTokPost> posts;
  final Future<void> Function() onRefresh;
  final VoidCallback? onNearEnd;
  final bool loadingMore;

  const _PostList({
    required this.posts,
    required this.onRefresh,
    this.onNearEnd,
    this.loadingMore = false,
  });

  @override
  Widget build(BuildContext context) {
    return RefreshIndicator(
      onRefresh: onRefresh,
      child: NotificationListener<ScrollNotification>(
        onNotification: (notification) {
          if (onNearEnd != null && notification.metrics.extentAfter < 800) {
            onNearEnd!();
          }
          return false;
        },
        child: ListView.builder(
          itemCount: posts.length + (loadingMore ? 1 : 0),
          itemBuilder: (context, index) {
            if (index >= posts.length) {
              return const Padding(
                padding: EdgeInsets.all(16),
                child: Center(child: CircularProgressIndicator()),
              );
            }
            return TikTokPostCard(post: posts[index]);
          },
        ),
      ),
    );
  }
}
