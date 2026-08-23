import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/hackernews/hn_client.dart';
import 'package:xta/plugins/hackernews/hn_models.dart';
import 'package:xta/plugins/hackernews/hn_plugin.dart';
import 'package:xta/plugins/hackernews/hn_search_sheet.dart';
import 'package:xta/plugins/hackernews/hn_settings.dart';
import 'package:xta/plugins/hackernews/hn_store.dart';
import 'package:xta/plugins/hackernews/hn_story_card.dart';
import 'package:xta/plugins/plugin_feed_insets.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/plugins/plugin_lazy_tabs.dart';
import 'package:xta/ui/empty_pane.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/ui/feed_list.dart';
import 'package:xta/plugins/plugin_feed_skeleton.dart';

class HnScreen extends StatefulWidget {
  final ScrollController scrollController;

  const HnScreen({super.key, required this.scrollController});

  @override
  State<HnScreen> createState() => _HnScreenState();
}

class _HnScreenState extends State<HnScreen> {
  final _tabs = _HnTabStore();
  late final Map<HnFeed, HnFeedStore> _feeds;
  late final HnFollowingStore _following;

  @override
  void initState() {
    super.initState();
    final client = context.read<HackerNewsClient>();
    _feeds = {
      for (final feed in HnFeed.values) feed: HnFeedStore(client, feed),
    };
    _following = HnFollowingStore(client, context.read<HnFollowsStore>());
    WidgetsBinding.instance.addPostFrameCallback((_) => _prime());
  }

  Future<void> _prime() async {
    if (!mounted) return;
    await context.read<HnLikesStore>().load();
    if (!mounted) return;
    await context.read<HnSavedStore>().load();
    if (!mounted) return;
    await context.read<HnFollowsStore>().load();
    if (!mounted) return;
    await _feeds[HnFeed.top]!.refresh();
  }

  @override
  void dispose() {
    for (final store in _feeds.values) {
      store.destroy();
    }
    _following.destroy();
    _tabs.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Scaffold(
      primary: !PluginEmbedded.maybeOf(context),
      body: ScopedBuilder<_HnTabStore, int>(
        store: _tabs,
        onState: (context, tab) => Column(
          children: [
            PluginHomeChrome(
              accent: hackerNewsBrand,
              tabs: [
                _tab(l10n.plugin_hn_tab_top, Icons.whatshot_outlined, 0),
                _tab(l10n.plugin_hn_tab_new, Icons.schedule_outlined, 1),
                _tab(l10n.plugin_hn_tab_best, Icons.emoji_events_outlined, 2),
                _tab(l10n.plugin_hn_tab_ask, Icons.help_outline, 3),
                _tab(l10n.plugin_hn_tab_show, Icons.slideshow_outlined, 4),
                _tab(l10n.plugin_hn_tab_jobs, Icons.work_outline, 5),
                _tab(l10n.plugin_hn_tab_saved, Icons.bookmark_border, 6),
                _tab(l10n.plugin_hn_tab_following, Icons.people_outline, 7),
              ],
              actions: [
                IconButton(
                  tooltip: l10n.plugin_hn_search,
                  icon: const Icon(Icons.search),
                  onPressed: () => showHnSearchSheet(context),
                ),
                IconButton(
                  tooltip: l10n.settings,
                  icon: const Icon(Icons.settings_outlined),
                  onPressed: () => Navigator.push(
                    context,
                    MaterialPageRoute(builder: (_) => const HnSettingsScreen()),
                  ),
                ),
              ],
            ),
            const Divider(height: 1),
            Expanded(
              child: PluginLazyTabs(
                index: tab,
                children: [
                  (_) => _FeedTab(
                    store: _feeds[HnFeed.top]!,
                    scrollController: widget.scrollController,
                    empty: l10n.plugin_hn_feed_empty,
                    onOpen: () => _feeds[HnFeed.top]!.refresh(),
                  ),
                  (_) => _FeedTab(
                    store: _feeds[HnFeed.newest]!,
                    scrollController: widget.scrollController,
                    empty: l10n.plugin_hn_feed_empty,
                    onOpen: () => _feeds[HnFeed.newest]!.refresh(),
                  ),
                  (_) => _FeedTab(
                    store: _feeds[HnFeed.best]!,
                    scrollController: widget.scrollController,
                    empty: l10n.plugin_hn_feed_empty,
                    onOpen: () => _feeds[HnFeed.best]!.refresh(),
                  ),
                  (_) => _FeedTab(
                    store: _feeds[HnFeed.ask]!,
                    scrollController: widget.scrollController,
                    empty: l10n.plugin_hn_feed_empty,
                    onOpen: () => _feeds[HnFeed.ask]!.refresh(),
                  ),
                  (_) => _FeedTab(
                    store: _feeds[HnFeed.show]!,
                    scrollController: widget.scrollController,
                    empty: l10n.plugin_hn_feed_empty,
                    onOpen: () => _feeds[HnFeed.show]!.refresh(),
                  ),
                  (_) => _FeedTab(
                    store: _feeds[HnFeed.jobs]!,
                    scrollController: widget.scrollController,
                    empty: l10n.plugin_hn_feed_empty,
                    onOpen: () => _feeds[HnFeed.jobs]!.refresh(),
                  ),
                  (_) => _SavedTab(scrollController: widget.scrollController),
                  (_) => _FollowingTab(
                    store: _following,
                    scrollController: widget.scrollController,
                    onOpen: _following.refresh,
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  PluginHomeTab _tab(String label, IconData icon, int index) {
    return PluginHomeTab(
      label: label,
      icon: icon,
      selected: _tabs.state == index,
      onTap: () => _tabs.select(index),
    );
  }
}

class _HnTabStore extends Store<int> {
  _HnTabStore() : super(0);

  void select(int index) => update(index);
}

class _FeedTab extends StatefulWidget {
  final HnFeedStore store;
  final ScrollController scrollController;
  final String empty;
  final Future<void> Function() onOpen;

  const _FeedTab({
    required this.store,
    required this.scrollController,
    required this.empty,
    required this.onOpen,
  });

  @override
  State<_FeedTab> createState() => _FeedTabState();
}

class _FeedTabState extends State<_FeedTab> {
  @override
  void initState() {
    super.initState();
    if (widget.store.state.isEmpty && !widget.store.isLoading) {
      widget.onOpen();
    }
  }

  @override
  Widget build(BuildContext context) {
    return ScopedBuilder<HnFeedStore, List<HnStory>>(
      store: widget.store,
      onLoading: (_) => widget.store.state.isNotEmpty
          ? _StoryList(
              stories: widget.store.state,
              scrollController: widget.scrollController,
              onRefresh: widget.store.refresh,
              onMore: widget.store.loadMore,
              ranked: true,
            )
          : const PluginFeedSkeleton(),
      onError: (_, error) => widget.store.state.isNotEmpty
          ? _StoryList(
              stories: widget.store.state,
              scrollController: widget.scrollController,
              onRefresh: widget.store.refresh,
              onMore: widget.store.loadMore,
              ranked: true,
            )
          : FullPageErrorWidget(
              error: error,
              stackTrace: null,
              prefix: error.toString(),
              onRetry: widget.store.refresh,
            ),
      onState: (_, stories) {
        if (stories.isEmpty) {
          return EmptyPane(
            icon: Icons.forum_outlined,
            message: widget.empty,
            scrollController: widget.scrollController,
            onRefresh: widget.store.refresh,
          );
        }
        return _StoryList(
          stories: stories,
          scrollController: widget.scrollController,
          onRefresh: widget.store.refresh,
          onMore: widget.store.loadMore,
          ranked: true,
        );
      },
    );
  }
}

class _SavedTab extends StatelessWidget {
  final ScrollController scrollController;

  const _SavedTab({required this.scrollController});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final store = context.read<HnSavedStore>();
    return ScopedBuilder<HnSavedStore, List<HnStory>>(
      store: store,
      onState: (_, stories) {
        if (stories.isEmpty) {
          return EmptyPane(
            icon: Icons.bookmark_border,
            message: l10n.plugin_hn_saved_empty,
            scrollController: scrollController,
          );
        }
        return _StoryList(
          stories: stories,
          scrollController: scrollController,
          onRefresh: store.load,
          ranked: false,
        );
      },
    );
  }
}

class _FollowingTab extends StatefulWidget {
  final HnFollowingStore store;
  final ScrollController scrollController;
  final Future<void> Function() onOpen;

  const _FollowingTab({
    required this.store,
    required this.scrollController,
    required this.onOpen,
  });

  @override
  State<_FollowingTab> createState() => _FollowingTabState();
}

class _FollowingTabState extends State<_FollowingTab> {
  @override
  void initState() {
    super.initState();
    widget.onOpen();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final follows = context.read<HnFollowsStore>();
    return ScopedBuilder<HnFollowingStore, List<HnStory>>(
      store: widget.store,
      onLoading: (_) => const PluginFeedSkeleton(),
      onError: (_, error) => FullPageErrorWidget(
        error: error,
        stackTrace: null,
        prefix: error.toString(),
        onRetry: widget.onOpen,
      ),
      onState: (_, stories) {
        if (stories.isEmpty) {
          return EmptyPane(
            icon: Icons.people_outline,
            message: follows.state.isEmpty
                ? l10n.plugin_hn_following_empty
                : l10n.plugin_hn_feed_empty,
            scrollController: widget.scrollController,
            onRefresh: widget.onOpen,
            action: TextButton.icon(
              onPressed: () => showHnSearchSheet(context),
              icon: const Icon(Icons.search),
              label: Text(l10n.plugin_hn_search),
            ),
          );
        }
        return _StoryList(
          stories: stories,
          scrollController: widget.scrollController,
          onRefresh: widget.onOpen,
          ranked: false,
        );
      },
    );
  }
}

class _StoryList extends StatelessWidget {
  final List<HnStory> stories;
  final ScrollController scrollController;
  final Future<void> Function() onRefresh;
  final Future<void> Function()? onMore;
  final bool ranked;

  const _StoryList({
    required this.stories,
    required this.scrollController,
    required this.onRefresh,
    this.onMore,
    required this.ranked,
  });

  @override
  Widget build(BuildContext context) {
    return RefreshIndicator(
      onRefresh: onRefresh,
      child: NotificationListener<ScrollNotification>(
        onNotification: (notification) {
          if (onMore != null &&
              notification.metrics.extentAfter < 400 &&
              notification is ScrollUpdateNotification) {
            onMore!();
          }
          return false;
        },
        child: FeedListView(
          controller: pluginInnerScrollController(context, scrollController),
          padding: pluginFeedPadding(context),
          physics: const AlwaysScrollableScrollPhysics(),
          itemCount: stories.length,
          itemBuilder: (context, index) => HnStoryCard(
            story: stories[index],
            rank: ranked ? index + 1 : null,
          ),
        ),
      ),
    );
  }
}
