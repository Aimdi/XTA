import 'package:flutter/widgets.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_api.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_models.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_store.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_watchlist.dart';
import 'package:xta/plugins/pixiv/pixiv_ranking_modes.dart';
import 'package:xta/plugins/pixiv/pixiv_ranking_section.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:xta/plugins/pixiv/pixiv_view_state.dart';
import 'package:xta/plugins/pixiv/pixiv_watchlist.dart';
import 'package:xta/plugins/plugin_view_store.dart';

/// Hands out the store kept in [slot] for the session, creating it the first
/// time: `PluginSessionLease.obtain`.
typedef PixivSessionObtain = T Function<T extends Object>(String slot, T Function() create);

/// Novel mode's lists for one Home session and what its controls change.
/// Every loader reads the session's choices when it runs, so a session
/// restored from page storage needs nothing put back.
class PixivNovelSession {
  final PluginViewStore<PixivViewState> view;
  final PixivClient client;
  final PixivNovelListStore recommended;
  final PixivNovelListStore following;
  final PixivWatchlistStore watchlist;
  final PixivRankingPinsStore rankingPins;
  final PixivNovelListStore ranking;
  final PixivNovelListStore bookmarks;

  const PixivNovelSession._({
    required this.view,
    required this.client,
    required this.recommended,
    required this.following,
    required this.watchlist,
    required this.rankingPins,
    required this.ranking,
    required this.bookmarks,
  });

  factory PixivNovelSession.obtain(
    PixivSessionObtain obtain, {
    required PluginViewStore<PixivViewState> view,
    required PixivNovelApi api,
    required PixivClient client,
    required PixivMuteStore mute,
    required BasePrefService prefs,
  }) {
    PixivNovelView chosen() => view.state.novel;
    PixivNovelListStore list(String slot, PixivPageLoader<PixivNovel> loader) =>
        obtain(slot, () => PixivNovelListStore(loader, filter: mute.filterNovels));
    final pins = obtain('novelRankingPins', () => pixivNovelRankingPinsStore(prefs));
    String board() => pixivEffectiveRankingMode(chosen().rankingMode, pixivVisibleNovelRankingModes(pins, client));
    return PixivNovelSession._(
      view: view,
      client: client,
      recommended: list('novelRecommended', api.recommended),
      following: list(
        'novelFollowing',
        ({nextUrl}) => api.following(restrict: chosen().followRestrict, nextUrl: nextUrl),
      ),
      watchlist: obtain('novelWatchlist', () => pixivNovelWatchlistStore(api)),
      rankingPins: pins,
      ranking: list(
        'novelRanking',
        ({nextUrl}) => api.ranking(board(), date: pixivRankingDateParam(chosen().rankingDate), nextUrl: nextUrl),
      ),
      bookmarks: list(
        'novelBookmarks',
        ({nextUrl}) => api.ownBookmarks(restrict: chosen().bookmarksRestrict, nextUrl: nextUrl),
      ),
    );
  }

  PixivNovelView get state => view.state.novel;

  /// Every list, for emptying them all when the account changes.
  List<PixivPagedListStore<Object>> get all => [recommended, following, watchlist, ranking, bookmarks];

  PixivPagedListStore<Object> homeList(PixivNovelHomeSource source) => switch (source) {
    PixivNovelHomeSource.recommended => recommended,
    PixivNovelHomeSource.following => following,
    PixivNovelHomeSource.watchlist => watchlist,
  };

  /// The lists section [section] shows now; Search and More keep their own.
  List<PixivPagedListStore<Object>> storesFor(int section) => switch (section) {
    0 => [homeList(state.homeSource)],
    1 => [ranking],
    2 => [bookmarks],
    _ => const [],
  };

  /// Loads the lists [section] shows that have nothing yet.
  void ensureLoaded(int section) {
    for (final store in storesFor(section)) {
      if (store.state.isEmpty) store.refresh();
    }
  }

  void _choose(PixivNovelView next) => view.select(view.state.copyWith(novel: next));

  /// Chooses [next] and reloads [list] for it from its first page.
  Future<void> _reload(PixivPagedListStore<Object> list, PixivNovelView next) async {
    _choose(next);
    list.clear();
    await list.refresh();
  }

  void selectHomeSource(PixivNovelHomeSource source) {
    _choose(state.copyWith(homeSource: source));
    ensureLoaded(0);
  }

  Future<void> changeFollowRestrict(String restrict) => _reload(following, state.copyWith(followRestrict: restrict));

  Future<void> changeBookmarksRestrict(String restrict) =>
      _reload(bookmarks, state.copyWith(bookmarksRestrict: restrict));

  List<PixivRankingMode> get rankingModes => pixivVisibleNovelRankingModes(rankingPins, client);

  /// The board shown: the chosen one while its chip is there, else the first chip.
  String get rankingMode => pixivEffectiveRankingMode(state.rankingMode, rankingModes);

  /// Whether the chosen board lost its chip, unpinned or hidden by Show R-18.
  bool get rankingBehind => rankingMode != state.rankingMode;

  Future<void> changeRankingMode(String mode) => _reload(ranking, state.copyWith(rankingMode: mode));

  Future<void> changeRankingDate(DateTime? date) async {
    if (date == null && state.rankingDate == null) return;
    await _reload(ranking, date == null ? state.copyWith(clearRankingDate: true) : state.copyWith(rankingDate: date));
  }

  /// Moves to the first chip and drops the old board's novels; true when it moved.
  bool syncRankingMode() {
    if (!rankingBehind) return false;
    _choose(state.copyWith(rankingMode: rankingMode));
    ranking.clear();
    return true;
  }

  Future<void> editRankingModes(BuildContext context) => showPixivRankingModeSheet(
    context,
    pins: rankingPins,
    offered: pixivRankingModesOffered(pixivNovelRankingModes, showR18: client.showR18),
  );
}

/// The novel boards pinned under their own preference.
PixivRankingPinsStore pixivNovelRankingPinsStore(BasePrefService prefs) => PixivRankingPinsStore(
  prefs,
  prefKey: optionPluginPixivNovelRankingModes,
  table: pixivNovelRankingModes,
  defaults: pixivDefaultNovelRankingPins,
);

List<PixivRankingMode> pixivVisibleNovelRankingModes(PixivRankingPinsStore pins, PixivClient client) =>
    pixivVisibleRankingModes(pins.state, pixivNovelRankingModes, showR18: client.showR18);
