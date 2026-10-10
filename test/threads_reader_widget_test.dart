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

Map<String, Object?> _json(String id, {String user = 'zuck'}) => {
  'pk': id,
  'code': 'C$id',
  'taken_at': 1767225600,
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
    final prefs = PrefServiceCache(
      cache: {optionPluginThreadsDirectCooldownUntil: '', optionPluginThreadsUserIds: '{}'},
    );
    final accounts = ThreadsAccountsStore();
    final likes = ThreadsLikesStore(prefs);
    final direct = ThreadsDirectClient(
      prefs,
      minGap: Duration.zero,
      httpClient: MockClient(
        (_) async => http.Response(
          _page([
            [_json('1', user: 'meta'), _json('2'), _json('3')],
            [_json('4', user: 'a'), _json('5', user: 'b'), _json('6', user: 'a'), _json('7', user: 'b')],
            [_json('8', user: 'c'), _json('9')],
          ]),
          200,
        ),
      ),
    );
    addTearDown(() {
      accounts.destroy();
      likes.destroy();
    });

    await tester.pumpWidget(
      _app(prefs, [
        Provider<ThreadsAccountsStore>.value(value: accounts),
        Provider<ThreadsLikesStore>.value(value: likes),
        Provider<ThreadsDirectClient>.value(value: direct),
      ], ThreadsThreadScreen(post: ThreadsPost.linkStub('https://www.threads.net/@zuck/post/C2?igshid=x'))),
    );
    await tester.pumpAndSettle();

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
}
