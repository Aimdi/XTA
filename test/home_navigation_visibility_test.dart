import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/home/home_chrome.dart';
import 'package:xta/home/home_navigation_visibility.dart';
import 'package:xta/ui/x_look_theme.dart';

typedef _Visibility = HomeNavigationVisibilityStore;

/// Notifications want a real element; any pumped widget's will do.
late BuildContext _ctx;

Future<void> _pumpContext(WidgetTester tester) async {
  await tester.pumpWidget(const SizedBox());
  _ctx = tester.element(find.byType(SizedBox));
}

/// The bar as the store sees it, 100 px of travel so fractions read as pixels.
class _Motion implements HomeNavigationMotion {
  @override
  final AnimationController controller = AnimationController(vsync: const TestVSync());
  @override
  final double travel = 100;
  @override
  bool reduceMotion;

  _Motion({this.reduceMotion = false});
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

UserScrollNotification _user(ScrollDirection direction) =>
    UserScrollNotification(metrics: _metrics(), context: _ctx, direction: direction);

/// A drag by the reader: down is positive, as in [ScrollUpdateNotification].
void _drag(_Visibility store, List<double> deltas, {bool accessible = false}) {
  for (final delta in deltas) {
    store.onScroll(_user(delta > 0 ? ScrollDirection.reverse : ScrollDirection.forward), accessible: accessible);
    store.onScroll(_move(delta), accessible: accessible);
  }
}

void _release(_Visibility store, {double pixels = 200}) {
  store.onScroll(
    ScrollEndNotification(
      metrics: _metrics(pixels: pixels),
      context: _ctx,
    ),
  );
  store.onScroll(_user(ScrollDirection.idle));
}

_Visibility _store({bool enabled = true}) =>
    _Visibility(PrefServiceCache(defaults: {optionHideNavigationOnScroll: enabled}));

Future<(_Visibility, _Motion)> _attached(WidgetTester tester, {bool reduceMotion = false, bool enabled = true}) async {
  await _pumpContext(tester);
  final store = _store(enabled: enabled);
  final motion = _Motion(reduceMotion: reduceMotion);
  store.attach(motion);
  addTearDown(motion.controller.dispose);
  addTearDown(store.destroy);
  return (store, motion);
}

void main() {
  group('HomeNavigationVisibilityStore pure rules', () {
    test('the bar follows the content pixel for pixel, within its travel', () {
      expect(_Visibility.followed(0, 30, 100), 0.3);
      expect(_Visibility.followed(0.3, -10, 100), closeTo(0.2, 1e-9));
      expect(_Visibility.followed(0.9, 50, 100), 1);
      expect(_Visibility.followed(0.1, -50, 100), 0);
      expect(_Visibility.followed(0.4, 50, 0), 0.4, reason: 'not laid out yet');
    });

    test('a stopped bar rests by the last direction and how far it went', () {
      bool rests(double hiddenBy, bool down) =>
          _Visibility.restsHidden(hiddenBy: hiddenBy, travel: 100, movingDown: down);
      expect(rests(10, true), isFalse, reason: 'under 24 down springs back');
      expect(rests(24, true), isTrue);
      expect(rests(95, false), isTrue, reason: 'under 12 up drops away again');
      expect(rests(88, false), isFalse);
    });

    test('settling takes up to 250 ms, never under 120 ms', () {
      expect(_Visibility.settleDuration(1), const Duration(milliseconds: 250));
      expect(_Visibility.settleDuration(0.6), const Duration(milliseconds: 150));
      expect(_Visibility.settleDuration(0.1), const Duration(milliseconds: 120));
    });
  });

  group('HomeNavigationVisibilityStore', () {
    testWidgets('a partial scroll moves the bar partly, in both directions', (tester) async {
      final (store, motion) = await _attached(tester);
      _drag(store, [10, 20]);
      expect(motion.controller.value, closeTo(0.3, 1e-9));
      expect(store.state, isTrue, reason: 'partly on screen still takes taps');
      _drag(store, [-10]);
      expect(motion.controller.value, closeTo(0.2, 1e-9));
      _drag(store, [200]);
      expect(motion.controller.value, 1);
      expect(store.state, isFalse);
      _drag(store, [-40]);
      expect(motion.controller.value, closeTo(0.6, 1e-9));
      expect(store.state, isTrue);
    });

    testWidgets('a scroll end settles a caught bar away with an eased motion', (tester) async {
      final (store, motion) = await _attached(tester);
      _drag(store, [30]);
      _release(store);
      expect(store.state, isFalse);
      expect(motion.controller.isAnimating, isTrue);
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 60));
      final early = motion.controller.value;
      expect(early, inExclusiveRange(0.3, 1));
      expect(early, greaterThan(0.3 + 0.7 * 60 / 175), reason: 'ease-out: ahead of linear at the start');
      await tester.pumpAndSettle();
      expect(motion.controller.value, 1);
    });

    testWidgets('a short scroll down springs back, a short scroll up drops away again', (tester) async {
      final (store, motion) = await _attached(tester);
      _drag(store, [10]);
      _release(store);
      expect(store.state, isTrue);
      await tester.pumpAndSettle();
      expect(motion.controller.value, 0);

      _drag(store, [200]);
      _drag(store, [-5]);
      _release(store);
      await tester.pumpAndSettle();
      expect(motion.controller.value, 1);
      expect(store.state, isFalse);

      _drag(store, [-20]);
      _release(store);
      expect(store.state, isTrue);
      await tester.pumpAndSettle();
      expect(motion.controller.value, 0);
    });

    testWidgets('with reduced motion the settle is instant', (tester) async {
      final (store, motion) = await _attached(tester, reduceMotion: true);
      _drag(store, [30]);
      _release(store);
      expect(motion.controller.value, 1);
      expect(motion.controller.isAnimating, isFalse);
      store.show();
      expect(motion.controller.value, 0);
    });

    testWidgets('the top, the end of a list and a tab change bring it back', (tester) async {
      final (store, motion) = await _attached(tester);
      _drag(store, [200]);
      store.onScroll(_move(5, pixels: 1000));
      expect(store.state, isTrue, reason: 'at the end');
      await tester.pumpAndSettle();
      expect(motion.controller.value, 0);

      _drag(store, [200]);
      store.onScroll(_move(-1, pixels: 0));
      await tester.pumpAndSettle();
      expect(motion.controller.value, 0, reason: 'at the top');

      _drag(store, [50]);
      _release(store, pixels: 0);
      await tester.pumpAndSettle();
      expect(motion.controller.value, 0, reason: 'stopping at the top');

      store.onScroll(_move(30, pixels: 0, max: 0));
      expect(motion.controller.value, 0, reason: 'nothing to scroll');

      _drag(store, [200]);
      store.show();
      expect(store.state, isTrue, reason: 'a tab change');
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));
      expect(motion.controller.value, inExclusiveRange(0, 1));
      await tester.pumpAndSettle();
      expect(motion.controller.value, 0);
    });

    testWidgets('a programmatic jump or a sideways scroll does not move it', (tester) async {
      final (store, motion) = await _attached(tester);
      store.onScroll(_move(300));
      expect(motion.controller.value, 0);
      _drag(store, [300]);
      _release(store);
      _drag(store, [-300]);
      _release(store);
      store.onScroll(_move(300, axis: Axis.horizontal));
      expect(motion.controller.value, 0);
    });

    testWidgets('a screen reader keeps it on screen', (tester) async {
      final (store, motion) = await _attached(tester);
      _drag(store, [300], accessible: true);
      expect(motion.controller.value, 0);
      _drag(store, [50]);
      _drag(store, [10], accessible: true);
      expect(store.state, isTrue);
      await tester.pumpAndSettle();
      expect(motion.controller.value, 0);
    });

    testWidgets('with the setting off the bar stays', (tester) async {
      final (store, motion) = await _attached(tester, enabled: false);
      _drag(store, [300]);
      _release(store);
      expect(store.state, isTrue);
      expect(motion.controller.value, 0);
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

    Widget app(_Visibility store, ScrollController scroll, {bool reduceMotion = false, VoidCallback? onTap}) =>
        PrefService(
          service: PrefServiceCache(
            defaults: {optionDisableAnimations: reduceMotion, optionHideNavigationOnScroll: true},
          ),
          child: MaterialApp(
            theme: xLookLightsOutTheme(null),
            home: Scaffold(
              extendBody: true,
              body: NotificationListener<ScrollNotification>(
                onNotification: (notification) {
                  store.onScroll(notification);
                  return false;
                },
                child: ListView.builder(
                  controller: scroll,
                  itemCount: 60,
                  itemBuilder: (_, i) => SizedBox(height: 80, child: Text('row $i')),
                ),
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

    Future<(_Visibility, ScrollController)> pump(
      WidgetTester tester, {
      bool reduceMotion = false,
      VoidCallback? onTap,
    }) async {
      final store = _store();
      final scroll = ScrollController();
      addTearDown(store.destroy);
      addTearDown(scroll.dispose);
      await tester.pumpWidget(app(store, scroll, reduceMotion: reduceMotion, onTap: onTap));
      return (store, scroll);
    }

    double top(WidgetTester tester) => tester.getTopLeft(find.byType(HomeNavigationBar)).dy;
    double height(WidgetTester tester) => tester.getSize(find.byType(HomeNavigationSlide)).height;

    testWidgets('the bar tracks the finger, then settles away when it lifts', (tester) async {
      final (store, scroll) = await pump(tester);
      final resting = top(tester);
      final gesture = await tester.startGesture(tester.getCenter(find.byType(ListView)));
      await gesture.moveBy(const Offset(0, -30));
      await gesture.moveBy(const Offset(0, -30));
      await tester.pump();
      expect(scroll.offset, greaterThan(24));
      expect(top(tester) - resting, closeTo(scroll.offset, 0.01), reason: 'one pixel of bar per pixel of scroll');

      await gesture.up();
      await tester.pumpAndSettle();
      expect(store.state, isFalse);
      expect(top(tester) - resting, closeTo(height(tester) * HomeNavigationSlide.travelFactor, 0.01));

      await tester.drag(find.byType(ListView), const Offset(0, 100));
      await tester.pumpAndSettle();
      expect(store.state, isTrue);
      expect(top(tester), resting);
    });

    testWidgets('a hidden bar lets taps through, a shown one takes them', (tester) async {
      var taps = 0;
      final (store, _) = await pump(tester, onTap: () => taps++);
      final where = tester.getCenter(find.byIcon(Icons.bookmark_border));

      await tester.drag(find.byType(ListView), const Offset(0, -300));
      await tester.pumpAndSettle();
      expect(store.state, isFalse);
      await tester.tapAt(where);
      await tester.pump();
      expect(taps, 0);

      store.show();
      await tester.pumpAndSettle();
      await tester.tapAt(where);
      expect(taps, 1);
    });

    testWidgets('with reduced motion the lifted finger snaps it away at once', (tester) async {
      final (store, _) = await pump(tester, reduceMotion: true);
      final resting = top(tester);
      final gesture = await tester.startGesture(tester.getCenter(find.byType(ListView)));
      await gesture.moveBy(const Offset(0, -30));
      await gesture.moveBy(const Offset(0, -30));
      await tester.pump();
      final travel = height(tester) * HomeNavigationSlide.travelFactor;
      expect(top(tester) - resting, inExclusiveRange(24, travel), reason: 'caught halfway');
      await gesture.up();
      await tester.pump();
      expect(store.state, isFalse);
      expect(top(tester) - resting, closeTo(height(tester) * HomeNavigationSlide.travelFactor, 0.01));
    });
  });
}
