import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_search_body.dart';
import 'package:xta/search/search_scope.dart';
import 'package:xta/trends/_list.dart';
import 'package:xta/trends/discover_plugin_search.dart';
import 'package:xta/trends/trends_model.dart';
import 'package:xta/trends/trends_screen.dart';

Widget _app({
  required Widget home,
  required BasePrefService prefs,
  SearchScopeStore? scope,
  DiscoverQueryStore? query,
  RedditClient? reddit,
}) {
  return PrefService(
    service: prefs,
    child: MultiProvider(
      providers: [
        Provider(create: (_) => UserTrendLocationModel(prefs)),
        ProxyProvider<UserTrendLocationModel, TrendsModel>(
          update: (_, locations, previous) =>
              previous ?? TrendsModel(locations),
        ),
        Provider(create: (_) => scope ?? SearchScopeStore()),
        Provider(create: (_) => query ?? DiscoverQueryStore()),
        Provider(
          create: (_) =>
              reddit ??
              RedditClient(
                httpClient: MockClient((request) async {
                  return http.Response(
                    jsonEncode({
                      'kind': 'Listing',
                      'data': {'children': <Object>[]},
                    }),
                    200,
                    headers: {'content-type': 'application/json'},
                  );
                }),
              ),
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
        home: home,
      ),
    ),
  );
}

void main() {
  test('a plugin chip with a query is plugin search, not X trends', () {
    expect(
      discoverBodyKind(pluginIdReddit, 'hu tao'),
      DiscoverBodyKind.pluginSearch,
    );
    expect(discoverBodyKind(pluginIdReddit, ''), DiscoverBodyKind.pluginEmpty);
    expect(discoverBodyKind(searchScopeX, 'hu tao'), DiscoverBodyKind.xTrends);
    expect(discoverBodyKind(searchScopeX, ''), DiscoverBodyKind.xTrends);
  });

  testWidgets('selecting Reddit with a query does not render trend rows', (
    tester,
  ) async {
    final prefs = PrefServiceCache(cache: {optionPluginRedditEnabled: true});
    final scope = SearchScopeStore();
    final query = DiscoverQueryStore();
    query.commit('hu tao');
    scope.select(pluginIdReddit);

    await tester.pumpWidget(
      _app(
        prefs: prefs,
        scope: scope,
        query: query,
        home: TrendsScreen(
          scrollController: ScrollController(),
          focusNode: FocusNode(),
        ),
      ),
    );
    await tester.pump();

    expect(find.byType(TrendsList), findsNothing);
    expect(find.text('Worldwide'), findsNothing);
    expect(find.byType(RedditSearchBody), findsOneWidget);
    expect(find.text('Subreddits'), findsOneWidget);
  });

  testWidgets('empty Reddit Discover is not the X worldwide list', (
    tester,
  ) async {
    final prefs = PrefServiceCache(cache: {optionPluginRedditEnabled: true});
    final scope = SearchScopeStore();
    scope.select(pluginIdReddit);

    await tester.pumpWidget(
      _app(
        prefs: prefs,
        scope: scope,
        home: TrendsScreen(
          scrollController: ScrollController(),
          focusNode: FocusNode(),
        ),
      ),
    );
    await tester.pump();

    expect(find.byType(TrendsList), findsNothing);
    expect(find.byType(DiscoverPluginEmpty), findsOneWidget);
    expect(find.textContaining('Search Reddit'), findsWidgets);
  });
}
