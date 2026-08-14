import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/plugins/bluesky/bluesky_client.dart';
import 'package:xta/plugins/bluesky/bluesky_import_follows_screen.dart';
import 'package:xta/plugins/bluesky/bluesky_import_list_screen.dart';
import 'package:xta/plugins/bluesky/bluesky_likes_store.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/bluesky/bluesky_post_card.dart';
import 'package:xta/plugins/bluesky/bluesky_profile_screen.dart';
import 'package:xta/plugins/bluesky/bluesky_search_sheet.dart';
import 'package:xta/plugins/bluesky/bluesky_store.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/ui/empty_pane.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/ui/feed_list.dart';

/// The Bluesky tab: local follows feed, plus a device-only Liked library.
class BlueskyScreen extends StatefulWidget {
  final ScrollController scrollController;

  const BlueskyScreen({super.key, required this.scrollController});

  @override
  State<BlueskyScreen> createState() => _BlueskyScreenState();
}

class _BlueskyScreenState extends State<BlueskyScreen> {
  final _shell = _BlueskyShellStore();
  final _likedScrollController = ScrollController();

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
    _likedScrollController.dispose();
    _shell.destroy();
    super.dispose();
  }

  Future<void> _loadHome({bool force = false}) async {
    final likes = context.read<BlueskyLikesStore>();
    final feed = context.read<BlueskyFeedStore>();
    await likes.load();
    // The pull is the reader asking for new posts, so it has to get past the
    // ten-minute per-account cache — without this the spinner ran and nothing
    // was refetched.
    await feed.refresh(force: force);
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
    final l10n = L10n.of(context);

    return Scaffold(
      primary: !PluginEmbedded.maybeOf(context),
      body: ScopedBuilder<_BlueskyShellStore, int>(
        store: _shell,
        onState: (context, tab) => Column(
          children: [
            PluginHomeChrome(
              tabs: [
                PluginHomeTab(
                  label: l10n.plugin_bluesky_home,
                  icon: Icons.home_outlined,
                  selected: tab == 0,
                  onTap: () => _shell.select(0),
                ),
                PluginHomeTab(
                  label: l10n.plugin_bluesky_liked,
                  icon: Icons.favorite_border,
                  selected: tab == 1,
                  onTap: () => _shell.select(1),
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
                  ],
                ),
              ],
            ),
            const Divider(height: 1),
            Expanded(
              child: IndexedStack(
                index: tab,
                children: [
                  _HomePane(
                    scrollController: widget.scrollController,
                    onRefresh: () => _loadHome(force: true),
                  ),
                  _LikedPane(
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
      onLoading: (_) {
        if (feed.state.isNotEmpty) {
          return _feed(context, l10n, feed.state);
        }
        return const Center(child: CircularProgressIndicator());
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
      // the pull — and the note that says more accounts are still to come used
      // to be hidden in precisely that state.
      return ScopedBuilder<BlueskyAccountsStore, List<BlueskyAccount>>(
        store: context.read<BlueskyAccountsStore>(),
        onState: (context, accounts) {
          final pending = context.read<BlueskyFeedStore>().pending(
            accounts.map((e) => e.actor).toList(growable: false),
          );

          return EmptyPane(
            icon: Icons.cloud_outlined,
            message: accounts.isEmpty
                ? l10n.plugin_bluesky_empty
                : l10n.plugin_bluesky_no_posts,
            scrollController: scrollController,
            onRefresh: onRefresh,
            leading: pending > 0
                ? _PendingAccountsNote(pending: pending)
                : null,
            action: accounts.isEmpty
                ? FilledButton.icon(
                    onPressed: () => showBlueskySearchSheet(context),
                    icon: const Icon(Icons.explore_outlined),
                    label: Text(l10n.plugin_bluesky_discover),
                  )
                : FilledButton.icon(
                    onPressed: onRefresh,
                    icon: const Icon(Icons.refresh),
                    label: Text(l10n.retry),
                  ),
          );
        },
      );
    }

    final accounts = context.read<BlueskyAccountsStore>().state;
    final pending = context.read<BlueskyFeedStore>().pending(
      accounts.map((e) => e.actor).toList(growable: false),
    );

    return RefreshIndicator(
      onRefresh: onRefresh,
      child: FeedListView(
        controller: scrollController,
        itemCount: posts.length + (pending > 0 ? 1 : 0),
        itemBuilder: (context, index) {
          if (pending > 0 && index == 0) {
            return _PendingAccountsNote(pending: pending);
          }
          final post = posts[index - (pending > 0 ? 1 : 0)];
          return BlueskyPostCard(
            key: ValueKey(post.uri),
            post: post,
            showSourceBadge: false,
          );
        },
      ),
    );
  }
}

/// Says that more followed accounts are still to be read, and that pulling to
/// refresh reads the next batch of them.
class _PendingAccountsNote extends StatelessWidget {
  final int pending;

  const _PendingAccountsNote({required this.pending});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
      child: Row(
        children: [
          Icon(Icons.hourglass_bottom, size: 16, color: theme.hintColor),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              L10n.of(context).plugin_bluesky_accounts_pending(pending),
              style: theme.textTheme.bodySmall?.copyWith(
                color: theme.hintColor,
              ),
            ),
          ),
        ],
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
              controller: scrollController,
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

          return ListView.builder(
            controller: scrollController,
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
  final controller = TextEditingController();

  return showDialog<String>(
    context: context,
    builder: (dialogContext) {
      final l10n = L10n.of(dialogContext);
      String? error;

      return StatefulBuilder(
        builder: (context, setState) => AlertDialog(
          title: Text(
            lookup ? l10n.plugin_bluesky_lookup : l10n.plugin_bluesky_add,
          ),
          content: TextField(
            controller: controller,
            autofocus: true,
            decoration: InputDecoration(
              hintText: l10n.plugin_bluesky_handle_hint,
              errorText: error,
            ),
            onSubmitted: (_) {
              final handle = normaliseBlueskyHandle(controller.text);
              if (handle == null) {
                setState(() => error = l10n.plugin_bluesky_invalid_handle);
              } else {
                Navigator.pop(context, handle);
              }
            },
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: Text(l10n.cancel),
            ),
            TextButton(
              onPressed: () {
                final handle = normaliseBlueskyHandle(controller.text);
                if (handle == null) {
                  setState(() => error = l10n.plugin_bluesky_invalid_handle);
                } else {
                  Navigator.pop(context, handle);
                }
              },
              child: Text(l10n.ok),
            ),
          ],
        ),
      );
    },
  );
}
