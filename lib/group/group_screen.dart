import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/_feed.dart';
import 'package:xta/group/_feed_shell.dart';
import 'package:xta/group/feed_cache.dart';
import 'package:xta/group/feed_chunk_plan.dart';
import 'package:xta/group/feed_session_cache.dart';
import 'package:xta/group/group_chrome.dart';
import 'package:xta/group/group_custom_settings.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/group/group_discovery.dart';
import 'package:xta/group/group_discovery_screen.dart';
import 'package:xta/group/group_feed_title.dart';
import 'package:xta/group/group_view_store.dart';
import 'package:xta/utils/ai_client.dart';
import 'package:xta/home/home_group_filter.dart';
import 'package:xta/tweet/cached_tweet_list.dart';
import 'package:xta/tweet/tweet_context_scope.dart';
import 'package:xta/tweet/tweet_skeleton.dart';
import 'package:xta/ui/errors.dart';
import 'package:provider/provider.dart';

export 'package:xta/group/feed_chunk_hash.dart' show feedChunkSize;
export 'package:xta/group/feed_chunk_plan.dart' show SubscriptionGroupFeedChunk;

class GroupScreenArguments {
  final String id;
  final String name;

  GroupScreenArguments({required this.id, required this.name});

  @override
  String toString() {
    return 'GroupScreenArguments{id: $id, name: $name}';
  }
}

class GroupScreen extends StatefulWidget {
  const GroupScreen({super.key});

  @override
  State<GroupScreen> createState() => _GroupScreenState();
}

class _GroupScreenState extends State<GroupScreen> {
  late final ScrollController _scrollController;

  // Which group this route currently shows. Switching from the title swaps it
  // in place rather than pushing another route, so Back always returns to
  // wherever the first group was opened from, however many groups were visited.
  GroupScreenArguments? _current;

  @override
  void initState() {
    super.initState();
    _scrollController = ScrollController();
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _current ??=
        ModalRoute.of(context)!.settings.arguments as GroupScreenArguments;
  }

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  void _switchTo(SubscriptionGroup group) {
    if (_scrollController.hasClients) {
      _scrollController.jumpTo(0);
    }
    setState(
      () => _current = GroupScreenArguments(id: group.id, name: group.name),
    );
  }

  @override
  Widget build(BuildContext context) {
    final args = _current!;
    return SubscriptionGroupScreen(
      // A new group needs its own shell state, model and feed; keying by id is
      // what makes the swap clean instead of half-updating the old one.
      key: ValueKey(args.id),
      scrollController: _scrollController,
      id: args.id,
      name: args.name,
      // Pushed routes persist their feed state across pop/push via the cache.
      // The cache key matches the groupId so re-pushing the same group restores
      // the previous tweets and scroll offset.
      cacheKey: args.id,
      onSwitchGroup: _switchTo,
      actions: const [],
    );
  }
}

class SubscriptionGroupScreenContent extends StatefulWidget {
  final String id;
  final String? cacheKey;
  final bool mediaOnly;

  const SubscriptionGroupScreenContent({
    super.key,
    required this.id,
    this.cacheKey,
    this.mediaOnly = false,
  });

  @override
  State<SubscriptionGroupScreenContent> createState() =>
      _SubscriptionGroupScreenContentState();
}

class _SubscriptionGroupScreenContentState
    extends State<SubscriptionGroupScreenContent> {
  // Cached tweets shown while the group's subscriptions load, so the feed
  // reveals its content instead of a full-screen spinner on cold start.
  CachedChains? _preview;
  Set<String>? _excludedProfiles;

  @override
  void initState() {
    super.initState();
    // Only the combined "All"/Following feed (id '-1') can preview every cached
    // chunk up front; a specific group needs its own chunk hashes (unknown until
    // loadGroup finishes) to avoid showing tweets from other groups.
    if (widget.id == '-1') {
      _loadPreview();
    }
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (widget.id == '-1' && _excludedProfiles == null) {
      _loadExcludedProfiles();
    }
  }

  Future<void> _loadExcludedProfiles() async {
    HomeGroupFilterStore? filter;
    GroupsModel? groups;
    try {
      filter = context.read<HomeGroupFilterStore>();
      groups = context.read<GroupsModel>();
    } on ProviderNotFoundException {
      if (mounted) {
        setState(() => _excludedProfiles = const {});
      }
      return;
    }
    final disabled = filter.state;
    if (disabled.isEmpty) {
      if (mounted) {
        setState(() => _excludedProfiles = const {});
      }
      return;
    }
    try {
      final members = await groups.listGroupMembers();
      final parents = await readGroupParents(await Repository.readOnly());
      if (!mounted) {
        return;
      }
      setState(() {
        _excludedProfiles = profileIdsExcludedByGroups(
          members: members,
          disabledGroupIds: disabled,
          parentOf: parents,
        );
      });
    } catch (_) {
      if (mounted) {
        setState(() => _excludedProfiles = const {});
      }
    }
  }

  Future<void> _loadPreview() async {
    try {
      var repository = await Repository.readOnly();
      var cached = await readAllCachedChains(repository);
      if (!mounted) return;
      setState(() => _preview = cached);
    } catch (_) {
      // A bad cached chunk must not take the first Following frame down.
    }
  }

  Widget _loadingView() {
    var preview = _preview;
    if (preview != null && preview.chains.isNotEmpty) {
      return TweetContextScope(child: CachedTweetList(preview.chains));
    }
    // Post-shaped placeholders, like the paginated list's own first page — a
    // centred spinner was the one loading state left that didn't look like the
    // feed it was standing in for.
    return const TweetFeedSkeleton();
  }

  @override
  Widget build(BuildContext context) {
    return ScopedBuilder<GroupModel, SubscriptionGroupGet>(
      store: context.read<GroupModel>(),
      onLoading: (_) => _loadingView(),
      onError: (_, error) => ScaffoldErrorWidget(
        error: error,
        stackTrace: null,
        prefix: L10n.current.unable_to_load_the_group,
        onRetry: () => context.read<GroupModel>().loadGroup(),
      ),
      onState: (_, group) {
        // TODO: This is pretty gross. Figure out how to have a "no data" state
        if (group.id.isEmpty) {
          return _loadingView();
        }
        if (widget.id == '-1' && _excludedProfiles == null) {
          return _loadingView();
        }
        // The same flags, order and chunks Discover and the unread dots use.
        final plan = planGroupFeed(
          group,
          prefs: PrefService.of(context, listen: false),
          excludedProfiles: _excludedProfiles ?? const <String>{},
        );

        return SubscriptionGroupFeed(
          group: group,
          chunks: plan.chunks,
          pluginMembers: plan.split.pluginMembers,
          includeReplies: plan.includeReplies,
          includeRetweets: plan.includeRetweets,
          mediaOnly: widget.mediaOnly,
          cacheKey: widget.cacheKey,
          initialPreview: _preview?.chains,
          initialPreviewCachedAt: _preview?.cachedAt,
        );
      },
    );
  }
}

class SubscriptionGroupScreen extends StatefulWidget {
  final ScrollController scrollController;
  final String id;
  final String name;
  final List<Widget>? actions;
  // Forwarded to SubscriptionGroupFeed — see its docs. Null disables caching.
  final String? cacheKey;

  /// When set, the title becomes a group picker and this is called with the
  /// chosen group. Null leaves the title as plain text — the home Following tab
  /// already uses its own feed-tab dropdown there.
  final ValueChanged<SubscriptionGroup>? onSwitchGroup;

  /// The store behind the inline Discover view; tests hand in a canned one.
  final GroupDiscoveryStore Function() createDiscoveryStore;

  const SubscriptionGroupScreen({
    super.key,
    required this.scrollController,
    required this.id,
    required this.name,
    this.actions,
    this.cacheKey,
    this.onSwitchGroup,
    this.createDiscoveryStore = GroupDiscoveryStore.new,
  });

  @override
  State<SubscriptionGroupScreen> createState() =>
      _SubscriptionGroupScreenState();
}

class _SubscriptionGroupScreenState extends State<SubscriptionGroupScreen> {
  late final GroupMediaModeStore _media;
  final _discovery = GroupDiscoveryModeStore();
  late final _discoveryStore = widget.createDiscoveryStore();

  @override
  void dispose() {
    _discovery.destroy();
    _discoveryStore.destroy();
    _media.destroy();
    super.dispose();
  }

  @override
  void initState() {
    super.initState();
    // Restore the filter together with the cached feed it was applied to, so a
    // re-pushed route never shows filtered tweets under an unfiltered toggle.
    final cacheKey = widget.cacheKey;
    _media = GroupMediaModeStore(
      cacheKey != null && context.read<FeedSessionCache>().readMediaOnly(cacheKey),
    );
  }

  void _toggleMediaOnly() {
    _media.toggle();
    final cacheKey = widget.cacheKey;
    if (cacheKey != null) {
      context.read<FeedSessionCache>().saveMediaOnly(cacheKey, _media.state);
    }
  }

  /// Opening Discover, or picking the order the group already has, writes nothing.
  Future<void> _selectOrder(GroupModel model, int order) async {
    if (order == 3) return _openDiscovery();
    _discovery.select(false);
    if (!groupOrderNeedsSave(model.state, order)) return;
    if (order == 2) {
      await model.toggleSubscriptionGroupCustom(true);
    } else {
      await model.toggleSubscriptionGroupPopular(order == 1);
    }
  }

  Future<void> _reloadDiscovery(BuildContext context) async {
    final group = context.read<GroupModel>().state;
    if (group.id.isNotEmpty) await loadGroupDiscovery(context, _discoveryStore, group);
  }

  /// The feed may have scrolled the shell's header away; Discover does not
  /// drive that scroll, so it is brought back or the pane's top hides under it.
  void _openDiscovery() {
    _discovery.select(true);
    if (widget.scrollController.hasClients) widget.scrollController.jumpTo(0);
  }

  /// The sparkle opens the inline Discover and asks the reader's AI to order it;
  /// a load that is still running applies the ranking once it finishes.
  void _rerankDiscovery(BuildContext context) {
    _openDiscovery();
    _discoveryStore.rerankWithAi(AiConfig.fromPrefs(PrefService.of(context, listen: false)));
  }

  Widget? _aiAction(BuildContext context) {
    if (!AiConfig.fromPrefs(PrefService.of(context)).isConfigured) return null;
    return IconButton(
      tooltip: L10n.of(context).group_discovery_ai,
      icon: const Icon(Icons.auto_awesome),
      onPressed: () => _rerankDiscovery(context),
    );
  }

  List<Widget> _discoveryActions(BuildContext context) => [
    ?_aiAction(context),
    IconButton(
      tooltip: MaterialLocalizations.of(context).refreshIndicatorSemanticLabel,
      icon: const Icon(Icons.refresh),
      onPressed: () => _reloadDiscovery(context),
    ),
    ...widget.actions ?? const [],
  ];

  List<Widget> _feedActions(BuildContext context) => [
    ?_aiAction(context),
    ...defaultGroupActions(
      context,
      model: context.read<GroupModel>(),
      // A Home destination already scrolls to the top when reselected.
      // Keep the explicit action only on the pushed Group route.
      scrollToTopController: widget.onSwitchGroup == null
          ? null
          : widget.scrollController,
      showSettings: false,
      extra: widget.actions ?? const [],
    ),
  ];

  void _openCustomSettings(BuildContext context, GroupModel model) {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => GroupCustomSettingsScreen(model: model),
      ),
    );
  }

  PreferredSizeWidget _controls(BuildContext context) {
    final model = context.read<GroupModel>();
    return PreferredSize(
      preferredSize: const Size.fromHeight(kGroupControlBarHeight),
      child: ScopedBuilder<GroupModel, SubscriptionGroupGet>(
        store: model,
        onState: (_, group) => group.id.isEmpty
            ? const SizedBox(height: kGroupControlBarHeight)
            : ScopedBuilder<GroupDiscoveryModeStore, int>(
                store: _discovery,
                onState: (_, _) => ScopedBuilder<GroupMediaModeStore, bool>(
                  store: _media,
                  onState: (_, mediaOnly) => GroupFeedControlBar(
                    group: group,
                    discovery: _discovery.selected,
                    mediaOnly: mediaOnly,
                    onOrderSelected: (order) => _selectOrder(model, order),
                    onMediaToggle: _toggleMediaOnly,
                    onCustomSettings: () => _openCustomSettings(context, model),
                    onDiscoveryClosed: () => _discovery.select(false),
                  ),
                ),
              ),
      ),
    );
  }

  Widget _feed(BuildContext context) => ScopedBuilder<GroupMediaModeStore, bool>(
    store: _media,
    onState: (_, mediaOnly) => SubscriptionGroupScreenContent(
      id: widget.id, cacheKey: widget.cacheKey, mediaOnly: mediaOnly,
    ),
  );

  Widget _discoveryPane(BuildContext context) => ScopedBuilder<GroupModel, SubscriptionGroupGet>(
    store: context.read<GroupModel>(),
    onState: (_, group) => group.id.isEmpty
      ? const SizedBox.shrink()
      : GroupDiscoveryPane(group: group, store: _discoveryStore),
  );

  @override
  Widget build(BuildContext context) {
    return GroupFeedShell(
      scrollController: widget.scrollController,
      groupId: widget.id,
      usesFeedCache: widget.cacheKey != null,
      titleBuilder: (context) => GroupFeedTitle(
        name: widget.name,
        groupId: widget.id,
        onSwitch: widget.onSwitchGroup,
      ),
      // Back closes Discover first; only the feed itself leaves the group.
      bodyBuilder: (context) => ScopedBuilder<GroupDiscoveryModeStore, int>(
        store: _discovery,
        onState: (_, _) => PopScope(
          canPop: !_discovery.selected,
          onPopInvokedWithResult: (didPop, _) {
            if (!didPop) _discovery.select(false);
          },
          child: IndexedStack(
            index: _discovery.selected ? 1 : 0,
            children: [
              HeroMode(enabled: !_discovery.selected, child: TickerMode(
                enabled: !_discovery.selected,
                child: _feed(context),
              )),
              HeroMode(enabled: _discovery.selected, child: TickerMode(
                enabled: _discovery.selected,
                child: _discovery.opened ? _discoveryPane(context) : const SizedBox.shrink(),
              )),
            ],
          ),
        ),
      ),
      bottomBuilder: _controls,
      // Feed-only actions (search loaded posts, filters, scroll to top) would
      // act on the hidden feed while Discover is open, so they step aside with it.
      actionsBuilder: (context) => [
        ScopedBuilder<GroupDiscoveryModeStore, int>(
          store: _discovery,
          onState: (context, _) => Row(
            mainAxisSize: MainAxisSize.min,
            children: _discovery.selected ? _discoveryActions(context) : _feedActions(context),
          ),
        ),
      ],
    );
  }
}
