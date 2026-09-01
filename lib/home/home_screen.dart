import 'dart:math';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/group/group_screen.dart';
import 'package:xta/group/group_unread_store.dart';
import 'package:xta/subscriptions/widgets/group_unread_badge.dart';
import 'package:xta/home/_account_avatar.dart';
import 'package:xta/home/chrome_avatar.dart';
import 'package:xta/subscriptions/group_identity.dart';
import 'package:xta/home/_feed.dart';
import 'package:xta/home/_missing.dart';
import 'package:xta/home/_saved.dart';
import 'package:xta/home/edge_swipe.dart';
import 'package:xta/home/home_model.dart';
import 'package:xta/home/home_chrome.dart';
import 'package:xta/home/network_recents_store.dart';
import 'package:xta/home/network_switcher.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/search/search.dart';
import 'package:xta/search/search_scope.dart';
import 'package:xta/subscriptions/subscriptions.dart';
import 'package:xta/trends/trends_screen.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/ui/motion.dart';
import 'package:xta/ui/reader_chrome.dart';
import 'package:xta/ui/scroll_to_top.dart';

typedef NavigationTitleBuilder = String Function(BuildContext context);

class NavigationPage {
  final String id;
  final NavigationTitleBuilder titleBuilder;
  final Widget icon;
  final Widget selectedIcon;

  NavigationPage(this.id, this.titleBuilder, this.icon, this.selectedIcon);
}

final List<NavigationPage> defaultHomePages = [
  NavigationPage(
    'feed',
    (c) => L10n.of(c).home,
    const Icon(Icons.home_outlined),
    const Icon(Icons.home),
  ),
  NavigationPage(
    'subscriptions',
    (c) => L10n.of(c).subscriptions,
    const Icon(Icons.people_outlined),
    const Icon(Icons.people),
  ),
  NavigationPage(
    'trending',
    (c) => L10n.of(c).discover,
    const Icon(Icons.search_outlined),
    const Icon(Icons.search),
  ),
  NavigationPage(
    'saved',
    (c) => L10n.of(c).saved,
    const Icon(Icons.bookmark_border_outlined),
    const Icon(Icons.bookmark),
  ),
];

/// NavigationBar asserts at least two destinations. One leftover tab after
/// a restore, or an empty list, used to take the first frame down.
List<NavigationPage> pagesForNavigationBar(List<NavigationPage> pages) =>
    pages.length < 2 ? List<NavigationPage>.from(defaultHomePages) : pages;

class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    // listen: false — a theme / zen / plugin pref write used to rebuild the
    // whole home (and every keep-alive'd feed) because this ancestor subscribed
    // to every key. The nav bar reads the labels pref itself.
    var prefs = PrefService.of(context, listen: false);
    var model = context.read<HomeModel>();

    return _HomeScreen(prefs: prefs, model: model);
  }
}

class _HomeScreen extends StatefulWidget {
  final BasePrefService prefs;
  final HomeModel model;

  const _HomeScreen({required this.prefs, required this.model});

  @override
  State<_HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<_HomeScreen> {
  int _initialPage = 0;
  List<NavigationPage> _pages = [];

  @override
  void initState() {
    super.initState();

    _pages = _selectedPages(widget.model.state);
    _initialPage = _initialPageOf(_pages);
    widget.model.observer(onState: _onPages);
  }

  List<NavigationPage> _selectedPages(List<HomePage> state) {
    final pages = [
      for (final element in state)
        if (element.selected) element.page,
    ];
    return pagesForNavigationBar(pages);
  }

  int _initialPageOf(List<NavigationPage> pages) {
    if (!widget.prefs.getKeys().contains(optionHomeInitialTab)) {
      return 0;
    }
    return max(
      0,
      pages.indexWhere(
        (element) => element.id == widget.prefs.get(optionHomeInitialTab),
      ),
    );
  }

  void _onPages(List<HomePage> state) {
    final pages = _selectedPages(state);
    _initialPage = _initialPageOf(pages);
    if (!mounted) {
      _pages = pages;
      return;
    }
    setState(() => _pages = pages);
  }

  @override
  Widget build(BuildContext context) {
    // Plain ScopedBuilder: .transition wraps the pager in AnimatedSwitcher,
    // which remounts every keep-alive'd feed when a group tab is added.
    return ScopedBuilder<HomeModel, List<HomePage>>(
      store: widget.model,
      onError: (_, e) => XtaSystemBars(
        child: ScaffoldErrorWidget(
          prefix: L10n.current.unable_to_load_home_pages,
          error: e,
          stackTrace: null,
          onRetry: () async => await widget.model.resetPages(),
          retryText: L10n.current.reset_home_pages,
        ),
      ),
      onLoading: (_) => const HomeLoadingState(),
      onState: (_, state) {
        return ScaffoldWithBottomNavigation(
          pages: _pages,
          prefs: widget.prefs,
          initialPage: _initialPage,
          builder: (index, scrollControllers, focusNodes) {
            final page = _pages[index];
            if (page.id.startsWith('group-')) {
              return SubscriptionGroupScreen(
                scrollController: scrollControllers[index]!,
                id: page.id.replaceAll('group-', ''),
                name: page.titleBuilder(context),
              );
            }
            switch (page.id) {
              case 'feed':
                return FeedScreen(
                  scrollController: scrollControllers[index]!,
                  id: '-1',
                  name: L10n.current.feed,
                );
              case 'subscriptions':
                return SubscriptionsScreen(
                  scrollController: scrollControllers[index]!,
                );
              case 'trending':
                return TrendsScreen(
                  scrollController: scrollControllers[index]!,
                  focusNode: focusNodes[index]!,
                );
              case 'saved':
                return SavedScreen(scrollController: scrollControllers[index]!);
              default:
                final plugin = pluginById(page.id);
                final screen = plugin?.homeScreen(
                  scrollController: scrollControllers[index]!,
                );
                return screen ?? const MissingScreen();
            }
          },
        );
      },
    );
  }
}

class ScaffoldWithBottomNavigation extends StatefulWidget {
  final List<NavigationPage> pages;
  final BasePrefService prefs;
  final int initialPage;
  final Widget Function(
    int index,
    Map<int, ScrollController> scrollControllers,
    Map<int, FocusNode> focusNodes,
  )
  builder;

  const ScaffoldWithBottomNavigation({
    super.key,
    required this.pages,
    required this.prefs,
    required this.initialPage,
    required this.builder,
  });

  @override
  State<ScaffoldWithBottomNavigation> createState() =>
      _ScaffoldWithBottomNavigationState();
}

/// Which page a swipe on the navigation bar should land on.
///
/// Positive [velocity] and [distance] both mean a drag to the right, which goes
/// back a tab. Ends are clamped rather than wrapping around, so a swipe never
/// jumps across the whole bar. Returns [current] when nothing should move.
///
/// A flick *or* a long enough drag counts. Velocity alone is not enough: a
/// deliberate, slow drag — and any drag that pauses before the finger lifts —
/// ends at roughly zero velocity, so gating on speed made the bar ignore it no
/// matter how far it travelled. A mis-tap is still ignored because it has
/// neither speed nor distance, which is what the guard was for.
int pageAfterNavigationSwipe({
  required int current,
  required int pageCount,
  required double velocity,
  double distance = 0,
  double threshold = 120,
  double distanceThreshold = 48,
}) {
  if (pageCount <= 1) {
    return current;
  }

  final flicked = velocity.abs() >= threshold;
  final dragged = distance.abs() >= distanceThreshold;
  if (!flicked && !dragged) {
    return current;
  }

  // A flick states the intent better than where the finger happened to stop,
  // so its direction wins whenever there was one.
  final forward = flicked ? velocity < 0 : distance < 0;

  final next = forward ? current + 1 : current - 1;
  if (next < 0 || next >= pageCount) {
    return current;
  }
  return next;
}

class _ScaffoldWithBottomNavigationState
    extends State<ScaffoldWithBottomNavigation> {
  late PageController _pageController;
  late final ValueNotifier<int> _pageIndex;
  final Map<int, ScrollController> _scrollControllers = {};
  final Map<int, FocusNode> _focusNodes = {};

  /// How far the current drag across the navigation bar has travelled.
  double _dragDistance = 0;

  List<NavigationPage> get _barPages => pagesForNavigationBar(widget.pages);

  /// Drops focus everywhere before a tab change.
  ///
  /// Sparing the page being left kept its search field focused, and tabs are
  /// kept alive — swiping back re-attached a still-focused field and the
  /// keyboard came up on its own.
  void unfocusPages() {
    for (final focusNode in _focusNodes.values) {
      focusNode.unfocus();
    }
    FocusManager.instance.primaryFocus?.unfocus();
  }

  @override
  void initState() {
    super.initState();
    final pageCount = _barPages.length;
    final initial = widget.pages.isEmpty
        ? 0
        : widget.initialPage.clamp(0, _barPages.length - 1);
    _pageIndex = ValueNotifier(initial);
    _pageController = PageController(initialPage: initial);
    for (int i = 0; i < pageCount; i++) {
      _scrollControllers[i] = ScrollController();
      _focusNodes[i] = FocusNode();
    }
  }

  @override
  void didUpdateWidget(covariant ScaffoldWithBottomNavigation oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (_barPages.length != pagesForNavigationBar(oldWidget.pages).length) {
      // Dispose controllers that are no longer needed.
      _scrollControllers.keys
          .where((k) => k >= _barPages.length)
          .toList()
          .forEach((k) {
            _scrollControllers[k]?.dispose();
            _scrollControllers.remove(k);
          });
      _focusNodes.keys.where((k) => k >= _barPages.length).toList().forEach((
        k,
      ) {
        _focusNodes[k]?.dispose();
        _focusNodes.remove(k);
      });
      // Create controllers for new pages.
      for (int i = 0; i < _barPages.length; i++) {
        _scrollControllers.putIfAbsent(i, () => ScrollController());
        _focusNodes.putIfAbsent(i, () => FocusNode());
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);

    return _buildScaffold(context, l10n);
  }

  /// Closes the drawer before going: navigating from an open drawer left it
  /// sitting open under the pushed route, waiting behind the Back button.
  Future<void> _goFromDrawer(
    BuildContext context,
    String route, {
    Object? arguments,
  }) async {
    Navigator.pop(context);
    await Navigator.pushNamed(context, route, arguments: arguments);
    if (context.mounted) {
      await maybeGroupUnreadStore(context)?.reload();
    }
  }

  /// What X keeps in its drawer, translated to this app: the account at the
  /// top, then search and settings, then the groups — which are this app's
  /// Lists, and the part the reader actually reaches for.
  Widget _buildDrawer(BuildContext context, L10n l10n) {
    return Drawer(
      child: SafeArea(
        child: ScopedBuilder<GroupsModel, List<SubscriptionGroup>>(
          store: context.read<GroupsModel>(),
          onError: (context, _) => const SizedBox.shrink(),
          onState: (context, groups) => GroupUnreadScope(
            builder: (context, unreadIds) => ListView(
              padding: EdgeInsets.zero,
              children: [
                _drawerAccountHeader(context, l10n),
                ListTile(
                  leading: const Icon(Icons.search),
                  title: Text(l10n.search),
                  onTap: () => _goFromDrawer(
                    context,
                    routeSearch,
                    arguments: SearchArguments(0, focusInputOnOpen: true),
                  ),
                ),
                ListTile(
                  leading: const Icon(Icons.settings),
                  title: Text(l10n.settings),
                  onTap: () => _goFromDrawer(context, routeSettings),
                ),
                if (groups.isNotEmpty) ...[
                  const Divider(),
                  Padding(
                    padding: const EdgeInsetsDirectional.fromSTEB(
                      16,
                      12,
                      16,
                      4,
                    ),
                    child: Text(
                      l10n.groups,
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ),
                  for (final group in groups)
                    _drawerGroupTile(context, l10n, group, unreadIds),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }

  /// The account block at the top of the drawer. Uses the custom chrome avatar
  /// when set; otherwise a monogram. Long-press the picture to change it;
  /// tapping the block opens settings.
  Widget _drawerAccountHeader(BuildContext context, L10n l10n) {
    final theme = Theme.of(context);
    return FutureBuilder<Account?>(
      future: primaryAccount(),
      builder: (context, snapshot) {
        final account = snapshot.data;
        return InkWell(
          onTap: () => _goFromDrawer(context, routeSettings),
          onLongPress: () => showChromeAvatarSheet(context),
          child: Padding(
            padding: const EdgeInsetsDirectional.fromSTEB(16, 20, 16, 16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                ChromeAvatarMark(account: account, size: 44),
                const SizedBox(height: 10),
                Text(l10n.fritter, style: theme.textTheme.titleLarge),
                if (account?.screenName != null)
                  Text(
                    '@${account!.screenName}',
                    style: theme.textTheme.bodyMedium!.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
              ],
            ),
          ),
        );
      },
    );
  }

  /// One group shortcut: its colour disc, its name, a muted member count, and a
  /// pin when it is pinned (the pinned ones already float to the top).
  Widget _drawerGroupTile(
    BuildContext context,
    L10n l10n,
    SubscriptionGroup group,
    Set<String> unreadIds,
  ) {
    final unread = unreadIds.contains(group.id);
    return ListTile(
      leading: GroupUnreadBadge(
        unread: unread,
        child: GroupMark.forGroup(group, size: 36),
      ),
      title: Row(
        children: [
          Expanded(
            child: Text(
              group.name,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          ),
          if (unread)
            Semantics(
              label: l10n.group_has_unread,
              child: const SizedBox.shrink(),
            ),
        ],
      ),
      subtitle: Text(
        l10n.subscription_group_member_count(group.numberOfMembers),
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      ),
      trailing: group.pinned
          ? Icon(
              Icons.push_pin,
              size: 16,
              color: tweetReadableAccentColor(context),
            )
          : null,
      onTap: () => _goFromDrawer(
        context,
        routeGroup,
        arguments: GroupScreenArguments(id: group.id, name: group.name),
      ),
    );
  }

  Widget _buildScaffold(BuildContext context, L10n l10n) {
    return XtaSystemBars(
      child: Scaffold(
        drawer: _buildDrawer(context, l10n),
        body: HomePageSwiper(
          movePage: _movePageBy,
          child: PageView.builder(
            controller: _pageController,
            // Tabs change from the bar and nowhere else. A drag anywhere in a
            // page used to make media, nested tabs and sliders compete with the
            // pager. Edge-aware children explicitly hand off at their boundary.
            physics: const NeverScrollableScrollPhysics(),
            itemCount: _barPages.length,
            onPageChanged: (page) {
              final previous = _pageIndex.value;
              _pageIndex.value = page;
              _adoptSearchScope(previous, page);
            },
            itemBuilder: (context, index) {
              return KeyedSubtree(
                key: PageStorageKey<String>(_barPages[index].id),
                child: widget.builder(index, _scrollControllers, _focusNodes),
              );
            },
          ),
        ),
        // Labels pref is read here so a Settings toggle does not rebuild feeds.
        bottomNavigationBar: Builder(
          builder: (context) {
            final showLabels =
                PrefService.of(context).get(optionShowNavigationLabels) == true;
            final disableAnimations =
                PrefService.of(
                  context,
                  listen: false,
                ).get<bool>(optionDisableAnimations) ==
                true;
            return GestureDetector(
              behavior: HitTestBehavior.translucent,
              onHorizontalDragStart: (_) => _dragDistance = 0,
              onHorizontalDragUpdate: (details) =>
                  _dragDistance += details.primaryDelta ?? 0,
              onHorizontalDragEnd: (details) => _swipeNavigationBar(
                details.primaryVelocity ?? 0,
                _dragDistance,
              ),
              child: ValueListenableBuilder<int>(
                valueListenable: _pageIndex,
                builder: (context, currentPage, _) {
                  final slots = _bottomBarSlots(context);
                  return HomeNavigationBar(
                    selectedIndex: destinationIndexForPage(slots, currentPage),
                    items: [
                      for (final slot in slots)
                        _navigationItemForSlot(context, slot),
                    ],
                    showLabels: showLabels,
                    disableAnimations: disableAnimations,
                    onSelected: (index) => _onBarDestination(
                      context,
                      slots,
                      index,
                      currentPage,
                    ),
                  );
                },
              ),
            );
          },
        ),
      ),
    );
  }

  void _adoptSearchScope(int from, int to) {
    if (to < 0 || to >= _barPages.length) {
      return;
    }
    if (_barPages[to].id != 'trending') {
      return;
    }
    if (from < 0 || from >= _barPages.length) {
      return;
    }
    final plugin = pluginById(_barPages[from].id);
    if (plugin == null || !plugin.supportsSearch) {
      return;
    }
    context.read<SearchScopeStore>().select(plugin.id);
  }

  List<BottomBarSlot> _bottomBarSlots(BuildContext context) {
    String? recentPluginId;
    try {
      final recents = context.read<NetworkRecentsStore>().state;
      recentPluginId = recents
          .where((id) => _barPages.any((page) => page.id == id))
          .firstOrNull;
    } on ProviderNotFoundException {
      recentPluginId = null;
    }
    return layoutBottomBar([
      for (final page in _barPages) page.id,
    ], recentPluginId: recentPluginId);
  }

  HomeNavigationItem _navigationItemForSlot(
    BuildContext context,
    BottomBarSlot slot,
  ) {
    if (slot.isOverflow) {
      return HomeNavigationItem(
        icon: const Icon(Icons.public_outlined),
        selectedIcon: const Icon(Icons.public),
        label: L10n.of(context).home_networks,
      );
    }
    final page = _barPages[slot.pageIndex!];
    return HomeNavigationItem(
      icon: page.icon,
      selectedIcon: page.selectedIcon,
      label: page.titleBuilder(context),
    );
  }

  Future<void> _onBarDestination(
    BuildContext context,
    List<BottomBarSlot> slots,
    int index,
    int currentPage,
  ) async {
    final slot = slots[index];
    if (slot.isOverflow) {
      await _openBarNetworks(context);
      return;
    }
    final pageIndex = slot.pageIndex!;
    if (pageIndex == currentPage) {
      final atTop = scrollControllerAtTop(_scrollControllers[currentPage]);
      if (!atTop) {
        await scrollToTop(context, _scrollControllers[currentPage]);
      } else if (_barPages[pageIndex].id == 'trending') {
        _focusNodes[currentPage]?.requestFocus();
      }
      return;
    }
    unfocusPages();
    _pageController.jumpToPage(pageIndex);
  }

  Future<void> _openBarNetworks(BuildContext context) async {
    final pluginPages = [
      for (final page in _barPages)
        if (pluginById(page.id) != null) pluginById(page.id)!,
    ];
    if (pluginPages.isEmpty) return;
    List<String> recent = const [];
    try {
      recent = context.read<NetworkRecentsStore>().state;
    } on ProviderNotFoundException {
      recent = const [];
    }
    final currentId = _barPages[_pageIndex.value].id;
    final picked = await showNetworkSwitcherSheet(
      context,
      plugins: pluginPages,
      currentId: pluginById(currentId)?.id,
      recentIds: recent,
    );
    if (!mounted || !context.mounted || picked == null) return;
    final pageIndex = _barPages.indexWhere((page) => page.id == picked);
    if (pageIndex < 0) return;
    await rememberNetwork(context, picked);
    if (!mounted) return;
    unfocusPages();
    _pageController.jumpToPage(pageIndex);
  }

  void _swipeNavigationBar(double velocity, double distance) {
    final target = pageAfterNavigationSwipe(
      current: _pageIndex.value,
      pageCount: _barPages.length,
      velocity: velocity,
      distance: distance,
    );
    _goToPage(target, animate: true);
  }

  void _movePageBy(int direction) {
    final target = max(
      0,
      min(_pageIndex.value + direction, _barPages.length - 1),
    );
    _goToPage(target, animate: true);
  }

  void _goToPage(int target, {required bool animate}) {
    if (target == _pageIndex.value) return;
    unfocusPages();
    final reduceMotion = xtaReduceMotion(context);
    if (!animate || reduceMotion) {
      _pageController.jumpToPage(target);
    } else {
      _pageController.animateToPage(
        target,
        duration: kXtaMotionNavigation,
        curve: Curves.easeOut,
      );
    }
  }

  @override
  void dispose() {
    _pageIndex.dispose();
    _pageController.dispose();
    for (final controller in _scrollControllers.values) {
      controller.dispose();
    }
    for (final focusNode in _focusNodes.values) {
      focusNode.dispose();
    }
    super.dispose();
  }
}
