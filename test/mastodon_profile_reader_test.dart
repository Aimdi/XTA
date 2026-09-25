import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_profile_screen.dart';
import 'package:xta/plugins/mastodon/mastodon_profile_store.dart';

import 'support/mastodon_harness.dart';

MastodonPost _post(String id, {String? timelineId}) => MastodonPost(
  id: id,
  timelineId: timelineId,
  acct: sampleProfile.acct,
  authorName: sampleProfile.displayName,
  text: id,
  url: '${sampleProfile.url}/$id',
);

class _Request {
  final bool media;
  final bool replies;
  final String? cursor;
  final result = Completer<List<MastodonPost>>();
  _Request(this.media, this.replies, this.cursor);
}

class _ProfileClient extends MastodonFixtureClient {
  final raw = List.generate(20, (index) => _post('post-$index'));
  List<MastodonPost> pinned = [];
  final requests = <_Request>[];

  @override
  Future<
    ({
      MastodonProfile profile,
      List<MastodonPost> posts,
      List<MastodonPost> rawPosts,
      Set<String> pinnedIds,
      String instance,
    })
  >
  profileAnywhere(List<String> instances, String acct) async => (
    profile: sampleProfile,
    posts: mergeMastodonPinned(pinned, raw),
    rawPosts: List.of(raw),
    pinnedIds: pinned.map((post) => post.id).toSet(),
    instance: 'https://studio.example',
  );

  @override
  Future<List<MastodonPost>> getStatuses(
    String instance,
    String id, {
    int limit = 20,
    bool excludeReplies = true,
    bool onlyMedia = false,
    bool pinned = false,
    String? maxId,
  }) {
    final request = _Request(onlyMedia, !excludeReplies, maxId);
    requests.add(request);
    return request.result.future;
  }
}

Future<void> _settle() => Future<void>.delayed(Duration.zero);

void main() {
  late _ProfileClient client;
  late MastodonProfileStore store;

  setUp(() {
    client = _ProfileClient();
    store = MastodonProfileStore(client, ['https://studio.example'], sampleProfile.acct);
  });
  tearDown(() async {
    await store.destroy();
    client.httpClient.close();
  });

  test('three tabs load independently and cache their own responses', () async {
    await store.refresh();
    final postsRequest = store.loadMore();
    store.select(MastodonProfileTab.replies);
    store.select(MastodonProfileTab.media);
    expect(client.requests, hasLength(3));
    expect(client.requests.map((request) => (request.media, request.replies)), [
      (false, false),
      (false, true),
      (true, true),
    ]);
    client.requests[2].result.complete([_post('photo')]);
    client.requests[1].result.complete([_post('reply')]);
    client.requests[0].result.complete([_post('older')]);
    await postsRequest;
    await _settle();
    expect(store.state.visible.single.id, 'photo');
    expect(store.state.posts.last.id, 'older');
    store.select(MastodonProfileTab.replies);
    expect(store.state.visible.single.id, 'reply');
    expect(store.state.loadingMore, isFalse);
    expect(client.requests, hasLength(3));
  });

  test('pins do not change the raw page cursor or hide later timeline entries', () async {
    client.raw[19] = _post('post-19', timelineId: 'wrapper-19');
    client.pinned = [client.raw.last];
    await store.refresh();
    expect(store.state.posts.first.id, 'post-19');
    final request = store.loadMore();
    expect(client.requests.single.cursor, 'wrapper-19');
    client.requests.single.result.complete([_post('older')]);
    await request;
    expect(store.state.posts.last.id, 'older');
  });

  test('a profile with only pinned posts does not claim another timeline page', () async {
    client.raw.clear();
    client.pinned = [_post('pin')];
    await store.refresh();
    expect(store.state.posts.single.id, 'pin');
    expect(store.state.morePosts, isFalse);
    await store.loadMore();
    expect(client.requests, isEmpty);
  });

  test('failed replies remain retryable without polluting the media tab', () async {
    await store.refresh();
    store.select(MastodonProfileTab.replies);
    client.requests.single.result.completeError(StateError('offline'));
    await _settle();
    expect(store.state.error, isA<StateError>());
    store.select(MastodonProfileTab.media);
    expect(store.state.error, isNull);
    client.requests.last.result.complete([_post('photo')]);
    await _settle();
    store.select(MastodonProfileTab.replies);
    expect(client.requests, hasLength(3));
    expect(client.requests.last.cursor, isNull);
    client.requests.last.result.complete([_post('recovered-reply')]);
    await _settle();
    expect(store.state.visible.single.id, 'recovered-reply');
    expect(store.state.media.single.id, 'photo');
    expect(store.state.error, isNull);
  });

  test('refresh invalidates every outstanding tab response', () async {
    await store.refresh();
    final pending = store.loadMore();
    await store.refresh();
    client.requests.single.result.complete([_post('stale')]);
    await pending;
    expect(store.state.posts.any((post) => post.id == 'stale'), isFalse);
    expect(store.state.loadingMore, isFalse);
  });

  test('a repeated page cannot create an endless pagination loop', () async {
    await store.refresh();
    final pending = store.loadMore();
    client.requests.single.result.complete(List.of(client.raw));
    await pending;
    expect(store.state.posts, hasLength(20));
    expect(store.state.morePosts, isFalse);
    await store.loadMore();
    expect(client.requests, hasLength(1));
  });

  test('disposed stores ignore in-flight replies', () async {
    await store.refresh();
    store.select(MastodonProfileTab.replies);
    await store.destroy();
    client.requests.single.result.complete([_post('late')]);
    await _settle();
    expect(store.state.visible, isEmpty);
  });

  testWidgets('profile metadata links, verified status and enlarged text remain readable', (tester) async {
    tester.view.physicalSize = const Size(320, 720);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final h = MastodonHarness();
    String? opened;
    final profile = MastodonProfile(
      id: 'profile',
      acct: 'a-very-long-account-name@studio.example',
      username: 'maya',
      displayName: 'Maya Chen with a longer display name',
      note: sampleProfile.note,
      url: sampleProfile.url,
      createdAt: DateTime.utc(2020, 3),
      fields: [
        MastodonField(
          name: 'Website',
          value: 'studio.example/photography-and-everyday-observations',
          url: 'https://studio.example/portfolio',
          verifiedAt: DateTime.utc(2024),
        ),
      ],
    );
    await tester.pumpWidget(
      h.app(
        scale: 2,
        rtl: true,
        child: Scaffold(
          body: SingleChildScrollView(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: MastodonProfileCard(profile: profile, following: false, onFieldTap: (url) => opened = url),
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.byTooltip('Verified link'), findsOneWidget);
    expect(find.text('Joined March 2020'), findsOneWidget);
    await tester.ensureVisible(find.text(profile.fields.single.value));
    await tester.tap(find.text(profile.fields.single.value));
    expect(opened, 'https://studio.example/portfolio');
    expect(tester.takeException(), isNull);
    await h.close(tester);
  });

  testWidgets('profile exposes replies and accessible profile actions on a compact screen', (tester) async {
    tester.view.physicalSize = const Size(320, 720);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final h = MastodonHarness();
    await tester.pumpWidget(h.app(child: const MastodonProfileScreen(acct: 'maya@studio.example')));
    await tester.pumpAndSettle();
    expect(find.byTooltip('Share link'), findsOneWidget);
    expect(find.descendant(of: find.byType(AppBar), matching: find.byTooltip('Open in browser')), findsOneWidget);
    final replies = find.byKey(const ValueKey('mastodon-profile-tab-replies'));
    await tester.ensureVisible(replies);
    await tester.tap(replies);
    await tester.pumpAndSettle();
    expect(find.text('Nothing to read yet'), findsOneWidget);
    expect(tester.takeException(), isNull);
    await h.close(tester);
  });

  test('profile browser actions reject malformed and non-web destinations', () {
    MastodonProfile profile(String url) =>
        MastodonProfile(id: '', acct: '', username: '', displayName: '', note: '', url: url);
    expect(mastodonProfileWebUrl(profile('javascript:alert(1)')), isNull);
    expect(mastodonProfileWebUrl(profile('https://user:password@example.com')), isNull);
    expect(mastodonProfileWebUrl(profile('https://example.com/@reader')), 'https://example.com/@reader');
  });
}
