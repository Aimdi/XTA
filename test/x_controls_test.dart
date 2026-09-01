import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/ui/x_controls.dart';
import 'package:xta/ui/theme_presets.dart';
import 'package:xta/ui/x_look_theme.dart';

Widget _wrap(Widget child, {ThemeData? theme}) => MaterialApp(
      theme: theme,
      home: Scaffold(body: Padding(padding: const EdgeInsets.all(12), child: child)),
    );

void main() {
  group('XSearchField', () {
    testWidgets('is a pill, not a Material SearchBar', (tester) async {
      final controller = TextEditingController();
      addTearDown(controller.dispose);

      await tester.pumpWidget(_wrap(XSearchField(controller: controller, hintText: 'Search')));
      await tester.pumpAndSettle();

      expect(find.byType(SearchBar), findsNothing);

      final decoration = tester.widget<TextField>(find.byType(TextField)).decoration!;
      final border = decoration.border as OutlineInputBorder;
      expect(border.borderRadius.topLeft.x, greaterThan(100), reason: 'full-round, not a Material rounded rectangle');
      expect(decoration.filled, isTrue);
    });

    testWidgets('offers a clear affordance only while there is a query', (tester) async {
      final controller = TextEditingController();
      addTearDown(controller.dispose);
      var changes = <String>[];

      await tester.pumpWidget(_wrap(XSearchField(
        controller: controller,
        hintText: 'Search',
        onChanged: changes.add,
      )));
      await tester.pumpAndSettle();

      expect(find.byIcon(Icons.cancel), findsNothing);

      await tester.enterText(find.byType(TextField), 'anime');
      await tester.pumpAndSettle();
      expect(changes.last, 'anime');
      expect(find.byIcon(Icons.cancel), findsOneWidget);

      await tester.tap(find.byIcon(Icons.cancel));
      await tester.pumpAndSettle();

      expect(controller.text, isEmpty);
      expect(changes.last, isEmpty);
      expect(find.byIcon(Icons.cancel), findsNothing);
    });

    testWidgets('takes its accent from the X-look tokens when present', (tester) async {
      final controller = TextEditingController();
      addTearDown(controller.dispose);

      await tester.pumpWidget(_wrap(
        XSearchField(controller: controller, hintText: 'Search'),
        theme: xLookLightsOutTheme(null),
      ));
      await tester.pumpAndSettle();

      await tester.tap(find.byType(TextField));
      await tester.pumpAndSettle();

      final decoration = tester.widget<TextField>(find.byType(TextField)).decoration!;
      final focused = decoration.focusedBorder as OutlineInputBorder;
      expect(focused.borderSide.color, XLookTokens.accentBlue);
    });
  });

  group('xPrimaryPillStyle', () {
    for (final entry in <String, ThemeData?>{
      'default': null,
      'x-look lights out': xLookLightsOutTheme(null),
      'fairy forest': fairyForestTheme(null),
    }.entries) {
      testWidgets('${entry.key} renders a stadium, not a rounded rectangle', (tester) async {
        late ButtonStyle style;
        await tester.pumpWidget(_wrap(
          Builder(builder: (context) {
            style = xPrimaryPillStyle(context);
            return const SizedBox();
          }),
          theme: entry.value,
        ));

        expect(style.shape!.resolve({}), isA<StadiumBorder>());
        expect(style.elevation!.resolve({}), 0);
        expect(style.textStyle!.resolve({})!.fontWeight, FontWeight.w700);
      });
    }
  });
}
