import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:image/image.dart' as img;
import 'package:provider/provider.dart';
import 'package:provider/single_child_widget.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_illust_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_ugoira.dart';
import 'package:xta/plugins/pixiv/pixiv_ugoira_export.dart';
import 'package:xta/plugins/pixiv/pixiv_ugoira_save.dart';

import 'support/pixiv_reader_harness.dart';
import 'support/pixiv_zip_fixture.dart';

Uint8List _frame(int red, {int size = 4}) => img.encodePng(
  img.fill(
    img.Image(width: size, height: size),
    color: img.ColorRgb8(red, 40, 200),
  ),
);

List<PixivFrameBytes> _frames(List<int> delays, {int size = 4}) => [
  for (final (index, delay) in delays.indexed)
    (bytes: _frame(index * 60, size: size), delay: Duration(milliseconds: delay)),
];

PixivUgoiraSource _source() =>
    PixivUgoiraSource(archive: Uint8List.fromList([80, 75, 1, 2]), frames: _frames([60, 60]));

const _ugoira = PixivUgoira(
  zipUrl: 'https://i.pximg.net/img-zip-ugoira/120_ugoira600x600.zip',
  frames: [
    PixivUgoiraFrame(file: '0.png', delay: Duration(milliseconds: 80)),
    PixivUgoiraFrame(file: '1.png', delay: Duration(milliseconds: 120)),
  ],
);

class _FakeGifEncoder extends PixivGifEncoder {
  final calls = <int>[];

  @override
  Future<Uint8List> encode(
    List<PixivFrameBytes> frames, {
    required void Function(int done) onFrame,
    required Future<void> cancelled,
  }) async {
    calls.add(frames.length);
    for (var done = 1; done <= frames.length; done++) {
      onFrame(done);
    }
    return Uint8List.fromList([71, 73, 70]);
  }
}

Future<void> _saveFromPageSheet(WidgetTester tester, String action) async {
  await tester.longPressAt(tester.getTopLeft(find.byType(PageView)) + const Offset(40, 40));
  await settlePixiv(tester);
  await tester.tap(find.byKey(ValueKey('pixiv-page-action-$action')));
  await settlePixiv(tester);
}

void main() {
  group('GIF encoding', () {
    test('delays become hundredths of a second, never under two', () {
      expect(pixivGifCentiseconds(const Duration(milliseconds: 60)), 6);
      expect(pixivGifCentiseconds(const Duration(milliseconds: 125)), 13);
      expect(pixivGifCentiseconds(const Duration(milliseconds: 5)), 2);
      expect(pixivGifCentiseconds(const Duration(seconds: 10)), 1000);
    });

    test('keeps every frame and its own delay, and loops', () {
      final done = <int>[];
      final bytes = encodePixivGif(_frames([60, 120, 200]), onFrame: done.add);

      final gif = img.decodeGif(bytes)!;
      expect(gif.numFrames, 3);
      expect(gif.frames.map((frame) => frame.frameDuration), [60, 120, 200]);
      expect(gif.loopCount, 0);
      expect(done, [1, 2, 3]);
    });

    test('an unreadable frame fails instead of saving a broken file', () {
      final frames = [
        (bytes: Uint8List.fromList([1, 2, 3]), delay: const Duration(milliseconds: 50)),
      ];
      expect(() => encodePixivGif(frames), throwsFormatException);
    });

    test('runs on a background isolate and reports each frame', () async {
      final done = <int>[];
      final bytes = await const PixivGifEncoder().encode(
        _frames([40, 80]),
        onFrame: done.add,
        cancelled: Completer<void>().future,
      );
      expect(img.decodeGif(bytes)!.numFrames, 2);
      expect(done, [1, 2]);
    });

    test('cancelling stops the background encode', () async {
      final cancel = Completer<void>()..complete();
      await expectLater(
        const PixivGifEncoder().encode(
          _frames(List.filled(12, 50), size: 96),
          onFrame: (_) {},
          cancelled: cancel.future,
        ),
        throwsA(isA<PixivExportCancelled>()),
      );
    });
  });

  test('frames come out of the archive in the metadata order', () {
    final archive = pixivZipFixture({
      '1.png': [2],
      '0.png': [1],
    });
    final frames = pixivUgoiraFrames(_ugoira, archive);
    expect(frames.map((frame) => frame.bytes), [
      [1],
      [2],
    ]);
    expect(frames.map((frame) => frame.delay.inMilliseconds), [80, 120]);
    expect(
      () => pixivUgoiraFrames(
        _ugoira,
        pixivZipFixture({
          'other.png': [1],
        }),
      ),
      throwsFormatException,
    );
  });

  group('PixivUgoiraExportStore', () {
    test('the ZIP path saves the archive bytes as fetched, without encoding', () async {
      final saved = <Uint8List>[];
      final encoder = _FakeGifEncoder();
      final store = PixivUgoiraExportStore(
        load: () async => _source(),
        encodeGif: encoder.encode,
        save: (bytes) async {
          saved.add(bytes);
          return true;
        },
      );
      addTearDown(store.destroy);

      expect(await store.run(PixivUgoiraFormat.zip), isTrue);
      expect(saved.single, [80, 75, 1, 2]);
      expect(encoder.calls, isEmpty);
      expect(store.state.phase, PixivExportPhase.saved);
    });

    test('the GIF path encodes with progress, then saves the GIF', () async {
      final phases = <(PixivExportPhase, int)>[];
      final saved = <Uint8List>[];
      final store = PixivUgoiraExportStore(
        load: () async => _source(),
        encodeGif: _FakeGifEncoder().encode,
        save: (bytes) async {
          saved.add(bytes);
          return true;
        },
      );
      addTearDown(store.destroy);
      store.observer(onState: (progress) => phases.add((progress.phase, progress.frame)));

      expect(await store.run(PixivUgoiraFormat.gif), isTrue);
      expect(saved.single, [71, 73, 70]);
      expect(phases, [
        (PixivExportPhase.encoding, 0),
        (PixivExportPhase.encoding, 1),
        (PixivExportPhase.encoding, 2),
        (PixivExportPhase.saving, 0),
        (PixivExportPhase.saved, 0),
      ]);
    });

    test('cancel during encoding stops it and saves nothing', () async {
      final started = Completer<void>();
      var saves = 0;
      final store = PixivUgoiraExportStore(
        load: () async => _source(),
        encodeGif: (frames, {required onFrame, required cancelled}) async {
          started.complete();
          await cancelled;
          throw const PixivExportCancelled();
        },
        save: (_) async => ++saves > 0,
      );
      addTearDown(store.destroy);

      final run = store.run(PixivUgoiraFormat.gif);
      await started.future;
      store.cancel();

      expect(await run, isFalse);
      expect(saves, 0);
      expect(store.state.phase, PixivExportPhase.cancelled);
    });

    test('cancel while fetching ends the export at once and ignores the late response', () async {
      final response = Completer<PixivUgoiraSource>();
      var saves = 0;
      final store = PixivUgoiraExportStore(
        load: () => response.future,
        encodeGif: _FakeGifEncoder().encode,
        save: (_) async => ++saves > 0,
      );
      addTearDown(store.destroy);

      final run = store.run(PixivUgoiraFormat.zip);
      await pumpEventQueue();
      store.cancel();

      expect(await run, isFalse, reason: 'returns without waiting for the archive');
      expect(store.state.phase, PixivExportPhase.cancelled);
      response.complete(_source());
      await pumpEventQueue();
      expect(saves, 0);
      expect(store.state.phase, PixivExportPhase.cancelled);
    });

    test('a failed fetch or a refused write ends as failed', () async {
      final fetch = PixivUgoiraExportStore(
        load: () async => throw StateError('offline'),
        encodeGif: _FakeGifEncoder().encode,
        save: (_) async => true,
      );
      final write = PixivUgoiraExportStore(
        load: () async => _source(),
        encodeGif: _FakeGifEncoder().encode,
        save: (_) async => false,
      );
      addTearDown(fetch.destroy);
      addTearDown(write.destroy);

      expect(await fetch.run(PixivUgoiraFormat.zip), isFalse);
      expect(fetch.state.phase, PixivExportPhase.failed);
      expect(await write.run(PixivUgoiraFormat.zip), isFalse);
      expect(write.state.phase, PixivExportPhase.failed);
    });
  });

  group('saving from the work', () {
    final archive = pixivZipFixture({'0.png': _frame(0), '1.png': _frame(200)});

    Future<PixivHarness> pumpUgoira(WidgetTester tester, {List<SingleChildWidget> encoder = const []}) => pumpPixiv(
      tester,
      PixivIllustScreen(illust: pixivWork(pages: 1, type: 'ugoira')),
      client: (prefs) => FakePixivClient(
        prefs,
        detail: pixivWork(pages: 1, type: 'ugoira'),
        ugoira: _ugoira,
        archiveBytes: archive,
      ),
      extraProviders: encoder,
    );

    testWidgets('the page sheet of an ugoira offers GIF, with a slow note, and ZIP', (tester) async {
      await pumpUgoira(tester);
      await tester.longPressAt(tester.getTopLeft(find.byType(PageView)) + const Offset(40, 40));
      await settlePixiv(tester);

      expect(find.text('Save as GIF'), findsOneWidget);
      expect(find.text('Encoding can take a minute'), findsOneWidget);
      expect(find.text('Save frames as ZIP'), findsOneWidget);
      expect(tester.getSize(find.byKey(const ValueKey('pixiv-page-action-saveGif'))).height, greaterThanOrEqualTo(48));
      await disposePixiv(tester);
    });

    testWidgets('Save frames as ZIP writes the archive under the template and marks the work saved', (tester) async {
      final harness = await pumpUgoira(tester);

      await _saveFromPageSheet(tester, 'saveZip');

      final file = harness.downloader.files.single;
      expect(file.fileName, '120_p0.zip');
      expect(file.bytes, archive);
      expect(file.treeUri, 'content://tree/pictures');
      expect(file.subfolder, isNull);
      expect(harness.downloads.isSaved(120, 0), isTrue);
      expect(find.text('Frames saved as ZIP'), findsOneWidget);
      await disposePixiv(tester);
    });

    testWidgets('Save as GIF encodes the fetched frames and saves a .gif', (tester) async {
      final encoder = _FakeGifEncoder();
      final harness = await pumpUgoira(tester, encoder: [Provider<PixivGifEncoder>.value(value: encoder)]);

      await _saveFromPageSheet(tester, 'saveGif');

      expect(encoder.calls, [2]);
      expect(harness.downloader.files.single.fileName, '120_p0.gif');
      expect(harness.downloader.files.single.bytes, [71, 73, 70]);
      expect(find.text('Animation saved as GIF'), findsOneWidget);
      await disposePixiv(tester);
    });

    testWidgets('exporting a work saved before asks first, and Cancel saves nothing', (tester) async {
      final harness = await pumpUgoira(tester);
      await harness.downloads.record(120, const [0]);

      await _saveFromPageSheet(tester, 'saveGif');
      expect(find.text('Already saved'), findsOneWidget);
      await tester.tap(find.text('Cancel'));
      await settlePixiv(tester);
      expect(harness.downloader.files, isEmpty);
      expect(harness.client.calls.where((call) => call.startsWith('ugoira')), isEmpty);

      await _saveFromPageSheet(tester, 'saveZip');
      await tester.tap(find.byKey(const ValueKey('pixiv-resave-all')));
      await settlePixiv(tester);
      expect(harness.downloader.files.single.fileName, '120_p0.zip');
      await disposePixiv(tester);
    });

    testWidgets('declining the folder saves nothing', (tester) async {
      final harness = await pumpUgoira(tester);
      harness.downloader.folder = null;

      await _saveFromPageSheet(tester, 'saveZip');

      expect(harness.downloader.files, isEmpty);
      expect(harness.client.calls.where((call) => call.startsWith('ugoira')), isEmpty);
      await disposePixiv(tester);
    });
  });

  testWidgets('the progress row shows the frame, the slow note and Cancel only while it can stop', (tester) async {
    var cancels = 0;
    // The saving ring spins without end, so this pumps once instead of settling.
    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: const [
          L10n.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
        ],
        supportedLocales: L10n.delegate.supportedLocales,
        home: Scaffold(
          body: Column(
            children: [
              PixivExportProgressRow(
                key: const ValueKey('encoding'),
                progress: const PixivExportProgress(PixivExportPhase.encoding, frame: 1, frames: 5),
                onCancel: () => cancels++,
              ),
              PixivExportProgressRow(
                key: const ValueKey('saving'),
                progress: const PixivExportProgress(PixivExportPhase.saving),
                onCancel: () => cancels++,
              ),
            ],
          ),
        ),
      ),
    );
    await tester.pump();

    Finder inRow(String key, Finder finder) => find.descendant(of: find.byKey(ValueKey(key)), matching: finder);
    expect(inRow('encoding', find.text('Encoding frame 2 of 5…')), findsOneWidget);
    expect(inRow('encoding', find.text('Encoding can take a minute')), findsOneWidget);
    await tester.tap(inRow('encoding', find.text('Cancel')));
    expect(cancels, 1);
    expect(inRow('saving', find.text('Saving…')), findsOneWidget);
    expect(inRow('saving', find.text('Cancel')), findsNothing);
    await tester.pumpWidget(const SizedBox());
  });
}
