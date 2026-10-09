import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/home/home_chrome.dart';
import 'package:xta/home/home_navigation_visibility.dart';
import 'package:xta/ui/x_look_theme.dart';

/// Notifications want a real element; any pumped widget's will do.
late BuildContext _ctx;

Future<void> _pumpContext(WidgetTester tester) async {
  await tester.pumpWidget(const SizedBox());
  _ctx = tester.element(find.byType(SizedBox));
}

ScrollMetrics _metrics({double pixels = 200, double max = 1000, Axis axis = Axis.vertical}) => FixedScrollMetrics(
  minScrollExtent: 0,
  maxScrollExtent: max,
  pixels: pixels,
  viewportDimension: 600,
  axisDirection: axis == Axis.vertical ? AxisDirection.down : AxisDirection.right,
  devicePixelRatio: 1,
);

ScrollUpdateNotification _move(double delta, {double pixels = 200, double max = 1000, Axis axis = Axis.vertical}) =>
    ScrollUpdateNotification(
      metrics: _metrics(pixels: pixels, max: max, axis: axis),
      context: _ctx,
      scrollDelta: delta,
    );

HomeNavigationVisibilityStore _store({bool enabled = true}) =>
    HomeNavigationVisibilityStore(PrefServiceCache(defaults: {optionHideNavigationOnScroll: enabled}));

void main() {
  group('HomeNavigationVisibilityStore', () {
    testWidgets('hides after 24 dp down and returns after 12 dp up', (tester) async {
      await _pumpContext(tester);
      final store = _store();
      store.onScroll(_move(10));
      store.onScroll(_move(10));
      expect(store.state, isTrue, reason: 'under the threshold');
      store.onScroll(_move(5));
      expect(store.state, isFalse);
      store.onScroll(_move(-8));
      expect(store.state, isFalse, reason: 'a short wobble does not bring it back');
      store.onScroll(_move(-5));
      expect(store.state, isTrue);
    });

    testWidgets('a direction change starts the count again', (tester) async {
      await _pumpContext(tester);
      final store = _store();
      store.onScroll(_move(20));
      store.onScroll(_move(-3));
      store.onScroll(_move(20));
      expect(store.state, isTrue);
      store.onScroll(_move(5));
      expect(store.state, isFalse);
    });

    testWidgets('the top and the end of a list always show the bar', (tester) async {
      await _pumpContext(tester);
      final store = _store();
      store.onScroll(_move(30));
      expect(store.state, isFalse);
      store.onScroll(_move(5, pixels: 1000));
      expect(store.state, isTrue, reason: 'at the end');
      store.onScroll(_move(30));
      expect(store.state, isFalse);
      store.onScroll(_move(-1, pixels: 0));
      expect(store.state, isTrue, reason: 'at the top');
      store.onScroll(_move(30, pixels: 0, max: 0));
      expect(store.state, isTrue, reason: 'nothing to scroll');
    });

    testWidgets('horizontal scrolling, stopping and a screen reader never hide it', (tester) async {
      await _pumpContext(tester);
      final store = _store();
      store.onScroll(_move(300, axis: Axis.horizontal));
      expect(store.state, isTrue);
      store.onScroll(_move(300, pixels: 200));
      expect(store.state, isFalse);
      store.onScroll(ScrollEndNotification(metrics: _metrics(), context: _ctx));
      expect(store.state, isFalse, reason: 'a stop is not a reveal');
      store.onScroll(_move(300), accessible: true);
      expect(store.state, isTrue);
      store.onScroll(_move(300), accessible: true);
      expect(store.state, isTrue);
    });

    testWidgets('with the setting off the bar stays, and show() brings it back', (tester) async {
      await _pumpContext(tester);
      final off = _store(enabled: false);
      off.onScroll(_move(300));
      expect(off.state, isTrue);
      final on = _store();
      on.onScroll(_move(300));
      expect(on.state, isFalse);
      on.show();
      expect(on.state, isTrue);
    });
  });

  group('HomeNavigationSlide', () {
    final items = [
      HomeNavigationItem(label: 'Home', icon: const Icon(Icons.home_outlined), selectedIcon: const Icon(Icons.home)),
      HomeNavigationItem(
        label: 'Saved',
        icon: const Icon(Icons.bookmark_border),
        selectedIcon: const Icon(Icons.bookmark),
      ),
    ];

    Widget app(HomeNavigationVisibilityStore store, {bool reduceMotion = true, VoidCallback? onTap}) => PrefService(
      service: PrefServiceCache(defaults: {optionDisableAnimations: reduceMotion, optionHideNavigationOnScroll: true}),
      child: MaterialApp(
        theme: xLookLightsOutTheme(null),
        home: Scaffold(
          extendBody: true,
          body: NotificationListener<ScrollNotification>(
            onNotification: (notification) {
              store.onScroll(notification);
              return false;
            },
            child: ListView.builder(itemCount: 60, itemBuilder: (_, i) => SizedBox(height: 80, child: Text('row $i'))),
          ),
          bottomNavigationBar: HomeNavigationSlide(
            store: store,
            child: HomeNavigationBar(
              selectedIndex: 0,
              items: items,
              showLabels: false,
              disableAnimations: reduceMotion,
              onSelected: (_) => onTap?.call(),
            ),
          ),
        ),
      ),
    );

    testWidgets('scrolling down slides the bar away and up brings it back', (tester) async {
      final store = _store();
      addTearDown(store.destroy);
      await tester.pumpWidget(app(store));
      Offset slide() => tester.widget<AnimatedSlide>(find.byType(AnimatedSlide)).offset;
      expect(slide(), Offset.zero);

      await tester.drag(find.byType(ListView), const Offset(0, -300));
      await tester.pumpAndSettle();
      expect(store.state, isFalse);
      expect(slide().dy, greaterThan(1));

      await tester.drag(find.byType(ListView), const Offset(0, 100));
      await tester.pumpAndSettle();
      expect(store.state, isTrue);
      expect(slide(), Offset.zero);
    });

    testWidgets('a hidden bar takes no taps and the motion follows the reduced-motion setting', (tester) async {
      var taps = 0;
      final store = _store();
      addTearDown(store.destroy);
      await tester.pumpWidget(app(store, reduceMotion: false, onTap: () => taps++));
      expect(tester.widget<AnimatedSlide>(find.byType(AnimatedSlide)).duration, isNot(Duration.zero));

      await tester.drag(find.byType(ListView), const Offset(0, -300));
      await tester.pumpAndSettle();
      expect(tester.widget<IgnorePointer>(find.byType(IgnorePointer).last).ignoring, isTrue);

      store.show();
      await tester.pumpAndSettle();
      await tester.tap(find.byIcon(Icons.bookmark_border));
      expect(taps, 1);

      await tester.pumpWidget(app(store, reduceMotion: true));
      expect(tester.widget<AnimatedSlide>(find.byType(AnimatedSlide)).duration, Duration.zero);
    });
  });
}
