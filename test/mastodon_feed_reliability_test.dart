import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/mastodon/mastodon_client.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_store.dart';
import 'package:xta/plugins/mastodon/mastodon_timeline_controls.dart';

MastodonPost _post(
  String id, {
  String host = 'origin.example',
  String? url,
  String? timelineId,
  bool boosted = false,
  DateTime? timelineAt,
  DateTime? publishedAt,
}) => MastodonPost(
  id: id,
  acct: 'reader@$host',
  authorName: 'Reader',
  text: id,
  url: url ?? 'https://$host/@reader/$id',
  timelineId: timelineId,
  boosted: boosted,
  timelineAt: timelineAt,
  publishedAt: publishedAt ?? DateTime.utc(2026, 9, 1),
);

List<MastodonPost> _page(String prefix) => List.generate(30, (index) => _post('$prefix-$index'));

class _PublicRead {
  final String instance;
  final String? cursor;
  final response = Completer<List<MastodonPost>>();

  _PublicRead(this.instance, this.cursor);
}

class _Client extends MastodonClient {
  final publicReads = <_PublicRead>[];
  final followingReads = <Completer<List<MastodonPost>>>[];
  final trendingReads = <Completer<List<MastodonPost>>>[];
  Future<List<MastodonTrendingTag>> Function()? readTags;

  @override
  Future<List<MastodonPost>> getPublicTimeline(String instance, {bool local = false, int limit = 30, String? maxId}) {
    final read = _PublicRead(instance, maxId);
    publicReads.add(read);
    return read.response.future;
  }

  @override
  Future<List<MastodonPost>> fetchAccountAnywhere(List<String> instances, String acct, {int limit = 20}) {
    final read = Completer<List<MastodonPost>>();
    followingReads.add(read);
    return read.future;
  }

  @override
  Future<List<MastodonTrendingTag>> getTrendingTagsAnywhere(List<String> instances, {int limit = 20}) async =>
      readTags == null ? const [] : await readTags!();

  @override
  Future<List<MastodonPost>> getTrendingStatusesAnywhere(List<String> instances, {int limit = 20}) {
    final read = Completer<List<MastodonPost>>();
    trendingReads.add(read);
    return read.future;
  }
}

void main() {
  late _Client client;
  late PrefServiceCache prefs;
  late MastodonAccountsStore accounts;
  late MastodonPublicFeedStore public;
  late MastodonFeedStore following;
  late MastodonExploreStore explore;

  setUp(() {
    client = _Client();
    prefs = PrefServiceCache(
      defaults: {optionPluginMastodonInstance: 'https://first.example', optionPluginMastodonInstances: '[]'},
    );
    accounts = MastodonAccountsStore()..update([const MastodonAccount(acct: 'reader@origin.example', name: 'Reader')]);
    public = MastodonLocalStore(client, prefs);
    following = MastodonFeedStore(client, prefs, accounts);
    explore = MastodonExploreStore(client, prefs);
  });

  tearDown(() async {
    await public.destroy();
    await following.destroy();
    await explore.destroy();
    await accounts.destroy();
    client.httpClient.close();
  });

  Future<void> seedPublic() async {
    final refresh = public.refresh();
    client.publicReads.last.response.complete(_page('seed'));
    await refresh;
  }

  group('canonical feed deduplication', () {
    test('collapses remote aliases and keeps same ids from separate origins', () {
      final original = _post('local-id', url: 'https://origin.example/@reader/status');
      final remote = _post('remote-id', url: 'https://ORIGIN.example/@reader/status/');
      final different = _post('local-id', host: 'another.example');
      expect(appendUniqueMastodonPosts([original, original], [remote, different]), [original, different]);
    });

    for (final boostFirst in [true, false]) {
      test('prefers originals at the first public position, boost first: $boostFirst', () {
        final original = _post('shared');
        final boost = _post('shared', boosted: true, timelineAt: DateTime.utc(2026, 9, 3));
        final other = _post('other');
        final first = boostFirst ? boost : original;
        final second = boostFirst ? original : boost;
        expect(appendUniqueMastodonPosts([first, other], [second]), [original, other]);
      });
    }
  });

  group('public feed', () {
    test('newest refresh owns both timeline and source', () async {
      final older = public.refresh();
      await prefs.set(optionPluginMastodonInstance, 'https://second.example');
      final newer = public.refresh();
      client.publicReads[1].response.complete(_page('new'));
      await newer;
      client.publicReads[0].response.complete([_post('old')]);
      await older;
      expect(public.state.first.id, 'new-0');
      expect(public.instance, 'https://second.example');
      expect(public.canLoadMore, isTrue);
    });

    test('forget invalidates a pending refresh and resets source', () async {
      final pending = public.refresh();
      public.forget();
      client.publicReads.single.response.complete(_page('forgotten'));
      await pending;
      expect(public.state, isEmpty);
      expect(public.instance, isNull);
      expect(public.isLoading, isFalse);
      expect(public.loadingMore, isFalse);
    });

    test('destroy ignores a pending read and prevents new ones', () async {
      final pending = public.refresh();
      await public.destroy();
      client.publicReads.single.response.completeError(StateError('late failure'));
      await pending;
      await public.refresh();
      expect(client.publicReads, hasLength(1));
      expect(public.refreshError, isNull);
    });

    test('uses raw timeline cursor after boost deduplication', () async {
      final refresh = public.refresh();
      client.publicReads.single.response.complete(
        List.generate(30, (index) => _post('original', timelineId: 'boost-$index')),
      );
      await refresh;
      expect(public.state, hasLength(1));
      final paging = public.loadMore();
      expect(client.publicReads.last.cursor, 'boost-29');
      client.publicReads.last.response.complete([]);
      await paging;
      expect(public.canLoadMore, isFalse);
    });

    test('paging failures preserve posts and notify a retryable state', () async {
      await seedPublic();
      final loadingEvents = <bool>[];
      public.observer(onState: (_) => loadingEvents.add(public.loadingMore));
      final first = public.loadMore();
      final failure = StateError('temporary outage');
      client.publicReads.last.response.completeError(failure);
      await first;
      expect(public.state, hasLength(30));
      expect(public.error, isNull);
      expect(public.loadMoreError, same(failure));
      expect(public.canLoadMore, isFalse);
      expect(loadingEvents, [true, false]);

      final retry = public.retryLoadMore();
      expect(public.loadMoreError, isNull);
      expect(client.publicReads.last.cursor, 'seed-29');
      client.publicReads.last.response.complete([_post('older')]);
      await retry;
      expect(public.state.last.id, 'older');
      expect(public.loadMoreError, isNull);
      expect(public.loadingMore, isFalse);
    });

    test('an old page cannot append to a refreshed feed or clear newer loading', () async {
      await seedPublic();
      final stalePage = public.loadMore();
      final oldRead = client.publicReads.last;
      final refresh = public.refresh();
      client.publicReads.last.response.complete(_page('fresh'));
      await refresh;
      final currentPage = public.loadMore();
      final currentRead = client.publicReads.last;
      oldRead.response.complete([_post('stale')]);
      await stalePage;
      expect(public.state.any((post) => post.id == 'stale'), isFalse);
      expect(public.loadingMore, isTrue);
      currentRead.response.complete([_post('older')]);
      await currentPage;
      expect(public.state.last.id, 'older');
    });

    test('failed refresh retains original source and cursor', () async {
      await seedPublic();
      await prefs.set(optionPluginMastodonInstance, 'https://second.example');
      final refresh = public.refresh();
      final failure = StateError('offline');
      client.publicReads.last.response.completeError(failure);
      await refresh;
      expect(public.refreshError, same(failure));
      expect(public.instance, 'https://first.example');
      expect(public.canLoadMore, isTrue);
      final page = public.loadMore();
      expect(client.publicReads.last.instance, 'https://first.example');
      expect(client.publicReads.last.cursor, 'seed-29');
      client.publicReads.last.response.complete([]);
      await page;
    });

    test('repeated cursor stops an unbounded duplicate paging loop', () async {
      await seedPublic();
      final page = public.loadMore();
      client.publicReads.last.response.complete(_page('seed'));
      await page;
      expect(public.state, hasLength(30));
      expect(public.canLoadMore, isFalse);
      await public.loadMore();
      expect(client.publicReads, hasLength(2));
    });

    test('forget also invalidates a pending page', () async {
      await seedPublic();
      final page = public.loadMore();
      public.forget();
      client.publicReads.last.response.complete([_post('forgotten')]);
      await page;
      expect(public.state, isEmpty);
      expect(public.loadingMore, isFalse);
      expect(public.loadMoreError, isNull);
    });
  });

  group('following feed', () {
    for (final boostFirst in [true, false]) {
      test('hide boosts keeps original followed posts in date order, boost arrives first: $boostFirst', () async {
        final original = _post('shared');
        final boost = _post('shared', boosted: true, timelineAt: DateTime.utc(2026, 9, 3));
        final other = _post('other', publishedAt: DateTime.utc(2026, 9, 2));
        accounts.update([...accounts.state, const MastodonAccount(acct: 'booster@origin.example', name: 'Booster')]);
        final refresh = following.refresh();
        client.followingReads[boostFirst ? 1 : 0].complete(boostFirst ? [boost] : [original, other]);
        await Future<void>.delayed(Duration.zero);
        client.followingReads[boostFirst ? 0 : 1].complete(boostFirst ? [original, other] : [boost]);
        await refresh;
        final visible = filterMastodonTimeline(following.state, const MastodonTimelineOptions(hideBoosts: true));
        expect(following.state, [other, original]);
        expect(visible, [other, original]);
      });
    }

    test('new refresh wins and an older request cannot contaminate its cache', () async {
      final older = following.refresh();
      final newer = following.refresh(force: true);
      client.followingReads[1].complete([_post('new')]);
      await newer;
      client.followingReads[0].complete([_post('old')]);
      await older;
      await following.refresh();
      expect(following.state.single.id, 'new');
      expect(client.followingReads, hasLength(2));
    });

    test('mixed Home and group reads leave dedicated Following state intact', () async {
      following.update([_post('following')]);
      final partials = <List<MastodonPost>>[];
      final group = following.postsFor(['another@other.example'], onPartial: partials.add);
      client.followingReads.single.complete([_post('group')]);
      expect((await group).single.id, 'group');
      expect(partials.single.single.id, 'group');
      expect(following.state.single.id, 'following');
    });

    test('forget discards pending partials and prevents cache resurrection', () async {
      final old = following.refresh();
      following.forget();
      client.followingReads.single.complete([_post('old')]);
      await old;
      expect(following.state, isEmpty);
      final fresh = following.refresh();
      expect(client.followingReads, hasLength(2));
      client.followingReads.last.complete([_post('new')]);
      await fresh;
      expect(following.state.single.id, 'new');
    });

    test('forgotten mixed-feed reads do not deliver stale results', () async {
      final partials = <List<MastodonPost>>[];
      final group = following.postsFor(['reader@origin.example'], onPartial: partials.add);
      following.forget();
      client.followingReads.single.complete([_post('old')]);
      expect(await group, isEmpty);
      expect(partials, isEmpty);
    });

    test('instance change invalidates an older mixed-feed request', () async {
      final older = following.postsFor(['reader@origin.example']);
      await prefs.set(optionPluginMastodonInstance, 'https://second.example');
      final newer = following.refresh();
      client.followingReads.last.complete([_post('new')]);
      await newer;
      client.followingReads.first.complete([_post('old')]);
      expect(await older, isEmpty);
      expect(following.state.single.id, 'new');
    });

    test('failed forced refresh keeps all prior posts, including cached partials', () async {
      accounts.update([...accounts.state, const MastodonAccount(acct: 'another@origin.example', name: 'Another')]);
      final initial = following.refresh();
      client.followingReads[0].complete([_post('first')]);
      client.followingReads[1].complete([_post('second')]);
      await initial;
      final refresh = following.refresh(force: true);
      client.followingReads[2].completeError(StateError('first offline'));
      client.followingReads[3].completeError(StateError('second offline'));
      await refresh;
      expect(following.state.map((post) => post.id), containsAll(['first', 'second']));
      expect(following.refreshError, isA<StateError>());
      expect(following.error, isNull);
    });

    test('destroy ignores pending following partials', () async {
      final pending = following.refresh();
      await following.destroy();
      client.followingReads.single.complete([_post('old')]);
      await pending;
      expect(following.state, isEmpty);
    });
  });

  group('Explore', () {
    test('newer refresh wins even when the old request finishes last', () async {
      final older = explore.refresh();
      final newer = explore.refresh();
      client.trendingReads[1].complete([_post('new')]);
      await newer;
      client.trendingReads[0].complete([_post('old')]);
      await older;
      expect(explore.state.posts.single.id, 'new');
    });

    test('forget and destroy invalidate pending Explore responses', () async {
      final forgotten = explore.refresh();
      explore.forget();
      client.trendingReads.last.complete([_post('forgotten')]);
      await forgotten;
      expect(explore.state.posts, isEmpty);
      final destroyed = explore.refresh();
      await explore.destroy();
      client.trendingReads.last.complete([_post('destroyed')]);
      await destroyed;
      expect(explore.state.posts, isEmpty);
    });

    test('failed refresh preserves posts and reports a recoverable error', () async {
      explore.update(MastodonExplorePage(posts: [_post('readable')]));
      final refresh = explore.refresh();
      client.trendingReads.single.completeError(StateError('offline'));
      await refresh;
      expect(explore.state.posts.single.id, 'readable');
      expect(explore.refreshError, isA<StateError>());
      expect(explore.error, isNull);
    });

    test('post errors are handled immediately while tags are still pending', () async {
      final tags = Completer<List<MastodonTrendingTag>>();
      client.readTags = () => tags.future;
      final refresh = explore.refresh();
      client.trendingReads.single.completeError(StateError('post failure'));
      await refresh;
      expect(explore.error, isA<StateError>());
      tags.complete([]);
    });
  });
}
