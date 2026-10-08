import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/ui/snack_bar_policy.dart';

Widget _app({bool accessibleNavigation = false}) => MaterialApp(
  builder: (context, child) => MediaQuery(
    data: MediaQuery.of(context).copyWith(accessibleNavigation: accessibleNavigation),
    child: XtaScaffoldMessenger(child: child!),
  ),
  home: const Scaffold(body: SizedBox.expand()),
);

ScaffoldMessengerState _messenger(WidgetTester tester) => ScaffoldMessenger.of(tester.element(find.byType(Scaffold)));

SnackBar _undo() => SnackBar(
  content: const Text('saved'),
  duration: const Duration(seconds: 8),
  action: SnackBarAction(label: 'Undo', onPressed: () {}),
);

void main() {
  testWidgets('a message leaves after three seconds instead of four', (tester) async {
    await tester.pumpWidget(_app());
    _messenger(tester).showSnackBar(const SnackBar(content: Text('failed')));
    await tester.pumpAndSettle();
    expect(find.text('failed'), findsOneWidget);

    await tester.pump(kSnackBarDuration);
    await tester.pumpAndSettle();

    expect(find.text('failed'), findsNothing);
  });

  testWidgets('an Undo bar no longer stays until it is tapped', (tester) async {
    await tester.pumpWidget(_app());
    _messenger(tester).showSnackBar(_undo());
    await tester.pumpAndSettle();
    await tester.pump(kSnackBarDuration);
    expect(find.text('saved'), findsOneWidget);

    await tester.pump(kSnackBarActionDuration - kSnackBarDuration);
    await tester.pumpAndSettle();

    expect(find.text('saved'), findsNothing);
  });

  testWidgets('with a screen reader an Undo bar waits for the reader', (tester) async {
    await tester.pumpWidget(_app(accessibleNavigation: true));
    _messenger(tester).showSnackBar(_undo());
    await tester.pumpAndSettle();
    await tester.pump(const Duration(seconds: 30));
    await tester.pumpAndSettle();

    expect(find.text('saved'), findsOneWidget);
    _messenger(tester).removeCurrentSnackBar();
  });

  testWidgets('a burst of errors shows the latest at once instead of queueing them', (tester) async {
    await tester.pumpWidget(_app());
    for (final message in ['first', 'second', 'third']) {
      _messenger(tester).showSnackBar(SnackBar(content: Text(message)));
    }
    await tester.pumpAndSettle();

    expect(find.text('third'), findsOneWidget);
    expect(find.text('first'), findsNothing);

    await tester.pump(kSnackBarDuration);
    await tester.pumpAndSettle();
    expect(find.text('first'), findsNothing);
    expect(find.text('second'), findsNothing);
    expect(find.text('third'), findsNothing);
  });

  testWidgets('a download in progress stays until its caller replaces it', (tester) async {
    await tester.pumpWidget(_app());
    _messenger(tester).showSnackBar(workingSnackBar('downloading'));
    await tester.pump();
    await tester.pump(const Duration(seconds: 30));

    expect(find.text('downloading'), findsOneWidget);

    _messenger(tester).showSnackBar(const SnackBar(content: Text('done')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));
    expect(find.text('downloading'), findsNothing);
    expect(find.text('done'), findsOneWidget);
    await tester.pump(kSnackBarDuration);
    await tester.pump(const Duration(seconds: 1));
  });

  test('a caller asking for less time keeps it', () {
    const brief = SnackBar(content: Text('x'), duration: Duration(seconds: 1));
    expect(snackBarTimeLimit(brief, accessibleNavigation: false), const Duration(seconds: 1));
  });
}
