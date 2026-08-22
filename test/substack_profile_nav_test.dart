import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/substack/substack_archive_screen.dart';
import 'package:xta/plugins/substack/substack_client.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/plugins/substack/substack_post_card.dart';
import 'package:xta/plugins/substack/substack_store.dart';

const _speedrun = SubstackPublication(
  subdomain: 'speedrun',
  baseUrl: 'https://speedrun.substack.com',
  name: 'a16z speedrun',
);

SubstackPost _post({
  required String title,
  required String name,
  String slug = 'a-post',
}) {
  return SubstackPost(
    id: slug,
    title: title,
    slug: slug,
    publicationBaseUrl: 'https://speedrun.substack.com',
    publicationName: name,
  );
}

http.Response _json(Object body, {int status = 200}) {
  return http.Response(
    body is String ? body : jsonEncode(body),
    status,
    headers: {'content-type': 'application/json'},
  );
}

Widget _app({
  required Widget home,
  SubstackClient? client,
  BasePrefService? prefs,
}) {
  final cache = prefs ?? PrefServiceCache(cache: {});
  return MultiProvider(
    providers: [
      Provider(
        create: (_) =>
            client ??
            SubstackClient(httpClient: MockClient((_) async => _json([]))),
      ),
      Provider(create: (_) => SubstackPublicationsStore(cache)),
      Provider(create: (_) => SubstackReadStore(cache)),
      Provider(create: (_) => SubstackLikesStore(cache)),
      Provider(create: (_) => SubstackSavedStore(cache)),
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
  );
}

void main() {
  test('www is never a publication title', () {
    expect(
      const SubstackPublication(
        subdomain: 'www',
        baseUrl: 'https://www.garbageday.email',
        name: 'www',
      ).displayName,
      'garbageday',
    );
    expect(
      subdomainOf(Uri.parse('https://www.garbageday.email')),
      'garbageday',
    );
    expect(subdomainOf(Uri.parse('https://garbageday.email')), 'garbageday');
    expect(
      subdomainOf(Uri.parse('https://astralcodexten.substack.com')),
      'astralcodexten',
    );
  });

  test('garbageday.email probes the leftover Substack host', () {
    final hosts = substackHostCandidates(
      Uri.parse('https://www.garbageday.email'),
    ).map((e) => e.host);
    expect(hosts, contains('garbageday.substack.com'));
    expect(hosts, contains('garbageday.email'));
    expect(hosts, isNot(contains('www.substack.com')));
  });

  test('publicationForPost keeps a tappable in-app profile', () {
    final pub = publicationForPost(
      _post(title: 'Hello', name: 'a16z speedrun'),
    );
    expect(pub.displayName, 'a16z speedrun');
    expect(pub.baseUrl, 'https://speedrun.substack.com');
  });

  test('resolvePublication follows garbageday via the leftover host', () async {
    final client = SubstackClient(
      httpClient: MockClient((request) async {
        if (request.url.host == 'garbageday.substack.com' &&
            request.url.path == '/api/v1/posts') {
          return _json([
            {
              'id': 1,
              'title': 'Hello',
              'slug': 'hello',
              'publishedBylines': [
                {
                  'publicationUsers': [
                    {
                      'publication': {
                        'name': 'Garbage Day',
                        'subdomain': 'garbageday',
                      },
                    },
                  ],
                },
              ],
            },
          ]);
        }
        return http.Response('nope', 404);
      }),
    );

    final pub = await client.resolvePublication('https://www.garbageday.email');
    expect(pub.displayName, 'Garbage Day');
    expect(pub.displayName.toLowerCase(), isNot('www'));
    expect(pub.subdomain, 'garbageday');

    final posts = await client.fetchPosts(
      const SubstackPublication(
        subdomain: 'www',
        baseUrl: 'https://www.garbageday.email',
        name: 'www',
      ),
    );
    expect(posts, isNotEmpty);
    expect(posts.first.title, 'Hello');
    expect(posts.first.publicationName, isNot('www'));
  });

  testWidgets('tapping the publication name opens the in-app profile', (
    tester,
  ) async {
    await tester.pumpWidget(
      _app(
        home: Scaffold(
          body: SubstackPostCard(
            post: _post(title: 'Guillermo Rauch', name: 'a16z speedrun'),
            showSourceBadge: false,
          ),
        ),
      ),
    );
    await tester.pump();

    await tester.tap(find.text('a16z speedrun'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));

    expect(find.byType(SubstackArchiveScreen), findsOneWidget);
    expect(find.text('Guillermo Rauch'), findsWidgets);
  });

  testWidgets('the publication profile scrolls header and posts together', (
    tester,
  ) async {
    final posts = [
      for (var i = 0; i < 8; i++)
        {
          'id': i,
          'title': 'Post $i — a long enough title to take a row',
          'slug': 'post-$i',
          'subtitle': 'Excerpt $i',
        },
    ];
    final client = SubstackClient(
      httpClient: MockClient((request) async {
        if (request.url.path == '/api/v1/posts') {
          return _json(posts);
        }
        return http.Response('nope', 404);
      }),
    );

    await tester.pumpWidget(
      _app(
        client: client,
        home: const SubstackArchiveScreen(publication: _speedrun),
      ),
    );
    await tester.pump();
    for (
      var i = 0;
      i < 30 && find.textContaining('Post 0').evaluate().isEmpty;
      i++
    ) {
      await tester.pump(const Duration(milliseconds: 50));
    }

    expect(find.byType(CustomScrollView), findsOneWidget);
    expect(find.text('a16z speedrun'), findsWidgets);
    expect(find.byType(SubstackPostCard), findsWidgets);
    expect(find.textContaining('Post 0'), findsOneWidget);

    final scrollable = tester
        .stateList<ScrollableState>(find.byType(Scrollable))
        .where((state) => state.position.axis == Axis.vertical)
        .reduce(
          (a, b) =>
              a.position.maxScrollExtent >= b.position.maxScrollExtent ? a : b,
        );
    expect(scrollable.position.pixels, 0);
    await tester.drag(find.byType(CustomScrollView), const Offset(0, -400));
    await tester.pump();
    expect(scrollable.position.pixels, greaterThan(100));
  });
}
