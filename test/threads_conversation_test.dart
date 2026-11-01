import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/threads/threads_client.dart';
import 'package:xta/plugins/threads/threads_conversation.dart';
import 'package:xta/plugins/threads/threads_direct_client.dart';
import 'package:xta/plugins/threads/threads_models.dart';
import 'package:xta/plugins/threads/threads_thread_store.dart';
import 'package:xta/ui/conversation_sort.dart';

import 'support/threads_pages.dart';

ThreadsPost _post(String id, {String handle = 'zuck'}) => ThreadsPost(
  id: id,
  handle: handle,
  authorName: handle,
  text: 'post $id',
  url: 'https://www.threads.com/@$handle/post/C$id',
);

ThreadsDirectClient _client(http.Client http) => ThreadsDirectClient(
  PrefServiceCache(cache: {optionPluginThreadsDirectCooldownUntil: '', optionPluginThreadsUserIds: '{}'}),
  minGap: Duration.zero,
  httpClient: http,
);

void main() {
  group('threadsConversationFrom', () {
    test('splits the focus chain into ancestors, the post and its continuation', () {
      final conversation = threadsConversationFrom([
        [_post('1', handle: 'meta'), _post('2'), _post('3'), _post('4', handle: 'other')],
        [_post('5', handle: 'a'), _post('6', handle: 'b')],
      ], _post('2'));

      expect(conversation.ancestors.map((p) => p.id), ['1']);
      expect(conversation.focus.id, '2');
      expect(conversation.continuation.map((p) => p.id), ['3']);
      expect(conversation.replies.map((c) => c.map((p) => p.id).toList()), [
        ['4'],
        ['5', '6'],
      ]);
      expect(conversation.replyCount, 3);
      expect(conversation.loaded, isTrue);
    });

    test('a link stub finds its post by short code, whatever the host', () {
      const stub = ThreadsPost(
        id: 'https://www.threads.net/@zuck/post/C2?igshid=x',
        handle: 'zuck',
        authorName: 'zuck',
        text: '',
        url: 'https://www.threads.net/@zuck/post/C2?igshid=x',
      );
      final conversation = threadsConversationFrom([
        [_post('1', handle: 'meta'), _post('2')],
      ], stub);

      expect(conversation.focus.id, '2');
      expect(conversation.focus.text, 'post 2');
      expect(conversation.ancestors.map((p) => p.id), ['1']);
    });

    test('a short link whose code is not on the page focuses the first root', () {
      const stub = ThreadsPost(
        id: 'https://www.threads.com/t/Short',
        handle: '',
        authorName: '',
        text: '',
        url: 'https://www.threads.com/t/Short',
      );
      final conversation = threadsConversationFrom([
        [_post('9')],
        [_post('10', handle: 'a')],
      ], stub);

      expect(conversation.focus.id, '9');
      expect(conversation.replies.single.single.id, '10');
    });

    test('an opened repost keeps saying who reposted it', () {
      final repost = _post('2').repostedBy(id: 'outer', handle: 'friend', name: 'Friend');
      final conversation = threadsConversationFrom([
        [_post('2')],
      ], repost);

      expect(conversation.focus.reposterDisplayName, 'Friend');
    });

    test('an empty page keeps the seed', () {
      final conversation = threadsConversationFrom(const [], _post('1'));

      expect(conversation.focus.id, '1');
      expect(conversation.replies, isEmpty);
      expect(conversation.loaded, isTrue);
    });
  });

  group('reply rows', () {
    final conversation = ThreadsConversation(
      focus: _post('1'),
      replies: [
        [_post('2', handle: 'a'), _post('3', handle: 'b'), _post('4', handle: 'a'), _post('5', handle: 'b')],
        [_post('6', handle: 'c'), _post('7')],
      ],
      loaded: true,
    );

    test('long reply threads are cut until opened', () {
      final rows = threadsReplyRows(conversation, const ThreadsThreadView());

      expect(rows.whereType<ThreadsReplyPostRow>().map((r) => r.post.id), ['2', '3', '4', '6', '7']);
      final more = rows.whereType<ThreadsReplyMoreRow>().single;
      expect(more.hidden, 1);
      final last = rows.whereType<ThreadsReplyPostRow>().firstWhere((r) => r.post.id == '4');
      expect(last.connectBottom, isTrue);
    });

    test('an opened thread shows everything', () {
      final rows = threadsReplyRows(conversation, const ThreadsThreadView(expanded: {'2'}));

      expect(rows.whereType<ThreadsReplyMoreRow>(), isEmpty);
      expect(rows.length, 6);
    });

    test('author-only keeps the threads the author took part in', () {
      final rows = threadsReplyRows(conversation, const ThreadsThreadView(authorOnly: true));

      expect(rows.whereType<ThreadsReplyPostRow>().map((r) => r.post.id), ['6', '7']);
    });
  });

  group('reply order', () {
    ThreadsPost on(String id, int day, {String handle = 'a'}) => ThreadsPost(
      id: id,
      handle: handle,
      authorName: handle,
      text: 'post $id',
      publishedAt: DateTime.utc(2026, 10, day),
      likeCount: day,
    );
    final conversation = ThreadsConversation(
      focus: _post('1'),
      replies: [
        [on('2', 3), on('3', 9, handle: 'b')],
        [on('4', 5)],
        [on('5', 1, handle: 'zuck')],
      ],
      loaded: true,
    );
    List<String> ids(ThreadsThreadView view) =>
        threadsReplyRows(conversation, view).whereType<ThreadsReplyPostRow>().map((row) => row.post.id).toList();

    test('Top keeps the order Meta sent', () {
      expect(ids(const ThreadsThreadView()), ['2', '3', '4', '5']);
    });

    test('Newest and Oldest move each reply thread whole, by its first reply', () {
      expect(ids(const ThreadsThreadView(order: ReplySort.recent)), ['4', '2', '3', '5']);
      expect(ids(const ThreadsThreadView(order: ReplySort.oldest)), ['5', '2', '3', '4']);
    });

    test('an order Threads does not offer keeps Meta\'s', () {
      expect(ids(const ThreadsThreadView(order: ReplySort.mostLiked)), ['2', '3', '4', '5']);
    });

    test('the order applies to the author\'s replies too', () {
      expect(ids(const ThreadsThreadView(authorOnly: true, order: ReplySort.oldest)), ['5']);
    });

    test('choosing an order keeps the rest of the view', () {
      final view = ThreadsThreadViewStore()
        ..toggleAuthorOnly()
        ..selectOrder(ReplySort.oldest);
      addTearDown(view.destroy);

      expect(view.state.authorOnly, isTrue);
      expect(view.state.order, ReplySort.oldest);
    });
  });

  group('ThreadsThreadStore', () {
    test('reads the page once and reuses it on the next open', () async {
      var pageReads = 0;
      final direct = _client(
        MockClient((request) async {
          pageReads++;
          expect(request.url.host, 'www.threads.com');
          expect(request.url.query, isEmpty);
          return http.Response(
            threadsPage([
              [threadsPostJson('1')],
              [threadsPostJson('2', user: 'a')],
            ]),
            200,
          );
        }),
      );
      final seed = _post('1').repostedBy(id: 'x', handle: 'f', name: 'F');
      final stub = ThreadsPost(
        id: seed.id,
        handle: 'zuck',
        authorName: 'zuck',
        text: '',
        url: 'https://threads.net/@zuck/post/C1?igshid=abc',
      );

      final first = ThreadsThreadStore(direct, stub);
      await first.load();
      final second = ThreadsThreadStore(direct, _post('1'));
      await second.load();

      expect(pageReads, 1);
      expect(first.state.focus.text, 'post 1');
      expect(second.state.replies.single.single.id, '2');

      await second.load(force: true);
      expect(pageReads, 2);
    });

    test('a failed read keeps the tapped post on screen', () async {
      final direct = _client(MockClient((_) async => http.Response('nope', 500)));
      final store = ThreadsThreadStore(direct, _post('1'));

      await store.load();

      expect(store.error, isA<ThreadsException>());
      expect(store.state.focus.id, '1');
    });

    test('a post without a permalink needs no network', () async {
      final direct = _client(MockClient((_) async => fail('no request expected')));
      const post = ThreadsPost(id: 'rss', handle: 'zuck', authorName: 'zuck', text: 'from a feed');
      final store = ThreadsThreadStore(direct, post);

      await store.load();

      expect(store.state.loaded, isTrue);
    });
  });
}
