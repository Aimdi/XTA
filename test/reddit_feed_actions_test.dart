import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/reddit/reddit_actions.dart';

Widget _app(Widget child) {
  return PrefService(
    service: PrefServiceCache(),
    child: MaterialApp(
      localizationsDelegates: const [
        L10n.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: L10n.delegate.supportedLocales,
      home: Scaffold(appBar: AppBar(actions: [child])),
    ),
  );
}

void main() {
  testWidgets('Reddit chrome has search, not a plus next to it', (
    tester,
  ) async {
    await tester.pumpWidget(_app(const RedditFeedActions()));
    await tester.pumpAndSettle();

    expect(find.byIcon(Icons.search), findsOneWidget);
    expect(find.byIcon(Icons.add), findsNothing);
    expect(find.byIcon(Icons.list), findsOneWidget);
    expect(find.byIcon(Icons.more_vert), findsOneWidget);
  });

  testWidgets('overflow offers settings, not Reddit sign-in', (tester) async {
    await tester.pumpWidget(_app(const RedditFeedActions()));
    await tester.pumpAndSettle();

    await tester.tap(find.byIcon(Icons.more_vert));
    await tester.pumpAndSettle();

    expect(find.text('Sign in to Reddit'), findsNothing);
    expect(find.text('Reddit · Settings'), findsOneWidget);
    expect(find.text('Best available'), findsOneWidget);
    expect(find.text('Without an account'), findsOneWidget);
  });
}
