import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/ui/errors.dart';

/// A phone-sized viewport, so "does this fit" is a real question rather than
/// one the default 800x600 test surface answers generously.
const _phone = Size(360, 640);

Widget _app(Widget child, {required double textScale}) {
  return MediaQuery(
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
}

Future<void> _pumpAt(WidgetTester tester, Widget child, double textScale) async {
  tester.view.physicalSize = _phone;
  tester.view.devicePixelRatio = 1.0;
  addTearDown(tester.view.reset);

  await tester.pumpWidget(_app(child, textScale: textScale));
  await tester.pumpAndSettle();
}

const _longDetails =
    'HTTP 403 from both public hosts. The request was refused before it reached the '
    'listing, which usually passes on its own but can also mean this reader has been throttled.';

void main() {
  group('an error screen at twice the text size', () {
    // The layout was a centred Column. Given less height than it wanted it ran
    // its children off the bottom edge, and the last child is the retry button
    // — so the one control on the screen went missing exactly for the readers
    // who had turned the text up.
    testWidgets('does not overflow, and keeps its actions reachable', (tester) async {
      await _pumpAt(
        tester,
        ActionableErrorWidget(
          emoji: '🚧',
          title: 'Reddit refused the request',
          details: _longDetails,
          actions: [FilledButton(onPressed: () {}, child: const Text('Retry'))],
        ),
        2.0,
      );

      expect(tester.takeException(), isNull);

      await tester.scrollUntilVisible(find.text('Retry'), 80, scrollable: find.byType(Scrollable).first);
      expect(find.text('Retry'), findsOneWidget);
    });

    testWidgets('the emoji screen keeps both of its buttons', (tester) async {
      await _pumpAt(
        tester,
        EmojiErrorWidget(
          emoji: '🤔',
          message: 'Page not found',
          errorMessage: _longDetails,
          onRetry: () {},
          retryText: 'Try again',
        ),
        2.0,
      );

      expect(tester.takeException(), isNull);

      final scrollable = find.byType(Scrollable).first;
      await tester.scrollUntilVisible(find.text('Try again'), 80, scrollable: scrollable);
      expect(find.text('Try again'), findsOneWidget);
    });
  });

  testWidgets('at the ordinary text size it still centres rather than scrolling', (tester) async {
    await _pumpAt(
      tester,
      const ActionableErrorWidget(emoji: '🔌', title: 'Offline', details: 'No connection.', actions: []),
      1.0,
    );

    expect(tester.takeException(), isNull);

    final position = tester.widget<Scrollable>(find.byType(Scrollable).first).controller?.position;
    expect(position?.maxScrollExtent ?? 0, 0, reason: 'nothing to scroll when everything fits');
  });
}
