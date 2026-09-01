import 'dart:async';
import 'dart:math';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/home/_for_you.dart';
import 'package:xta/home/chrome_avatar.dart';
import 'package:xta/home/feed_strip_store.dart';
import 'package:xta/home/home_account_filter.dart';
import 'package:xta/home/home_group_filter.dart';
import 'package:xta/tweet/paginated_tweet_list.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/_feed_shell.dart';
import 'package:xta/group/feed_refresh_controller.dart';
import 'package:xta/group/feed_session_cache.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/group/group_screen.dart';
import 'package:xta/group/feed_read_position.dart';
import 'package:xta/group/group_unread_store.dart';
import 'package:xta/home/feed_strip_add_sheet.dart';
import 'package:xta/home/feed_strip_tab.dart';
import 'package:xta/home/network_switcher.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/plugins/plugin_marks.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/plugins/reddit/reddit_actions.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/scroll_to_top.dart';

typedef FeedTabTitleBuilder = String Function(BuildContext context);

/// One entry on the home feed strip (Following, For you, or a pinned plugin).
///
/// A value type rather than an enum so plugin pins are not a code change every
/// time a new network ships.
class FeedTab {
  final String id;

  const FeedTab(this.id);

  static const following = FeedTab('following');
  static const foryou = FeedTab('foryou');

  /// Same id as [pluginIdReddit] — kept so Reddit-specific chrome still matches.
  static const reddit = FeedTab(pluginIdReddit);

  /// Prefs and settings historically stored the enum `.name`; keep that shape.
  String get name => id;

  bool get isPlugin => id != following.id && id != foryou.id;

  /// Chip / store mark for this tab — [XtaPlugin.icon], or house / spark for X.
  IconData get icon {
    if (this == following) return followingTabIcon;
    if (this == foryou) return forYouTabIcon;
    return pluginById(id)?.icon ?? Icons.extension_outlined;
  }

  @override
  bool operator ==(Object other) => other is FeedTab && other.id == id;

  @override
  int get hashCode => id.hashCode;
}

class FeedTabOption {
  final FeedTab id;
  final FeedTabTitleBuilder titleBuilder;
  final IconData? icon;
  final Widget? mark;

  FeedTabOption(this.id, this.titleBuilder, {this.icon, this.mark});
}

/// House for Following, spark for For you — matches the chip-style tab row.
const IconData followingTabIcon = Icons.home_outlined;
const IconData forYouTabIcon = Icons.auto_awesome_outlined;

/// Built-in strip entries — plugin pins are appended by [availableFeedTabs].
final List<FeedTabOption> feedTabs = [
  FeedTabOption(
    FeedTab.following,
    (c) => L10n.of(c).following,
    icon: Icons.home_outlined,
  ),
  FeedTabOption(
    FeedTab.foryou,
    (c) => L10n.of(c).foryou,
    icon: Icons.auto_awesome_outlined,
  ),
];

/// The feeds the switcher and home strip currently offer.
List<FeedTabOption> availableFeedTabs(BasePrefService prefs) =>
    availableFeedTabsFromIds(feedStripPluginIds(prefs), prefs);

/// Same shape as [availableFeedTabs], but from an explicit id list (the store).
List<FeedTabOption> availableFeedTabsFromIds(
  List<String> pluginIds,
  BasePrefService prefs,
) {
  final options = <FeedTabOption>[
    FeedTabOption(
      FeedTab.following,
      (c) => L10n.of(c).following,
      icon: Icons.home_outlined,
    ),
    FeedTabOption(
      FeedTab.foryou,
      (c) => L10n.of(c).foryou,
      icon: Icons.auto_awesome_outlined,
    ),
  ];
  for (final pluginId in feedStripVisibleIds(prefs, pluginIds)) {
    final plugin = pluginById(pluginId);
    if (plugin == null ||
        !plugin.isEnabled(prefs) ||
        !plugin.supportsFeedStrip) {
      continue;
    }
    options.add(
      FeedTabOption(
        FeedTab(pluginId),
        (c) => plugin.title(c),
        icon: plugin.icon,
        mark: pluginMark(plugin, size: 16),
      ),
    );
  }
  return List.unmodifiable(options);
}

/// Following / For you first, then every pinned plugin in pin order.
///
/// The strip scrolls, so extra networks stay on the row instead of hiding
/// behind a globe. Recency still decides *which* pin would have been kept
/// when a caller passes a smaller [pluginLimit].
List<FeedTabOption> visibleFeedTabs({
  required List<FeedTabOption> available,
  required List<String> recent,
  required FeedTab current,
  int pluginLimit = kHomeStripRecentLimit,
}) {
  final xTabs = [
    for (final e in available)
      if (!e.id.isPlugin) e,
  ];
  final plugins = [
    for (final e in available)
      if (e.id.isPlugin) e,
  ];
  final limit = plugins.length > pluginLimit ? plugins.length : pluginLimit;
  final visibleIds = recentPluginTabIds(
    pinned: [for (final e in plugins) e.id.id],
    recent: recent,
    currentPluginId: current.isPlugin ? current.id : null,
    limit: limit,
  );
  final byId = {for (final e in plugins) e.id.id: e};
  final visible = <FeedTabOption>[...xTabs];
  for (final id in visibleIds) {
    final option = byId[id];
    if (option != null) visible.add(option);
  }
  return visible;
}

List<FeedTabOption> overflowFeedTabs({
  required List<FeedTabOption> available,
  required List<FeedTabOption> visible,
}) {
  final shown = {for (final e in visible) e.id.id};
  return [
    for (final e in available)
      if (e.id.isPlugin && !shown.contains(e.id.id)) e,
  ];
}

FeedTab feedTabFromId(String? id) {
  if (id == null || id.isEmpty) return FeedTab.following;
  return FeedTab(id);
}

/// Which feed the home screen is showing.
///
/// A store rather than screen state, because the feed can be chosen from
/// somewhere else — the group screen's switcher jumps straight to Following —
/// and the tabs have to follow when it is.
class FeedTabStore extends Store<FeedTab> {
  FeedTabStore(super.initialState);

  void select(FeedTab tab) => update(tab);
}

class FeedScreen extends StatefulWidget {
  final ScrollController scrollController;
  final String id;
  final String name;

  const FeedScreen({
    super.key,
    required this.scrollController,
    required this.id,
    required this.name,
  });

  @override
  State<FeedScreen> createState() => _FeedScreenState();
}

class _FeedScreenState extends State<FeedScreen> {
  TweetFeedController _forYouFeed = TweetFeedController();
  FeedTab? _tab;
  // Bumped on For-you refresh so the tab remounts with a fresh controller —
  // softRefresh alone left mid-scroll users looking at stale tiles until they
  // switched tabs (#168).
  int _forYouEpoch = 0;
  // Bumped with the Following cache evict so toggling an account does not
  // leave home--1 showing pages fetched with the old mix.
  int _followingEpoch = 0;

  /// Bumped only when the feed is chosen from somewhere other than these tabs,
  /// so the bar is rebuilt at the new index. A tap on the bar itself leaves it
  /// alone: the controller survives and the indicator slides, as it should.
  int _externalTabEpoch = 0;
  FeedTabStore? _tabStore;
  FeedStripStore? _stripStore;
  HomeAccountFilterStore? _accountFilter;
  HomeGroupFilterStore? _groupFilter;
  Timer? _unreadReloadDebounce;
  Set<String> _lastDisabledAccountIds = const {};
  Set<String> _lastDisabledGroupIds = const {};
  List<String> _lastStripPlugins = const [];

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final store = context.read<FeedTabStore>();
    if (!identical(store, _tabStore)) {
      _tabStore = store;
      _tab ??= store.state;
      store.observer(onState: _onFeedChosenElsewhere);
    }

    final strip = context.read<FeedStripStore>();
    if (!identical(strip, _stripStore)) {
      _stripStore = strip;
      _lastStripPlugins = List<String>.from(strip.state);
      strip.observer(onState: _onStripChanged);
      strip.seedEnabled();
      // Hidden-tab plugins used to live as Groups chips. Pin them here so
      // switching sites stays on the home strip.
      strip.pinHiddenTabs();
    }

    final filter = context.read<HomeAccountFilterStore>();
    if (!identical(filter, _accountFilter)) {
      _accountFilter = filter;
      _lastDisabledAccountIds = Set<String>.from(filter.state);
      // Settings → Accounts and the manage-accounts sheet both write here.
      // Remount For you whenever the set changes so a toggle on Following (or
      // in Settings) does not leave a KeepAlive'd For you showing spare accounts.
      filter.observer(onState: _onHomeAccountFilterChanged);
    }

    try {
      final groupFilter = context.read<HomeGroupFilterStore>();
      if (!identical(groupFilter, _groupFilter)) {
        _groupFilter = groupFilter;
        _lastDisabledGroupIds = Set<String>.from(groupFilter.state);
        groupFilter.observer(onState: _onHomeGroupFilterChanged);
      }
    } on ProviderNotFoundException {
      _groupFilter = null;
    }
  }

  void _onFeedChosenElsewhere(FeedTab tab) {
    if (!mounted || tab == _tab) {
      return;
    }
    setState(() {
      _tab = tab;
      _externalTabEpoch++;
    });
  }

  void _onStripChanged(List<String> plugins) {
    if (!mounted) return;
    final same =
        plugins.length == _lastStripPlugins.length &&
        List.generate(
          plugins.length,
          (i) => plugins[i] == _lastStripPlugins[i],
        ).every((e) => e);
    if (same) return;
    _lastStripPlugins = List<String>.from(plugins);
    setState(() => _externalTabEpoch++);
  }

  void _onHomeAccountFilterChanged(Set<String> disabled) {
    if (!mounted) {
      return;
    }
    final same =
        disabled.length == _lastDisabledAccountIds.length &&
        disabled.every(_lastDisabledAccountIds.contains);
    if (same) {
      return;
    }
    _lastDisabledAccountIds = Set<String>.from(disabled);
    _reloadHomeFeeds();
  }

  void _onHomeGroupFilterChanged(Set<String> disabled) {
    if (!mounted) {
      return;
    }
    final same =
        disabled.length == _lastDisabledGroupIds.length &&
        disabled.every(_lastDisabledGroupIds.contains);
    if (same) {
      return;
    }
    _lastDisabledGroupIds = Set<String>.from(disabled);
    _reloadHomeFeeds();
  }

  /// Following's [FeedRefreshController] lives *inside* [GroupFeedShell], so
  /// this State's context cannot see it. Evict the cached pages and remount
  /// instead — otherwise the toggle looks like it did nothing.
  void _reloadHomeFeeds() {
    try {
      context.read<FeedSessionCache>().evict(homeFollowingCacheKey(widget.id));
    } on ProviderNotFoundException {
      // Tests and routes without a session cache still remount the tab.
    }
    if (!mounted) {
      return;
    }
    setState(() => _followingEpoch++);
    _remountForYou(scrollToTopFirst: _tab == FeedTab.foryou);
  }

  @override
  void dispose() {
    _unreadReloadDebounce?.cancel();
    _forYouFeed.dispose();
    super.dispose();
  }

  /// Strip taps used to run a full unread SQLite scan on every switch.
  void _reloadUnreadSoon() {
    _unreadReloadDebounce?.cancel();
    _unreadReloadDebounce = Timer(const Duration(milliseconds: 200), () {
      if (!mounted) return;
      maybeGroupUnreadStore(context)?.reload();
    });
  }

  void _remountForYou({required bool scrollToTopFirst}) {
    if (scrollToTopFirst) {
      // Fire-and-forget: remount should not wait on the scroll animation.
      scrollToTop(context, widget.scrollController);
    }
    if (!mounted) {
      return;
    }
    final previous = _forYouFeed;
    setState(() {
      _forYouFeed = TweetFeedController();
      _forYouEpoch++;
    });
    // Dispose after the remount so the outgoing ForYouTweets isn't holding a
    // dead controller for the rest of this frame.
    WidgetsBinding.instance.addPostFrameCallback((_) => previous.dispose());
  }

  void _selectStripTab(FeedTab tab) {
    setState(() {
      _tab = tab;
      _tabStore?.select(tab);
    });
    if (tab.isPlugin) {
      rememberNetwork(context, tab.id);
    }
    _reloadUnreadSoon();
  }

  Future<void> _refreshActiveTab(BuildContext feedContext) async {
    await scrollToTop(context, widget.scrollController);
    if (!mounted || !feedContext.mounted) {
      return;
    }
    await feedContext.read<FeedRefreshController>().refresh();
  }

  String _unreadKeyFor(FeedTab tab) {
    if (tab == FeedTab.following) {
      return feedKeyFollowing;
    }
    if (tab == FeedTab.foryou) {
      return feedKeyForYou;
    }
    return tab.id;
  }

  Widget _pluginBody(FeedTab tab) {
    final plugin = pluginById(tab.id);
    final screen = plugin?.feedStripScreen(
      scrollController: widget.scrollController,
    );
    if (screen != null) return PluginEmbedded(child: screen);
    return Center(child: Text(L10n.of(context).feed_strip_unavailable));
  }

  @override
  Widget build(BuildContext context) {
    // Strip membership is observed separately; listening to every pref here
    // rebuilt Following on theme, zen, and unrelated plugin writes.
    final BasePrefService prefs = PrefService.of(context, listen: false);
    final strip = context.read<FeedStripStore>();
    // Store list is the source of truth once edited; prefs alone lag a frame.
    final available = availableFeedTabsFromIds(strip.state, prefs);
    var tab = _tab ??= feedTabFromId(
      prefs.get<String>(optionHomeDefaultFeedTab),
    );
    // The plugin can be turned off while its feed is the one being shown.
    if (!available.any((e) => e.id == tab)) {
      tab = _tab = FeedTab.following;
    }

    final visible = visibleFeedTabs(
      available: available,
      recent: const [],
      current: tab,
    );

    // TabBar lives in its own DefaultTabController so a strip edit can remount
    // the indicator without recreating NestedScrollView (two outers on the
    // same ScrollController froze, then crashed, home).
    return GroupFeedShell(
      key: ValueKey('home-shell-${tab.id}'),
      scrollController: widget.scrollController,
      groupId: widget.id,
      centerTitle: false,
      leading: const DrawerAvatarButton(),
      titleBuilder: (context) => Text(L10n.of(context).home),
      bottomBuilder: (context) => PreferredSize(
        preferredSize: const Size.fromHeight(kTweetTouchTarget),
        child: DefaultTabController(
          key: ValueKey(
            '${visible.map((e) => e.id.id).join(',')}:$_externalTabEpoch',
          ),
          length: visible.length,
          initialIndex: max(0, visible.indexWhere((e) => e.id == tab)),
          child: Row(
            children: [
              Expanded(
                child: GroupUnreadScope(
                  builder: (context, unreadIds) => TabBar(
                    // The shell draws the bar's hairline; the TabBar's own divider on top
                    // of it would double the line.
                    dividerHeight: 0,
                    isScrollable: true,
                    tabAlignment: TabAlignment.start,
                    indicatorColor: tweetReadableAccentColor(context),
                    indicatorSize: TabBarIndicatorSize.tab,
                    labelColor: tweetPrimaryColor(context),
                    unselectedLabelColor: tweetSecondaryColor(context),
                    labelStyle: tweetLabelStyle(context),
                    unselectedLabelStyle: tweetLabelStyle(
                      context,
                    ).copyWith(fontWeight: FontWeight.w500),
                    tabs: [
                      for (final e in visible)
                        Tab(
                          child: FeedStripTab(
                            title: e.titleBuilder(context),
                            icon: e.icon ?? e.id.icon,
                            mark: e.mark,
                            unread: unreadIds.contains(_unreadKeyFor(e.id)),
                          ),
                        ),
                    ],
                    onTap: (index) {
                      _selectStripTab(visible[index].id);
                    },
                  ),
                ),
              ),
              IconButton(
                tooltip: L10n.of(context).feed_strip_add,
                icon: const Icon(Icons.add),
                onPressed: () async {
                  final pinnedId = await showFeedStripAddSheet(context);
                  if (!context.mounted || pinnedId == null) return;
                  await rememberNetwork(context, pinnedId);
                  if (!context.mounted) return;
                  _selectStripTab(FeedTab(pinnedId));
                },
              ),
            ],
          ),
        ),
      ),
      actionsBuilder: (context) {
        // Reddit brings its own bar: sorting, search and adding a subreddit
        // are what this feed is steered with, and the generic feed actions
        // steer nothing here. Its overflow carries the app settings so they
        // stay reachable from this tab too.
        if (tab == FeedTab.reddit) {
          return const [RedditFeedActions(showAppSettings: true)];
        }

        // Only the feed filters. Refresh is the pull gesture and settings
        // live in the drawer — except on For you, whose pull gesture cannot
        // rebuild the timeline, so it keeps the explicit refresh (#168).
        final model = context.read<GroupModel>();
        final disabledCount =
            _lastDisabledAccountIds.length + _lastDisabledGroupIds.length;
        return defaultGroupActions(
          context,
          model: model,
          showMore: tab == FeedTab.following,
          showRefresh: tab == FeedTab.foryou,
          onRefresh: () => _refreshActiveTab(context),
          showSettings: false,
          extra: [
            IconButton(
              icon: Badge(
                isLabelVisible: disabledCount > 0,
                smallSize: 8,
                child: Icon(
                  disabledCount > 0
                      ? Icons.manage_accounts
                      : Icons.manage_accounts_outlined,
                ),
              ),
              tooltip: L10n.of(context).home_feed_accounts,
              // Store observer remounts For you; sheet only needs to open.
              onPressed: () => showHomeAccountFilterSheet(context),
            ),
          ],
        );
      },
      bodyBuilder: (context) {
        if (tab == FeedTab.following) {
          // With a cache key this feed survives a trip to another tab, the
          // way a pushed group route already does. Without one, every
          // Following -> For you -> Following swipe rebuilt the whole
          // per-chunk fan-out. Namespaced so it never shares state with the
          // pushed route for the same group.
          return SubscriptionGroupScreenContent(
            key: ValueKey(_followingEpoch),
            id: widget.id,
            cacheKey: homeFollowingCacheKey(widget.id),
          );
        }
        if (tab == FeedTab.foryou) {
          return ForYouTweets(
            _forYouFeed,
            key: ValueKey(_forYouEpoch),
            type: 'profile',
            includeReplies: false,
            pref: prefs,
          );
        }
        return _pluginBody(tab);
      },
    );
  }
}
