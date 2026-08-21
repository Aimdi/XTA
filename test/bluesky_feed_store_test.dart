import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/bluesky/bluesky_client.dart';
import 'package:xta/plugins/bluesky/bluesky_feed.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/bluesky/bluesky_store.dart';

BlueskyPost _post(
  String id, {
  DateTime? at,
  String handle = 'alice.bsky.social',
  String? repostedByHandle,
}) {
  return BlueskyPost(
    uri: 'at://did:plc:a/app.bsky.feed.post/$id',
    cid: 'cid-$id',
    handle: handle,
    did: 'did:plc:a',
    authorName: handle,
    text: id,
    url: 'https://bsky.app/profile/$handle/post/$id',
    publishedAt: at ?? DateTime.utc(2026, 8, 1),
    repostedByHandle: repostedByHandle,
  );
}

class _Client extends BlueskyClient {
  _Client() : super(baseUrl: kBlueskyDefaultAppView);

  var calls = 0;
  final cursors = <String?>[];
  final Map<String, List<BlueskyPost>> feeds = {};

  @override
  Future<BlueskyFeedPage> getAuthorFeed(
    String actor, {
    int limit = 20,
    String? cursor,
    String? filter,
  }) async {
    calls++;
    cursors.add(cursor);
    return BlueskyFeedPage(posts: feeds[actor] ?? const [], cursor: cursor);
  }
}

BlueskyFeedStore _store(_Client client, List<BlueskyAccount> follows) {
  final accounts = BlueskyAccountsStore()..update(follows);
  return BlueskyFeedStore(client, accounts);
}

void main() {
  final alice = const BlueskyAccount(
    handle: 'alice.bsky.social',
    name: 'Alice',
  );
  final bob = const BlueskyAccount(handle: 'bob.bsky.social', name: 'Bob');

  group('stabilizeBlueskyFeed', () {
    test('a second page of the same uris is not a replacement', () {
      final page = [_post('a'), _post('b')];
      expect(sameBlueskyFeedPage(page, [_post('a'), _post('b')]), isTrue);
      expect(blueskyFeedShouldReplace(page, [_post('a'), _post('b')]), isFalse);
    });

    test('a cache-walk prefix does not shrink a painted feed', () {
      final painted = [_post('a'), _post('b'), _post('c')];
      expect(blueskyFeedShouldReplace(painted, [_post('a')]), isFalse);
    });

    test('new uris at the top may appear', () {
      final painted = [_post('b'), _post('c')];
      final incoming = [_post('a', at: DateTime.utc(2026, 8, 2)), ...painted];
      expect(blueskyFeedShouldReplace(painted, incoming), isTrue);
    });

    test('duplicate uris from two accounts collapse to one row', () {
      final original = _post('same', handle: 'alice.bsky.social');
      final boost = _post(
        'same',
        handle: 'alice.bsky.social',
        repostedByHandle: 'bob.bsky.social',
      );
      final rows = stabilizeBlueskyFeed([boost, original, original]);
      expect(rows.map((e) => e.uri), [
        'at://did:plc:a/app.bsky.feed.post/same',
      ]);
    });

    test('an empty poll does not blank a painted feed', () {
      expect(blueskyFeedShouldReplace([_post('a')], const []), isFalse);
    });
  });

  group('BlueskyFeedStore.refresh', () {
    test(
      'a second remount poll does not replace an unchanged first page',
      () async {
        final client = _Client()
          ..feeds['alice.bsky.social'] = [_post('a'), _post('b')];
        final store = _store(client, [alice]);

        await store.refresh();
        final first = store.state.map((e) => e.uri).toList();
        final fetchedAt = store.fetchedAt;
        var notifies = 0;
        store.observer(onState: (_) => notifies++);

        client.feeds['alice.bsky.social'] = [
          _post('a'),
          _post('b'),
          _post('c'),
        ];
        await store.refresh();

        expect(client.calls, 1);
        expect(client.cursors, [null]);
        expect(store.state.map((e) => e.uri), first);
        expect(store.fetchedAt, fetchedAt);
        expect(notifies, 0);
        expect(first, [
          'at://did:plc:a/app.bsky.feed.post/a',
          'at://did:plc:a/app.bsky.feed.post/b',
        ]);
      },
    );

    test('the first-page cursor is not reset on a later poll', () async {
      final client = _Client()..feeds['alice.bsky.social'] = [_post('a')];
      final store = _store(client, [alice]);

      await store.refresh();
      await store.refresh();
      await store.postsFor(['alice.bsky.social']);

      expect(client.calls, 1, reason: 'the cached first page is reused');
      expect(client.cursors, [
        null,
      ], reason: 'a remount must not send a new page-0 cursor');
      expect(store.state.map((e) => e.uri), [
        'at://did:plc:a/app.bsky.feed.post/a',
      ]);
    });

    test('pull-to-refresh asks again even inside the TTL', () async {
      final client = _Client()..feeds['alice.bsky.social'] = [_post('a')];
      final store = _store(client, [alice]);

      await store.refresh();
      await store.refresh(force: true);

      expect(client.calls, 2);
      expect(store.state.map((e) => e.uri), [
        'at://did:plc:a/app.bsky.feed.post/a',
      ]);
    });

    test('force refresh with the same uris does not notify', () async {
      final client = _Client()
        ..feeds['alice.bsky.social'] = [_post('a'), _post('b')];
      final store = _store(client, [alice]);
      await store.refresh();

      var notifies = 0;
      store.observer(onState: (_) => notifies++);
      await store.refresh(force: true);

      expect(notifies, 0);
      expect(identical(store.state, store.state), isTrue);
    });

    test('the same uri from two followed accounts is one row', () async {
      final shared = _post('shared');
      final client = _Client()
        ..feeds['alice.bsky.social'] = [shared]
        ..feeds['bob.bsky.social'] = [
          _post('shared', repostedByHandle: 'bob.bsky.social'),
        ];
      final store = _store(client, [alice, bob]);

      await store.refresh();

      expect(store.state.map((e) => e.uri), [
        'at://did:plc:a/app.bsky.feed.post/shared',
      ]);
    });
  });
}
