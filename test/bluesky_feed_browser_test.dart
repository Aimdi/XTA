import 'dart:async';
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
import 'package:xta/plugins/bluesky/bluesky_client.dart';
import 'package:xta/plugins/bluesky/bluesky_feeds_pane.dart';
import 'package:xta/plugins/bluesky/bluesky_feeds_store.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/bluesky/bluesky_source_reader.dart';

const _feedA = 'at://did:plc:a/app.bsky.feed.generator/a';
const _feedB = 'at://did:plc:a/app.bsky.feed.generator/b';

http.Response _page(List<String> ids, {String? cursor}) => http.Response(
  jsonEncode({
    'feed': [
      for (final id in ids)
        {
          'post': {
            'uri': 'at://did:plc:a/app.bsky.feed.post/$id',
            'author': {'did': 'did:plc:a', 'handle': 'alice.test'},
            'record': {'text': id, 'createdAt': id == 'older' ? '2026-01-01T00:00:00Z' : '2026-09-01T00:00:00Z'},
          },
        },
    ],
    'cursor': ?cursor,
  }),
  200,
);

http.Response _catalog(List<String> names, {String? cursor}) => http.Response(
  jsonEncode({
    'feeds': [
      for (final name in names)
        {
          'uri': 'at://did:plc:a/app.bsky.feed.generator/$name',
          'displayName': name,
          'description': 'Description $name',
          'creator': {'handle': 'curator.test'},
        },
    ],
    'cursor': ?cursor,
  }),
  200,
);

void main() {
  for (final isList in [false, true]) {
    group(isList ? 'list source' : 'ranked feed source', () {
      BlueskySourceStore create(BlueskyClient client, [BasePrefService? prefs]) => isList
          ? BlueskyListsStore(client, prefs ?? PrefServiceCache())
          : BlueskyAlgoStore(client, prefs ?? PrefServiceCache());

      test('switch selects immediately, late response cannot overwrite it', () async {
        final first = Completer<http.Response>();
        final second = Completer<http.Response>();
        final store = create(
          BlueskyClient(
            httpClient: MockClient(
              (request) => request.url.queryParameters.values.contains(_feedA) ? first.future : second.future,
            ),
          ),
        );
        addTearDown(store.destroy);
        final pendingA = store.open(_feedA, name: 'A');
        final pendingB = store.open(_feedB, name: 'B');
        expect(store.sourceUri, _feedB);
        expect(store.sourceName, 'B');
        expect(store.sourcePage.loading, isTrue);
        second.complete(_page(['b']));
        await pendingB;
        first.complete(_page(['a']));
        await pendingA;
        expect(store.sourcePage.posts.single.text, 'b');
      });

      test('new failed selection never borrows the old feed posts', () async {
        final store = create(
          BlueskyClient(
            httpClient: MockClient(
              (request) async =>
                  request.url.queryParameters.values.contains(_feedA) ? _page(['a']) : http.Response('failed', 500),
            ),
          ),
        );
        addTearDown(store.destroy);
        await store.open(_feedA);
        await store.open(_feedB);
        expect(store.sourceUri, _feedB);
        expect(store.sourcePage.posts, isEmpty);
        expect(store.sourcePage.error, isNotNull);
        await store.open(_feedA);
        expect(store.sourcePage.posts.single.text, 'a');
        expect(store.feedFetches, 2);
      });

      test('refresh failure keeps cached posts and can be retried', () async {
        var fail = false;
        final store = create(
          BlueskyClient(httpClient: MockClient((_) async => fail ? http.Response('failed', 500) : _page(['a']))),
        );
        addTearDown(store.destroy);
        await store.open(_feedA);
        fail = true;
        await store.open(_feedA, force: true);
        expect(store.sourcePage.posts.single.text, 'a');
        expect(store.sourcePage.error, isNotNull);
        fail = false;
        await store.open(_feedA, force: true);
        expect(store.sourcePage.error, isNull);
      });

      test('pagination coalesces calls, preserves source order, stops a repeated cursor', () async {
        final next = Completer<http.Response>();
        var calls = 0;
        final store = create(
          BlueskyClient(
            httpClient: MockClient((request) {
              calls++;
              return request.url.queryParameters['cursor'] == null
                  ? Future.value(_page(['older', 'newer'], cursor: 'c1'))
                  : next.future;
            }),
          ),
        );
        addTearDown(store.destroy);
        await store.open(_feedA);
        expect(store.sourcePage.posts.map((p) => p.text), ['older', 'newer']);
        final pending = store.loadMore();
        await store.open(_feedA);
        await store.loadMore();
        await Future<void>.delayed(Duration.zero);
        expect(calls, 2);
        expect(store.sourcePage.loadingMore, isTrue);
        next.complete(_page(['newer', 'third'], cursor: 'c1'));
        await pending;
        expect(store.sourcePage.posts.map((p) => p.text), ['older', 'newer', 'third']);
        expect(store.sourcePage.hasMore, isFalse);
      });

      test('pagination failure preserves cursor and permits explicit retry', () async {
        var fail = true;
        final store = create(
          BlueskyClient(
            httpClient: MockClient((request) async {
              if (request.url.queryParameters['cursor'] == null) return _page(['first'], cursor: 'c1');
              return fail ? http.Response('failed', 500) : _page(['second']);
            }),
          ),
        );
        addTearDown(store.destroy);
        await store.open(_feedA);
        await store.loadMore();
        expect(store.sourcePage.posts.single.text, 'first');
        expect(store.sourcePage.cursor, 'c1');
        expect(store.sourcePage.moreError, isNotNull);
        fail = false;
        await store.loadMore();
        expect(store.sourcePage.posts.map((p) => p.text), ['first', 'second']);
        expect(store.sourcePage.moreError, isNull);
      });

      test('switch during pagination cannot append another source', () async {
        final next = Completer<http.Response>();
        final store = create(
          BlueskyClient(
            httpClient: MockClient((request) async {
              if (request.url.queryParameters['cursor'] != null) return next.future;
              return request.url.queryParameters.values.contains(_feedA) ? _page(['a'], cursor: 'next') : _page(['b']);
            }),
          ),
        );
        addTearDown(store.destroy);
        await store.open(_feedA);
        final pending = store.loadMore();
        await store.open(_feedB);
        next.complete(_page(['late']));
        await pending;
        expect(store.sourcePage.posts.single.text, 'b');
        await store.open(_feedA);
        expect(store.sourcePage.posts.single.text, 'a');
        expect(store.sourcePage.loadingMore, isFalse);
      });

      test('changing AppView invalidates in-flight response and loads the new server', () async {
        var server = 'https://one.test';
        final old = Completer<http.Response>();
        final client = BlueskyClient(
          resolveBaseUrl: () => server,
          httpClient: MockClient(
            (request) async => request.url.host == 'one.test' ? old.future : _page(['new-server']),
          ),
        );
        final store = create(client);
        addTearDown(store.destroy);
        final first = store.open(_feedA);
        server = 'https://two.test';
        await store.open(_feedA);
        old.complete(_page(['old-server']));
        await first;
        expect(store.sourcePage.posts.single.text, 'new-server');
      });

      test('destroying while loading ignores the response', () async {
        final response = Completer<http.Response>();
        final store = create(BlueskyClient(httpClient: MockClient((_) => response.future)));
        final pending = store.open(_feedA);
        await store.destroy();
        response.complete(_page(['late']));
        await pending;
        expect(store.sourcePage.posts, isEmpty);
      });

      test('remembered selection is local and scoped to AppView', () async {
        var server = 'https://one.test';
        final prefs = PrefServiceCache();
        final client = BlueskyClient(resolveBaseUrl: () => server, httpClient: MockClient((_) async => _page([])));
        final store = create(client, prefs);
        addTearDown(store.destroy);
        await store.open(_feedA, name: 'A');
        await Future<void>.delayed(Duration.zero);
        expect(store.rememberedSource(), (_feedA, 'A'));
        server = 'https://two.test';
        expect(store.rememberedSource(), isNull);
        await store.open(_feedB, name: 'B');
        await Future<void>.delayed(Duration.zero);
        expect(store.rememberedSource(), (_feedB, 'B'));
        server = 'https://one.test';
        expect(store.rememberedSource(), (_feedA, 'A'));
      });
    });
  }

  test('catalog failure cannot block Discover and remains retryable', () async {
    var fail = true;
    final store = BlueskyAlgoStore(
      BlueskyClient(
        httpClient: MockClient((request) async {
          if (request.url.path.endsWith('getFeed')) return _page(['ready']);
          return fail ? http.Response('fail', 500) : _catalog(['science']);
        }),
      ),
      PrefServiceCache(),
    );
    addTearDown(store.destroy);
    await store.ensureLoaded();
    expect(store.state.posts.single.text, 'ready');
    expect(store.state.catalogError, isNotNull);
    fail = false;
    await store.loadCatalog();
    expect(store.state.popular.single.displayName, 'science');
    expect(store.state.catalogError, isNull);
  });

  test('new store restores its saved custom feed without waiting for the catalog', () async {
    final prefs = PrefServiceCache();
    final catalog = Completer<http.Response>();
    final client = BlueskyClient(
      httpClient: MockClient((request) async {
        if (request.url.path.endsWith('getFeed')) return _page(['selected']);
        return catalog.future;
      }),
    );
    final previous = BlueskyAlgoStore(client, prefs);
    await previous.open(_feedA, name: 'My feed');
    await Future<void>.delayed(Duration.zero);
    await previous.destroy();
    final restored = BlueskyAlgoStore(client, prefs);
    addTearDown(restored.destroy);
    final pending = restored.ensureLoaded();
    await Future<void>.delayed(Duration.zero);
    expect(restored.state.selectedUri, _feedA);
    expect(restored.state.selectedName, 'My feed');
    expect(restored.state.posts.single.text, 'selected');
    expect(restored.state.catalogLoading, isTrue);
    catalog.complete(_catalog([]));
    await pending;
  });

  test('catalog search ignores late queries and retains new pins', () async {
    final old = Completer<http.Response>();
    final store = BlueskyAlgoStore(
      BlueskyClient(
        httpClient: MockClient(
          (request) async => request.url.queryParameters['query'] == 'old' ? old.future : _catalog(['new']),
        ),
      ),
      PrefServiceCache(),
    );
    addTearDown(store.destroy);
    final pending = store.searchCatalog('old');
    await store.searchCatalog('new');
    await store.pin(const BlueskyFeedGenerator(uri: _feedA, displayName: 'Pinned'));
    old.complete(_catalog(['old']));
    await pending;
    expect(store.state.query, 'new');
    expect(store.state.popular.single.displayName, 'new');
    expect(store.state.pinned.single.uri, _feedA);
  });

  test('catalog cursor cycles terminate and duplicates are removed', () async {
    final store = BlueskyAlgoStore(
      BlueskyClient(
        httpClient: MockClient(
          (request) async => request.url.queryParameters['cursor'] == null
              ? _catalog(['a'], cursor: 'next')
              : _catalog(['a', 'b'], cursor: 'next'),
        ),
      ),
      PrefServiceCache(),
    );
    addTearDown(store.destroy);
    await store.searchCatalog('');
    await store.loadMoreCatalog();
    expect(store.state.popular.map((f) => f.displayName), ['a', 'b']);
    expect(store.state.catalogCursor, isNull);
  });

  test('catalog paging restarts after changing AppView without reusing the old cursor', () async {
    var server = 'https://one.test';
    final requests = <Uri>[];
    final store = BlueskyAlgoStore(
      BlueskyClient(
        resolveBaseUrl: () => server,
        httpClient: MockClient((request) async {
          requests.add(request.url);
          return _catalog([request.url.host], cursor: 'next');
        }),
      ),
      PrefServiceCache(),
    );
    addTearDown(store.destroy);
    await store.searchCatalog('science');
    server = 'https://two.test';
    await store.loadMoreCatalog();
    expect(requests.last.queryParameters['cursor'], isNull);
    expect(store.state.popular.single.displayName, 'two.test');
  });

  test('list lookup without an open source refreshes on AppView change', () async {
    var server = 'https://one.test';
    final store = BlueskyListsStore(
      BlueskyClient(
        resolveBaseUrl: () => server,
        httpClient: MockClient(
          (request) async => http.Response(
            jsonEncode({
              'lists': [
                {'uri': _feedA, 'name': request.url.host},
              ],
            }),
            200,
          ),
        ),
      ),
      PrefServiceCache(),
    );
    addTearDown(store.destroy);
    await store.lookupActor('reader.test');
    await store.ensureLoaded();
    expect(store.sourceUri, isNull);
    server = 'https://two.test';
    await store.ensureLoaded();
    expect(store.state.actorLists.single.name, 'two.test');
  });

  test('list catalog paging restarts after changing AppView', () async {
    var server = 'https://one.test';
    final requests = <Uri>[];
    final store = BlueskyListsStore(
      BlueskyClient(
        resolveBaseUrl: () => server,
        httpClient: MockClient((request) async {
          requests.add(request.url);
          return http.Response(
            jsonEncode({
              'lists': [
                {'uri': _feedA, 'name': request.url.host},
              ],
              'cursor': 'next',
            }),
            200,
          );
        }),
      ),
      PrefServiceCache(),
    );
    addTearDown(store.destroy);
    await store.lookupActor('reader.test');
    server = 'https://two.test';
    await store.loadMoreLists();
    expect(requests.last.queryParameters['cursor'], isNull);
    expect(store.state.actorLists.single.name, 'two.test');
  });

  test('list lookup ignores stale actors and pages their lists', () async {
    final old = Completer<http.Response>();
    final store = BlueskyListsStore(
      BlueskyClient(
        httpClient: MockClient((request) async {
          if (request.url.queryParameters['actor'] == 'old.test') return old.future;
          final more = request.url.queryParameters['cursor'] != null;
          return http.Response(
            jsonEncode({
              'lists': [
                {'uri': more ? _feedB : _feedA, 'name': more ? 'B' : 'A'},
              ],
              'cursor': 'next',
            }),
            200,
          );
        }),
      ),
      PrefServiceCache(),
    );
    addTearDown(store.destroy);
    final pending = store.lookupActor('old.test');
    await store.lookupActor('new.test');
    await store.loadMoreLists();
    old.complete(http.Response('{"lists":[]}', 200));
    await pending;
    expect(store.state.actor, 'new.test');
    expect(store.state.actorLists.map((l) => l.name), ['A', 'B']);
    expect(store.state.listsCursor, isNull);
  });

  test('concurrent pins preserve existing preferences and persist the final order', () async {
    final prefs = PrefServiceCache();
    await prefs.set(
      optionPluginBlueskyPinnedFeeds,
      blueskyGeneratorsToPrefs([const BlueskyFeedGenerator(uri: kBlueskyDiscoverFeedUri, displayName: 'Discover')]),
    );
    final store = BlueskyAlgoStore(BlueskyClient(), prefs);
    addTearDown(store.destroy);
    await Future.wait([
      store.pin(const BlueskyFeedGenerator(uri: _feedA, displayName: 'A')),
      store.pin(const BlueskyFeedGenerator(uri: _feedB, displayName: 'B')),
    ]);
    expect(store.state.pinned.map((f) => f.uri), [kBlueskyDiscoverFeedUri, _feedA, _feedB]);
    expect(blueskyGeneratorsFromPrefs(prefs.get<String>(optionPluginBlueskyPinnedFeeds)).map((f) => f.uri), [
      kBlueskyDiscoverFeedUri,
      _feedA,
      _feedB,
    ]);
  });

  testWidgets('empty feed retains selection, pin, browse and explicit paging', (tester) async {
    final client = BlueskyClient(
      httpClient: MockClient(
        (request) async => request.url.path.endsWith('getFeed') ? _page([], cursor: 'next') : _catalog([]),
      ),
    );
    final store = BlueskyAlgoStore(client, PrefServiceCache());
    final scroll = ScrollController();
    addTearDown(store.destroy);
    addTearDown(scroll.dispose);
    await tester.pumpWidget(
      _app(
        MultiProvider(
          providers: [
            Provider.value(value: store),
            Provider.value(value: client),
          ],
          child: Scaffold(body: BlueskyAlgoPane(scrollController: scroll)),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Find feeds'), findsOneWidget);
    expect(find.text('Pin'), findsOneWidget);
    expect(find.text('Load more'), findsOneWidget);
    expect(find.text('No posts in this feed yet'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('catalog search shows metadata and an accessible pin action', (tester) async {
    final store = BlueskyAlgoStore(
      BlueskyClient(httpClient: MockClient((_) async => _catalog(['Science']))),
      PrefServiceCache(),
    );
    addTearDown(store.destroy);
    await store.searchCatalog('science');
    await tester.pumpWidget(_app(BlueskyFeedCatalogScreen(store: store)));
    await tester.pumpAndSettle();
    expect(find.text('Science'), findsOneWidget);
    expect(find.text('@curator.test\nDescription Science'), findsOneWidget);
    expect(find.byTooltip('Pin'), findsOneWidget);
    await tester.tap(find.byTooltip('Pin'));
    await tester.pumpAndSettle();
    expect(find.byTooltip('Unpin'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}

Widget _app(Widget child) => MaterialApp(
  home: child,
  localizationsDelegates: const [
    L10n.delegate,
    GlobalMaterialLocalizations.delegate,
    GlobalWidgetsLocalizations.delegate,
    GlobalCupertinoLocalizations.delegate,
  ],
  supportedLocales: L10n.delegate.supportedLocales,
);
