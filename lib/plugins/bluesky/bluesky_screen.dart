import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:extended_image/extended_image.dart';
import 'package:xta/plugins/plugin_feed_insets.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/plugins/plugin_lazy_tabs.dart';
import 'package:xta/plugins/bluesky/bluesky_client.dart';
import 'package:xta/plugins/bluesky/bluesky_discovery.dart';
import 'package:xta/plugins/bluesky/bluesky_feed.dart';
import 'package:xta/plugins/bluesky/bluesky_feeds_pane.dart';
import 'package:xta/plugins/bluesky/bluesky_plugin.dart';
import 'package:xta/plugins/bluesky/bluesky_import_follows_screen.dart';
import 'package:xta/plugins/bluesky/bluesky_import_list_screen.dart';
import 'package:xta/plugins/bluesky/bluesky_import_starter_screen.dart';
import 'package:xta/plugins/bluesky/bluesky_likes_store.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/bluesky/bluesky_post_card.dart';
import 'package:xta/plugins/bluesky/bluesky_profile_screen.dart';
import 'package:xta/plugins/bluesky/bluesky_search_sheet.dart';
import 'package:xta/plugins/bluesky/bluesky_store.dart';
import 'package:xta/plugins/plugin_feed_people.dart';
import 'package:xta/subscriptions/widgets/fallback_avatar.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/ui/empty_pane.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/ui/feed_list.dart';
import 'package:xta/plugins/plugin_feed_skeleton.dart';

/// Home remounts used to poll the AppView whenever the ten-minute TTL expired.
/// Only a pull-to-refresh, or the first empty paint, should hit the network.
bool blueskyHomeShouldFetch({required bool force, required bool feedEmpty}) =>
    force || feedEmpty;

/// The Bluesky tab: local follows feed, plus a device-only Liked library.
class BlueskyScreen extends StatefulWidget {
  final ScrollController scrollController;

  const BlueskyScreen({super.key, required this.scrollController});

  @override
  State<BlueskyScreen> createState() => _BlueskyScreenState();
}

class _BlueskyScreenState extends State<BlueskyScreen>
    with AutomaticKeepAliveClientMixin {
  final _shell = _BlueskyShellStore();
  final _algoScrollController = ScrollController();
  final _listsScrollController = ScrollController();
  final _likedScrollController = ScrollController();

  @override
  bool get wantKeepAlive => true;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        _loadHome();
      }
    });
  }

  @override
  void dispose() {
    _algoScrollController.dispose();
    _listsScrollController.dispose();
    _likedScrollController.dispose();
    _shell.destroy();
    super.dispose();
  }

  Future<void> _loadHome({bool force = false}) async {
    final accounts = context.read<BlueskyAccountsStore>();
    final likes = context.read<BlueskyLikesStore>();
    final feed = context.read<BlueskyFeedStore>();
    // Startup already hydrated these when the plugin was on. A remount from
    // the home strip should not hit SQLite again just to paint the same list.
    await Future.wait([
      if (accounts.state.isEmpty) accounts.load(),
      if (likes.state.isEmpty) likes.load(),
    ]);
    // Remounts used to poll whenever the ten-minute TTL expired, which
    // jumped the list and flashed a "N more accounts" count. Only a
    // pull-to-refresh, or the first empty paint, should hit the AppView.
    if (blueskyHomeShouldFetch(force: force, feedEmpty: feed.state.isEmpty)) {
      await feed.refresh(force: force);
    }
  }

  Future<void> _searchPeople() async {
    await showBlueskySearchSheet(context);
    if (mounted) {
      await context.read<BlueskyFeedStore>().refresh();
    }
  }

  Future<void> _addAccount() async {
    final actor = await showBlueskyAddAccountDialog(context);
    if (actor == null || !mounted) {
      return;
    }

    final messenger = ScaffoldMessenger.of(context);
    final client = context.read<BlueskyClient>();
    final accounts = context.read<BlueskyAccountsStore>();
    final l10n = L10n.of(context);

    final subscriptions = context.read<SubscriptionsModel>();
    final feed = context.read<BlueskyFeedStore>();

    try {
      final profile = await client.getProfile(actor);
      await accounts.add(profile.toAccount());
      await subscriptions.reloadSubscriptions();
    } catch (e) {
      if (mounted) {
        messenger.showSnackBar(
          SnackBar(content: Text(blueskyErrorMessage(l10n, e))),
        );
      }
      return;
    }

    if (mounted) {
      await feed.refresh();
    }
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    final l10n = L10n.of(context);

    return Scaffold(
      primary: !PluginEmbedded.maybeOf(context),
      body: ScopedBuilder<_BlueskyShellStore, int>(
        store: _shell,
        onState: (context, tab) => Column(
          children: [
            PluginHomeChrome(
              accent: BlueskyPlugin().brandColor,
              tabs: [
                PluginHomeTab(
                  label: l10n.plugin_bluesky_following,
                  icon: Icons.home_outlined,
                  selected: tab == 0,
                  onTap: () => _shell.select(0),
                ),
                PluginHomeTab(
                  label: l10n.plugin_bluesky_discover,
                  icon: Icons.auto_awesome_outlined,
                  selected: tab == 1,
                  onTap: () => _shell.select(1),
                ),
                PluginHomeTab(
                  label: l10n.plugin_bluesky_lists,
                  icon: Icons.list_alt_outlined,
                  selected: tab == 2,
                  onTap: () => _shell.select(2),
                ),
                PluginHomeTab(
                  label: l10n.plugin_bluesky_liked,
                  icon: Icons.favorite_border,
                  selected: tab == 3,
                  onTap: () => _shell.select(3),
                ),
              ],
              actions: [
                IconButton(
                  icon: const Icon(Icons.search),
                  tooltip: l10n.plugin_bluesky_search,
                  onPressed: _searchPeople,
                ),
                IconButton(
                  icon: const Icon(Icons.person_add_alt),
                  tooltip: l10n.plugin_bluesky_add,
                  onPressed: _addAccount,
                ),
                PopupMenuButton<String>(
                  onSelected: (value) {
                    final page = switch (value) {
                      'following' => const BlueskyImportFollowsScreen(),
                      'list' => const BlueskyImportListScreen(),
                      'starter' => const BlueskyImportStarterPackScreen(),
                      _ => null,
                    };
                    if (page != null) {
                      Navigator.push(
                        context,
                        MaterialPageRoute(builder: (_) => page),
                      );
                    }
                  },
                  itemBuilder: (context) => [
                    PopupMenuItem(
                      value: 'following',
                      child: Text(l10n.plugin_bluesky_import_following),
                    ),
                    PopupMenuItem(
                      value: 'list',
                      child: Text(l10n.plugin_bluesky_import_list),
                    ),
                    PopupMenuItem(
                      value: 'starter',
                      child: Text(l10n.plugin_bluesky_import_starter),
                    ),
                  ],
                ),
              ],
            ),
            const Divider(height: 1),
            Expanded(
              child: PluginLazyTabs(
                index: tab,
                children: [
                  (_) => _HomePane(
                    scrollController: widget.scrollController,
                    onRefresh: () => _loadHome(force: true),
                  ),
                  (_) =>
                      BlueskyAlgoPane(scrollController: _algoScrollController),
                  (_) => BlueskyListsPane(
                    scrollController: _listsScrollController,
                  ),
                  (_) => _LikedPane(
                    scrollController: _likedScrollController,
                    likes: context.read<BlueskyLikesStore>(),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _BlueskyShellStore extends Store<int> {
  _BlueskyShellStore() : super(0);

  void select(int index) => update(index);
}

class _HomePane extends StatelessWidget {
  final ScrollController scrollController;
  final Future<void> Function() onRefresh;

  const _HomePane({required this.scrollController, required this.onRefresh});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);

    final feed = context.read<BlueskyFeedStore>();

    return ScopedBuilder<BlueskyFeedStore, List<BlueskyPost>>(
      store: feed,
      distinct: blueskyFeedDistinct,
      onLoading: (_) {
        if (feed.state.isNotEmpty) {
          return _feed(context, l10n, feed.state);
        }
        return const PluginFeedSkeleton();
      },
      onError: (context, error) {
        if (feed.state.isNotEmpty) {
          return _feed(context, l10n, feed.state);
        }
        return Padding(
          padding: const EdgeInsets.all(24),
          child: FullPageErrorWidget(
            error: error,
            stackTrace: null,
            prefix: blueskyErrorMessage(l10n, error ?? Exception()),
            onRetry: () => context.read<BlueskyFeedStore>().refresh(),
          ),
        );
      },
      onState: (context, posts) => _feed(context, l10n, posts),
    );
  }

  Widget _feed(BuildContext context, L10n l10n, List<BlueskyPost> posts) {
    if (posts.isEmpty) {
      // Scrollable and refreshable even when empty: with more follows than one
      // load's budget, an empty first batch is exactly when the reader needs
      // the pull.
      return ScopedBuilder<BlueskyAccountsStore, List<BlueskyAccount>>(
        store: context.read<BlueskyAccountsStore>(),
        onState: (context, accounts) {
          return EmptyPane(
            icon: Icons.cloud_outlined,
            message: accounts.isEmpty
                ? l10n.plugin_bluesky_empty
                : l10n.plugin_bluesky_no_posts,
            scrollController: scrollController,
            onRefresh: onRefresh,
            action: _emptyActions(context, l10n, accounts.isEmpty),
          );
        },
      );
    }

    return ScopedBuilder<BlueskyAccountsStore, List<BlueskyAccount>>(
      store: context.read<BlueskyAccountsStore>(),
      onState: (context, _) {
        final people = peopleToFollowFromBluesky(
          posts: posts,
          alreadyFollows: context.read<BlueskyAccountsStore>().follows,
        );
        final peopleOffset = people.isEmpty ? 0 : 1;
        return RefreshIndicator(
          onRefresh: onRefresh,
          child: FeedListView(
            key: const PageStorageKey<String>('bluesky-home-feed'),
            controller: pluginInnerScrollController(context, scrollController),
            padding: pluginFeedPadding(context),
            itemCount: posts.length + peopleOffset,
            itemBuilder: (context, index) {
              if (peopleOffset == 1 && index == 0) {
                return PluginFeedPeopleStrip(
                  title: l10n.plugin_bluesky_from_feed,
                  followLabel: l10n.plugin_bluesky_follow,
                  people: people,
                  avatar: (person) => _feedPersonAvatar(context, person),
                  onOpen: (person) => Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (_) =>
                          BlueskyProfileScreen(actor: person.handle),
                    ),
                  ),
                  onFollow: (person) =>
                      context.read<BlueskyAccountsStore>().add(
                        BlueskyAccount(
                          handle: person.handle,
                          name: person.name,
                          avatarUrl: person.avatarUrl,
                        ),
                      ),
                );
              }
              final post = posts[index - peopleOffset];
              return BlueskyPostCard(
                key: ValueKey(blueskyFeedRowKey(post)),
                post: post,
                showSourceBadge: false,
              );
            },
          ),
        );
      },
    );
  }

  Widget _emptyActions(BuildContext context, L10n l10n, bool noAccounts) {
    Future<void> discover() async {
      await showBlueskySearchSheet(context);
      if (context.mounted) {
        await context.read<BlueskyFeedStore>().refresh();
      }
    }

    return Column(
      children: [
        FilledButton.icon(
          onPressed: discover,
          icon: const Icon(Icons.explore_outlined),
          label: Text(l10n.plugin_bluesky_discover),
        ),
        if (noAccounts) ...[
          const SizedBox(height: 8),
          TextButton.icon(
            onPressed: () => Navigator.push(
              context,
              MaterialPageRoute(
                builder: (_) => const BlueskyImportFollowsScreen(),
              ),
            ),
            icon: const Icon(Icons.group_add_outlined),
            label: Text(l10n.plugin_bluesky_import_following),
          ),
          TextButton.icon(
            onPressed: () => Navigator.push(
              context,
              MaterialPageRoute(
                builder: (_) => const BlueskyImportStarterPackScreen(),
              ),
            ),
            icon: const Icon(Icons.auto_awesome_outlined),
            label: Text(l10n.plugin_bluesky_import_starter),
          ),
        ] else ...[
          const SizedBox(height: 8),
          TextButton.icon(
            onPressed: onRefresh,
            icon: const Icon(Icons.refresh),
            label: Text(l10n.retry),
          ),
        ],
      ],
    );
  }

  Widget _feedPersonAvatar(BuildContext context, PluginFeedPerson person) {
    final url = person.avatarUrl;
    if (url == null || url.isEmpty) {
      return FallbackAvatar(
        seed: person.handle,
        displayName: person.name,
        size: 20,
        accent: Theme.of(context).colorScheme.primary,
      );
    }
    return ClipOval(
      child: ExtendedImage.network(
        url,
        width: 20,
        height: 20,
        fit: BoxFit.cover,
        cacheWidth: (20 * MediaQuery.devicePixelRatioOf(context)).ceil(),
      ),
    );
  }
}

class _LikedPane extends StatelessWidget {
  final ScrollController scrollController;
  final BlueskyLikesStore likes;

  const _LikedPane({required this.scrollController, required this.likes});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return RefreshIndicator(
      onRefresh: likes.load,
      child: ScopedBuilder<BlueskyLikesStore, List<BlueskyPost>>(
        store: likes,
        onState: (context, posts) {
          if (posts.isEmpty) {
            return ListView(
              controller: pluginInnerScrollController(
                context,
                scrollController,
              ),
              padding: const EdgeInsets.fromLTRB(32, 72, 32, 32),
              children: [
                Icon(
                  Icons.favorite_border,
                  size: 52,
                  color: Theme.of(context).colorScheme.outline,
                ),
                const SizedBox(height: 16),
                Text(
                  l10n.plugin_bluesky_liked_empty,
                  textAlign: TextAlign.center,
                  style: Theme.of(context).textTheme.titleMedium,
                ),
              ],
            );
          }

          return FeedListView(
            controller: pluginInnerScrollController(context, scrollController),
            padding: pluginFeedPadding(context),
            itemCount: posts.length,
            itemBuilder: (context, index) => BlueskyPostCard(
              key: ValueKey('liked-${posts[index].uri}'),
              post: posts[index],
              showSourceBadge: false,
            ),
          );
        },
      ),
    );
  }
}

/// Asks for a handle or DID, and hands back the normalised one.
Future<String?> showBlueskyAddAccountDialog(
  BuildContext context, {
  bool lookup = false,
}) {
  return showDialog<String>(
    context: context,
    builder: (_) => _BlueskyAddAccountDialog(lookup: lookup),
  );
}

class _BlueskyAddAccountDialog extends StatefulWidget {
  final bool lookup;

  const _BlueskyAddAccountDialog({required this.lookup});

  @override
  State<_BlueskyAddAccountDialog> createState() =>
      _BlueskyAddAccountDialogState();
}

class _BlueskyAddAccountDialogState extends State<_BlueskyAddAccountDialog> {
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
    final handle = normaliseBlueskyHandle(_controller.text);
    if (handle == null) {
      setState(() => _error = l10n.plugin_bluesky_invalid_handle);
    } else {
      Navigator.pop(context, handle);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return AlertDialog(
      title: Text(
        widget.lookup ? l10n.plugin_bluesky_lookup : l10n.plugin_bluesky_add,
      ),
      content: TextField(
        controller: _controller,
        autofocus: true,
        decoration: InputDecoration(
          hintText: l10n.plugin_bluesky_handle_hint,
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
