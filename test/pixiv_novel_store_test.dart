import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_models.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_series_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_session.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_store.dart';
import 'package:xta/plugins/pixiv/pixiv_ranking_modes.dart';
import 'package:xta/plugins/pixiv/pixiv_view_state.dart';
import 'package:xta/plugins/plugin_view_store.dart';

import 'support/pixiv_novel_fakes.dart';

PixivNovelSeriesPage _seriesPage({
  required List<PixivNovelChapter> chapters,
  required int listed,
  String? nextUrl,
  bool watched = false,
}) => PixivNovelSeriesPage(
  series: PixivNovelSeries(id: 77, title: 'Seasons', user: pixivNovel().user, watchlistAdded: watched),
  first: pixivNovel(id: 10),
  latest: pixivNovel(id: 19),
  chapters: chapters,
  listed: listed,
  nextUrl: nextUrl,
);

void main() {
  group('novel bookmark overrides', () {
    test('follow the card until a write, and move the count by one', () {
      final store = PixivNovelBookmarkStore();
      final plain = pixivNovel(id: 1, bookmarks: 10);
      final kept = pixivNovel(id: 2, bookmarked: true, bookmarks: 5);

      expect((store.isBookmarked(plain), store.bookmarkCount(plain)), (false, 10));
      expect((store.isBookmarked(kept), store.bookmarkCount(kept)), (true, 5));

      store
        ..mark(1, true)
        ..mark(2, false);
      expect((store.isBookmarked(plain), store.bookmarkCount(plain)), (true, 11));
      expect((store.isBookmarked(kept), store.bookmarkCount(kept)), (false, 4));
      store.destroy();
    });
  });

  group('novel bookmark actions', () {
    late PrefServiceCache prefs;
    late FakePixivNovelApi api;
    late PixivNovelBookmarkStore bookmarks;

    PixivNovelBookmarkActions actions() => PixivNovelBookmarkActions(api: api, bookmarks: bookmarks, prefs: prefs);

    setUp(() {
      prefs = PrefServiceCache(defaults: {optionPluginPixivDefaultPrivateBookmark: false});
      api = FakePixivNovelApi(PixivClient(prefs));
      bookmarks = PixivNovelBookmarkStore();
    });

    tearDown(() => bookmarks.destroy());

    test('a tap bookmarks with the default visibility, then removes', () async {
      final novel = pixivNovel(id: 5);
      expect(await actions().toggle(novel), isTrue);
      expect(await actions().toggle(novel), isFalse);

      await prefs.set(optionPluginPixivDefaultPrivateBookmark, true);
      expect(await actions().toggle(novel), isTrue);
      expect(api.calls, ['bookmark:5:public', 'unbookmark:5', 'bookmark:5:private']);
    });

    test('a long press always files it privately, even a public bookmark', () async {
      final novel = pixivNovel(id: 6, bookmarked: true);
      expect(await actions().bookmarkPrivately(novel), isTrue);
      expect(api.calls, ['bookmark:6:private']);
      expect(bookmarks.isBookmarked(novel), isTrue);
    });

    test('a write while one is on its way is skipped, and a failed one leaves the heart as it was', () async {
      final novel = pixivNovel(id: 7);
      final gate = Completer<void>();
      final held = bookmarks.exclusive(7, () => gate.future);
      expect(await actions().toggle(novel), isNull);
      gate.complete();
      await held;

      api.writeError = PixivException(PixivErrorKind.network, 'offline');
      await expectLater(actions().toggle(novel), throwsA(isA<PixivException>()));
      expect((bookmarks.isBookmarked(novel), bookmarks.isBusy(7)), (false, false));
    });
  });

  group('novel series', () {
    late FakePixivNovelApi api;
    late PixivNovelSeriesStore store;

    setUp(() {
      api = FakePixivNovelApi(PixivClient(PrefServiceCache()));
      store = PixivNovelSeriesStore(api, 77);
    });

    tearDown(() => store.destroy());

    test('numbers chapters across pages, counting the ones a page hid', () async {
      const next = 'https://app-api.pixiv.net/v2/novel/series?series_id=77&last_order=3';
      api.seriesPages = {
        null: _seriesPage(
          chapters: [
            PixivNovelChapter(order: 1, novel: pixivNovel(id: 10)),
            PixivNovelChapter(order: 3, novel: pixivNovel(id: 12)),
          ],
          listed: 3,
          nextUrl: next,
        ),
        next: _seriesPage(chapters: [PixivNovelChapter(order: 1, novel: pixivNovel(id: 13))], listed: 1),
      };

      final first = await store.loadPage();
      final second = await store.loadPage(nextUrl: first.nextUrl);

      expect([for (final chapter in first.items) chapter.order], [1, 3]);
      expect([for (final chapter in second.items) (chapter.order, chapter.novel.id)], [(4, 13)]);
      expect(
        (store.state.series!.series.title, store.state.series!.first!.id, store.state.series!.latest!.id),
        ('Seasons', 10, 19),
      );
    });

    test('the watchlist toggle writes, and a failure leaves it as it was', () async {
      api.seriesPages = {null: _seriesPage(chapters: const [], listed: 0)};
      await store.loadPage();

      await store.toggleWatchlist();
      expect((api.calls.last, store.state.series!.series.watchlistAdded), ('watch:77', true));

      api.writeError = PixivException(PixivErrorKind.network, 'offline');
      await expectLater(store.toggleWatchlist(), throwsA(isA<PixivException>()));
      expect((store.state.series!.series.watchlistAdded, store.state.busy), (true, false));
    });
  });

  group('novel session', () {
    late PrefServiceCache prefs;
    late PluginViewStore<PixivViewState> view;
    late FakePixivNovelApi api;
    late PixivMuteStore mute;
    late PixivNovelSession session;
    late List<void Function()> disposers;

    setUp(() {
      prefs = PrefServiceCache(
        defaults: {
          optionPluginPixivShowR18: true,
          optionPluginPixivUserId: 77,
          optionPluginPixivNovelRankingModes: jsonEncode([...pixivDefaultNovelRankingPins, 'day_r18']),
        },
      );
      view = PluginViewStore(const PixivViewState(mode: PixivContentMode.novel));
      api = FakePixivNovelApi(PixivClient(prefs));
      mute = PixivMuteStore(prefs);
      disposers = [];
      session = PixivNovelSession.obtain(
        <T extends Object>(String slot, T Function() create) {
          final value = create();
          if (value is Store) disposers.add(value.destroy);
          return value;
        },
        view: view,
        api: api,
        client: api.client,
        mute: mute,
        prefs: prefs,
      );
    });

    tearDown(() {
      for (final dispose in disposers) {
        dispose();
      }
      view.destroy();
      mute.destroy();
    });

    test('each list loads with the choices made, read when it loads', () async {
      session.selectHomeSource(PixivNovelHomeSource.following);
      // A store's first load waits out triple's 50 ms debounce.
      await Future<void>.delayed(const Duration(milliseconds: 80));
      await session.changeFollowRestrict('private');
      await session.changeBookmarksRestrict('private');
      await session.changeRankingMode('week');
      await session.changeRankingDate(DateTime(2026, 8, 1));

      expect(api.calls, [
        'following:public',
        'following:private',
        'own:private',
        'rank:week:null',
        'rank:week:2026-08-01',
      ]);
      expect(view.state.novel.followRestrict, 'private');
      expect(view.state.section, 0, reason: 'novel choices leave the illustration ones alone');
    });

    test('a board hidden by Show R-18 going off moves to the first chip', () async {
      await session.changeRankingMode('day_r18');
      expect(session.rankingModes.map((mode) => mode.id), contains('day_r18'));

      await prefs.set(optionPluginPixivShowR18, false);
      expect((session.rankingBehind, session.rankingMode), (true, 'day'));
      expect(session.syncRankingMode(), isTrue);
      expect((view.state.novel.rankingMode, session.ranking.state.length), ('day', 0));
      expect(session.syncRankingMode(), isFalse);
    });

    test('the novel boards pin under their own preference, with every board R-18 does not gate by default', () {
      final defaults = pixivNovelRankingPinsStore(PrefServiceCache());
      expect(defaults.state, pixivDefaultNovelRankingPins);
      expect(defaults.prefKey, optionPluginPixivNovelRankingModes);
      expect(
        pixivNovelRankingModes.where((mode) => !mode.r18).map((mode) => mode.id),
        unorderedEquals(pixivDefaultNovelRankingPins),
      );
      defaults.destroy();
    });
  });
}
