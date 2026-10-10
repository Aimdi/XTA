import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_grid.dart';
import 'package:xta/plugins/pixiv/pixiv_illust_pager.dart';
import 'package:xta/plugins/pixiv/pixiv_illust_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';

import 'support/pixiv_reader_harness.dart';

/// A list of single-page works with a second page behind `next`, which fails while [failing].
class _Feed {
  final first = [pixivWork(id: 1, pages: 1, title: 'One'), pixivWork(id: 2, pages: 1, title: 'Two')];
  final second = [pixivWork(id: 3, pages: 1, title: 'Three')];
  final asked = <String?>[];
  var failing = false;
  late final store = PixivIllustListStore(({nextUrl}) async {
    asked.add(nextUrl);
    if (nextUrl == null) return PixivIllustPage(illusts: first, nextUrl: 'next');
    if (failing) throw PixivException(PixivErrorKind.network, 'offline');
    return PixivIllustPage(illusts: second);
  });
}

/// Answers each work's detail with the work itself, so a page keeps its title and page count.
class _EchoClient extends FakePixivClient {
  final List<PixivIllust> works;

  _EchoClient(super.prefs, this.works);

  @override
  Future<PixivIllust> illustDetail(int illustId) async =>
      works.firstWhere((work) => work.id == illustId, orElse: () => pixivWork(id: illustId));
}

ScrollMetrics _metrics({double pixels = 0, Axis axis = Axis.horizontal}) => FixedScrollMetrics(
  minScrollExtent: 0,
  maxScrollExtent: 400,
  pixels: pixels,
  viewportDimension: 400,
  axisDirection: axis == Axis.horizontal ? AxisDirection.right : AxisDirection.down,
  devicePixelRatio: 1,
);

final _drag = DragUpdateDetails(globalPosition: Offset.zero);

class _NoContext extends Fake implements BuildContext {}

final _context = _NoContext();

Future<_Feed> _openFromGrid(WidgetTester tester, {bool swipe = true, bool failing = false}) async {
  final feed = _Feed()..failing = failing;
  await tester.runAsync(feed.store.refresh);
  addTearDown(feed.store.destroy);
  await pumpPixiv(
    tester,
    Scaffold(
      body: PixivIllustGrid(illusts: feed.store.state, source: feed.store),
    ),
    client: (prefs) {
      prefs.set(optionPluginPixivSwipeBetweenWorks, swipe);
      return _EchoClient(prefs, [...feed.first, ...feed.second]);
    },
  );
  await tester.tap(find.text('One'));
  await settlePixiv(tester);
  return feed;
}

Future<void> _swipeToNextWork(WidgetTester tester) async {
  await tester.dragFrom(const Offset(300, 760), const Offset(-320, 0));
  await settlePixiv(tester);
}

void main() {
  group('page edges', () {
    test('a drag pushing past the last or first page is reported with its direction', () {
      final past = pixivPageEdge(
        OverscrollNotification(metrics: _metrics(pixels: 400), context: _context, overscroll: 12, dragDetails: _drag),
      );
      expect(past?.delta, 12);
      final before = pixivPageEdge(
        ScrollUpdateNotification(metrics: _metrics(pixels: -8), context: _context, scrollDelta: -8, dragDetails: _drag),
      );
      expect(before?.delta, -8);
      expect(pixivPageEdge(ScrollEndNotification(metrics: _metrics(), context: _context))?.ended, isTrue);
    });

    test('ordinary paging, flings and vertical scrolling are not edge pushes', () {
      expect(
        pixivPageEdge(
          ScrollUpdateNotification(
            metrics: _metrics(pixels: 200),
            context: _context,
            scrollDelta: 20,
            dragDetails: _drag,
          ),
        ),
        isNull,
      );
      expect(
        pixivPageEdge(OverscrollNotification(metrics: _metrics(pixels: 400), context: _context, overscroll: 12)),
        isNull,
      );
      expect(
        pixivPageEdge(
          OverscrollNotification(
            metrics: _metrics(axis: Axis.vertical),
            context: _context,
            overscroll: 12,
            dragDetails: _drag,
          ),
        ),
        isNull,
      );
    });

    test('a push turns once it is far enough, once per drag, either way', () {
      final store = PixivIllustPagerStore([pixivWork()]);
      expect(store.edgePush(const PixivPageEdgeNotification(30)), 0);
      expect(store.edgePush(const PixivPageEdgeNotification(30)), 1);
      expect(store.edgePush(const PixivPageEdgeNotification(90)), 0);
      expect(store.edgePush(const PixivPageEdgeNotification.ended()), 0);
      expect(store.edgePush(const PixivPageEdgeNotification(-40)), 0);
      expect(store.edgePush(const PixivPageEdgeNotification(30)), 0);
      expect(store.edgePush(const PixivPageEdgeNotification(-pixivEdgeTurnDistance)), -1);
      store.destroy();
    });
  });

  group('pager store', () {
    test('the next page joins at the end, then the list says it has no more', () async {
      final feed = _Feed();
      await feed.store.refresh();
      final pager = PixivIllustPagerStore(feed.store.state, source: feed.store);
      expect((pager.state.tail, pager.pageCount), (PixivPagerTail.more, 3));
      await pager.loadMore();
      expect(pager.state.illusts.map((work) => work.id), [1, 2, 3]);
      expect((pager.state.tail, pager.pageCount), (PixivPagerTail.end, 4));
      pager.destroy();
      feed.store.destroy();
    });

    test('a failed page offers retry, and the retry asks again', () async {
      final feed = _Feed()..failing = true;
      await feed.store.refresh();
      final pager = PixivIllustPagerStore(feed.store.state, source: feed.store);
      await pager.loadMore();
      expect(pager.state.tail, PixivPagerTail.failed);
      expect(feed.store.loadMoreFailed, isTrue);
      feed.failing = false;
      await pager.loadMore();
      expect(pager.state.tail, PixivPagerTail.end);
      expect(feed.asked, [null, 'next', 'next']);
      pager.destroy();
      feed.store.destroy();
    });

    test('works muted after opening stay out of the later pages', () async {
      final feed = _Feed();
      await feed.store.refresh();
      final pager = PixivIllustPagerStore(
        feed.store.state,
        source: feed.store,
        visible: (works) => works.where((work) => work.id != 3).toList(),
      );
      await pager.loadMore();
      expect(pager.state.illusts.map((work) => work.id), [1, 2]);
      pager.destroy();
      feed.store.destroy();
    });

    test('a second next-page call waits for the one in flight instead of returning early', () async {
      final feed = _Feed();
      await feed.store.refresh();
      final first = feed.store.loadMore();
      final second = feed.store.loadMore();
      await second;
      expect(feed.store.state.map((work) => work.id), [1, 2, 3]);
      await first;
      expect(feed.asked, [null, 'next']);
      feed.store.destroy();
    });

    test('a refresh forgets that the last next page failed', () async {
      final feed = _Feed()..failing = true;
      await feed.store.refresh();
      await feed.store.loadMore();
      expect(feed.store.loadMoreFailed, isTrue);
      await feed.store.refresh();
      expect(feed.store.loadMoreFailed, isFalse);
      feed.store.destroy();
    });

    test('a list without a store pages through what it has and nothing after', () {
      final pager = PixivIllustPagerStore([pixivWork(id: 1), pixivWork(id: 2)]);
      expect((pager.state.tail, pager.pageCount), (PixivPagerTail.none, 2));
      pager.destroy();
    });
  });

  group('swipe between works', () {
    testWidgets('is off by default: a tile opens its work alone', (tester) async {
      final feed = await _openFromGrid(tester, swipe: false);
      expect(find.byType(PixivIllustScreen), findsOneWidget);
      expect(find.byType(PixivIllustPager), findsNothing);
      expect(feed.asked, [null]);
      await disposePixiv(tester);
    });

    testWidgets('swipes to the next work, loads the list\'s next page and ends with no more', (tester) async {
      final feed = await _openFromGrid(tester);
      expect(find.byType(PixivIllustPager), findsOneWidget);
      expect(find.widgetWithText(AppBar, 'One'), findsOneWidget);
      expect(feed.asked, [null, 'next']);

      await _swipeToNextWork(tester);
      expect(find.widgetWithText(AppBar, 'Two'), findsOneWidget);
      await _swipeToNextWork(tester);
      expect(find.widgetWithText(AppBar, 'Three'), findsOneWidget);
      await _swipeToNextWork(tester);
      expect(find.text('No more works'), findsOneWidget);
      await disposePixiv(tester);
    });

    testWidgets('a failed next page says so, and Retry loads it', (tester) async {
      final feed = await _openFromGrid(tester, failing: true);
      await _swipeToNextWork(tester);
      await _swipeToNextWork(tester);
      expect(find.text('Couldn\'t load more works'), findsOneWidget);

      feed.failing = false;
      await tester.tap(find.widgetWithText(FilledButton, 'Retry'));
      await settlePixiv(tester);
      expect(find.widgetWithText(AppBar, 'Three'), findsOneWidget);
      await disposePixiv(tester);
    });

    testWidgets('a work of many pages turns to the next work only past its last page', (tester) async {
      final works = [pixivWork(id: 1, pages: 2, title: 'One'), pixivWork(id: 2, pages: 1, title: 'Two')];
      await pumpPixiv(
        tester,
        PixivIllustPager(
          illusts: works,
          initialIndex: 0,
          page: (work) => PixivIllustScreen(illust: work),
        ),
        client: (prefs) => _EchoClient(prefs, works),
      );
      final pages = find.byKey(const PageStorageKey('pixiv-detail-pages-1'));
      await tester.drag(pages, const Offset(-300, 0));
      await settlePixiv(tester);
      expect(find.widgetWithText(AppBar, 'One'), findsOneWidget);
      expect(find.text('2 / 2'), findsOneWidget);

      await tester.drag(pages, const Offset(-300, 0));
      await settlePixiv(tester);
      expect(find.widgetWithText(AppBar, 'Two'), findsOneWidget);
      await disposePixiv(tester);
    });
  });
}
