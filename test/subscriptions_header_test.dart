import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/subscriptions/subscriptions.dart';
import 'package:xta/subscriptions/subscriptions_header.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/ui/x_controls.dart';

class _Groups extends GroupsModel {
  _Groups(super.prefs) {
    update([
      for (final (id, name) in [('1', 'Ai'), ('2', 'Ai Art'), ('3', 'Cooking')])
        SubscriptionGroup(
          id: id,
          name: name,
          icon: defaultGroupIcon,
          color: null,
          numberOfMembers: 3,
          createdAt: DateTime.utc(2024),
        ),
    ]);
  }
}

UserSubscription _person(String handle, String name) => UserSubscription(
  id: handle,
  screenName: handle,
  name: name,
  profileImageUrlHttps: null,
  verified: false,
  createdAt: DateTime.utc(2024),
  inFeed: true,
);

/// The screen at [width] dp in [locale] with [textScale]; pushed over another
/// route when [pushed], as the X reader opens it.
Future<void> _pump(
  WidgetTester tester, {
  Locale locale = const Locale('en'),
  double width = 412,
  double textScale = 1,
  bool pushed = false,
}) async {
  tester.view.physicalSize = Size(width, 900);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
  final prefs = PrefServiceCache(
    cache: {
      optionSubscriptionGroupsOrderByField: 'name',
      optionSubscriptionGroupsOrderByAscending: true,
      optionSubscriptionGroupsLayout: subscriptionGroupsLayoutBoard,
      optionSubscriptionGroupsColumns: 2,
      optionSubscriptionGroupsCompact: false,
      optionSubscriptionOrderByField: 'name',
      optionSubscriptionOrderByAscending: true,
      optionSubscriptionOrderCustom: '',
      optionDisableAnimations: true,
      optionAiBaseUrl: '',
      optionAiApiKey: '',
      optionAiModel: '',
    },
  );
  final groups = _Groups(prefs);
  final people = SubscriptionsModel(prefs, groups)
    ..update([_person('alice', 'Alice Example'), _person('bob', 'Bob Builder')]);
  addTearDown(groups.destroy);
  addTearDown(people.destroy);
  final screen = SubscriptionsScreen(scrollController: ScrollController());
  await tester.pumpWidget(
    PrefService(
      service: prefs,
      child: MultiProvider(
        providers: [
          Provider<GroupsModel>.value(value: groups),
          Provider<SubscriptionsModel>.value(value: people),
        ],
        child: MaterialApp(
          locale: locale,
          localizationsDelegates: const [
            L10n.delegate,
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          supportedLocales: L10n.delegate.supportedLocales,
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(textScale)),
            child: child!,
          ),
          home: pushed ? const Scaffold() : screen,
        ),
      ),
    ),
  );
  if (pushed) {
    tester.state<NavigatorState>(find.byType(Navigator)).push(MaterialPageRoute<void>(builder: (_) => screen));
  }
  await _settle(tester);
}

/// A focused search field blinks its cursor forever, so pumpAndSettle would
/// never return; a few fixed frames cover the tab and entrance animations.
Future<void> _settle(WidgetTester tester) async {
  for (var i = 0; i < 8; i++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

AppBar _appBar(WidgetTester tester) => tester.widget<AppBar>(find.byType(AppBar));

Finder _inHeader(Finder finder) => find.descendant(of: find.byType(AppBar), matching: finder);

void main() {
  group('the header layout', () {
    test('keeps the icons while they fit, then the names, then takes two rows', () {
      expect(subscriptionsHeaderLayout(room: 220, withIcons: 210, labelsOnly: 166), SubscriptionsHeaderLayout.oneRow);
      expect(
        subscriptionsHeaderLayout(room: 200, withIcons: 210, labelsOnly: 166),
        SubscriptionsHeaderLayout.oneRowLabelsOnly,
      );
      expect(subscriptionsHeaderLayout(room: 150, withIcons: 210, labelsOnly: 166), SubscriptionsHeaderLayout.twoRows);
    });

    // The test font draws every glyph a full em wide, so labels measure far
    // wider than in Inter: these widths are where its labels fit.
    for (final locale in const [Locale('en'), Locale('de')]) {
      testWidgets('one row holds the switch, search, add and options in ${locale.languageCode}', (tester) async {
        await _pump(tester, locale: locale, width: 640);

        final l10n = L10n.of(tester.element(find.byType(SubscriptionsScreen)));
        expect(tester.takeException(), isNull);
        expect(_appBar(tester).bottom, isNull);
        expect(_inHeader(find.byType(TabBar)), findsOneWidget);
        expect(find.text(l10n.subscriptions), findsOneWidget, reason: 'the tab, no longer a title above it as well');
        for (final icon in [Icons.folder_outlined, Icons.search, Icons.add, Icons.tune]) {
          expect(_inHeader(find.byIcon(icon)), findsOneWidget);
        }
        expect(tester.getSize(find.byType(AppBar)).height, 52);
      });
    }

    testWidgets('drops the tab icons before it gives up the row', (tester) async {
      await _pump(tester, locale: const Locale('de'), width: 380);

      expect(tester.takeException(), isNull);
      expect(_appBar(tester).bottom, isNull);
      expect(_inHeader(find.text('Gruppen')), findsOneWidget);
      expect(_inHeader(find.byIcon(Icons.folder_outlined)), findsNothing);
    });

    testWidgets('a narrow screen with large text falls back to two rows', (tester) async {
      await _pump(tester, locale: const Locale('de'), width: 320, textScale: 2);

      expect(tester.takeException(), isNull);
      expect(_appBar(tester).bottom, isA<TabBar>());
      expect(find.text('Abos'), findsNWidgets(2), reason: 'the title and the tab, as before');
    });

    testWidgets('keeps Back when the X reader opens the screen', (tester) async {
      await _pump(tester, pushed: true, width: 640);

      expect(tester.takeException(), isNull);
      expect(find.byType(BackButton), findsOneWidget);
      expect(_appBar(tester).bottom, isNull);
    });
  });

  group('the header search', () {
    testWidgets('filters the groups and closes on Back', (tester) async {
      await _pump(tester, width: 640);

      await tester.tap(_inHeader(find.byIcon(Icons.search)));
      await _settle(tester);
      expect(find.byType(XSearchField), findsOneWidget);
      expect(_inHeader(find.byType(TabBar)), findsNothing);

      await tester.enterText(find.byType(TextField), 'ai');
      await _settle(tester);
      expect(find.text('Ai'), findsOneWidget);
      expect(find.text('Ai Art'), findsOneWidget);
      expect(find.text('Cooking'), findsNothing);

      await tester.binding.handlePopRoute();
      await _settle(tester);
      expect(find.byType(XSearchField), findsNothing);
      expect(find.byType(SubscriptionsScreen), findsOneWidget, reason: 'Back closed the search, not the screen');
      expect(find.text('Cooking'), findsOneWidget);
    });

    testWidgets('filters the people by name and handle', (tester) async {
      await _pump(tester, width: 640);
      final l10n = L10n.of(tester.element(find.byType(SubscriptionsScreen)));

      await tester.tap(_inHeader(find.text(l10n.subscriptions)));
      await _settle(tester);
      await tester.tap(_inHeader(find.byIcon(Icons.search)));
      await _settle(tester);
      expect(
        tester.widget<XSearchField>(find.byType(XSearchField)).hintText,
        l10n.search_subscriptions,
        reason: 'the hint says which list is searched',
      );

      await tester.enterText(find.byType(TextField), 'bob');
      await _settle(tester);
      expect(find.textContaining('Bob Builder'), findsOneWidget);
      expect(find.textContaining('Alice Example'), findsNothing);

      await tester.tap(find.byTooltip(MaterialLocalizations.of(tester.element(find.byType(AppBar))).backButtonTooltip));
      await _settle(tester);
      expect(find.textContaining('Alice Example'), findsOneWidget);
    });
  });
}
