import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';

PixivUser _user(int id) => PixivUser(id: id, name: 'u$id', account: 'u$id', comment: '');

/// Pages of creators served in order; a null entry fails that request.
PixivPageLoader<PixivUser> _pages(List<PixivPage<PixivUser>?> pages, List<String?> asked) => ({nextUrl}) async {
  asked.add(nextUrl);
  final page = pages[asked.length - 1];
  if (page == null) throw PixivException(PixivErrorKind.network, 'offline');
  return page;
};

PixivPagedListStore<PixivUser> _store(PixivPageLoader<PixivUser> loader, {PixivListFilter<PixivUser>? filter}) =>
    PixivPagedListStore<PixivUser>(loader, keyOf: (user) => user.id, filter: filter);

void main() {
  group('PixivPagedListStore', () {
    test('refresh loads the first page and remembers where the next one is', () async {
      final asked = <String?>[];
      final store = _store(
        _pages([
          PixivPage([_user(1), _user(2)], nextUrl: 'next-1'),
        ], asked),
      );

      await store.refresh();
      expect(store.state.map((u) => u.id), [1, 2]);
      expect(store.hasMore, isTrue);
      expect(asked, [null]);
    });

    test('loadMore follows next_url and merges without repeats', () async {
      final asked = <String?>[];
      final store = _store(
        _pages([
          PixivPage([_user(1), _user(2)], nextUrl: 'next-1'),
          PixivPage([_user(2), _user(3)]),
        ], asked),
      );

      await store.refresh();
      await store.loadMore();
      expect(store.state.map((u) => u.id), [1, 2, 3]);
      expect(asked, [null, 'next-1']);
      expect(store.hasMore, isFalse);

      await store.loadMore();
      expect(asked, hasLength(2));
    });

    test('useLoader clears the old list so a failure cannot show it under the new source', () async {
      final store = _store(
        _pages([
          PixivPage([_user(1)], nextUrl: 'next-1'),
        ], []),
      );
      await store.refresh();

      store.useLoader(_pages([null], []));
      expect(store.state, isEmpty);
      expect(store.hasMore, isFalse);

      await store.refresh();
      expect(store.state, isEmpty);
      expect(store.error, isA<PixivException>());
    });

    test('a failed page or refresh keeps the list it had', () async {
      final store = _store(
        _pages([
          PixivPage([_user(1)], nextUrl: 'next-1'),
          null,
          null,
        ], []),
      );

      await store.refresh();
      await store.loadMore();
      expect(store.state.map((u) => u.id), [1]);
      expect(store.loadingMore, isFalse);

      await store.refresh();
      expect(store.state.map((u) => u.id), [1]);
    });

    test('pages the filter empties are skipped until something shows', () async {
      final asked = <String?>[];
      final store = _store(
        _pages([
          PixivPage([_user(1)], nextUrl: 'next-1'),
          PixivPage([_user(2)], nextUrl: 'next-2'),
          PixivPage([_user(3)]),
        ], asked),
        filter: (users) => [
          for (final user in users)
            if (user.id == 3) user,
        ],
      );

      await store.refresh();
      expect(store.state.map((u) => u.id), [3]);
      expect(asked, [null, 'next-1', 'next-2']);
    });

    group('a page that lands late', () {
      /// A first page with more to come, then a second page held back until [slow] completes.
      PixivPageLoader<PixivUser> oldSource(Completer<PixivPage<PixivUser>> slow) =>
          ({nextUrl}) async => nextUrl == null ? PixivPage([_user(1)], nextUrl: 'old-next') : slow.future;

      test('from the old source is dropped after useLoader', () async {
        final slow = Completer<PixivPage<PixivUser>>();
        final store = _store(oldSource(slow));
        await store.refresh();
        final more = store.loadMore();

        store.useLoader(({nextUrl}) async => PixivPage([_user(100)]));
        await store.refresh();
        slow.complete(PixivPage([_user(2)], nextUrl: 'old-next-2'));
        await more;

        expect(store.state.map((u) => u.id), [100]);
        expect(store.hasMore, isFalse);
        expect(store.loadingMore, isFalse);
      });

      test('from before a refresh is dropped, and paging goes on from the fresh page', () async {
        final slow = Completer<PixivPage<PixivUser>>();
        var refreshed = false;
        final store = _store(({nextUrl}) async {
          if (nextUrl == 'old-next') return slow.future;
          if (nextUrl != null) return PixivPage([_user(30)]);
          return refreshed ? PixivPage([_user(10)], nextUrl: 'fresh-next') : PixivPage([_user(1)], nextUrl: 'old-next');
        });
        await store.refresh();
        final more = store.loadMore();

        refreshed = true;
        await store.refresh();
        slow.complete(PixivPage([_user(2)], nextUrl: 'old-next-2'));
        await more;
        expect(store.state.map((u) => u.id), [10]);

        await store.loadMore();
        expect(store.state.map((u) => u.id), [10, 30]);
      });

      test('from a soft refresh of the old source never shows after useLoader', () async {
        final slow = Completer<PixivPage<PixivUser>>();
        var calls = 0;
        final store = _store(({nextUrl}) async => calls++ == 0 ? PixivPage([_user(1)]) : slow.future);
        await store.refresh();
        final oldRefresh = store.refresh();

        final asked = <String?>[];
        store.useLoader(({nextUrl}) async {
          asked.add(nextUrl);
          return PixivPage([_user(nextUrl == null ? 100 : 101)], nextUrl: nextUrl == null ? 'new-next' : null);
        });
        await store.refresh();
        slow.complete(PixivPage([_user(2)], nextUrl: 'old-next'));
        await oldRefresh;
        expect(store.state.map((u) => u.id), [100]);

        await store.loadMore();
        expect(asked, [null, 'new-next']);
        expect(store.state.map((u) => u.id), [100, 101]);
      });
    });
  });

  test('the illust list store is the paged store keyed by work id', () async {
    final store = PixivIllustListStore(({nextUrl}) async => const PixivIllustPage(illusts: []));
    expect(store, isA<PixivPagedListStore<PixivIllust>>());
    expect(store.keyOf(_work(5)), 5);
  });
}

PixivIllust _work(int id) => PixivIllust(
  id: id,
  title: '',
  caption: '',
  type: 'illust',
  thumbnailUrl: 'https://i.pximg.net/$id.jpg',
  pageCount: 1,
  userId: 1,
  userName: '',
  userAccount: '',
);
