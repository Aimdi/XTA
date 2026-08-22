import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_feed_insets.dart';
import 'package:xta/plugins/plugin_feed_people.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/plugins/plugin_lazy_tabs.dart';
import 'package:xta/plugins/threads/threads_client.dart';
import 'package:xta/plugins/threads/threads_plugin.dart';
import 'package:xta/plugins/threads/threads_direct_client.dart';
import 'package:xta/plugins/threads/threads_discovery.dart';
import 'package:xta/plugins/threads/threads_image.dart';
import 'package:xta/plugins/threads/threads_likes_store.dart';
import 'package:xta/plugins/threads/threads_models.dart';
import 'package:xta/plugins/threads/threads_post_card.dart';
import 'package:xta/plugins/threads/threads_profile_screen.dart';
import 'package:xta/plugins/threads/threads_search_sheet.dart';
import 'package:xta/plugins/threads/threads_settings.dart';
import 'package:xta/plugins/threads/threads_store.dart';
import 'package:xta/subscriptions/widgets/fallback_avatar.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/ui/feed_list.dart';
import 'package:xta/plugins/plugin_feed_skeleton.dart';

/// What a failed Threads read should say, in the reader's terms.
String threadsErrorMessage(L10n l10n, Object error) {
  if (error is! ThreadsException) {
    return l10n.plugin_threads_error_unreachable;
  }
  return switch (error.kind) {
    ThreadsErrorKind.notConfigured => l10n.plugin_threads_not_configured,
    ThreadsErrorKind.noSuchFeed => l10n.plugin_threads_error_no_feed,
    ThreadsErrorKind.throttled => l10n.plugin_threads_error_throttled,
    ThreadsErrorKind.unreachable => l10n.plugin_threads_error_unreachable,
    ThreadsErrorKind.unauthorized => l10n.plugin_threads_error_unauthorized,
    ThreadsErrorKind.sessionSuspended =>
      l10n.plugin_threads_error_session_suspended,
  };
}

/// The Threads tab: every followed account, merged newest first.
class ThreadsScreen extends StatefulWidget {
  final ScrollController scrollController;

  const ThreadsScreen({super.key, required this.scrollController});

  @override
  State<ThreadsScreen> createState() => _ThreadsScreenState();
}

class _ThreadsScreenState extends State<ThreadsScreen> {
  final _shell = _ThreadsShellStore();
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
    final accounts = context.read<ThreadsAccountsStore>();
    final likes = context.read<ThreadsLikesStore>();
    final feed = context.read<ThreadsFeedStore>();
    // Accounts and likes are independent; waiting on likes before the feed
    // only delayed the first paint. Remounts from the home strip already have
    // both from startup (or the last visit) — don't hit SQLite again.
    await Future.wait([
      if (accounts.state.isEmpty) accounts.load(),
      if (likes.state.isEmpty) likes.load(),
    ]);
    await feed.refresh(force: force);
  }

  /// Opens Discover / people search (cookie multi-result, else exact handle).
  Future<void> _lookUpProfile() async {
    await showThreadsSearchSheet(context);
    if (mounted) {
      await context.read<ThreadsFeedStore>().refresh();
    }
  }

  Future<void> _addAccount() async {
    final direct = context.read<ThreadsDirectClient>();
    final accounts = context.read<ThreadsAccountsStore>();
    final feed = context.read<ThreadsFeedStore>();
    final handle = await showThreadsAddAccountDialog(context);
    if (handle == null || !mounted) {
      return;
    }

    var account = ThreadsAccount(handle: handle, name: handle);
    try {
      account = (await direct.fetchProfile(handle)).toAccount();
    } catch (_) {
      // Public posts can still load even when the profile card cannot.
    }
    await accounts.add(account);
    await feed.refresh(force: true);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);

    return Scaffold(
      primary: !PluginEmbedded.maybeOf(context),
      body: ScopedBuilder<_ThreadsShellStore, int>(
        store: _shell,
        onState: (context, tab) => Column(
          children: [
            PluginHomeChrome(
              accent: ThreadsPlugin().brandColor,
              tabs: [
                PluginHomeTab(
                  label: l10n.plugin_threads_home,
                  icon: Icons.home_outlined,
                  selected: tab == 0,
                  onTap: () => _shell.select(0),
                ),
                PluginHomeTab(
                  label: l10n.plugin_threads_liked,
                  icon: Icons.favorite_border,
                  selected: tab == 1,
                  onTap: () => _shell.select(1),
                ),
              ],
              actions: [
                IconButton(
                  icon: const Icon(Icons.search),
                  tooltip: l10n.plugin_threads_search,
                  onPressed: _lookUpProfile,
                ),
                IconButton(
                  icon: const Icon(Icons.person_add_alt),
                  tooltip: l10n.plugin_threads_add_account,
                  onPressed: _addAccount,
                ),
                IconButton(
                  icon: const Icon(Icons.settings),
                  tooltip: l10n.settings,
                  onPressed: () => Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (_) => const ThreadsSettingsScreen(),
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
                  (_) => _HomePane(
                    scrollController: widget.scrollController,
                    onAddAccount: _addAccount,
                    onLookUpProfile: _lookUpProfile,
                    onRefresh: () => _loadHome(force: true),
                  ),
                  (_) => _LikedPane(
                    scrollController: _likedScrollController,
                    likes: context.read<ThreadsLikesStore>(),
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

class _ThreadsShellStore extends Store<int> {
  _ThreadsShellStore() : super(0);

  void select(int index) => update(index);
}

class _HomePane extends StatelessWidget {
  final ScrollController scrollController;
  final Future<void> Function() onAddAccount;
  final Future<void> Function() onLookUpProfile;
  final Future<void> Function() onRefresh;

  const _HomePane({
    required this.scrollController,
    required this.onAddAccount,
    required this.onLookUpProfile,
    required this.onRefresh,
  });

  Widget _feed(BuildContext context, L10n l10n, List<ThreadsPost> posts) {
    final handles = context
        .read<ThreadsAccountsStore>()
        .state
        .map((e) => e.handle)
        .toList(growable: false);
    final pending = context.read<ThreadsFeedStore>().pending(handles);

    if (posts.isEmpty) {
      return _empty(context, pending: pending);
    }

    return ScopedBuilder<ThreadsAccountsStore, List<ThreadsAccount>>(
      store: context.read<ThreadsAccountsStore>(),
      onState: (context, _) {
        final accounts = context.read<ThreadsAccountsStore>();
        final people = peopleToFollowFromThreads(
          posts: posts,
          alreadyFollows: accounts.follows,
        );
        final peopleOffset = people.isEmpty ? 0 : 1;
        final pendingOffset = pending > 0 ? 1 : 0;
        return RefreshIndicator(
          // The reader pulled: that is the one moment worth going past the cache.
          onRefresh: onRefresh,
          child: FeedListView(
            controller: pluginInnerScrollController(context, scrollController),
            padding: pluginFeedPadding(context),
            itemCount: posts.length + peopleOffset + pendingOffset,
            itemBuilder: (context, index) {
              if (peopleOffset == 1 && index == 0) {
                return PluginFeedPeopleStrip(
                  title: l10n.plugin_threads_from_feed,
                  followLabel: l10n.plugin_threads_follow,
                  people: people,
                  avatar: (person) => _feedPersonAvatar(context, person),
                  onOpen: (person) => Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (_) =>
                          ThreadsProfileScreen(username: person.handle),
                    ),
                  ),
                  onFollow: (person) => accounts.add(
                    ThreadsAccount(
                      handle: person.handle,
                      name: person.name,
                      avatarUrl: person.avatarUrl,
                    ),
                  ),
                );
              }
              final postIndex = index - peopleOffset;
              if (postIndex >= posts.length) {
                return _PendingAccountsNote(pending: pending);
              }
              return ThreadsPostCard(
                key: ValueKey(posts[postIndex].id),
                post: posts[postIndex],
                showSourceBadge: false,
              );
            },
          ),
        );
      },
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
      child: ThreadsNetworkImage(
        url,
        width: 20,
        height: 20,
        fit: BoxFit.cover,
        cacheWidth: (20 * MediaQuery.devicePixelRatioOf(context)).ceil(),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final feed = context.read<ThreadsFeedStore>();

    return Column(
      children: [
        if (context.read<ThreadsDirectClient>().isSessionParked)
          Material(
            color: Theme.of(context).colorScheme.errorContainer,
            child: ListTile(
              dense: true,
              leading: Icon(
                Icons.pause_circle_outline,
                color: Theme.of(context).colorScheme.onErrorContainer,
              ),
              title: Text(
                l10n.plugin_threads_session_parked,
                style: TextStyle(
                  color: Theme.of(context).colorScheme.onErrorContainer,
                ),
              ),
            ),
          ),
        ThreadsFollowingStrip(onAddAccount: onAddAccount),
        Expanded(
          child: ScopedBuilder<ThreadsFeedStore, List<ThreadsPost>>(
            store: feed,
            onLoading: (_) {
              // Soft refresh must not blank a healthy feed into a forever spinner.
              if (feed.state.isNotEmpty) {
                return _feed(context, l10n, feed.state);
              }
              return const PluginFeedSkeleton();
            },
            onError: (context, error) {
              if (feed.state.isNotEmpty) {
                return _feed(context, l10n, feed.state);
              }
              final notConfigured =
                  error is ThreadsException &&
                  error.kind == ThreadsErrorKind.notConfigured;
              if (notConfigured) {
                return _empty(context);
              }
              return Padding(
                padding: const EdgeInsets.all(24),
                child: FullPageErrorWidget(
                  error: error,
                  stackTrace: null,
                  prefix: threadsErrorMessage(l10n, error ?? Exception()),
                  onRetry: onRefresh,
                ),
              );
            },
            onState: (context, posts) => _feed(context, l10n, posts),
          ),
        ),
      ],
    );
  }

  Widget _empty(BuildContext context, {int pending = 0}) {
    final theme = Theme.of(context);
    final l10n = L10n.of(context);
    final accounts = context.read<ThreadsAccountsStore>().state;

    return RefreshIndicator(
      onRefresh: onRefresh,
      child: ListView(
        controller: pluginInnerScrollController(context, scrollController),
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(32, 72, 32, 32),
        children: [
          if (pending > 0) _PendingAccountsNote(pending: pending),
          Icon(
            Icons.alternate_email,
            size: 52,
            color: theme.colorScheme.outline,
          ),
          const SizedBox(height: 16),
          Text(
            accounts.isEmpty
                ? l10n.plugin_threads_no_accounts
                : l10n.plugin_threads_no_posts,
            textAlign: TextAlign.center,
            style: theme.textTheme.titleMedium!.copyWith(
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            l10n.plugin_threads_empty_cta,
            textAlign: TextAlign.center,
            style: theme.textTheme.bodyMedium!.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
          const SizedBox(height: 24),
          Center(
            child: FilledButton.icon(
              onPressed: onAddAccount,
              icon: const Icon(Icons.person_add_alt),
              label: Text(l10n.plugin_threads_add_account),
            ),
          ),
          Center(
            child: TextButton.icon(
              onPressed: onLookUpProfile,
              icon: const Icon(Icons.search),
              label: Text(l10n.plugin_threads_discover),
            ),
          ),
        ],
      ),
    );
  }
}

class _LikedPane extends StatelessWidget {
  final ScrollController scrollController;
  final ThreadsLikesStore likes;

  const _LikedPane({required this.scrollController, required this.likes});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return RefreshIndicator(
      onRefresh: likes.load,
      child: ScopedBuilder<ThreadsLikesStore, List<ThreadsPost>>(
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
                  l10n.plugin_threads_liked_empty,
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
            itemBuilder: (context, index) => ThreadsPostCard(
              key: ValueKey('liked-${posts[index].id}'),
              post: posts[index],
              showSourceBadge: false,
            ),
          );
        },
      ),
    );
  }
}

/// Who the reader follows on Threads, along the top of the tab.
///
/// The list used to live only in Settings, which meant the tab could show an
/// empty feed with no way to tell whether that was because nobody was followed
/// or because the fetch came back with nothing. A row of faces answers that
/// before the feed loads, and each one opens the profile it belongs to. The
/// trailing add chip is the same action as the AppBar person-add button.
class ThreadsFollowingStrip extends StatelessWidget {
  final Future<void> Function()? onAddAccount;

  const ThreadsFollowingStrip({super.key, this.onAddAccount});

  Future<void> _confirmUnfollow(
    BuildContext context,
    ThreadsAccount account,
  ) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) {
        final l10n = L10n.of(dialogContext);
        return AlertDialog(
          title: Text(l10n.plugin_threads_unfollow),
          content: Text('@${account.handle}'),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(dialogContext, false),
              child: Text(l10n.cancel),
            ),
            TextButton(
              onPressed: () => Navigator.pop(dialogContext, true),
              child: Text(l10n.plugin_threads_unfollow),
            ),
          ],
        );
      },
    );
    if (confirmed == true && context.mounted) {
      await context.read<ThreadsAccountsStore>().remove(account.handle);
      if (context.mounted) {
        await context.read<ThreadsFeedStore>().refresh(force: true);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = L10n.of(context);

    return ScopedBuilder<ThreadsAccountsStore, List<ThreadsAccount>>(
      store: context.read<ThreadsAccountsStore>(),
      onState: (context, accounts) {
        if (accounts.isEmpty && onAddAccount == null) {
          return const SizedBox.shrink();
        }

        final addChip = onAddAccount == null ? 0 : 1;

        return SizedBox(
          height: 84,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            itemCount: accounts.length + addChip,
            separatorBuilder: (_, _) => const SizedBox(width: 12),
            itemBuilder: (context, index) {
              if (index >= accounts.length) {
                return Tooltip(
                  message: l10n.plugin_threads_add_account,
                  child: InkWell(
                    onTap: () => onAddAccount?.call(),
                    customBorder: const CircleBorder(),
                    child: SizedBox(
                      width: 64,
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Container(
                            width: 40,
                            height: 40,
                            decoration: BoxDecoration(
                              shape: BoxShape.circle,
                              border: Border.all(
                                color: theme.colorScheme.outline,
                              ),
                            ),
                            child: Icon(
                              Icons.add,
                              color: theme.colorScheme.primary,
                            ),
                          ),
                          // Match the handle line under avatars so the chip sits level.
                          const SizedBox(height: 4 + 14),
                        ],
                      ),
                    ),
                  ),
                );
              }

              final account = accounts[index];

              return InkWell(
                onTap: () => Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (_) =>
                        ThreadsProfileScreen(username: account.handle),
                  ),
                ),
                onLongPress: () => _confirmUnfollow(context, account),
                child: SizedBox(
                  width: 64,
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      _FollowingAvatar(account: account),
                      const SizedBox(height: 4),
                      Text(
                        account.handle,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        textAlign: TextAlign.center,
                        style: theme.textTheme.bodySmall,
                      ),
                    ],
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

class _FollowingAvatar extends StatelessWidget {
  final ThreadsAccount account;

  const _FollowingAvatar({required this.account});

  @override
  Widget build(BuildContext context) {
    const size = 40.0;
    final avatarUrl = account.avatarUrl;
    if (avatarUrl == null || avatarUrl.isEmpty) {
      return FallbackAvatar(
        seed: account.handle,
        displayName: account.name,
        size: size,
        accent: Theme.of(context).colorScheme.primary,
      );
    }

    return ClipOval(
      child: ThreadsNetworkImage(
        avatarUrl,
        width: size,
        height: size,
        fit: BoxFit.cover,
        cacheWidth: (size * MediaQuery.devicePixelRatioOf(context)).ceil(),
      ),
    );
  }
}

/// Asks for a handle, and hands back the normalised one.
Future<String?> showThreadsAddAccountDialog(
  BuildContext context, {
  bool lookup = false,
}) {
  return showDialog<String>(
    context: context,
    builder: (_) => _ThreadsAddAccountDialog(lookup: lookup),
  );
}

class _ThreadsAddAccountDialog extends StatefulWidget {
  final bool lookup;

  const _ThreadsAddAccountDialog({required this.lookup});

  @override
  State<_ThreadsAddAccountDialog> createState() =>
      _ThreadsAddAccountDialogState();
}

class _ThreadsAddAccountDialogState extends State<_ThreadsAddAccountDialog> {
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
    final handle = normaliseThreadsHandle(_controller.text);
    if (handle == null) {
      setState(() => _error = l10n.plugin_threads_invalid_handle);
    } else {
      Navigator.pop(context, handle);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return AlertDialog(
      title: Text(
        widget.lookup
            ? l10n.plugin_threads_lookup
            : l10n.plugin_threads_add_account,
      ),
      content: TextField(
        controller: _controller,
        autofocus: true,
        decoration: InputDecoration(
          hintText: l10n.plugin_threads_account_hint,
          errorText: _error,
          prefixText: '@',
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

class _PendingAccountsNote extends StatelessWidget {
  final int pending;

  const _PendingAccountsNote({required this.pending});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
      child: Text(
        L10n.of(context).plugin_threads_accounts_pending(pending),
        textAlign: TextAlign.center,
        style: Theme.of(context).textTheme.bodySmall!.copyWith(
          color: Theme.of(context).colorScheme.onSurfaceVariant,
        ),
      ),
    );
  }
}
