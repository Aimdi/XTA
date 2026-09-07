import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:xta/generated/l10n.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:xta/offline/offline_library_screen.dart';
import 'package:xta/offline/offline_store.dart';

List<OfflineEntry> _entries() => [
  OfflineEntry(id: 'rss:journal:1', directory: '${'a' * 64}-1', title: 'A slower way to read the city', source: 'The Daily Journal',
    kind: OfflineKind.article, totalMedia: 3, articleBytes: 42000, retainedAt: DateTime(2026, 9, 7),
    files: List.generate(3, (i) => OfflineFile(name: 'media-$i', url: 'https://example.com/$i.jpg', mime: 'image/jpeg', bytes: 240000)),
    url: 'https://example.com/journal'),
  OfflineEntry(id: 'substack:design:2', directory: '${'b' * 64}-2', title: 'What makes a place worth staying in?', source: 'Notes on Design',
    kind: OfflineKind.article, totalMedia: 4, articleBytes: 38000, retainedAt: DateTime(2026, 9, 7),
    files: const [OfflineFile(name: 'media-0', url: 'https://example.com/place.jpg', mime: 'image/jpeg', bytes: 680000)],
    url: 'https://example.com/design'),
  OfflineEntry(id: 'saved:3', directory: '${'c' * 64}-3', title: 'Morning light, just outside the city.', source: 'maya@studio.example',
    kind: OfflineKind.media, totalMedia: 2, retainedAt: DateTime(2026, 9, 6),
    files: List.generate(2, (i) => OfflineFile(name: 'media-$i', url: 'https://example.com/photo-$i.jpg', mime: 'image/jpeg', bytes: 1200000))),
  OfflineEntry(id: 'saved:4', directory: '${'d' * 64}-4', title: 'A quiet moment from the archive', source: 'Photographic journal',
    kind: OfflineKind.media, totalMedia: 1, retainedAt: DateTime(2026, 9, 6), sensitive: true,
    files: const [OfflineFile(name: 'media-0', url: 'https://example.com/hidden.jpg', mime: 'image/jpeg', bytes: 720000)]),
];

void main() {
  setUpAll(() async {
    autoUpdateGoldenFiles = true;
    await (FontLoader('Inter')..addFont(rootBundle.load('assets/fonts/Inter-Regular.ttf'))).load();
    await (FontLoader('MaterialIcons')..addFont(rootBundle.load('fonts/MaterialIcons-Regular.otf'))).load();
  });
  for (final variant in ['offline-library', 'offline-library-dark', 'offline-library-large']) {
    testWidgets('offline production collection $variant', (tester) async {
      final large = variant.endsWith('large');
      tester.view.physicalSize = Size(large ? 320 : 390, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      late Directory directory;
      late OfflineStore model;
      await tester.runAsync(() async {
        directory = await Directory.systemTemp.createTemp('xta-offline-layout-');
        model = OfflineStore(directory: () async => directory, client: MockClient((_) async => http.Response('', 500)));
        await model.load();
        model.update(OfflineState(entries: _entries(), loading: false));
      });
      addTearDown(() async { await model.destroy(); await directory.delete(recursive: true); });
      final dark = variant.endsWith('dark');
      await tester.pumpWidget(MaterialApp(
        theme: ThemeData(brightness: dark ? Brightness.dark : Brightness.light, useMaterial3: true,
          fontFamily: 'Inter', colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xff49796b),
            brightness: dark ? Brightness.dark : Brightness.light)),
        localizationsDelegates: const [L10n.delegate, GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate, GlobalCupertinoLocalizations.delegate],
        supportedLocales: L10n.delegate.supportedLocales,
        locale: const Locale('en'),
        builder: (context, child) => MediaQuery(data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(large ? 2 : 1)), child: child!),
        home: RepaintBoundary(key: const ValueKey('offline-window'), child: OfflineLibraryScreen(store: model)),
      ));
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      expect(find.textContaining('Partly available offline'), findsOneWidget);
      expect(find.byType(Image), findsNothing); // Sensitive thumbnails never load in the collection.
      await expectLater(find.byKey(const ValueKey('offline-window')),
        matchesGoldenFile('../review-artifacts/renders/$variant.png'));
      await tester.pumpWidget(const SizedBox.shrink());
    });
  }
}
