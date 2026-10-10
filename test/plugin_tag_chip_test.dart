import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/plugin_tag_chip.dart';

Future<void> _pump(WidgetTester tester, Widget chip, {double width = 240}) => tester.pumpWidget(
  MaterialApp(
    home: Scaffold(
      body: Align(
        alignment: Alignment.topLeft,
        child: SizedBox(
          width: width,
          child: Wrap(children: [chip]),
        ),
      ),
    ),
  ),
);

RenderParagraph _paragraph(WidgetTester tester) => tester.renderObject<RenderParagraph>(
  find.descendant(of: find.byType(PluginTagChip), matching: find.byType(RichText)).first,
);

void main() {
  testWidgets('a long tag and its translation wrap instead of running off the edge', (tester) async {
    await _pump(
      tester,
      PluginTagChip(label: '#マルチャーナ(勝利の女神:NIKKE)', detail: 'Marciana (NIKKE)', onPressed: () {}),
      width: 360,
    );
    expect(tester.takeException(), isNull);
    final paragraph = _paragraph(tester);
    expect(paragraph.didExceedMaxLines, isFalse);
    expect(tester.getSize(find.byType(PluginTagChip)).width, lessThanOrEqualTo(360));
    expect(paragraph.size.height, greaterThan(paragraph.preferredLineHeight * 1.5));
  });

  testWidgets('a short tag stays one line with a full-size touch target', (tester) async {
    var taps = 0;
    var holds = 0;
    await _pump(
      tester,
      PluginTagChip(label: 'solo', detail: '4.33M', onPressed: () => taps++, onLongPress: () => holds++),
      width: 400,
    );
    expect(tester.getSize(find.byType(ActionChip)).height, greaterThanOrEqualTo(48));
    final paragraph = _paragraph(tester);
    expect(paragraph.size.height, lessThan(paragraph.preferredLineHeight * 1.5));
    await tester.tap(find.byType(PluginTagChip));
    await tester.longPress(find.byType(PluginTagChip));
    expect((taps, holds), (1, 1));
  });

  testWidgets('a weak tag is dashed and unfilled, at full colour and touch target', (tester) async {
    await _pump(tester, PluginTagChip(label: 'twin tails', kind: PluginTagKind.general, weak: true, onPressed: () {}));
    final chip = tester.widget<ActionChip>(find.byType(ActionChip));
    final scheme = Theme.of(tester.element(find.byType(ActionChip))).colorScheme;
    final colour = pluginTagKindColor(PluginTagKind.general, scheme);

    expect(chip.backgroundColor, Colors.transparent);
    expect(chip.side?.color, colour);
    expect(chip.shape.runtimeType, isNot(RoundedRectangleBorder));
    // The chip re-applies its side through copyWith; the dashes must survive it.
    expect(chip.shape!.copyWith(side: const BorderSide()).runtimeType, chip.shape.runtimeType);
    Color? labelColour;
    _paragraph(tester).text.visitChildren((span) {
      if (span is TextSpan && span.text == 'twin tails') labelColour = span.style?.color;
      return true;
    });
    expect(labelColour, colour);
    expect(tester.getSize(find.byType(ActionChip)).height, greaterThanOrEqualTo(48));
  });
}
