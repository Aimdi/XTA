import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/substack/substack_article_navigation.dart';
import 'package:xta/plugins/substack/substack_article_navigation_sheet.dart';

Widget navigationApp(SubstackArticleDocument document, {bool rtl = false, double scale = 1}) => MaterialApp(
  debugShowCheckedModeBanner: false,
  localizationsDelegates: const [
    L10n.delegate,
    GlobalMaterialLocalizations.delegate,
    GlobalWidgetsLocalizations.delegate,
    GlobalCupertinoLocalizations.delegate,
  ],
  supportedLocales: L10n.delegate.supportedLocales,
  theme: ThemeData(useMaterial3: true, brightness: rtl ? Brightness.dark : Brightness.light, fontFamily: 'Inter'),
  builder: (context, child) => MediaQuery(
    data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(scale)),
    child: Directionality(textDirection: rtl ? TextDirection.rtl : TextDirection.ltr, child: child!),
  ),
  home: Scaffold(
    body: RepaintBoundary(
      key: const ValueKey('substack-article-navigation'),
      child: Material(child: SubstackArticleNavigationSheet(document: document)),
    ),
  ),
);

void main() {
  setUpAll(() async {
    autoUpdateGoldenFiles = true;
    await (FontLoader('Inter')..addFont(rootBundle.load('assets/fonts/Inter-Regular.ttf'))).load();
    await (FontLoader('MaterialIcons')..addFont(rootBundle.load('fonts/MaterialIcons-Regular.otf'))).load();
  });

  final document = SubstackArticleDocument.parse('''
    <h2>Field notes from the garden</h2><p>It rained through the morning.</p>
    <h3>Making room for wildflowers</h3><p>Wildflowers need time and space, even in a small garden.</p>
    <h2>What changed this season</h2><p>A quiet place to read and notice the changing light.</p>
  ''');

  testWidgets('contents become matching passages and clearing search restores headings', (tester) async {
    await tester.pumpWidget(navigationApp(document));
    await tester.pumpAndSettle();
    expect(find.text('Field notes from the garden'), findsOneWidget);
    expect(find.text('It rained through the morning.'), findsNothing);
    await tester.enterText(find.byType(TextField), 'time space');
    await tester.pumpAndSettle();
    expect(find.text('Wildflowers need time and space, even in a small garden.'), findsOneWidget);
    expect(find.text('Field notes from the garden'), findsNothing);
    await tester.enterText(find.byType(TextField), 'absent');
    await tester.pumpAndSettle();
    expect(find.text('No matching passages'), findsOneWidget);
    await tester.tap(find.byTooltip('Clear search'));
    await tester.pumpAndSettle();
    expect(find.text('Field notes from the garden'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('an article without headings still allows full local find', (tester) async {
    await tester.pumpWidget(navigationApp(SubstackArticleDocument.parse('<p>A short public article.</p>')));
    await tester.pumpAndSettle();
    expect(find.text('This article has no headings'), findsOneWidget);
    await tester.enterText(find.byType(TextField), 'public');
    await tester.pumpAndSettle();
    expect(find.text('A short public article.'), findsOneWidget);
  });

  testWidgets('selecting a passage returns its precise document target', (tester) async {
    String? selected;
    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: const [
          L10n.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        supportedLocales: L10n.delegate.supportedLocales,
        home: Scaffold(
          body: Builder(
            builder: (context) => TextButton(
              onPressed: () async {
                selected = await showSubstackArticleNavigation(context, document);
              },
              child: const Text('Open'),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('Open'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Making room for wildflowers'));
    await tester.pumpAndSettle();
    expect(selected, document.headings[1].anchor);
    expect(find.byType(SubstackArticleNavigationSheet), findsNothing);
  });

  for (final large in [false, true]) {
    testWidgets('article navigation renders ${large ? 'compact large-text RTL' : 'contents and results'}', (
      tester,
    ) async {
      tester.view.physicalSize = Size(large ? 320 : 390, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      await tester.pumpWidget(navigationApp(document, rtl: large, scale: large ? 2 : 1));
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      await expectLater(
        find.byKey(const ValueKey('substack-article-navigation')),
        matchesGoldenFile('../review-artifacts/renders/substack-article-${large ? 'rtl' : 'contents'}.png'),
      );
      if (!large) {
        await tester.enterText(find.byType(TextField), 'garden');
        await tester.pumpAndSettle();
        await expectLater(
          find.byKey(const ValueKey('substack-article-navigation')),
          matchesGoldenFile('../review-artifacts/renders/substack-article-find.png'),
        );
      } else {
        tester.view.viewInsets = const FakeViewPadding(bottom: 320);
        addTearDown(tester.view.resetViewInsets);
        await tester.enterText(find.byType(TextField), 'light');
        await tester.pumpAndSettle();
        expect(find.text('A quiet place to read and notice the changing light.'), findsOneWidget);
        expect(tester.takeException(), isNull);
      }
      await tester.pumpWidget(const SizedBox());
    });
  }
}
