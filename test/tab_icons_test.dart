import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/home/_feed.dart';
import 'package:xta/home/feed_strip_tab.dart';
import 'package:xta/subscriptions/subscriptions.dart';

Widget _app(Widget Function(BuildContext context) builder) {
  return MaterialApp(
    localizationsDelegates: const [
      L10n.delegate,
      GlobalMaterialLocalizations.delegate,
      GlobalWidgetsLocalizations.delegate,
      GlobalCupertinoLocalizations.delegate,
    ],
    supportedLocales: L10n.delegate.supportedLocales,
    home: Builder(builder: builder),
  );
}

void main() {
  testWidgets('Abos section tabs show folder and people beside the labels', (
    tester,
  ) async {
    await tester.pumpWidget(
      _app((context) {
        final l10n = L10n.of(context);
        return DefaultTabController(
          length: 2,
          child: Scaffold(
            appBar: AppBar(bottom: TabBar(tabs: subscriptionSectionTabs(l10n))),
          ),
        );
      }),
    );
    await tester.pumpAndSettle();

    expect(find.byIcon(subscriptionGroupsTabIcon), findsOneWidget);
    expect(find.byIcon(subscriptionPeopleTabIcon), findsOneWidget);
    expect(find.text('Groups'), findsOneWidget);
    expect(find.text('Subscriptions'), findsOneWidget);
  });

  testWidgets('home strip tabs show plugin icons beside the labels', (
    tester,
  ) async {
    final prefs = PrefServiceCache(
      cache: {
        optionPluginSubstackEnabled: true,
        optionPluginPixivEnabled: true,
        optionPluginBooruEnabled: true,
      },
    );
    final tabs = availableFeedTabsFromIds([
      pluginIdSubstack,
      pluginIdPixiv,
      pluginIdBooru,
    ], prefs);

    await tester.pumpWidget(
      _app((context) {
        return DefaultTabController(
          length: tabs.length,
          child: Scaffold(
            appBar: AppBar(
              bottom: TabBar(
                isScrollable: true,
                tabs: [
                  for (final e in tabs)
                    Tab(
                      child: FeedStripTab(
                        title: e.titleBuilder(context),
                        icon: e.id.icon,
                      ),
                    ),
                ],
              ),
            ),
          ),
        );
      }),
    );
    await tester.pumpAndSettle();

    expect(find.byIcon(followingTabIcon), findsOneWidget);
    expect(find.byIcon(forYouTabIcon), findsOneWidget);
    expect(find.byIcon(Icons.newspaper), findsOneWidget);
    expect(find.byIcon(Icons.brush), findsOneWidget);
    expect(find.byIcon(Icons.photo_library_outlined), findsOneWidget);
    expect(find.text('Substack'), findsOneWidget);
    expect(find.text('Pixiv'), findsOneWidget);
    expect(find.text('Booru'), findsOneWidget);
  });
}
