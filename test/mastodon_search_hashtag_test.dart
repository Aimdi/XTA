import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/mastodon/mastodon_client.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_search_sheet.dart';
import 'package:xta/plugins/mastodon/mastodon_search_store.dart';
import 'package:xta/plugins/mastodon/mastodon_tag_store.dart';

import 'support/mastodon_harness.dart';

MastodonPost _post(String id, {String? timelineId}) => MastodonPost(
  id: id,
  timelineId: timelineId,
  acct: 'reader@studio.example',
  authorName: 'Reader',
  text: 'Post $id',
  url: 'https://studio.example/@reader/$id',
);

final _failure = MastodonException(MastodonErrorKind.network, 'offline');

class _Client extends MastodonFixtureClient {
  Future<List<MastodonPost>> Function(String? cursor) readTag = (_) async => [];
  Future<MastodonPost> Function(String instance, String id) readStatus = (_, id) async => _post(id);
  final tagCursors = <String?>[];
  final statusReads = <({String instance, String id})>[];
  int lookups = 0;

  @override
  Future<List<MastodonPost>> getTagTimeline(String instance, String tag, {int limit = 30, String? maxId}) {
    tagCursors.add(maxId);
    return readTag(maxId);
  }

  @override
  Future<MastodonPost> getStatus(String instance, String id) {
    statusReads.add((instance: instance, id: id));
    return readStatus(instance, id);
  }

  @override
  Future<MastodonProfile> lookupAnywhere(List<String> instances, String acct) {
    lookups++;
    return super.lookupAnywhere(instances, acct);
  }
}

void main() {
  test('post URL recognition accepts exact Mastodon paths and rejects ambiguous URLs', () {
    expect(mastodonSearchStatusTarget(' https://social.example:8443/@Ada/123/?source=reader#post '), (
      instance: 'https://social.example:8443',
      id: '123',
    ));
    expect(mastodonSearchStatusTarget('https://social.example/users/ada/statuses/456'), (
      instance: 'https://social.example',
      id: '456',
    ));
    for (final input in [
      '',
      'https://social.example',
      'https://social.example/@ada',
      'https://social.example/@ada/123/embed',
      'https://social.example/articles/123',
      'https://social.example/@/123',
      'https://social.example/@ada/not-a-status',
      'ftp://social.example/@ada/123',
      'https://password@social.example/@ada/123',
    ]) {
      expect(mastodonSearchStatusTarget(input), isNull, reason: input);
    }
  });

  test('post search reads the origin directly before account lookup or guest search', () async {
    final client = _Client();
    final store = MastodonSearchStore(client, ['https://configured.example']);
    await store.search('https://studio.example/@reader/123');
    expect(client.statusReads, [(instance: 'https://studio.example', id: '123')]);
    expect(client.lookups, 0);
    expect(client.searches, 0);
    expect(store.state.results.posts.single.id, '123');
    expect(store.state.tab, 1);
    await store.search('reader@studio.example');
    expect(client.lookups, 1);
    await store.search('photography');
    expect(client.searches, 1);
    await store.destroy();
    client.httpClient.close();
  });

  test('direct post failures remain retryable and an old URL cannot replace a newer search', () async {
    final client = _Client();
    final store = MastodonSearchStore(client, ['https://studio.example']);
    client.readStatus = (_, _) async => throw _failure;
    await store.search('https://studio.example/@reader/123');
    expect(store.state.error, same(_failure));
    final delayed = Completer<MastodonPost>();
    client.readStatus = (_, _) => delayed.future;
    final old = store.search('https://studio.example/@reader/123');
    await store.search('photography');
    delayed.complete(_post('123'));
    await old;
    expect(store.state.query, 'photography');
    expect(store.state.results.accounts.single, same(sampleProfile));
    client.readStatus = (_, id) async => _post(id);
    await store.search('https://studio.example/@reader/123');
    expect(store.state.error, isNull);
    expect(store.state.results.posts.single.id, '123');
    await store.destroy();
    client.httpClient.close();
  });

  test('closed search routes neither update from direct reads nor start new reads', () async {
    final client = _Client();
    final delayed = Completer<MastodonPost>();
    client.readStatus = (_, _) => delayed.future;
    final store = MastodonSearchStore(client, ['https://studio.example']);
    final pending = store.search('https://studio.example/@reader/123');
    final before = store.state;
    await store.destroy();
    delayed.complete(_post('123'));
    await pending;
    await store.search('another query');
    store.select(2);
    expect(store.state, same(before));
    expect(client.searches, 0);
    client.httpClient.close();
  });

  test('hashtag refresh keeps loaded posts and provides a recoverable error', () async {
    final client = _Client()..readTag = (_) async => [_post('123')];
    final store = MastodonTagStore(client, ['https://studio.example'], 'art');
    await store.refresh();
    client.readTag = (_) async => throw _failure;
    await store.refresh();
    expect(store.state.posts.single.id, '123');
    expect(store.state.error, same(_failure));
    expect(store.state.loading, isFalse);
    client.readTag = (_) async => [_post('456')];
    await store.refresh();
    expect(store.state.posts.single.id, '456');
    expect(store.state.error, isNull);
    await store.destroy();
    client.httpClient.close();
  });

  test('hashtag pagination uses wrapper cursors, deduplicates URLs and stops repeated pages', () async {
    final page = List.generate(30, (i) => _post('$i', timelineId: 'wrapper-$i'));
    final client = _Client()..readTag = (_) async => page;
    final store = MastodonTagStore(client, ['https://studio.example'], 'art');
    await store.refresh();
    expect(store.state.hasMore, isTrue);
    await store.loadMore();
    expect(client.tagCursors, [null, 'wrapper-29']);
    expect(store.state.posts.length, 30);
    expect(store.state.hasMore, isFalse);
    await store.loadMore();
    expect(client.tagCursors.length, 2);
    await store.destroy();
    client.httpClient.close();
  });

  test('hashtag pagination can retry after failure without losing the current page', () async {
    final client = _Client()..readTag = (_) async => List.generate(30, (i) => _post('$i'));
    final store = MastodonTagStore(client, ['https://studio.example'], 'art');
    await store.refresh();
    client.readTag = (_) async => throw _failure;
    await store.loadMore();
    expect(store.state.posts.length, 30);
    expect(store.state.moreError, same(_failure));
    expect(store.state.canLoadMore, isTrue);
    client.readTag = (_) async => [_post('new')];
    await store.loadMore();
    expect(store.state.posts.last.id, 'new');
    expect(store.state.moreError, isNull);
    expect(store.state.hasMore, isFalse);
    await store.destroy();
    client.httpClient.close();
  });

  test('refresh invalidates older hashtag pagination and overlapping refreshes', () async {
    final client = _Client()..readTag = (_) async => List.generate(30, (i) => _post('$i'));
    final store = MastodonTagStore(client, ['https://studio.example'], 'art');
    await store.refresh();
    final page = Completer<List<MastodonPost>>();
    client.readTag = (_) => page.future;
    final paging = store.loadMore();
    final refresh = Completer<List<MastodonPost>>();
    client.readTag = (_) => refresh.future;
    final earlier = store.refresh();
    client.readTag = (_) async => [_post('latest')];
    await store.refresh();
    page.complete([_post('stale-page')]);
    refresh.complete([_post('stale-refresh')]);
    await Future.wait([paging, earlier]);
    expect(store.state.posts.single.id, 'latest');
    expect(store.state.loadingMore, isFalse);
    await store.destroy();
    client.httpClient.close();
  });

  test('closing a hashtag route ignores its pending request', () async {
    final client = _Client();
    final delayed = Completer<List<MastodonPost>>();
    client.readTag = (_) => delayed.future;
    final store = MastodonTagStore(client, ['https://studio.example'], 'art');
    final pending = store.refresh();
    final before = store.state;
    await store.destroy();
    delayed.complete([_post('late')]);
    await pending;
    await store.refresh();
    await store.loadMore();
    expect(store.state, same(before));
    expect(client.tagCursors.length, 1);
    client.httpClient.close();
  });

  testWidgets('hashtag reader retries an initial failure and offers manual page retry', (tester) async {
    final client = _Client()..readTag = (_) async => throw _failure;
    final harness = MastodonHarness(client: client);
    await tester.pumpWidget(harness.app(child: const MastodonTagScreen(tag: 'art')));
    await tester.pumpAndSettle();
    expect(find.text('Retry'), findsOneWidget);
    client.readTag = (_) async => List.filled(30, _post('123'));
    await tester.tap(find.text('Retry'));
    await tester.pumpAndSettle();
    expect(find.text('Post 123'), findsOneWidget);
    expect(find.byKey(const ValueKey('mastodon-tag-load-more')), findsOneWidget);
    client.readTag = (_) async => throw _failure;
    await tester.tap(find.byKey(const ValueKey('mastodon-tag-load-more')));
    await tester.pumpAndSettle();
    expect(find.text('Could not load more posts'), findsOneWidget);
    expect(find.text('Post 123'), findsOneWidget);
    client.readTag = (_) async => [_post('456')];
    await tester.tap(find.text('Retry'));
    await tester.pumpAndSettle();
    expect(find.text('Post 456'), findsOneWidget);
    expect(find.text('Could not load more posts'), findsNothing);
    expect(tester.takeException(), isNull);
    await harness.close(tester);
  });
}
