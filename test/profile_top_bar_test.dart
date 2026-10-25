import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/profile/profile_chrome.dart';
import 'package:xta/ui/x_look_theme.dart';

const _banner = 'https://pbs.twimg.com/profile_banners/1/1500x500';

Future<void> _pump(
  WidgetTester tester, {
  required bool scrolled,
  String? banner = _banner,
  bool reduceMotion = false,
  ThemeData? theme,
}) => tester.pumpWidget(
  MaterialApp(
    theme: theme ?? xLookLightTheme(null),
    home: MediaQuery(
      data: MediaQueryData(disableAnimations: reduceMotion),
      child: Scaffold(
        body: CustomScrollView(
          slivers: [
            ProfileTopBar(
              name: 'Ice Universe',
              username: 'UniverseIce',
              bannerUrl: banner,
              scrolled: scrolled,
              actions: [IconButton(onPressed: () {}, tooltip: 'Search', icon: const Icon(Icons.search))],
            ),
          ],
        ),
      ),
    ),
  ),
);

SliverAppBar _bar(WidgetTester tester) => tester.widget<SliverAppBar>(find.byType(SliverAppBar));

Color? _colorOf(WidgetTester tester, String text) => tester.widget<Text>(find.text(text)).style?.color;

/// The colour the search action is drawn in.
Color? _iconColour(WidgetTester tester) => tester
    .widget<RichText>(find.descendant(of: find.byIcon(Icons.search), matching: find.byType(RichText)))
    .text
    .style
    ?.color;

void main() {
  testWidgets('with the header in view the bar holds no title and no banner', (tester) async {
    await _pump(tester, scrolled: false);

    expect(find.text('Ice Universe'), findsNothing);
    expect(find.byType(ProfileBarBanner), findsNothing);
    expect(_bar(tester).systemOverlayStyle, isNull);
  });

  testWidgets('scrolled, the name rises in over the blurred banner, in white', (tester) async {
    await _pump(tester, scrolled: false);
    await _pump(tester, scrolled: true);
    await tester.pump(const Duration(milliseconds: 60));

    final rising = tester.widget<Transform>(
      find.ancestor(of: find.text('Ice Universe'), matching: find.byType(Transform)).first,
    );
    expect(rising.transform.getTranslation().y, greaterThan(0), reason: 'still rising into place');

    await tester.pumpAndSettle();
    expect(find.byType(ProfileBarBanner), findsOneWidget);
    expect(find.byType(ImageFiltered), findsOneWidget, reason: 'the banner is blurred');
    expect(_colorOf(tester, 'Ice Universe'), Colors.white);
    expect(_colorOf(tester, '@UniverseIce'), Colors.white70);
    expect(_bar(tester).foregroundColor, Colors.white, reason: 'Back and the actions turn white too');
    expect(_iconColour(tester)?.toARGB32(), Colors.white.toARGB32(), reason: 'not the theme\'s grey icon buttons');
    expect(_bar(tester).systemOverlayStyle, SystemUiOverlayStyle.light);
    expect(tester.takeException(), isNull);
  });

  testWidgets('the actions stay light while the bar turns, never the theme\'s grey', (tester) async {
    Widget app(bool scrolled) => MaterialApp(
      theme: xLookLightsOutTheme(null),
      home: Scaffold(
        body: CustomScrollView(
          slivers: [
            ProfileTopBar(
              name: 'Ice Universe',
              username: 'UniverseIce',
              bannerUrl: _banner,
              scrolled: scrolled,
              actions: [IconButton(onPressed: () {}, tooltip: 'Search', icon: const Icon(Icons.search))],
            ),
          ],
        ),
      ),
    );
    await tester.pumpWidget(app(false));
    final resting = _iconColour(tester)!;
    await tester.pumpWidget(app(true));

    for (var i = 0; i < 4; i++) {
      await tester.pump(const Duration(milliseconds: 45));
      expect(_iconColour(tester)!.r, greaterThanOrEqualTo(resting.r), reason: 'frame ${i + 1}');
    }
  });

  testWidgets('scrolling back to the top clears the bar again', (tester) async {
    await _pump(tester, scrolled: true);
    await tester.pumpAndSettle();
    await _pump(tester, scrolled: false);
    await tester.pumpAndSettle();

    expect(find.text('Ice Universe'), findsNothing);
    expect(find.byType(ProfileBarBanner), findsNothing);
  });

  testWidgets('without a banner the bar keeps its colour and only the title rises in', (tester) async {
    await _pump(tester, scrolled: true, banner: null);
    await tester.pumpAndSettle();

    expect(find.byType(ProfileBarBanner), findsNothing);
    expect(find.text('Ice Universe'), findsOneWidget);
    expect(_colorOf(tester, 'Ice Universe'), isNot(Colors.white));
    expect(_bar(tester).systemOverlayStyle, isNull);
    final foreground = Theme.of(tester.element(find.byType(Scaffold))).appBarTheme.foregroundColor;
    expect(_iconColour(tester)?.toARGB32(), foreground?.toARGB32(), reason: 'the bar\'s own colour, as before');
  });

  testWidgets('with reduced motion the bar changes at once', (tester) async {
    await _pump(tester, scrolled: false, reduceMotion: true);
    await _pump(tester, scrolled: true, reduceMotion: true);
    await tester.pump();

    expect(find.byType(ProfileBarBanner), findsOneWidget);
    expect(_colorOf(tester, 'Ice Universe'), Colors.white);
    final title = find.ancestor(of: find.text('Ice Universe'), matching: find.byType(Opacity)).first;
    expect(tester.widget<Opacity>(title).opacity, 1, reason: 'already in place, not fading in');
  });

  test('the scrim keeps white text readable over a white banner', () {
    final darkened = Color.alphaBlend(Colors.black.withValues(alpha: kProfileBarBannerScrim), Colors.white);
    final contrast = (Colors.white.computeLuminance() + 0.05) / (darkened.computeLuminance() + 0.05);
    expect(contrast, greaterThanOrEqualTo(4.5));
  });
}
