import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/mastodon/mastodon_client.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_plugin.dart';
import 'package:xta/plugins/mastodon/mastodon_post_card.dart';
import 'package:xta/plugins/mastodon/mastodon_profile_screen.dart';
import 'package:xta/plugins/mastodon/mastodon_search_sheet.dart';
import 'package:xta/plugins/mastodon/mastodon_store.dart';
import 'package:xta/plugins/plugin_feed_insets.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/plugins/plugin_lazy_tabs.dart';
import 'package:xta/ui/empty_pane.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/ui/feed_list.dart';
import 'package:xta/plugins/plugin_feed_skeleton.dart';

/// The Mastodon tab: Explore / Local / Federated / Following, like Tusky.
class MastodonScreen extends StatefulWidget {
  final ScrollController scrollController;

  const MastodonScreen({super.key, required this.scrollController});

  @override
  State<MastodonScreen> createState() => _MastodonScreenState();
}

class _MastodonTabStore extends Store<int> {
  _MastodonTabStore() : super(0);

  void select(int index) => update(index);
}

class _MastodonScreenState extends State<MastodonScreen> {
  final _tabs = _MastodonTabStore();

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        // Explore only. Following used to start the same frame and fan out
        // every followed acct across several instances — that is what made
        // opening the tab stall the rest of the app.
        context.read<MastodonExploreStore>().refresh();
      }
    });
  }

  @override
  void dispose() {
    _tabs.destroy();
    super.dispose();
  }

  Future<void> _lookUpProfile() async {
    await showMastodonSearchSheet(context);
    if (mounted) {
      await context.read<MastodonFeedStore>().refresh();
    }
  }

  Future<void> _addAccount() async {
    final acct = await showMastodonAddAccountDialog(context);
    if (acct == null || !mounted) {
      return;
    }

    final messenger = ScaffoldMessenger.of(context);
    final prefs = PrefService.of(context, listen: false);
    final client = context.read<MastodonClient>();
    final accounts = context.read<MastodonAccountsStore>();
    final l10n = L10n.of(context);

    try {
      // No instance required any more: the acct's own instance is asked first,
      // then the reader's, then the built-in defaults.
      final candidates = mastodonInstanceCandidates(
        acct,
        configured: mastodonConfiguredInstances(prefs),
      );
      final profile = await client.lookupAnywhere(candidates, acct);
      await accounts.add(profile.toAccount());
    } catch (e) {
      if (mounted) {
        messenger.showSnackBar(
          SnackBar(content: Text(mastodonErrorMessage(l10n, e))),
        );
      }
      return;
    }

    if (mounted) {
      await context.read<MastodonFeedStore>().refresh();
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);

    return Scaffold(
      primary: !PluginEmbedded.maybeOf(context),
      body: ScopedBuilder<_MastodonTabStore, int>(
        store: _tabs,
        onState: (context, tab) => Column(
          children: [
            PluginHomeChrome(
              accent: MastodonPlugin().brandColor,
              actions: [
                IconButton(
                  icon: const Icon(Icons.search),
                  tooltip: l10n.plugin_mastodon_search,
                  onPressed: _lookUpProfile,
                ),
                IconButton(
                  icon: const Icon(Icons.person_add_alt),
                  tooltip: l10n.plugin_mastodon_add,
                  onPressed: _addAccount,
                ),
              ],
            ),
            _MastodonTabs(selected: tab, onSelected: _onTab),
            const Divider(height: 1),
            Expanded(
              child: PluginLazyTabs(
                index: tab,
                children: [
                  (_) =>
                      _ExplorePane(scrollController: widget.scrollController),
                  (_) => _PublicPane(
                    store: context.read<MastodonLocalStore>(),
                    emptyIcon: Icons.home_outlined,
                    scrollController: widget.scrollController,
                  ),
                  (_) => _PublicPane(
                    store: context.read<MastodonFederatedStore>(),
                    emptyIcon: Icons.public,
                    scrollController: widget.scrollController,
                  ),
                  (_) =>
                      _FollowingPane(scrollController: widget.scrollController),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  void _onTab(int index) {
    _tabs.select(index);
    if (!mounted) return;
    if (index == 1) {
      final store = context.read<MastodonLocalStore>();
      if (store.state.isEmpty) unawaited(store.refresh());
    }
    if (index == 2) {
      final store = context.read<MastodonFederatedStore>();
      if (store.state.isEmpty) unawaited(store.refresh());
    }
    if (index == 3) {
      final feed = context.read<MastodonFeedStore>();
      if (feed.state.isEmpty) unawaited(feed.refresh());
    }
  }
}

class _MastodonTabs extends StatelessWidget {
  final int selected;
  final ValueChanged<int> onSelected;

  const _MastodonTabs({required this.selected, required this.onSelected});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Material(
      color: Theme.of(context).scaffoldBackgroundColor,
      child: Row(
        children: [
          _Tab(
            label: l10n.plugin_mastodon_tab_explore,
            icon: Icons.explore_outlined,
            selected: selected == 0,
            onTap: () => onSelected(0),
          ),
          _Tab(
            label: l10n.plugin_mastodon_tab_local,
            icon: Icons.home_outlined,
            selected: selected == 1,
            onTap: () => onSelected(1),
          ),
          _Tab(
            label: l10n.plugin_mastodon_tab_federated,
            icon: Icons.public,
            selected: selected == 2,
            onTap: () => onSelected(2),
          ),
          _Tab(
            label: l10n.plugin_mastodon_tab_following,
            icon: Icons.people_outline,
            selected: selected == 3,
            onTap: () => onSelected(3),
          ),
        ],
      ),
    );
  }
}

class _Tab extends StatelessWidget {
  final String label;
  final IconData icon;
  final bool selected;
  final VoidCallback onTap;

  const _Tab({
    required this.label,
    required this.icon,
    required this.selected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final color = selected
        ? theme.colorScheme.primary
        : theme.colorScheme.onSurfaceVariant;
    return Expanded(
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 10),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(icon, size: 20, color: color),
              const SizedBox(height: 2),
              Text(
                label,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.labelSmall?.copyWith(
                  color: color,
                  fontWeight: selected ? FontWeight.w700 : FontWeight.w500,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _ExplorePane extends StatelessWidget {
  final ScrollController scrollController;

  const _ExplorePane({required this.scrollController});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final store = context.read<MastodonExploreStore>();
    return ScopedBuilder<MastodonExploreStore, MastodonExplorePage>(
      store: store,
      onLoading: (_) =>
          store.state.posts.isNotEmpty || store.state.tags.isNotEmpty
          ? _exploreBody(context, l10n, store.state)
          : const PluginFeedSkeleton(),
      onError: (_, error) => store.state.posts.isNotEmpty
          ? _exploreBody(context, l10n, store.state)
          : Padding(
              padding: const EdgeInsets.all(24),
              child: FullPageErrorWidget(
                error: error,
                stackTrace: null,
                prefix: mastodonErrorMessage(l10n, error ?? Exception()),
                onRetry: store.refresh,
              ),
            ),
      onState: (context, page) => _exploreBody(context, l10n, page),
    );
  }

  Widget _exploreBody(
    BuildContext context,
    L10n l10n,
    MastodonExplorePage page,
  ) {
    if (page.tags.isEmpty && page.posts.isEmpty) {
      return EmptyPane(
        icon: Icons.explore_outlined,
        message: l10n.plugin_mastodon_empty_public,
        scrollController: scrollController,
        onRefresh: context.read<MastodonExploreStore>().refresh,
      );
    }
    return RefreshIndicator(
      onRefresh: context.read<MastodonExploreStore>().refresh,
      child: FeedListView(
        controller: pluginInnerScrollController(context, scrollController),
        padding: pluginFeedPadding(context),
        itemCount: page.posts.length + (page.tags.isEmpty ? 0 : 1),
        itemBuilder: (context, index) {
          if (page.tags.isNotEmpty && index == 0) {
            return _TrendingTags(tags: page.tags);
          }
          final post = page.posts[index - (page.tags.isEmpty ? 0 : 1)];
          return MastodonPostCard(
            key: ValueKey(post.id),
            post: post,
            showSourceBadge: false,
          );
        },
      ),
    );
  }
}

class _TrendingTags extends StatelessWidget {
  final List<MastodonTrendingTag> tags;

  const _TrendingTags({required this.tags});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            l10n.plugin_mastodon_trending,
            style: Theme.of(
              context,
            ).textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              for (final tag in tags.take(12))
                ActionChip(
                  label: Text('#${tag.name}'),
                  onPressed: () => Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (_) => MastodonTagScreen(tag: tag.name),
                    ),
                  ),
                ),
            ],
          ),
        ],
      ),
    );
  }
}

class _PublicPane extends StatelessWidget {
  final MastodonPublicFeedStore store;
  final IconData emptyIcon;
  final ScrollController scrollController;

  const _PublicPane({
    required this.store,
    required this.emptyIcon,
    required this.scrollController,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return ScopedBuilder<MastodonPublicFeedStore, List<MastodonPost>>(
      store: store,
      onLoading: (_) => store.state.isNotEmpty
          ? _list(context, store.state, store)
          : const PluginFeedSkeleton(),
      onError: (_, error) => store.state.isNotEmpty
          ? _list(context, store.state, store)
          : Padding(
              padding: const EdgeInsets.all(24),
              child: FullPageErrorWidget(
                error: error,
                stackTrace: null,
                prefix: mastodonErrorMessage(l10n, error ?? Exception()),
                onRetry: store.refresh,
              ),
            ),
      onState: (context, posts) {
        if (posts.isEmpty) {
          return EmptyPane(
            icon: emptyIcon,
            message: l10n.plugin_mastodon_empty_public,
            scrollController: scrollController,
            onRefresh: store.refresh,
          );
        }
        return _list(context, posts, store);
      },
    );
  }

  Widget _list(
    BuildContext context,
    List<MastodonPost> posts,
    MastodonPublicFeedStore store,
  ) {
    return NotificationListener<ScrollNotification>(
      onNotification: (notification) {
        if (notification.metrics.pixels >
            notification.metrics.maxScrollExtent - 1200) {
          store.loadMore();
        }
        return false;
      },
      child: RefreshIndicator(
        onRefresh: store.refresh,
        child: FeedListView(
          controller: pluginInnerScrollController(context, scrollController),
          padding: pluginFeedPadding(context),
          itemCount: posts.length + (store.loadingMore ? 1 : 0),
          itemBuilder: (context, index) {
            if (index >= posts.length) {
              return const Padding(
                padding: EdgeInsets.all(16),
                child: Center(child: CircularProgressIndicator()),
              );
            }
            return MastodonPostCard(
              key: ValueKey(posts[index].id),
              post: posts[index],
              showSourceBadge: false,
            );
          },
        ),
      ),
    );
  }
}

class _FollowingPane extends StatelessWidget {
  final ScrollController scrollController;

  const _FollowingPane({required this.scrollController});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final feed = context.read<MastodonFeedStore>();
    return ScopedBuilder<MastodonFeedStore, List<MastodonPost>>(
      store: feed,
      onLoading: (_) => feed.state.isNotEmpty
          ? _followingList(context, l10n, feed.state)
          : const PluginFeedSkeleton(),
      onError: (context, error) => feed.state.isNotEmpty
          ? _followingList(context, l10n, feed.state)
          : Padding(
              padding: const EdgeInsets.all(24),
              child: FullPageErrorWidget(
                error: error,
                stackTrace: null,
                prefix: mastodonErrorMessage(l10n, error ?? Exception()),
                onRetry: () => feed.refresh(),
              ),
            ),
      onState: (context, posts) => _followingList(context, l10n, posts),
    );
  }

  Widget _followingList(
    BuildContext context,
    L10n l10n,
    List<MastodonPost> posts,
  ) {
    if (posts.isEmpty) {
      return ScopedBuilder<MastodonAccountsStore, List<MastodonAccount>>(
        store: context.read<MastodonAccountsStore>(),
        onState: (context, accounts) => EmptyPane(
          icon: Icons.public,
          message: accounts.isEmpty
              ? l10n.plugin_mastodon_empty
              : l10n.plugin_mastodon_no_posts,
          scrollController: scrollController,
          onRefresh: () =>
              context.read<MastodonFeedStore>().refresh(force: true),
          action: accounts.isEmpty
              ? FilledButton.icon(
                  onPressed: () => showMastodonSearchSheet(context),
                  icon: const Icon(Icons.explore_outlined),
                  label: Text(l10n.plugin_mastodon_discover),
                )
              : FilledButton.icon(
                  onPressed: () =>
                      context.read<MastodonFeedStore>().refresh(force: true),
                  icon: const Icon(Icons.refresh),
                  label: Text(l10n.retry),
                ),
        ),
      );
    }
    return RefreshIndicator(
      onRefresh: () => context.read<MastodonFeedStore>().refresh(force: true),
      child: FeedListView(
        controller: scrollController,
        padding: pluginFeedPadding(context),
        itemCount: posts.length,
        itemBuilder: (context, index) => MastodonPostCard(
          key: ValueKey(posts[index].id),
          post: posts[index],
          showSourceBadge: false,
        ),
      ),
    );
  }
}

Future<String?> showMastodonAddAccountDialog(
  BuildContext context, {
  bool lookup = false,
}) {
  return showDialog<String>(
    context: context,
    builder: (_) => _MastodonAddAccountDialog(lookup: lookup),
  );
}

class _MastodonAddAccountDialog extends StatefulWidget {
  final bool lookup;

  const _MastodonAddAccountDialog({required this.lookup});

  @override
  State<_MastodonAddAccountDialog> createState() =>
      _MastodonAddAccountDialogState();
}

class _MastodonAddAccountDialogState extends State<_MastodonAddAccountDialog> {
  late final TextEditingController _controller;
  String? _error;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController();
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _submit() {
    final l10n = L10n.of(context);
    final acct = normaliseMastodonAcct(_controller.text);
    if (acct == null) {
      setState(() => _error = l10n.plugin_mastodon_invalid_handle);
    } else {
      Navigator.pop(context, acct);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return AlertDialog(
      title: Text(
        widget.lookup ? l10n.plugin_mastodon_lookup : l10n.plugin_mastodon_add,
      ),
      content: TextField(
        controller: _controller,
        autofocus: true,
        decoration: InputDecoration(
          hintText: l10n.plugin_mastodon_handle_hint,
          errorText: _error,
        ),
        onSubmitted: (_) => _submit(),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: Text(l10n.cancel),
        ),
        TextButton(onPressed: _submit, child: Text(l10n.ok)),
      ],
    );
  }
}
