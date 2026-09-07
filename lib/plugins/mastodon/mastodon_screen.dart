import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/mastodon/mastodon_client.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_navigation.dart';
import 'package:xta/plugins/mastodon/mastodon_people.dart';
import 'package:xta/plugins/mastodon/mastodon_profile_screen.dart';
import 'package:xta/plugins/mastodon/mastodon_search_sheet.dart';
import 'package:xta/plugins/mastodon/mastodon_store.dart';
import 'package:xta/plugins/mastodon/mastodon_settings.dart';
import 'package:xta/plugins/plugin_feed_insets.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/plugins/plugin_view_store.dart';
import 'package:xta/plugins/plugin_session.dart';
import 'package:xta/plugins/plugin_lazy_tabs.dart';
import 'package:xta/ui/empty_pane.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/plugins/mastodon/mastodon_reading_store.dart';
import 'package:xta/plugins/mastodon/mastodon_reading_list.dart';
import 'package:xta/plugins/plugin_feed_skeleton.dart';

/// A compact Home reader or a dedicated Mastodon client.
class MastodonScreen extends StatefulWidget {
  final ScrollController scrollController;

  final bool fullClient;

  const MastodonScreen({super.key, required this.scrollController, this.fullClient = false});

  @override
  State<MastodonScreen> createState() => _MastodonScreenState();
}

class _MastodonTabStore extends PluginViewStore<int> {
  _MastodonTabStore(int initial) : super(initial);
}

class _MastodonScreenState extends State<MastodonScreen> {
  late final PluginSessionLease _session;
  late final _MastodonTabStore _tabs;
  late final MastodonReadingStore _reading;
  final _restoredTabs = <int>{};
  String get _surface => widget.fullClient ? 'client' : 'home';
  late final PluginViewStore<bool> _people;
  final _chrome = PluginViewStore(true);
  late final PageStorageBucket _pageStorage;

  @override
  void initState() {
    super.initState();
    _session = PluginSessionLease(context, 'mastodon');
    final prefs = PrefService.of(context, listen: false);
    final scope = mastodonConfiguredInstances(prefs).join('|');
    _reading = _session.obtain('reading', () => MastodonReadingStore(prefs, scope));
    _reading.changeScope(scope);
    _tabs = _session.obtain('view', () => _MastodonTabStore(_reading.state.tab));
    _people = _session.obtain('people', () => PluginViewStore(_reading.state.people));
    _pageStorage = _session.obtain(widget.fullClient ? 'client-scroll' : 'home-scroll', PageStorageBucket.new);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        // Restore the chosen timeline before considering a network request.
        _onTab(_tabs.state);
      }
    });
  }

  @override
  void dispose() {
    _chrome.destroy();
    _session.dispose();
    super.dispose();
  }

  Future<void> _lookUpProfile() => showMastodonSearchSheet(context);

  Future<void> _settings() async {
    final prefs = PrefService.of(context, listen: false);
    final previous = mastodonConfiguredInstances(prefs).join('|');
    await Navigator.push(context, MaterialPageRoute(builder: (_) => const MastodonSettingsScreen()));
    if (!mounted || previous == mastodonConfiguredInstances(prefs).join('|')) return;
    // Invalidate old servers, but fetch only the visible destination.
    context.read<MastodonExploreStore>().forget();
    context.read<MastodonLocalStore>().forget();
    context.read<MastodonFederatedStore>().forget();
    context.read<MastodonFeedStore>().forget();
    _reading.changeScope(mastodonConfiguredInstances(prefs).join('|'));
    _restoredTabs.clear();
    _onTab(_tabs.state);
  }

  void _updateChrome(ScrollMetrics metrics, int depth) {
    if (widget.fullClient || depth != 0 || metrics.axis != Axis.vertical) return;
    if (metrics.pixels <= metrics.minScrollExtent + 0.5) {
      _chrome.select(true);
    } else if (metrics.pixels > metrics.minScrollExtent + 24) {
      _chrome.select(false);
    }
  }

  void _selectPeople(bool people) {
    _people.select(people);
    _reading.select(_tabs.state, people);
    if (!people) _onTab(3);
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
      final candidates = mastodonInstanceCandidates(acct, configured: mastodonConfiguredInstances(prefs));
      final profile = await client.lookupAnywhere(candidates, acct);
      await accounts.add(profile.toAccount());
    } catch (e) {
      if (mounted) {
        messenger.showSnackBar(SnackBar(content: Text(mastodonErrorMessage(l10n, e))));
      }
      return;
    }

    if (mounted) {
      await context.read<MastodonFeedStore>().refresh();
    }
  }

  @override
  Widget build(BuildContext context) {
    _tabs.restore(context, 'mastodon');
    return Provider<MastodonReadingStore>.value(value: _reading, child: ScopedBuilder<_MastodonTabStore, int>(
      store: _tabs,
      onState: (context, tab) => Scaffold(
        primary: !PluginEmbedded.maybeOf(context),
        appBar: widget.fullClient ? MastodonClientBar(selected: tab,
          height: (MediaQuery.textScalerOf(context).scale(40) + 16).clamp(64, double.infinity),
          onSearch: _lookUpProfile, onSettings: _settings) : null,
        bottomNavigationBar: widget.fullClient ? MastodonNavigation(selected: tab, onSelected: _onTab) : null,
        body: NotificationListener<ScrollNotification>(
          onNotification: (notification) { _updateChrome(notification.metrics, notification.depth); return false; },
          child: NotificationListener<ScrollMetricsNotification>(
            onNotification: (notification) { _updateChrome(notification.metrics, notification.depth); return false; },
            child: Column(children: [
            _controls(context, tab),
            Expanded(child: PageStorage(bucket: _pageStorage,
              child: PluginLazyTabs(index: tab, children: [
                (_) => _ExplorePane(slot: '$_surface:0', scrollController: widget.scrollController),
                (_) => _PublicPane(slot: '$_surface:1', store: context.read<MastodonLocalStore>(),
                  emptyIcon: Icons.home_outlined, scrollController: widget.scrollController),
                (_) => _PublicPane(slot: '$_surface:2', store: context.read<MastodonFederatedStore>(),
                  emptyIcon: Icons.public, scrollController: widget.scrollController),
                (_) => ScopedBuilder<PluginViewStore<bool>, bool>(store: _people,
                  onState: (context, people) => people
                    ? MastodonPeoplePane(onAdd: _addAccount, scrollController: widget.scrollController)
                    : _FollowingPane(slot: '$_surface:3', scrollController: widget.scrollController)),
              ]),
            )),
          ])),
        ),
      ),
    ));
  }

  Widget _controls(BuildContext context, int tab) => ScopedBuilder<PluginViewStore<bool>, bool>(
    store: _chrome,
    onState: (context, visible) {
      final child = widget.fullClient || visible ? Column(mainAxisSize: MainAxisSize.min, children: [
        if (!widget.fullClient) MastodonCompactBar(selected: tab, onSelected: _onTab,
          onSearch: _lookUpProfile, onSettings: _settings),
        if (tab == 3) ScopedBuilder<PluginViewStore<bool>, bool>(
          store: _people, onState: (context, people) => MastodonFollowingControls(
            people: people, onSelected: _selectPeople, onAdd: _addAccount)),
      ]) : const SizedBox.shrink();
      // Bypass the render animation for reduced motion. A zero-duration
      // AnimatedSize can redirty its own layout in Flutter 3.44.
      if (widget.fullClient || MediaQuery.disableAnimationsOf(context)) return child;
      return AnimatedSize(duration: const Duration(milliseconds: 180), alignment: Alignment.topCenter, child: child);
    },
  );

  void _restoreReading(int index) {
    if (!_restoredTabs.add(index)) return;
    final point = _reading.state.points['$_surface:$index'];
    if (point == null) return;
    if (index == 0) {
      final store = context.read<MastodonExploreStore>();
      if (store.state.posts.isEmpty) store.update(MastodonExplorePage(posts: point.posts, tags: point.tags));
    } else if (index == 1) {
      context.read<MastodonLocalStore>().restoreReading(point.posts, point.instance);
    } else if (index == 2) {
      context.read<MastodonFederatedStore>().restoreReading(point.posts, point.instance);
    } else {
      final store = context.read<MastodonFeedStore>();
      final accounts = context.read<MastodonAccountsStore>();
      if (store.state.isEmpty) store.update(point.posts.where((post) => accounts.follows(post.acct)).toList());
    }
  }

  void _onTab(int index) {
    _restoreReading(index);
    _reading.select(index, _people.state);
    _tabs.select(index);
    _chrome.select(true);
    if (!mounted) return;
    if (index == 0) {
      final store = context.read<MastodonExploreStore>();
      if (store.state.posts.isEmpty && store.state.tags.isEmpty) unawaited(store.refresh());
    }
    if (index == 1) {
      final store = context.read<MastodonLocalStore>();
      if (store.state.isEmpty) unawaited(store.refresh());
    }
    if (index == 2) {
      final store = context.read<MastodonFederatedStore>();
      if (store.state.isEmpty) unawaited(store.refresh());
    }
    if (index == 3 && !_people.state) {
      final feed = context.read<MastodonFeedStore>();
      if (feed.state.isEmpty) unawaited(feed.refresh());
    }
  }
}

class _ExplorePane extends StatelessWidget {
  final String slot;
  final ScrollController scrollController;

  const _ExplorePane({required this.slot, required this.scrollController});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final store = context.read<MastodonExploreStore>();
    return ScopedBuilder<MastodonExploreStore, MastodonExplorePage>(
      store: store,
      onLoading: (_) => store.state.posts.isNotEmpty || store.state.tags.isNotEmpty
          ? _exploreBody(context, l10n, store.state)
          : const PluginFeedSkeleton(),
      onError: (_, error) => store.state.posts.isNotEmpty || store.state.tags.isNotEmpty
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

  Widget _exploreBody(BuildContext context, L10n l10n, MastodonExplorePage page) {
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
      child: MastodonReadingList(slot: slot, controller: scrollController,
        posts: page.posts, tags: page.tags,
        heading: page.tags.isEmpty ? null : _TrendingTags(tags: page.tags)),
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
            style: Theme.of(context).textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 8),
          SizedBox(height: MediaQuery.textScalerOf(context).scale(14) + 40, child: ListView.separated(
            scrollDirection: Axis.horizontal, itemCount: tags.length,
            separatorBuilder: (_, _) => const SizedBox(width: 8),
            itemBuilder: (context, index) => Center(child: ActionChip(
              avatar: const Icon(Icons.tag, size: 16), label: Text(tags[index].name),
              onPressed: () => Navigator.push(context,
                MaterialPageRoute(builder: (_) => MastodonTagScreen(tag: tags[index].name))),
            )),
          )),
        ],
      ),
    );
  }
}

class _PublicPane extends StatelessWidget {
  final String slot;
  final MastodonPublicFeedStore store;
  final IconData emptyIcon;
  final ScrollController scrollController;

  const _PublicPane({required this.slot, required this.store, required this.emptyIcon, required this.scrollController});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return ScopedBuilder<MastodonPublicFeedStore, List<MastodonPost>>(
      store: store,
      onLoading: (_) => store.state.isNotEmpty ? _list(context, store.state, store) : const PluginFeedSkeleton(),
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

  Widget _list(BuildContext context, List<MastodonPost> posts, MastodonPublicFeedStore store) {
    return NotificationListener<ScrollNotification>(
      onNotification: (notification) {
        if (notification.metrics.pixels > notification.metrics.maxScrollExtent - 1200) {
          store.loadMore();
        }
        return false;
      },
      child: RefreshIndicator(
        onRefresh: store.refresh,
        child: MastodonReadingList(slot: slot, controller: scrollController,
          posts: posts, instance: store.instance, loadingMore: store.loadingMore),
      ),
    );
  }
}

class _FollowingPane extends StatelessWidget {
  final String slot;
  final ScrollController scrollController;

  const _FollowingPane({required this.slot, required this.scrollController});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final feed = context.read<MastodonFeedStore>();
    return ScopedBuilder<MastodonFeedStore, List<MastodonPost>>(
      store: feed,
      onLoading: (_) => feed.state.isNotEmpty ? _followingList(context, l10n, feed.state) : const PluginFeedSkeleton(),
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

  Widget _followingList(BuildContext context, L10n l10n, List<MastodonPost> posts) {
    if (posts.isEmpty) {
      return ScopedBuilder<MastodonAccountsStore, List<MastodonAccount>>(
        store: context.read<MastodonAccountsStore>(),
        onState: (context, accounts) => EmptyPane(
          icon: Icons.public,
          message: accounts.isEmpty ? l10n.plugin_mastodon_empty : l10n.plugin_mastodon_no_posts,
          scrollController: scrollController,
          onRefresh: () => context.read<MastodonFeedStore>().refresh(force: true),
          action: accounts.isEmpty
              ? FilledButton.icon(
                  onPressed: () => showMastodonSearchSheet(context),
                  icon: const Icon(Icons.explore_outlined),
                  label: Text(l10n.plugin_mastodon_discover),
                )
              : FilledButton.icon(
                  onPressed: () => context.read<MastodonFeedStore>().refresh(force: true),
                  icon: const Icon(Icons.refresh),
                  label: Text(l10n.retry),
                ),
        ),
      );
    }
    return RefreshIndicator(
      onRefresh: () => context.read<MastodonFeedStore>().refresh(force: true),
      child: MastodonReadingList(key: const PageStorageKey('mastodon-following-posts'),
        slot: slot, controller: scrollController, posts: posts),
    );
  }
}

Future<String?> showMastodonAddAccountDialog(BuildContext context, {bool lookup = false}) {
  return showDialog<String>(
    context: context,
    builder: (_) => _MastodonAddAccountDialog(lookup: lookup),
  );
}

class _MastodonAddAccountDialog extends StatefulWidget {
  final bool lookup;

  const _MastodonAddAccountDialog({required this.lookup});

  @override
  State<_MastodonAddAccountDialog> createState() => _MastodonAddAccountDialogState();
}

class _MastodonAddAccountDialogState extends State<_MastodonAddAccountDialog> {
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
    if (_form.currentState?.validate() != true) return;
    Navigator.pop(context, normaliseMastodonAcct(_controller.text));
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return AlertDialog(
      title: Text(widget.lookup ? l10n.plugin_mastodon_lookup : l10n.plugin_mastodon_add),
      content: Form(key: _form, child: TextFormField(
        controller: _controller,
        autofocus: true,
        decoration: InputDecoration(hintText: l10n.plugin_mastodon_handle_hint),
        validator: (value) => normaliseMastodonAcct(value ?? '') == null ? l10n.plugin_mastodon_invalid_handle : null,
        onFieldSubmitted: (_) => _submit(),
      )),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: Text(l10n.cancel)),
        TextButton(onPressed: _submit, child: Text(l10n.ok)),
      ],
    );
  }
}
