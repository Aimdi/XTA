import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/ui/conversation_sort.dart';
import 'package:xta/ui/sort_menu_button.dart';

void main() {
  testWidgets('the reply order sits in its own full-width row, closed by a hairline', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: const [
          L10n.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
        ],
        supportedLocales: const [Locale('en')],
        home: Scaffold(
          body: Column(
            children: [
              ReplySortBar(
                child: ReplySortButton(value: ReplySort.relevant, options: xReplySorts, onSelected: (_) {}),
              ),
            ],
          ),
        ),
      ),
    );

    final bar = tester.getRect(find.byType(ReplySortBar));
    final chip = tester.getRect(find.byType(ReplySortButton));
    final divider = tester.getRect(find.byType(Divider));
    expect(bar.width, tester.view.physicalSize.width / tester.view.devicePixelRatio);
    expect(chip.left, lessThan(bar.width / 2), reason: 'the order reads from the start, as on X');
    expect(divider.width, bar.width);
    expect(divider.top, greaterThanOrEqualTo(chip.bottom));
    expect(find.text('Relevant'), findsOneWidget);
  });
}
