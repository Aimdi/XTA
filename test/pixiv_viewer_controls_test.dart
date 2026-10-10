import 'dart:async';
import 'dart:io';
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:share_plus/share_plus.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_image.dart';
import 'package:xta/plugins/pixiv/pixiv_quality.dart';
import 'package:xta/plugins/pixiv/pixiv_reader_bar.dart';
import 'package:xta/plugins/pixiv/pixiv_reader_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_reader_store.dart';
import 'package:xta/plugins/pixiv/pixiv_share_image.dart';
import 'package:xta/plugins/pixiv/pixiv_ugoira.dart';
import 'package:xta/plugins/pixiv/pixiv_ugoira_view.dart';
import 'package:xta/plugins/pixiv/pixiv_zoomable.dart';

import 'support/pixiv_reader_harness.dart';
import 'support/pixiv_zip_fixture.dart';

class _RecordingSharer extends PixivImageSharer {
  final shared = <XFile>[];

  _RecordingSharer(super.client, {required super.tempDir});

  @override
  Future<void> send(XFile file) async => shared.add(file);
}

List<String> _pageUrls(WidgetTester tester) => [
  for (final image in tester.widgetList<PixivNetworkImage>(find.byType(PixivNetworkImage))) image.url,
];

Future<ui.Image> _frame() {
  final recorder = ui.PictureRecorder();
  Canvas(recorder).drawRect(const Rect.fromLTWH(0, 0, 2, 2), Paint()..color = Colors.red);
  return recorder.endRecording().toImage(2, 2);
}

PixivUgoiraStore _ugoira({Future<void>? archiveGate, required ui.Image frame}) => PixivUgoiraStore(
  metadata: () async => const PixivUgoira(
    zipUrl: 'https://i.pximg.net/z.zip',
    frames: [PixivUgoiraFrame(file: 'a', delay: Duration(milliseconds: 50))],
  ),
  archive: (_) async {
    await archiveGate;
    return pixivZipFixture({
      'a': [0],
    });
  },
  decode: (_) async => frame.clone(),
);

void main() {
  group('reader store', () {
    test('HD flips for the visit and decodes whole, as does a zoom', () {
      final store = PixivReaderStore(pageCount: 3, hd: true);
      expect((store.state.hd, store.state.fullResolution), (true, true));
      store.toggleHd();
      expect((store.state.hd, store.state.fullResolution), (false, false));
      store.setZoomed(true);
      expect(store.state.fullResolution, isTrue);
      store.destroy();
    });

    test('turning a page in the horizontal reader lets go of the zoom; the vertical one keeps it', () {
      final store = PixivReaderStore(pageCount: 3, vertical: false)..setZoomed(true);
      store.observedPage(1);
      expect((store.state.pageIndex, store.state.zoomed), (1, false));
      store
        ..toggleDirection()
        ..setZoomed(true)
        ..pageVisibility(2, 100);
      expect((store.state.pageIndex, store.state.zoomed), (2, true));
      store.destroy();
    });

    test('a loaded page is remembered once, and nothing changes after closing', () {
      final store = PixivReaderStore(pageCount: 2);
      var updates = 0;
      store.observer(onState: (_) => updates++);
      store
        ..pageLoaded('a')
        ..pageLoaded('a');
      expect(store.state.loaded, {'a'});
      expect(updates, 1);
      store.destroy();
      store.pageLoaded('b');
      expect(store.state.loaded, {'a'});
    });
  });

  group('reader bar', () {
    Future<void> pumpBar(WidgetTester tester, {VoidCallback? onShare, required VoidCallback onToggleHd}) => pumpPixiv(
      tester,
      Scaffold(
        bottomNavigationBar: PixivReaderBar(
          state: const PixivReaderState(hd: true),
          pages: 1,
          onShare: onShare,
          onToggleHd: onToggleHd,
          onScrub: (_) {},
          onJump: (_) {},
          onOverview: () {},
        ),
      ),
    );

    testWidgets('share waits for the page and HD shows whether it is on', (tester) async {
      var hd = 0;
      await pumpBar(tester, onToggleHd: () => hd++);
      expect(tester.widget<IconButton>(find.byKey(const ValueKey('pixiv-reader-share'))).onPressed, isNull);
      final toggle = tester.widget<IconButton>(find.byKey(const ValueKey('pixiv-reader-hd')));
      expect(toggle.isSelected, isTrue);
      await tester.tap(find.byTooltip('Original quality'));
      expect(hd, 1);
      await disposePixiv(tester);
    });

    testWidgets('a loaded page can be shared', (tester) async {
      var shared = 0;
      await pumpBar(tester, onShare: () => shared++, onToggleHd: () {});
      await tester.tap(find.byTooltip('Share image'));
      expect(shared, 1);
      await disposePixiv(tester);
    });
  });

  group('reader screen', () {
    testWidgets('starts on the originals when full-screen pages are set to original, and HD switches back', (
      tester,
    ) async {
      final work = pixivWork(pages: 2);
      await pumpPixiv(
        tester,
        PixivReaderScreen(illust: work, vertical: false),
        client: (prefs) {
          prefs.set(optionPluginPixivQualityReader, PixivImageQuality.original.name);
          return FakePixivClient(prefs);
        },
      );
      expect(_pageUrls(tester).first, work.originalUrls.first);
      expect(tester.widget<PixivNetworkImage>(find.byType(PixivNetworkImage).first).fullResolution, isTrue);

      await tester.tap(find.byKey(const ValueKey('pixiv-reader-hd')));
      await settlePixiv(tester);
      expect(_pageUrls(tester).first, work.pageUrls.first);
      expect(tester.widget<PixivNetworkImage>(find.byType(PixivNetworkImage).first).fullResolution, isFalse);
      expect(tester.widget<IconButton>(find.byKey(const ValueKey('pixiv-reader-share'))).onPressed, isNull);
      await disposePixiv(tester);
    });

    testWidgets('the vertical reader zooms on double-tap and then decodes pages whole', (tester) async {
      await pumpPixiv(tester, PixivReaderScreen(illust: pixivWork(pages: 3)));
      expect(find.byType(PixivZoomable), findsOneWidget);
      expect(tester.widget<PixivNetworkImage>(find.byType(PixivNetworkImage).first).fullResolution, isFalse);

      final list = find.byType(ListView);
      await tester.tapAt(tester.getCenter(list));
      await tester.pump(const Duration(milliseconds: 60));
      await tester.tapAt(tester.getCenter(list));
      await settlePixiv(tester);
      expect(tester.widget<PixivNetworkImage>(find.byType(PixivNetworkImage).first).fullResolution, isTrue);
      await disposePixiv(tester);
    });
  });

  group('sharing a page', () {
    test('goes out under Pixiv\'s name with the type of the file shown', () {
      final work = pixivWork();
      expect(pixivSharedImageName(work, 2, work.originalUrls[2]), (name: '120_p2.png', mimeType: 'image/png'));
      expect(pixivSharedImageName(work, 0, work.pageUrls[0]), (name: '120_p0.jpg', mimeType: 'image/jpeg'));
      expect(pixivSharedImageName(work, 0, 'https://i.pximg.net/img/x'), (name: '120_p0.jpg', mimeType: 'image/jpeg'));
    });

    test('downloads the page when the cache has no copy and hands the file to the share sheet', () async {
      final folder = await Directory.systemTemp.createTemp('xta-pixiv-share');
      addTearDown(() => folder.delete(recursive: true));
      final asked = <http.BaseRequest>[];
      final sharer = _RecordingSharer(
        MockClient((request) async {
          asked.add(request);
          return http.Response.bytes([9, 8, 7], 200);
        }),
        tempDir: () async => folder,
      );
      final work = pixivWork();
      expect(await sharer.share(work, 1, work.originalUrls[1]), isTrue);
      expect(asked.single.headers['Referer'], 'https://www.pixiv.net/');
      final file = sharer.shared.single;
      expect((file.name, file.mimeType), ('120_p1.png', 'image/png'));
      expect(await file.readAsBytes(), Uint8List.fromList([9, 8, 7]));
    });

    test('says so when the page cannot be had', () async {
      final folder = await Directory.systemTemp.createTemp('xta-pixiv-share');
      addTearDown(() => folder.delete(recursive: true));
      final sharer = _RecordingSharer(MockClient((_) async => http.Response('', 404)), tempDir: () async => folder);
      expect(await sharer.share(pixivWork(), 0, pixivWork().pageUrls[0]), isFalse);
      expect(sharer.shared, isEmpty);
    });
  });

  group('ugoira while covered', () {
    testWidgets('holding stops playback and resuming plays again; a reader\'s own pause stays', (tester) async {
      final frame = (await tester.runAsync(_frame))!;
      addTearDown(frame.dispose);
      final store = _ugoira(frame: frame);
      await store.play();
      expect(store.state.phase, PixivUgoiraPhase.playing);

      store.hold();
      expect(store.state.phase, PixivUgoiraPhase.paused);
      store.resume();
      await tester.pump();
      expect(store.state.phase, PixivUgoiraPhase.playing);

      store
        ..pause()
        ..hold()
        ..resume();
      expect(store.state.phase, PixivUgoiraPhase.paused);
      await store.destroy();
      await tester.pump(const Duration(seconds: 1));
    });

    testWidgets('covered while the frames download, it waits paused until shown again', (tester) async {
      final frame = (await tester.runAsync(_frame))!;
      addTearDown(frame.dispose);
      final gate = Completer<void>();
      final store = _ugoira(frame: frame, archiveGate: gate.future);
      final playing = store.play();
      expect(store.state.phase, PixivUgoiraPhase.loading);
      store.hold();
      gate.complete();
      await playing;
      expect(store.state.phase, PixivUgoiraPhase.paused);
      store.resume();
      await tester.pump();
      expect(store.state.phase, PixivUgoiraPhase.playing);
      await store.destroy();
      await tester.pump(const Duration(seconds: 1));
    });

    testWidgets('the view pauses while its ticker is off and plays on when it comes back', (tester) async {
      final frame = (await tester.runAsync(_frame))!;
      addTearDown(frame.dispose);
      final ticking = ValueNotifier(true);
      addTearDown(ticking.dispose);
      final work = pixivWork(pages: 1, type: 'ugoira');
      await pumpPixiv(
        tester,
        ValueListenableBuilder<bool>(
          valueListenable: ticking,
          builder: (context, enabled, _) => TickerMode(
            enabled: enabled,
            child: PixivUgoiraView(illust: work, poster: const SizedBox(), decode: (_) async => frame.clone()),
          ),
        ),
        client: (prefs) => FakePixivClient(
          prefs,
          ugoira: const PixivUgoira(
            zipUrl: 'https://i.pximg.net/z.zip',
            frames: [PixivUgoiraFrame(file: 'a', delay: Duration(milliseconds: 50))],
          ),
          archiveBytes: pixivZipFixture({
            'a': [0],
          }),
        ),
      );
      await tester.tap(find.byKey(const ValueKey('pixiv-ugoira-play')));
      for (var i = 0; i < 20 && find.byTooltip('Pause animation').evaluate().isEmpty; i++) {
        await tester.runAsync(() => Future<void>.delayed(const Duration(milliseconds: 10)));
        await tester.pump();
      }
      expect(find.byTooltip('Pause animation'), findsOneWidget);

      ticking.value = false;
      await tester.pump();
      expect(find.byTooltip('Play animation'), findsOneWidget);

      ticking.value = true;
      await tester.runAsync(() => Future<void>.delayed(const Duration(milliseconds: 10)));
      await tester.pump();
      expect(find.byTooltip('Pause animation'), findsOneWidget);
      await disposePixiv(tester);
    });
  });
}
