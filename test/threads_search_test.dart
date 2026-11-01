import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/threads/threads_direct_client.dart';
import 'package:xta/plugins/threads/threads_likes_store.dart';
import 'package:xta/plugins/threads/threads_post_card.dart';
import 'package:xta/plugins/threads/threads_search_screen.dart';
import 'package:xta/plugins/threads/threads_store.dart';
import 'package:xta/ui/reader_failure.dart';
import 'package:xta/ui/x_look_theme.dart';

import 'support/feed_ink_harness.dart';
import 'support/threads_pages.dart';

ThreadsDirectClient _client(http.Client http) => ThreadsDirectClient(
  PrefServiceCache(cache: {optionPluginThreadsDirectCooldownUntil: '', optionPluginThreadsUserIds: '{}'}),
  minGap: Duration.zero,
  httpClient: http,
);

/// docs/specs/threads-x-parity.md, Search.
void main() {
  group('threadsSearchResults', () {
    test('takes, in each chain, the post the page found it for', () {
      final page = threadsPage([
        [threadsPostJson('1', text: 'A walk by the sea'), threadsPostJson('2', user: 'b', text: 'Flutter on desktop')],
        [threadsPostJson('3', text: 'Nothing here matches')],
      ]);

      expect(threadsSearchResults(page, 'flutter').map((post) => post.id), ['2', '3']);
    });

    test('takes a tag search by the topic the post is filed under', () {
      final page = threadsPage([
        [threadsPostJson('1', text: 'Context'), threadsPostJson('2', text: 'Reading', tag: 'Books')],
      ]);

      expect(threadsSearchResults(page, '#books', tag: true).single.id, '2');
    });
  });

  test('asks the public search page: top, recent, or a tag', () {
    expect(threadsSearchUri('cats').toString(), 'https://www.threads.com/search?q=cats&serp_type=default');
    expect(threadsSearchUri('cats', recent: true).queryParameters, {
      'q': 'cats',
      'serp_type': 'default',
      'filter': 'recent',
    });
    expect(threadsSearchUri('Books', tag: true).queryParameters, {'q': 'Books', 'serp_type': 'tags'});
  });

  test('a guest search reads the page as a logged-out browser and returns its posts', () async {
    final asked = <Uri>[];
    final client = _client(
      MockClient((request) async {
        asked.add(request.url);
        return http.Response(
          threadsPage([
            [threadsPostJson('7', user: 'maya', text: 'flutter tips')],
          ]),
          200,
          headers: {'content-type': 'text/html; charset=utf-8'},
        );
      }),
    );

    final posts = await client.searchGuestPosts('flutter', recent: true);

    expect(posts.single.handle, 'maya');
    expect(asked.single.queryParameters['filter'], 'recent');
    expect(asked.single.path, '/search');
  });

  group('the search screen', () {
    late ThreadsAccountsStore accounts;
    late ThreadsLikesStore likes;

    setUp(() {
      accounts = ThreadsAccountsStore();
      likes = ThreadsLikesStore(PrefServiceCache());
    });

    tearDown(() async {
      await accounts.destroy();
      await likes.destroy();
    });

    Future<List<Uri>> pump(WidgetTester tester, http.Response Function(Uri url) answer, {String? profile}) async {
      final asked = <Uri>[];
      final client = _client(
        MockClient((request) async {
          asked.add(request.url);
          return answer(request.url);
        }),
      );
      await pumpInk(
        tester,
        MultiProvider(
          providers: [
            Provider<ThreadsDirectClient>.value(value: client),
            Provider<ThreadsAccountsStore>.value(value: accounts),
            Provider<ThreadsLikesStore>.value(value: likes),
          ],
          child: ThreadsSearchScreen(query: 'flutter', profile: profile),
        ),
        theme: xLookLightsOutTheme(null),
      );
      await tester.pumpAndSettle();
      return asked;
    }

    http.Response page(Uri url) => http.Response(
      threadsPage([
        [threadsPostJson(url.queryParameters['filter'] == 'recent' ? '9' : '8', text: 'flutter news')],
      ]),
      200,
      headers: {'content-type': 'text/html; charset=utf-8'},
    );

    testWidgets('shows the top posts as Threads cards, and Recent when chosen', (tester) async {
      final asked = await pump(tester, page);

      expect(find.byType(ThreadsPostCard), findsOneWidget);
      expect(asked, hasLength(1));
      expect(asked.single.queryParameters.containsKey('filter'), isFalse);

      await tester.tap(find.text(L10n.current.recent));
      await tester.pumpAndSettle();
      expect(asked, hasLength(2));
      expect(asked.last.queryParameters['filter'], 'recent');
      expect(find.byType(ThreadsPostCard), findsOneWidget);
    });

    testWidgets('says so when nothing is found', (tester) async {
      await pump(tester, (_) => http.Response(threadsPage([]), 200));

      expect(find.text(L10n.current.no_results), findsOneWidget);
    });

    testWidgets('a failed read offers to try again', (tester) async {
      await pump(tester, (_) => http.Response('', 500));

      expect(find.byType(ReaderFailureNotice), findsOneWidget);
    });

    testWidgets('words that also spell a handle offer that profile on top', (tester) async {
      await pump(tester, page, profile: 'flutter');

      expect(find.text(L10n.current.plugin_threads_open_handle('flutter')), findsOneWidget);
    });
  });
}
