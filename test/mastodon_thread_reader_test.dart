import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_thread_screen.dart';
import 'package:xta/plugins/mastodon/mastodon_thread_store.dart';

import 'support/mastodon_harness.dart';

MastodonPost post(String id, {String? parent, String acct = 'writer@studio.example'}) => MastodonPost(
  id: id,
  replyToId: parent,
  acct: acct,
  authorName: acct.split('@').first,
  text: 'Post $id',
  url: 'https://studio.example/@writer/$id',
);

final conversation = MastodonThread(
  status: post('root', parent: 'parent'),
  ancestors: [
    post('parent', parent: 'grandparent'),
    post('grandparent'),
  ],
  descendants: [
    post('bridge', parent: 'root', acct: 'reader@studio.example'),
    post('unrelated', parent: 'root', acct: 'other@studio.example'),
    post('continuation', parent: 'bridge'),
    post('nested', parent: 'continuation'),
  ],
);

class ThreadClient extends MastodonFixtureClient {
  MastodonThread thread;
  final requests = <Completer<MastodonThread>>[];
  final bool delayed;

  ThreadClient({this.delayed = false, MastodonThread? thread}) : thread = thread ?? conversation;

  @override
  Future<MastodonThread> fetchThreadAnywhere(List<String> instances, MastodonPost seed) {
    if (!delayed) return Future.value(thread);
    final request = Completer<MastodonThread>();
    requests.add(request);
    return request.future;
  }
}

void main() {
  test('author focus keeps connecting replies and excludes unrelated branches', () {
    final rows = mastodonReplyRows(conversation, {}, authorOnly: true);
    expect(rows.map((row) => row.post.id), ['bridge', 'continuation', 'nested']);
    expect(rows.map((row) => row.depth), [0, 1, 2]);
    expect(rows.map((row) => row.contextOnly), [true, false, false]);
    expect(rows.map((row) => row.descendants), [2, 1, 0]);
    expect(mastodonReplyRows(conversation, {'bridge'}, authorOnly: true).single.descendants, 2);
  });

  test('author identity is case insensitive and does not match another server', () {
    final thread = MastodonThread(
      status: post('root'),
      descendants: [
        post('same', parent: 'root', acct: 'WRITER@STUDIO.EXAMPLE'),
        post('different', parent: 'root', acct: 'writer@other.example'),
      ],
    );
    expect(mastodonReplyRows(thread, {}, authorOnly: true).single.post.id, 'same');
  });

  test('ancestors follow actual parent links without repeating the selected post', () {
    expect(mastodonThreadAncestors(conversation).map((item) => item.id), ['grandparent', 'parent']);
    final cyclic = MastodonThread(
      status: post('root', parent: 'a'),
      ancestors: [
        post('a', parent: 'b'),
        post('b', parent: 'a'),
        post('a', parent: 'b'),
        post('root'),
        post('extra'),
      ],
    );
    expect(mastodonThreadAncestors(cyclic).map((item) => item.id), ['extra', 'b', 'a']);
  });

  test('malformed duplicate, cyclic, orphan, and ancestor replies remain finite', () {
    final thread = MastodonThread(
      status: post('root'),
      ancestors: [post('ancestor')],
      descendants: [
        post('a', parent: 'b'),
        post('b', parent: 'a'),
        post('a', parent: 'b'),
        post('orphan', parent: 'missing'),
        post('self', parent: 'self'),
        post('ancestor'),
        post('root'),
      ],
    );
    final rows = mastodonReplyRows(thread, {});
    expect(rows.map((row) => row.post.id), ['orphan', 'self', 'a', 'b']);
    expect(rows.map((row) => row.descendants), [0, 0, 1, 0]);
    expect(mastodonReplyRows(thread, {'a'}).map((row) => row.post.id), ['orphan', 'self', 'a']);
  });

  test('a deeply nested conversation avoids recursive stack growth', () {
    final thread = MastodonThread(
      status: post('root'),
      descendants: [
        for (var index = 0; index < 10000; index++) post('$index', parent: index == 0 ? 'root' : '${index - 1}'),
      ],
    );
    final rows = mastodonReplyRows(thread, {});
    expect(rows, hasLength(10000));
    expect(rows.first.descendants, 9999);
    expect(rows.last.depth, 9999);
    expect(mastodonReplyRows(thread, {'0'}), hasLength(1));
  });

  test('author mode and expansion changes survive refresh; vanished IDs are pruned', () async {
    final client = ThreadClient(delayed: true);
    final store = MastodonThreadStore(client, ['https://studio.example'], conversation.status);
    final first = store.refresh();
    client.requests[0].complete(conversation);
    await first;
    final second = store.refresh();
    store.selectAuthor(true);
    store.setAllExpanded(false);
    store.toggleAncestors();
    store.toggle('vanished');
    client.requests[1].complete(conversation);
    await second;
    expect(store.state.authorOnly, isTrue);
    expect(store.state.ancestorsOpen, isTrue);
    expect(store.state.collapsed, {'bridge', 'continuation'});
    store.setAllExpanded(true);
    expect(store.state.collapsed, isEmpty);
    store.focusSelected();
    expect(store.state.ancestorsOpen, isFalse);
    await store.destroy();
    client.httpClient.close();
  });

  test('failed refresh keeps the conversation readable and a retry clears the error', () async {
    final client = ThreadClient(delayed: true);
    final store = MastodonThreadStore(client, [], conversation.status);
    final initial = store.refresh();
    client.requests[0].complete(conversation);
    await initial;
    final failing = store.refresh();
    client.requests[1].completeError(StateError('offline'));
    await failing;
    expect(store.state.thread, same(conversation));
    expect(store.state.error, isA<StateError>());
    expect(store.state.loading, isFalse);
    final retry = store.refresh();
    expect(store.state.error, isNull);
    client.requests[2].complete(conversation);
    await retry;
    expect(store.state.error, isNull);
    await store.destroy();
    client.httpClient.close();
  });

  test('superseded reads and disposal cannot replace current conversation state', () async {
    final client = ThreadClient(delayed: true);
    final store = MastodonThreadStore(client, [], post('seed'));
    final first = store.refresh();
    final second = store.refresh();
    client.requests[1].complete(conversation);
    await second;
    client.requests[0].complete(MastodonThread(status: post('stale')));
    await first;
    expect(store.state.thread, same(conversation));
    final third = store.refresh();
    await store.destroy();
    client.requests[2].complete(MastodonThread(status: post('late')));
    await third;
    expect(store.state.thread, same(conversation));
    await store.refresh();
    expect(client.requests, hasLength(3));
    client.httpClient.close();
  });

  testWidgets('author focus, fold-all, and selected-post return work in the conversation', (tester) async {
    final h = MastodonHarness(client: ThreadClient());
    await tester.pumpWidget(h.app(child: MastodonThreadScreen(post: conversation.status)));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('mastodon-thread-author')));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('thread-post-unrelated')), findsNothing);
    expect(find.text('Reply context'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('mastodon-thread-branches')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Collapse all replies'));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('thread-post-continuation')), findsNothing);
    await tester.tap(find.byKey(const ValueKey('mastodon-thread-branches')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Expand all replies'));
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(find.byKey(const ValueKey('thread-post-continuation')), 180);
    expect(find.byKey(const ValueKey('thread-post-continuation')), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('mastodon-thread-jump')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('mastodon-thread-context')));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('thread-post-parent')), findsOneWidget);
    await tester.drag(find.byType(ListView), const Offset(0, -700));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('mastodon-thread-jump')));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('thread-post-parent')), findsNothing);
    final selected = tester.getRect(find.byKey(const ValueKey('mastodon-thread-selected')));
    expect(selected.top, greaterThanOrEqualTo(0));
    expect(selected.bottom, lessThan(tester.view.physicalSize.height));
    expect(tester.takeException(), isNull);
    await h.close(tester);
  });

  testWidgets('empty public context has a clear finished state', (tester) async {
    final h = MastodonHarness(
      client: ThreadClient(thread: MastodonThread(status: post('root'))),
    );
    await tester.pumpWidget(h.app(child: MastodonThreadScreen(post: post('root'))));
    await tester.pumpAndSettle();
    expect(find.text('No public replies were returned by this server.'), findsOneWidget);
    expect(find.byType(CircularProgressIndicator), findsNothing);
    expect(tester.takeException(), isNull);
    await h.close(tester);
  });

  testWidgets('an empty author filter explains its scope and can return to all replies', (tester) async {
    final thread = MastodonThread(
      status: post('root'),
      descendants: [post('reader', parent: 'root', acct: 'reader@studio.example')],
    );
    final h = MastodonHarness(client: ThreadClient(thread: thread));
    await tester.pumpWidget(h.app(child: MastodonThreadScreen(post: thread.status)));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('mastodon-thread-author')));
    await tester.pumpAndSettle();
    expect(find.text('No further posts from this author in the loaded conversation.'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('mastodon-thread-all')));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('thread-post-reader')), findsOneWidget);
    expect(find.byKey(const ValueKey('mastodon-thread-empty')), findsNothing);
    expect(tester.takeException(), isNull);
    await h.close(tester);
  });

  testWidgets('thread controls fit a compact enlarged-text RTL layout', (tester) async {
    tester.view.physicalSize = const Size(320, 740);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final h = MastodonHarness(client: ThreadClient());
    await tester.pumpWidget(
      h.app(
        child: const Scaffold(body: SizedBox(key: ValueKey('start'))),
        scale: 2,
        rtl: true,
      ),
    );
    Navigator.of(
      tester.element(find.byKey(const ValueKey('start'))),
    ).push(MaterialPageRoute<void>(builder: (_) => MastodonThreadScreen(post: conversation.status)));
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.byKey(const ValueKey('mastodon-thread-author')));
    await tester.tap(find.byKey(const ValueKey('mastodon-thread-author')));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    await h.close(tester);
  });
}
