import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/home/home_chrome.dart';
import 'package:xta/home/home_navigation_visibility.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/contrast.dart';
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

Finder get _highlight => find.byKey(const ValueKey('home-navigation-highlight'));

Rect _destination(WidgetTester tester, int index) => tester.getRect(find.byType(NavigationDestination).at(index));

HomeNavigationPalette _palette(WidgetTester tester) =>
    HomeNavigationPalette.of(tester.element(find.byType(NavigationBar)));

BoxDecoration _glass(WidgetTester tester) => tester
    .widgetList<DecoratedBox>(find.descendant(of: find.byType(BackdropFilter), matching: find.byType(DecoratedBox)))
    .map((box) => box.decoration)
    .whereType<BoxDecoration>()
    .first;

void main() {
  final themes = {'light': xLookLightTheme(null), 'dim': xLookDimTheme(null), 'lights out': xLookLightsOutTheme(null)};

  for (final MapEntry(key: name, value: theme) in themes.entries) {
    testWidgets('the $name pill is frosted glass the page shows through', (tester) async {
      _phone(tester);
      await tester.pumpWidget(_app(theme));

      final blur = find.descendant(of: find.byType(HomeNavigationBar), matching: find.byType(BackdropFilter));
      expect(blur, findsOneWidget);
      expect(find.ancestor(of: blur, matching: find.byType(ClipRRect)), findsWidgets);
      final glass = _glass(tester);
      expect(glass.color!.a, inInclusiveRange(0.7, 0.9));
      expect(glass.border, isA<Border>());
      expect((glass.border! as Border).top.width, kTweetDividerThickness);
      expect(find.descendant(of: find.byType(HomeNavigationBar), matching: find.byType(ColoredBox)), findsNothing);
    });
  }

  testWidgets('Lights Out gets a dark translucent pill rather than black', (tester) async {
    _phone(tester);
    await tester.pumpWidget(_app(xLookLightsOutTheme(null)));

    final glass = _glass(tester).color!;
    final overBlack = Color.alphaBlend(glass, const Color(0xFF000000));
    expect(glass.a, lessThan(1));
    expect(overBlack, isNot(const Color(0xFF000000)));
    expect(relativeLuminance(overBlack), inExclusiveRange(0, 0.05));
  });

  testWidgets('the selected item sits on an accent highlight spanning icon and label', (tester) async {
    _phone(tester);
    await tester.pumpWidget(_app(xLookLightTheme(null), selected: 1));

    final highlight = tester.getRect(_highlight);
    final icon = tester.getRect(find.byIcon(Icons.people));
    final label = tester.getRect(find.text('Abonnements'));
    expect(highlight.top, lessThan(icon.top));
    expect(highlight.bottom, greaterThan(label.bottom));
    expect(highlight.left, lessThanOrEqualTo(label.left));
    expect(highlight.right, greaterThanOrEqualTo(label.right));
    expect(highlight.center.dx, closeTo(_destination(tester, 1).center.dx, 0.5));
    expect(highlight.height, greaterThanOrEqualTo(kHomeNavigationHeight - 2 * kHomeNavigationHighlightInset));

    final palette = _palette(tester);
    final tint = (tester.widget<DecoratedBox>(_highlight).decoration as BoxDecoration).color!;
    expect(tint.a, inExclusiveRange(0, 0.3));
    expect(tint.withValues(alpha: 1), XLookTokens.light.accent);

    final selected = tester.widget<Text>(find.text('Abonnements')).style!;
    final other = tester.widget<Text>(find.text('Entdecken')).style!;
    expect(selected.color, palette.selected);
    expect(selected.fontWeight, FontWeight.w700);
    expect(other.color, XLookTokens.light.onBackground);
    expect(other.fontWeight, FontWeight.w500);
    expect(IconTheme.of(tester.element(find.byIcon(Icons.people))).color, palette.selected);
    expect(IconTheme.of(tester.element(find.byIcon(Icons.search_outlined))).color, XLookTokens.light.onBackground);
  });

  testWidgets('the selected colour reads at 4.5:1 through the glass for every accent', (tester) async {
    for (final accent in xLookAccents.values) {
      for (final base in [XLookTokens.light, XLookTokens.dim, XLookTokens.lightsOut]) {
        final tokens = base.copyWith(accent: accent);
        await tester.pumpWidget(_app(xLookThemeData(tokens, null)));
        // MaterialApp animates between themes; read the palette once it lands.
        await tester.pumpAndSettle();
        final palette = _palette(tester);
        final underLabel = Color.alphaBlend(palette.highlight, Color.alphaBlend(palette.glass, tokens.background));
        expect(
          contrastRatio(palette.selected, underLabel),
          greaterThanOrEqualTo(4.5),
          reason: '$accent on ${base.background}',
        );
      }
    }
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
        expect(tester.getRect(label).width, lessThanOrEqualTo(tester.getRect(_highlight).width));
      }
      final first = _destination(tester, 0).center.dx;
      expect(tester.getRect(_highlight).center.dx, closeTo(first, 0.5));
      expect(first > 160, direction == TextDirection.rtl);
    });
  }

  testWidgets('without labels the icon is centred in its highlight', (tester) async {
    _phone(tester);
    await tester.pumpWidget(_app(xLookLightTheme(null), showLabels: false));

    final highlight = tester.getRect(_highlight);
    expect(tester.getRect(find.byIcon(Icons.home)).center.dy, closeTo(highlight.center.dy, 0.5));
  });

  testWidgets('icons alone make the slimmest bar that still fits a finger', (tester) async {
    _phone(tester);
    await tester.pumpWidget(_app(xLookLightTheme(null), showLabels: false));

    expect(tester.getSize(find.byType(NavigationBar)).height, kHomeNavigationIconOnlyHeight);
    expect(kHomeNavigationIconOnlyHeight, lessThan(kHomeNavigationHeight));
    for (var index = 0; index < 4; index++) {
      expect(_destination(tester, index).height, greaterThanOrEqualTo(kTweetTouchTarget));
    }
    expect(tester.takeException(), isNull);
  });

  testWidgets('the highlight slides to a new selection', (tester) async {
    _phone(tester);
    await tester.pumpWidget(_app(xLookLightTheme(null), disableAnimations: false));
    await tester.pumpWidget(_app(xLookLightTheme(null), selected: 2, disableAnimations: false));
    await tester.pump(const Duration(milliseconds: 60));

    final target = _destination(tester, 2).center.dx;
    final moving = tester.getRect(_highlight).center.dx;
    expect(moving, greaterThan(_destination(tester, 0).center.dx));
    expect(moving, lessThan(target));
    await tester.pumpAndSettle();
    expect(tester.getRect(_highlight).center.dx, closeTo(target, 0.5));
  });

  testWidgets('reduced motion moves the highlight at once', (tester) async {
    _phone(tester);
    await tester.pumpWidget(_app(xLookLightTheme(null)));
    await tester.pumpWidget(_app(xLookLightTheme(null), selected: 3));
    await tester.pump();

    expect(tester.getRect(_highlight).center.dx, closeTo(_destination(tester, 3).center.dx, 0.5));
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
    expect(clearance, greaterThan(kHomeNavigationHeight));

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
