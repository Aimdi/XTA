import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:timeago/timeago.dart' as timeago;
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/threads/threads_direct_client.dart';
import 'package:xta/plugins/threads/threads_likes_store.dart';
import 'package:xta/plugins/threads/threads_models.dart';
import 'package:xta/plugins/threads/threads_post_card.dart';
import 'package:xta/plugins/threads/threads_store.dart';
import 'package:xta/plugins/threads/threads_thread_screen.dart';
import 'package:xta/ui/conversation_sort.dart';

Widget _app(PrefServiceCache prefs, List<Provider> providers, Widget child, {Locale locale = const Locale('en')}) =>
    PrefService(
      service: prefs,
      child: MultiProvider(
        providers: providers,
        child: MaterialApp(
          locale: locale,
          localizationsDelegates: const [
            L10n.delegate,
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          supportedLocales: L10n.delegate.supportedLocales,
          home: child,
        ),
      ),
    );

Map<String, Object?> _json(String id, {String user = 'zuck', int takenAt = 1767225600}) => {
  'pk': id,
  'code': 'C$id',
  'taken_at': takenAt,
  'caption': {'text': 'post $id by $user'},
  'user': {'username': user, 'full_name': user},
};

String _page(List<List<Map<String, Object?>>> chains) =>
    '<html><script data-sjs>${jsonEncode({
      'edges': [
        for (final chain in chains) {
            'node': {
              'thread_items': [
                for (final post in chain) {'post': post},
              ],
            },
          },
      ],
    })}</script></html>';

/// `@zuck/post/C2` opened, its public page carrying [chains]. [sorts] holds the reader's orders for the session.
Future<void> _pumpConversation(
  WidgetTester tester,
  List<List<Map<String, Object?>>> chains, {
  ConversationSortStore? sorts,
  Locale locale = const Locale('en'),
}) async {
  final prefs = PrefServiceCache(cache: {optionPluginThreadsDirectCooldownUntil: '', optionPluginThreadsUserIds: '{}'});
  final accounts = ThreadsAccountsStore();
  final likes = ThreadsLikesStore(prefs);
  final orders = sorts ?? ConversationSortStore();
  final direct = ThreadsDirectClient(
    prefs,
    minGap: Duration.zero,
    httpClient: MockClient((_) async => http.Response(_page(chains), 200)),
  );
  addTearDown(() {
    accounts.destroy();
    likes.destroy();
    if (sorts == null) orders.destroy();
  });

  await tester.pumpWidget(
    _app(
      prefs,
      [
        Provider<ThreadsAccountsStore>.value(value: accounts),
        Provider<ThreadsLikesStore>.value(value: likes),
        Provider<ThreadsDirectClient>.value(value: direct),
        Provider<ConversationSortStore>.value(value: orders),
      ],
      ThreadsThreadScreen(post: ThreadsPost.linkStub('https://www.threads.net/@zuck/post/C2?igshid=x')),
      locale: locale,
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  timeago.setLocaleMessages('de', timeago.DeMessages());

  testWidgets('a quoting, tagged, chained card fits a narrow phone in German', (tester) async {
    tester.view.physicalSize = const Size(320, 1400);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final prefs = PrefServiceCache();
    final accounts = ThreadsAccountsStore();
    final likes = ThreadsLikesStore(prefs);
    addTearDown(() {
      accounts.destroy();
      likes.destroy();
    });

    await tester.pumpWidget(
      _app(
        prefs,
        [Provider<ThreadsAccountsStore>.value(value: accounts), Provider<ThreadsLikesStore>.value(value: likes)],
        Scaffold(
          body: SingleChildScrollView(
            child: ThreadsPostCard(
              post: ThreadsPost(
                id: '1',
                handle: 'ein.sehr.langer.handle',
                authorName: 'Ein sehr langer Anzeigename hier',
                text: 'Lies das @mosseri',
                url: 'https://www.threads.com/@ein.sehr.langer.handle/post/C1',
                publishedAt: DateTime.now(),
                topicTag: 'Wissenschaftsjournalismus',
                selfThreadCount: 3,
                quoteCount: 2,
                fragments: const [
                  ThreadsTextFragment(ThreadsFragmentKind.text, 'Lies das '),
                  ThreadsTextFragment(ThreadsFragmentKind.mention, '@mosseri', 'mosseri'),
                ],
                quoted: ThreadsPost(
                  id: '2',
                  handle: 'noch.ein.langer.handle',
                  authorName: 'Zitierte Person mit langem Namen',
                  text: 'Das Original',
                  isVerified: true,
                  publishedAt: DateTime.now(),
                ),
              ),
            ),
          ),
        ),
        locale: const Locale('de'),
      ),
    );
    await tester.pump();

    expect(tester.takeException(), isNull);
    expect(find.text('Wissenschaftsjournalismus'), findsOneWidget);
    expect(find.text('Das Original'), findsOneWidget, reason: 'the quoted post is embedded');
    expect(find.text(L10n.current.plugin_threads_show_thread(3)), findsOneWidget);
  });

  testWidgets('the conversation shows context, the author\'s thread and cut reply threads', (tester) async {
    await _pumpConversation(tester, [
      [_json('1', user: 'meta'), _json('2'), _json('3')],
      [_json('4', user: 'a'), _json('5', user: 'b'), _json('6', user: 'a'), _json('7', user: 'b')],
      [_json('8', user: 'c'), _json('9')],
    ]);

    expect(find.text('post 1 by meta'), findsOneWidget, reason: 'what the post answers');
    expect(find.text('post 2 by zuck'), findsOneWidget);
    expect(find.text('post 3 by zuck'), findsOneWidget, reason: 'the author\'s continuation');
    expect(find.text('post 7 by b'), findsNothing, reason: 'long reply threads are cut');
    await tester.scrollUntilVisible(find.text(L10n.current.plugin_threads_more_replies(1)), 200);
    expect(find.text(L10n.current.plugin_threads_more_replies(1)), findsOneWidget);

    await tester.tap(find.text(L10n.current.plugin_threads_more_replies(1)));
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(find.text('post 7 by b'), 200);
    expect(find.text('post 7 by b'), findsOneWidget);

    await tester.scrollUntilVisible(find.text(L10n.current.plugin_threads_author_replies), -200);
    await tester.tap(find.text(L10n.current.plugin_threads_author_replies));
    await tester.pumpAndSettle();
    expect(find.text('post 4 by a'), findsNothing, reason: 'only threads the author replied in');
    await tester.scrollUntilVisible(find.text('post 9 by zuck'), 200);
    expect(find.text('post 9 by zuck'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('replies can be sorted, each reply thread moving whole, and the order holds for the session', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(390, 2400);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final sorts = ConversationSortStore();
    addTearDown(sorts.destroy);
    await _pumpConversation(tester, sorts: sorts, [
      [_json('1', user: 'meta'), _json('2')],
      [_json('4', user: 'a', takenAt: 1767225600), _json('5', user: 'b', takenAt: 1767400000)],
      [_json('8', user: 'c', takenAt: 1767300000)],
    ]);
    double top(String text) => tester.getTopLeft(find.text(text)).dy;

    // Meta's own order first.
    expect(top('post 4 by a'), lessThan(top('post 8 by c')));
    expect(top('post 5 by b'), lessThan(top('post 8 by c')));

    await tester.tap(find.byKey(const ValueKey('threads-thread-sort')));
    await tester.pumpAndSettle();
    await tester.tap(find.text(L10n.current.recent).last);
    await tester.pumpAndSettle();

    expect(sorts.state.replies, ReplySort.recent);
    expect(top('post 8 by c'), lessThan(top('post 4 by a')), reason: 'the newer first reply first');
    expect(top('post 4 by a'), lessThan(top('post 5 by b')), reason: 'a reply thread stays whole');
    expect(tester.takeException(), isNull);
  });

  testWidgets('the replies header, author filter and order fit a narrow phone in German', (tester) async {
    tester.view.physicalSize = const Size(320, 1400);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await _pumpConversation(locale: const Locale('de'), tester, [
      [_json('2')],
      [_json('4', user: 'a'), _json('5')],
    ]);

    expect(find.byKey(const ValueKey('threads-thread-sort')), findsOneWidget);
    expect(find.text(L10n.current.plugin_threads_author_replies), findsOneWidget);
    expect(tester.getSize(find.byKey(const ValueKey('threads-thread-sort'))).height, greaterThanOrEqualTo(48));
    expect(tester.takeException(), isNull);
  });
}
