import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/tweet/_ExpandableTweetText.dart';

const _long =
    'Ein sehr langer Beitrag über Aktien, Zinsen, Anleihen und noch viel mehr, '
    'der auf einem schmalen Telefon ganz sicher über mehrere Zeilen läuft und '
    'daher gekürzt werden müsste, wenn eine Zeilengrenze gesetzt ist.';

Widget _wrap(Widget child, {double width = 120, TextDirection? direction}) {
  final content = SizedBox(width: width, child: child);
  return MaterialApp(
    localizationsDelegates: const [
      L10n.delegate,
      GlobalMaterialLocalizations.delegate,
      GlobalWidgetsLocalizations.delegate,
      GlobalCupertinoLocalizations.delegate,
    ],
    supportedLocales: L10n.delegate.supportedLocales,
    home: Scaffold(
      body: SingleChildScrollView(
        child: Align(
          alignment: Alignment.topLeft,
          child: direction == null ? content : Directionality(textDirection: direction, child: content),
        ),
      ),
    ),
  );
}

Finder get _showMore => find.text('Show more');

void main() {
  testWidgets('a post that fits shows no expand link', (tester) async {
    await tester.pumpWidget(_wrap(const ExpandableTweetText(textSpans: [TextSpan(text: 'Short one.')], maxLines: 8)));
    await tester.pumpAndSettle();

    expect(_showMore, findsNothing);
  });

  testWidgets('a clipped post offers the link and expands on tap', (tester) async {
    await tester.pumpWidget(_wrap(const ExpandableTweetText(textSpans: [TextSpan(text: _long)], maxLines: 3)));
    await tester.pumpAndSettle();

    expect(_showMore, findsOneWidget);

    await tester.tap(_showMore);
    await tester.pumpAndSettle();

    expect(_showMore, findsNothing);
  });

  // Nested quotes used to open-on-tap from the faded text under "Show more",
  // so tapping near the link navigated away instead of expanding.
  testWidgets('expanding does not fire the open-on-tap callback', (tester) async {
    var opens = 0;
    await tester.pumpWidget(
      _wrap(
        ExpandableTweetText(
          textSpans: const [TextSpan(text: _long)],
          maxLines: 3,
          onTap: () => opens++,
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(_showMore);
    await tester.pumpAndSettle();

    expect(_showMore, findsNothing);
    expect(opens, 0);
  });

  testWidgets('while clipped, taps on the faded text do not open the post', (tester) async {
    var opens = 0;
    await tester.pumpWidget(
      _wrap(
        ExpandableTweetText(
          textSpans: const [TextSpan(text: _long)],
          maxLines: 3,
          onTap: () => opens++,
        ),
      ),
    );
    await tester.pumpAndSettle();

    // Tap the faded body, not the expand link below it.
    final body = tester.getRect(find.byType(ShaderMask));
    await tester.tapAt(body.center);
    await tester.pumpAndSettle();

    expect(opens, 0, reason: 'the fade zone must not steal the expand affordance');
    expect(_showMore, findsOneWidget);
  });

  testWidgets('show more still expands under a parent open-on-tap detector', (tester) async {
    var parentOpens = 0;
    await tester.pumpWidget(
      _wrap(
        GestureDetector(
          behavior: HitTestBehavior.translucent,
          onTap: () => parentOpens++,
          child: const ExpandableTweetText(textSpans: [TextSpan(text: _long)], maxLines: 3),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(_showMore);
    await tester.pumpAndSettle();

    expect(_showMore, findsNothing);
    expect(parentOpens, 0, reason: 'the expand control must win against the quote card wrapper');
  });

  testWidgets('lines are counted at the painted width, not the screen width', (tester) async {
    // The regression: this fits on one line across the 800px test window but
    // needs several in the 120px column it is painted in. Measuring against
    // MediaQuery.size.width therefore reported "not truncated", and the text
    // was silently clipped with no way to expand it.
    await tester.pumpWidget(
      _wrap(
        const ExpandableTweetText(
          textSpans: [TextSpan(text: 'Zinsen, Anleihen und Aktien heute im Überblick')],
          maxLines: 2,
        ),
        width: 120,
      ),
    );
    await tester.pumpAndSettle();

    expect(_showMore, findsOneWidget);

    // The same text in a wide column genuinely fits, and must not be clipped.
    await tester.pumpWidget(
      _wrap(const ExpandableTweetText(textSpans: [TextSpan(text: 'Short one.')], maxLines: 2), width: 780),
    );
    await tester.pumpAndSettle();

    expect(_showMore, findsNothing);
  });

  testWidgets('the inherited text style is taken into account', (tester) async {
    // Spans carry no size of their own; a large DefaultTextStyle must be able
    // to push the text past the cap.
    await tester.pumpWidget(
      _wrap(
        const DefaultTextStyle(
          style: TextStyle(fontSize: 40),
          child: ExpandableTweetText(textSpans: [TextSpan(text: 'Zinsen und Anleihen')], maxLines: 1),
        ),
        width: 160,
      ),
    );
    await tester.pumpAndSettle();

    expect(_showMore, findsOneWidget);
  });

  testWidgets('maxLines null never clips or offers the link', (tester) async {
    await tester.pumpWidget(_wrap(const ExpandableTweetText(textSpans: [TextSpan(text: _long)], maxLines: null)));
    await tester.pumpAndSettle();

    expect(_showMore, findsNothing);
  });

  testWidgets('a larger text scale can clip text that fits at 1x', (tester) async {
    await tester.pumpWidget(
      MediaQuery(
        data: const MediaQueryData(textScaler: TextScaler.linear(3.0)),
        child: _wrap(
          const ExpandableTweetText(textSpans: [TextSpan(text: 'Zinsen und Anleihen heute')], maxLines: 1),
          width: 200,
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(_showMore, findsOneWidget);
  });

  testWidgets('right-to-left text is measured without throwing', (tester) async {
    await tester.pumpWidget(
      _wrap(
        const ExpandableTweetText(
          textSpans: [TextSpan(text: 'مرحبا بالعالم هذا منشور طويل جدا عن الأسهم والسندات')],
          maxLines: 2,
        ),
        direction: TextDirection.rtl,
      ),
    );
    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
    expect(_showMore, findsOneWidget);
  });

  // A post's spans can carry WidgetSpans now — the alt-text badge, inline
  // chips. Counting lines with a bare TextPainter over one of those was a
  // null-check crash inside WidgetSpan.build, and it took the whole timeline
  // down the first time such a post scrolled in.
  testWidgets('a post carrying a WidgetSpan measures instead of crashing', (tester) async {
    await tester.pumpWidget(
      _wrap(
        const ExpandableTweetText(
          textSpans: [
            TextSpan(text: 'Look: '),
            WidgetSpan(child: Icon(Icons.image, size: 14), alignment: PlaceholderAlignment.middle),
            TextSpan(text: ' a badge in the text.'),
          ],
          maxLines: 8,
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
    expect(_showMore, findsNothing);
  });

  testWidgets('a long post still truncates with a WidgetSpan aboard', (tester) async {
    await tester.pumpWidget(
      _wrap(
        const ExpandableTweetText(
          textSpans: [
            WidgetSpan(child: Icon(Icons.image, size: 14), alignment: PlaceholderAlignment.middle),
            TextSpan(text: _long),
          ],
          maxLines: 3,
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
    expect(_showMore, findsOneWidget);
  });
}
