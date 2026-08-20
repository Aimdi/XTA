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
import 'package:xta/home/home_model.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/search/search.dart';
import 'package:xta/search/search_scope.dart';
import 'package:xta/subscriptions/subscriptions.dart';
import 'package:xta/trends/trends_screen.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/ui/scroll_to_top.dart';
import 'package:xta/ui/x_look_theme.dart';

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

class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    var prefs = PrefService.of(context);
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

    _buildPages(widget.model.state);
    widget.model.observer(onState: _buildPages);
  }

  void _buildPages(List<HomePage> state) {
    var pages = state
        .where((element) => element.selected)
        .map((e) => e.page)
        .toList();

    if (widget.prefs.getKeys().contains(optionHomeInitialTab)) {
      _initialPage = max(
        0,
        pages.indexWhere(
          (element) => element.id == widget.prefs.get(optionHomeInitialTab),
        ),
      );
    }

    setState(() {
      _pages = pages;
    });
  }

  @override
  Widget build(BuildContext context) {
    return ScopedBuilder<HomeModel, List<HomePage>>.transition(
      store: widget.model,
      onError: (_, e) => ScaffoldErrorWidget(
        prefix: L10n.current.unable_to_load_home_pages,
        error: e,
        stackTrace: null,
        onRetry: () async => await widget.model.resetPages(),
        retryText: L10n.current.reset_home_pages,
      ),
      onLoading: (_) => const Center(child: CircularProgressIndicator()),
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
                name: '',
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
    _pageIndex = ValueNotifier(widget.initialPage);
    _pageController = PageController(initialPage: widget.initialPage);
    for (int i = 0; i < widget.pages.length; i++) {
      _scrollControllers[i] = ScrollController();
      _focusNodes[i] = FocusNode();
    }
  }

  @override
  void didUpdateWidget(covariant ScaffoldWithBottomNavigation oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.pages.length != oldWidget.pages.length) {
      // Dispose controllers that are no longer needed.
      _scrollControllers.keys
          .where((k) => k >= widget.pages.length)
          .toList()
          .forEach((k) {
            _scrollControllers[k]?.dispose();
            _scrollControllers.remove(k);
          });
      _focusNodes.keys.where((k) => k >= widget.pages.length).toList().forEach((
        k,
      ) {
        _focusNodes[k]?.dispose();
        _focusNodes.remove(k);
      });
      // Create controllers for new pages.
      for (int i = 0; i < widget.pages.length; i++) {
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
                    padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
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
            padding: const EdgeInsets.fromLTRB(16, 20, 16, 16),
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
    final theme = Theme.of(context);
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
          ? Icon(Icons.push_pin, size: 16, color: theme.colorScheme.primary)
          : null,
      onTap: () => _goFromDrawer(
        context,
        routeGroup,
        arguments: GroupScreenArguments(id: group.id, name: group.name),
      ),
    );
  }

  Widget _buildScaffold(BuildContext context, L10n l10n) {
    final theme = Theme.of(context);
    final tokens = XLookTokens.maybeOf(context);
    final showLabels = widget.prefs.get(optionShowNavigationLabels) == true;
    final isDark = theme.brightness == Brightness.dark;

    // X chrome: tight capsule, hairline edge, accent on the glyph — not a
    // frosted iOS glass blob. Theme tokens drive the fill so Dim / Lights Out
    // stay consistent with menus and sheets.
    final Color pillFill;
    final Color pillBorder;
    final Color accent;
    if (tokens != null) {
      pillFill = xLookFloatingSurface(tokens);
      pillBorder = tokens.border.withValues(alpha: isDark ? 0.55 : 0.9);
      accent = tokens.accent;
    } else {
      pillFill = theme.colorScheme.surfaceContainerHighest.withValues(
        alpha: 0.94,
      );
      pillBorder = theme.colorScheme.outlineVariant.withValues(alpha: 0.7);
      accent = theme.colorScheme.primary;
    }

    const radius = 24.0;
    final barHeight = showLabels ? 60.0 : 56.0;

    return Scaffold(
      extendBody: true,
      drawer: _buildDrawer(context, l10n),
      body: PageView.builder(
        controller: _pageController,
        // Tabs change from the bar and nowhere else. A drag anywhere in a page
        // used to change them too, which meant every horizontal gesture in the
        // app — a media carousel, a nested tab view, a slider — was competing
        // with the pager for the same finger.
        physics: const NeverScrollableScrollPhysics(),
        itemCount: widget.pages.length,
        onPageChanged: (page) {
          final previous = _pageIndex.value;
          _pageIndex.value = page;
          _adoptSearchScope(previous, page);
        },
        itemBuilder: (context, index) {
          return KeyedSubtree(
            key: PageStorageKey<String>(widget.pages[index].id),
            child: widget.builder(index, _scrollControllers, _focusNodes),
          );
        },
      ),
      // Floating capsule: swipe still changes tab; the page itself never does.
      bottomNavigationBar: SafeArea(
        minimum: const EdgeInsets.fromLTRB(14, 0, 14, 8),
        child: GestureDetector(
          behavior: HitTestBehavior.translucent,
          onHorizontalDragStart: (_) => _dragDistance = 0,
          onHorizontalDragUpdate: (details) =>
              _dragDistance += details.primaryDelta ?? 0,
          onHorizontalDragEnd: (details) =>
              _swipeNavigationBar(details.primaryVelocity ?? 0, _dragDistance),
          child: DecoratedBox(
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(radius),
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withValues(alpha: isDark ? 0.28 : 0.08),
                  blurRadius: 16,
                  offset: const Offset(0, 4),
                ),
              ],
            ),
            child: ClipRRect(
              borderRadius: BorderRadius.circular(radius),
              child: DecoratedBox(
                decoration: BoxDecoration(
                  color: pillFill,
                  borderRadius: BorderRadius.circular(radius),
                  border: Border.all(color: pillBorder, width: 0.5),
                ),
                child: ValueListenableBuilder<int>(
                  valueListenable: _pageIndex,
                  builder: (context, currentPage, _) => NavigationBar(
                    selectedIndex: currentPage,
                    labelBehavior: showLabels
                        ? NavigationDestinationLabelBehavior.alwaysShow
                        : NavigationDestinationLabelBehavior.alwaysHide,
                    shadowColor: Colors.transparent,
                    backgroundColor: Colors.transparent,
                    surfaceTintColor: Colors.transparent,
                    // Accent lives on the icon / label (theme), not a tinted stadium.
                    indicatorColor: Colors.transparent,
                    overlayColor: WidgetStateProperty.resolveWith((states) {
                      if (states.contains(WidgetState.pressed) ||
                          states.contains(WidgetState.focused)) {
                        return accent.withValues(alpha: 0.08);
                      }
                      if (states.contains(WidgetState.hovered)) {
                        return accent.withValues(alpha: 0.04);
                      }
                      return Colors.transparent;
                    }),
                    height: barHeight,
                    destinations: widget.pages.asMap().entries.map((e) {
                      final index = e.key;
                      final page = e.value;
                      final isSelected = currentPage == index;
                      // Subtle lift only when labels are off — with labels the
                      // bold weight + accent colour already mark the tab.
                      final scale =
                          (!showLabels && isSelected && tokens != null)
                          ? 1.05
                          : 1.0;
                      return NavigationDestination(
                        icon: AnimatedScale(
                          scale: scale,
                          duration: Duration(
                            milliseconds: tokens != null ? 200 : 0,
                          ),
                          curve: Curves.easeOutCubic,
                          child: page.icon,
                        ),
                        selectedIcon: AnimatedScale(
                          scale: scale,
                          duration: Duration(
                            milliseconds: tokens != null ? 200 : 0,
                          ),
                          curve: Curves.easeOutCubic,
                          child: page.selectedIcon,
                        ),
                        label: page.titleBuilder(context),
                      );
                    }).toList(),
                    onDestinationSelected: (index) async {
                      if (index == currentPage) {
                        final controller = _scrollControllers[currentPage];
                        final atTop =
                            controller == null ||
                            !controller.hasClients ||
                            controller.offset <= 0;
                        if (!atTop) {
                          await scrollToTop(context, controller);
                        } else if (widget.pages[index].id == 'trending') {
                          _focusNodes[currentPage]?.requestFocus();
                        }
                        return;
                      }
                      unfocusPages();
                      _pageController.jumpToPage(index);
                    },
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  void _adoptSearchScope(int from, int to) {
    if (to < 0 || to >= widget.pages.length) {
      return;
    }
    if (widget.pages[to].id != 'trending') {
      return;
    }
    if (from < 0 || from >= widget.pages.length) {
      return;
    }
    final plugin = pluginById(widget.pages[from].id);
    if (plugin == null || !plugin.supportsSearch) {
      return;
    }
    context.read<SearchScopeStore>().select(plugin.id);
  }

  void _swipeNavigationBar(double velocity, double distance) {
    final target = pageAfterNavigationSwipe(
      current: _pageIndex.value,
      pageCount: widget.pages.length,
      velocity: velocity,
      distance: distance,
    );
    if (target == _pageIndex.value) {
      return;
    }

    unfocusPages();
    if (widget.prefs.get<bool>(optionDisableAnimations) == true) {
      _pageController.jumpToPage(target);
    } else {
      _pageController.animateToPage(
        target,
        duration: const Duration(milliseconds: 250),
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
