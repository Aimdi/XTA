import 'dart:ui';

import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/ehviewer/eh_client.dart';
import 'package:xta/plugins/ehviewer/eh_gallery_store.dart';
import 'package:xta/plugins/ehviewer/eh_grid.dart';
import 'package:xta/plugins/ehviewer/eh_models.dart';
import 'package:xta/plugins/ehviewer/eh_parse.dart';
import 'package:xta/plugins/ehviewer/eh_store.dart';

import 'support/eh_gallery_fixture.dart';

EhHistoryStore _history({int? lastPage}) {
  final history = EhHistoryStore();
  if (lastPage != null) {
    history.update([EhHistoryEntry(gallery: ehFixtureGallery, lastPage: lastPage, viewedAt: DateTime(2026))]);
  }
  return history;
}

EhGalleryStore _store(EhFixtureServer server, {int? lastPage}) => EhGalleryStore(
  client: server.client(),
  history: _history(lastPage: lastPage),
  gallery: ehFixtureGallery,
);

List<int> _pages(EhGalleryStore store) => store.state.previews.map((p) => p.page).toList();

void main() {
  group('EhGalleryStore', () {
    test('the first load reads the detail and its first preview sheet', () async {
      final server = EhFixtureServer();
      final store = _store(server);
      expect(store.state.detail, isNull);
      expect(store.shown, same(ehFixtureGallery));

      await store.load();

      final detail = store.state.detail!;
      expect(detail.title, 'Sommer Book');
      expect(detail.uploader, 'Alice');
      expect(detail.weakTags, {'female:twin tails'});
      expect(detail.previewSheetCount, 3);
      expect(_pages(store), [1, 2, 3, 4]);
      expect(store.state.nextSheet, 1);
      expect(store.state.hasMorePreviews, isTrue);
      expect(store.shown, same(detail));
      expect(server.sheetsRequested, [0]);
    });

    test('preview sheets append in order until the last one', () async {
      final server = EhFixtureServer();
      final store = _store(server);
      await store.load();

      await store.loadMorePreviews();
      expect(_pages(store), [1, 2, 3, 4, 5, 6, 7, 8]);
      expect(store.state.nextSheet, 2);

      await store.nearEnd();
      expect(_pages(store), [for (var page = 1; page <= 12; page++) page]);
      expect(store.state.hasMorePreviews, isFalse);

      await store.nearEnd();
      await store.loadMorePreviews();
      expect(server.sheetsRequested, [0, 1, 2]);
    });

    test('a failed sheet stops the scroll trigger but not the button', () async {
      final server = EhFixtureServer();
      final store = _store(server);
      await store.load();

      server.failures = 1;
      await store.nearEnd();
      expect(store.state.moreFailed, isTrue);
      expect(store.state.loadingMore, isFalse);
      expect(_pages(store), [1, 2, 3, 4]);

      await store.nearEnd();
      expect(server.sheetsRequested, [0, 1]);

      await store.loadMorePreviews();
      expect(store.state.moreFailed, isFalse);
      expect(_pages(store), [1, 2, 3, 4, 5, 6, 7, 8]);
      expect(server.sheetsRequested, [0, 1, 1]);
    });

    test('continue page comes from history, past the first page only', () async {
      final server = EhFixtureServer();
      expect(_store(server).continuePage, isNull);
      expect(_store(server, lastPage: 1).continuePage, isNull);
      expect(_store(server, lastPage: 7).continuePage, 7);

      final beyond = _store(server, lastPage: 30);
      await beyond.load();
      expect(beyond.continuePage, 24);
    });

    test('a failed load keeps the error until a retry succeeds', () async {
      final server = EhFixtureServer()..failures = 1;
      final store = _store(server);

      await store.load();
      expect(store.state.detail, isNull);
      expect(store.triple.error, isA<EhException>());
      expect((store.triple.error as EhException).kind, EhErrorKind.badResponse);

      await store.load();
      expect(store.triple.error, isNull);
      expect(store.state.detail?.title, 'Sommer Book');
      expect(_pages(store), [1, 2, 3, 4]);
    });

    test('a sheet that lands after a reload is dropped', () async {
      final server = EhFixtureServer();
      final store = _store(server);
      await store.load();

      final more = store.loadMorePreviews();
      final reload = store.load();
      await Future.wait([more, reload]);

      expect(_pages(store), [1, 2, 3, 4]);
      expect(store.state.nextSheet, 1);
    });
  });

  group('preview tiles', () {
    test('merging keeps each page once, in order', () {
      const a = EhPreview(pageToken: 'a', page: 1);
      const b = EhPreview(pageToken: 'b', page: 2);
      const c = EhPreview(pageToken: 'c', page: 3);
      expect(mergeEhPreviews([a, c], [b, c]).map((p) => p.pageToken), ['a', 'b', 'c']);
    });

    test('tiles read their sheet offset and size from the style', () {
      final tiles = parseEhPreviewSheet(ehGalleryHtml(sheet: 1, thumbs: true));
      expect(tiles.map((t) => t.page), [5, 6, 7, 8]);
      expect(tiles[1].thumbUrl, 'https://ehgt.org/sheet1.webp');
      expect(tiles[1].thumbOffsetX, -200);
      expect(tiles[1].thumbWidth, 200);
      expect(tiles[1].thumbHeight, 283);
    });

    test('a lone thumbnail without an offset still has its image', () {
      final tile = parseEhPreviewSheet(
        '<a href="https://e-hentai.org/s/aa/9-1"><div style="width:200px;height:290px;'
        'background:transparent url(https://ehgt.org/one.webp) no-repeat"></div></a>'
        '<a href="https://e-hentai.org/s/bb/9-2"><img alt="2" src="https://ehgt.org/two.jpg" /></a>',
      );
      expect(tile.map((t) => t.thumbUrl), ['https://ehgt.org/one.webp', 'https://ehgt.org/two.jpg']);
      expect(tile.first.thumbOffsetX, isNull);
    });

    test('the sprite crop is the tile in sheet pixels, never past the sheet', () {
      const sheet = Size(4000, 290);
      expect(
        ehSpriteSource(sheet, offsetX: -400, tileWidth: 200, tileHeight: 283),
        const Rect.fromLTWH(400, 0, 200, 283),
      );
      expect(ehSpriteSource(sheet, offsetX: 0), const Rect.fromLTWH(0, 0, 200, 290));
      expect(ehSpriteSource(sheet, offsetX: -3900), const Rect.fromLTWH(3900, 0, 100, 290));
      expect(ehSpriteSource(const Size(140, 200), offsetX: 0), const Rect.fromLTWH(0, 0, 140, 200));
    });
  });
}
