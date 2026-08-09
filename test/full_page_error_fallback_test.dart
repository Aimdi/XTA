import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/ui/errors.dart';

/// A phone-sized viewport, so "does this fit" is a real question.
const _phone = Size(360, 640);

class _PluginException implements Exception {
  final String message;
  _PluginException(this.message);

  @override
  String toString() => message;
}

Widget _app(Widget child, {double textScale = 1.0}) => MediaQuery(
  data: MediaQueryData(size: _phone, textScaler: TextScaler.linear(textScale)),
  child: MaterialApp(
    localizationsDelegates: const [
      L10n.delegate,
      GlobalMaterialLocalizations.delegate,
      GlobalWidgetsLocalizations.delegate,
      GlobalCupertinoLocalizations.delegate,
    ],
    supportedLocales: L10n.delegate.supportedLocales,
    home: Scaffold(body: child),
  ),
);

Future<void> _pump(WidgetTester tester, Widget child, {double textScale = 1.0}) async {
  tester.view.physicalSize = _phone;
  tester.view.devicePixelRatio = 1.0;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(_app(child, textScale: textScale));
  await tester.pumpAndSettle();
}

void main() {
  group('the error screen every plugin falls through to', () {
    // Every plugin exception is a plain `implements Exception`, so none match
    // the SocketException/TwitterError special cases — they all land here, and
    // every plugin passes stackTrace: null.
    testWidgets('never prints the word "null" under the message', (tester) async {
      await _pump(
        tester,
        FullPageErrorWidget(
          error: _PluginException('Could not reach Bluesky'),
          stackTrace: null,
          prefix: 'Unable to load the feed',
          onRetry: () {},
        ),
      );

      expect(find.text('null'), findsNothing);
      expect(find.text('Could not reach Bluesky'), findsOneWidget);
    });

    testWidgets('keeps Retry reachable when the details are long', (tester) async {
      var retried = false;
      await _pump(
        tester,
        FullPageErrorWidget(
          error: _PluginException('HTTP 502 from the instance. ' * 40),
          stackTrace: null,
          prefix: 'Unable to load the feed',
          onRetry: () => retried = true,
        ),
      );

      final retry = find.text(L10n.current.retry);
      await tester.scrollUntilVisible(retry, 200);
      await tester.tap(retry);

      expect(retried, isTrue);
    });

    // The readers who turned the text up are exactly the ones the old 500px
    // cap clipped the button away from.
    testWidgets('keeps Retry reachable at doubled text size', (tester) async {
      var retried = false;
      await _pump(
        tester,
        FullPageErrorWidget(
          error: _PluginException('Could not reach the instance'),
          stackTrace: null,
          prefix: 'Unable to load the feed',
          onRetry: () => retried = true,
        ),
        textScale: 2.0,
      );

      final retry = find.text(L10n.current.retry);
      await tester.scrollUntilVisible(retry, 200);
      await tester.tap(retry);

      expect(retried, isTrue);
    });

    testWidgets('shows no retry row when there is nothing to retry', (tester) async {
      await _pump(
        tester,
        FullPageErrorWidget(error: _PluginException('gone'), stackTrace: null, prefix: 'Unable to load'),
      );

      expect(find.text(L10n.current.retry), findsNothing);
    });
  });
}
