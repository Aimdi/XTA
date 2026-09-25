import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/bluesky/bluesky_client.dart';
import 'package:xta/plugins/bluesky/bluesky_feed.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/bluesky/bluesky_store.dart';

BlueskyPost _post(
  String id, {
  int likes = 0,
  List<String> labels = const [],
  String? reposter,
  DateTime? at,
  DateTime? repostedAt,
}) => BlueskyPost(
  uri: 'at://did:plc:alice/app.bsky.feed.post/$id',
  cid: id,
  did: 'did:plc:alice',
  handle: 'alice.test',
  authorName: 'Alice',
  text: id,
  url: 'https://bsky.app/profile/alice.test/post/$id',
  likeCount: likes,
  labels: labels,
  repostedByHandle: reposter,
  publishedAt: at ?? DateTime.utc(2026, 1, 1),
  repostedAt: repostedAt,
);

class _Client extends BlueskyClient {
  _Client({String Function()? server}) : super(resolveBaseUrl: server);
  final calls = <String>[];
  Future<List<BlueskyPost>> Function(String actor) read = (_) async => [];

  @override
  Future<BlueskyFeedPage> getAuthorFeed(String actor, {int limit = 20, String? cursor, String? filter}) async {
    calls.add(actor);
    return BlueskyFeedPage(posts: await read(actor));
  }
}

BlueskyFeedStore _store(_Client client, List<String> actors) {
  final accounts = BlueskyAccountsStore()
    ..update([for (final actor in actors) BlueskyAccount(handle: actor, name: actor)]);
  return BlueskyFeedStore(client, accounts);
}

void main() {
  group('Following identity and activity', () {
    test('new repost activity sorts ahead of newly authored posts', () {
      final old = _post('old', reposter: 'bob.test', repostedAt: DateTime.utc(2026, 9, 20));
      final recent = _post('recent', at: DateTime.utc(2026, 9, 19));
      expect(stabilizeBlueskyFeed([recent, old]).first, same(old));
    });

    test('followed original wins a duplicate repost regardless of merge order', () {
      final original = _post('same');
      final repost = _post('same', reposter: 'bob.test', repostedAt: DateTime.utc(2026, 9, 20));
      for (final rows in [
        [original, repost],
        [repost, original],
      ]) {
        expect(stabilizeBlueskyFeed(rows).single.isRepost, isFalse);
      }
    });

    test('newest repost wins among multiple reposters while source rank stays stable', () {
      final first = _post('other');
      final old = _post('same', reposter: 'old.test', repostedAt: DateTime.utc(2026, 9, 19));
      final fresh = _post('same', reposter: 'new.test', repostedAt: DateTime.utc(2026, 9, 20));
      final rows = dedupeBlueskyPosts([first, old, fresh]);
      expect(rows.first, same(first));
      expect(rows.last, same(fresh));
    });

    test('count and moderation updates repaint even when identities match', () {
      expect(sameBlueskyFeedPage([_post('same')], [_post('same', likes: 3)]), isFalse);
      expect(
        blueskyFeedDistinct([_post('same')]),
        isNot(
          blueskyFeedDistinct([
            _post('same', labels: ['!warn']),
          ]),
        ),
      );
    });
  });

  group('Following recovery', () {
    test('failed first load retries on re-entry', () async {
      final client = _Client()..read = (_) async => throw StateError('offline');
      final store = _store(client, ['alice.test']);
      await store.ensureLoaded();
      expect(store.refreshError, isA<StateError>());
      client.read = (_) async => [_post('recovered')];
      await store.ensureLoaded();
      expect(client.calls, hasLength(2));
      expect(store.state.single.text, 'recovered');
      expect(store.refreshError, isNull);
    });

    test('partial failure preserves failed author posts and retries them', () async {
      var fail = false;
      final client = _Client()
        ..read = (actor) async {
          if (actor == 'bob.test' && fail) throw StateError('offline');
          return [
            _post(
              actor == 'bob.test'
                  ? 'bob'
                  : fail
                  ? 'alice-new'
                  : 'alice',
            ),
          ];
        };
      final store = _store(client, ['alice.test', 'bob.test']);
      await store.ensureLoaded();
      fail = true;
      await store.refresh(force: true);
      expect(store.state.map((post) => post.text), containsAll(['bob', 'alice-new']));
      expect(store.refreshError, isA<StateError>());
      expect(store.pending(['alice.test', 'bob.test']), 1);
      fail = false;
      await store.ensureLoaded();
      expect(store.refreshError, isNull);
      expect(store.pending(['alice.test', 'bob.test']), 0);
    });

    test('unfollowing everyone clears the authoritative completed feed', () async {
      final client = _Client()..read = (_) async => [_post('old')];
      final store = _store(client, ['alice.test']);
      await store.ensureLoaded();
      store.accounts.update(const []);
      await store.ensureLoaded();
      expect(store.state, isEmpty);
    });

    test('forced bounded refresh progresses past first imported accounts', () async {
      final actors = List.generate(blueskyMaxAccountsPerLoad + 5, (i) => 'actor$i.test');
      final client = _Client()..read = (actor) async => [_post(actor)];
      final store = _store(client, actors);
      await store.ensureLoaded();
      expect(client.calls, hasLength(blueskyMaxAccountsPerLoad));
      expect(store.pending(actors), 5);
      await store.refresh(force: true);
      expect(client.calls.toSet(), containsAll(actors));
      expect(store.pending(actors), 0);
    });

    test('failed import batch cannot starve unread accounts', () async {
      final actors = List.generate(blueskyMaxAccountsPerLoad + 5, (i) => 'actor$i.test');
      final client = _Client()..read = (_) async => throw StateError('offline');
      final store = _store(client, actors);
      await store.ensureLoaded();
      await store.ensureLoaded();
      expect(client.calls.toSet(), containsAll(actors));
    });

    test('group subset read cannot replace Following or freshness', () async {
      final client = _Client()..read = (actor) async => [_post(actor)];
      final store = _store(client, ['alice.test']);
      await store.ensureLoaded();
      final before = store.state;
      final fetchedAt = store.fetchedAt;
      await store.postsFor(['bob.test'], forceRefresh: true);
      expect(store.state, same(before));
      expect(store.fetchedAt, fetchedAt);
    });

    test('older forced refresh cannot overwrite a newer result', () async {
      final first = Completer<List<BlueskyPost>>();
      final second = Completer<List<BlueskyPost>>();
      var reads = 0;
      final client = _Client()..read = (_) => ++reads == 1 ? first.future : second.future;
      final store = _store(client, ['alice.test']);
      final old = store.refresh(force: true);
      final current = store.refresh(force: true);
      second.complete([_post('current')]);
      await current;
      first.complete([_post('old')]);
      await old;
      expect(store.state.single.text, 'current');
      expect((await store.postsFor(['alice.test'])).single.text, 'current');
    });

    test('response from an old AppView cannot appear in the new source', () async {
      var server = 'https://one.example';
      final first = Completer<List<BlueskyPost>>();
      final client = _Client(server: () => server)
        ..read = (_) => server.contains('one.') ? first.future : Future.value([_post('two')]);
      final store = _store(client, ['alice.test']);
      final old = store.ensureLoaded();
      server = 'https://two.example';
      await store.ensureLoaded();
      first.complete([_post('one')]);
      await old;
      expect(store.state.single.text, 'two');
    });

    test('follow-set change discards the old pending request', () async {
      final first = Completer<List<BlueskyPost>>();
      final client = _Client()..read = (actor) => actor == 'alice.test' ? first.future : Future.value([_post('bob')]);
      final store = _store(client, ['alice.test']);
      final old = store.ensureLoaded();
      store.accounts.update([const BlueskyAccount(handle: 'bob.test', name: 'Bob')]);
      await store.ensureLoaded();
      first.complete([_post('alice')]);
      await old;
      expect(store.state.single.text, 'bob');
    });

    test('disposal ignores late reads and leaves no loading work', () async {
      final result = Completer<List<BlueskyPost>>();
      final client = _Client()..read = (_) => result.future;
      final store = _store(client, ['alice.test']);
      final pending = store.ensureLoaded();
      await store.destroy();
      result.complete([_post('late')]);
      await expectLater(pending, completes);
      expect(store.state, isEmpty);
    });
  });
}
