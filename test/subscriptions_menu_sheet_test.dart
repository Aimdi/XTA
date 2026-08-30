import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/subscriptions/subscriptions_menu_sheet.dart';
import 'package:xta/subscriptions/users_model.dart';

class _FakeGroupsModel extends GroupsModel {
  _FakeGroupsModel(BasePrefService prefs) : super(prefs) {
    update([]);
  }
}

Widget _wrap({
  required Widget child,
  Map<String, dynamic> cache = const {},
}) {
  final prefs = PrefServiceCache(
    cache: {
      optionSubscriptionGroupsOrderByField: 'name',
      optionSubscriptionGroupsOrderByAscending: true,
      optionSubscriptionGroupsLayout: subscriptionGroupsLayoutBoard,
      optionSubscriptionGroupsColumns: 2,
      optionSubscriptionOrderByField: 'name',
      optionSubscriptionOrderByAscending: true,
      ...cache,
    },
  );
  final groups = _FakeGroupsModel(prefs);

  return PrefService(
    service: prefs,
    child: MultiProvider(
      providers: [
        Provider<GroupsModel>.value(value: groups),
        Provider<SubscriptionsModel>(
          create: (_) => SubscriptionsModel(prefs, groups),
        ),
      ],
      child: MaterialApp(
        localizationsDelegates: const [
          L10n.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        supportedLocales: L10n.delegate.supportedLocales,
        home: Scaffold(body: child),
      ),
    ),
  );
}

SubscriptionsMenuSheet _sheet({required bool onGroups}) {
  return SubscriptionsMenuSheet(
    onGroups: onGroups,
    onImportPack: () {},
    onSortUngrouped: () {},
    onImportList: () {},
    onFindBroken: () {},
    onAntennas: () {},
    onDeck: () {},
    onSettings: () {},
  );
}

void main() {
  testWidgets('groups sheet shows the board, not a wall of unlabeled rows', (
    tester,
  ) async {
    await tester.pumpWidget(_wrap(child: _sheet(onGroups: true)));
    await tester.pumpAndSettle();

    final l10n = L10n.of(
      tester.element(find.byType(SubscriptionsMenuSheet)),
    );

    expect(find.text(l10n.groups), findsOneWidget);
    expect(find.text(l10n.subscription_groups_layout_board), findsOneWidget);
    expect(find.text(l10n.subscription_groups_layout_list), findsOneWidget);
    expect(find.text(l10n.subscription_groups_columns), findsOneWidget);
    expect(find.text('2'), findsOneWidget);
    expect(find.text('3'), findsOneWidget);
    expect(find.byIcon(Icons.check), findsOneWidget);
    expect(find.text(l10n.deck_title), findsOneWidget);
    expect(find.byType(PopupMenuItem), findsNothing);
  });

  testWidgets('list layout hides the column picker', (tester) async {
    await tester.pumpWidget(
      _wrap(
        cache: {optionSubscriptionGroupsLayout: subscriptionGroupsLayoutList},
        child: _sheet(onGroups: true),
      ),
    );
    await tester.pumpAndSettle();

    final l10n = L10n.of(
      tester.element(find.byType(SubscriptionsMenuSheet)),
    );

    expect(find.text(l10n.subscription_groups_columns), findsNothing);
    expect(find.text('2'), findsNothing);
  });

  testWidgets('people sheet has no board, columns, or deck', (tester) async {
    await tester.pumpWidget(_wrap(child: _sheet(onGroups: false)));
    await tester.pumpAndSettle();

    final l10n = L10n.of(
      tester.element(find.byType(SubscriptionsMenuSheet)),
    );

    expect(find.text(l10n.subscriptions), findsOneWidget);
    expect(find.text(l10n.subscription_groups_layout_board), findsNothing);
    expect(find.text(l10n.subscription_groups_columns), findsNothing);
    expect(find.text(l10n.deck_title), findsNothing);
    expect(find.text(l10n.username), findsOneWidget);
    expect(find.text(l10n.find_broken_subscriptions), findsOneWidget);
  });

  testWidgets('picking list layout updates the pref and hides columns', (
    tester,
  ) async {
    await tester.pumpWidget(_wrap(child: _sheet(onGroups: true)));
    await tester.pumpAndSettle();

    final l10n = L10n.of(
      tester.element(find.byType(SubscriptionsMenuSheet)),
    );

    await tester.tap(find.text(l10n.subscription_groups_layout_list));
    await tester.pumpAndSettle();

    expect(find.text(l10n.subscription_groups_columns), findsNothing);
  });
}
