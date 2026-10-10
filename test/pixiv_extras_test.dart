import 'dart:async';
import 'dart:convert';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_store.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_grid.dart';
import 'package:xta/plugins/pixiv/pixiv_illust_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_reader_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_settings.dart';
import 'package:xta/plugins/pixiv/pixiv_ugoira.dart';
import 'package:xta/plugins/pixiv/pixiv_zoomable.dart';

import 'support/pixiv_reader_harness.dart';
import 'support/pixiv_zip_fixture.dart';

Future<ui.Image> _image(Color color) {
  final recorder = ui.PictureRecorder();
  Canvas(recorder).drawRect(const Rect.fromLTWH(0, 0, 4, 4), Paint()..color = color);
  return recorder.endRecording().toImage(4, 4);
}

Future<List<int>> _png(Color color) async {
  final image = await _image(color);
  final bytes = await image.toByteData(format: ui.ImageByteFormat.png);
  image.dispose();
  return bytes!.buffer.asUint8List();
}

Map<String, Object?> _illustJson(int id, {int aiType = 1}) => {
  'id': id,
  'title': 'Work $id',
  'type': 'illust',
  'image_urls': {'medium': 'https://i.pximg.net/c/540x540_70/${id}_p0.jpg'},
  'user': {'id': 1, 'name': 'A', 'account': 'a'},
  'page_count': 1,
  'illust_ai_type': aiType,
};

Future<void> _openDetailMenu(WidgetTester tester, String item) async {
  await tester.tap(find.byKey(const ValueKey('pixiv-illust-menu')));
  await settlePixiv(tester);
  await tester.tap(find.byKey(ValueKey('pixiv-illust-menu-$item')));
  await settlePixiv(tester);
}

void main() {
  group('double-tap zoom', () {
    test('zooming keeps the tapped point where it was', () {
      const focal = Offset(120, 300);
      final matrix = pixivZoomAt(focal, pixivDoubleTapScale);
      expect(MatrixUtils.transformPoint(matrix, focal), focal);
      expect(matrix.getMaxScaleOnAxis(), pixivDoubleTapScale);
    });

    testWidgets('a double-tap zooms in where tapped and the next one zooms back out', (tester) async {
      final transform = TransformationController();
      addTearDown(transform.dispose);
      var taps = 0;
      await tester.pumpWidget(
        MaterialApp(
          home: Center(
            child: SizedBox.square(
              dimension: 400,
              child: PixivZoomable(
                controller: transform,
                onTap: () => taps++,
                child: const ColoredBox(color: Colors.teal),
              ),
            ),
          ),
        ),
      );
      final point = tester.getTopLeft(find.byType(PixivZoomable)) + const Offset(100, 300);
      Future<void> doubleTap() async {
        await tester.tapAt(point);
        await tester.pump(const Duration(milliseconds: 60));
        await tester.tapAt(point);
        await tester.pumpAndSettle();
      }

      await doubleTap();
      expect(transform.value.getMaxScaleOnAxis(), closeTo(pixivDoubleTapScale, 0.001));
      expect(MatrixUtils.transformPoint(transform.value, const Offset(100, 300)), const Offset(100, 300));
      await doubleTap();
      expect(transform.value.getMaxScaleOnAxis(), closeTo(1, 0.001));
      expect(taps, 0);

      await tester.tapAt(point);
      await tester.pumpAndSettle(const Duration(milliseconds: 400));
      expect(taps, 1);
    });
  });

  testWidgets('tapping the detail image opens that page in the zoomable reader', (tester) async {
    await pumpPixiv(tester, PixivIllustScreen(illust: pixivWork()));
    await tester.tapAt(tester.getTopLeft(find.byType(PixivZoomable).first) + const Offset(40, 40));
    await tester.pump(const Duration(milliseconds: 400));
    await settlePixiv(tester);

    final reader = tester.widget<PixivReaderScreen>(find.byType(PixivReaderScreen));
    expect((reader.vertical, reader.initialPage), (false, 0));
    await disposePixiv(tester);
  });

  group('author works', () {
    testWidgets('the detail screen shows the author\'s other works and opens them', (tester) async {
      final harness = await pumpPixiv(
        tester,
        PixivIllustScreen(illust: pixivWork()),
        size: const Size(390, 1600),
        client: (prefs) => FakePixivClient(
          prefs,
          authorWorks: [pixivWork(), pixivWork(id: 121, pages: 1), pixivWork(id: 122, pages: 2)],
        ),
      );

      expect(harness.client.calls, contains('userIllusts:42'));
      expect(find.text('More by Mika'), findsOneWidget);
      expect(find.byKey(const ValueKey('pixiv-author-work-121')), findsOneWidget);
      expect(find.byKey(const ValueKey('pixiv-author-work-122')), findsOneWidget);
      expect(find.byKey(const ValueKey('pixiv-author-work-120')), findsNothing);
      expect(tester.getSize(find.byKey(const ValueKey('pixiv-author-works-header'))).height, greaterThanOrEqualTo(48));

      await tester.tap(find.byKey(const ValueKey('pixiv-author-work-121')));
      await settlePixiv(tester);
      expect(
        find.byWidgetPredicate((widget) => widget is PixivIllustScreen && widget.illust.id == 121),
        findsOneWidget,
      );
      await disposePixiv(tester);
    });

    testWidgets('leaving the detail before the author\'s works arrive is safe', (tester) async {
      final gate = Completer<void>();
      await pumpPixiv(
        tester,
        PixivIllustScreen(illust: pixivWork()),
        client: (prefs) => FakePixivClient(prefs, authorWorks: [pixivWork(id: 121)], authorWorksGate: gate.future),
      );
      await tester.pumpWidget(const SizedBox());
      gate.complete();
      await tester.pump(const Duration(seconds: 1));
      expect(tester.takeException(), isNull);
      await disposePixiv(tester);
    });

    testWidgets('an author with no other works adds nothing', (tester) async {
      await pumpPixiv(
        tester,
        PixivIllustScreen(illust: pixivWork()),
        size: const Size(390, 1600),
        client: (prefs) => FakePixivClient(prefs, authorWorks: [pixivWork()]),
      );
      expect(find.text('More by Mika'), findsNothing);
      await disposePixiv(tester);
    });
  });

  testWidgets('tags show Pixiv\'s spelling with the translation beside it', (tester) async {
    final tags = [const PixivTag(name: 'オリジナル', translatedName: 'original'), const PixivTag(name: '漫画')];
    await pumpPixiv(
      tester,
      PixivIllustScreen(illust: pixivWork(tags: tags)),
      size: const Size(390, 1600),
      client: (prefs) => FakePixivClient(prefs, detail: pixivWork(tags: tags)),
    );
    expect(find.text('#オリジナル  original', findRichText: true), findsOneWidget);
    expect(find.text('#漫画'), findsOneWidget);
    await disposePixiv(tester);
  });

  group('detail menu', () {
    testWidgets('copies the link and downloads every page', (tester) async {
      String? copied;
      tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(SystemChannels.platform, (call) async {
        if (call.method == 'Clipboard.setData') copied = (call.arguments as Map)['text'] as String?;
        return null;
      });
      addTearDown(() => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(SystemChannels.platform, null));
      final harness = await pumpPixiv(tester, PixivIllustScreen(illust: pixivWork()));

      await _openDetailMenu(tester, 'copyLink');
      expect(copied, 'https://www.pixiv.net/artworks/120');
      expect(find.text('Link copied'), findsOneWidget);

      await _openDetailMenu(tester, 'downloadAll');
      expect(harness.downloader.requests, hasLength(8));
      expect(find.text('Files saved: 8 / 8'), findsOneWidget);

      await tester.tap(find.byKey(const ValueKey('pixiv-illust-download')));
      await settlePixiv(tester);
      expect(harness.downloader.pages, [0]);
      await disposePixiv(tester);
    });
  });

  group('AI-generated works', () {
    test('the creator\'s AI flag is read and can be filtered out', () {
      final json = {
        'illusts': [_illustJson(1), _illustJson(2, aiType: 2)],
      };
      expect(parsePixivIllustList(json).map((illust) => (illust.id, illust.isAi)), [(1, false), (2, true)]);
      expect(parsePixivIllustList(json, includeAi: false).map((illust) => illust.id), [1]);
    });

    test('hiding AI works filters feeds but never the reader\'s own bookmarks', () async {
      final prefs = PrefServiceCache(
        cache: {
          optionPluginPixivRefreshToken: 'refresh',
          optionPluginPixivAccessToken: 'access',
          optionPluginPixivAccessExpiresAt: DateTime.now().add(const Duration(hours: 1)).toIso8601String(),
          optionPluginPixivHideAi: true,
        },
      );
      final client = PixivClient(
        prefs,
        httpClient: MockClient(
          (_) async => http.Response(
            jsonEncode({
              'illusts': [_illustJson(1), _illustJson(2, aiType: 2)],
            }),
            200,
          ),
        ),
      );
      expect((await client.following()).illusts.map((illust) => illust.id), [1]);
      expect((await client.bookmarks(userId: 7)).illusts.map((illust) => illust.id), [1, 2]);
      await prefs.set(optionPluginPixivHideAi, false);
      expect((await client.following()).illusts.map((illust) => illust.id), [1, 2]);
    });

    testWidgets('AI works carry a badge in the grid', (tester) async {
      await tester.pumpWidget(
        Provider<PixivBookmarkStore>.value(
          value: PixivBookmarkStore(),
          child: MaterialApp(
            localizationsDelegates: const [L10n.delegate, GlobalMaterialLocalizations.delegate],
            supportedLocales: L10n.delegate.supportedLocales,
            home: Scaffold(
              body: SizedBox(width: 200, child: PixivIllustTile(illust: pixivWork(pages: 1, ai: true))),
            ),
          ),
        ),
      );
      expect(find.text('AI'), findsOneWidget);
      await tester.pumpWidget(const SizedBox());
      await tester.pump(const Duration(seconds: 1));
    });

    testWidgets('the settings switch turns AI hiding on and off', (tester) async {
      final prefs = PrefServiceCache();
      await tester.pumpWidget(
        PrefService(
          service: prefs,
          child: MaterialApp(
            localizationsDelegates: const [L10n.delegate, GlobalMaterialLocalizations.delegate],
            supportedLocales: L10n.delegate.supportedLocales,
            home: const Scaffold(body: PixivHideAiSwitch()),
          ),
        ),
      );
      expect(find.text('Hide AI-generated works'), findsOneWidget);
      expect(tester.widget<SwitchListTile>(find.byType(SwitchListTile)).value, isFalse);
      await tester.tap(find.byType(SwitchListTile));
      await tester.pumpAndSettle();
      expect(prefs.get<bool>(optionPluginPixivHideAi), isTrue);
      expect(tester.widget<SwitchListTile>(find.byType(SwitchListTile)).value, isTrue);
    });
  });

  group('ugoira', () {
    test('metadata lists the archive and each frame\'s delay', () {
      final ugoira = parsePixivUgoira({
        'ugoira_metadata': {
          'zip_urls': {'medium': 'https://i.pximg.net/img-zip-ugoira/9_ugoira600x600.zip'},
          'frames': [
            {'file': '000000.jpg', 'delay': 80},
            {'file': '000001.jpg', 'delay': 0},
            {'delay': 50},
          ],
        },
      })!;
      expect(ugoira.zipUrl, endsWith('9_ugoira600x600.zip'));
      expect(ugoira.frames.map((frame) => (frame.file, frame.delay.inMilliseconds)), [
        ('000000.jpg', 80),
        ('000001.jpg', 16),
      ]);
      expect(parsePixivUgoira({'ugoira_metadata': <String, Object?>{}}), isNull);
    });

    for (final deflate in [false, true]) {
      test('frames come out of a ${deflate ? 'deflated' : 'stored'} archive intact', () {
        final files = {'000000.jpg': List.generate(300, (i) => i % 7), '000001.jpg': List.generate(90, (i) => 255 - i)};
        final read = readPixivZip(pixivZipFixture(files, deflate: deflate));
        expect(read.keys, files.keys);
        for (final name in files.keys) {
          expect(read[name], files[name]);
        }
      });
    }

    test('a damaged archive is refused', () {
      expect(() => readPixivZip(Uint8List.fromList([1, 2, 3, 4])), throwsFormatException);
    });

    testWidgets('playback shows frames in order for their delays and stops when paused', (tester) async {
      final images = await tester.runAsync(
        () => Future.wait([_image(Colors.red), _image(Colors.green), _image(Colors.blue)]),
      );
      addTearDown(() {
        for (final image in images!) {
          image.dispose();
        }
      });
      final shownAs = Map<ui.Image, int>.identity();
      final decoded = <int>[];
      final store = PixivUgoiraStore(
        metadata: () async => const PixivUgoira(
          zipUrl: 'https://i.pximg.net/z.zip',
          frames: [
            PixivUgoiraFrame(file: 'a', delay: Duration(milliseconds: 100)),
            PixivUgoiraFrame(file: 'b', delay: Duration(milliseconds: 200)),
            PixivUgoiraFrame(file: 'c', delay: Duration(milliseconds: 100)),
          ],
        ),
        archive: (_) async => pixivZipFixture({
          'a': [0],
          'b': [1],
          'c': [2],
        }),
        decode: (bytes) async {
          decoded.add(bytes.first);
          final image = images![bytes.first].clone();
          shownAs[image] = bytes.first;
          return image;
        },
      );
      int? shown() => store.state.frame == null ? null : shownAs[store.state.frame!];

      await store.play();
      await tester.pump();
      expect((store.state.phase, shown()), (PixivUgoiraPhase.playing, 0));
      await tester.pump(const Duration(milliseconds: 100));
      expect(shown(), 1);
      await tester.pump(const Duration(milliseconds: 150));
      expect(shown(), 1);
      await tester.pump(const Duration(milliseconds: 50));
      expect(shown(), 2);
      await tester.pump(const Duration(milliseconds: 100));
      expect(shown(), 0);

      store.pause();
      final settled = decoded.length;
      await tester.pump(const Duration(seconds: 2));
      expect(store.state.phase, PixivUgoiraPhase.paused);
      expect(decoded.length, settled);
      expect(shown(), 0);
      await store.destroy();
    });

    testWidgets('the detail screen plays an ugoira in place and pauses it', (tester) async {
      final frames = await tester.runAsync(() => Future.wait([_png(Colors.red), _png(Colors.blue)]));
      final harness = await pumpPixiv(
        tester,
        PixivIllustScreen(illust: pixivWork(pages: 1, type: 'ugoira')),
        client: (prefs) => FakePixivClient(
          prefs,
          detail: pixivWork(pages: 1, type: 'ugoira'),
          ugoira: const PixivUgoira(
            zipUrl: 'https://i.pximg.net/img-zip-ugoira/120_ugoira600x600.zip',
            frames: [
              PixivUgoiraFrame(file: '0.png', delay: Duration(milliseconds: 60)),
              PixivUgoiraFrame(file: '1.png', delay: Duration(milliseconds: 60)),
            ],
          ),
          archiveBytes: pixivZipFixture({'0.png': frames![0], '1.png': frames[1]}),
        ),
      );
      expect(find.byTooltip('Play animation'), findsOneWidget);

      await tester.tap(find.byKey(const ValueKey('pixiv-ugoira-play')));
      for (var i = 0; i < 50 && find.byType(RawImage).evaluate().isEmpty; i++) {
        await tester.runAsync(() => Future<void>.delayed(const Duration(milliseconds: 20)));
        await tester.pump(const Duration(milliseconds: 20));
      }
      expect(harness.client.calls.where((call) => !call.startsWith('userIllusts')), [
        'ugoira:120',
        'archive:https://i.pximg.net/img-zip-ugoira/120_ugoira600x600.zip',
      ]);
      expect(find.byType(RawImage), findsOneWidget);
      expect(find.byTooltip('Pause animation'), findsOneWidget);

      await tester.tap(find.byKey(const ValueKey('pixiv-ugoira-pause')));
      await tester.pump();
      expect(find.byTooltip('Play animation'), findsOneWidget);
      expect(find.byType(RawImage), findsOneWidget);
      await disposePixiv(tester);
    });
  });
}
