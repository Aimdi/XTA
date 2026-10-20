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
