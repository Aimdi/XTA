import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/home/chrome_avatar.dart';
import 'package:xta/home/home_account_filter.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/plugins/x/x_plugin.dart';
import 'package:xta/plugins/x/x_reader_chrome.dart';
import 'package:xta/plugins/x/x_reader_routes.dart';
import 'package:xta/search/search.dart';
import 'package:xta/saved/saved_screen.dart';
import 'package:xta/settings/_account.dart';
import 'package:xta/subscriptions/subscriptions.dart';
import 'package:xta/subscriptions/users_model.dart';

import 'support/reader_review_harness.dart';

Widget _app(Widget child, {RouteFactory? onGenerateRoute}) => MaterialApp(
  localizationsDelegates: const [
    L10n.delegate,
    GlobalMaterialLocalizations.delegate,
    GlobalWidgetsLocalizations.delegate,
    GlobalCupertinoLocalizations.delegate,
  ],
  supportedLocales: L10n.delegate.supportedLocales,
  onGenerateRoute: onGenerateRoute,
  home: Scaffold(body: child),
);

Future<void> _settleReaderRoute(WidgetTester tester) async {
  for (var attempt = 0; attempt < 3; attempt++) {
    await tester.pump(const Duration(milliseconds: 150));
    await tester.runAsync(() async {
      await Future<void>.delayed(const Duration(milliseconds: 20));
    });
  }
  await tester.pumpAndSettle(
    const Duration(milliseconds: 100),
    EnginePhase.sendSemanticsUpdate,
    const Duration(seconds: 3),
  );
}

void main() {
  test('X advertises its existing search capability', () {
    expect(XPlugin().supportsSearch, isTrue);
  });

  for (final query in <String?>[null, '   ', '  from:reader flutter  ']) {
    testWidgets('X search routes with query/focus preserved: $query', (tester) async {
      RouteSettings? pushed;
      await tester.pumpWidget(
        _app(
          Builder(
            builder: (context) => TextButton(
              onPressed: () => XPlugin().openSearch(context, initialQuery: query),
              child: const Text('Open'),
            ),
          ),
          onGenerateRoute: (settings) {
            pushed = settings;
            return MaterialPageRoute<void>(
              settings: settings,
              builder: (_) => const Scaffold(body: Text('Results')),
            );
          },
        ),
      );

      await tester.tap(find.text('Open'));
      await tester.pumpAndSettle();

      expect(pushed?.name, routeSearch);
      final arguments = pushed!.arguments! as SearchArguments;
      expect(arguments.initialTab, 0);
      expect(arguments.query, query?.trim());
      expect(arguments.focusInputOnOpen, query == null || query.trim().isEmpty);
      expect(find.text('Results'), findsOneWidget);
    });
  }

  testWidgets('reader exposes search and a heading without a dead tab', (tester) async {
    var searches = 0;
    final semantics = tester.ensureSemantics();
    await tester.pumpWidget(
      _app(
        PluginEmbedded(
          child: XReaderChrome(onSearch: () => searches++, onOpenDestination: (_) {}),
        ),
      ),
    );

    expect(find.byTooltip('Search X'), findsOneWidget);
    expect(find.byType(PluginHomeChrome), findsNothing);
    expect(tester.getSemantics(find.text('For you')), matchesSemantics(label: 'For you', isHeader: true));
    await tester.tap(find.byKey(const ValueKey('x-reader-search')));
    expect(searches, 1);
    semantics.dispose();
  });

  testWidgets('reader menu dispatches every named library destination', (tester) async {
    final opened = <XReaderDestination>[];
    await tester.pumpWidget(_app(XReaderChrome(onSearch: () {}, onOpenDestination: opened.add)));

    expect(find.byTooltip('X: Subscriptions, Saved, Accounts'), findsOneWidget);
    for (final destination in XReaderDestination.values) {
      await tester.tap(find.byKey(const ValueKey('x-reader-library-menu')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(ValueKey('x-reader-${destination.name}')));
      await tester.pumpAndSettle();
      expect(opened.last, destination);
    }
    expect(opened, XReaderDestination.values);
  });

  testWidgets('real reader destinations inherit stores and return safely', (tester) async {
    sqfliteFfiInit();
    databaseFactory = databaseFactoryFfiNoIsolate;
    final directory = await tester.runAsync(() async {
      final temporary = await Directory.systemTemp.createTemp('xta-x-hub');
      await databaseFactory.setDatabasesPath(temporary.path);
      await Repository().migrate();
      await Repository.readOnly();
      return temporary;
    });
    final harness = ReaderReviewHarness();
    harness.saved.update([]);
    final subscriptions = SubscriptionsModel(harness.prefs, harness.groups);
    final accounts = HomeAccountFilterStore(harness.prefs);
    final avatar = ChromeAvatarStore(harness.prefs);
    addTearDown(() async {
      await harness.close(tester);
      await subscriptions.destroy();
      await accounts.destroy();
      await avatar.destroy();
      await tester.runAsync(() async {
        final reader = await Repository.readOnly();
        await reader.close();
        final database = await Repository.writable();
        await database.close();
        await directory!.delete(recursive: true);
      });
    });

    await tester.pumpWidget(
      harness.providers(
        MultiProvider(
          providers: [
            Provider<SubscriptionsModel>.value(value: subscriptions),
            Provider<HomeAccountFilterStore>.value(value: accounts),
            Provider<ChromeAvatarStore>.value(value: avatar),
          ],
          child: harness.app(
            child: Scaffold(
              body: Builder(
                builder: (context) => XReaderChrome(
                  onSearch: () {},
                  onOpenDestination: (destination) => openXReaderDestination(context, destination),
                ),
              ),
            ),
          ),
        ),
      ),
    );

    for (final destination in XReaderDestination.values) {
      await tester.tap(find.byKey(const ValueKey('x-reader-library-menu')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(ValueKey('x-reader-${destination.name}')));
      await _settleReaderRoute(tester);
      final screen = switch (destination) {
        XReaderDestination.subscriptions => find.byType(SubscriptionsScreen),
        XReaderDestination.saved => find.byType(SavedScreen),
        XReaderDestination.accounts => find.byType(SettingsAccountFragment),
      };
      expect(screen, findsOneWidget);
      if (destination == XReaderDestination.accounts) {
        expect(find.text(L10n.current.home_feed_accounts_empty), findsOneWidget);
      }
      expect(find.byType(BackButton), findsOneWidget);
      expect(tester.takeException(), isNull);
      await tester.tap(find.byType(BackButton));
      await tester.pumpAndSettle();
      expect(screen, findsNothing);
      expect(find.byKey(const ValueKey('x-reader-search')), findsOneWidget);
      expect(tester.takeException(), isNull);
    }
  });

  testWidgets('reader tools fit narrow screens with larger text', (tester) async {
    tester.view.physicalSize = const Size(320, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    await tester.pumpWidget(
      _app(
        MediaQuery(
          data: const MediaQueryData(textScaler: TextScaler.linear(2.0)),
          child: XReaderChrome(onSearch: () {}, onOpenDestination: (_) {}),
        ),
      ),
    );
    expect(tester.takeException(), isNull);
    expect(tester.getSize(find.byKey(const ValueKey('x-reader-search'))).height, greaterThanOrEqualTo(48));
    await tester.tap(find.byKey(const ValueKey('x-reader-library-menu')));
    await tester.pumpAndSettle();
    expect(find.text('Subscriptions'), findsOneWidget);
    expect(find.text('Saved'), findsOneWidget);
    expect(find.text('Accounts'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
