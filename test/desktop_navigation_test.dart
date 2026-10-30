import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/ui/desktop_navigation.dart';
import 'package:xta/utils/desktop.dart';

final _navigator = GlobalKey<NavigatorState>();

Widget _app() => MaterialApp(
  navigatorKey: _navigator,
  builder: (context, child) => DesktopNavigation(navigatorKey: _navigator, child: child!),
  home: const Scaffold(body: Text('home')),
);

Future<void> _openPage(WidgetTester tester, {Widget body = const Text('page')}) async {
  _navigator.currentState!.push(MaterialPageRoute<void>(builder: (_) => Scaffold(body: body)));
  await tester.pumpAndSettle();
}

Future<void> _pressBackButton(WidgetTester tester) async {
  final mouse = await tester.createGesture(kind: PointerDeviceKind.mouse, buttons: kBackMouseButton);
  await mouse.down(tester.getCenter(find.byType(Scaffold).last));
  await mouse.up();
  await tester.pumpAndSettle();
}

void main() {
  setUp(() => debugDesktopOverride = true);
  tearDown(() => debugDesktopOverride = null);

  group('back on the desktop', () {
    testWidgets('Esc goes back from a page', (tester) async {
      await tester.pumpWidget(_app());
      await _openPage(tester);

      await tester.sendKeyEvent(LogicalKeyboardKey.escape);
      await tester.pumpAndSettle();

      expect(find.text('page'), findsNothing);
      expect(find.text('home'), findsOneWidget);
    });

    testWidgets('Esc closes a dialog and leaves the page under it', (tester) async {
      await tester.pumpWidget(_app());
      await _openPage(tester);
      showDialog<void>(
        context: _navigator.currentContext!,
        builder: (_) => const AlertDialog(title: Text('dialog')),
      );
      await tester.pumpAndSettle();

      await tester.sendKeyEvent(LogicalKeyboardKey.escape);
      await tester.pumpAndSettle();

      expect(find.text('dialog'), findsNothing);
      expect(find.text('page'), findsOneWidget);
    });

    testWidgets('typing in a page, the first Esc leaves the field and the next goes back', (tester) async {
      await tester.pumpWidget(_app());
      await _openPage(tester, body: const TextField(autofocus: true));

      await tester.sendKeyEvent(LogicalKeyboardKey.escape);
      await tester.pumpAndSettle();
      expect(find.byType(TextField), findsOneWidget);
      expect(FocusManager.instance.primaryFocus?.context?.findAncestorWidgetOfExactType<EditableText>(), isNull);

      await tester.sendKeyEvent(LogicalKeyboardKey.escape);
      await tester.pumpAndSettle();
      expect(find.byType(TextField), findsNothing);
    });

    testWidgets('typing in a dialog, Esc closes the dialog as before', (tester) async {
      await tester.pumpWidget(_app());
      await _openPage(tester);
      showDialog<void>(
        context: _navigator.currentContext!,
        builder: (_) => const AlertDialog(content: TextField(autofocus: true)),
      );
      await tester.pumpAndSettle();

      await tester.sendKeyEvent(LogicalKeyboardKey.escape);
      await tester.pumpAndSettle();

      expect(find.byType(AlertDialog), findsNothing);
      expect(find.text('page'), findsOneWidget);
    });

    testWidgets('Esc on the first page does nothing', (tester) async {
      await tester.pumpWidget(_app());

      await tester.sendKeyEvent(LogicalKeyboardKey.escape);
      await tester.pumpAndSettle();

      expect(find.text('home'), findsOneWidget);
    });

    testWidgets('Alt+← goes back, a dialog first', (tester) async {
      await tester.pumpWidget(_app());
      await _openPage(tester);
      showDialog<void>(
        context: _navigator.currentContext!,
        builder: (_) => const AlertDialog(title: Text('dialog')),
      );
      await tester.pumpAndSettle();

      Future<void> altLeft() async {
        await tester.sendKeyDownEvent(LogicalKeyboardKey.altLeft);
        await tester.sendKeyEvent(LogicalKeyboardKey.arrowLeft);
        await tester.sendKeyUpEvent(LogicalKeyboardKey.altLeft);
        await tester.pumpAndSettle();
      }

      await altLeft();
      expect(find.text('dialog'), findsNothing);
      expect(find.text('page'), findsOneWidget);
      await altLeft();
      expect(find.text('page'), findsNothing);
    });

    testWidgets("the mouse's back button goes back", (tester) async {
      await tester.pumpWidget(_app());
      await _openPage(tester);

      await _pressBackButton(tester);

      expect(find.text('page'), findsNothing);
      expect(find.text('home'), findsOneWidget);
    });
  });

  testWidgets('off the desktop neither keys nor the mouse go back', (tester) async {
    debugDesktopOverride = false;
    await tester.pumpWidget(_app());
    await _openPage(tester);

    await tester.sendKeyEvent(LogicalKeyboardKey.escape);
    await _pressBackButton(tester);

    expect(find.text('page'), findsOneWidget);
  });

  group('DesktopRefreshKeys', () {
    Future<int> refreshes(WidgetTester tester, List<List<LogicalKeyboardKey>> chords) async {
      var count = 0;
      await tester.pumpWidget(
        MaterialApp(
          home: DesktopRefreshKeys(
            onRefresh: () async => count++,
            child: const Scaffold(body: Text('timeline')),
          ),
        ),
      );
      for (final chord in chords) {
        for (final key in chord) {
          await tester.sendKeyDownEvent(key);
        }
        for (final key in chord.reversed) {
          await tester.sendKeyUpEvent(key);
        }
      }
      await tester.pump();
      return count;
    }

    const keys = [
      [LogicalKeyboardKey.f5],
      [LogicalKeyboardKey.controlLeft, LogicalKeyboardKey.keyR],
    ];

    testWidgets('F5 and Ctrl+R refresh on the desktop', (tester) async {
      expect(await refreshes(tester, keys), 2);
    });

    testWidgets('nothing refreshes off the desktop', (tester) async {
      debugDesktopOverride = false;
      expect(await refreshes(tester, keys), 0);
    });
  });
}
