import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/substack/substack_add_screen.dart';
import 'package:xta/plugins/substack/substack_client.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/plugins/substack/substack_search_sheet.dart';
import 'package:xta/plugins/substack/substack_search_store.dart';
import 'package:xta/plugins/substack/substack_store.dart';

SubstackPublication publication(String id) => SubstackPublication(
  subdomain: id,
  baseUrl: 'https://$id.substack.com',
  name: 'Publication $id',
  description: 'Essays and observations about our world.',
);
SubstackPost article(String id, {String pub = 'one', int day = 1}) => SubstackPost(
  id: id,
  title: 'Article $id',
  slug: id,
  publicationBaseUrl: 'https://$pub.substack.com',
  publicationName: 'Publication $pub',
  postDate: '2026-09-${day.toString().padLeft(2, '0')}T12:00:00Z',
);
const science = SubstackCategory(id: 1, name: 'Science', slug: 'science');
const arts = SubstackCategory(id: 2, name: 'Arts', slug: 'arts');

class SearchClient extends SubstackClient {
  final searches = <({String query, int page})>[];
  final categoryCalls = <({int category, int page})>[];
  final articleCalls = <({String id, String query, int offset})>[];
  final resolves = <String>[];
  final requestedArticles = <String>[];
  final publicationResponses = <Future<List<SubstackPublication>>>[];
  final categoryResponses = <Future<List<SubstackPublication>>>[];
  final resolveResponses = <Future<SubstackPublication>>[];
  final articleResponses = <Future<SubstackPost>>[];
  final postResponses = <String, List<Future<List<SubstackPost>>>>{};
  Completer<List<SubstackCategory>>? categories;
  @override
  Future<List<SubstackCategory>> fetchCategories() => categories?.future ?? Future.value([science, arts]);
  @override
  Future<List<SubstackPublication>> searchPublications(String query, {int page = 0}) {
    searches.add((query: query, page: page));
    return publicationResponses.isEmpty ? Future.value([publication('one')]) : publicationResponses.removeAt(0);
  }

  @override
  Future<List<SubstackPublication>> fetchCategoryPublications(int categoryId, {String tier = 'all', int page = 0}) {
    categoryCalls.add((category: categoryId, page: page));
    return categoryResponses.isEmpty
        ? Future.value([publication('category-$categoryId')])
        : categoryResponses.removeAt(0);
  }

  @override
  Future<SubstackPublication> resolvePublication(String input) {
    resolves.add(input);
    return resolveResponses.isEmpty ? Future.value(publication('one')) : resolveResponses.removeAt(0);
  }

  @override
  Future<SubstackPost> fetchPost(SubstackPublication publication, String slug) {
    requestedArticles.add(slug);
    return articleResponses.isEmpty ? Future.value(article(slug)) : articleResponses.removeAt(0);
  }

  @override
  Future<List<SubstackPost>> searchPosts(
    SubstackPublication publication,
    String query, {
    int limit = 25,
    int offset = 0,
  }) {
    articleCalls.add((id: publication.id, query: query, offset: offset));
    final queued = postResponses[publication.id];
    return queued == null || queued.isEmpty
        ? Future.value([article('${publication.id}-$offset', pub: publication.id)])
        : queued.removeAt(0);
  }
}

void main() {
  group('publication discovery', () {
    late SearchClient client;
    late SubstackSearchStore store;
    setUp(() {
      client = SearchClient();
      store = SubstackSearchStore(client);
    });
    tearDown(() async {
      await store.destroy();
      client.httpClient.close();
    });

    test('late category list cannot replace a submitted search', () async {
      client.categories = Completer<List<SubstackCategory>>();
      final loading = store.loadCategories();
      await store.search('climate change');
      client.categories!.complete([science]);
      await loading;
      expect(store.state.query, 'climate change');
      expect(store.state.publications.single.id, 'one');
      expect(client.categoryCalls, isEmpty);
    });

    test('late category results cannot overwrite a newer category', () async {
      final pending = Completer<List<SubstackPublication>>();
      client.categoryResponses.add(pending.future);
      final old = store.browse(science);
      await store.browse(arts);
      pending.complete([publication('old')]);
      await old;
      expect(store.state.category?.id, 2);
      expect(store.state.publications.single.id, 'category-2');
    });

    test('late query failure cannot overwrite a newer query', () async {
      final pending = Completer<List<SubstackPublication>>();
      client.publicationResponses.add(pending.future);
      final old = store.search('old query');
      await store.search('new query');
      pending.completeError(StateError('offline'));
      await old;
      expect(store.state.query, 'new query');
      expect(store.state.error, isNull);
      expect(store.state.publications.single.id, 'one');
    });

    test('name results page, deduplicate, and stop repeated pages', () async {
      client.publicationResponses.addAll([
        Future.value([publication('one')]),
        Future.value([publication('one'), publication('two')]),
        Future.value([publication('two')]),
      ]);
      await store.search('climate change');
      await store.loadMore();
      await store.loadMore();
      await store.loadMore();
      expect(client.searches.map((call) => call.page), [0, 1, 2]);
      expect(store.state.publications.map((pub) => pub.id), ['one', 'two']);
      expect(store.state.hasMore, isFalse);
    });

    test('failed page retains prior results and retries the same page', () async {
      await store.search('climate change');
      final failure = Completer<List<SubstackPublication>>();
      client.publicationResponses.add(failure.future);
      final pending = store.loadMore();
      failure.completeError(StateError('offline'));
      await pending;
      expect(store.state.publications, hasLength(1));
      expect(store.state.retryMore, isTrue);
      await store.loadMore();
      expect(client.searches.map((call) => call.page), [0, 1, 1]);
      expect(store.state.error, isNull);
    });

    test('category paging stays on its selected category', () async {
      await store.browse(arts);
      await store.loadMore();
      expect(client.categoryCalls, [(category: 2, page: 0), (category: 2, page: 1)]);
    });

    test('punctuated publication names remain name searches', () async {
      await store.search('Dr. Science');
      expect(client.resolves, isEmpty);
      expect(client.searches.single.query, 'Dr. Science');
    });

    test('refresh failure retains the last readable category page', () async {
      await store.browse(science);
      final failed = Completer<List<SubstackPublication>>();
      client.categoryResponses.add(failed.future);
      final pending = store.refresh();
      failed.completeError(StateError('offline'));
      await pending;
      expect(store.state.publications.single.id, 'category-1');
      expect(store.state.error, isNotNull);
      await store.refresh();
      expect(store.state.error, isNull);
    });

    test('publication URL resolves without a name search', () async {
      await store.search('https://one.substack.com');
      expect(client.searches, isEmpty);
      expect(client.resolves, ['https://one.substack.com']);
      expect(store.state.hasMore, isFalse);
    });

    test('handle resolution can recover when search is unavailable', () async {
      final failed = Completer<List<SubstackPublication>>();
      client.publicationResponses.add(failed.future);
      final pending = store.search('one');
      failed.completeError(StateError('search offline'));
      await pending;
      expect(store.state.publications.single.id, 'one');
      expect(store.state.error, isNull);
    });

    test('empty publication search stays distinct from a failed request', () async {
      client.publicationResponses.add(Future.value([]));
      await store.search('not a publication');
      expect(store.state.publications, isEmpty);
      expect(store.state.error, isNull);
      final failed = Completer<List<SubstackPublication>>();
      client.publicationResponses.add(failed.future);
      final pending = store.search('failed request');
      failed.completeError(StateError('offline'));
      await pending;
      expect(store.state.error, isNotNull);
    });

    test('pasted post resolves its publication before fetching article', () async {
      await store.search('https://open.substack.com/pub/one/p/essay?utm_source=share');
      expect(store.state.tab, SubstackSearchTab.posts);
      expect(store.state.direct, isTrue);
      expect(store.state.publications.single.id, 'one');
      expect(store.state.posts.single.slug, 'essay');
      expect(client.requestedArticles, ['essay']);
    });

    test('pasted article failure preserves publication preview', () async {
      final failed = Completer<SubstackPost>();
      client.articleResponses.add(failed.future);
      final pending = store.search('https://one.substack.com/p/essay');
      await Future<void>.delayed(Duration.zero);
      failed.completeError(StateError('article offline'));
      await pending;
      expect(store.state.publications.single.id, 'one');
      expect(store.state.error, isNotNull);
      await store.refresh();
      expect(store.state.posts.single.slug, 'essay');
    });

    test('disposal prevents late publication results and further requests', () async {
      final pending = Completer<List<SubstackPublication>>();
      client.publicationResponses.add(pending.future);
      final loading = store.search('climate change');
      await store.destroy();
      pending.complete([publication('late')]);
      await loading;
      await store.search('next query');
      expect(client.searches, hasLength(1));
    });
  });

  group('followed publication article search', () {
    late SearchClient client;
    late SubstackSearchStore store;
    setUp(() {
      client = SearchClient();
      store = SubstackSearchStore(client, followed: () => [publication('one'), publication('two')]);
    });
    tearDown(() async {
      await store.destroy();
      client.httpClient.close();
    });

    test('merges newest-first without confusing equal IDs on different publications', () async {
      client.postResponses['one'] = [
        Future.value([article('same', pub: 'one', day: 1)]),
      ];
      client.postResponses['two'] = [
        Future.value([article('same', pub: 'two', day: 2)]),
      ];
      await store.search('essay', tab: SubstackSearchTab.posts);
      expect(store.state.posts.map((post) => post.publicationName), ['Publication two', 'Publication one']);
      expect(client.articleCalls, hasLength(2));
    });

    test('partial failure retains other articles and retries only the failed publication', () async {
      final failed = Completer<List<SubstackPost>>();
      client.postResponses['two'] = [failed.future];
      final pending = store.search('essay', tab: SubstackSearchTab.posts);
      failed.completeError(StateError('two offline'));
      await pending;
      expect(store.state.failedCount, 1);
      expect(store.state.posts, hasLength(1));
      await store.retryFailedPosts();
      expect(store.state.failedCount, 0);
      expect(client.articleCalls.where((call) => call.id == 'one'), hasLength(1));
      expect(client.articleCalls.where((call) => call.id == 'two'), hasLength(2));
      expect(store.state.posts, hasLength(2));
    });

    test('paging uses independent offsets and stops an unchanged result page', () async {
      client.postResponses['one'] = [
        Future.value([article('a')]),
        Future.value([article('a')]),
      ];
      client.postResponses['two'] = [Future.value([])];
      await store.search('essay', tab: SubstackSearchTab.posts);
      await store.loadMore();
      expect(client.articleCalls.map((call) => (call.id, call.offset)), [('one', 0), ('two', 0), ('one', 20)]);
      expect(store.state.posts, hasLength(1));
      expect(store.state.hasMore, isFalse);
    });

    test('failed refresh keeps earlier readable article results', () async {
      await store.search('essay', tab: SubstackSearchTab.posts);
      final one = Completer<List<SubstackPost>>();
      final two = Completer<List<SubstackPost>>();
      client.postResponses['one'] = [one.future];
      client.postResponses['two'] = [two.future];
      final pending = store.refresh();
      one.completeError(StateError('offline'));
      two.completeError(StateError('offline'));
      await pending;
      expect(store.state.failedCount, 2);
      expect(store.state.posts, hasLength(2));
      await store.retryFailedPosts();
      expect(store.state.posts, hasLength(2));
      expect(store.state.failedCount, 0);
    });

    test('a changed followed publication host cannot commit an older search', () async {
      var selected = publication('one');
      final scoped = SubstackSearchStore(client, followed: () => [selected]);
      final old = Completer<List<SubstackPost>>();
      client.postResponses['one'] = [old.future];
      final pending = scoped.search('essay', tab: SubstackSearchTab.posts);
      selected = const SubstackPublication(subdomain: 'one', baseUrl: 'https://new.example', name: 'New host');
      old.complete([article('stale')]);
      await pending;
      expect(scoped.state.posts, isEmpty);
      expect(scoped.state.error, isNotNull);
      await scoped.refresh();
      expect(scoped.state.error, isNull);
      expect(scoped.state.posts, hasLength(1));
      await scoped.destroy();
    });

    test('load more resets to the current followed set instead of paging a removed source', () async {
      var followed = [publication('one')];
      final scoped = SubstackSearchStore(client, followed: () => followed);
      await scoped.search('essay', tab: SubstackSearchTab.posts);
      followed = [publication('two')];
      await scoped.loadMore();
      expect(client.articleCalls.map((call) => (call.id, call.offset)), [('one', 0), ('two', 0)]);
      expect(scoped.state.posts.single.publicationName, 'Publication two');
      await scoped.destroy();
    });

    test('overlapping article pages update metadata without duplicating rows', () async {
      client.postResponses['one'] = [
        Future.value([article('same', day: 1)]),
        Future.value([article('same', day: 5)]),
      ];
      client.postResponses['two'] = [Future.value([])];
      await store.search('essay', tab: SubstackSearchTab.posts);
      await store.loadMore();
      expect(store.state.posts, hasLength(1));
      expect(store.state.posts.single.publishedAt?.day, 5);
      expect(store.state.hasMore, isFalse);
    });

    test('only four publication searches run before the next batch starts', () async {
      final multi = SubstackSearchStore(client, followed: () => [for (var i = 0; i < 9; i++) publication('pub$i')]);
      final first = [for (var i = 0; i < 4; i++) Completer<List<SubstackPost>>()];
      for (var i = 0; i < 4; i++) {
        client.postResponses['pub$i'] = [first[i].future];
      }
      final pending = multi.search('essay', tab: SubstackSearchTab.posts);
      expect(client.articleCalls, hasLength(4));
      for (final next in first) {
        next.complete([]);
      }
      await pending;
      expect(client.articleCalls, hasLength(9));
      await multi.destroy();
    });

    test('changing query prevents the old search from starting further batches', () async {
      final multi = SubstackSearchStore(client, followed: () => [for (var i = 0; i < 9; i++) publication('pub$i')]);
      final first = [for (var i = 0; i < 4; i++) Completer<List<SubstackPost>>()];
      for (var i = 0; i < 4; i++) {
        client.postResponses['pub$i'] = [first[i].future];
      }
      final pending = multi.search('old', tab: SubstackSearchTab.posts);
      await multi.search('new', tab: SubstackSearchTab.publications);
      for (final next in first) {
        next.complete([article('late')]);
      }
      await pending;
      expect(client.articleCalls, hasLength(4));
      expect(multi.state.query, 'new');
      expect(multi.state.posts, isEmpty);
      await multi.destroy();
    });
  });

  group('publication preview', () {
    late SearchClient client;
    late SubstackPreviewStore store;
    setUp(() {
      client = SearchClient();
      store = SubstackPreviewStore(client);
    });
    tearDown(() async {
      await store.destroy();
      client.httpClient.close();
    });

    test('failed replacement lookup cannot reuse the prior publication', () async {
      await store.lookup('one');
      final failed = Completer<SubstackPublication>();
      client.resolveResponses.add(failed.future);
      final pending = store.lookup('two');
      failed.completeError(StateError('offline'));
      await pending;
      var writes = 0;
      expect(await store.follow((_) async => writes++), isFalse);
      expect(store.state.publication, isNull);
      expect(writes, 0);
    });

    test('looking up an article never silently follows it', () async {
      await store.lookup('https://one.substack.com/p/essay');
      expect(store.state.publication?.id, 'one');
      expect(store.state.post?.slug, 'essay');
      expect(store.state.followed, isFalse);
      var writes = 0;
      expect(await store.follow((_) async => writes++), isTrue);
      expect(await store.follow((_) async => writes++), isFalse);
      expect(writes, 1);
    });

    test('new lookup supersedes a slow earlier preview', () async {
      final old = Completer<SubstackPublication>();
      client.resolveResponses.add(old.future);
      final pending = store.lookup('old');
      await store.lookup('new');
      old.complete(publication('old'));
      await pending;
      expect(store.state.publication?.id, 'one');
    });

    test('follow return state remains changed across subsequent previews', () async {
      await store.lookup('one');
      await store.follow((_) async {});
      await store.lookup('two');
      expect(store.state.followed, isFalse);
      expect(store.followedAny, isTrue);
    });

    test('pending follow serializes user writes and blocks a new lookup', () async {
      await store.lookup('one');
      final saved = Completer<void>();
      final pending = store.follow((_) => saved.future);
      await store.lookup('two');
      expect(client.resolves, ['one']);
      expect(await store.follow((_) async => fail('duplicate write')), isFalse);
      saved.complete();
      expect(await pending, isTrue);
    });

    test('closing a preview prevents late lookup from starting article fetch', () async {
      final pub = Completer<SubstackPublication>();
      client.resolveResponses.add(pub.future);
      final pending = store.lookup('https://one.substack.com/p/essay');
      await store.destroy();
      pub.complete(publication('one'));
      await pending;
      expect(client.requestedArticles, isEmpty);
    });

    test('article failure leaves preview followable and independently retryable', () async {
      final post = Completer<SubstackPost>();
      client.articleResponses.add(post.future);
      final pending = store.lookup('https://one.substack.com/p/essay');
      await Future<void>.delayed(Duration.zero);
      post.completeError(StateError('offline'));
      await pending;
      expect(store.state.postError, isNotNull);
      expect(await store.follow((_) async {}), isTrue);
      await store.retryPost();
      expect(store.state.followed, isTrue);
      expect(store.state.postError, isNull);
      expect(store.state.post?.slug, 'essay');
    });
  });

  for (final add in [false, true]) {
    testWidgets('compact enlarged-text ${add ? 'preview' : 'search'} remains usable', (tester) async {
      tester.view.physicalSize = const Size(320, 720);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final client = SearchClient();
      final prefs = PrefServiceCache(defaults: {substackSearchHistoryKey: '[]'});
      final pubs = (await tester.runAsync(() async => SubstackPublicationsStore(prefs)))!;
      addTearDown(() async {
        await tester.pumpWidget(const SizedBox());
        await tester.pump();
        await tester.runAsync(pubs.destroy);
        client.httpClient.close();
      });
      await tester.pumpWidget(
        PrefService(
          service: prefs,
          child: MultiProvider(
            providers: [
              Provider<SubstackClient>.value(value: client),
              Provider<SubstackPublicationsStore>.value(value: pubs),
            ],
            child: MaterialApp(
              localizationsDelegates: const [
                L10n.delegate,
                GlobalMaterialLocalizations.delegate,
                GlobalWidgetsLocalizations.delegate,
                GlobalCupertinoLocalizations.delegate,
              ],
              supportedLocales: L10n.delegate.supportedLocales,
              builder: (context, child) => MediaQuery(
                data: MediaQuery.of(context).copyWith(textScaler: const TextScaler.linear(2)),
                child: child!,
              ),
              home: add ? const SubstackAddScreen() : const SubstackSearchScreen(initialQuery: 'climate change'),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle(
        const Duration(milliseconds: 100),
        EnginePhase.sendSemanticsUpdate,
        const Duration(seconds: 5),
      );
      if (add) {
        await tester.enterText(find.byType(TextField), 'one');
        await tester.tap(find.text('Preview publication'));
        await tester.pumpAndSettle();
        expect(find.text('Publication one'), findsOneWidget);
        expect(pubs.state, isEmpty);
      } else {
        expect(find.text('Publication one'), findsOneWidget);
        await tester.tap(find.text('Posts').first);
        await tester.pumpAndSettle(
          const Duration(milliseconds: 100),
          EnginePhase.sendSemanticsUpdate,
          const Duration(seconds: 5),
        );
        expect(find.text('Posts from followed publications'), findsOneWidget);
        expect(find.text('Follow a publication to search its articles'), findsOneWidget);
      }
      expect(tester.takeException(), isNull);
    });
  }
}
