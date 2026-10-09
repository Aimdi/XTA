import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/downloads/download_transfer.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/speech/offline_voice_catalog.dart';
import 'package:xta/speech/offline_voices_section.dart';
import 'package:xta/speech/voice_download_store.dart';

final german = offlineVoiceById('de-thorsten-emotional')!;
final english = offlineVoiceById('en-libritts-r')!;

/// A store whose downloads wait until the test lets them finish.
class Harness {
  final requested = <Uri>[];
  final cancelled = <String>[];
  final deleted = <String>[];
  Completer<void> transferDone = Completer<void>();
  late final Directory root = Directory.systemTemp.createTempSync('voices-');

  late final store = VoiceDownloadStore(
    root: () async => root,
    transfer: (save) => (entry, cancellation, progress, phase) async {
      requested.add(entry.uri);
      cancellation.onCancel(() => cancelled.add(entry.id));
      progress(0, entry.uri == german.url ? german.archiveBytes : null);
      await transferDone.future;
      throw const DownloadCancelled();
    },
  );
}

Future<void> pump(
  WidgetTester tester,
  VoiceDownloadStore store, {
  double scale = 1,
}) async {
  tester.view.physicalSize = const Size(320, 640);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    PrefService(
      service: PrefServiceCache(defaults: {optionTtsOfflineVoice: true}),
      child: MaterialApp(
        localizationsDelegates: const [
          L10n.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
        ],
        supportedLocales: L10n.delegate.supportedLocales,
        builder: (context, app) => MediaQuery(
          data: MediaQuery.of(
            context,
          ).copyWith(textScaler: TextScaler.linear(scale)),
          child: app!,
        ),
        home: Scaffold(
          body: ListView(children: [OfflineVoicesSection(store: store)]),
        ),
      ),
    ),
  );
  await tester.pump();
}

/// Lets real file system work, which fake test time does not run, finish:
/// each await on it needs a round of real time and then a pump.
Future<void> settleFiles(WidgetTester tester) async {
  for (var round = 0; round < 6; round++) {
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 20)),
    );
    await tester.pump();
  }
}

Finder tileOf(OfflineVoice voice) => find.ancestor(
  of: find.text(voice.name),
  matching: find.byType(OfflineVoiceTile),
);

Finder inTile(OfflineVoice voice, Finder finder) =>
    find.descendant(of: tileOf(voice), matching: finder);

void main() {
  late Harness harness;

  setUp(() => harness = Harness());
  tearDown(() {
    if (!harness.transferDone.isCompleted) harness.transferDone.complete();
    harness.root.deleteSync(recursive: true);
  });

  testWidgets('lists every voice with language, size and licence', (
    tester,
  ) async {
    await pump(tester, harness.store);

    expect(find.text('Downloaded voices'), findsOneWidget);
    expect(find.text('Use a downloaded voice'), findsOneWidget);
    expect(find.textContaining('GitHub (k2-fsa/sherpa-onnx)'), findsOneWidget);
    expect(
      find.text('Deutsch (Deutschland) · 22.4 MiB · License: CC0-1.0'),
      findsOneWidget,
    );
    expect(
      find.textContaining('English (United States) · 22.3 MiB'),
      findsOneWidget,
    );
    expect(find.byTooltip('Download'), findsNWidgets(2));
    expect(harness.requested, isEmpty, reason: 'nothing until a tap');
  });

  testWidgets('download shows progress and can be cancelled', (tester) async {
    await pump(tester, harness.store);

    await tester.tap(inTile(german, find.byTooltip('Download')));
    await settleFiles(tester);

    expect(harness.requested, [german.url]);
    expect(inTile(german, find.byType(LinearProgressIndicator)), findsOne);
    expect(inTile(german, find.textContaining('0 B / 22.4 MiB')), findsOne);
    expect(inTile(english, find.byTooltip('Download')), findsOne);

    await tester.tap(inTile(german, find.byTooltip('Cancel')));
    harness.transferDone.complete();
    await settleFiles(tester);

    expect(harness.cancelled, ['voice-${german.id}']);
    expect(inTile(german, find.byTooltip('Download')), findsOne);
    expect(find.byType(LinearProgressIndicator), findsNothing);
  });

  testWidgets('installing, ready and failed states', (tester) async {
    await pump(tester, harness.store);
    harness.store.update({
      german.id: const VoiceInstalling(),
      english.id: const VoiceFailed(VoiceFailure.checksum),
    });
    await tester.pump();

    expect(inTile(german, find.text('Checking and unpacking…')), findsOne);
    expect(inTile(german, find.byType(CircularProgressIndicator)), findsOne);
    expect(
      inTile(english, find.textContaining('did not match the expected file')),
      findsOne,
    );
    expect(inTile(english, find.byTooltip('Retry')), findsOne);

    harness.store.update({german.id: VoiceReady('/x', 40 * 1024 * 1024)});
    await tester.pump();
    expect(inTile(german, find.text('On this device · 40 MiB')), findsOne);
    expect(inTile(german, find.byTooltip('Delete')), findsOne);
  });

  testWidgets('delete asks first, then removes the voice', (tester) async {
    final folder = Directory('${harness.root.path}/${german.id}')
      ..createSync(recursive: true);
    harness.store.update({german.id: VoiceReady(folder.path, 1)});
    await pump(tester, harness.store);

    await tester.tap(inTile(german, find.byTooltip('Delete')));
    await tester.pumpAndSettle();
    expect(find.text('Delete Thorsten?'), findsOneWidget);
    await tester.tap(find.widgetWithText(TextButton, 'Cancel'));
    await tester.pumpAndSettle();
    expect(harness.store.installOf(german.id), isA<VoiceReady>());

    await tester.tap(inTile(german, find.byTooltip('Delete')));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(TextButton, 'Delete'));
    await tester.pumpAndSettle();
    await settleFiles(tester);

    expect(harness.store.installOf(german.id), isA<VoiceAbsent>());
    expect(folder.existsSync(), isFalse);
  });

  testWidgets('fits 320 dp at large text with full-size tap targets', (
    tester,
  ) async {
    harness.store.update({
      german.id: const VoiceDownloading(5 << 20, 23479221),
      english.id: VoiceReady('/x', 40 << 20),
    });
    await pump(tester, harness.store, scale: 2);

    expect(tester.takeException(), isNull);
    expect(inTile(german, find.byTooltip('Cancel')), findsOne);
    await expectLater(tester, meetsGuideline(androidTapTargetGuideline));
    for (final button in find.byType(IconButton).evaluate()) {
      final box = button.renderObject! as RenderBox;
      expect(box.size.width, greaterThanOrEqualTo(48));
      expect(
        box.localToGlobal(box.size.bottomRight(Offset.zero)).dx,
        lessThanOrEqualTo(320),
      );
    }
  });
}
