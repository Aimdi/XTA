import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/downloads/download_entry.dart';
import 'package:xta/downloads/download_store.dart';
import 'package:xta/downloads/downloads_screen.dart';
import 'package:xta/generated/l10n.dart';

import 'download_center_test.dart' show MemoryHistory;

DownloadEntry sampleDownload(String id, String name, DownloadStatus status, {int received = 0, int? total}) =>
  DownloadEntry(id: id, uri: Uri.parse('https://media.example/$id'), fileName: name,
    createdAt: DateTime.utc(2026, 9, 7, 12, int.parse(id)), status: status,
    received: received, total: total,
    savedUri: status == DownloadStatus.completed ? 'content://provider/document/$id' : null);

Widget downloadApp(DownloadStore store, {bool dark = false, bool large = false}) => MaterialApp(
  theme: ThemeData(brightness: dark ? Brightness.dark : Brightness.light, fontFamily: 'Inter',
    colorSchemeSeed: const Color(0xff1d9bf0), scaffoldBackgroundColor: dark ? Colors.black : null),
  localizationsDelegates: const [L10n.delegate, GlobalMaterialLocalizations.delegate,
    GlobalWidgetsLocalizations.delegate, GlobalCupertinoLocalizations.delegate],
  supportedLocales: L10n.delegate.supportedLocales,
  locale: const Locale('en'),
  home: Builder(builder: (context) => MediaQuery(data: MediaQuery.of(context).copyWith(
      textScaler: TextScaler.linear(large ? 2 : 1)), child: Directionality(
        textDirection: large ? TextDirection.rtl : TextDirection.ltr,
        child: RepaintBoundary(key: const ValueKey('download-review'), child: DownloadsScreen(store: store))))),
);

void main() {
  setUpAll(() async {
    autoUpdateGoldenFiles = true;
    await (FontLoader('Inter')..addFont(rootBundle.load('assets/fonts/Inter-Regular.ttf'))).load();
    await (FontLoader('MaterialIcons')..addFont(rootBundle.load('fonts/MaterialIcons-Regular.otf'))).load();
  });

  for (final variant in ['downloads', 'downloads-dark', 'downloads-large-rtl']) {
    testWidgets('Downloads production screen render $variant', (tester) async {
      final large = variant.endsWith('rtl');
      tester.view.physicalSize = Size(large ? 320 : 390, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final history = MemoryHistory([
        sampleDownload('1', 'maya-weekend-on-the-coast.mp4', DownloadStatus.downloading,
          received: 12976128, total: 32505856),
        sampleDownload('2', 'maya-colour-study.jpg', DownloadStatus.queued),
        sampleDownload('3', 'field-notes-autumn.jpg', DownloadStatus.completed),
        sampleDownload('4', 'architecture-details.jpg', DownloadStatus.completed),
        sampleDownload('5', 'an-evening-in-the-city.mp4', DownloadStatus.failed),
        sampleDownload('6', 'long-walk-home.jpg', DownloadStatus.interrupted),
      ]);
      final store = DownloadStore(history: history, runner: (_, _, _, _) async => null);
      await store.initialize();
      await tester.pumpWidget(downloadApp(store, dark: variant.endsWith('dark'), large: large));
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      await expectLater(find.byKey(const ValueKey('download-review')),
        matchesGoldenFile('../review-artifacts/renders/$variant.png'));
      await tester.pumpWidget(const SizedBox());
      await store.destroy();
    });
  }

  testWidgets('retry and clear history act on real store without deleting downloaded files', (tester) async {
    var requests = 0;
    final store = DownloadStore(history: MemoryHistory([
      sampleDownload('1', 'retry.jpg', DownloadStatus.failed),
      sampleDownload('2', 'keep-on-device.jpg', DownloadStatus.completed),
    ]), runner: (_, _, _, _) async { requests++; return 'content://provider/document/retried'; });
    await store.initialize();
    await tester.pumpWidget(downloadApp(store));
    await tester.pumpAndSettle();
    expect(find.text('Download failed'), findsOneWidget);
    await tester.tap(find.byTooltip('Retry'));
    await tester.pumpAndSettle();
    expect(requests, 1);
    expect(store.state.entries.every((entry) => entry.status == DownloadStatus.completed), isTrue);
    await tester.tap(find.byTooltip('Clear completed history'));
    await tester.pumpAndSettle();
    expect(store.state.entries, isEmpty);
    expect(find.text('Your downloads will appear here. Save media from a post to get started.'), findsOneWidget);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox());
    await store.destroy();
  });
}
