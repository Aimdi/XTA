import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:infinite_scroll_pagination/infinite_scroll_pagination.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/tweet/quotes_screen.dart';
import 'package:xta/user.dart';
import 'package:xta/utils/paging.dart';

Widget _app(Widget child) {
  return MaterialApp(
    locale: const Locale('en'),
    localizationsDelegates: const [
      L10n.delegate,
      GlobalMaterialLocalizations.delegate,
      GlobalWidgetsLocalizations.delegate,
      GlobalCupertinoLocalizations.delegate,
    ],
    supportedLocales: L10n.delegate.supportedLocales,
    home: Scaffold(body: child),
  );
}

void main() {
  testWidgets(
    'the retweets tab starts its first fetch without mounting a paged list',
    (tester) async {
      var fetches = 0;
      final started = Completer<CursorPage<String, UserWithExtra>>();

      await tester.pumpWidget(
        _app(
          RetweetersList(
            tweetId: '1',
            loadPage: (_) {
              fetches++;
              return started.future;
            },
          ),
        ),
      );
      await tester.pump();
      await tester.pump();

      expect(fetches, 1);
      expect(find.byType(CircularProgressIndicator), findsOneWidget);
      expect(find.byType(PagedListView), findsNothing);
    },
  );

  testWidgets(
    'an empty first page leaves the spinner for the empty message',
    (tester) async {
      await tester.pumpWidget(
        _app(
          RetweetersList(
            tweetId: '1',
            loadPage: (_) async => (items: <UserWithExtra>[], nextCursor: null),
          ),
        ),
      );
      await tester.pump();
      await tester.pump();

      expect(find.byType(CircularProgressIndicator), findsNothing);
      expect(find.byType(PagedListView), findsNothing);
      expect(
        find.text("Couldn't find anyone who retweeted this post!"),
        findsOneWidget,
      );
    },
  );

  testWidgets('a failed first page leaves the spinner for the error', (
    tester,
  ) async {
    await tester.pumpWidget(
      _app(
        RetweetersList(
          tweetId: '1',
          loadPage: (_) async => throw Exception('nope'),
        ),
      ),
    );
    await tester.pump();
    await tester.pump();

    expect(find.byType(CircularProgressIndicator), findsNothing);
    expect(find.byType(PagedListView), findsNothing);
    expect(find.textContaining('Unable to load who retweeted this post'), findsOneWidget);
  });
}
