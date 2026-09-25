import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/bluesky/bluesky_client.dart';
import 'package:xta/plugins/bluesky/bluesky_likes_store.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/bluesky/bluesky_search_sheet.dart';
import 'package:xta/plugins/bluesky/bluesky_search_store.dart';
import 'package:xta/plugins/bluesky/bluesky_store.dart';

import 'support/bluesky_reading_harness.dart';

class SearchClient extends BlueskyClient {
  SearchClient({super.resolveBaseUrl});
  final peopleCalls = <({String query, String? cursor})>[];
  final postsCalls = <({String query, String? cursor, String sort, String? author, List<String> tags})>[];
  final postLookups = <List<String>>[];
  final actors = <String>[];
  final peopleReplies = <Future<BlueskyActorsPage>>[];
  final postsReplies = <Future<BlueskyFeedPage>>[];
  Completer<List<BlueskyProfile>>? suggestions;
  @override
  Future<BlueskyActorsPage> searchActorsPage(String q, {int limit = 20, String? cursor}) {
    peopleCalls.add((query: q, cursor: cursor));
    return peopleReplies.isEmpty
        ? Future.value(const BlueskyActorsPage(actors: [blueProfile]))
        : peopleReplies.removeAt(0);
  }

  @override
  Future<BlueskyFeedPage> searchPosts(
    String q, {
    int limit = 20,
    String? cursor,
    String sort = 'latest',
    String? author,
    List<String> tags = const [],
  }) {
    postsCalls.add((query: q, cursor: cursor, sort: sort, author: author, tags: tags));
    return postsReplies.isEmpty ? Future.value(BlueskyFeedPage(posts: [bluePost('root')])) : postsReplies.removeAt(0);
  }

  @override
  Future<List<BlueskyProfile>> getSuggestions({int limit = 20}) => suggestions?.future ?? Future.value([blueProfile]);
  @override
  Future<BlueskyProfile> getProfile(String actor) async {
    actors.add(actor);
    return blueProfile;
  }

  @override
  Future<List<BlueskyPost>> getPosts(List<String> uris) async {
    postLookups.add(uris);
    return [bluePost('root')];
  }
}

void main() {
  group('strict public post targets', () {
    test('canonical URL and AT URI preserve actor and record key', () {
      expect(blueskySearchPostTarget('https://bsky.app/profile/maya.bsky.social/post/root?source=share'), (
        actor: 'maya.bsky.social',
        rkey: 'root',
      ));
      expect(blueskySearchPostTarget('at://did:plc:maya/app.bsky.feed.post/root'), (
        actor: 'did:plc:maya',
        rkey: 'root',
      ));
      expect(blueskySearchPostTarget('at://did:web:example.com/app.bsky.feed.post/root'), (
        actor: 'did:web:example.com',
        rkey: 'root',
      ));
    });
    for (final input in [
      'https://bsky.app/profile/maya.bsky.social',
      'https://evil.example/profile/maya.bsky.social/post/root',
      'https://bsky.app.evil.example/profile/maya.bsky.social/post/root',
      'https://user@bsky.app/profile/maya.bsky.social/post/root',
      'https://bsky.app/profile/maya.bsky.social/post/root/extra',
      'at://did:plc:maya/app.bsky.graph.list/root',
      'at://did:plc:maya/app.bsky.feed.post/..',
      'at://did:plc:maya/app.bsky.feed.post/root?query',
    ]) {
      test('rejects $input', () => expect(blueskySearchPostTarget(input), isNull));
    }
    test('post URL cannot be interpreted as an author filter', () {
      expect(blueskySearchActor('https://bsky.app/profile/maya.bsky.social/post/root'), isNull);
      expect(blueskySearchActor('@maya.bsky.social'), 'maya.bsky.social');
      expect(blueskySearchActor('https://bsky.app/profile/did:web:example.com'), 'did:web:example.com');
    });
  });

  group('search store', () {
    late SearchClient client;
    late BlueskySearchStore store;
    setUp(() {
      client = SearchClient();
      store = BlueskySearchStore(client);
    });
    tearDown(() async {
      await store.destroy();
      client.httpClient.close();
    });

    test('profile search begins with an author filter ready for a query', () async {
      final scoped = BlueskySearchStore(client, initialTab: BlueskySearchTab.posts, initialAuthor: 'maya.bsky.social');
      await scoped.search('coast');
      expect(client.postsCalls.single.author, 'maya.bsky.social');
      expect(client.postsCalls.single.query, 'coast');
      await scoped.destroy();
    });

    test('keeps independent pages when changing tabs', () async {
      await store.search('sketches');
      await store.select(BlueskySearchTab.posts);
      await store.select(BlueskySearchTab.people);
      expect(client.peopleCalls, hasLength(1));
      expect(client.postsCalls, hasLength(1));
      expect(store.state.people.items, [blueProfile]);
      expect(store.state.posts.items.single.uri, bluePost('root').uri);
    });

    test('older successful people search cannot replace a newer one', () async {
      final old = Completer<BlueskyActorsPage>();
      client.peopleReplies.add(old.future);
      final pending = store.search('old');
      await store.search('new');
      old.complete(const BlueskyActorsPage(actors: []));
      await pending;
      expect(store.state.query, 'new');
      expect(store.state.people.items, [blueProfile]);
    });

    test('older failed post search cannot replace a newer one', () async {
      await store.select(BlueskySearchTab.posts);
      final old = Completer<BlueskyFeedPage>();
      client.postsReplies.add(old.future);
      final pending = store.search('old');
      await store.search('new');
      old.completeError(StateError('offline'));
      await pending;
      expect(store.state.query, 'new');
      expect(store.state.posts.error, isNull);
      expect(store.state.posts.items, hasLength(1));
    });

    test('clearing query invalidates outstanding reads', () async {
      final pendingPage = Completer<BlueskyActorsPage>();
      client.peopleReplies.add(pendingPage.future);
      final pending = store.search('old');
      await store.search('');
      pendingPage.complete(const BlueskyActorsPage(actors: [blueProfile]));
      await pending;
      expect(store.state.query, isEmpty);
      expect(store.state.people.items, isEmpty);
    });

    test('suggestion failures stay separate from a submitted query', () async {
      client.suggestions = Completer<List<BlueskyProfile>>();
      final pending = store.loadSuggestions();
      await store.search('sketches');
      client.suggestions!.completeError(StateError('offline'));
      await pending;
      expect(store.state.people.error, isNull);
      expect(store.state.people.items, [blueProfile]);
      expect(store.state.suggestions.error, isNotNull);
    });

    test('people pagination deduplicates and stops a repeated cursor', () async {
      client.peopleReplies.addAll([
        Future.value(const BlueskyActorsPage(actors: [blueProfile], cursor: 'next')),
        Future.value(const BlueskyActorsPage(actors: [blueProfile], cursor: 'next')),
      ]);
      await store.search('maya');
      await store.loadMore();
      await store.loadMore();
      expect(store.state.people.items, [blueProfile]);
      expect(store.state.people.cursor, isNull);
      expect(client.peopleCalls.last.cursor, 'next');
      expect(client.peopleCalls, hasLength(2));
    });

    test('post cursor cycle terminates without discarding newly loaded posts', () async {
      await store.select(BlueskySearchTab.posts);
      client.postsReplies.addAll([
        Future.value(BlueskyFeedPage(posts: [bluePost('root')], cursor: 'one')),
        Future.value(BlueskyFeedPage(posts: [bluePost('root'), bluePost('a')], cursor: 'two')),
        Future.value(BlueskyFeedPage(posts: [bluePost('b')], cursor: 'one')),
      ]);
      await store.search('sketches');
      await store.loadMore();
      await store.loadMore();
      expect(store.state.posts.items, hasLength(3));
      expect(store.state.posts.cursor, isNull);
    });

    test('load more failure preserves results and cursor for retry', () async {
      await store.select(BlueskySearchTab.posts);
      client.postsReplies.add(Future.value(BlueskyFeedPage(posts: [bluePost('root')], cursor: 'next')));
      await store.search('sketches');
      final failed = Completer<BlueskyFeedPage>();
      client.postsReplies.add(failed.future);
      final pending = store.loadMore();
      failed.completeError(StateError('offline'));
      await pending;
      expect(store.state.posts.items, hasLength(1));
      expect(store.state.posts.cursor, 'next');
      expect(store.state.posts.retryMore, isTrue);
      await store.loadMore();
      expect(store.state.posts.error, isNull);
    });

    test('refresh error preserves content but retries refresh instead of pagination', () async {
      await store.select(BlueskySearchTab.posts);
      client.postsReplies.add(Future.value(BlueskyFeedPage(posts: [bluePost('root')], cursor: 'next')));
      await store.search('sketches');
      final failed = Completer<BlueskyFeedPage>();
      client.postsReplies.add(failed.future);
      final pending = store.refresh();
      failed.completeError(StateError('offline'));
      await pending;
      expect(store.state.posts.items, hasLength(1));
      expect(store.state.posts.retryMore, isFalse);
    });

    test('structured filters invalidate earlier post requests and preserve people', () async {
      await store.search('sketches');
      await store.select(BlueskySearchTab.posts);
      final old = Completer<BlueskyFeedPage>();
      client.postsReplies.add(old.future);
      final pending = store.setFilters(sort: BlueskySearchSort.top);
      await store.setFilters(author: 'maya.bsky.social', tag: 'art');
      old.complete(const BlueskyFeedPage(posts: []));
      await pending;
      final sent = client.postsCalls.last;
      expect(sent.query, 'sketches');
      expect(sent.cursor, isNull);
      expect(sent.sort, 'top');
      expect(sent.author, 'maya.bsky.social');
      expect(sent.tags, ['art']);
      expect(store.state.posts.items, hasLength(1));
      expect(store.state.people.items, [blueProfile]);
    });

    test('a hashtag uses a structured exact tag', () async {
      await store.select(BlueskySearchTab.posts);
      await store.search('#art');
      expect(client.postsCalls.single.query, isEmpty);
      expect(client.postsCalls.single.tags, ['art']);
    });

    test('a post URL switches to posts and resolves through the configured AppView', () async {
      await store.search('https://bsky.app/profile/maya.bsky.social/post/root');
      expect(store.state.tab, BlueskySearchTab.posts);
      expect(client.actors, ['maya.bsky.social']);
      expect(client.postLookups.single, ['at://did:plc:maya/app.bsky.feed.post/root']);
      expect(client.peopleCalls, isEmpty);
      expect(client.postsCalls, isEmpty);
    });

    test('an AT URI skips handle resolution', () async {
      await store.search('at://did:plc:maya/app.bsky.feed.post/root');
      expect(client.actors, isEmpty);
      expect(client.postLookups.single, ['at://did:plc:maya/app.bsky.feed.post/root']);
    });

    test('AppView changes discard the old response and expose retry', () async {
      var source = 'https://one.example';
      final changingClient = SearchClient(resolveBaseUrl: () => source);
      final changingStore = BlueskySearchStore(changingClient);
      final old = Completer<BlueskyActorsPage>();
      changingClient.peopleReplies.add(old.future);
      final pending = changingStore.search('maya');
      source = 'https://two.example';
      old.complete(const BlueskyActorsPage(actors: [blueProfile], cursor: 'old-cursor'));
      await pending;
      expect(changingStore.state.people.items, isEmpty);
      expect(changingStore.state.people.cursor, isNull);
      expect(changingStore.state.people.error, isNotNull);
      await changingStore.refresh();
      expect(changingClient.peopleCalls.last.cursor, isNull);
      expect(changingStore.state.people.items, [blueProfile]);
      await changingStore.destroy();
      changingClient.httpClient.close();
    });

    test('unchanged filters do not issue another request', () async {
      await store.select(BlueskySearchTab.posts);
      await store.search('sketches');
      await store.setFilters(sort: BlueskySearchSort.latest, author: '', tag: '');
      expect(client.postsCalls, hasLength(1));
    });

    test('disposal ignores completion and further commands', () async {
      final next = Completer<BlueskyActorsPage>();
      client.peopleReplies.add(next.future);
      final pending = store.search('old');
      await store.destroy();
      next.complete(const BlueskyActorsPage(actors: [blueProfile]));
      await pending;
      await store.search('new');
      expect(client.peopleCalls, hasLength(1));
    });
  });

  testWidgets('full-screen compact search supports filters and tabs without overflow', (tester) async {
    tester.view.physicalSize = const Size(320, 720);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final client = SearchClient();
    final accounts = BlueskyAccountsStore();
    final prefs = PrefServiceCache(defaults: {optionPluginBlueskySearchHistory: '[]'});
    final likes = BlueLikes(prefs);
    addTearDown(() async {
      await tester.pumpWidget(const SizedBox());
      await tester.pump();
      await accounts.destroy();
      await likes.destroy();
      client.httpClient.close();
    });
    await tester.pumpWidget(
      PrefService(
        service: prefs,
        child: MultiProvider(
          providers: [
            Provider<BlueskyClient>.value(value: client),
            Provider<BlueskyAccountsStore>.value(value: accounts),
            Provider<BlueskyLikesStore>.value(value: likes),
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
            home: const BlueskySearchScreen(initialQuery: 'sketches'),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Maya Chen'), findsOneWidget);
    expect(tester.takeException(), isNull);
    await tester.tap(find.text('Posts').first);
    await tester.pumpAndSettle();
    expect(find.text('Latest'), findsOneWidget);
    expect(find.textContaining('A few sketches'), findsOneWidget);
    expect(tester.takeException(), isNull);
    await tester.tap(find.text('Filters'));
    await tester.pumpAndSettle();
    expect(find.text('Author handle'), findsOneWidget);
    expect(tester.takeException(), isNull);
    await tester.tap(find.text('Cancel'));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  });
}
