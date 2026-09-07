import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/reading/article_reader_controls.dart';
import 'package:xta/reading/article_reading_store.dart';

Widget readerApp(ArticleReadingStore store, {double scale = 1, bool dark = false,
  bool rtl = false, VoidCallback? changed}) => RepaintBoundary(
  key: const ValueKey('article-controls-review'), child: MaterialApp(
  localizationsDelegates: const [L10n.delegate, GlobalMaterialLocalizations.delegate,
    GlobalWidgetsLocalizations.delegate, GlobalCupertinoLocalizations.delegate],
  supportedLocales: L10n.delegate.supportedLocales,
  theme: ThemeData(useMaterial3: true, brightness: dark ? Brightness.dark : Brightness.light,
    fontFamily: 'Inter'),
  builder: (context, child) => MediaQuery(data: MediaQuery.of(context).copyWith(
    textScaler: TextScaler.linear(scale)), child: Directionality(
      textDirection: rtl ? TextDirection.rtl : TextDirection.ltr, child: child!)),
  home: Scaffold(
    appBar: AppBar(title: const Text('Field notes')),
    body: Column(children: [
      ArticleReaderControls(store: store, onAppearanceChanged: changed ?? () {}, onStartOver: () {}),
      const Expanded(child: Padding(padding: EdgeInsets.all(20), child: Text(
        'Space for a slower read\n\nYour article stays where you left it. Reading appearance is shared between RSS and newsletters.',
        style: TextStyle(fontFamily: 'serif', fontSize: 20, height: 1.7)))),
    ]),
  ),
));

void main() {
  setUpAll(() async {
    autoUpdateGoldenFiles = true;
    await (FontLoader('Inter')..addFont(rootBundle.load('assets/fonts/Inter-Regular.ttf'))).load();
    await (FontLoader('MaterialIcons')..addFont(rootBundle.load('fonts/MaterialIcons-Regular.otf'))).load();
  });

  testWidgets('appearance changes are shared, finish is explicit and controls fit narrow large-text RTL', (tester) async {
    tester.view.physicalSize = const Size(320, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    var finishes = 0;
    var appearanceChanges = 0;
    final store = ArticleReadingStore(prefs: PrefServiceCache(), articleId: 'rss:feed:item',
      onCompleted: () async { finishes++; });
    await tester.pumpWidget(readerApp(store, scale: 2, rtl: true, changed: () { appearanceChanges++; }));
    await tester.pumpAndSettle();
    expect(find.text('Opened'), findsOneWidget);
    expect(finishes, 0);
    await tester.tap(find.byTooltip('Reading appearance'));
    await tester.pumpAndSettle();
    final slider = tester.widgetList<Slider>(find.byType(Slider)).first;
    slider.onChanged!(24);
    await tester.pumpAndSettle();
    expect(store.state.fontSize, 24);
    expect(appearanceChanges, 1);
    expect(tester.takeException(), isNull);
    await tester.tap(find.byTooltip('Close'));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Mark as finished'));
    await tester.pumpAndSettle();
    expect(finishes, 1);
    expect(find.text('Finished'), findsOneWidget);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox());
    await store.destroy();
  });

  for (final variant in ['article-controls', 'article-appearance', 'article-large-rtl']) {
    testWidgets('production reader controls render $variant', (tester) async {
      final large = variant == 'article-large-rtl';
      tester.view.physicalSize = Size(large ? 320 : 390, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final store = ArticleReadingStore(prefs: PrefServiceCache(defaults: {
        articleReadingPreference: jsonEncode({'a': {'fraction': 0.42, 'paragraph': 8}}),
      }), articleId: 'a', onCompleted: () async {});
      await tester.pumpWidget(readerApp(store, scale: large ? 2 : 1, dark: large, rtl: large));
      await tester.pumpAndSettle();
      if (variant == 'article-appearance') {
        await tester.tap(find.byTooltip('Reading appearance'));
        await tester.pumpAndSettle();
      }
      expect(tester.takeException(), isNull);
      await expectLater(find.byKey(const ValueKey('article-controls-review')), matchesGoldenFile('../review-artifacts/renders/$variant.png'));
      await tester.pumpWidget(const SizedBox());
      await store.destroy();
    });
  }
}
