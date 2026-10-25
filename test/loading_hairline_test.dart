import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/ui/loading_hairline.dart';

Widget _page({required bool loading}) => MaterialApp(
  home: Scaffold(
    body: Column(
      children: [
        const SizedBox(height: 40, child: Text('header')),
        Expanded(
          child: LoadingHairline(
            loading: loading,
            semanticsLabel: 'Loading posts',
            child: ListView(children: const [SizedBox(height: 60, child: Text('first post'))]),
          ),
        ),
      ],
    ),
  ),
);

void main() {
  testWidgets('the content does not move when loading starts or stops', (tester) async {
    await tester.pumpWidget(_page(loading: false));
    final idle = tester.getTopLeft(find.text('first post'));
    expect(idle.dy, 40);

    await tester.pumpWidget(_page(loading: true));
    expect(tester.getTopLeft(find.text('first post')), idle);
    expect(find.byType(LinearProgressIndicator), findsOneWidget);

    await tester.pumpWidget(_page(loading: false));
    expect(tester.getTopLeft(find.text('first post')), idle);
    await tester.pumpAndSettle();
    expect(find.byType(LinearProgressIndicator), findsNothing);
  });

  /// How opaque the line is drawn, through its fade in and its fade out.
  double opacity(WidgetTester tester) {
    Iterable<T> around<T extends Widget>() =>
        tester.widgetList<T>(find.ancestor(of: find.byType(LinearProgressIndicator), matching: find.byType(T)));
    return [
      for (final fade in around<Opacity>()) fade.opacity,
      for (final fade in around<FadeTransition>()) fade.opacity.value,
    ].fold(1.0, (opacity, fade) => opacity * fade);
  }

  testWidgets('the line fades in as loading starts and out once it ends, leaving nothing behind', (tester) async {
    await tester.pumpWidget(_page(loading: false));
    expect(find.byType(LinearProgressIndicator), findsNothing);

    await tester.pumpWidget(_page(loading: true));
    await tester.pump(const Duration(milliseconds: 40));
    expect(opacity(tester), inExclusiveRange(0, 1));
    await tester.pump(const Duration(milliseconds: 200));
    expect(opacity(tester), 1);

    await tester.pumpWidget(_page(loading: false));
    await tester.pump(const Duration(milliseconds: 60));
    expect(opacity(tester), inExclusiveRange(0, 1));
    await tester.pump(const Duration(milliseconds: 200));
    await tester.pump();
    expect(find.byType(LinearProgressIndicator), findsNothing);
    expect(tester.hasRunningAnimations, isFalse, reason: 'no line animates unseen');
  });

  testWidgets('known progress glides, and fills to the end before the line goes', (tester) async {
    Widget line(double value, {bool loading = true}) => MaterialApp(
      home: LoadingHairline(loading: loading, value: value, child: const SizedBox.expand()),
    );
    double value() => tester.widget<LinearProgressIndicator>(find.byType(LinearProgressIndicator)).value!;
    await tester.pumpWidget(line(0.25));
    await tester.pumpAndSettle();

    await tester.pumpWidget(line(0.75));
    await tester.pump(const Duration(milliseconds: 40));
    expect(value(), inExclusiveRange(0.25, 0.75));
    await tester.pumpAndSettle();
    expect(value(), 0.75);

    await tester.pumpWidget(line(0.75, loading: false));
    await tester.pump(const Duration(milliseconds: 120));
    expect(value(), 1, reason: 'full');
    expect(opacity(tester), 1, reason: 'before it fades');
    await tester.pumpAndSettle();
    expect(find.byType(LinearProgressIndicator), findsNothing);
  });

  testWidgets('the line lies over the top edge of what loads', (tester) async {
    await tester.pumpWidget(_page(loading: true));
    final line = tester.getRect(find.byType(LinearProgressIndicator));
    expect(line.top, 40);
    expect(line.height, kLoadingHairlineHeight);
    expect(line.width, tester.getSize(find.byType(ListView)).width);
    expect(find.bySemanticsLabel('Loading posts'), findsOneWidget);
  });

  testWidgets('a header can carry the line on its bottom edge', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Column(
          children: [
            LoadingHairline(loading: true, atBottom: true, child: SizedBox(height: 50, child: Text('controls'))),
            Text('posts'),
          ],
        ),
      ),
    );
    expect(tester.getRect(find.byType(LinearProgressIndicator)).bottom, 50);
    expect(tester.getTopLeft(find.text('posts')).dy, 50);
  });

  testWidgets('the line has no track, and starts moving before any progress', (tester) async {
    Widget line(double? value) => MaterialApp(
      home: LoadingHairline(loading: true, value: value, child: const SizedBox.expand()),
    );
    await tester.pumpWidget(line(0));
    final idle = tester.widget<LinearProgressIndicator>(find.byType(LinearProgressIndicator));
    expect(idle.value, isNull);
    expect(idle.backgroundColor, Colors.transparent);
    await tester.pumpWidget(line(0.5));
    expect(tester.widget<LinearProgressIndicator>(find.byType(LinearProgressIndicator)).value, 0.5);
  });

  testWidgets('the child keeps its own state across loading', (tester) async {
    await tester.pumpWidget(_page(loading: false));
    await tester.drag(find.byType(ListView), const Offset(0, -20));
    final element = tester.element(find.text('first post'));
    await tester.pumpWidget(_page(loading: true));
    expect(tester.element(find.text('first post')), same(element));
  });
}
