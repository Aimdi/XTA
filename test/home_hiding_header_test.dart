import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/home/home_hiding_header.dart';
import 'package:xta/home/home_navigation_visibility.dart';

const double _headerHeight = 50;

class _Motion implements HomeNavigationMotion {
  @override
  final AnimationController controller = AnimationController(vsync: const TestVSync());
  @override
  final double travel = 100;
  @override
  final bool reduceMotion = false;
}

Widget _app(HomeNavigationVisibilityStore? store) {
  const header = SizedBox(width: double.infinity, height: _headerHeight, child: Text('header'));
  final body = Column(
    children: [
      const HomeHidingHeader(child: header),
      Expanded(child: Container()),
    ],
  );
  return MaterialApp(
    home: Scaffold(
      body: store == null ? body : Provider<HomeNavigationVisibilityStore>.value(value: store, child: body),
    ),
  );
}

ScrollMetrics _metrics() => FixedScrollMetrics(
  minScrollExtent: 0,
  maxScrollExtent: 1000,
  pixels: 200,
  viewportDimension: 600,
  axisDirection: AxisDirection.down,
  devicePixelRatio: 1,
);

void _drag(HomeNavigationVisibilityStore store, BuildContext context, double delta) {
  final direction = delta > 0 ? ScrollDirection.reverse : ScrollDirection.forward;
  store.onScroll(UserScrollNotification(metrics: _metrics(), context: context, direction: direction));
  store.onScroll(ScrollUpdateNotification(metrics: _metrics(), context: context, scrollDelta: delta));
}

double _height(WidgetTester tester) => tester.getSize(find.byType(HomeHidingHeader)).height;

void main() {
  testWidgets('without the home bar to follow, the header stays', (tester) async {
    await tester.pumpWidget(_app(null));
    expect(_height(tester), _headerHeight);
  });

  testWidgets('the header leaves with the bottom bar and returns with it', (tester) async {
    final store = HomeNavigationVisibilityStore(PrefServiceCache(defaults: {optionHideNavigationOnScroll: true}));
    final motion = _Motion();
    store.attach(motion);
    addTearDown(motion.controller.dispose);
    addTearDown(store.destroy);

    await tester.pumpWidget(_app(store));
    final context = tester.element(find.text('header'));
    expect(_height(tester), _headerHeight);

    _drag(store, context, 100);
    await tester.pumpAndSettle();
    expect(store.state, isFalse);
    expect(_height(tester), 0);

    _drag(store, context, -10);
    await tester.pumpAndSettle();
    expect(store.state, isTrue);
    expect(_height(tester), _headerHeight);
  });
}
