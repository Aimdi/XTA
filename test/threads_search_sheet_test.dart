import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/threads/threads_direct_client.dart';
import 'package:xta/plugins/threads/threads_profile_screen.dart';
import 'package:xta/plugins/threads/threads_search_screen.dart';
import 'package:xta/plugins/threads/threads_search_sheet.dart';
import 'package:xta/plugins/threads/threads_store.dart';
import 'package:xta/plugins/threads/threads_thread_screen.dart';
import 'package:xta/ui/x_look_theme.dart';

import 'support/feed_ink_harness.dart';
import 'support/opened_pages.dart';

/// docs/specs/threads-x-parity-3.md, Search: the sheet opens what a guest's query names, by the rules the results
/// page follows.
void main() {
  /// The sheet opened as a guest, in a navigator under the providers as the app has them.
  Future<OpenedPages> openSheet(WidgetTester tester) async {
    final opened = OpenedPages();
    final accounts = ThreadsAccountsStore();
    addTearDown(accounts.destroy);
    final direct = ThreadsDirectClient(
      PrefServiceCache(cache: {optionPluginThreadsDirectCooldownUntil: '', optionPluginThreadsUserIds: '{}'}),
      minGap: Duration.zero,
      httpClient: MockClient((_) async => http.Response('', 404)),
    );
    await pumpInk(
      tester,
      MultiProvider(
        providers: [
          Provider<ThreadsDirectClient>.value(value: direct),
          Provider<ThreadsAccountsStore>.value(value: accounts),
        ],
        child: Navigator(
          observers: [opened],
          onGenerateRoute: (_) => MaterialPageRoute<void>(
            builder: (context) => Scaffold(
              body: TextButton(onPressed: () => showThreadsSearchSheet(context), child: const Text('search')),
            ),
          ),
        ),
      ),
      theme: xLookLightsOutTheme(null),
    );
    await tester.tap(find.text('search'));
    await tester.pumpAndSettle();
    return opened;
  }

  for (final (typed, page) in [
    ('#Keramik', isA<ThreadsSearchScreen>().having((screen) => (screen.query, screen.tag), 'tag', ('Keramik', true))),
    ('@Maya.Makes', isA<ThreadsProfileScreen>().having((screen) => screen.username, 'username', 'maya.makes')),
    (
      'glaze recipes',
      isA<ThreadsSearchScreen>().having((screen) => (screen.query, screen.tag, screen.profile), 'words', (
        'glaze recipes',
        false,
        null,
      )),
    ),
    (
      'https://www.threads.com/@maya.makes/post/DAbc123',
      isA<ThreadsThreadScreen>().having((screen) => screen.post.handle, 'handle', 'maya.makes'),
    ),
  ]) {
    testWidgets('a guest typing $typed gets the page it names', (tester) async {
      final opened = await openSheet(tester);

      await tester.enterText(find.byType(TextField), typed);
      await tester.testTextInput.receiveAction(TextInputAction.search);
      await tester.idle();

      expect(opened.take(), page);
      await tester.pumpAndSettle();
      expect(find.byType(TextField), findsNothing, reason: 'the sheet closed behind the page');
    });
  }
}
