import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';

PixivIllust _illust(int id) => PixivIllust(
  id: id,
  title: 't$id',
  caption: '',
  type: 'illust',
  thumbnailUrl: 'https://i.pximg.net/$id.jpg',
  pageCount: 1,
  userId: 1,
  userName: 'a',
  userAccount: 'a',
);

void main() {
  group('mergePixivIllusts', () {
    test('appends only new ids', () {
      final merged = mergePixivIllusts([_illust(1), _illust(2)], [_illust(2), _illust(3)]);

      expect(merged.map((e) => e.id), [1, 2, 3]);
    });
  });

  group('PixivIllustListStore', () {
    test('soft refresh keeps prior tiles while replacing contents', () async {
      var calls = 0;
      final store = PixivIllustListStore(({nextUrl}) async {
        calls++;
        return PixivIllustPage(illusts: [_illust(calls)]);
      });

      await store.refresh();
      expect(store.state.single.id, 1);

      await store.refresh();
      expect(store.state.single.id, 2);
      expect(calls, 2);
    });

    test('loadMore dedupes and does not wipe the list on append failure', () async {
      var page = 0;
      final store = PixivIllustListStore(({nextUrl}) async {
        page++;
        if (page == 1) {
          return PixivIllustPage(illusts: [_illust(1)], nextUrl: 'https://example/next');
        }
        if (page == 2) {
          return PixivIllustPage(illusts: [_illust(1), _illust(2)], nextUrl: 'https://example/next2');
        }
        throw PixivException(PixivErrorKind.network, 'boom');
      });

      await store.refresh();
      await store.loadMore();
      expect(store.state.map((e) => e.id), [1, 2]);
      expect(store.loadingMore, isFalse);

      await store.loadMore();
      expect(store.state.map((e) => e.id), [1, 2]);
      expect(store.error, isNull);
    });

    // The bug: useLoader swapped the fetcher but left the old source's grid
    // in state — so a failed refresh on the new mode quietly showed the old
    // mode's illusts under the new label.
    test('switching sources never shows the old source under the new label', () async {
      final store = PixivIllustListStore(({nextUrl}) async {
        return PixivIllustPage(illusts: [_illust(1)]);
      });
      await store.refresh();
      expect(store.state.single.id, 1);

      store.useLoader(({nextUrl}) async {
        throw PixivException(PixivErrorKind.network, 'boom');
      });
      expect(store.state, isEmpty);

      await store.refresh();
      expect(store.state, isEmpty);
      expect(store.error, isNotNull);
    });

    test('soft refresh failure keeps prior tiles', () async {
      var fail = false;
      final store = PixivIllustListStore(({nextUrl}) async {
        if (fail) {
          throw PixivException(PixivErrorKind.network, 'boom');
        }
        return PixivIllustPage(illusts: [_illust(1)]);
      });

      await store.refresh();
      fail = true;
      await store.refresh();
      expect(store.state.single.id, 1);
      expect(store.error, isNull);
    });

    test('skips fully filtered pages until something visible remains', () async {
      var page = 0;
      final store = PixivIllustListStore(({nextUrl}) async {
        page++;
        if (page == 1) {
          return PixivIllustPage(illusts: [_illust(1)], nextUrl: 'https://example/next');
        }
        return PixivIllustPage(illusts: [_illust(2)]);
      }, filter: (illusts) => illusts.where((e) => e.id != 1).toList());

      await store.refresh();
      expect(store.state.map((e) => e.id), [2]);
      expect(page, 2);
    });
  });
}
