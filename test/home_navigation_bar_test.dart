import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/home/home_chrome.dart';
import 'package:xta/home/home_navigation_visibility.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/x_look_theme.dart';

const _labels = ['Start', 'Abonnements', 'Entdecken', 'Gespeichert'];
const _icons = [
  (Icons.home_outlined, Icons.home),
  (Icons.people_outlined, Icons.people),
  (Icons.search_outlined, Icons.search),
  (Icons.bookmark_border_outlined, Icons.bookmark),
];

final _items = [
  for (var i = 0; i < _labels.length; i++)
    HomeNavigationItem(label: _labels[i], icon: Icon(_icons[i].$1), selectedIcon: Icon(_icons[i].$2)),
];

Widget _app(
  ThemeData theme, {
  int selected = 0,
  bool showLabels = true,
  bool disableAnimations = true,
  double textScale = 1,
  TextDirection direction = TextDirection.ltr,
}) => MaterialApp(
  theme: theme,
  builder: (context, child) => MediaQuery(
    data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(textScale)),
    child: Directionality(textDirection: direction, child: child!),
  ),
  home: Scaffold(
    extendBody: true,
    body: const ColoredBox(color: Color(0xFFE91E63), child: SizedBox.expand()),
    bottomNavigationBar: HomeNavigationBar(
      selectedIndex: selected,
      items: _items,
      showLabels: showLabels,
      disableAnimations: disableAnimations,
      onSelected: (_) {},
    ),
  ),
);

void _phone(WidgetTester tester, {double width = 390}) {
  tester.view.physicalSize = Size(width, 844);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
}

Finder get _surface => find.byKey(const ValueKey('home-navigation-surface'));

Rect _destination(WidgetTester tester, int index) => tester.getRect(find.byType(NavigationDestination).at(index));

void main() {
  final themes = {
    'light': (xLookLightTheme(null), XLookTokens.light),
    'dim': (xLookDimTheme(null), XLookTokens.dim),
    'lights out': (xLookLightsOutTheme(null), XLookTokens.lightsOut),
  };

  for (final MapEntry(key: name, value: (theme, tokens)) in themes.entries) {
    testWidgets('the $name bar is flat across the bottom with a hairline on top', (tester) async {
      _phone(tester);
      await tester.pumpWidget(_app(theme));

      expect(find.descendant(of: find.byType(HomeNavigationBar), matching: find.byType(BackdropFilter)), findsNothing);
      final surface = tester.widget<DecoratedBox>(_surface).decoration as BoxDecoration;
      expect(surface.color, tokens.background.withValues(alpha: kHomeNavigationBarOpacity));
      final top = (surface.border! as Border).top;
      expect(top.width, kTweetDividerThickness);
      expect(top.color, tokens.divider);
      expect(tester.getRect(_surface), const Rect.fromLTRB(0, 844 - kHomeNavigationHeight, 390, 844));
    });

    testWidgets('on $name every icon is in the text colour and the selected one is filled', (tester) async {
      _phone(tester);
      await tester.pumpWidget(_app(theme, selected: 1));

      expect(find.byIcon(Icons.people), findsOneWidget);
      expect(find.byIcon(Icons.people_outlined), findsNothing);
      expect(find.byIcon(Icons.home_outlined), findsOneWidget);
      for (final icon in [Icons.people, Icons.home_outlined, Icons.search_outlined]) {
        final theme = IconTheme.of(tester.element(find.byIcon(icon)));
        expect(theme.color, tokens.onBackground);
        expect(theme.size, kHomeNavigationIconSize);
      }
      final selected = tester.widget<Text>(find.text('Abonnements')).style!;
      final other = tester.widget<Text>(find.text('Entdecken')).style!;
      expect(selected.fontWeight, FontWeight.w700);
      expect(other.fontWeight, FontWeight.w500);
      expect(selected.color, tokens.onBackground);
      expect(other.color, tokens.onBackground);
    });
  }

  testWidgets('the bar keeps its colour under the system gesture area', (tester) async {
    _phone(tester);
    await tester.pumpWidget(
      MediaQuery(
        data: const MediaQueryData(size: Size(390, 844), padding: EdgeInsets.only(bottom: 24)),
        child: _app(xLookLightsOutTheme(null)),
      ),
    );

    expect(tester.getRect(_surface).bottom, 844);
    expect(tester.getRect(find.byType(NavigationBar)).bottom, 844 - 24);
    expect(tester.getSize(find.byType(NavigationBar)).height, kHomeNavigationHeight);
  });

  testWidgets('every destination keeps a 48dp touch target on a narrow phone', (tester) async {
    _phone(tester, width: 320);
    await tester.pumpWidget(_app(xLookLightTheme(null)));

    for (var i = 0; i < _labels.length; i++) {
      final size = _destination(tester, i).size;
      expect(size.width, greaterThanOrEqualTo(kTweetTouchTarget));
      expect(size.height, greaterThanOrEqualTo(kTweetTouchTarget));
    }
  });

  for (final direction in TextDirection.values) {
    testWidgets('long labels ellipsize at 2x text on 320dp (${direction.name})', (tester) async {
      _phone(tester, width: 320);
      await tester.pumpWidget(_app(xLookLightTheme(null), textScale: 2, direction: direction));

      expect(tester.takeException(), isNull);
      expect(tester.getSize(find.byType(NavigationBar)).height, kHomeNavigationHeight);
      for (var i = 0; i < _labels.length; i++) {
        final label = find.text(_labels[i]);
        final paragraph = tester.renderObject<RenderParagraph>(label);
        expect(paragraph.maxLines, 1);
        expect(_destination(tester, i).contains(tester.getRect(label).topLeft), isTrue);
      }
      expect(_destination(tester, 0).center.dx > 160, direction == TextDirection.rtl);
    });
  }

  testWidgets('without labels, as by default, the icons sit in the middle of the bar', (tester) async {
    _phone(tester);
    await tester.pumpWidget(_app(xLookLightTheme(null), showLabels: false));

    final bar = tester.getRect(find.byType(NavigationBar));
    expect(bar.height, kHomeNavigationHeight);
    expect(tester.getRect(find.byIcon(Icons.home)).center.dy, closeTo(bar.center.dy, 1));
    for (var index = 0; index < 4; index++) {
      expect(_destination(tester, index).height, greaterThanOrEqualTo(kTweetTouchTarget));
    }
    expect(tester.takeException(), isNull);
  });

  group('sliding away while scrolling', _scrollingTests);
}

Widget _scrolling(HomeNavigationVisibilityStore store, {bool accessible = false}) => PrefService(
  service: PrefServiceCache(defaults: {optionHideNavigationOnScroll: true}),
  child: MaterialApp(
    theme: xLookLightTheme(null),
    builder: (context, child) => MediaQuery(
      data: MediaQuery.of(context).copyWith(accessibleNavigation: accessible),
      child: child!,
    ),
    home: Scaffold(
      extendBody: true,
      body: Builder(
        builder: (context) => NotificationListener<ScrollNotification>(
          onNotification: (notification) {
            store.onScroll(notification, accessible: MediaQuery.accessibleNavigationOf(context));
            return false;
          },
          child: ListView(children: [for (var i = 0; i < 40; i++) SizedBox(height: 80, child: Text('row $i'))]),
        ),
      ),
      bottomNavigationBar: HomeNavigationSlide(
        store: store,
        child: HomeNavigationBar(
          selectedIndex: 0,
          items: _items,
          showLabels: true,
          disableAnimations: false,
          onSelected: (_) {},
        ),
      ),
    ),
  ),
);

void _scrollingTests() {
  HomeNavigationVisibilityStore store() {
    final visibility = HomeNavigationVisibilityStore(PrefServiceCache(defaults: {optionHideNavigationOnScroll: true}));
    addTearDown(visibility.destroy);
    return visibility;
  }

  testWidgets('the bar leaves the screen while the pages keep their clearance', (tester) async {
    _phone(tester);
    final visibility = store();
    await tester.pumpWidget(_scrolling(visibility));
    final clearance = MediaQuery.paddingOf(tester.element(find.byType(ListView))).bottom;
    expect(clearance, greaterThanOrEqualTo(kHomeNavigationHeight));

    await tester.drag(find.byType(ListView), const Offset(0, -300));
    await tester.pumpAndSettle();
    expect(visibility.state, isFalse);
    expect(tester.getRect(find.byType(NavigationBar)).top, greaterThanOrEqualTo(844));
    expect(MediaQuery.paddingOf(tester.element(find.byType(ListView))).bottom, clearance);
  });

  testWidgets('with a screen reader the bar never moves', (tester) async {
    _phone(tester);
    final visibility = store();
    await tester.pumpWidget(_scrolling(visibility, accessible: true));
    final resting = tester.getRect(find.byType(NavigationBar));

    final gesture = await tester.startGesture(tester.getCenter(find.byType(ListView)));
    await gesture.moveBy(const Offset(0, -60));
    await tester.pump();
    expect(tester.getRect(find.byType(NavigationBar)), resting);
    await gesture.up();
    await tester.pumpAndSettle();
    expect(visibility.state, isTrue);
    expect(tester.getRect(find.byType(NavigationBar)), resting);
  });
}
