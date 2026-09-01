import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/combined_groups.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/group/group_switcher.dart';
import 'package:xta/home/_feed.dart';

SubscriptionGroup _group(String id, String name, {int members = 3, bool pinned = false}) => SubscriptionGroup(
      id: id,
      name: name,
      icon: defaultGroupIcon,
      color: null,
      numberOfMembers: members,
      createdAt: DateTime.utc(2026),
      pinned: pinned,
    );

/// A GroupsModel whose state is set directly, so the switcher can be exercised
/// without a database.
class _FakeGroupsModel extends GroupsModel {
  _FakeGroupsModel(BasePrefService prefs, List<SubscriptionGroup> groups) : super(prefs) {
    update(groups);
  }
}

Widget _wrap(Widget child, List<SubscriptionGroup> groups) {
  final prefs = PrefServiceCache(cache: {
    optionSubscriptionGroupsOrderByField: 'name',
    optionSubscriptionGroupsOrderByAscending: true,
  });

  return PrefService(
    service: prefs,
    child: MultiProvider(
      providers: [
        Provider<GroupsModel>(create: (_) => _FakeGroupsModel(prefs, groups)),
        // The title and the sheet both read which groups are being read
        // together, so the switcher cannot be built without one.
        Provider<CombinedGroupsStore>(create: (_) => CombinedGroupsStore()),
        // The title's menu offers the home feeds as well as the groups.
        Provider<FeedTabStore>(create: (_) => FeedTabStore(FeedTab.following)),
      ],
      child: MaterialApp(
        localizationsDelegates: const [
          L10n.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        supportedLocales: L10n.delegate.supportedLocales,
        home: Scaffold(appBar: AppBar(title: child)),
      ),
    ),
  );
}

/// Opens the title's menu and goes through to the full list of groups, which is
/// where the sheet now lives.
Future<void> _openGroupSheet(WidgetTester tester, String title) async {
  await tester.tap(find.text(title));
  await tester.pumpAndSettle();
  await tester.tap(find.text('Groups'));
  await tester.pumpAndSettle();
}

void main() {
  final groups = [
    _group('a', 'Ai Art', members: 15, pinned: true),
    _group('b', 'Anime', members: 3),
    _group('c', 'Demographics', members: 7),
  ];

  testWidgets('the title shows the group and hints that it opens something', (tester) async {
    await tester.pumpWidget(_wrap(
      GroupSwitcherTitle(name: 'Ai Art', currentGroupId: 'a', onSwitch: (_) {}),
      groups,
    ));
    await tester.pumpAndSettle();

    expect(find.text('Ai Art'), findsOneWidget);
    expect(find.byIcon(Icons.expand_more), findsOneWidget);
  });

  testWidgets('the menu offers the home feeds and a way to every group', (tester) async {
    await tester.pumpWidget(_wrap(
      GroupSwitcherTitle(name: 'Ai Art', currentGroupId: 'a', onSwitch: (_) {}),
      groups,
    ));
    await tester.pumpAndSettle();

    await tester.tap(find.text('Ai Art'));
    await tester.pumpAndSettle();

    expect(find.text('Following'), findsOneWidget);
    expect(find.text('For you'), findsOneWidget);
    expect(find.text('Groups'), findsOneWidget);
    // The full list is a row away, not spilled into the menu.
    expect(find.text('Demographics'), findsNothing);
  });

  testWidgets('a pinned group is one tap, without opening the full list', (tester) async {
    SubscriptionGroup? chosen;
    await tester.pumpWidget(_wrap(
      GroupSwitcherTitle(name: 'Anime', currentGroupId: 'b', onSwitch: (g) => chosen = g),
      groups,
    ));
    await tester.pumpAndSettle();

    await tester.tap(find.text('Anime'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Ai Art'));
    await tester.pumpAndSettle();

    expect(chosen?.id, 'a');
  });

  testWidgets('tapping it lists every group with the current one checked', (tester) async {
    await tester.pumpWidget(_wrap(
      GroupSwitcherTitle(name: 'Ai Art', currentGroupId: 'a', onSwitch: (_) {}),
      groups,
    ));
    await tester.pumpAndSettle();

    await _openGroupSheet(tester, 'Ai Art');

    expect(find.text('Anime'), findsOneWidget);
    expect(find.text('Demographics'), findsOneWidget);
    expect(find.text('3 subscriptions'), findsOneWidget);
    expect(find.byIcon(Icons.check), findsOneWidget);
  });

  testWidgets('choosing another group reports it and closes the sheet', (tester) async {
    SubscriptionGroup? chosen;
    await tester.pumpWidget(_wrap(
      GroupSwitcherTitle(name: 'Ai Art', currentGroupId: 'a', onSwitch: (g) => chosen = g),
      groups,
    ));
    await tester.pumpAndSettle();

    await _openGroupSheet(tester, 'Ai Art');
    await tester.tap(find.text('Demographics'));
    await tester.pumpAndSettle();

    expect(chosen?.id, 'c');
    expect(find.text('Demographics'), findsNothing, reason: 'the sheet closed');
  });

  testWidgets('choosing the group you are already in changes nothing', (tester) async {
    var switches = 0;
    await tester.pumpWidget(_wrap(
      GroupSwitcherTitle(name: 'Ai Art', currentGroupId: 'a', onSwitch: (_) => switches++),
      groups,
    ));
    await tester.pumpAndSettle();

    await _openGroupSheet(tester, 'Ai Art');
    // The sheet's own row for the current group, not the app bar title.
    await tester.tap(find.text('15 subscriptions'));
    await tester.pumpAndSettle();

    expect(switches, 0, reason: 'reloading the same feed would be a pointless refresh');
  });

  testWidgets('with no groups the sheet says so instead of showing an empty list', (tester) async {
    await tester.pumpWidget(_wrap(
      GroupSwitcherTitle(name: 'Ai Art', currentGroupId: 'a', onSwitch: (_) {}),
      const [],
    ));
    await tester.pumpAndSettle();

    await _openGroupSheet(tester, 'Ai Art');

    expect(find.text('No groups yet'), findsOneWidget);
  });
}
