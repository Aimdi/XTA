import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:xta/plugins/plugin_bookmarks.dart';
import 'package:xta/saved/saved_source_filter.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:extended_image/extended_image.dart';
import 'package:xta/plugins/plugin_feed_insets.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/plugins/plugin_marks.dart';
import 'package:xta/plugins/plugin_view_store.dart';
import 'package:xta/plugins/plugin_session.dart';
import 'package:xta/plugins/plugin_lazy_tabs.dart';
import 'package:xta/plugins/bluesky/bluesky_client.dart';
import 'package:xta/plugins/bluesky/bluesky_discovery.dart';
import 'package:xta/plugins/bluesky/bluesky_feeds_pane.dart';
import 'package:xta/plugins/bluesky/bluesky_feeds_store.dart';
import 'package:xta/plugins/bluesky/bluesky_plugin.dart';
import 'package:xta/plugins/bluesky/bluesky_import_follows_screen.dart';
import 'package:xta/plugins/bluesky/bluesky_import_list_screen.dart';
import 'package:xta/plugins/bluesky/bluesky_import_starter_screen.dart';
import 'package:xta/plugins/bluesky/bluesky_likes_store.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/bluesky/bluesky_reader_store.dart';
import 'package:xta/plugins/bluesky/bluesky_reader_view.dart';
import 'package:xta/plugins/bluesky/bluesky_settings.dart';
import 'package:xta/plugins/bluesky/bluesky_profile_screen.dart';
import 'package:xta/plugins/bluesky/bluesky_search_sheet.dart';
import 'package:xta/plugins/bluesky/bluesky_store.dart';
import 'package:xta/plugins/plugin_feed_people.dart';
import 'package:xta/subscriptions/widgets/fallback_avatar.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/ui/empty_pane.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/plugins/plugin_feed_skeleton.dart';

/// Home remounts used to poll the AppView whenever the ten-minute TTL expired.
/// Only a pull-to-refresh, or the first empty paint, should hit the network.
bool blueskyHomeShouldFetch({required bool force, required bool feedEmpty}) => force || feedEmpty;

/// The Bluesky tab: local follows feed, plus a device-only Liked library.
class BlueskyScreen extends StatefulWidget {
  final ScrollController scrollController;

  const BlueskyScreen({super.key, required this.scrollController});

  @override
  State<BlueskyScreen> createState() => _BlueskyScreenState();
}

class _BlueskyScreenState extends State<BlueskyScreen> with AutomaticKeepAliveClientMixin {
  late final PluginSessionLease _session;
  late final _BlueskyShellStore _shell;
  late final BlueskyReaderStore _reader;
  final _algoScrollController = ScrollController();
  final _listsScrollController = ScrollController();
  final _likedScrollController = ScrollController();

  @override
  bool get wantKeepAlive => true;

  @override
  void initState() {
    super.initState();
    _session = PluginSessionLease(context, 'bluesky');
    _reader = _session.obtain(
      'reader',
      () => BlueskyReaderStore(PrefService.of(context, listen: false), context.read<BlueskyClient>().baseUrl),
    );
    _reader.changeSource(context.read<BlueskyClient>().baseUrl);
    _shell = _session.obtain('view', () => _BlueskyShellStore(_reader.state.tab));
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
    _session.dispose();
    super.dispose();
  }

  Future<void> _loadHome({bool force = false}) async {
    final accounts = context.read<BlueskyAccountsStore>();
    final likes = context.read<BlueskyLikesStore>();
    final feed = context.read<BlueskyFeedStore>();
    // Startup already hydrated these when the plugin was on. A remount from
    // the home strip should not hit SQLite again just to paint the same list.
    await Future.wait([if (accounts.state.isEmpty) accounts.load(), if (likes.state.isEmpty) likes.load()]);
    if (!mounted) return;
    _reader.changeSource(context.read<BlueskyClient>().baseUrl);
    if (!force && feed.state.isEmpty) {
      final restored = _reader.restorePosts(accounts.state);
      if (restored.isNotEmpty) feed.update(restored);
    }
    // Remounts used to poll whenever the ten-minute TTL expired, which
    // jumped the list and flashed a "N more accounts" count. Only a
    // pull-to-refresh, or the first empty paint, should hit the AppView.
    await feed.ensureLoaded(force: force);
  }

  void _selectTab(int tab) {
    _shell.select(tab);
    _reader.selectTab(tab);
    if (tab == 1) context.read<BlueskyAlgoStore>().ensureLoaded();
    if (tab == 2) context.read<BlueskyListsStore>().ensureLoaded();
  }

  Future<void> _settings() async {
    final client = context.read<BlueskyClient>();
    final source = client.baseUrl;
    await Navigator.push(context, MaterialPageRoute(builder: (_) => const BlueskySettingsScreen()));
    if (mounted && source != client.baseUrl) {
      _reader.changeSource(client.baseUrl);
      await _loadHome(force: true);
      if (!mounted) return;
      if (_shell.state == 1) await context.read<BlueskyAlgoStore>().ensureLoaded();
      if (mounted && _shell.state == 2) await context.read<BlueskyListsStore>().ensureLoaded();
    }
  }

  Future<void> _searchPeople() async {
    await showBlueskySearchSheet(context);
    if (mounted) {
      await context.read<BlueskyFeedStore>().ensureLoaded();
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
        messenger.showSnackBar(SnackBar(content: Text(blueskyErrorMessage(l10n, e))));
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
    final compact = PluginEmbedded.maybeOf(context) ||
        MediaQuery.sizeOf(context).width < 360 || MediaQuery.textScalerOf(context).scale(1) > 1.4;
    _shell.restore(context, 'bluesky');

    return Provider<BlueskyReaderStore>.value(
      value: _reader,
      child: Scaffold(
        primary: !PluginEmbedded.maybeOf(context),
        body: ScopedBuilder<_BlueskyShellStore, int>(
          store: _shell,
          onState: (context, tab) => Column(
            children: [
              PluginHomeChrome(
                title: l10n.plugin_bluesky_title,
                mark: pluginMark(BlueskyPlugin(), size: 24),
                accent: BlueskyPlugin().brandColor,
                tabs: [
                  PluginHomeTab(
                    label: l10n.plugin_bluesky_following,
                    icon: Icons.home_outlined,
                    selected: tab == 0,
                    onTap: () => _selectTab(0),
                  ),
                  PluginHomeTab(
                    label: l10n.plugin_bluesky_discover,
                    icon: Icons.auto_awesome_outlined,
                    selected: tab == 1,
                    onTap: () => _selectTab(1),
                  ),
                  PluginHomeTab(
                    label: l10n.plugin_bluesky_lists,
                    icon: Icons.list_alt_outlined,
                    selected: tab == 2,
                    onTap: () => _selectTab(2),
                  ),
                  PluginHomeTab(
                    label: l10n.plugin_bluesky_liked,
                    icon: Icons.favorite_border,
                    selected: tab == 3,
                    onTap: () => _selectTab(3),
                  ),
                ],
                actions: [
                  if (!compact)
                    IconButton(
                      icon: const Icon(Icons.bookmark_border),
                      tooltip: l10n.saved,
                      onPressed: () => openPluginBookmarks(context, SavedSource.bluesky),
                    ),
                  IconButton(
                    icon: const Icon(Icons.search),
                    tooltip: l10n.plugin_bluesky_search,
                    onPressed: _searchPeople,
                  ),
                  if (!compact)
                    IconButton(
                      icon: const Icon(Icons.person_add_alt),
                      tooltip: l10n.plugin_bluesky_add,
                      onPressed: _addAccount,
                    ),
                  PopupMenuButton<String>(
                    onSelected: (value) {
                      if (value == 'add') {
                        _addAccount();
                        return;
                      }
                      if (value == 'saved') {
                        openPluginBookmarks(context, SavedSource.bluesky);
                        return;
                      }
                      if (value == 'settings') {
                        _settings();
                        return;
                      }
                      final page = switch (value) {
                        'following' => const BlueskyImportFollowsScreen(),
                        'list' => const BlueskyImportListScreen(),
                        'starter' => const BlueskyImportStarterPackScreen(),
                        _ => null,
                      };
                      if (page != null) {
                        Navigator.push(context, MaterialPageRoute(builder: (_) => page));
                      }
                    },
                    itemBuilder: (context) => [
                      if (compact) PopupMenuItem(value: 'add', child: Text(l10n.plugin_bluesky_add)),
                      if (compact) PopupMenuItem(value: 'saved', child: Text(l10n.saved)),
                      PopupMenuItem(value: 'following', child: Text(l10n.plugin_bluesky_import_following)),
                      PopupMenuItem(value: 'list', child: Text(l10n.plugin_bluesky_import_list)),
                      PopupMenuItem(value: 'starter', child: Text(l10n.plugin_bluesky_import_starter)),
                      PopupMenuItem(value: 'settings', child: Text(l10n.settings)),
                    ],
                  ),
                ],
              ),
              const Divider(height: 1),
              Expanded(
                child: PluginLazyTabs(
                  index: tab,
                  children: [
                    (_) =>
                        _HomePane(scrollController: widget.scrollController, onRefresh: () => _loadHome(force: true)),
                    (_) => BlueskyAlgoPane(scrollController: _algoScrollController),
                    (_) => BlueskyListsPane(scrollController: _listsScrollController),
                    (_) =>
                        _LikedPane(scrollController: _likedScrollController, likes: context.read<BlueskyLikesStore>()),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _BlueskyShellStore extends PluginViewStore<int> {
  _BlueskyShellStore(super.initialState);
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
            message: accounts.isEmpty ? l10n.plugin_bluesky_empty : l10n.plugin_bluesky_no_posts,
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
        final feed = context.read<BlueskyFeedStore>();
        final pending = feed.pending(
          context.read<BlueskyAccountsStore>().state.map((account) => account.actor).toList(),
        );
        return RefreshIndicator(
          onRefresh: onRefresh,
          child: BlueskyReaderView(
            key: ValueKey(context.read<BlueskyClient>().baseUrl),
            slot: 'following',
            posts: posts,
            controller: scrollController,
            heading: people.isEmpty
                ? null
                : PluginFeedPeopleStrip(
                    title: l10n.plugin_bluesky_from_feed,
                    followLabel: l10n.plugin_bluesky_follow,
                    people: people,
                    avatar: (person) => _feedPersonAvatar(context, person),
                    onOpen: (person) => Navigator.push(
                      context,
                      MaterialPageRoute(builder: (_) => BlueskyProfileScreen(actor: person.handle)),
                    ),
                    onFollow: (person) => context.read<BlueskyAccountsStore>().add(
                      BlueskyAccount(handle: person.handle, name: person.name, avatarUrl: person.avatarUrl),
                    ),
                  ),
            footer: feed.refreshError == null && pending == 0
                ? null
                : Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      children: [
                        if (feed.refreshError != null) ...[
                          Text(l10n.plugin_mastodon_refresh_failed),
                          TextButton.icon(
                            onPressed: () => feed.refresh(),
                            icon: const Icon(Icons.refresh),
                            label: Text(l10n.retry),
                          ),
                        ],
                        if (pending > 0)
                          TextButton.icon(
                            onPressed: () => feed.refresh(),
                            icon: const Icon(Icons.download_outlined),
                            label: Text(l10n.plugin_bluesky_load_more_accounts),
                          ),
                      ],
                    ),
                  ),
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
            onPressed: () =>
                Navigator.push(context, MaterialPageRoute(builder: (_) => const BlueskyImportFollowsScreen())),
            icon: const Icon(Icons.group_add_outlined),
            label: Text(l10n.plugin_bluesky_import_following),
          ),
          TextButton.icon(
            onPressed: () =>
                Navigator.push(context, MaterialPageRoute(builder: (_) => const BlueskyImportStarterPackScreen())),
            icon: const Icon(Icons.auto_awesome_outlined),
            label: Text(l10n.plugin_bluesky_import_starter),
          ),
        ] else ...[
          const SizedBox(height: 8),
          TextButton.icon(
            onPressed: () => context.read<BlueskyFeedStore>().refresh(),
            icon: const Icon(Icons.download_outlined),
            label: Text(l10n.plugin_bluesky_load_more_accounts),
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
              controller: pluginInnerScrollController(context, scrollController),
              padding: const EdgeInsets.fromLTRB(32, 72, 32, 32),
              children: [
                Icon(Icons.favorite_border, size: 52, color: Theme.of(context).colorScheme.outline),
                const SizedBox(height: 16),
                Text(
                  l10n.plugin_bluesky_liked_empty,
                  textAlign: TextAlign.center,
                  style: Theme.of(context).textTheme.titleMedium,
                ),
              ],
            );
          }

          return BlueskyReaderView(slot: 'likes', posts: posts, controller: scrollController);
        },
      ),
    );
  }
}

/// Asks for a handle or DID, and hands back the normalised one.
Future<String?> showBlueskyAddAccountDialog(BuildContext context, {bool lookup = false}) {
  return showDialog<String?>(
    context: context,
    builder: (_) => _BlueskyAddAccountDialog(lookup: lookup),
  );
}

class _BlueskyAddAccountDialog extends StatefulWidget {
  final bool lookup;

  const _BlueskyAddAccountDialog({required this.lookup});

  @override
  State<_BlueskyAddAccountDialog> createState() => _BlueskyAddAccountDialogState();
}

class _BlueskyAddAccountDialogState extends State<_BlueskyAddAccountDialog> {
  late final TextEditingController _controller;
  final _form = GlobalKey<FormState>();

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
    if (_form.currentState?.validate() == true) {
      Navigator.pop(context, normaliseBlueskyHandle(_controller.text));
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return AlertDialog(
      title: Text(widget.lookup ? l10n.plugin_bluesky_lookup : l10n.plugin_bluesky_add),
      content: Form(
        key: _form,
        child: TextFormField(
          validator: (value) => normaliseBlueskyHandle(value ?? '') == null ? l10n.plugin_bluesky_invalid_handle : null,
          controller: _controller,
          autofocus: true,
          decoration: InputDecoration(hintText: l10n.plugin_bluesky_handle_hint),
          onFieldSubmitted: (_) => _submit(),
        ),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: Text(l10n.cancel)),
        TextButton(onPressed: _submit, child: Text(l10n.ok)),
      ],
    );
  }
}