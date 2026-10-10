import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_staggered_grid_view/flutter_staggered_grid_view.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_grid.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_paged_feed.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:xta/plugins/plugin_feed_skeleton.dart';
import 'package:xta/ui/empty_pane.dart';
import 'package:xta/ui/errors.dart';

import 'support/pixiv_reader_harness.dart';

/// Three pages of twelve works handed out on request, each ask recorded.
class _Pages {
  static const pages = 3;
  static const perPage = 12;
  final asked = <String?>[];
  Completer<void>? gate;
  Object? failure;

  Future<PixivIllustPage> load({String? nextUrl}) async {
    asked.add(nextUrl);
    await gate?.future;
    if (failure case final error?) throw error;
    final index = nextUrl == null ? 0 : int.parse(nextUrl.substring('page'.length));
    return PixivIllustPage(
      illusts: [for (var i = 0; i < perPage; i++) pixivWork(id: 1000 + index * perPage + i, pages: 1)],
      nextUrl: index + 1 < pages ? 'page${index + 1}' : null,
    );
  }
}

Widget _feed(PixivIllustListStore store, {List<Widget> leadingSlivers = const []}) => Scaffold(
  body: PixivIllustFeed(store: store, emptyMessage: 'Nothing here', leadingSlivers: leadingSlivers),
);

/// A sideways strip that, unlike Home's header, lets its scroll notifications through.
Widget _strip() => SliverToBoxAdapter(
  child: SizedBox(
    height: 80,
    child: ListView(
      key: const ValueKey('strip'),
      scrollDirection: Axis.horizontal,
      children: [for (var i = 0; i < 20; i++) SizedBox(width: 120, child: Text('Chip $i'))],
    ),
  ),
);

int _columns(WidgetTester tester) =>
    (tester.widget<SliverMasonryGrid>(find.byType(SliverMasonryGrid)).gridDelegate
            as SliverSimpleGridDelegateWithFixedCrossAxisCount)
        .crossAxisCount;

Map<int, PixivIllustTile> _tiles(WidgetTester tester) => {
  for (final tile in tester.widgetList<PixivIllustTile>(find.byType(PixivIllustTile, skipOffstage: false)))
    tile.illust.id: tile,
};

double _offset(WidgetTester tester) => tester.state<ScrollableState>(find.byType(Scrollable).first).position.pixels;

/// Ten small steps, one frame each, that reveal no new tile.
Future<void> _nudge(WidgetTester tester) async {
  final gesture = await tester.startGesture(tester.getCenter(find.byType(CustomScrollView)));
  await gesture.moveBy(const Offset(0, -40));
  await tester.pump();
  for (var i = 0; i < 10; i++) {
    await gesture.moveBy(const Offset(0, -2));
    await tester.pump();
  }
  await gesture.up();
  await tester.pump();
}

void main() {
  late _Pages pages;
  late PixivIllustListStore store;

  setUp(() {
    pages = _Pages();
    store = PixivIllustListStore(pages.load);
  });

  tearDown(() => store.destroy());

  testWidgets('a works feed is the one paged feed: skeleton first, then the grid kept through a refresh', (
    tester,
  ) async {
    await pumpPixiv(tester, _feed(store));
    expect(find.byType(PixivPagedFeed<PixivIllust>), findsOneWidget);
    pages.gate = Completer<void>();
    unawaited(store.refresh());
    // The skeleton pulses for as long as it shows, so this waits frame by frame rather than settling.
    await tester.pump(const Duration(milliseconds: 100));
    expect(find.byType(PluginGridSkeleton), findsOneWidget);

    pages.gate!.complete();
    await tester.pump(const Duration(milliseconds: 100));
    await settlePixiv(tester);
    expect(find.byType(PluginGridSkeleton), findsNothing);
    expect(find.byType(SliverMasonryGrid), findsOneWidget);

    pages.gate = Completer<void>();
    unawaited(store.refresh());
    await tester.pump(const Duration(milliseconds: 100));
    expect(pages.asked, [null, null]);
    expect(find.byType(PluginGridSkeleton), findsNothing, reason: 'a soft refresh keeps the works on screen');
    expect(find.byType(SliverMasonryGrid), findsOneWidget);
    pages.gate!.complete();
    await settlePixiv(tester);
    await disposePixiv(tester);
  });

  testWidgets('an empty works feed offers Retry, and a failed first page says why', (tester) async {
    final empty = PixivIllustListStore(({nextUrl}) async => const PixivIllustPage(illusts: []));
    addTearDown(empty.destroy);
    unawaited(empty.refresh());
    await pumpPixiv(tester, _feed(empty));
    expect(find.byType(EmptyPane), findsOneWidget);
    expect(find.text('Nothing here'), findsOneWidget);
    expect(find.byIcon(Icons.photo_outlined), findsOneWidget);
    expect(find.text('Retry'), findsOneWidget);
    await disposePixiv(tester);

    pages.failure = PixivException(PixivErrorKind.network, 'offline');
    unawaited(store.refresh());
    await pumpPixiv(tester, _feed(store));
    expect(find.byType(FullPageErrorWidget), findsOneWidget);

    pages.failure = null;
    await tester.tap(find.text('Retry'));
    await settlePixiv(tester);
    expect(find.byType(FullPageErrorWidget), findsNothing);
    expect(find.byType(SliverMasonryGrid), findsOneWidget);
    await disposePixiv(tester);
  });

  testWidgets('its own scroll asks for the next page; a sideways strip above the works never does', (tester) async {
    unawaited(store.refresh());
    await pumpPixiv(tester, _feed(store, leadingSlivers: [_strip()]));
    expect(pages.asked, [null]);

    await tester.drag(find.byKey(const ValueKey('strip')), const Offset(-1500, 0));
    await settlePixiv(tester);
    expect(pages.asked, [null], reason: 'a strip inside the list does not page it');

    await tester.drag(find.byType(CustomScrollView), const Offset(0, -900));
    await settlePixiv(tester);
    expect(pages.asked.take(2), [null, 'page1'], reason: 'the works scrolling near their end ask for more');
    await disposePixiv(tester);
  });

  testWidgets('columns are worked out over the whole width, outside the grid\'s padding', (tester) async {
    unawaited(store.refresh());
    await pumpPixiv(
      tester,
      _feed(store),
      client: (prefs) {
        // 390 / 96 fits four tiles; the 382 dp inside the padding would fit only three.
        prefs.set(optionPluginPixivGridColumnsPortrait, 4);
        return FakePixivClient(prefs);
      },
    );
    expect(_columns(tester), 4);
    await disposePixiv(tester);
  });

  testWidgets('a fixed works grid scrolls the same way, without paging its source', (tester) async {
    unawaited(store.refresh());
    await tester.pump(const Duration(milliseconds: 100));
    expect(store.state, hasLength(_Pages.perPage));
    await pumpPixiv(
      tester,
      Scaffold(
        body: PixivIllustGrid(illusts: store.state, source: store),
      ),
    );
    expect(find.byType(PixivPagedFeed<PixivIllust>), findsNothing);
    expect(find.byType(PixivIllustTile), findsWidgets);

    await tester.drag(find.byType(CustomScrollView), const Offset(0, -900));
    await settlePixiv(tester);
    expect(_offset(tester), greaterThan(0), reason: 'the works scroll');
    expect(pages.asked, [null], reason: 'only a paged feed asks for the next page as it scrolls');
    await disposePixiv(tester);
  });

  testWidgets('scrolling a fixed grid builds no shown tile again', (tester) async {
    final works = [for (var i = 0; i < 200; i++) pixivWork(id: i + 1, pages: 1)];
    final builds = <int, int>{};
    Widget tile(BuildContext context, List<PixivIllust> illusts, int index) {
      builds.update(index, (count) => count + 1, ifAbsent: () => 1);
      return SizedBox(height: 120, child: Text('${illusts[index].id}'));
    }

    await pumpPixiv(
      tester,
      Scaffold(
        body: PixivIllustGrid(illusts: works, tileBuilder: tile),
      ),
    );
    final before = Map.of(builds);
    expect(before, isNotEmpty);
    await _nudge(tester);
    expect(_offset(tester), greaterThan(0));
    expect({for (final index in before.keys) index: builds[index]}, before);
    await disposePixiv(tester);
  });

  testWidgets('scrolling a works feed keeps every built tile as it was', (tester) async {
    final works = [for (var i = 0; i < 200; i++) pixivWork(id: i + 1, pages: 1)];
    final whole = PixivIllustListStore(({nextUrl}) async => PixivIllustPage(illusts: works));
    addTearDown(whole.destroy);
    unawaited(whole.refresh());
    await pumpPixiv(tester, _feed(whole));
    final before = _tiles(tester);
    expect(before, isNotEmpty);
    await _nudge(tester);
    expect(_offset(tester), greaterThan(0));
    final after = _tiles(tester);
    final kept = before.keys.where(after.containsKey);
    expect(kept, isNotEmpty);
    expect(kept.where((id) => !identical(before[id], after[id])), isEmpty, reason: 'no shown tile is built again');
    await disposePixiv(tester);
  });
}
