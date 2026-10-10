import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:xta/downloads/download_destination.dart';
import 'package:xta/downloads/download_entry.dart';
import 'package:xta/downloads/download_transfer.dart';
import 'package:xta/plugins/pixiv/pixiv_download.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_page_actions.dart';
import 'package:xta/plugins/pixiv/pixiv_reader_screen.dart';

import 'support/pixiv_reader_harness.dart';

DownloadRequest _request(int page) =>
    DownloadRequest(uri: Uri.parse('https://i.pximg.net/img-original/$page.png'), fileName: 'p$page.png');

class _HeaderClient extends http.BaseClient {
  final seen = <Map<String, String>>[];

  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) async {
    seen.add(request.headers);
    return http.StreamedResponse(Stream.value([1, 2, 3]), 200, contentLength: 3);
  }
}

/// Opens a work's "Download all pages" from the reader, where screens start it.
Future<void> _downloadAllFromReader(WidgetTester tester) async {
  await tester.tap(find.byKey(const ValueKey('pixiv-reader-more')));
  await settlePixiv(tester);
  await tester.tap(find.byKey(const ValueKey('pixiv-page-action-downloadAll')));
  await tester.pump();
}

void main() {
  group('PixivDownloadStore', () {
    test('requests every page in order and a failed page does not stop the rest', () async {
      final asked = <String>[];
      final store = PixivDownloadStore(
        save: (request) async {
          asked.add(request.fileName);
          return request.fileName != 'p1.png';
        },
        cancelActive: (_) {},
        total: 4,
      );
      addTearDown(store.destroy);

      final result = await store.run([for (var page = 0; page < 4; page++) _request(page)]);

      expect(asked, ['p0.png', 'p1.png', 'p2.png', 'p3.png']);
      expect(result.done, 4);
      expect(result.saved, 3);
      expect(result.failed, 1);
      expect(result.cancelled, isFalse);
    });

    test('publishes progress after each page', () async {
      final gates = [for (var i = 0; i < 3; i++) Completer<bool>()];
      var next = 0;
      final store = PixivDownloadStore(save: (_) => gates[next++].future, cancelActive: (_) {}, total: 3);
      addTearDown(store.destroy);
      final run = store.run([_request(0), _request(1), _request(2)]);

      expect(store.state.current, 1);
      gates[0].complete(true);
      await pumpEventQueue();
      expect((store.state.done, store.state.current), (1, 2));
      gates[1].complete(false);
      await pumpEventQueue();
      expect((store.state.done, store.state.saved, store.state.current), (2, 1, 3));
      gates[2].complete(true);
      expect((await run).saved, 2);
    });

    test('cancelling stops before the next page and cancels the page in flight', () async {
      final gate = Completer<bool>();
      final cancelled = <Uri>[];
      final asked = <String>[];
      final store = PixivDownloadStore(
        save: (request) {
          asked.add(request.fileName);
          return gate.future;
        },
        cancelActive: cancelled.add,
        total: 3,
      );
      addTearDown(store.destroy);
      final run = store.run([_request(0), _request(1), _request(2)]);

      store.cancel();
      expect(cancelled, [_request(0).uri]);
      gate.complete(false);
      final result = await run;

      expect(asked, ['p0.png']);
      expect(result.cancelled, isTrue);
      expect(result.done, 1);
    });
  });

  group('PixivDownloadStore with a wider queue', () {
    test('keeps as many pages in flight as the queue allows and remembers which saved', () async {
      final gates = <String, Completer<bool>>{};
      final store = PixivDownloadStore(
        save: (request) => (gates[request.fileName] = Completer<bool>()).future,
        cancelActive: (_) {},
        total: 3,
        parallel: 2,
      );
      addTearDown(store.destroy);
      final run = store.run([_request(0), _request(1), _request(2)]);

      expect(gates.keys, ['p0.png', 'p1.png']);
      gates['p1.png']!.complete(true);
      await pumpEventQueue();
      expect(gates.keys, ['p0.png', 'p1.png', 'p2.png']);
      gates['p0.png']!.complete(false);
      gates['p2.png']!.complete(true);

      final result = await run;
      expect((result.done, result.saved), (3, 2));
      expect(result.savedIndexes..sort(), [1, 2]);
    });

    test('cancelling stops every page in flight', () async {
      final cancelled = <Uri>[];
      final gate = Completer<bool>();
      final store = PixivDownloadStore(save: (_) => gate.future, cancelActive: cancelled.add, total: 4, parallel: 2);
      addTearDown(store.destroy);
      final run = store.run([for (var page = 0; page < 4; page++) _request(page)]);

      store.cancel();
      expect(cancelled, [_request(0).uri, _request(1).uri]);
      gate.complete(false);
      expect((await run).done, 2);
    });
  });

  testWidgets('download all shows page progress with a cancel button and reports the final count', (tester) async {
    final harness = await pumpPixiv(tester, PixivReaderScreen(illust: pixivWork(pages: 3)));
    final gates = <Completer<bool>>[];
    harness.downloader.onSave = (_) {
      gates.add(Completer<bool>());
      return gates.last.future;
    };

    await _downloadAllFromReader(tester);
    await tester.pump(const Duration(milliseconds: 300));
    expect(find.text('Downloading page 1 of 3…'), findsOneWidget);
    expect(find.descendant(of: find.byType(SnackBar), matching: find.text('Cancel')), findsOneWidget);

    gates[0].complete(true);
    await tester.pump();
    expect(find.text('Downloading page 2 of 3…'), findsOneWidget);
    gates[1].complete(false);
    await tester.pump();
    expect(find.text('Downloading page 3 of 3…'), findsOneWidget);
    gates[2].complete(true);
    await settlePixiv(tester);

    expect(harness.downloader.requests.map((request) => request.uri.path.split('/').last), [
      '120_p0.png',
      '120_p1.png',
      '120_p2.png',
    ]);
    expect(harness.downloader.requests.map((request) => request.treeUri).toSet(), {'content://tree/pictures'});
    expect(find.text('Files saved: 2 / 3'), findsOneWidget);
    await disposePixiv(tester);
  });

  testWidgets('cancelling a download-all from the snackbar stops the remaining pages', (tester) async {
    final harness = await pumpPixiv(tester, PixivReaderScreen(illust: pixivWork(pages: 5)));
    final gate = Completer<bool>();
    harness.downloader.onSave = (_) => gate.future;

    await _downloadAllFromReader(tester);
    await tester.pump(const Duration(milliseconds: 300));
    await tester.tap(find.descendant(of: find.byType(SnackBar), matching: find.text('Cancel')));
    await tester.pump();
    expect(find.text('Cancelled'), findsOneWidget);
    expect(harness.downloader.cancelled, hasLength(1));
    gate.complete(false);
    await settlePixiv(tester);

    expect(harness.downloader.requests, hasLength(1));
    expect(find.text('Files saved: 0 / 5'), findsOneWidget);
    await disposePixiv(tester);
  });

  testWidgets('declining the folder for a whole work downloads nothing', (tester) async {
    final harness = await pumpPixiv(tester, PixivReaderScreen(illust: pixivWork(pages: 3)));
    harness.downloader.folder = null;

    await _downloadAllFromReader(tester);
    await settlePixiv(tester);

    expect(harness.downloader.requests, isEmpty);
    expect(find.byType(SnackBar), findsNothing);
    await disposePixiv(tester);
  });

  testWidgets('the reader download button and a long-press save the page on screen, asking the second time', (
    tester,
  ) async {
    final harness = await pumpPixiv(tester, PixivReaderScreen(illust: pixivWork(), initialPage: 2, vertical: false));

    await tester.tap(find.byKey(const ValueKey('pixiv-reader-download')));
    await settlePixiv(tester);
    expect(harness.downloads.isSaved(120, 2), isTrue);
    await tester.longPressAt(tester.getTopLeft(find.byType(PageView)) + const Offset(40, 40));
    await settlePixiv(tester);
    expect(find.text('Page 3 of 8'), findsOneWidget);
    await tester.tap(find.byKey(ValueKey('pixiv-page-action-${PixivPageAction.downloadPage.name}')));
    await settlePixiv(tester);
    expect(find.text('This page is already saved. Save it again?'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('pixiv-resave-all')));
    await settlePixiv(tester);

    expect(harness.downloader.pages, [2, 2]);
    await disposePixiv(tester);
  });

  test('page requests save the originals under the default {illust_id}_p{part} names', () {
    final requests = pixivPageRequests(pixivWork(pages: 2), const DownloadDestination.folder('content://tree/x'));
    expect(requests.map((request) => request.fileName), ['120_p0.png', '120_p1.png']);
    expect(requests.map((request) => request.subfolder).toSet(), {null});
    expect(requests.first.uri.toString(), 'https://i.pximg.net/img-original/img/2026/07/01/00/00/00/120_p0.png');
    expect(requests.map((request) => request.treeUri).toSet(), {'content://tree/x'});
    expect(pixivPageMedia(pixivWork(), 3).url, contains('120_p3_master1200'));
  });

  test('only the Pixiv image host is sent the Pixiv referer', () {
    expect(downloadRequestHeaders(Uri.parse('https://i.pximg.net/a.png'))['Referer'], 'https://www.pixiv.net/');
    expect(downloadRequestHeaders(Uri.parse('https://pximg.net/a.png'))['Referer'], 'https://www.pixiv.net/');
    for (final url in [
      'https://pbs.twimg.com/a.jpg',
      'https://i.pximg.net.evil.example/a.png',
      'https://pximg.com/a',
    ]) {
      expect(downloadRequestHeaders(Uri.parse(url)), isEmpty, reason: url);
    }
  });

  test('the shared transfer sends that referer when it fetches the file', () async {
    final directory = await Directory.systemTemp.createTemp('xta-pixiv-transfer');
    addTearDown(() => directory.delete(recursive: true));
    final client = _HeaderClient();
    final transfer = DownloadTransfer(
      clientFactory: () => client,
      temporaryDirectory: () async => directory,
      save: (_, _, _) async => 'saved',
    );
    for (final url in ['https://i.pximg.net/img-original/1_p0.png', 'https://example.org/1.png']) {
      final entry = DownloadEntry(
        id: 'x${url.length}',
        uri: Uri.parse(url),
        fileName: '1.png',
        createdAt: DateTime(2026),
      );
      expect(await transfer.call(entry, DownloadCancellation(), (_, _) {}, (_) {}), 'saved');
    }
    expect(client.seen.first['Referer'], 'https://www.pixiv.net/');
    expect(client.seen.first['accept-encoding'], 'identity');
    expect(client.seen.last.containsKey('Referer'), isFalse);
  });

  group('per-page files from the API', () {
    final json = {
      'id': 7,
      'title': 'Manga',
      'type': 'manga',
      'image_urls': {'medium': 'https://i.pximg.net/c/540x540_70/7_p0.jpg'},
      'user': {'id': 1, 'name': 'A', 'account': 'a'},
      'page_count': 2,
      'meta_pages': [
        {
          'image_urls': {
            'square_medium': 'https://i.pximg.net/c/360x360_70/7_p0_square.jpg',
            'medium': 'https://i.pximg.net/c/540x540_70/7_p0.jpg',
            'large': 'https://i.pximg.net/c/600x1200_90/7_p0.jpg',
            'original': 'https://i.pximg.net/img-original/7_p0.png',
          },
        },
        {
          'image_urls': {'large': 'https://i.pximg.net/c/600x1200_90/7_p1.jpg'},
        },
      ],
    };

    test('a manga page saves its original and previews its medium image', () {
      final illust = pixivIllustFromJson(json)!;
      expect(illust.downloadUrlAt(0), 'https://i.pximg.net/img-original/7_p0.png');
      expect(illust.thumbUrlAt(0), 'https://i.pximg.net/c/540x540_70/7_p0.jpg');
      expect(illust.downloadUrlAt(1), 'https://i.pximg.net/c/600x1200_90/7_p1.jpg');
      expect(illust.thumbUrlAt(1), 'https://i.pximg.net/c/600x1200_90/7_p1.jpg');
    });

    test('a single image saves the original from meta_single_page', () {
      final illust = pixivIllustFromJson({
        ...json,
        'meta_pages': <Object>[],
        'meta_single_page': {'original_image_url': 'https://i.pximg.net/img-original/7_p0.jpg'},
      })!;
      expect(illust.viewerUrls, hasLength(1));
      expect(illust.downloadUrlAt(0), 'https://i.pximg.net/img-original/7_p0.jpg');
    });

    test('an illust built without per-page lists falls back to the viewer pages', () {
      final illust = pixivWork(pages: 2).copyWith();
      const bare = PixivIllust(
        id: 1,
        title: '',
        caption: '',
        type: 'illust',
        thumbnailUrl: 'https://i.pximg.net/t.jpg',
        pageCount: 1,
        userId: 1,
        userName: '',
        userAccount: '',
      );
      expect(bare.downloadUrlAt(0), 'https://i.pximg.net/t.jpg');
      expect(bare.thumbUrlAt(5), 'https://i.pximg.net/t.jpg');
      expect(illust.originalUrls, hasLength(2));
    });
  });
}
