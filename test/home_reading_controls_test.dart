import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/plugins/plugin_home_reading_controls.dart';

void main() {
  late HomeReadingControlsStore store;
  late PrefServiceCache prefs;
  setUp(() {
    prefs = PrefServiceCache(defaults: {});
    store = HomeReadingControlsStore()..bind('blue', prefs);
  });
  tearDown(() => store.destroy());

  void scroll(
    double delta, {
    double before = 200,
    double extent = 1000,
    double height = 48,
    bool protected = false,
    String source = 'blue',
  }) => store.observe(
    source: source,
    extentBefore: before,
    scrollExtent: extent,
    controlsHeight: height,
    userDelta: delta,
    protected: protected,
  );

  test('deliberate down hides, small reversals do not flicker, deliberate up reveals', () {
    scroll(20);
    scroll(20);
    expect(store.state.visible, isTrue);
    scroll(30);
    expect(store.state.visible, isFalse);
    scroll(-8);
    expect(store.state.visible, isFalse);
    scroll(-20);
    expect(store.state.visible, isTrue);
  });
  test('programmatic jumps and stale source notifications cannot hide controls', () {
    scroll(0, before: 900);
    expect(store.state.visible, isTrue);
    scroll(200, source: 'departed');
    expect(store.state.visible, isTrue);
  });
  test('short feeds and protected focus, modal or accessible navigation stay visible', () {
    scroll(200, extent: 100);
    expect(store.state.visible, isTrue);
    scroll(200, protected: true);
    expect(store.state.visible, isTrue);
    scroll(200);
    expect(store.state.visible, isFalse);
    scroll(0, protected: true);
    expect(store.state.visible, isTrue);
  });
  test('reclaimed space does not create a short-feed hide/reveal loop', () {
    scroll(100, extent: 160);
    expect(store.state.visible, isFalse);
    scroll(0, extent: 112);
    scroll(0, extent: 112);
    expect(store.state.visible, isFalse);
    scroll(0, before: 0, extent: 112);
    expect(store.state.visible, isTrue);
  });
  test('pin is reversible, persisted, and restored by a new Home session', () async {
    await store.setPinned(true);
    scroll(500);
    expect(store.state.visible, isTrue);
    expect(prefs.get<bool>(homeKeepControlsVisibleKey), isTrue);
    final next = HomeReadingControlsStore()..bind('masto', prefs);
    expect(next.state.pinned, isTrue);
    await next.setPinned(false);
    expect(next.state.pinned, isFalse);
    expect(prefs.get<bool>(homeKeepControlsVisibleKey), isFalse);
    await next.destroy();
  });
  test('source changes reveal controls without changing the global pin setting', () {
    scroll(100);
    expect(store.state.visible, isFalse);
    store.bind('substack', prefs);
    expect(store.state.visible, isTrue);
    expect(store.state.source, 'substack');
  });

  for (final protection in ['focus', 'keyboard', 'accessibility', 'short']) {
    testWidgets('real viewport retains controls for $protection', (tester) async {
      final focus = FocusNode();
      final scrollController = ScrollController();
      if (protection == 'keyboard') {
        tester.view.viewInsets = const FakeViewPadding(bottom: 200);
        addTearDown(tester.view.resetViewInsets);
      }
      try {
        await tester.pumpWidget(
          MaterialApp(
            home: Builder(
              builder: (context) => MediaQuery(
                data: MediaQuery.of(context).copyWith(
                  accessibleNavigation: protection == 'accessibility',
                  viewInsets: EdgeInsets.only(bottom: protection == 'keyboard' ? 200 : 0),
                ),
                child: Scaffold(
                  body: HomeReadingViewport(
                    store: store,
                    source: 'blue',
                    enabled: true,
                    prefs: prefs,
                    controls: SizedBox(
                      height: 48,
                      child: TextButton(focusNode: focus, onPressed: () {}, child: const Text('Reading options')),
                    ),
                    child: ListView(
                      controller: scrollController,
                      children: [
                        for (var i = 0; i < (protection == 'short' ? 2 : 30); i++)
                          SizedBox(height: 80, child: Text('Post $i')),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          ),
        );
        await tester.pumpAndSettle();
        if (protection == 'focus') {
          focus.requestFocus();
          await tester.pump();
        }
        await tester.drag(find.byType(ListView), const Offset(0, -300));
        await tester.pumpAndSettle();
        expect(store.state.visible, isTrue);
        expect(find.text('Reading options').hitTestable(), findsOneWidget);
        expect(tester.takeException(), isNull);
      } finally {
        await tester.pumpWidget(const SizedBox());
        await tester.pump();
        focus.dispose();
        scrollController.dispose();
      }
    });
  }
}
