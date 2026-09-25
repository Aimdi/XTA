import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/bluesky/bluesky_post_card.dart';
import 'package:xta/plugins/bluesky/bluesky_media_grid.dart';
import 'package:xta/plugins/bluesky/bluesky_thread_screen.dart';
import 'package:xta/plugins/bluesky/bluesky_thread_store.dart';
import 'support/bluesky_reading_harness.dart';

BlueskyPost _post(
  String id, {
  String? parent,
  String did = 'did:plc:maya',
  int likes = 0,
  String? date,
  List<String> labels = const [],
  String? text,
  BlueskyPost? quote,
}) => BlueskyPost.fromSnapshot({
  ...bluePost(id, parent: parent).toJson(),
  'did': did,
  'handle': did == 'did:plc:maya' ? 'maya.bsky.social' : '$did.example',
  'likeCount': likes,
  'publishedAt': date,
  'labels': labels,
  'text': text ?? id,
  'quotedPost': quote?.toJson(),
});

class _ChangingAppViewClient extends BlueskyReadingClient {
  String source = 'https://first.example';
  final pending = <Completer<BlueskyThread>>[];
  @override
  String get baseUrl => source;
  @override
  Future<BlueskyThread> getPostThread(String uri, {int depth = 10, int parentHeight = 80}) {
    final response = Completer<BlueskyThread>();
    pending.add(response);
    return response.future;
  }
}

void main() {
  test('author focus preserves connecting context but removes unrelated branches', () {
    final thread = BlueskyThread(
      post: _post('root'),
      replies: [
        _post('question', parent: 'root', did: 'did:plc:other'),
        _post('answer', parent: 'question'),
        _post('other', parent: 'root', did: 'did:plc:other'),
        _post('followup', parent: 'answer'),
        _post('context'), // also an ancestor, not a reply
      ],
      ancestors: [_post('context')],
    );
    final rows = blueskyVisibleReplies(blueskyReplyBranches(thread, authorOnly: true), {});
    expect(rows.map((row) => row.branch.post.text), ['question', 'answer', 'followup']);
    expect(rows.map((row) => row.branch.contextOnly), [true, false, false]);
    expect(rows.first.branch.descendants, 2);
    expect(
      blueskyVisibleReplies(blueskyReplyBranches(thread, authorOnly: true), {_post('question').uri}),
      hasLength(1),
    );
  });

  test('reply order sorts siblings stably while retaining their descendants', () {
    final thread = BlueskyThread(
      post: _post('root'),
      replies: [
        _post('a', parent: 'root', likes: 10, date: '2026-01-01'),
        _post('b', parent: 'root', likes: 20, date: '2026-01-03'),
        _post('a1', parent: 'a', likes: 100, date: '2026-01-04'),
        _post('c', parent: 'root', likes: 20, date: '2026-01-02'),
        _post('undated', parent: 'root'),
      ],
    );
    List<String> order(BlueskyReplyOrder order) => blueskyVisibleReplies(
      blueskyReplyBranches(thread, order: order),
      {},
    ).map((row) => row.branch.post.text).toList();
    expect(order(BlueskyReplyOrder.original), ['a', 'a1', 'b', 'c', 'undated']);
    expect(order(BlueskyReplyOrder.popular), ['b', 'c', 'a', 'a1', 'undated']);
    expect(order(BlueskyReplyOrder.newest), ['b', 'c', 'a', 'a1', 'undated']);
    expect(order(BlueskyReplyOrder.oldest), ['a', 'a1', 'c', 'b', 'undated']);
  });

  test('twenty thousand nested replies build, count and collapse without recursion', () {
    final replies = [for (var i = 0; i < 20000; i++) _post('reply-$i', parent: i == 0 ? 'root' : 'reply-${i - 1}')];
    final branches = blueskyReplyBranches(BlueskyThread(post: _post('root'), replies: replies));
    expect(branches.single.descendants, 19999);
    final rows = blueskyVisibleReplies(branches, {});
    expect(rows, hasLength(20000));
    expect(rows.last.depth, 19999);
    expect(blueskyVisibleReplies(branches, {replies.first.uri}), hasLength(1));
  });

  test('refresh errors retain controls and branches; stale collapse ids are pruned', () async {
    final client = BlueskyReadingClient();
    final store = BlueskyThreadStore(client, _post('root'));
    addTearDown(store.destroy);
    addTearDown(client.httpClient.close);
    await store.refresh();
    store.selectOrder(BlueskyReplyOrder.popular);
    store.selectAuthor(true);
    store.setAllExpanded(false);
    expect(store.state.collapsed, {_post('a').uri});
    client.nextThread = Completer<BlueskyThread>();
    final failure = store.refresh();
    client.nextThread!.completeError(StateError('offline'));
    await failure;
    store.toggleContext();
    expect(store.state.error, isNotNull);
    expect(store.state.branches, isNotEmpty);
    expect(store.state.order, BlueskyReplyOrder.popular);
    expect(store.state.authorOnly, isTrue);
    client.nextThread = Completer<BlueskyThread>();
    final retry = store.refresh();
    client.nextThread!.complete(
      BlueskyThread(
        post: _post('root'),
        replies: [_post('new', parent: 'root')],
      ),
    );
    await retry;
    expect(store.state.error, isNull);
    expect(store.state.collapsed, isEmpty);
  });

  test('newest refresh wins and disposed stores never start new requests', () async {
    final client = BlueskyReadingClient();
    final store = BlueskyThreadStore(client, _post('root'));
    addTearDown(client.httpClient.close);
    final old = Completer<BlueskyThread>();
    client.nextThread = old;
    final oldRequest = store.refresh();
    client.nextThread = Completer<BlueskyThread>();
    final newRequest = store.refresh();
    client.nextThread!.complete(BlueskyThread(post: _post('new')));
    await newRequest;
    old.complete(BlueskyThread(post: _post('old')));
    await oldRequest;
    expect(store.state.thread.post.text, 'new');
    await store.destroy();
    await store.refresh();
    store.toggleContext();
    store.toggle('x');
    store.selectAuthor(true);
    store.selectOrder(BlueskyReplyOrder.newest);
    store.setAllExpanded(false);
    store.focusSelected();
    expect(store.state.thread.post.text, 'new');
  });

  for (final fails in [false, true]) {
    test('thread discards late AppView ${fails ? 'error' : 'content'} and reloads from current server', () async {
      final client = _ChangingAppViewClient();
      final store = BlueskyThreadStore(client, _post('root'));
      addTearDown(store.destroy);
      addTearDown(client.httpClient.close);
      final first = store.refresh();
      client.source = 'https://second.example';
      if (fails) {
        client.pending.first.completeError(StateError('old server error'));
      } else {
        client.pending.first.complete(BlueskyThread(post: _post('stale')));
      }
      await first;
      expect(client.pending, hasLength(2));
      expect(store.state.thread.post.text, 'root');
      expect(store.state.loading, isTrue);
      expect(store.state.error, isNull);
      client.pending.last.complete(BlueskyThread(post: _post('current')));
      await Future<void>.delayed(Duration.zero);
      expect(store.state.thread.post.text, 'current');
      expect(store.state.loading, isFalse);
    });
  }

  testWidgets('generic warnings cover text and reset when revision changes', (tester) async {
    final h = BlueReadingHarness();
    addTearDown(() => h.close(tester));
    Widget card(BlueskyPost post) => h.app(
      Scaffold(
        body: SingleChildScrollView(child: BlueskyPostCard(post: post)),
      ),
    );
    await tester.pumpWidget(card(_post('warned', labels: ['!warn'], text: 'Hidden first revision')));
    await tester.pumpAndSettle();
    expect(find.text('Hidden first revision'), findsNothing);
    await tester.tap(find.byKey(const ValueKey('bluesky-content-warning')));
    await tester.pumpAndSettle();
    expect(find.text('Hidden first revision'), findsOneWidget);
    await tester.pumpWidget(card(_post('warned', labels: ['!warn'], text: 'Hidden new revision')));
    await tester.pumpAndSettle();
    expect(find.text('Hidden new revision'), findsNothing);
    await tester.tap(find.byKey(const ValueKey('bluesky-content-warning')));
    await tester.pumpAndSettle();
    expect(find.text('Hidden new revision'), findsOneWidget);
  });

  testWidgets('unavailable text and quotes never offer reveal; media labels retain text', (tester) async {
    final h = BlueReadingHarness();
    addTearDown(() => h.close(tester));
    for (final label in ['!hide', '!no-unauthenticated']) {
      final post = _post('hidden', labels: [label], text: 'Restricted body');
      await tester.pumpWidget(
        h.app(
          Scaffold(
            body: SingleChildScrollView(
              child: Column(
                children: [
                  BlueskyPostCard(post: post),
                  BlueskyPostCard(post: _post('outer', quote: post)),
                ],
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Restricted body'), findsNothing);
      expect(find.byKey(const ValueKey('bluesky-content-warning')), findsNothing);
      expect(find.text('This post cannot be displayed.'), findsNWidgets(2));
    }
    await tester.pumpWidget(
      h.app(
        Scaffold(
          body: BlueskyPostCard(post: _post('media-only', labels: ['nudity'])),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('media-only'), findsOneWidget);
  });

  testWidgets('footer actions remain named when counts are hidden', (tester) async {
    final semantics = tester.ensureSemantics();
    final h = BlueReadingHarness();
    addTearDown(() => h.close(tester));
    await h.prefs.set(optionCalmMode, true);
    await tester.pumpWidget(h.app(Scaffold(body: BlueskyPostCard(post: _post('root')))));
    await tester.pumpAndSettle();
    expect(find.byTooltip('Thread'), findsOneWidget);
    expect(find.byTooltip('Quotes'), findsOneWidget);
    expect(find.byIcon(Icons.format_quote_outlined), findsOneWidget);
    expect(find.bySemanticsLabel(RegExp('Remove like from this device')), findsOneWidget);
    semantics.dispose();
  });

  testWidgets('media grid exposes ALT but never announces protected descriptions', (tester) async {
    final semantics = tester.ensureSemantics();
    final h = BlueReadingHarness();
    addTearDown(() => h.close(tester));
    final post = BlueskyPost.fromSnapshot({
      ...bluePost('alt', media: true).toJson(),
      'imageAlts': ['A bright red bridge'],
    });
    await tester.pumpWidget(
      h.app(
        Scaffold(
          body: SizedBox(width: 150, height: 150, child: BlueskyMediaTile(post: post)),
        ),
      ),
    );
    await tester.pump();
    expect(find.bySemanticsLabel(RegExp('Maya Chen · A bright red bridge')), findsOneWidget);
    expect(find.text('ALT'), findsOneWidget);
    final covered = BlueskyPost.fromSnapshot({
      ...post.toJson(),
      'labels': ['!no-unauthenticated'],
    });
    await tester.pumpWidget(
      h.app(
        Scaffold(
          body: SizedBox(width: 150, height: 150, child: BlueskyMediaTile(post: covered)),
        ),
      ),
    );
    await tester.pump();
    expect(find.bySemanticsLabel(RegExp('A bright red bridge')), findsNothing);
    expect(find.text('ALT'), findsNothing);
    semantics.dispose();
  });

  testWidgets('conversation controls work in compact large-text RTL layout', (tester) async {
    tester.view.physicalSize = const Size(320, 1300);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final h = BlueReadingHarness();
    addTearDown(() => h.close(tester));
    await tester.pumpWidget(h.app(BlueskyThreadScreen(post: _post('root')), scale: 1.5, rtl: true));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('bluesky-thread-branches')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Collapse all replies'));
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(find.byKey(const ValueKey('bluesky-thread-author')), 250);
    await tester.tap(find.byKey(const ValueKey('bluesky-thread-author')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('bluesky-thread-sort')));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(CheckedPopupMenuItem<BlueskyReplyOrder>, 'Most liked first'));
    await tester.pumpAndSettle();
    expect(find.byType(CheckedPopupMenuItem<BlueskyReplyOrder>), findsNothing);
    expect(find.text('Most liked first'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('bluesky-thread-jump')));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('bluesky-thread-selected')), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
