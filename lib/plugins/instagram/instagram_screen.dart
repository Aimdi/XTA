import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/instagram/instagram_client.dart';
import 'package:xta/plugins/instagram/instagram_discovery.dart';
import 'package:xta/plugins/instagram/instagram_errors.dart';
import 'package:xta/plugins/instagram/instagram_models.dart';
import 'package:xta/plugins/instagram/instagram_plugin.dart';
import 'package:xta/plugins/instagram/instagram_post_card.dart';
import 'package:xta/plugins/instagram/instagram_profile_screen.dart';
import 'package:xta/plugins/instagram/instagram_search_sheet.dart';
import 'package:xta/plugins/instagram/instagram_settings.dart';
import 'package:xta/plugins/instagram/instagram_store.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/plugins/plugin_lazy_tabs.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/ui/feed_list.dart';

/// Guest Instagram home: For you + Following + local accounts.
class InstagramScreen extends StatefulWidget {
  final ScrollController scrollController;

  const InstagramScreen({super.key, required this.scrollController});

  @override
  State<InstagramScreen> createState() => _InstagramScreenState();
}

class _InstagramScreenState extends State<InstagramScreen> {
  final _tabs = _InstagramTabStore();
  late final InstagramFollowingStore _following;
  late final InstagramForYouStore _forYou;

  @override
  void initState() {
    super.initState();
    _following = context.read<InstagramFollowingStore>();
    _forYou = InstagramForYouStore(context.read<InstagramClient>());

    WidgetsBinding.instance.addPostFrameCallback((_) async {
      if (!mounted) return;
      final follows = context.read<InstagramFollowsStore>();
      final likes = context.read<InstagramLikesStore>();
      final history = context.read<InstagramSearchHistoryStore>();
      if (follows.state.isEmpty) await follows.load();
      if (!mounted) return;
      if (likes.state.isEmpty) await likes.load();
      if (!mounted) return;
      if (history.state.isEmpty) await history.load();
      if (!mounted) return;
      await _forYou.refresh();
      if (!mounted) return;
      await _following.refresh();
    });
  }

  @override
  void dispose() {
    _tabs.destroy();
    _forYou.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);

    return Scaffold(
      primary: !PluginEmbedded.maybeOf(context),
      body: ScopedBuilder<_InstagramTabStore, int>(
        store: _tabs,
        onState: (context, tab) => Column(
          children: [
            PluginHomeChrome(
              accent: InstagramPlugin().brandColor,
              tabs: [
                PluginHomeTab(
                  label: l10n.plugin_instagram_tab_for_you,
                  icon: Icons.explore_outlined,
                  selected: tab == 0,
                  onTap: () => _tabs.select(0),
                ),
                PluginHomeTab(
                  label: l10n.plugin_instagram_tab_following,
                  icon: Icons.photo_library_outlined,
                  selected: tab == 1,
                  onTap: () => _tabs.select(1),
                ),
                PluginHomeTab(
                  label: l10n.plugin_instagram_tab_accounts,
                  icon: Icons.people_outline,
                  selected: tab == 2,
                  onTap: () {
                    _tabs.select(2);
                    context.read<InstagramFollowsStore>().load();
                  },
                ),
              ],
              actions: [
                IconButton(
                  tooltip: l10n.plugin_instagram_search,
                  icon: const Icon(Icons.search),
                  onPressed: _openSearch,
                ),
                IconButton(
                  tooltip: l10n.settings,
                  icon: const Icon(Icons.settings_outlined),
                  onPressed: () async {
                    await Navigator.push(
                      context,
                      MaterialPageRoute(
                        builder: (_) => const InstagramSettingsScreen(),
                      ),
                    );
                    if (context.mounted) await _refreshFeeds();
                  },
                ),
              ],
            ),
            const Divider(height: 1),
            Expanded(
              child: PluginLazyTabs(
                index: tab,
                children: [
                  (_) => _ForYouTab(
                    scrollController: widget.scrollController,
                    store: _forYou,
                    follows: context.read<InstagramFollowsStore>(),
                    onFindHandle: _openSearch,
                    onProfileClosed: _refreshFeeds,
                    onFollowingChanged: () => _following.refresh(force: true),
                  ),
                  (_) => _FollowingTab(
                    store: _following,
                    follows: context.read<InstagramFollowsStore>(),
                    onFindHandle: _openSearch,
                    onProfileClosed: _refreshFeeds,
                  ),
                  (_) => _AccountsTab(
                    onFindHandle: _openSearch,
                    onProfileClosed: _refreshFeeds,
                    onUnfollow: _refreshFeeds,
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
    await showInstagramSearchSheet(context);
    if (!mounted) return;
    await _refreshFeeds();
  }

  Future<void> _refreshFeeds() async {
    if (!mounted) return;
    await Future.wait([_forYou.refresh(), _following.refresh(force: true)]);
  }
}

class _InstagramTabStore extends Store<int> {
  _InstagramTabStore() : super(0);

  void select(int index) => update(index);
}

class _ForYouTab extends StatelessWidget {
  final ScrollController scrollController;
  final InstagramForYouStore store;
  final InstagramFollowsStore follows;
  final Future<void> Function() onFindHandle;
  final Future<void> Function() onProfileClosed;
  final Future<void> Function() onFollowingChanged;

  const _ForYouTab({
    required this.scrollController,
    required this.store,
    required this.follows,
    required this.onFindHandle,
    required this.onProfileClosed,
    required this.onFollowingChanged,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return ScopedBuilder<InstagramForYouStore, List<InstagramPost>>.transition(
      store: store,
      onLoading: (_) => store.state.isNotEmpty
          ? _forYouList(context, l10n, store.state)
          : const Center(child: CircularProgressIndicator()),
      onError: (_, error) => store.state.isNotEmpty
          ? _forYouList(context, l10n, store.state)
          : FullPageErrorWidget(
              error: error,
              stackTrace: null,
              prefix: instagramErrorMessage(l10n, error),
              onRetry: store.refresh,
            ),
      onState: (context, posts) {
        if (posts.isEmpty) {
          return _EmptyForYou(
            onRefresh: store.refresh,
            onFindHandle: onFindHandle,
          );
        }
        return _forYouList(context, l10n, posts);
      },
    );
  }

  Widget _forYouList(
    BuildContext context,
    L10n l10n,
    List<InstagramPost> posts,
  ) {
    return ScopedBuilder<InstagramFollowsStore, List<InstagramFollow>>(
      store: follows,
      onState: (context, _) {
        final people = peopleToFollowFromInstagram(
          posts: posts,
          alreadyFollows: follows.containsHandle,
        );
        final guest = !context.read<InstagramClient>().hasSession;
        final extras =
            (people.isEmpty ? 0 : 1) +
            (store.loadingMore ? 1 : 0) +
            (guest ? 1 : 0);
        return NotificationListener<ScrollNotification>(
          onNotification: (notification) {
            if (notification.metrics.extentAfter < 600) {
              store.loadMore();
            }
            return false;
          },
          child: RefreshIndicator(
            onRefresh: store.refresh,
            child: FeedListView(
              controller: scrollController,
              itemCount: posts.length + extras,
              itemBuilder: (context, index) {
                final peopleOffset = people.isEmpty ? 0 : 1;
                if (people.isNotEmpty && index == 0) {
                  return _DiscoverPeopleStrip(
                    people: people,
                    onOpen: (author) async {
                      await Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) =>
                              InstagramProfileScreen(handle: author.username),
                        ),
                      );
                      if (context.mounted) await onProfileClosed();
                    },
                    onFollow: (author) async {
                      await follows.followAuthor(author);
                      await onFollowingChanged();
                    },
                  );
                }
                final postIndex = index - peopleOffset;
                if (postIndex < posts.length) {
                  return InstagramPostCard(
                    post: posts[postIndex],
                    showFollow: true,
                    onFollowed: onFollowingChanged,
                    onProfileClosed: onProfileClosed,
                  );
                }
                if (store.loadingMore && index == posts.length + peopleOffset) {
                  return const Padding(
                    padding: EdgeInsets.all(16),
                    child: Center(child: CircularProgressIndicator()),
                  );
                }
                return Padding(
                  padding: const EdgeInsets.fromLTRB(24, 8, 24, 24),
                  child: Text(
                    l10n.plugin_instagram_for_you_guest_note,
                    textAlign: TextAlign.center,
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                      color: Theme.of(context).colorScheme.onSurfaceVariant,
                    ),
                  ),
                );
              },
            ),
          ),
        );
      },
    );
  }
}

class _DiscoverPeopleStrip extends StatelessWidget {
  final List<InstagramAuthor> people;
  final Future<void> Function(InstagramAuthor) onOpen;
  final Future<void> Function(InstagramAuthor) onFollow;

  const _DiscoverPeopleStrip({
    required this.people,
    required this.onOpen,
    required this.onFollow,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 0, 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            l10n.plugin_instagram_from_feed,
            style: theme.textTheme.titleSmall?.copyWith(
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 8),
          SizedBox(
            height: 48,
            child: ListView.separated(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.only(right: 16),
              itemCount: people.length,
              separatorBuilder: (_, _) => const SizedBox(width: 8),
              itemBuilder: (context, index) {
                final person = people[index];
                return Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    ActionChip(
                      avatar: InstagramAvatar(
                        url: person.avatarUrl,
                        seed: person.username,
                        name: person.displayName,
                        size: 20,
                      ),
                      label: Text('@${person.username}'),
                      onPressed: () => onOpen(person),
                    ),
                    TextButton(
                      onPressed: () => onFollow(person),
                      child: Text(l10n.plugin_instagram_follow),
                    ),
                  ],
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}

class _EmptyForYou extends StatelessWidget {
  final Future<void> Function() onRefresh;
  final Future<void> Function() onFindHandle;

  const _EmptyForYou({required this.onRefresh, required this.onFindHandle});

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
            Icons.explore_outlined,
            size: 52,
            color: theme.colorScheme.outline,
          ),
          const SizedBox(height: 16),
          Text(
            l10n.plugin_instagram_for_you_empty,
            textAlign: TextAlign.center,
            style: theme.textTheme.titleMedium?.copyWith(
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            l10n.plugin_instagram_for_you_guest_note,
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
              label: Text(l10n.plugin_instagram_search),
            ),
          ),
        ],
      ),
    );
  }
}

class _FollowingTab extends StatelessWidget {
  final InstagramFollowingStore store;
  final InstagramFollowsStore follows;
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
    return ScopedBuilder<
      InstagramFollowingStore,
      List<InstagramPost>
    >.transition(
      store: store,
      onLoading: (_) => store.state.isNotEmpty
          ? _PostList(
              posts: store.state,
              onRefresh: () => store.refresh(force: true),
              onProfileClosed: onProfileClosed,
            )
          : const Center(child: CircularProgressIndicator()),
      onError: (_, error) => store.state.isNotEmpty
          ? _PostList(
              posts: store.state,
              onRefresh: () => store.refresh(force: true),
              onProfileClosed: onProfileClosed,
            )
          : FullPageErrorWidget(
              error: error,
              stackTrace: null,
              prefix: instagramErrorMessage(l10n, error),
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
    return ScopedBuilder<InstagramFollowsStore, List<InstagramFollow>>(
      store: context.read<InstagramFollowsStore>(),
      onState: (context, follows) {
        if (follows.isEmpty) {
          return _EmptyFollowing(
            hasAccounts: false,
            onRefresh: context.read<InstagramFollowsStore>().load,
            onFindHandle: onFindHandle,
          );
        }
        return RefreshIndicator(
          onRefresh: () => context.read<InstagramFollowsStore>().load(),
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
                  leading: InstagramAvatar(
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
                              L10n.of(context).plugin_instagram_unfollow,
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
                        builder: (_) =>
                            InstagramProfileScreen(handle: follow.id),
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

  Future<bool> _unfollow(BuildContext context, String handle) async {
    final followsStore = context.read<InstagramFollowsStore>();
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
        content: Text(l10n.plugin_instagram_unfollow_confirm(handle)),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text(l10n.cancel),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: Text(l10n.plugin_instagram_unfollow),
          ),
        ],
      ),
    );
  }
}

class _PostList extends StatelessWidget {
  final List<InstagramPost> posts;
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
      child: FeedListView(
        itemCount: posts.length,
        itemBuilder: (context, index) {
          return InstagramPostCard(
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
            Icons.photo_library_outlined,
            size: 52,
            color: theme.colorScheme.outline,
          ),
          const SizedBox(height: 16),
          Text(
            hasAccounts
                ? l10n.plugin_instagram_no_posts
                : l10n.plugin_instagram_no_accounts,
            textAlign: TextAlign.center,
            style: theme.textTheme.titleMedium?.copyWith(
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            l10n.plugin_instagram_empty_cta,
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
              label: Text(
                hasAccounts ? l10n.retry : l10n.plugin_instagram_search,
              ),
            ),
          ),
          if (hasAccounts)
            Center(
              child: TextButton.icon(
                onPressed: onFindHandle,
                icon: const Icon(Icons.search),
                label: Text(l10n.plugin_instagram_search),
              ),
            ),
        ],
      ),
    );
  }
}
