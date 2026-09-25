import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
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
import 'package:xta/plugins/substack/substack_publication_store.dart';
import 'package:xta/plugins/substack/substack_similar_sheet.dart';
import 'package:xta/plugins/substack/substack_store.dart';

const publication = SubstackPublication(
  subdomain: 'example',
  baseUrl: 'https://example.substack.com',
  name: 'A publication with a considerably long name',
  description: 'Independent reporting and essays. ',
);

SubstackPost post(int id, {String? date, String? audience, String? audio, bool video = false, int? likes}) =>
    SubstackPost(
      id: '$id',
      title: 'Article $id',
      slug: 'article-$id',
      publicationBaseUrl: publication.baseUrl,
      publicationName: publication.name,
      postDate: date,
      audience: audience,
      audioUrl: audio,
      hasVideoUpload: video,
      reactionCount: likes,
    );

class PublicationClient extends SubstackClient {
  final requests = <int>[];
  final searches = <({String query, int offset})>[];
  Future<List<SubstackPost>> Function(int offset)? fetch;
  Future<List<SubstackPost>> Function(String query, int offset)? search;
  Future<List<SubstackRecommendation>> Function()? similar;
  Future<SubstackPublication> Function(Uri base)? metadata;
  PublicationClient() : super(httpClient: MockClient((_) async => http.Response('[]', 200)));

  @override
  Future<List<SubstackPost>> fetchPosts(SubstackPublication publication, {int limit = 12, int offset = 0}) {
    requests.add(offset);
    return fetch?.call(offset) ?? Future.value([for (var i = 0; i < limit; i++) post(offset + i)]);
  }

  @override
  Future<List<SubstackPost>> searchPosts(
    SubstackPublication publication,
    String query, {
    int limit = 25,
    int offset = 0,
  }) {
    searches.add((query: query, offset: offset));
    return search?.call(query, offset) ?? Future.value([for (var i = 0; i < limit; i++) post(offset + i + 100)]);
  }

  @override
  Future<SubstackPublication> fetchPublication(Uri base) async => metadata?.call(base) ?? publication;

  @override
  Future<List<SubstackRecommendation>> fetchSimilarPublications(SubstackPublication publication) =>
      similar?.call() ?? Future.value(const []);
}

void main() {
  setUpAll(() async {
    autoUpdateGoldenFiles = true;
    await (FontLoader('Inter')..addFont(rootBundle.load('assets/fonts/Inter-Regular.ttf'))).load();
    await (FontLoader('MaterialIcons')..addFont(rootBundle.load('fonts/MaterialIcons-Regular.otf'))).load();
  });
  late PublicationClient client;
  late SubstackPublicationStore store;
  setUp(() {
    client = PublicationClient();
    store = SubstackPublicationStore(client, publication);
  });
  tearDown(() async {
    await store.destroy();
    client.httpClient.close();
  });

  test('a failed next page keeps articles and retry uses the failed offset', () async {
    await store.refresh();
    client.fetch = (_) async => throw StateError('offline');
    await store.loadMore();
    expect(store.state.page.posts.length, 20);
    expect(store.state.page.offset, 20);
    expect(store.state.page.failedMore, isTrue);
    client.fetch = (offset) async => [post(offset)];
    await store.retry();
    expect(client.requests, [0, 20, 20]);
    expect(store.state.page.posts.length, 21);
    expect(store.state.page.error, isNull);
  });

  test('failed refresh retains articles and retries the first page', () async {
    await store.refresh();
    client.fetch = (_) async => throw StateError('offline');
    await store.refresh();
    expect(store.state.page.posts.length, 20);
    expect(store.state.page.failedMore, isFalse);
    client.fetch = (_) async => [post(42)];
    await store.retry();
    expect(client.requests, [0, 0, 0]);
    expect(store.state.page.posts.single.id, '42');
  });

  test('duplicate page requests are suppressed and refresh supersedes an old page', () async {
    await store.refresh();
    final more = Completer<List<SubstackPost>>();
    client.fetch = (_) => more.future;
    final request = store.loadMore();
    await store.loadMore();
    expect(client.requests, [0, 20]);
    client.fetch = (_) async => [post(99)];
    await store.refresh();
    more.complete([post(20)]);
    await request;
    expect(store.state.page.posts.single.id, '99');
  });

  test('repeated server pages stop paging while updating metadata', () async {
    await store.refresh();
    client.fetch = (_) async => [for (var i = 0; i < 20; i++) post(i, likes: 50)];
    await store.loadMore();
    expect(store.state.page.posts.length, 20);
    expect(store.state.page.canLoadMore, isFalse);
    expect(store.state.page.posts.first.reactionCount, 50);
    await store.loadMore();
    expect(client.requests, [0, 20]);
  });

  test('duplicates inside the first server page only appear once', () async {
    client.fetch = (_) async => [post(1), post(1), post(2)];
    await store.refresh();
    expect(store.state.page.posts.map((post) => post.id), ['1', '2']);
  });

  test('search has separate pagination and clearing it restores the archive', () async {
    await store.refresh();
    store.search('climate');
    await store.refresh();
    await store.loadMore();
    expect(client.searches, [(query: 'climate', offset: 0), (query: 'climate', offset: 20)]);
    expect(store.state.page.posts.length, 40);
    store.search('');
    expect(store.state.page.posts.length, 20);
    expect(store.state.page.posts.first.id, '0');
    expect(client.requests, [0]);
  });

  test('late results cannot replace the newer query, even after returning to the same query', () async {
    final first = Completer<List<SubstackPost>>();
    client.search = (_, _) => first.future;
    store.search('a');
    final stale = store.refresh();
    client.search = (_, _) async => [post(200)];
    store.search('b');
    await store.refresh();
    store.search('a');
    await store.refresh();
    first.complete([post(100)]);
    await stale;
    expect(store.state.page.posts.single.id, '200');
  });

  test('search failure is retryable and does not empty the archive', () async {
    await store.refresh();
    client.search = (_, _) async => throw StateError('offline');
    store.search('report');
    await store.refresh();
    expect(store.state.page.error, isNotNull);
    client.search = (_, _) async => [post(100)];
    await store.retry();
    expect(store.state.page.posts.single.id, '100');
    store.search('');
    expect(store.state.page.posts.length, 20);
  });

  test('disposing while a request is in flight ignores completion', () async {
    final pending = Completer<List<SubstackPost>>();
    client.fetch = (_) => pending.future;
    final request = store.refresh();
    await store.destroy();
    pending.complete([post(1)]);
    await request;
    expect(store.state.page.posts, isEmpty);
  });

  testWidgets('publication search waits for a pause in typing', (tester) async {
    store.search('c');
    await tester.pump(const Duration(milliseconds: 200));
    store.search('climate');
    await tester.pump(const Duration(milliseconds: 299));
    expect(client.searches, isEmpty);
    await tester.pump(const Duration(milliseconds: 1));
    expect(client.searches.single.query, 'climate');
    store.search('queued');
    await store.destroy();
    await tester.pump(const Duration(seconds: 1));
    expect(client.searches.length, 1);
  });

  test('metadata enrichment preserves local identity and custom-domain address', () async {
    client.metadata = (_) async => const SubstackPublication(
      subdomain: 'canonical',
      baseUrl: 'https://canonical.substack.com',
      name: 'The enriched name',
      description: 'A richer biography',
      logoUrl: 'https://example.com/logo.png',
    );
    final enriched = await store.enrich();
    expect(enriched!.id, publication.id);
    expect(enriched.baseUrl, publication.baseUrl);
    expect(enriched.displayName, 'The enriched name');
    expect(enriched.description, 'A richer biography');
  });

  test('a failed metadata lookup leaves the archive usable', () async {
    client.metadata = (_) async => throw StateError('metadata offline');
    await Future.wait([store.enrich(), store.refresh()]);
    expect(store.state.publication.name, publication.name);
    expect(store.state.page.posts.length, 20);
    expect(store.state.page.error, isNull);
  });

  test('filters distinguish unread, paid, audio and video without changing loaded pages', () {
    final state = SubstackPublicationState(
      publication: publication,
      archive: SubstackPublicationPage(
        posts: [
          post(1),
          post(2, audience: 'only_paid'),
          post(3, audio: 'https://example.com/a.mp3'),
          post(4, video: true),
        ],
      ),
    );
    expect(visiblePublicationPosts(state.copy(filter: SubstackPublicationFilter.unread), {'1'}).length, 3);
    expect(visiblePublicationPosts(state.copy(filter: SubstackPublicationFilter.free), {}).map((p) => p.id), [
      '1',
      '3',
      '4',
    ]);
    expect(visiblePublicationPosts(state.copy(filter: SubstackPublicationFilter.podcasts), {}).single.id, '3');
    expect(visiblePublicationPosts(state.copy(filter: SubstackPublicationFilter.videos), {}).single.id, '4');
    expect(state.archive.posts.length, 4);
  });

  test('orders dates chronologically with unknown dates last and stable ties', () {
    final state = SubstackPublicationState(
      publication: publication,
      archive: SubstackPublicationPage(
        posts: [
          post(1),
          post(2, date: '2026-04-02T10:00:00+02:00', likes: 10),
          post(3, date: '2026-04-02T08:30:00Z', likes: 20),
          post(4, date: '2026-04-02T08:00:00Z', likes: 10),
        ],
      ),
    );
    expect(visiblePublicationPosts(state, {}).map((p) => p.id), ['3', '2', '4', '1']);
    expect(visiblePublicationPosts(state.copy(order: SubstackPublicationOrder.oldest), {}).map((p) => p.id), [
      '2',
      '4',
      '3',
      '1',
    ]);
    expect(visiblePublicationPosts(state.copy(order: SubstackPublicationOrder.popular), {}).first.id, '3');
  });

  test('similar recommendations suppress duplicate loads and exclude seed/duplicates', () async {
    final request = Completer<List<SubstackRecommendation>>();
    client.similar = () => request.future;
    final similar = SubstackSimilarStore(client, publication);
    final load = similar.load();
    await similar.load();
    const other = SubstackPublication(subdomain: 'other', baseUrl: 'https://other.substack.com', name: 'Other');
    request.complete(const [
      SubstackRecommendation(publication: publication),
      SubstackRecommendation(publication: other),
      SubstackRecommendation(publication: other),
    ]);
    await load;
    expect(similar.state.recommendations.single.publication.id, 'other');
    await similar.destroy();
  });

  test('similar recommendation errors are retryable', () async {
    client.similar = () async => throw StateError('offline');
    final similar = SubstackSimilarStore(client, publication);
    await similar.load();
    expect(similar.state.error, isNotNull);
    client.similar = () async => [];
    await similar.load();
    expect(similar.state.error, isNull);
    expect(similar.state.loading, isFalse);
    await similar.destroy();
  });

  testWidgets('a publication filter keeps paging available when its current page has no matches', (tester) async {
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    client.fetch = (offset) async => offset == 0 ? [for (var i = 0; i < 20; i++) post(i)] : [post(20, video: true)];
    await tester.pumpWidget(_app(client, const SubstackArchiveScreen(publication: publication), scale: 1));
    await tester.pumpAndSettle();
    await capture(tester, 'substack-publication.png');
    await tester.ensureVisible(find.widgetWithText(FilterChip, 'Videos'));
    await tester.tap(find.widgetWithText(FilterChip, 'Videos'));
    await tester.pumpAndSettle();
    final more = find.widgetWithText(OutlinedButton, 'Load more');
    await tester.ensureVisible(more);
    expect(tester.widget<OutlinedButton>(more).onPressed, isNotNull);
    await tester.tap(more);
    await tester.pumpAndSettle();
    expect(client.requests, [0, 20]);
    expect(find.text('Article 20'), findsOneWidget);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('publication controls fit a narrow RTL screen with large text', (tester) async {
    tester.view.physicalSize = const Size(320, 760);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(_app(client, const SubstackArchiveScreen(publication: publication), rtl: true));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    expect(find.byType(CustomScrollView), findsOneWidget);
    await capture(tester, 'substack-publication-rtl.png');
    await tester.drag(find.byType(CustomScrollView), const Offset(0, -500));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('recommendation rows fit a narrow RTL screen with large text', (tester) async {
    tester.view.physicalSize = const Size(320, 760);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    client.similar = () async => const [
      SubstackRecommendation(
        publication: SubstackPublication(
          subdomain: 'other',
          baseUrl: 'https://other.substack.com',
          name: 'Another publication with a very long name',
          description: 'Essays and reporting',
        ),
      ),
    ];
    await tester.pumpWidget(
      _app(
        client,
        Builder(
          builder: (context) => Scaffold(
            body: TextButton(
              onPressed: () => showSubstackSimilarSheet(context, publication),
              child: const Text('Open'),
            ),
          ),
        ),
        rtl: true,
      ),
    );
    await tester.tap(find.text('Open'));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    expect(find.byType(SubstackFollowButton), findsOneWidget);
    await capture(tester, 'substack-recommendations-rtl.png');
    await tester.pumpWidget(const SizedBox.shrink());
  });
}

Widget _app(PublicationClient client, Widget home, {bool rtl = false, double scale = 1.5}) {
  final prefs = PrefServiceCache(cache: {});
  return MultiProvider(
    providers: [
      Provider<SubstackClient>.value(value: client),
      Provider(create: (_) => SubstackPublicationsStore(prefs), dispose: (_, store) => store.destroy()),
      Provider(create: (_) => SubstackReadStore(prefs), dispose: (_, store) => store.destroy()),
      Provider(create: (_) => SubstackLikesStore(prefs), dispose: (_, store) => store.destroy()),
      Provider(create: (_) => SubstackSavedStore(prefs), dispose: (_, store) => store.destroy()),
    ],
    child: RepaintBoundary(
      key: const ValueKey('publication-capture'),
      child: MaterialApp(
        debugShowCheckedModeBanner: false,
        theme: ThemeData(fontFamily: 'Inter'),
        locale: rtl ? const Locale('ar') : const Locale('en'),
        localizationsDelegates: const [
          L10n.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        supportedLocales: L10n.delegate.supportedLocales,
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(scale)),
          child: child!,
        ),
        home: home,
      ),
    ),
  );
}

Future<void> capture(WidgetTester tester, String name) async {
  await expectLater(
    find.byKey(const ValueKey('publication-capture')),
    matchesGoldenFile('../review-artifacts/renders/$name'),
  );
}
