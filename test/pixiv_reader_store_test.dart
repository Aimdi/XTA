import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/pixiv/pixiv_reader_store.dart';

void main() {
  test('a 48-page work retains its page when switching reading direction', () {
    final store = PixivReaderStore(pageCount: 48, initialPage: 20);
    addTearDown(store.destroy);

    expect(store.state.vertical, isTrue);
    expect(store.state.pageIndex, 20);
    store.toggleDirection();
    expect(store.state.vertical, isFalse);
    expect(store.state.pageIndex, 20);
    store.selectPage(47);
    store.toggleDirection();
    expect(store.state.vertical, isTrue);
    expect(store.state.pageIndex, 47);
  });

  test('page selection cannot escape the artwork bounds', () {
    final store = PixivReaderStore(pageCount: 48, initialPage: 100);
    addTearDown(store.destroy);
    expect(store.state.pageIndex, 47);
    store.selectPage(-1);
    expect(store.state.pageIndex, 0);
    store.selectPage(48);
    expect(store.state.pageIndex, 47);
  });

  test('continuous progress follows the page occupying most viewport space', () {
    final store = PixivReaderStore(pageCount: 48);
    addTearDown(store.destroy);
    store.pageVisibility(4, 10000);
    store.pageVisibility(5, 20000);
    expect(store.state.pageIndex, 5);
    store.pageVisibility(5, 0);
    expect(store.state.pageIndex, 4);
    store.pageVisibility(-1, 50000);
    store.pageVisibility(48, 50000);
    expect(store.state.pageIndex, 4);
  });

  test('late vertical visibility callbacks do not move the horizontal page', () {
    final store = PixivReaderStore(pageCount: 48, initialPage: 8);
    addTearDown(store.destroy);
    store.toggleDirection();
    store.pageVisibility(2, 10000);
    expect(store.state.pageIndex, 8);
    store.selectPage(30);
    store.toggleDirection();
    expect(store.state.pageIndex, 30);
    store.pageVisibility(30, 20);
    expect(store.state.pageIndex, 30);
  });

  test('a jump retires the former page even when its hidden callback arrives during scrolling', () {
    final store = PixivReaderStore(pageCount: 48);
    addTearDown(store.destroy);
    store.pageVisibility(0, 10000);
    final request = store.beginNavigation(30);
    store.pageVisibility(0, 0);
    store.pageVisibility(1, 20000);
    expect(store.state.pageIndex, 30);
    expect(store.finishNavigation(request), isTrue);
    store.pageVisibility(30, 5000);
    expect(store.state.pageIndex, 30);
    store.toggleDirection();
    expect(store.state.pageIndex, 30);
  });

  test('an older scroll completion cannot replace the newest selected page', () {
    final store = PixivReaderStore(pageCount: 48);
    addTearDown(store.destroy);
    final oldRequest = store.beginNavigation(10);
    final newRequest = store.beginNavigation(47);
    expect(store.finishNavigation(oldRequest), isFalse);
    store.pageVisibility(10, 50000);
    expect(store.state.pageIndex, 47);
    expect(store.finishNavigation(newRequest), isTrue);
    store.pageVisibility(47, 10000);
    expect(store.state.pageIndex, 47);
  });

  test('attaching a horizontal controller cannot publish its old page during restoration', () {
    final store = PixivReaderStore(pageCount: 48, initialPage: 20);
    addTearDown(store.destroy);
    store.toggleDirection();
    final request = store.beginNavigation(20);
    store.observedPage(0);
    expect(store.state.pageIndex, 20);
    store.finishNavigation(request);
    store.observedPage(21);
    expect(store.state.pageIndex, 21);
  });

  test('disposing the reader retires pending navigation and visibility callbacks', () async {
    final store = PixivReaderStore(pageCount: 48, initialPage: 20);
    final request = store.beginNavigation(30);
    await store.destroy();
    expect(store.isCurrentNavigation(request), isFalse);
    expect(store.finishNavigation(request), isFalse);
    store.pageVisibility(0, 50000);
    store.observedPage(0);
    expect(store.state.pageIndex, 30);
  });
}
