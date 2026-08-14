import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/plugins/plugin_lazy_tabs.dart';
import 'package:xta/plugins/tiktok/tiktok_errors.dart';
import 'package:xta/plugins/tiktok/tiktok_plugin.dart';
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

class _TikTokScreenState extends State<TikTokScreen> {
  final _tabs = _TikTokTabStore();
  late final TikTokFollowingStore _following;

  @override
  void initState() {
    super.initState();
    _following = context.read<TikTokFollowingStore>();

    WidgetsBinding.instance.addPostFrameCallback((_) async {
      if (!mounted) return;
      final follows = context.read<TikTokFollowsStore>();
      final likes = context.read<TikTokLikesStore>();
      final history = context.read<TikTokSearchHistoryStore>();
      if (follows.state.isEmpty) await follows.load();
      if (!mounted) return;
      if (likes.state.isEmpty) await likes.load();
      if (!mounted) return;
      if (history.state.isEmpty) await history.load();
      if (!mounted) return;
      await _following.refresh();
    });
  }

  @override
  void dispose() {
    _tabs.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);

    return Scaffold(
      primary: !PluginEmbedded.maybeOf(context),
      body: ScopedBuilder<_TikTokTabStore, int>(
        store: _tabs,
        onState: (context, tab) => Column(
          children: [
            PluginHomeChrome(
              accent: TikTokPlugin().brandColor,
              tabs: [
                PluginHomeTab(
                  label: l10n.plugin_tiktok_tab_following,
                  icon: Icons.music_video_outlined,
                  selected: tab == 0,
                  onTap: () => _tabs.select(0),
                ),
                PluginHomeTab(
                  label: l10n.plugin_tiktok_tab_accounts,
                  icon: Icons.people_outline,
                  selected: tab == 1,
                  onTap: () {
                    _tabs.select(1);
                    context.read<TikTokFollowsStore>().load();
                  },
                ),
              ],
              actions: [
                IconButton(
                  tooltip: l10n.plugin_tiktok_search,
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
            ),
            const Divider(height: 1),
            Expanded(
              child: PluginLazyTabs(
                index: tab,
                children: [
                  (_) => _FollowingTab(
                    scrollController: widget.scrollController,
                    store: _following,
                    follows: context.read<TikTokFollowsStore>(),
                    onFindHandle: _openSearch,
                    onProfileClosed: _refreshFollowing,
                  ),
                  (_) => _AccountsTab(
                    onFindHandle: _openSearch,
                    onProfileClosed: _refreshFollowing,
                    onUnfollow: _refreshFollowing,
                  ),
                ],
              ),
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

class _TikTokTabStore extends Store<int> {
  _TikTokTabStore() : super(0);

  void select(int index) => update(index);
}

class _FollowingTab extends StatelessWidget {
  final ScrollController scrollController;
  final TikTokFollowingStore store;
  final TikTokFollowsStore follows;
  final Future<void> Function() onFindHandle;
  final Future<void> Function() onProfileClosed;

  const _FollowingTab({
    required this.scrollController,
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
              scrollController: scrollController,
              posts: store.state,
              onRefresh: () => store.refresh(force: true),
              onProfileClosed: onProfileClosed,
            )
          : const Center(child: CircularProgressIndicator()),
      onError: (_, error) => store.state.isNotEmpty
          ? _PostList(
              scrollController: scrollController,
              posts: store.state,
              onRefresh: () => store.refresh(force: true),
              onProfileClosed: onProfileClosed,
            )
          : FullPageErrorWidget(
              error: error,
              stackTrace: null,
              prefix: tiktokErrorMessage(l10n, error),
              onRetry: () => store.refresh(force: true),
            ),
      onState: (context, posts) {
        if (posts.isEmpty) {
          return _EmptyFollowing(
            hasAccounts: follows.state.isNotEmpty,
            onRefresh: () => store.refresh(force: true),
            onFindHandle: onFindHandle,
          );
        }
        return _PostList(
          scrollController: scrollController,
          posts: posts,
          onRefresh: () => store.refresh(force: true),
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
                confirmDismiss: (_) => _unfollow(context, follow.id),
                onDismissed: (_) {},
                background: Container(
                  color: Theme.of(context).colorScheme.error,
                  alignment: Alignment.centerRight,
                  padding: const EdgeInsets.symmetric(horizontal: 24),
                  child: Icon(
                    Icons.person_remove_outlined,
                    color: Theme.of(context).colorScheme.onError,
                  ),
                ),
                child: ListTile(
                  contentPadding: const EdgeInsets.symmetric(
                    horizontal: 16,
                    vertical: 4,
                  ),
                  leading: TikTokAvatar(
                    url: follow.avatarUrl,
                    seed: follow.id,
                    name: follow.name,
                    size: 48,
                  ),
                  title: Text(
                    follow.name,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontWeight: FontWeight.w600),
                  ),
                  subtitle: Text('@${follow.id}'),
                  trailing: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      PopupMenuButton<String>(
                        onSelected: (value) async {
                          if (value == 'unfollow') {
                            await _unfollow(context, follow.id);
                          }
                        },
                        itemBuilder: (context) => [
                          PopupMenuItem(
                            value: 'unfollow',
                            child: Text(
                              L10n.of(context).plugin_tiktok_unfollow,
                            ),
                          ),
                        ],
                      ),
                      Icon(
                        Icons.chevron_right,
                        color: Theme.of(context).colorScheme.onSurfaceVariant,
                      ),
                    ],
                  ),
                  onTap: () async {
                    await Navigator.push(
                      context,
                      MaterialPageRoute(
                        builder: (_) => TikTokProfileScreen(handle: follow.id),
                      ),
                    );
                    if (!context.mounted) return;
                    await onProfileClosed();
                    if (!context.mounted) return;
                  },
                ),
              );
            },
          ),
        );
      },
    );
  }

  Future<bool> _unfollow(BuildContext context, String handle) async {
    final followsStore = context.read<TikTokFollowsStore>();
    final confirmed = await _confirmUnfollow(context, handle);
    if (confirmed != true) return false;
    await followsStore.unfollow(handle);
    if (context.mounted) await onUnfollow();
    return true;
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
  final ScrollController? scrollController;
  final List<TikTokPost> posts;
  final Future<void> Function() onRefresh;
  final Future<void> Function()? onProfileClosed;

  const _PostList({
    this.scrollController,
    required this.posts,
    required this.onRefresh,
    this.onProfileClosed,
  });

  @override
  Widget build(BuildContext context) {
    return RefreshIndicator(
      onRefresh: onRefresh,
      child: ListView.builder(
        controller: scrollController,
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
              onPressed: hasAccounts ? onRefresh : onFindHandle,
              icon: Icon(hasAccounts ? Icons.refresh : Icons.search),
              label: Text(hasAccounts ? l10n.retry : l10n.plugin_tiktok_search),
            ),
          ),
          if (hasAccounts)
            Center(
              child: TextButton.icon(
                onPressed: onFindHandle,
                icon: const Icon(Icons.search),
                label: Text(l10n.plugin_tiktok_search),
              ),
            ),
        ],
      ),
    );
  }
}
