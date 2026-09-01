import 'dart:math';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:quax/constants.dart';
import 'package:quax/generated/l10n.dart';
import 'package:quax/home/edge_swipe.dart';
import 'package:quax/group/group_screen.dart';
import 'package:quax/home/_feed.dart';
import 'package:quax/home/_missing.dart';
import 'package:quax/home/_saved.dart';
import 'package:quax/home/home_chrome.dart';
import 'package:quax/home/home_model.dart';
import 'package:quax/home/home_selection_store.dart';
import 'package:quax/plugins/plugin_registry.dart';
import 'package:quax/search/search.dart';
import 'package:quax/subscriptions/subscriptions.dart';
import 'package:quax/trends/trends_screen.dart';
import 'package:quax/ui/errors.dart';
import 'package:quax/ui/reader_chrome.dart';
import 'package:quax/ui/scroll_to_top.dart';

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
    (c) => L10n.of(c).search,
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
    final prefs = PrefService.of(context);
    final model = context.read<HomeModel>();
    return ScopedBuilder<HomeModel, List<HomePage>>.transition(
      store: model,
      onError: (_, e) => ScaffoldErrorWidget(
        prefix: L10n.current.unable_to_load_home_pages,
        error: e,
        stackTrace: null,
        onRetry: () async => await model.resetPages(),
        retryText: L10n.current.reset_home_pages,
      ),
      onLoading: (_) => const HomeLoadingState(),
      onState: (_, state) {
        final pages = state
            .where((entry) => entry.selected)
            .map((entry) => entry.page)
            .toList(growable: false);
        final initialPage = _initialPage(prefs, pages);
        return ScaffoldWithBottomNavigation(
          pages: pages,
          prefs: prefs,
          initialPage: initialPage,
          builder: (scrollControllers, focusNodes) {
            return List.generate(pages.length, (index) {
              final page = pages[index];
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
                  return SavedScreen(
                    scrollController: scrollControllers[index]!,
                  );
                default:
                  final plugin = pluginById(page.id);
                  final screen = plugin?.homeScreen(
                    scrollController: scrollControllers[index]!,
                  );
                  return screen ?? const MissingScreen();
              }
            });
          },
        );
      },
    );
  }

  int _initialPage(BasePrefService prefs, List<NavigationPage> pages) {
    if (!prefs.getKeys().contains(optionHomeInitialTab)) {
      return 0;
    }
    return max(
      0,
      pages.indexWhere((page) => page.id == prefs.get(optionHomeInitialTab)),
    );
  }
}

class ScaffoldWithBottomNavigation extends StatefulWidget {
  final List<NavigationPage> pages;
  final BasePrefService prefs;
  final int initialPage;
  final List<Widget> Function(
    Map<int, ScrollController> scrollControllers,
    Map<int, FocusNode> focusNodes,
  )
  builder; // changed here

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
  late HomeSelectionStore<int> _pageStore;
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
    _pageStore = HomeSelectionStore<int>(widget.initialPage);
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
      if (widget.pages.isNotEmpty && _pageStore.state >= widget.pages.length) {
        final lastPage = widget.pages.length - 1;
        _pageStore.select(lastPage);
        _pageController.jumpToPage(lastPage);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return ScopedBuilder<HomeSelectionStore<int>, int>(
      store: _pageStore,
      onState: (context, currentPage) => HomePageSwiper(
        movePage: _movePage,
        child: _buildSystemBars(_buildScaffold(context, l10n, currentPage)),
      ),
    );
  }

  Widget _buildSystemBars(Widget child) => XtaSystemBars(child: child);

  /// Moves [direction] pages along, for a tab whose own content swallowed the
  /// swipe. Clamped, so the ends stay put rather than wrapping.
  void _movePage(int direction) {
    final target = _pageStore.state + direction;
    if (target < 0 || target >= widget.pages.length) {
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

  Widget _buildScaffold(BuildContext context, L10n l10n, int currentPage) {
    return Scaffold(
      drawer: Drawer(
        child: ListView(
          children: [
            ListTile(
              leading: const Icon(Icons.search),
              title: Text(l10n.search),
              onTap: () => Navigator.pushNamed(
                context,
                routeSearch,
                arguments: SearchArguments(0, focusInputOnOpen: true),
              ),
            ),
            ListTile(
              leading: const Icon(Icons.settings),
              title: Text(l10n.settings),
              onTap: () => Navigator.pushNamed(context, routeSettings),
            ),
          ],
        ),
      ),
      body: PageView(
        controller: _pageController,
        onPageChanged: _pageStore.select,
        children: widget.builder(_scrollControllers, _focusNodes),
      ),
      // Swiping the bar itself always changes tab, which swiping the page
      // cannot promise: the Subscriptions tab holds its own Groups/People tab
      // view, and a nested horizontal scroll keeps the gesture rather than
      // passing it out at its edge.
      bottomNavigationBar: GestureDetector(
        behavior: HitTestBehavior.translucent,
        // DragEndDetails carries velocity but not how far the finger went, so
        // the distance has to be accumulated as the drag happens.
        onHorizontalDragStart: (_) => _dragDistance = 0,
        onHorizontalDragUpdate: (details) =>
            _dragDistance += details.primaryDelta ?? 0,
        onHorizontalDragEnd: (details) =>
            _swipeNavigationBar(details.primaryVelocity ?? 0, _dragDistance),
        child: HomeNavigationBar(
          selectedIndex: currentPage,
          showLabels: widget.prefs.get(optionShowNavigationLabels),
          disableAnimations:
              widget.prefs.get<bool>(optionDisableAnimations) == true,
          items: widget.pages
              .map(
                (page) => HomeNavigationItem(
                  label: page.titleBuilder(context),
                  icon: page.icon,
                  selectedIcon: page.selectedIcon,
                ),
              )
              .toList(growable: false),
          // Tapping the tab you are already on goes back to the top, whichever
          // tab it is. The Search tab grabbed its field instead, which put a
          // keyboard where a scroll was asked for; it now only takes focus once
          // the list is already at the top, so reaching the search bar is a
          // deliberate second tap rather than a surprise.
          onSelected: (index) async {
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
    );
  }

  void _swipeNavigationBar(double velocity, double distance) {
    final target = pageAfterNavigationSwipe(
      current: _pageStore.state,
      pageCount: widget.pages.length,
      velocity: velocity,
      distance: distance,
    );
    if (target == _pageStore.state) {
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
    _pageController.dispose();
    _pageStore.destroy();
    for (final controller in _scrollControllers.values) {
      controller.dispose();
    }
    for (final focusNode in _focusNodes.values) {
      focusNode.dispose();
    }
    super.dispose();
  }
}
