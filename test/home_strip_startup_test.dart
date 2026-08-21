import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/home/_feed.dart';
import 'package:xta/home/feed_strip_store.dart';
import 'package:xta/home/feed_strip_tab.dart';
import 'package:xta/home/network_recents_store.dart';
import 'package:xta/home/network_switcher.dart';

Widget _app(Widget child, {required BasePrefService prefs}) {
  return PrefService(
    service: prefs,
    child: MultiProvider(
      providers: [
        Provider(create: (_) => FeedStripStore(prefs)),
        Provider(create: (_) => NetworkRecentsStore(prefs)),
        Provider(create: (_) => FeedTabStore(FeedTab.following)),
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

void main() {
  testWidgets('home strip chrome builds after a JSON-string strip pref', (
    tester,
  ) async {
    final prefs = PrefServiceCache(
      cache: {
        optionHomeFeedStripPlugins: '["reddit","rss"]',
        optionHomeRecentNetworks: '["reddit"]',
        optionPluginRedditEnabled: true,
        optionPluginRssEnabled: true,
      },
    );

    await tester.pumpWidget(
      _app(
        Builder(
          builder: (context) {
            final strip = context.read<FeedStripStore>();
            final available = availableFeedTabsFromIds(strip.state, prefs);
            return DefaultTabController(
              length: available.length,
              child: Column(
                children: [
                  TabBar(
                    isScrollable: true,
                    tabs: [
                      for (final e in available)
                        Tab(
                          child: FeedStripTab(
                            title: e.titleBuilder(context),
                            icon: e.icon ?? e.id.icon,
                          ),
                        ),
                    ],
                  ),
                  IconButton(
                    tooltip: L10n.of(context).feed_strip_add,
                    icon: const Icon(Icons.add),
                    onPressed: () {},
                  ),
                  IconButton(
                    tooltip: L10n.of(context).home_networks_more,
                    icon: const Icon(Icons.public),
                    onPressed: () {},
                  ),
                ],
              ),
            );
          },
        ),
        prefs: prefs,
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Following'), findsOneWidget);
    expect(find.text('For you'), findsOneWidget);
    expect(find.text('Reddit'), findsOneWidget);
    expect(find.text('RSS'), findsOneWidget);
    expect(find.byTooltip('Add timeline'), findsOneWidget);
    expect(find.byTooltip('More networks'), findsOneWidget);
  });

  testWidgets('networks sheet builds with RSS on the strip', (tester) async {
    final prefs = PrefServiceCache(
      cache: {
        optionHomeFeedStripPlugins: [pluginIdRss],
        optionPluginRssEnabled: true,
      },
    );

    await tester.pumpWidget(
      _app(
        Builder(
          builder: (context) {
            return TextButton(
              onPressed: () => showNetworkSwitcherSheet(
                context,
                plugins: pluginsForSwitcher([pluginIdRss]),
                currentId: pluginIdRss,
              ),
              child: const Text('open'),
            );
          },
        ),
        prefs: prefs,
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('open'));
    await tester.pumpAndSettle();

    expect(find.byKey(homeNetworksSheetKey), findsOneWidget);
    expect(find.text('Networks'), findsOneWidget);
    expect(find.text('RSS'), findsOneWidget);
  });
}
