import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_store.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';

void main() {
  test('isBookmarked uses the illust flag until a write is recorded', () {
    final store = PixivBookmarkStore();
    final plain = _illust(id: 1);
    final already = _illust(id: 2, isBookmarked: true, totalBookmarks: 4);

    expect(store.isBookmarked(plain), isFalse);
    expect(store.isBookmarked(already), isTrue);
    expect(store.bookmarkCount(plain), 0);
    expect(store.bookmarkCount(already), 4);
  });

  test('a recorded bookmark or removal adjusts the count', () {
    final store = PixivBookmarkStore();
    final illust = _illust(id: 7, totalBookmarks: 10);
    final bookmarked = _illust(id: 3, isBookmarked: true, totalBookmarks: 5);

    store.mark(7, true);
    store.mark(3, false);
    expect((store.isBookmarked(illust), store.bookmarkCount(illust)), (true, 11));
    expect((store.isBookmarked(bookmarked), store.bookmarkCount(bookmarked)), (false, 4));

    store.mark(7, false);
    expect((store.isBookmarked(illust), store.bookmarkCount(illust)), (false, 10));
  });

  test('a removal never drives the count below zero', () {
    final store = PixivBookmarkStore()..mark(4, false);
    expect(store.bookmarkCount(_illust(id: 4, isBookmarked: true)), 0);
  });

  test('one write per work at a time; a second one is skipped', () async {
    final store = PixivBookmarkStore();
    final gate = Completer<String>();
    final first = store.exclusive(7, () => gate.future);

    expect(store.isBusy(7), isTrue);
    expect(await store.exclusive(7, () async => 'second'), isNull);
    expect(await store.exclusive(8, () async => 'other work'), 'other work');

    gate.complete('first');
    expect(await first, 'first');
    expect(store.isBusy(7), isFalse);
  });

  test('a failed write frees the work and rethrows', () async {
    final store = PixivBookmarkStore();
    await expectLater(store.exclusive<void>(7, () async => throw StateError('offline')), throwsStateError);
    expect(store.isBusy(7), isFalse);
  });
}

PixivIllust _illust({required int id, bool isBookmarked = false, int totalBookmarks = 0}) => PixivIllust(
  id: id,
  title: '',
  caption: '',
  type: 'illust',
  thumbnailUrl: 'https://i.pximg.net/$id.jpg',
  pageCount: 1,
  userId: 1,
  userName: '',
  userAccount: '',
  isBookmarked: isBookmarked,
  totalBookmarks: totalBookmarks,
);
