import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/plugins/bluesky/bluesky_client.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/bluesky/bluesky_profile_links.dart';
import 'package:xta/plugins/bluesky/bluesky_profile_screen.dart';
import 'package:xta/plugins/bluesky/bluesky_profile_store.dart';
import 'package:xta/plugins/plugin_profile_tabs.dart';

import 'support/bluesky_reading_harness.dart';

class _ProfileClient extends BlueskyClient {
  BlueskyProfile profile = blueProfile;
  Future<BlueskyProfile>? nextProfile;
  Future<List<BlueskyPost>>? nextPins;
  Future<BlueskyFeedPage> Function(String? cursor)? nextFeed;
  final cursors = <String?>[];
  final actors = <String>[];
  String source = 'https://first.example';

  @override
  String get baseUrl => source;

  @override
  Future<BlueskyProfile> getProfile(String actor) async => nextProfile ?? profile;

  @override
  Future<List<BlueskyPost>> getPosts(List<String> uris) async => nextPins ?? [];

  @override
  Future<BlueskyFeedPage> getAuthorFeed(String actor, {int limit = 20, String? cursor, String? filter}) async {
    cursors.add(cursor);
    actors.add(actor);
    return nextFeed?.call(cursor) ?? BlueskyFeedPage(posts: [bluePost('one')], cursor: 'next');
  }
}

BlueskyProfile _pinnedProfile(String uri) => BlueskyProfile(
  did: blueProfile.did,
  handle: blueProfile.handle,
  displayName: blueProfile.displayName,
  description: blueProfile.description,
  pinnedPostUri: uri,
);

void main() {
  late _ProfileClient client;
  late BlueLikes likes;
  late BlueskyProfileStore store;
  var destroyed = false;
  setUp(() {
    destroyed = false;
    client = _ProfileClient();
    likes = BlueLikes(PrefServiceCache());
    store = BlueskyProfileStore(client, likes, blueProfile.handle);
  });
  tearDown(() async {
    if (!destroyed) await store.destroy();
    await likes.destroy();
    client.httpClient.close();
  });

  test('metadata failure does not block refresh of an already loaded feed', () async {
    await store.refresh();
    final metadata = Completer<BlueskyProfile>();
    client.nextProfile = metadata.future;
    client.nextFeed = (_) async => BlueskyFeedPage(posts: [bluePost('fresh')]);
    final refreshing = store.refresh();
    await Future<void>.delayed(Duration.zero);
    expect(store.state.feed.posts.single.uri, bluePost('fresh').uri);
    expect(store.state.loading, isTrue);
    metadata.completeError(StateError('metadata offline'));
    await refreshing;
    expect(store.state.feed.posts.single.uri, bluePost('fresh').uri);
    expect(store.state.profile, blueProfile);
    expect(store.state.error, isNotNull);
  });

  test('failed refresh retries page one without mistaking old cursor for pagination', () async {
    await store.refresh();
    client.nextFeed = (_) async => throw StateError('offline');
    await store.refresh();
    expect(store.state.feed.posts.single.uri, bluePost('one').uri);
    expect(store.state.feed.cursor, 'next');
    expect(store.state.feed.failedMore, isFalse);
    client.nextFeed = (_) async => BlueskyFeedPage(posts: [bluePost('replacement')], cursor: 'new-next');
    await store.retry();
    expect(client.cursors.last, isNull);
    expect(store.state.feed.posts.single.uri, bluePost('replacement').uri);
  });

  test('failed next page retries its cursor and preserves earlier cards', () async {
    await store.refresh();
    client.nextFeed = (_) async => throw StateError('offline');
    await store.loadMore();
    expect(store.state.feed.failedMore, isTrue);
    client.nextFeed = (_) async => BlueskyFeedPage(posts: [bluePost('two')]);
    await store.retry();
    expect(client.cursors.last, 'next');
    expect(store.state.feed.posts.map((post) => post.uri), [bluePost('one').uri, bluePost('two').uri]);
  });

  test('cursor cycles terminate while new posts from the final page survive', () async {
    await store.refresh();
    client.nextFeed = (cursor) async => cursor == 'next'
        ? BlueskyFeedPage(posts: [bluePost('two')], cursor: 'third')
        : BlueskyFeedPage(posts: [bluePost('three')], cursor: 'next');
    await store.loadMore();
    await store.loadMore();
    expect(store.state.feed.cursor, isNull);
    expect(store.state.feed.posts, hasLength(3));
    final count = client.cursors.length;
    await store.loadMore();
    expect(client.cursors.length, count);
  });

  test('refresh invalidates an older pagination result', () async {
    await store.refresh();
    final oldPage = Completer<BlueskyFeedPage>();
    client.nextFeed = (_) => oldPage.future;
    final more = store.loadMore();
    client.nextFeed = (_) async => BlueskyFeedPage(posts: [bluePost('new')]);
    await store.refresh();
    oldPage.complete(BlueskyFeedPage(posts: [bluePost('stale')]));
    await more;
    expect(store.state.feed.posts.single.uri, bluePost('new').uri);
  });

  test('a handle resolving to a new DID clears old identity feeds and pending pages', () async {
    await store.refresh();
    final oldPage = Completer<BlueskyFeedPage>();
    var reads = 0;
    client.nextFeed = (_) =>
        ++reads == 1 ? oldPage.future : Future.value(BlueskyFeedPage(posts: [bluePost('replacement')]));
    client.profile = const BlueskyProfile(
      did: 'did:plc:new-identity',
      handle: 'maya.bsky.social',
      displayName: 'New person',
      description: '',
    );
    final refresh = store.refresh();
    await Future<void>.delayed(Duration.zero);
    expect(client.actors.last, 'did:plc:new-identity');
    expect(store.state.feed.posts.single.uri, bluePost('replacement').uri);
    oldPage.complete(BlueskyFeedPage(posts: [bluePost('old-account')]));
    await refresh;
    expect(store.state.feed.posts.single.uri, bluePost('replacement').uri);
  });

  test('switching AppView rejects late results and reloads from the selected source', () async {
    await store.refresh();
    final oldPage = Completer<BlueskyFeedPage>();
    client.nextFeed = (_) => oldPage.future;
    final more = store.loadMore();
    client.source = 'https://second.example';
    client.nextFeed = (_) async => BlueskyFeedPage(posts: [bluePost('new-source')]);
    oldPage.complete(BlueskyFeedPage(posts: [bluePost('stale-source')]));
    await more;
    await Future<void>.delayed(Duration.zero);
    expect(store.state.feed.posts.map((post) => post.uri), [bluePost('new-source').uri]);
    expect(client.cursors.last, isNull);
    expect(store.state.feed.error, isNull);
  });

  test('pin is first and appears once only in Posts', () async {
    final pin = bluePost('one');
    client.profile = _pinnedProfile(pin.uri);
    client.nextPins = Future.value([pin]);
    await store.refresh();
    expect(store.state.pinnedPost?.uri, pin.uri);
    expect(store.state.visiblePosts.map((post) => post.uri), [pin.uri]);
    await store.select(PluginProfileFeedTab.replies);
    expect(store.state.visiblePosts, isEmpty);
  });

  test('pin failure retains the feed and has an independent retry', () async {
    final pin = bluePost('pinned');
    client.profile = _pinnedProfile(pin.uri);
    final pinRequest = Completer<List<BlueskyPost>>();
    client.nextPins = pinRequest.future;
    final refreshing = store.refresh();
    await Future<void>.delayed(Duration.zero);
    expect(store.state.feed.loaded, isTrue);
    pinRequest.completeError(StateError('pin offline'));
    await refreshing;
    expect(store.state.error, isNull);
    expect(store.state.pinError, isNotNull);
    final feedCalls = client.cursors.length;
    client.nextPins = Future.value([pin]);
    await store.retryPin();
    expect(store.state.pinError, isNull);
    expect(store.state.visiblePosts.first.uri, pin.uri);
    expect(client.cursors.length, feedCalls);
  });

  test('a removed pin and disposed late pin do not remain visible', () async {
    final pin = bluePost('pinned');
    client.profile = _pinnedProfile(pin.uri);
    client.nextPins = Future.value([pin]);
    await store.refresh();
    client.profile = blueProfile;
    await store.refresh();
    expect(store.state.pinnedPost, isNull);
    client.profile = _pinnedProfile(pin.uri);
    final pendingPin = Completer<List<BlueskyPost>>();
    client.nextPins = pendingPin.future;
    final refresh = store.refresh();
    await Future<void>.delayed(Duration.zero);
    await store.destroy();
    destroyed = true;
    pendingPin.complete([pin]);
    await refresh;
    expect(store.state.pinnedPost, isNull);
  });

  test('bio links preserve UTF-8 offsets and balanced URL punctuation', () {
    const bio = '🌌 See (https://example.org/a_(b)). Then https://other.example/path!';
    final links = blueskyProfileLinks(bio);
    expect(links.map((link) => link.value), ['https://example.org/a_(b)', 'https://other.example/path']);
    final bytes = utf8.encode(bio);
    for (final link in links) {
      expect(utf8.decode(bytes.sublist(link.byteStart, link.byteEnd)), link.value);
    }
    expect(blueskyProfileLinks('javascript:alert(1) ftp://example.org https:// https://user:pass@host'), isEmpty);
  });

  testWidgets('profile metadata fits narrow large text and exposes posts count', (tester) async {
    tester.view.physicalSize = const Size(280, 1000);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final harness = BlueReadingHarness();
    addTearDown(() => harness.close(tester));
    await tester.pumpWidget(
      harness.app(
        const Scaffold(
          body: SingleChildScrollView(child: BlueskyProfileCard(profile: blueProfile)),
        ),
        scale: 2,
        rtl: true,
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('906 posts'), findsOneWidget);
    expect(find.byType(SelectableText), findsOneWidget);
    expect(tester.takeException(), isNull);
    final followCount = find.ancestor(of: find.textContaining('followers'), matching: find.byType(InkWell));
    expect(tester.getSize(followCount).height, greaterThanOrEqualTo(48));
  });

  testWidgets('Posts identifies and renders the pinned card once', (tester) async {
    final harness = BlueReadingHarness();
    addTearDown(() => harness.close(tester));
    final pin = bluePost('one');
    client.profile = _pinnedProfile(pin.uri);
    client.nextPins = Future.value([pin]);
    await tester.pumpWidget(
      harness.app(
        Provider<BlueskyClient>.value(
          value: client,
          child: BlueskyProfileScreen(actor: blueProfile.handle),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Pinned post'), findsOneWidget);
    expect(find.text(pin.text), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
