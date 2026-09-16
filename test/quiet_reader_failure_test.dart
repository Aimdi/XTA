import 'dart:io' show SocketException;

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:visibility_detector/visibility_detector.dart';
import 'package:xta/catcher/exceptions.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/ui/rate_limit_retry.dart';
import 'package:xta/ui/reader_failure.dart';
import 'package:xta/utils/read_recovery.dart';

Future<void> _pump(WidgetTester tester, Widget child, {double scale = 1}) async {
  await tester.pumpWidget(
    MaterialApp(
      localizationsDelegates: const [
        L10n.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: L10n.delegate.supportedLocales,
      builder: (context, child) => MediaQuery(
        data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(scale)),
        child: child!,
      ),
      home: Scaffold(body: child),
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  setUp(() {
    VisibilityDetectorController.instance.updateInterval = Duration.zero;
    ReadRecovery.online = false;
  });
  tearDown(() {
    VisibilityDetectorController.instance.updateInterval = const Duration(milliseconds: 500);
    ReadRecovery.online = false;
  });

  testWidgets('failure and cache details stay hidden until requested, with one retry', (tester) async {
    var retries = 0;
    await _pump(
      tester,
      ReadRecovery(
        recoverableFailure: () => const SocketException('offline'),
        retry: () => retries++,
        child: Column(
        children: [
          ReaderFailureNotice(
            error: const SocketException('offline'),
            contextMessage: 'Cached yesterday',
            onRetry: () => retries++,
          ),
          const Text('Loaded posts'),
        ],
        ),
      ),
    );
    expect(find.textContaining(L10n.current.reader_connection_failed), findsNothing);
    expect(find.text('Cached yesterday'), findsNothing);
    expect(find.text(L10n.current.diagnostics), findsNothing);
    expect(find.text('Loaded posts'), findsOneWidget);
    expect(find.byType(ReadRecovery), findsOneWidget);
    await tester.tap(find.byTooltip(L10n.current.more_info));
    await tester.pumpAndSettle();
    expect(find.textContaining(L10n.current.reader_connection_failed), findsOneWidget);
    expect(find.text('Cached yesterday'), findsOneWidget);
    expect(find.text(L10n.current.diagnostics), findsOneWidget);
    expect(find.byType(ReadRecovery), findsOneWidget);
    final sheet = find.byType(BottomSheet);
    await tester.tap(find.descendant(of: sheet, matching: find.text(L10n.current.retry)));
    await tester.pumpAndSettle();
    expect(retries, 1);
    expect(sheet, findsNothing);
    expect(find.text('Loaded posts'), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('session help remains available without a persistent sign-in prompt', (tester) async {
    var retries = 0;
    await _pump(tester, ReaderFailureNotice(error: NoAccountAvailableException(), onRetry: () => retries++));
    expect(find.textContaining(L10n.current.reader_sign_in_needed), findsNothing);
    expect(find.text(L10n.current.add_account), findsNothing);
    await tester.tap(find.byTooltip(L10n.current.more_info));
    await tester.pumpAndSettle();
    expect(find.textContaining(L10n.current.reader_sign_in_needed), findsOneWidget);
    expect(find.text(L10n.current.add_account), findsOneWidget);
    await tester.tap(find.byTooltip(L10n.current.close));
    await tester.pumpAndSettle();
    expect(find.byType(BottomSheet), findsNothing);
    expect(retries, 0);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('known rate limits block both retry controls while details stay available', (tester) async {
    var retries = 0;
    final deadline = DateTime.now().add(const Duration(minutes: 5)).millisecondsSinceEpoch ~/ 1000;
    await _pump(
      tester,
      ReaderFailureNotice(
        error: HttpException(http.Response('', 429, headers: {'x-rate-limit-reset': '$deadline'})),
        onRetry: () => retries++,
      ),
    );
    expect(find.textContaining(L10n.current.rate_limited_title), findsNothing);
    await tester.tap(find.byTooltip(L10n.current.more_info));
    await tester.pumpAndSettle();
    expect(find.textContaining(L10n.current.rate_limited_title), findsOneWidget);
    final buttons = find.descendant(
      of: find.byType(RateLimitRetryButton),
      matching: find.byWidgetPredicate((widget) => widget is TextButton),
    );
    expect(buttons, findsNWidgets(2));
    for (final button in tester.widgetList<TextButton>(buttons)) {
      expect(button.onPressed, isNull);
    }
    expect(retries, 0);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('controls and scrollable details fit narrow screens with large text', (tester) async {
    tester.view.physicalSize = const Size(320, 480);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    var retries = 0;
    await _pump(
      tester,
      ReaderFailureNotice(
        error: NoAccountAvailableException(),
        onRetry: () => retries++,
        contextMessage: 'Cached posts. ' * 80,
      ),
      scale: 2,
    );
    expect(tester.takeException(), isNull);
    await tester.tap(find.byTooltip(L10n.current.more_info));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    final retry = find.descendant(of: find.byType(BottomSheet), matching: find.text(L10n.current.retry));
    await tester.scrollUntilVisible(retry, 200, scrollable: find.byType(Scrollable).last, maxScrolls: 80);
    await tester.tap(retry);
    await tester.pumpAndSettle();
    expect(retries, 1);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('parent recovery can own retries, and dismiss does not retry', (tester) async {
    var retries = 0;
    var dismissals = 0;
    await _pump(
      tester,
      ReaderFailureNotice(
        error: const SocketException('offline'),
        onRetry: () => retries++,
        onDismiss: () => dismissals++,
      ),
    );
    expect(find.byType(ReadRecovery), findsNothing);
    await tester.tap(find.byTooltip(L10n.current.close));
    expect(dismissals, 1);
    expect(retries, 0);
    await tester.tap(find.text(L10n.current.retry));
    expect(retries, 1);
    await tester.pumpWidget(const SizedBox.shrink());
  });
}
