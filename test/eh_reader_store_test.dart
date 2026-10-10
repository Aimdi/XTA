import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/ehviewer/eh_models.dart';
import 'package:xta/plugins/ehviewer/eh_reader_store.dart';

typedef _Reader = ({EhReaderStore store, List<int> settled, PrefServiceCache prefs});

_Reader _reader({int total = 10, int initialPage = 4, String? mode}) {
  final prefs = PrefServiceCache(cache: {optionPluginEhReadingMode: ?mode});
  final settled = <int>[];
  final store = EhReaderStore(total: total, initialPage: initialPage, prefs: prefs, onPageSettled: settled.add);
  addTearDown(store.destroy);
  return (store: store, settled: settled, prefs: prefs);
}

void main() {
  test('opens at the initial page, held inside the gallery', () {
    expect(_reader(initialPage: 4).store.state.page, 4);
    expect(_reader(initialPage: 4).store.state.anchor, 4);
    expect(_reader(initialPage: 0).store.state.page, 1);
    expect(_reader(initialPage: 99).store.state.page, 10);
    expect(_reader(total: 0, initialPage: 3).store.state.page, 1);
  });

  test('turning and jumping stay inside the gallery and settle once per page', () {
    final reader = _reader(total: 5, initialPage: 4);

    reader.store.next();
    reader.store.next();
    reader.store.goTo(5);
    reader.store.goTo(1);
    reader.store.previous();
    reader.store.goTo(-3);

    expect(reader.store.state.page, 1);
    expect(reader.settled, [5, 1]);
  });

  test('the slider previews a page without settling, and settles once on release', () {
    final reader = _reader();

    reader.store.scrub(7);
    reader.store.scrub(8);
    expect(reader.store.state.shownPage, 8);
    expect(reader.store.state.page, 4);
    expect(reader.settled, isEmpty);

    reader.store.goTo(8);
    expect(reader.store.state.scrubPage, isNull);
    expect(reader.settled, [8]);
  });

  test('a swipe moves the page but not the vertical anchor; a jump moves both', () {
    final reader = _reader();

    reader.store.observe(6);
    expect((reader.store.state.page, reader.store.state.anchor), (6, 4));

    reader.store.goTo(9);
    expect((reader.store.state.page, reader.store.state.anchor), (9, 9));
    expect(reader.settled, [6, 9]);
  });

  test('the reading mode comes from the pref and is saved back to it', () async {
    expect(_reader(mode: 'rightToLeft').store.state.mode, EhReadingMode.rightToLeft);
    expect(_reader(mode: 'sideways').store.state.mode, EhReadingMode.leftToRight);
    expect(_reader().store.state.mode, EhReadingMode.leftToRight);

    final reader = _reader(mode: 'leftToRight');
    reader.store.observe(7);
    await reader.store.setMode(EhReadingMode.vertical);

    expect(reader.prefs.get<String>(optionPluginEhReadingMode), 'vertical');
    expect(reader.store.state.anchor, 7, reason: 'the list is laid out from the page being read');
  });

  test('the middle third shows and hides the bars', () {
    final reader = _reader();

    expect(ehTapAction(195, 390, EhReadingMode.leftToRight), EhTapAction.toggleChrome);
    reader.store.toggleChrome();
    expect(reader.store.state.chromeVisible, isFalse);
    reader.store.toggleChrome();
    expect(reader.store.state.chromeVisible, isTrue);
  });

  test('the outer thirds turn pages, mirrored when reading right to left', () {
    expect(ehTapAction(20, 390, EhReadingMode.leftToRight), EhTapAction.previous);
    expect(ehTapAction(370, 390, EhReadingMode.leftToRight), EhTapAction.next);
    expect(ehTapAction(20, 390, EhReadingMode.rightToLeft), EhTapAction.next);
    expect(ehTapAction(370, 390, EhReadingMode.rightToLeft), EhTapAction.previous);
    expect(ehTapAction(20, 390, EhReadingMode.vertical), EhTapAction.previous);
    expect(EhReadingMode.rightToLeft.reversed, isTrue);
    expect(EhReadingMode.vertical.paged, isFalse);
  });

  test('preloads the next three pages and the one before, inside the gallery', () {
    expect(ehPreloadPages(5, 30), [6, 7, 8, 4]);
    expect(ehPreloadPages(1, 30), [2, 3, 4]);
    expect(ehPreloadPages(29, 30), [30, 28]);
  });

  test('a vertical reader is on the topmost page in view, or on a short last page once whole', () async {
    final reader = _reader(total: 5, initialPage: 3, mode: 'vertical');
    void seen(int page, double visible, double height) =>
        reader.store.pageVisibility(page, anchor: 3, visible: visible, height: height);

    seen(3, 120, 600);
    seen(4, 600, 600);
    expect(reader.store.state.page, 3);

    seen(3, 0, 600);
    expect(reader.store.state.page, 4);

    seen(5, 200, 200);
    expect(reader.store.state.page, 5);
    expect(reader.store.state.anchor, 3, reason: 'scrolling never re-lays the list out');
  });

  test('visibility from a list a jump replaced, or from a paged mode, is ignored', () {
    final vertical = _reader(total: 10, initialPage: 3, mode: 'vertical');
    vertical.store.goTo(8);
    vertical.store.pageVisibility(3, anchor: 3, visible: 600, height: 600);
    expect(vertical.store.state.page, 8);

    final paged = _reader(mode: 'leftToRight');
    paged.store.pageVisibility(9, anchor: 4, visible: 600, height: 600);
    expect(paged.store.state.page, 4);
  });

  test('the page count falls back to the furthest page known', () {
    EhGallery gallery(int? pages) => EhGallery(gid: 1, token: 't', title: 'T', pageCount: pages);
    final previews = [const EhPreview(pageToken: 'a', page: 12)];

    expect(ehReaderTotal(gallery(30), previews, 3), 30);
    expect(ehReaderTotal(gallery(null), previews, 3), 12);
    expect(ehReaderTotal(gallery(0), const [], 7), 7);
  });
}
