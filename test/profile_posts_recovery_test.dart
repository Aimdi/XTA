import 'dart:async';
import 'dart:io' show SocketException;

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:infinite_scroll_pagination/infinite_scroll_pagination.dart';
import 'package:pref/pref.dart';
import 'package:visibility_detector/visibility_detector.dart';
import 'package:xta/catcher/exceptions.dart';
import 'package:xta/client/client.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/profile/_tweets.dart';
import 'package:xta/ui/reader_failure.dart';
import 'package:xta/user.dart';
import 'package:xta/utils/paging.dart';
import 'package:xta/utils/read_recovery.dart';

Widget _app(Widget body) => MaterialApp(
  locale: const Locale('de'),
  localizationsDelegates: const [
    L10n.delegate,
    GlobalMaterialLocalizations.delegate,
    GlobalWidgetsLocalizations.delegate,
    GlobalCupertinoLocalizations.delegate,
  ],
  supportedLocales: L10n.delegate.supportedLocales,
  home: Scaffold(body: body),
);

/// The posts tab of the screenshot: "Alle" filter, first page from X.
Widget _postsTab(Future<TweetStatus> Function(String?) fetch) => _app(
  ProfileTweets(
    user: UserWithExtra.fromArguments(idStr: '42', screenName: 'someone', name: 'Someone'),
    type: 'profile',
    includeReplies: false,
    pinnedTweets: const [],
    pref: PrefServiceCache(),
    fetchTweets: fetch,
  ),
);

final _empty = TweetStatus(chains: const [], cursorBottom: null, cursorTop: null);

/// A loader that fails [failures] times with [error], then answers.
Future<TweetStatus> Function(String?) _failing(Object error, int failures, List<String?> calls) => (cursor) async {
  calls.add(cursor);
  if (calls.length <= failures) throw error;
  return _empty;
};

Future<void> _pumpUntil(WidgetTester tester, Finder finder) async {
  for (var second = 0; second < 120 && finder.evaluate().isEmpty; second++) {
    await tester.pump(const Duration(seconds: 1));
  }
  expect(finder, findsWidgets);
}

void main() {
  setUp(() {
    VisibilityDetectorController.instance.updateInterval = Duration.zero;
    ReadRecovery.online = null;
  });
  tearDown(() {
    VisibilityDetectorController.instance.updateInterval = const Duration(milliseconds: 500);
    ReadRecovery.online = null;
  });

  // Before: only connection, timeout and 5xx were retried, at most three times,
  // and only after Android had reported the network. Everything else in this
  // list stayed on "Erneut versuchen" until tapped.
  final failures = <String, Object>{
    'connection before any network report': const SocketException('down'),
    'unparseable response (unknown)': const FormatException('html instead of json'),
    'HTTP 403 (unavailable)': HttpException(http.Response('', 403)),
    'request signing (transactionUnavailable)': TransactionIdUnavailableException(Exception('shape')),
  };
  for (final MapEntry(key: name, value: error) in failures.entries) {
    testWidgets('a failed first page of posts recovers on its own: $name', (tester) async {
      final calls = <String?>[];
      await tester.pumpWidget(_postsTab(_failing(error, 3, calls)));
      await _pumpUntil(tester, find.byType(ReaderFailureNotice));
      // The tab became visible in the frame that drew the notice; the countdown follows.
      await tester.pump();
      expect(find.byType(ReadRetryCountdown), findsOneWidget);
      expect(find.text(L10n.current.retry), findsOneWidget);
      await _pumpUntil(tester, find.text(L10n.current.could_not_find_any_tweets_by_this_user));
      expect(calls, [null, null, null, null]);
      expect(find.text(L10n.current.could_not_find_any_tweets_by_this_user), findsOneWidget);
      expect(find.byType(ReaderFailureNotice), findsNothing);
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox.shrink());
    });
  }

  testWidgets('a posts tab that needs sign-in keeps the manual notice', (tester) async {
    final calls = <String?>[];
    await tester.pumpWidget(_postsTab(_failing(NoAccountAvailableException(), 99, calls)));
    await tester.pump(const Duration(minutes: 10));
    expect(calls, hasLength(1));
    expect(find.byType(ReaderFailureNotice), findsOneWidget);
    expect(find.byType(ReadRetryCountdown), findsNothing);
    expect(find.text(L10n.current.retry), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('a failed next page recovers on its own below the loaded posts', (tester) async {
    var failures = 2;
    final paging = CursorPagingController<String, String>(
      (cursor) async => cursor == null
          ? (items: ['first'], nextCursor: 'more')
          : failures-- > 0
          ? throw TimeoutException('slow')
          : (items: ['second'], nextCursor: null),
    );
    addTearDown(paging.dispose);
    final controller = paging.pagingController;
    await tester.pumpWidget(
      _app(
        PagingReadRecovery(
          controller: controller,
          child: PagingListener<int, String>(
            controller: controller,
            builder: (context, state, fetchNextPage) => PagedListView<int, String>(
              state: state,
              fetchNextPage: fetchNextPage,
              builderDelegate: PagedChildBuilderDelegate(
                itemBuilder: (context, item, index) => SizedBox(height: 40, child: Text(item)),
                newPageErrorIndicatorBuilder: (context) =>
                    ReaderFailureNotice(error: pagingErrorOf(state)?.error ?? state.error, onRetry: fetchNextPage),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.pump();
    await tester.pump();
    expect(find.text('first'), findsOneWidget);
    expect(find.byType(ReadRetryCountdown), findsOneWidget);
    await tester.pump(const Duration(seconds: 2));
    await tester.pump();
    await tester.pump(const Duration(seconds: 5));
    await tester.pump();
    expect(find.text('second'), findsOneWidget);
    expect(find.byType(ReaderFailureNotice), findsNothing);
    await tester.pumpWidget(const SizedBox.shrink());
  });
}
