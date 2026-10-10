import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:xta/plugins/plugin_profile_tabs.dart';
import 'package:xta/plugins/threads/threads_api.dart';
import 'package:xta/plugins/threads/threads_client.dart';
import 'package:xta/plugins/threads/threads_direct_client.dart';
import 'package:xta/plugins/threads/threads_models.dart';
import 'package:xta/plugins/threads/threads_profile_store.dart';
import 'package:xta/plugins/threads/threads_store.dart';

ThreadsPost _post(String id, {bool reply = false, bool media = false, int minute = 0}) => ThreadsPost(
  id: id,
  handle: 'zuck',
  authorName: 'Mark',
  text: 'post $id',
  isReply: reply,
  images: media ? const ['https://cdn/p.jpg'] : const [],
  publishedAt: DateTime.utc(2026, 1, 1, 0, minute),
);

class _Direct extends ThreadsDirectClient {
  _Direct(super.prefs, {this.repliesFail = false}) : super(httpClient: MockClient((_) async => http.Response('', 500)));

  final bool repliesFail;
  var replyReads = 0;
  var profileFails = false;

  @override
  Future<ThreadsProfile> fetchGuestProfile(String handle) async {
    if (profileFails) throw ThreadsException(ThreadsErrorKind.unreachable, 'down');
    return ThreadsProfile.fromJson({'username': handle, 'full_name': 'Mark'});
  }

  @override
  Future<List<ThreadsPost>> fetchGuestReplies(String handle) async {
    replyReads++;
    if (repliesFail) throw ThreadsException(ThreadsErrorKind.unreachable, 'down');
    return const [];
  }
}

class _Feed extends ThreadsFeedStore {
  _Feed(ThreadsDirectClient direct, BasePrefService prefs, this.posts)
    : super(ThreadsClient(), direct, prefs, ThreadsAccountsStore());

  List<ThreadsPost> posts;
  var fail = false;

  @override
  Future<List<ThreadsPost>> postsFor(
    List<String> handles, {
    bool forceRefresh = false,
    void Function(List<ThreadsPost>)? onPartial,
  }) async {
    if (fail) throw ThreadsException(ThreadsErrorKind.throttled, '429');
    return posts;
  }
}

ThreadsProfileStore _store(_Direct direct, _Feed feed, BasePrefService prefs) =>
    ThreadsProfileStore(handle: 'zuck', direct: direct, api: ThreadsApi(), feed: feed, prefs: prefs);

void main() {
  test('tabs split posts, replies and media, without repeats', () {
    final state = ThreadsProfileState(
      posts: [_post('1', minute: 3), _post('2', reply: true, minute: 2), _post('3', media: true, minute: 1)],
      replies: [_post('2', reply: true, minute: 2), _post('4', reply: true, media: true, minute: 4)],
    );

    expect(state.forTab(PluginProfileFeedTab.posts).map((p) => p.id), ['1', '3']);
    expect(state.forTab(PluginProfileFeedTab.replies).map((p) => p.id), ['4', '2']);
    expect(state.forTab(PluginProfileFeedTab.media).map((p) => p.id), ['4', '3']);
    expect(state.forTab(PluginProfileFeedTab.saved, liked: [_post('9')]).single.id, '9');
  });

  test('the replies page is asked for once, and a failure falls back to the posts', () async {
    final prefs = PrefServiceCache();
    final direct = _Direct(prefs, repliesFail: true);
    final store = _store(direct, _Feed(direct, prefs, [_post('1'), _post('2', reply: true)]), prefs);
    await store.load();

    await store.loadReplies();
    await store.loadReplies();

    expect(direct.replyReads, 1);
    expect(store.state.loadingReplies, isFalse);
    expect(store.state.forTab(PluginProfileFeedTab.replies).single.id, '2');
  });

  test('a failed refresh keeps the profile already on screen', () async {
    final prefs = PrefServiceCache();
    final direct = _Direct(prefs);
    final feed = _Feed(direct, prefs, [_post('1')]);
    final store = _store(direct, feed, prefs);
    await store.load();

    direct.profileFails = true;
    feed.fail = true;
    await store.load(force: true);

    expect(store.state.profile?.username, 'zuck');
    expect(store.state.posts.single.id, '1');
    expect(store.error, isNull);
  });

  test('nothing at all to show surfaces the error', () async {
    final prefs = PrefServiceCache();
    final direct = _Direct(prefs)..profileFails = true;
    final feed = _Feed(direct, prefs, const [])..fail = true;
    final store = _store(direct, feed, prefs);

    await store.load();

    expect(store.state.isEmpty, isTrue);
    expect(store.error, isA<ThreadsException>());
  });
}
