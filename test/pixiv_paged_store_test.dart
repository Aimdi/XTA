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
