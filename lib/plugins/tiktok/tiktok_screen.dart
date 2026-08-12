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
      if (!mounted) return;
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
                onPressed: _openSearch,
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
            _FollowingTab(
              store: _following,
              follows: context.read<TikTokFollowsStore>(),
              onFindHandle: _openSearch,
              onProfileClosed: _refreshFollowing,
            ),
            _AccountsTab(
              onFindHandle: _openSearch,
              onProfileClosed: _refreshFollowing,
              onUnfollow: _refreshFollowing,
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _openSearch() async {
    await showTikTokSearchSheet(context);
    if (!mounted) return;
    await _following.refresh(force: true);
    if (!mounted) return;
  }

  Future<void> _refreshFollowing() async {
    if (!mounted) return;
    await _following.refresh(force: true);
    if (!mounted) return;
  }
}

class _FollowingTab extends StatelessWidget {
  final TikTokFollowingStore store;
  final TikTokFollowsStore follows;
  final Future<void> Function() onFindHandle;
  final Future<void> Function() onProfileClosed;

  const _FollowingTab({
    required this.store,
    required this.follows,
    required this.onFindHandle,
    required this.onProfileClosed,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return ScopedBuilder<TikTokFollowingStore, List<TikTokPost>>.transition(
      store: store,
      onLoading: (_) => store.state.isNotEmpty
          ? _PostList(
              posts: store.state,
              onRefresh: store.refresh,
              onProfileClosed: onProfileClosed,
            )
          : const Center(child: CircularProgressIndicator()),
      onError: (_, error) => store.state.isNotEmpty
          ? _PostList(
              posts: store.state,
              onRefresh: store.refresh,
              onProfileClosed: onProfileClosed,
            )
          : FullPageErrorWidget(
              error: error,
              stackTrace: null,
              prefix: tiktokErrorMessage(l10n, error),
              onRetry: store.refresh,
            ),
      onState: (context, posts) {
        if (posts.isEmpty) {
          return _EmptyFollowing(
            hasAccounts: follows.state.isNotEmpty,
            onRefresh: store.refresh,
            onFindHandle: onFindHandle,
          );
        }
        return _PostList(
          posts: posts,
          onRefresh: store.refresh,
          onProfileClosed: onProfileClosed,
        );
      },
    );
  }
}

class _AccountsTab extends StatelessWidget {
  final Future<void> Function() onFindHandle;
  final Future<void> Function() onProfileClosed;
  final Future<void> Function() onUnfollow;

  const _AccountsTab({
    required this.onFindHandle,
    required this.onProfileClosed,
    required this.onUnfollow,
  });

  @override
  Widget build(BuildContext context) {
    return ScopedBuilder<TikTokFollowsStore, List<TikTokFollow>>(
      store: context.read<TikTokFollowsStore>(),
      onState: (context, follows) {
        if (follows.isEmpty) {
          return _EmptyFollowing(
            hasAccounts: false,
            onRefresh: context.read<TikTokFollowsStore>().load,
            onFindHandle: onFindHandle,
          );
        }
        return RefreshIndicator(
          onRefresh: () => context.read<TikTokFollowsStore>().load(),
          child: ListView.builder(
            itemCount: follows.length,
            itemBuilder: (context, index) {
              final follow = follows[index];
              return Dismissible(
                key: ValueKey(follow.id),
                direction: DismissDirection.endToStart,
                confirmDismiss: (_) => _confirmUnfollow(context, follow.id),
                onDismissed: (_) async {
                  await context.read<TikTokFollowsStore>().unfollow(follow.id);
                  if (!context.mounted) return;
                  await onUnfollow();
                },
                background: Container(
                  color: Theme.of(context).colorScheme.error,
                  alignment: Alignment.centerRight,
                  padding: const EdgeInsets.symmetric(horizontal: 24),
                  child: const Icon(Icons.person_remove_outlined),
                ),
                child: ListTile(
                  leading: TikTokAvatar(
                    url: follow.avatarUrl,
                    seed: follow.id,
                    name: follow.name,
                  ),
                  title: Text(follow.name),
                  subtitle: Text('@${follow.id}'),
                  onTap: () async {
                    await Navigator.push(
                      context,
                      MaterialPageRoute(
                        builder: (_) => TikTokProfileScreen(handle: follow.id),
                      ),
                    );
                    if (!context.mounted) return;
                    await onProfileClosed();
                  },
                ),
              );
            },
          ),
        );
      },
    );
  }

  Future<bool?> _confirmUnfollow(BuildContext context, String handle) {
    final l10n = L10n.of(context);
    return showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        content: Text(l10n.plugin_tiktok_unfollow_confirm(handle)),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text(l10n.cancel),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: Text(l10n.plugin_tiktok_unfollow),
          ),
        ],
      ),
    );
  }
}

class _PostList extends StatelessWidget {
  final List<TikTokPost> posts;
  final Future<void> Function() onRefresh;
  final Future<void> Function()? onProfileClosed;

  const _PostList({
    required this.posts,
    required this.onRefresh,
    this.onProfileClosed,
  });

  @override
  Widget build(BuildContext context) {
    return RefreshIndicator(
      onRefresh: onRefresh,
      child: ListView.builder(
        itemCount: posts.length,
        itemBuilder: (context, index) {
          return TikTokPostCard(
            post: posts[index],
            onProfileClosed: onProfileClosed,
          );
        },
      ),
    );
  }
}

class _EmptyFollowing extends StatelessWidget {
  final bool hasAccounts;
  final Future<void> Function() onRefresh;
  final Future<void> Function() onFindHandle;

  const _EmptyFollowing({
    required this.hasAccounts,
    required this.onRefresh,
    required this.onFindHandle,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = L10n.of(context);
    return RefreshIndicator(
      onRefresh: onRefresh,
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(32, 72, 32, 32),
        children: [
          Icon(
            Icons.music_video_outlined,
            size: 52,
            color: theme.colorScheme.outline,
          ),
          const SizedBox(height: 16),
          Text(
            hasAccounts
                ? l10n.plugin_tiktok_no_posts
                : l10n.plugin_tiktok_no_accounts,
            textAlign: TextAlign.center,
            style: theme.textTheme.titleMedium?.copyWith(
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            l10n.plugin_tiktok_empty_cta,
            textAlign: TextAlign.center,
            style: theme.textTheme.bodyMedium?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
          const SizedBox(height: 24),
          Center(
            child: FilledButton.icon(
              onPressed: onFindHandle,
              icon: const Icon(Icons.search),
              label: Text(l10n.plugin_tiktok_find_handle),
            ),
          ),
        ],
      ),
    );
  }
}
