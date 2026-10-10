import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/threads/threads_client.dart';
import 'package:xta/plugins/threads/threads_direct_client.dart';
import 'package:xta/plugins/threads/threads_feed_options.dart';
import 'package:xta/plugins/threads/threads_feed_snapshot.dart';
import 'package:xta/plugins/threads/threads_models.dart';
import 'package:xta/plugins/threads/threads_store.dart';

class _AccountsStub extends ThreadsAccountsStore {
  _AccountsStub(List<String> handles) {
    update([for (final handle in handles) ThreadsAccount(handle: handle, name: handle)]);
  }

  @override
  Future<void> load() async {}
}

ThreadsPost _post(String id, {String handle = 'zuck', bool reply = false, String? repostBy, bool media = false}) {
  final post = ThreadsPost(
    id: id,
    handle: handle,
    authorName: handle,
    text: id == 'link' ? 'read https://example.com' : 'post $id',
    isReply: reply,
    images: media ? const ['https://cdn/p.jpg'] : const [],
    publishedAt: DateTime.utc(2026, 1, 1).add(Duration(minutes: int.tryParse(id) ?? 0)),
  );
  return repostBy == null ? post : post.repostedBy(id: 'r$id', handle: repostBy, name: repostBy);
}

PrefServiceCache _prefs() => PrefServiceCache(
  cache: {
    optionPluginThreadsDirectCookies: '',
    optionPluginThreadsDirectBearer: '',
    optionPluginThreadsInstance: '',
    optionPluginThreadsUserIds: '{"zuck":"1","meta":"2"}',
    optionPluginThreadsDirectCooldownUntil: '',
    optionPluginThreadsGuestLsd: 'tok',
    optionPluginThreadsGuestLsdAt: DateTime.now().toIso8601String(),
  },
);

/// Guest GraphQL answering one post per known account, counting requests.
({ThreadsDirectClient client, List<String> asked}) _guest(PrefServiceCache prefs) {
  final asked = <String>[];
  final client = ThreadsDirectClient(
    prefs,
    minGap: Duration.zero,
    httpClient: MockClient((request) async {
      final id = RegExp(r'userID%22%3A%22(\d+)').firstMatch(request.body)?.group(1);
      final handle = id == '1' ? 'zuck' : 'meta';
      asked.add(handle);
      return http.Response(
        '{"data":{"mediaData":{"threads":[{"thread_items":[{"post":'
        '{"pk":"$handle-1","code":"c","taken_at":1767225600,"caption":{"text":"from $handle"},'
        '"user":{"username":"$handle"}}}]}]}}}',
        200,
      );
    }),
  );
  return (client: client, asked: asked);
}

ThreadsFeedStore _store(PrefServiceCache prefs, ThreadsDirectClient direct, List<String> handles) => ThreadsFeedStore(
  ThreadsClient(httpClient: MockClient((_) async => http.Response('', 404))),
  direct,
  prefs,
  _AccountsStub(handles),
);

void main() {
  group('filterThreadsFeed', () {
    final posts = [
      _post('1'),
      _post('2', reply: true),
      _post('3', repostBy: 'friend'),
      _post('4', media: true),
      _post('link'),
    ];

    test('showing everything changes nothing', () {
      expect(filterThreadsFeed(posts, const ThreadsFeedOptions()), same(posts));
    });

    test('hides replies and reposts independently', () {
      final visible = filterThreadsFeed(posts, const ThreadsFeedOptions(hideReplies: true, hideReposts: true));
      expect(visible.map((p) => p.id), ['1', '4', 'link']);
    });

    test('media and links narrow to what carries them', () {
      expect(filterThreadsFeed(posts, const ThreadsFeedOptions(content: ThreadsFeedContent.media)).map((p) => p.id), [
        '4',
      ]);
      expect(filterThreadsFeed(posts, const ThreadsFeedOptions(content: ThreadsFeedContent.links)).map((p) => p.id), [
        'link',
      ]);
    });

    test('choices survive a restart and a bad stored value reads as none', () async {
      final prefs = PrefServiceCache(cache: {optionPluginThreadsFeedOptions: ''});
      await ThreadsFeedOptionsStore(prefs).set(const ThreadsFeedOptions(hideReposts: true));

      expect(ThreadsFeedOptionsStore(prefs).state.hideReposts, isTrue);
      expect(ThreadsFeedOptions.fromJson('{nope').filtered, isFalse);
    });
  });

  group('feed snapshot', () {
    test('a restart inside the cache window paints at once and asks Meta nothing', () async {
      final prefs = _prefs();
      final first = _guest(prefs);
      await _store(prefs, first.client, ['zuck', 'meta']).refresh();
      expect(first.asked, unorderedEquals(['zuck', 'meta']));

      final second = _guest(prefs);
      final restarted = _store(prefs, second.client, ['zuck', 'meta']);
      restarted.restore();
      expect(restarted.state.map((p) => p.text), unorderedEquals(['from zuck', 'from meta']));

      await restarted.refresh();
      expect(second.asked, isEmpty);
      expect(restarted.pending(['zuck', 'meta']), 0);
    });

    test('an account unfollowed since is not restored', () async {
      final prefs = _prefs();
      await _store(prefs, _guest(prefs).client, ['zuck', 'meta']).refresh();

      final restarted = _store(prefs, _guest(prefs).client, ['zuck']);
      restarted.restore();

      expect(restarted.state.map((p) => p.handle), ['zuck']);
    });

    test('a reposted post belongs to the account that reposted it', () {
      final snapshot = ThreadsFeedSnapshot(
        posts: [_post('1', handle: 'stranger', repostBy: 'zuck')],
        answeredAt: {'zuck': DateTime.now()},
      );
      final back = ThreadsFeedSnapshot.decode(snapshot.encode()).followedBy({'zuck'});

      expect(back.postsOf('zuck').single.isRepost, isTrue);
    });

    test('forgetting empties the feed and its snapshot', () async {
      final prefs = _prefs();
      final store = _store(prefs, _guest(prefs).client, ['zuck']);
      await store.refresh();

      await store.forget();

      expect(store.state, isEmpty);
      expect(ThreadsFeedSnapshot.decode(prefs.get<String>(optionPluginThreadsFeedSnapshot)).isEmpty, isTrue);
    });

    test('a damaged snapshot restores nothing', () {
      expect(ThreadsFeedSnapshot.decode('{"v":1,"posts":"nope"}').isEmpty, isTrue);
      expect(ThreadsFeedSnapshot.decode('not json').isEmpty, isTrue);
    });
  });
}
