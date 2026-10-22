import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:xta/plugins/reddit/reddit_client.dart';

/// Live checks of the account-free Reddit client: a listing, its next page, a
/// thread, a comment's own page, a big thread, a search, a post filled in from
/// its card, a profile and a community's about.
/// Opt in with:
///   fvm flutter test test/live/reddit_smoke_test.dart --dart-define=RUN_LIVE=true
///
/// Reddit blocks datacenter addresses — GitHub's runners among them — with
/// "You've been blocked by network security", whatever the request. Add
/// `--dart-define=REDDIT_VIA=translate` there: www.reddit.com is then asked
/// through Google's translation proxy, which Reddit serves, so the markup the
/// parser reads is today's. Only the transport changes; the client is the
/// app's own. The proxy refuses a POST, so a big thread's next comments are
/// only asked for without it.
void main() {
  const enabled = bool.fromEnvironment('RUN_LIVE', defaultValue: false);
  const skip = enabled ? false : 'Set RUN_LIVE=true to hit live Reddit hosts';
  const via = String.fromEnvironment('REDDIT_VIA');

  RedditClient client() => RedditClient(httpClient: via == 'translate' ? _TranslateProxy() : null);

  test('a community lists posts, and pages on', () async {
    final reddit = client();
    final first = await reddit.fetchSubreddit('FlutterDev', clientId: '');
    expect(first.posts, isNotEmpty);
    expect(first.posts.first.title, isNotEmpty);
    expect(first.after, isNotNull);

    final next = await reddit.fetchSubreddit('FlutterDev', clientId: '', after: first.after);
    expect(next.posts, isNotEmpty);
    expect(next.posts.first.id, isNot(first.posts.first.id));
  }, skip: skip);

  test('a top listing keeps to its window', () async {
    final listing = await client().fetchSubreddit(
      'pics',
      clientId: '',
      sort: RedditSort.top,
      timeFilter: RedditTimeFilter.week,
    );
    final oldest = DateTime.now().subtract(const Duration(days: 8));
    expect(listing.posts, isNotEmpty);
    expect(listing.posts.every((p) => p.createdAt?.isAfter(oldest) ?? false), isTrue);
  }, skip: skip);

  test('a thread reads its comments, and a comment its replies', () async {
    final reddit = client();
    final listing = await reddit.fetchSubreddit('AskReddit', clientId: '');
    final post = listing.posts.firstWhere((p) => p.commentCount > 20);

    final thread = await reddit.fetchComments(post.permalink, clientId: '');
    expect(thread.comments, isNotEmpty);
    final answered = thread.comments.firstWhere((c) => c.permalink != null && !c.isStub);

    final own = await reddit.fetchComments(answered.permalink!, clientId: '');
    expect(own.comments.single.id, answered.id);
  }, skip: skip);

  test('a big thread reads every comment it serves, and where the rest are', () async {
    final reddit = client();
    final listing = await reddit.fetchSubreddit('AskReddit', clientId: '');
    final big = listing.posts.reduce((a, b) => a.commentCount >= b.commentCount ? a : b);

    final thread = await reddit.fetchComments(big.permalink, clientId: '');
    expect(thread.comments.where((c) => !c.isStub).length, greaterThan(4), reason: 'the ones Reddit renders late too');
    final more = thread.comments.last;
    expect(more.nextPage, isNotNull);

    if (via != 'translate') {
      final next = await reddit.fetchMoreComments(more);
      expect(next.where((c) => !c.isStub), isNotEmpty);
    }
  }, skip: skip);

  test('a post found by search is filled in from its card', () async {
    final reddit = client();
    final found = (await reddit.searchPosts('flutter')).first;

    final post = await reddit.fetchPost(found.permalink, clientId: '');
    expect(post?.id, found.id);
    expect(post?.title, isNotEmpty);
    expect(post?.author, isNotNull);
  }, skip: skip);

  test('a search finds posts, communities and people', () async {
    final reddit = client();
    expect(await reddit.searchPosts('flutter'), isNotEmpty);
    expect((await reddit.searchSubreddits('flutter')).map((c) => c.name), contains('FlutterDev'));
    expect((await reddit.searchUsers('spez')).map((u) => u.name), contains('spez'));
  }, skip: skip);

  test('a profile lists its posts', () async {
    final listing = await client().fetchUserPosts('spez');
    expect(listing.posts, isNotEmpty);
  }, skip: skip);

  test('a community says what it is about', () async {
    final about = await client().fetchSubredditAbout('FlutterDev', clientId: '');
    expect(about.description, isNotEmpty);
    expect(about.iconUrl, isNotNull);
  }, skip: skip);
}

/// Sends www.reddit.com and embed.reddit.com requests through Google's
/// translation proxy; every other host is asked directly.
class _TranslateProxy extends http.BaseClient {
  final _inner = http.Client();

  static const _hosts = {
    'www.reddit.com': 'www-reddit-com.translate.goog',
    'embed.reddit.com': 'embed-reddit-com.translate.goog',
  };

  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) {
    final url = request.url;
    final host = _hosts[url.host];
    if (host == null) {
      return _inner.send(request);
    }

    final proxied = http.Request(
      request.method,
      url.replace(
        host: host,
        queryParameters: {...url.queryParameters, '_x_tr_sl': 'auto', '_x_tr_tl': 'en', '_x_tr_hl': 'en'},
      ),
    )..headers.addAll(request.headers);
    return _inner.send(proxied);
  }

  @override
  void close() => _inner.close();
}
