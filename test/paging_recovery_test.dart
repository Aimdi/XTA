import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:xta/client/client.dart';
import 'package:xta/group/group_media_page.dart';
import 'package:xta/tweet/paginated_tweet_list.dart';
import 'package:xta/utils/paging.dart';

TweetChain _chain(String id) => TweetChain(id: id, tweets: [], isPinned: false);

void main() {
  testWidgets('stalled page unlocks retry and a late result cannot change its cursor', (tester) async {
    final old = Completer<CursorPage<String, String>>();
    var calls = 0;
    final paging = CursorPagingController<String, String>(
      (_) => ++calls == 1 ? old.future : Future.value((items: ['new'], nextCursor: 'new-cursor')),
    );
    addTearDown(paging.dispose);
    paging.pagingController.fetchNextPage();
    await tester.pump(const Duration(seconds: 46));
    expect(paging.pagingController.value.isLoading, isFalse);
    expect(pagingErrorOf(paging.pagingController.value)?.error, isA<TimeoutException>());
    paging.pagingController.fetchNextPage();
    await tester.pump();
    old.complete((items: ['old'], nextCursor: 'old-cursor'));
    await tester.pump();
    expect(paging.items, ['new']);
    expect(paging.nextCursor, 'new-cursor');
  });

  testWidgets('refresh invalidates the old cursor even when the old page finishes last', (tester) async {
    final old = Completer<CursorPage<String, String>>();
    var calls = 0;
    final paging = CursorPagingController<String, String>(
      (_) => ++calls == 1 ? old.future : Future.value((items: ['new'], nextCursor: 'new-cursor')),
    );
    addTearDown(paging.dispose);
    paging.pagingController.fetchNextPage();
    paging.pagingController.refresh();
    paging.pagingController.fetchNextPage();
    await tester.pump();
    old.complete((items: ['old'], nextCursor: null));
    await tester.pump();
    expect(paging.items, ['new']);
    expect(paging.nextCursor, 'new-cursor');
    paging.pagingController.fetchNextPage();
    await tester.pump();
    expect(calls, 3);
  });

  testWidgets('shared first page releases a timeout and does not clear an active retry', (tester) async {
    final load = SharedAsyncLoad<String>(timeout: const Duration(seconds: 1));
    final old = Completer<String>();
    final retry = Completer<String>();
    final first = load.load(() => old.future);
    expect(identical(first, load.load(() async => 'duplicate')), isTrue);
    final check = expectLater(first, throwsA(isA<TimeoutException>()));
    await tester.pump(const Duration(seconds: 2));
    await check;
    expect(load.isLoading, isFalse);
    final second = load.load(() => retry.future);
    old.complete('old');
    await tester.pump();
    expect(identical(second, load.load(() async => 'duplicate')), isTrue);
    retry.complete('new');
    expect(await second, 'new');
    expect(load.isLoading, isFalse);
  });

  testWidgets('soft refresh times out without losing posts and can retry', (tester) async {
    final feed = TweetFeedController(requestTimeout: const Duration(seconds: 1));
    addTearDown(feed.dispose);
    feed.loader = (_) async => (chains: [_chain('saved')], nextCursor: 'next');
    feed.controller.fetchNextPage();
    await tester.pump();
    final old = Completer<TweetPageResult>();
    feed.loader = (_) => old.future;
    final refresh = feed.softRefresh();
    await tester.pump(const Duration(seconds: 2));
    await refresh;
    expect(feed.items!.single.id, 'saved');
    expect(pagingErrorOf(feed.controller.value)?.error, isA<TimeoutException>());
    feed.loader = (_) async => (chains: [_chain('new')], nextCursor: 'fresh');
    await feed.softRefresh();
    old.complete((chains: [_chain('old')], nextCursor: null));
    await tester.pump();
    expect(feed.items!.single.id, 'new');
    expect(feed.nextCursor, 'fresh');
    expect(feed.controller.value.error, isNull);
  });

  testWidgets('soft refresh cancels an older in-flight page', (tester) async {
    final old = Completer<TweetPageResult>();
    final feed = TweetFeedController();
    addTearDown(feed.dispose);
    feed.loader = (_) => old.future;
    feed.controller.fetchNextPage();
    feed.loader = (_) async => (chains: [_chain('new')], nextCursor: 'fresh');
    await feed.softRefresh();
    old.complete((chains: [_chain('old')], nextCursor: null));
    await tester.pump();
    expect(feed.items!.map((e) => e.id), ['new']);
    expect(feed.nextCursor, 'fresh');
    expect(feed.controller.value.isLoading, isFalse);
  });
}
