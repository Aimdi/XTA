import 'dart:convert';

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

ThreadsPost _post(String id, {String handle = 'zuck'}) => ThreadsPost(
  id: id,
  handle: handle,
  authorName: handle,
  text: 'post $id',
  url: 'https://www.threads.com/@$handle/post/C$id',
);

Map<String, Object?> _json(String id, {String user = 'zuck'}) => {
  'pk': id,
  'code': 'C$id',
  'caption': {'text': 'post $id'},
  'user': {'username': user},
};

String _page(List<List<Map<String, Object?>>> chains) {
  final blob = jsonEncode({
    'edges': [
      for (final chain in chains)
        {
          'node': {
            'thread_items': [
              for (final post in chain) {'post': post},
            ],
          },
        },
    ],
  });
  return '<html><script data-sjs>$blob</script></html>';
}

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

  group('ThreadsThreadStore', () {
    test('reads the page once and reuses it on the next open', () async {
      var pageReads = 0;
      final direct = _client(
        MockClient((request) async {
          pageReads++;
          expect(request.url.host, 'www.threads.com');
          expect(request.url.query, isEmpty);
          return http.Response(
            _page([
              [_json('1')],
              [_json('2', user: 'a')],
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
