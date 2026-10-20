import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/home/home_chrome.dart';
import 'package:xta/ui/floating_notice.dart';

Widget _page({Widget? notice, EdgeInsets padding = EdgeInsets.zero}) => MaterialApp(
  home: MediaQuery(
    data: MediaQueryData(size: const Size(390, 800), padding: padding),
    child: Scaffold(
      body: FloatingNotice(
        notice: notice,
        child: ListView(children: const [SizedBox(height: 80, child: Text('first post'))]),
      ),
    ),
  ),
);

Widget _retry() => NoticePill(
  child: TextButton.icon(onPressed: () {}, icon: const Icon(Icons.refresh), label: const Text('Retry')),
);

void main() {
  testWidgets('the content does not move when a notice comes or goes', (tester) async {
    await tester.pumpWidget(_page());
    final idle = tester.getTopLeft(find.text('first post'));

    await tester.pumpWidget(_page(notice: _retry()));
    expect(tester.getTopLeft(find.text('first post')), idle);
    expect(find.text('Retry'), findsOneWidget);

    await tester.pumpWidget(_page());
    expect(tester.getTopLeft(find.text('first post')), idle);
    expect(find.byType(FrostedPill), findsNothing);
  });

  testWidgets('the notice floats centred near the top, as wide as it needs', (tester) async {
    await tester.pumpWidget(_page(notice: _retry()));
    final pill = tester.getRect(find.byType(FrostedPill));
    expect(pill.top, 8);
    expect(pill.center.dx, closeTo(tester.getSize(find.byType(Scaffold)).width / 2, 0.5));
    expect(pill.width, lessThan(200));
    expect(pill.height, greaterThanOrEqualTo(48));
  });

  testWidgets('it stays below a status bar the content runs under', (tester) async {
    await tester.pumpWidget(_page(notice: _retry(), padding: const EdgeInsets.only(top: 24)));
    expect(tester.getRect(find.byType(FrostedPill)).top, 32);
  });

  testWidgets('a notice with nothing to say draws nothing and blocks nothing', (tester) async {
    var tapped = false;
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: FloatingNotice(
            notice: const SizedBox.shrink(),
            child: Align(
              alignment: Alignment.topCenter,
              child: TextButton(onPressed: () => tapped = true, child: const Text('under')),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('under'));
    expect(tapped, isTrue);
    expect(find.byType(FrostedPill), findsNothing);
  });

  testWidgets('a notice inside another floats below its pill, and rises when it goes', (tester) async {
    Widget nested({required bool outer}) => MaterialApp(
      home: Scaffold(
        body: FloatingNotice(
          notice: outer
              ? NoticePill(key: const ValueKey('outer'), child: const SizedBox(width: 100, height: 48))
              : null,
          child: FloatingNotice(
            notice: NoticePill(key: const ValueKey('inner'), child: const SizedBox(width: 120, height: 48)),
            child: const SizedBox.expand(),
          ),
        ),
      ),
    );
    await tester.pumpWidget(nested(outer: true));
    await tester.pump();
    final outer = tester.getRect(find.byKey(const ValueKey('outer')));
    final inner = tester.getRect(find.byKey(const ValueKey('inner')));
    expect(outer.top, 8);
    expect(inner.top, outer.bottom + 8);

    await tester.pumpWidget(nested(outer: false));
    await tester.pump();
    expect(tester.getRect(find.byKey(const ValueKey('inner'))).top, 8);
  });

  testWidgets('a floating notice is announced when it appears', (tester) async {
    final semantics = tester.ensureSemantics();
    await tester.pumpWidget(_page(notice: _retry()));
    expect(tester.getSemantics(find.byType(NoticePill)), matchesSemantics(isLiveRegion: true, children: [anything]));
    semantics.dispose();
  });
}
