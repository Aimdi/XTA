import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/group/deck_groups.dart';
import 'package:xta/plugins/substack/substack_client.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/plugins/substack/substack_store.dart';

SubstackPublication _pub(String id, {String? baseUrl}) =>
    SubstackPublication(subdomain: id, baseUrl: baseUrl ?? 'https://$id.substack.com', name: id);

SubstackPost _post(SubstackPublication pub, String id, {int? reactions, String? date}) => SubstackPost(
  id: id,
  title: 'Article $id',
  slug: id,
  publicationBaseUrl: pub.baseUrl,
  publicationName: pub.name,
  reactionCount: reactions,
  postDate: date,
);

List<SubstackPost> _page(SubstackPublication pub, int offset) =>
    List.generate(substackFeedPageSize, (i) => _post(pub, '${offset + i}'));

SubstackNote _note(String id, {int? reactions}) => SubstackNote(id: id, body: id, reactionCount: reactions);

class _Publications extends SubstackPublicationsStore {
  _Publications(List<SubstackPublication> pubs, {BasePrefService? prefs})
    : super(prefs ?? PrefServiceCache(cache: {})) {
    update(pubs);
  }
}

class _Client extends SubstackClient {
  Future<List<SubstackPost>> Function(SubstackPublication pub, int offset)? posts;
  Future<SubstackNotesPage> Function(String? host, String? cursor)? notes;
  final postCalls = <({String baseUrl, int offset})>[];
  final noteCalls = <({String? host, String? cursor})>[];

  @override
  Future<List<SubstackPost>> fetchPosts(SubstackPublication publication, {int limit = 12, int offset = 0}) {
    postCalls.add((baseUrl: publication.baseUrl, offset: offset));
    return posts?.call(publication, offset) ?? Future.value(const []);
  }

  @override
  Future<SubstackNotesPage> fetchReaderNotes({String? host, String? cursor, int limit = 20}) {
    noteCalls.add((host: host, cursor: cursor));
    return notes?.call(host, cursor) ?? Future.value(const SubstackNotesPage());
  }
}

class _DelayedPrefs extends PrefServiceCache {
  Completer<void>? nextWrite;
  bool rejectNext = false;
  bool throwNext = false;
  int writes = 0;
  final firstWrite = Completer<void>();

  @override
  Future<bool> put<T>(String key, T val) async {
    writes++;
    if (!firstWrite.isCompleted) firstWrite.complete();
    final gate = nextWrite;
    nextWrite = null;
    await gate?.future;
    if (rejectNext) {
      rejectNext = false;
      return false;
    }
    if (throwNext) {
      throwNext = false;
      throw StateError('Storage unavailable');
    }
    return super.put(key, val);
  }
}

Future<void> _turn() => Future<void>.delayed(Duration.zero);

void main() {
  group('Substack home feed recovery', () {
    test('successful empty results are cached and failed results can retry immediately', () async {
      final client = _Client();
      final store = SubstackFeedStore(client, _Publications([_pub('alpha')]));
      addTearDown(store.destroy);
      final busyPaints = <bool>[];
      final stopObserving = store.observer(onState: (_) => busyPaints.add(store.refreshing));
      addTearDown(stopObserving);
      await store.refresh();
      expect(busyPaints.last, isFalse);
      await store.refresh();
      expect(client.postCalls, hasLength(1));
      expect(store.state.canLoadMore, isFalse);

      client.posts = (_, _) async => throw StateError('Offline');
      await store.refresh(force: true);
      expect(store.refreshError, isA<StateError>());
      expect(store.state.failedCount, 1);
      client.posts = (_, _) async => [];
      await store.refresh();
      expect(client.postCalls, hasLength(3));
      expect(store.refreshError, isNull);
      expect(store.state.failedCount, 0);
    });

    test('failed publication pages retain their offset while other publications progress', () async {
      final client = _Client();
      final pubs = [_pub('alpha'), _pub('beta')];
      var failure = true;
      client.posts = (pub, offset) async {
        if (offset == 0) return _page(pub, offset);
        if (pub.id == 'alpha' && failure) throw StateError('Offline');
        return [_post(pub, '$offset')];
      };
      final store = SubstackFeedStore(client, _Publications(pubs));
      addTearDown(store.destroy);
      await store.refresh();
      await store.loadMore();
      expect(store.allPosts, hasLength(17));
      expect(store.loadMoreError, isA<StateError>());
      expect(store.state.canLoadMore, isTrue);
      failure = false;
      await store.retryLoadMore();
      expect(client.postCalls.where((call) => call.baseUrl == pubs[0].baseUrl).map((call) => call.offset), [0, 8, 8]);
      expect(client.postCalls.where((call) => call.baseUrl == pubs[1].baseUrl).map((call) => call.offset), [0, 8]);
      expect(store.allPosts, hasLength(18));
      expect(store.loadMoreError, isNull);
      expect(store.state.canLoadMore, isFalse);
    });

    test('refresh failure keeps readable rows and a page retry clears the refresh error', () async {
      final pub = _pub('alpha');
      final client = _Client()..posts = (_, _) async => [_post(pub, 'cached')];
      final store = SubstackFeedStore(client, _Publications([pub]));
      addTearDown(store.destroy);
      await store.refresh();
      client.posts = (_, _) async => throw StateError('Offline');
      await store.refresh(force: true);
      expect(store.allPosts.single.id, 'cached');
      expect(store.refreshError, isNotNull);
      client.posts = (_, _) async => [_post(pub, 'fresh')];
      await store.retryLoadMore();
      expect(client.postCalls.last.offset, 0);
      expect(store.allPosts.single.id, 'fresh');
      expect(store.refreshError, isNull);
    });

    test('publication requests obey concurrency and pass budgets without starving untouched publications', () async {
      final pubs = List.generate(27, (i) => _pub('pub$i'));
      final gate = Completer<void>();
      var active = 0;
      var maximum = 0;
      final client = _Client()
        ..posts = (pub, offset) async {
          active++;
          if (active > maximum) maximum = active;
          await gate.future;
          active--;
          if (int.parse(pub.id.substring(3)) < 24) throw StateError('Unavailable');
          return _page(pub, offset);
        };
      final store = SubstackFeedStore(client, _Publications(pubs));
      addTearDown(store.destroy);
      final refresh = store.refresh();
      await _turn();
      expect(active, substackPublicationConcurrency);
      gate.complete();
      await refresh;
      expect(maximum, substackPublicationConcurrency);
      expect(client.postCalls, hasLength(substackPublicationsPerBatch));
      expect(store.pendingCount, 27);
      await store.loadMore();
      expect(client.postCalls.map((call) => call.baseUrl).toSet(), hasLength(27));
      expect(store.allPosts, hasLength(3 * substackFeedPageSize));
      expect(store.pendingCount, 24);
      await store.loadMore();
      expect(client.postCalls.where((call) => call.offset == 8), hasLength(3));
    });

    test('a duplicate paging request is coalesced and refresh supersedes the old page', () async {
      final pub = _pub('alpha');
      final delayed = Completer<List<SubstackPost>>();
      final client = _Client()..posts = (pub, offset) async => offset == 0 ? _page(pub, 0) : delayed.future;
      final store = SubstackFeedStore(client, _Publications([pub]));
      addTearDown(store.destroy);
      await store.refresh();
      final first = store.loadMore();
      await store.loadMore();
      expect(client.postCalls.where((call) => call.offset == 8), hasLength(1));
      expect(store.loadingMore, isTrue);
      client.posts = (_, _) async => [_post(pub, 'new')];
      await store.refresh(force: true);
      delayed.complete([_post(pub, 'stale')]);
      await first;
      expect(store.allPosts.map((post) => post.id), ['new']);
      expect(store.loadingMore, isFalse);
      expect(store.refreshing, isFalse);
    });

    test('changing a publication host rejects old results even when its id is unchanged', () async {
      final old = _pub('alpha');
      final current = _pub('alpha', baseUrl: 'https://custom.example');
      final pubs = _Publications([old]);
      final delayed = Completer<List<SubstackPost>>();
      final client = _Client()..posts = (_, _) => delayed.future;
      final store = SubstackFeedStore(client, pubs);
      addTearDown(store.destroy);
      final first = store.refresh();
      pubs.update([current]);
      client.posts = (_, _) async => [_post(current, 'new')];
      await store.refresh();
      delayed.complete([_post(old, 'old')]);
      await first;
      expect(store.allPosts.single.publicationBaseUrl, current.baseUrl);
      expect(store.allPosts.single.id, 'new');
    });

    test('source identity normalizes host spelling and trailing slashes while preserving paths', () async {
      final original = _pub('alpha', baseUrl: 'https://EXAMPLE.com/journal/');
      final same = _pub('alpha', baseUrl: 'https://example.com/journal');
      final changed = _pub('alpha', baseUrl: 'https://example.com/other');
      final pubs = _Publications([original]);
      final client = _Client()..posts = (pub, _) async => [_post(pub, 'article')];
      final store = SubstackFeedStore(client, pubs);
      addTearDown(store.destroy);
      await store.refresh();
      pubs.update([same]);
      await store.refresh();
      expect(client.postCalls, hasLength(1));
      expect(substackFeedPostKey(_post(original, 'article')), substackFeedPostKey(_post(same, 'article')));
      pubs.update([changed]);
      await store.refresh();
      expect(client.postCalls, hasLength(2));
      expect(store.allPosts.single.publicationBaseUrl, changed.baseUrl);
    });

    test('removing a followed publication removes only its cached rows', () async {
      final a = _pub('alpha');
      final b = _pub('beta');
      final pubs = _Publications([a, b]);
      final client = _Client()..posts = (pub, _) async => [_post(pub, 'shared-id')];
      final store = SubstackFeedStore(client, pubs);
      addTearDown(store.destroy);
      await store.refresh();
      expect(store.allPosts, hasLength(2));
      pubs.update([b]);
      await store.refresh();
      expect(store.allPosts.single.publicationBaseUrl, b.baseUrl);
    });

    test('overlapping pages update metadata and cyclic duplicate pages stop', () async {
      final pub = _pub('alpha');
      final client = _Client()
        ..posts = (pub, offset) async =>
            List.generate(substackFeedPageSize, (i) => _post(pub, '$i', reactions: offset == 0 ? 1 : 10));
      final store = SubstackFeedStore(client, _Publications([pub]));
      addTearDown(store.destroy);
      await store.refresh();
      await store.loadMore();
      expect(store.allPosts, hasLength(8));
      expect(store.allPosts.every((post) => post.reactionCount == 10), isTrue);
      expect(store.state.canLoadMore, isFalse);
    });

    test('read changes repaint all articles and unread filtering stays local', () async {
      final pub = _pub('alpha');
      final client = _Client()..posts = (_, _) async => [_post(pub, 'a'), _post(pub, 'b')];
      final store = SubstackFeedStore(client, _Publications([pub]));
      addTearDown(store.destroy);
      await store.refresh();
      var paints = 0;
      final dispose = store.observer(onState: (_) => paints++);
      addTearDown(dispose);
      store.syncReadIds({'a'});
      expect(paints, 1);
      expect(store.state.posts, hasLength(2));
      store.setFilter(SubstackFeedFilter.unread, {'a'});
      expect(store.state.posts.single.id, 'b');
      store.syncReadIds({});
      expect(store.state.posts, hasLength(2));
      expect(client.postCalls, hasLength(1));
    });

    test('disposed feeds ignore pending requests', () async {
      final pub = _pub('alpha');
      final delayed = Completer<List<SubstackPost>>();
      final client = _Client()..posts = (_, _) => delayed.future;
      final store = SubstackFeedStore(client, _Publications([pub]));
      final pending = store.refresh();
      await store.destroy();
      delayed.complete([_post(pub, 'late')]);
      await pending;
      expect(store.allPosts, isEmpty);
    });
  });

  group('Substack Notes recovery', () {
    test('pages keep the issuing host and refresh rotates discovery hosts', () async {
      final client = _Client()
        ..notes = (_, cursor) async =>
            SubstackNotesPage(notes: [_note(cursor ?? 'first')], nextCursor: cursor == null ? 'next' : null);
      final store = SubstackNotesStore(client, _Publications([_pub('alpha'), _pub('beta')]));
      addTearDown(store.destroy);
      await store.refresh();
      await store.loadMore();
      await store.refresh(force: true);
      expect(client.noteCalls, [
        (host: 'alpha.substack.com', cursor: null),
        (host: 'alpha.substack.com', cursor: 'next'),
        (host: 'beta.substack.com', cursor: null),
      ]);
    });

    test('failed pages keep visible notes and retry the same cursor', () async {
      var fail = true;
      final client = _Client()
        ..notes = (_, cursor) async {
          if (cursor != null && fail) throw StateError('Offline');
          return SubstackNotesPage(notes: [_note(cursor ?? 'first')], nextCursor: cursor == null ? 'next' : null);
        };
      final store = SubstackNotesStore(client, _Publications([_pub('alpha')]));
      addTearDown(store.destroy);
      await store.refresh();
      await store.loadMore();
      expect(store.state.notes.single.id, 'first');
      expect(store.state.nextCursor, 'next');
      expect(store.loadMoreError, isNotNull);
      fail = false;
      await store.retryLoadMore();
      expect(client.noteCalls.map((call) => call.cursor), [null, 'next', 'next']);
      expect(store.state.notes.map((note) => note.id), ['first', 'next']);
      expect(store.loadMoreError, isNull);
    });

    test('cyclic cursors stop and overlapping notes replace counts without duplicated rows', () async {
      final client = _Client()
        ..notes = (_, cursor) async => SubstackNotesPage(
          notes: [
            _note('same', reactions: cursor == null ? 1 : 5),
            if (cursor != null) _note(cursor),
          ],
          nextCursor: cursor == null
              ? 'a'
              : cursor == 'a'
              ? 'b'
              : 'a',
        );
      final store = SubstackNotesStore(client, _Publications([_pub('alpha')]));
      addTearDown(store.destroy);
      await store.refresh();
      await store.loadMore();
      await store.loadMore();
      await store.loadMore();
      expect(client.noteCalls, hasLength(3));
      expect(store.state.nextCursor, isNull);
      expect(store.state.notes.map((note) => note.id), ['same', 'a', 'b']);
      expect(store.state.notes.first.reactionCount, 5);
    });

    test('successful empty results are fresh and a failed refresh remains retryable', () async {
      final client = _Client();
      final store = SubstackNotesStore(client, _Publications([]));
      addTearDown(store.destroy);
      final busyPaints = <bool>[];
      final stopObserving = store.observer(onState: (_) => busyPaints.add(store.refreshing));
      addTearDown(stopObserving);
      await store.refresh();
      expect(busyPaints.last, isFalse);
      await store.refresh();
      expect(client.noteCalls, hasLength(1));
      client.notes = (_, _) async => throw StateError('Offline');
      await store.refresh(force: true);
      expect(store.refreshError, isNotNull);
      client.notes = (_, _) async => SubstackNotesPage(notes: [_note('recovered')]);
      await store.refresh();
      expect(client.noteCalls, hasLength(3));
      expect(store.refreshError, isNull);
      expect(store.state.notes.single.id, 'recovered');
    });

    test('source changes supersede pending discovery and disposal ignores late responses', () async {
      final pubs = _Publications([_pub('alpha')]);
      final delayed = Completer<SubstackNotesPage>();
      final client = _Client()..notes = (_, _) => delayed.future;
      final store = SubstackNotesStore(client, pubs);
      final pending = store.refresh();
      pubs.update([_pub('beta')]);
      client.notes = (_, _) async => SubstackNotesPage(notes: [_note('current')]);
      await store.refresh();
      delayed.complete(SubstackNotesPage(notes: [_note('stale')]));
      await pending;
      expect(store.state.notes.single.id, 'current');
      final late = Completer<SubstackNotesPage>();
      client.notes = (_, _) => late.future;
      final after = store.refresh(force: true);
      await store.destroy();
      late.complete(SubstackNotesPage(notes: [_note('closed')]));
      await after;
      expect(store.state.notes.single.id, 'current');
    });

    test('refresh keeps notes while offline and replaces a pending older page', () async {
      final client = _Client()..notes = (_, _) async => SubstackNotesPage(notes: [_note('cached')], nextCursor: 'a');
      final store = SubstackNotesStore(client, _Publications([_pub('alpha')]));
      addTearDown(store.destroy);
      await store.refresh();
      client.notes = (_, _) async => throw StateError('Offline');
      await store.refresh(force: true);
      expect(store.state.notes.single.id, 'cached');
      expect(store.refreshError, isNotNull);
      final delayed = Completer<SubstackNotesPage>();
      client.notes = (_, _) => delayed.future;
      final older = store.loadMore();
      client.notes = (_, _) async => SubstackNotesPage(notes: [_note('fresh')]);
      await store.refresh(force: true);
      delayed.complete(SubstackNotesPage(notes: [_note('stale')]));
      await older;
      expect(store.state.notes.single.id, 'fresh');
    });
  });

  group('Substack local preference ordering', () {
    test('rapid read changes preserve ids and explicit unread survives reload', () async {
      final gate = Completer<void>();
      final prefs = _DelayedPrefs()..nextWrite = gate;
      final store = SubstackReadStore(prefs);
      addTearDown(store.destroy);
      final a = store.markRead('a');
      final b = store.markRead('b');
      final unread = store.markUnread('a');
      await prefs.firstWrite.future;
      expect(prefs.writes, 1);
      gate.complete();
      await Future.wait([a, b, unread]);
      expect(store.state, {'b'});
      final restored = SubstackReadStore(prefs);
      addTearDown(restored.destroy);
      await restored.load();
      expect(restored.state, {'b'});
    });

    test('read ids are deduplicated before history capacity is applied', () async {
      final store = SubstackReadStore(PrefServiceCache(cache: {}));
      addTearDown(store.destroy);
      await store.markAllRead([...List.filled(500, 'a'), ...List.generate(450, (i) => '$i')]);
      expect(store.state, hasLength(substackReadIdsCap));
      expect(store.state, containsAll(['a', '398']));
      expect(store.state, isNot(contains('399')));
      await store.markAllUnread(['a', '398']);
      expect(store.state, hasLength(substackReadIdsCap - 2));
    });

    test('rapid likes use the latest committed state and repeated toggles cancel', () async {
      final gate = Completer<void>();
      final prefs = _DelayedPrefs()..nextWrite = gate;
      final store = SubstackLikesStore(prefs);
      addTearDown(store.destroy);
      final a = _post(_pub('alpha'), 'a');
      final b = _post(_pub('alpha'), 'b');
      final changes = [store.toggle(a), store.toggle(b), store.toggle(a)];
      await prefs.firstWrite.future;
      expect(prefs.writes, 1);
      gate.complete();
      await Future.wait(changes);
      expect(store.state.map((post) => post.id), ['b']);
      final restored = SubstackLikesStore(prefs);
      addTearDown(restored.destroy);
      await restored.load();
      expect(restored.isLiked('b'), isTrue);
      expect(restored.isLiked('a'), isFalse);
    });

    test('rapid saved changes persist before disposal and later actions do nothing', () async {
      final gate = Completer<void>();
      final prefs = _DelayedPrefs()..nextWrite = gate;
      final store = SubstackSavedStore(prefs);
      final changes = [store.toggle(_post(_pub('alpha'), 'a')), store.toggle(_post(_pub('alpha'), 'b'))];
      final disposing = store.destroy();
      gate.complete();
      await Future.wait([...changes, disposing]);
      await store.toggle(_post(_pub('alpha'), 'c'));
      final restored = SubstackSavedStore(prefs);
      addTearDown(restored.destroy);
      await restored.load();
      expect(restored.state.map((post) => post.id), ['b', 'a']);
    });

    for (final throws in [false, true]) {
      test('a ${throws ? 'thrown' : 'rejected'} preference write keeps state and allows the next action', () async {
        final prefs = _DelayedPrefs()
          ..rejectNext = !throws
          ..throwNext = throws;
        final store = SubstackSavedStore(prefs);
        addTearDown(store.destroy);
        await store.toggle(_post(_pub('alpha'), 'failed'));
        expect(store.state, isEmpty);
        expect(store.error, isA<StateError>());
        await store.toggle(_post(_pub('alpha'), 'kept'));
        expect(store.state.single.id, 'kept');
      });
    }

    test('rapid pin changes preserve both publications and repeated toggles unpin', () async {
      final gate = Completer<void>();
      final prefs = _DelayedPrefs()..nextWrite = gate;
      final store = _Publications([_pub('alpha'), _pub('beta')], prefs: prefs);
      addTearDown(store.destroy);
      final changes = [store.togglePinned('alpha'), store.togglePinned('beta'), store.togglePinned('alpha')];
      await prefs.firstWrite.future;
      expect(prefs.writes, 1);
      gate.complete();
      await Future.wait(changes);
      expect(parseDeckGroupIds(prefs.get(optionPluginSubstackPinnedPublications)), ['beta']);
      expect(store.state.first.id, 'beta');
      expect(store.isPinned('alpha'), isFalse);
      await store.togglePinned('beta');
      expect(store.state.map((pub) => pub.id), ['alpha', 'beta']);
    });
  });
}
