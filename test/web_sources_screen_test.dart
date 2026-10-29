import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/main.dart' show xtaLocalizationsDelegates;
import 'package:xta/plugins/websources/web_source_add_screen.dart';
import 'package:xta/plugins/websources/web_source_client.dart';
import 'package:xta/plugins/websources/web_source_follows_store.dart';
import 'package:xta/plugins/websources/web_source_item_card.dart';
import 'package:xta/plugins/websources/web_source_screen.dart';
import 'package:xta/plugins/websources/web_sources_screen.dart';
import 'package:xta/plugins/websources/web_sources_store.dart';

import 'support/web_source_fixtures.dart';

PrefServiceCache _prefs() => PrefServiceCache(
  cache: {
    optionPluginWebSourcesInstalled: '[]',
    optionPluginWebSourcesFollows: '[]',
    optionPluginWebSourcesCheckedAt: '',
    optionPluginWebSourcesInHomeFeed: false,
  },
);

WebSourceClient _serving(Map<String, String> pages) => WebSourceClient(
  client: MockClient((request) async {
    final body = pages[request.url.toString()];
    return body == null ? http.Response('', 404) : http.Response.bytes(utf8.encode(body), 200);
  }),
  spacing: Duration.zero,
);

Widget _app(Widget child, {required PrefServiceCache prefs, required WebSourceClient client, double textScale = 1}) =>
    PrefService(
      service: prefs,
      child: MultiProvider(
        providers: [
          Provider<WebSourceClient>.value(value: client),
          Provider<WebSourcesStore>.value(value: WebSourcesStore(prefs)),
          Provider<WebSourceFollowsStore>.value(value: WebSourceFollowsStore(prefs)),
        ],
        child: MaterialApp(
          localizationsDelegates: xtaLocalizationsDelegates,
          supportedLocales: const [Locale('en')],
          builder: (context, app) => MediaQuery(
            data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(textScale)),
            child: app!,
          ),
          home: child,
        ),
      ),
    );

/// Lets requests answer and lists draw. Pictures keep loading in tests, so
/// screens with pictures never settle.
Future<void> _load(WidgetTester tester) async {
  for (var i = 0; i < 10; i++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

void main() {
  testWidgets('the tab offers Add source while nothing is installed', (tester) async {
    await tester.pumpWidget(
      _app(
        WebSourcesScreen(scrollController: ScrollController()),
        prefs: _prefs(),
        client: _serving({}),
      ),
    );

    expect(find.text('No sources yet. Add one from the published list, or by link.'), findsOneWidget);
    expect(find.widgetWithText(FilledButton, 'Add source'), findsOneWidget);
  });

  testWidgets('an update asking for a new site waits for the reader to agree', (tester) async {
    final prefs = _prefs();
    final waiting = InstalledWebSource(
      source: sourceFrom(linksSourceJson()),
      agreedHosts: const ['lobste.rs'],
      origin: Uri.parse('https://example.org/lobsters.json'),
      waiting: sourceFrom(linksSourceJson(version: 2, hosts: ['lobste.rs', 'x.org'])),
    );
    await prefs.set(optionPluginWebSourcesInstalled, jsonEncode([waiting.toJson()]));
    await tester.pumpWidget(
      _app(
        WebSourcesScreen(scrollController: ScrollController()),
        prefs: prefs,
        client: _serving({}),
      ),
    );

    await tester.tap(find.text('Review update'));
    await tester.pumpAndSettle();
    expect(find.text('Update Lobsters?'), findsOneWidget);
    expect(find.text('x.org'), findsOneWidget);
    expect(find.text('It can only read public pages. It cannot use your accounts or post anything.'), findsOneWidget);

    await tester.tap(find.text('OK'));
    await tester.pumpAndSettle();
    expect(readInstalledWebSources(prefs).single.source.version, 2);
    expect(find.text('Review update'), findsNothing);
  });

  testWidgets('a source has a tab per list, its posts, and Follow', (tester) async {
    final prefs = _prefs();
    final source = sourceFrom(linksSourceJson());
    await tester.pumpWidget(
      _app(
        WebSourceScreen(source: source),
        prefs: prefs,
        client: _serving({'https://lobste.rs/page/1.json': linksPage}),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Hottest'), findsWidgets);
    expect(find.text('Newest'), findsWidgets);
    expect(find.text('Rust & Dart'), findsOneWidget);
    expect(find.text('3 comments'), findsOneWidget);
    expect(find.textContaining('12 points'), findsOneWidget);

    await tester.tap(find.byTooltip('Follow'));
    await tester.pumpAndSettle();
    expect(readWebSourceFollows(prefs).single.id, 'websources:lobsters/hottest');
    expect(find.byTooltip('Unfollow'), findsOneWidget);
  });

  testWidgets('a gallery list is a grid of pictures', (tester) async {
    final source = sourceFrom(gallerySourceJson());
    await tester.pumpWidget(
      _app(
        WebSourceScreen(source: source),
        prefs: _prefs(),
        client: _serving({
          'https://images-api.nasa.gov/search?q=moon&media_type=image&page=1': jsonEncode({
            'collection': {
              'items': [
                for (var i = 0; i < 4; i++)
                  {
                    'data': [
                      {'nasa_id': 'id$i', 'title': 'Picture $i'},
                    ],
                    'links': [
                      {'href': 'https://images-assets.nasa.gov/$i.jpg'},
                    ],
                  },
              ],
            },
          }),
        }),
      ),
    );
    await _load(tester);

    expect(find.byType(WebSourcePictureTile), findsNWidgets(4));
  });

  testWidgets('posts stay readable at large text sizes', (tester) async {
    final source = sourceFrom(postsSourceJson());
    await tester.pumpWidget(
      _app(
        WebSourceScreen(source: source),
        prefs: _prefs(),
        client: _serving({'https://lemmy.world/api/v3/post/list?sort=Hot&page_cursor=': postsPage}),
        textScale: 2,
      ),
    );
    await _load(tester);

    expect(find.text('Hello'), findsOneWidget);
    expect(find.text('First post'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Add source lists the published sources and adds one once agreed', (tester) async {
    final prefs = _prefs();
    await tester.pumpWidget(
      _app(
        const WebSourceAddScreen(),
        prefs: prefs,
        client: _serving({
          defaultWebSourceCatalogueUrl: jsonEncode({
            'format': 1,
            'sources': [
              {'id': 'lobsters', 'name': 'Lobsters', 'description': 'Links', 'url': 'sources/lobsters.json'},
            ],
          }),
          'https://raw.githubusercontent.com/$githubRepo/$githubPublishBranch/sources/lobsters.json': jsonEncode(
            linksSourceJson(),
          ),
        }),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.widgetWithText(FilledButton, 'Add'));
    await tester.pumpAndSettle();
    expect(find.text('Add Lobsters?'), findsOneWidget);
    expect(find.text('lobste.rs'), findsOneWidget);

    await tester.tap(find.descendant(of: find.byType(AlertDialog), matching: find.widgetWithText(FilledButton, 'Add')));
    await tester.pumpAndSettle();
    expect(readInstalledWebSources(prefs).single.source.id, 'lobsters');
    expect(find.text('Added'), findsOneWidget);
  });

  testWidgets('a refused source file says why and where', (tester) async {
    final prefs = _prefs();
    await tester.pumpWidget(
      _app(
        const WebSourceAddScreen(),
        prefs: prefs,
        client: _serving({
          'https://example.org/bad.json': jsonEncode({...linksSourceJson(), 'homepage': 'https://elsewhere.org'}),
        }),
      ),
    );
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(TextField), 'https://example.org/bad.json');
    await tester.tap(find.text('Add from link'));
    await tester.pumpAndSettle();

    expect(find.text('This source would read a site it does not list, or without HTTPS.'), findsOneWidget);
    expect(find.text('Where: homepage'), findsOneWidget);
    expect(readInstalledWebSources(prefs), isEmpty);
  });
}
