import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/client/errors.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/profile/profile_load_failure.dart';
import 'package:xta/ui/reader_failure.dart';

TwitterError _x(int code) =>
    TwitterError(uri: 'https://x.com/i/api/graphql/id/UserByScreenName', code: code, message: '');

Future<void> _pump(WidgetTester tester, Object error) => tester.pumpWidget(
  MaterialApp(
    localizationsDelegates: const [
      L10n.delegate,
      GlobalMaterialLocalizations.delegate,
      GlobalWidgetsLocalizations.delegate,
      GlobalCupertinoLocalizations.delegate,
    ],
    supportedLocales: const [Locale('en')],
    home: Scaffold(
      body: ProfileLoadFailure(error: error, onRetry: () {}),
    ),
  ),
);

void main() {
  testWidgets('an account that does not exist says so instead of offering a retry', (tester) async {
    await _pump(tester, _x(50));
    await tester.pumpAndSettle();
    expect(find.text('User not found'), findsOneWidget);
    expect(find.text('Retry'), findsNothing);
  });

  testWidgets('a suspended or private account says which', (tester) async {
    await _pump(tester, _x(63));
    await tester.pumpAndSettle();
    expect(find.text('Account suspended'), findsOneWidget);
    await _pump(tester, _x(22));
    await tester.pumpAndSettle();
    expect(find.text('Private profile'), findsOneWidget);
    expect(find.byIcon(Icons.lock_outline), findsOneWidget);
  });

  testWidgets('a failure X did not explain can be retried', (tester) async {
    await _pump(tester, const SocketException('offline'));
    await tester.pumpAndSettle();
    expect(find.text('Retry'), findsOneWidget);
    await _pump(tester, _x(239));
    await tester.pumpAndSettle();
    expect(find.text('Retry'), findsOneWidget, reason: 'a stale guest token is fixed by trying again');
  });

  test("the failure details give X's own answer", () async {
    final l10n = await L10n.load(const Locale('en'));
    expect(readFailureMessage(l10n, _x(50)), 'User not found');
    expect(readFailureMessage(l10n, StateError('unexpected')), l10n.oops_something_went_wrong);
  });
}
