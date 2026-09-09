import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/client/account_fetch_gate.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/home/home_account_filter.dart';
import 'package:xta/home/home_filter_sheet.dart';
import 'package:xta/home/home_group_drawer.dart';
import 'package:xta/home/home_group_filter.dart';
import 'package:xta/subscriptions/widgets/group_unread_badge.dart';
import 'package:xta/ui/x_look_theme.dart';

const _render = ValueKey('home-chrome-review-render');
const _openFilter = ValueKey('review-open-home-filter');
const _openDrawer = ValueKey('review-open-home-drawer');
const _filterSearch = ValueKey('home-filter-search');
const _drawerSearch = ValueKey('home-drawer-group-search');

SubscriptionGroup _group(String id, String name, int members, {bool pinned = false}) => SubscriptionGroup(
  id: id,
  name: name,
  icon: 'rss_feed',
  color: null,
  numberOfMembers: members,
  createdAt: DateTime.utc(2026, 9, 9),
  pinned: pinned,
);

class _HomeChromeHarness {
  final prefs = PrefServiceCache(defaults: {
    optionHomeFeedDisabledAccountIds: '[]',
    optionHomeFeedDisabledGroupIds: '[]',
    optionDisableAnimations: true,
  });
  late final accountsStore = HomeAccountFilterStore(prefs);
  late final groupsStore = HomeGroupFilterStore(prefs);
  final accounts = [
    Account(id: 'main', authHeader: '{}', screenName: 'marcel_reader'),
    Account(id: 'art', authHeader: '{}', screenName: 'illustration_feed'),
  ];
  final groups = [
    _group('art', 'Illustration & photography', 12),
    _group('tech', 'Technology', 8, pinned: true),
    _group('manga', 'Manga & manhwa', 24),
    _group('research', 'Research notes', 5, pinned: true),
  ];
  final openedGroups = <SubscriptionGroup>[];
  int filterChanges = 0;
  int accountAdds = 0;
  int searches = 0;
  int settingsOpens = 0;

  Widget app({double scale = 1, bool dark = false, bool rtl = false, bool noAccounts = false}) => RepaintBoundary(
    key: _render,
    child: PrefService(
      service: prefs,
      child: MaterialApp(
        debugShowCheckedModeBanner: false,
        theme: dark ? xLookLightsOutTheme(null) : xLookLightTheme(null),
        locale: const Locale('en'),
        localizationsDelegates: const [
          L10n.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        supportedLocales: L10n.delegate.supportedLocales,
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context).copyWith(
            textScaler: TextScaler.linear(scale),
            disableAnimations: true,
          ),
          child: Directionality(
            textDirection: rtl ? TextDirection.rtl : TextDirection.ltr,
            child: child!,
          ),
        ),
        home: Builder(builder: (context) => Scaffold(
          appBar: AppBar(title: const Text('Home')),
          drawer: Drawer(child: SafeArea(
            child: HomeGroupDrawer(
              accountHeader: const Padding(
                padding: EdgeInsets.fromLTRB(20, 24, 20, 20),
                child: Row(children: [
                  CircleAvatar(child: Text('M')),
                  SizedBox(width: 12),
                  Expanded(child: Text('Marcel', style: TextStyle(fontWeight: FontWeight.w700))),
                ]),
              ),
              groups: groups,
              unreadIds: const {'tech'},
              onSearch: () => searches++,
              onSettings: () => settingsOpens++,
              onGroup: openedGroups.add,
            ),
          )),
          body: Builder(builder: (scaffoldContext) => Center(child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              FilledButton(
                key: _openFilter,
                onPressed: () => showModalBottomSheet<void>(
                  context: context,
                  useSafeArea: true,
                  isScrollControlled: true,
                  showDragHandle: true,
                  builder: (_) => HomeFilterSheet(
                    accounts: noAccounts ? const [] : accounts,
                    groups: groups,
                    accountsStore: accountsStore,
                    groupsStore: groupsStore,
                    onChanged: () => filterChanges++,
                    onAddAccount: () => accountAdds++,
                  ),
                ),
                child: const Text('Open filters'),
              ),
              const SizedBox(height: 12),
              OutlinedButton(
                key: _openDrawer,
                onPressed: () => Scaffold.of(scaffoldContext).openDrawer(),
                child: const Text('Open groups'),
              ),
            ],
          ))),
        )),
      ),
    ),
  );

  Future<void> close(WidgetTester tester) async {
    await tester.pumpWidget(const SizedBox());
    await tester.pump();
    accountsStore.destroy();
    groupsStore.destroy();
    AccountFetchGate.disabledIds = const {};
  }
}

void _viewport(WidgetTester tester, {double width = 390, double height = 844}) {
  tester.view.physicalSize = Size(width, height);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
  addTearDown(tester.view.resetViewInsets);
}

Finder _account(String id) => find.byKey(ValueKey('home-account-$id'));
Finder _groupFilter(String id) => find.byKey(ValueKey('home-group-filter-$id'));
Finder _drawerGroup(String id) => find.byKey(ValueKey('drawer-group-$id'));
Finder _drawerAction(String label) => find.ancestor(
  of: find.text(label),
  matching: find.byWidgetPredicate((widget) => widget is OutlinedButton),
);

Future<void> _tap(WidgetTester tester, Finder finder) async {
  await tester.ensureVisible(finder);
  await tester.pumpAndSettle();
  await tester.tap(finder);
  await tester.pumpAndSettle();
}

Future<void> _golden(WidgetTester tester, String name) => expectLater(
  find.byKey(_render),
  matchesGoldenFile('../review-artifacts/renders/$name.png'),
);

Future<void> _open(WidgetTester tester, Key key) async {
  await tester.tap(find.byKey(key));
  await tester.pumpAndSettle();
}

void main() {
  setUpAll(() async {
    autoUpdateGoldenFiles = true;
    await (FontLoader('Inter')..addFont(rootBundle.load('assets/fonts/Inter-Regular.ttf'))).load();
    await (FontLoader('MaterialIcons')..addFont(rootBundle.load('fonts/MaterialIcons-Regular.otf'))).load();
  });

  testWidgets('Home filter persists account toggles and protects the last active account', (tester) async {
    _viewport(tester);
    final h = _HomeChromeHarness();
    addTearDown(() => h.close(tester));
    await tester.pumpWidget(h.app());
    await _open(tester, _openFilter);
    expect(tester.widget<SwitchListTile>(_account('main')).value, isTrue);
    await _tap(tester, _account('main'));
    expect(h.accountsStore.state, {'main'});
    expect(homeFeedDisabledIdsFromPrefs(h.prefs.get(optionHomeFeedDisabledAccountIds)), ['main']);
    expect(h.filterChanges, 1);
    final remaining = tester.widget<SwitchListTile>(_account('art'));
    expect(remaining.value, isTrue);
    expect(remaining.onChanged, isNull);
    expect(find.text(L10n.current.home_feed_keep_one_account), findsOneWidget);
    await _golden(tester, 'home-filter-accounts');

    await _tap(tester, _account('art'));
    expect(h.accountsStore.state, {'main'});
    expect(h.filterChanges, 1);
    await _tap(tester, _account('main'));
    expect(h.accountsStore.state, isEmpty);
    expect(tester.widget<SwitchListTile>(_account('art')).onChanged, isNotNull);
    expect(h.filterChanges, 2);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Home filter searches accounts and persists independent group toggles', (tester) async {
    _viewport(tester);
    final h = _HomeChromeHarness();
    addTearDown(() => h.close(tester));
    await tester.pumpWidget(h.app(dark: true));
    await _open(tester, _openFilter);
    await tester.enterText(find.byKey(_filterSearch), '  ILLUSTRATION  ');
    await tester.pumpAndSettle();
    expect(_account('art'), findsOneWidget);
    expect(_account('main'), findsNothing);
    await tester.enterText(find.byKey(_filterSearch), '');
    await tester.pumpAndSettle();
    await _tap(tester, find.text(L10n.current.groups));
    await _tap(tester, _groupFilter('tech'));
    expect(h.groupsStore.state, {'tech'});
    expect(homeFeedDisabledIdsFromPrefs(h.prefs.get(optionHomeFeedDisabledGroupIds)), ['tech']);
    expect(h.accountsStore.state, isEmpty);
    await _golden(tester, 'home-filter-groups');
    await _tap(tester, _groupFilter('tech'));
    expect(h.groupsStore.state, isEmpty);
    expect(h.filterChanges, 2);
    expect(tester.takeException(), isNull);
  });

  testWidgets('an empty Home account filter keeps Add account available', (tester) async {
    _viewport(tester);
    final h = _HomeChromeHarness();
    addTearDown(() => h.close(tester));
    await tester.pumpWidget(h.app(noAccounts: true));
    await _open(tester, _openFilter);
    expect(find.text(L10n.current.home_feed_accounts_empty), findsOneWidget);
    await _tap(tester, find.text(L10n.current.add_account));
    expect(h.accountAdds, 1);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Home group drawer shows pins first and searches without losing group identity', (tester) async {
    _viewport(tester);
    final h = _HomeChromeHarness();
    addTearDown(() => h.close(tester));
    await tester.pumpWidget(h.app());
    await _open(tester, _openDrawer);
    expect(tester.getTopLeft(_drawerGroup('tech')).dy, lessThan(tester.getTopLeft(_drawerGroup('research')).dy));
    expect(tester.getTopLeft(_drawerGroup('research')).dy, lessThan(tester.getTopLeft(_drawerGroup('art')).dy));
    expect(h.groups.map((group) => group.id), ['art', 'tech', 'manga', 'research']);
    final unread = find.descendant(of: _drawerGroup('tech'), matching: find.byType(GroupUnreadBadge));
    expect(tester.widget<GroupUnreadBadge>(unread).unread, isTrue);
    await _golden(tester, 'home-group-drawer');
    await _tap(tester, _drawerAction(L10n.current.search));
    await _tap(tester, _drawerAction(L10n.current.settings));
    expect(h.searches, 1);
    expect(h.settingsOpens, 1);
    await tester.enterText(find.byKey(_drawerSearch), '  MANGA  ');
    await tester.pumpAndSettle();
    expect(_drawerGroup('manga'), findsOneWidget);
    expect(_drawerGroup('tech'), findsNothing);
    await _tap(tester, _drawerGroup('manga'));
    expect(h.openedGroups.single, same(h.groups[2]));
    await tester.enterText(find.byKey(_drawerSearch), 'no such group');
    await tester.pumpAndSettle();
    expect(find.text(L10n.current.no_results), findsOneWidget);
    expect(find.byType(GroupUnreadBadge), findsNothing);
    await tester.enterText(find.byKey(_drawerSearch), '');
    await tester.pumpAndSettle();
    expect(_drawerGroup('tech'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Home filter remains usable at 320px with large text and the keyboard open', (tester) async {
    _viewport(tester, width: 320);
    final h = _HomeChromeHarness();
    addTearDown(() => h.close(tester));
    await tester.pumpWidget(h.app(scale: 1.8));
    await _open(tester, _openFilter);
    await tester.enterText(find.byKey(_filterSearch), 'marcel');
    tester.view.viewInsets = const FakeViewPadding(bottom: 280);
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.byKey(_filterSearch));
    await tester.pumpAndSettle();
    await _golden(tester, 'home-filter-keyboard-large');
    expect(tester.getRect(find.byKey(_filterSearch)).bottom, lessThanOrEqualTo(844 - 280));
    expect(_account('art'), findsNothing);
    await _tap(tester, _account('main'));
    expect(h.accountsStore.state, {'main'});
    final close = find.widgetWithText(FilledButton, L10n.current.close);
    await _tap(tester, close);
    expect(find.byType(HomeFilterSheet), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Home drawer search and selected group remain reachable with large text, RTL and keyboard', (tester) async {
    _viewport(tester, width: 320);
    final h = _HomeChromeHarness();
    addTearDown(() => h.close(tester));
    await tester.pumpWidget(h.app(scale: 1.8, rtl: true, dark: true));
    await _open(tester, _openDrawer);
    await tester.enterText(find.byKey(_drawerSearch), 'manga');
    tester.view.viewInsets = const FakeViewPadding(bottom: 280);
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.byKey(_drawerSearch));
    await tester.pumpAndSettle();
    await _golden(tester, 'home-group-drawer-keyboard-large-rtl');
    expect(tester.getRect(find.byKey(_drawerSearch)).bottom, lessThanOrEqualTo(844 - 280));
    await _tap(tester, _drawerGroup('manga'));
    expect(h.openedGroups.single.id, 'manga');
    expect(tester.takeException(), isNull);
  });
}
