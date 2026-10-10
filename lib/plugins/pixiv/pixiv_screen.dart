import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_tag_picker.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_discovery_api.dart';
import 'package:xta/plugins/pixiv/pixiv_favorites_section.dart';
import 'package:xta/plugins/pixiv/pixiv_grid.dart';
import 'package:xta/plugins/pixiv/pixiv_home_section.dart';
import 'package:xta/plugins/pixiv/pixiv_more_pane.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_api.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_home.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_session.dart';
import 'package:xta/plugins/pixiv/pixiv_plugin.dart';
import 'package:xta/plugins/pixiv/pixiv_ranking_modes.dart';
import 'package:xta/plugins/pixiv/pixiv_ranking_section.dart';
import 'package:xta/plugins/pixiv/pixiv_search_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_session_account.dart';
import 'package:xta/plugins/pixiv/pixiv_settings.dart';
import 'package:xta/plugins/pixiv/pixiv_sign_in_body.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:xta/plugins/pixiv/pixiv_view_state.dart';
import 'package:xta/plugins/plugin_feed_insets.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/plugins/plugin_lazy_tabs.dart';
import 'package:xta/plugins/plugin_marks.dart';
import 'package:xta/plugins/plugin_session.dart';
import 'package:xta/plugins/plugin_view_store.dart';
import 'package:xta/ui/scroll_to_top.dart';

/// Flare-style Pixiv home: Home / Rankings / Favorites / Search / More, over
/// illustrations or, in Novel mode, novels.
///
/// This shell owns the session stores, the section switch and the mode; each
/// section lives in its own file.
class PixivScreen extends StatefulWidget {
  final ScrollController scrollController;

  const PixivScreen({super.key, required this.scrollController});

  @override
  State<PixivScreen> createState() => _PixivScreenState();
}

/// The ranking board the view and the pins name when each page is asked for.
PixivIllustPageLoader _rankingLoader(
  PixivDiscoveryApi api,
  PixivClient client,
  PluginViewStore<PixivViewState> view,
  PixivRankingPinsStore pins,
) =>
    ({nextUrl}) => api.ranking(
      pixivEffectiveRankingMode(view.state.rankingMode, _visibleRankingModes(pins, client)),
      date: pixivRankingDateParam(view.state.rankingDate),
      nextUrl: nextUrl,
    );

List<PixivRankingMode> _visibleRankingModes(PixivRankingPinsStore pins, PixivClient client) =>
    pixivVisibleRankingModes(pins.state, pixivIllustRankingModes, showR18: client.showR18);

/// The Following filter is a choice for one Home session; the app-wide feed
/// goes back to every follow when the session ends.
void _endViewSession(PluginViewStore<PixivViewState> view, PixivIllustListStore feed, PixivDiscoveryApi api) {
  if (view.state.followRestrict != 'all') feed.useLoader(({nextUrl}) => api.following(nextUrl: nextUrl));
  view.destroy();
}

class _PixivScreenState extends State<PixivScreen> {
  late final PluginSessionLease _session;
  late final PluginViewStore<PixivViewState> _view;
  late final PixivDiscoveryApi _api;
  late final PixivHomeStores _home;
  late final PixivRankingPinsStore _rankingPins;
  late final PixivIllustListStore _ranking;
  late final PixivIllustListStore _bookmarks;
  late final PixivNovelSession _novels;

  /// The account the lists were loaded for; another one empties them.
  late final PixivSessionAccountStore _account;
  final _recommendedScroll = ScrollController();
  final _rankingScroll = ScrollController();
  final _favoritesScroll = ScrollController();
  final _moreScroll = ScrollController();
  final _novelRankingScroll = ScrollController();
  final _novelFavoritesScroll = ScrollController();
  PixivViewState get _state => _view.state;

  @override
  void initState() {
    super.initState();
    _session = PluginSessionLease(context, 'pixiv');
    _obtainStores();
    WidgetsBinding.instance.addPostFrameCallback((_) => _warmUp());
  }

  void _obtainStores() {
    final client = context.read<PixivClient>();
    final feed = context.read<PixivFeedStore>();
    final mute = context.read<PixivMuteStore>();
    final prefs = PrefService.of(context, listen: false);
    final api = _api = PixivDiscoveryApi.of(context);
    final view = _view = _session.obtain(
      'view',
      () => PluginViewStore<PixivViewState>(PixivViewState(section: _startSection)),
      dispose: (view) => _endViewSession(view, feed, api),
    );
    _home = PixivHomeStores.obtain(_session, following: feed, client: client, api: api, mute: mute);
    final pins = _rankingPins = _session.obtain('rankingPins', () => PixivRankingPinsStore(prefs));
    _ranking = _session.obtain(
      'ranking',
      () => PixivIllustListStore(_rankingLoader(api, client, view, pins), filter: mute.filter),
    );
    _bookmarks = _session.obtain(
      'bookmarks',
      () => PixivIllustListStore(
        _bookmarksLoader(_state.bookmarksRestrict, tag: _state.bookmarkTag),
        filter: mute.filter,
      ),
    );
    _novels = PixivNovelSession.obtain(
      _session.obtain,
      view: view,
      api: PixivNovelApi.of(context),
      client: client,
      mute: mute,
      prefs: prefs,
    );
    _watchAccount(prefs);
  }

  /// Empties every session list when the reader signs out or switches account,
  /// including while this screen was away and the session kept its lists.
  void _watchAccount(BasePrefService prefs) {
    final lists = [..._home.all, _ranking, _bookmarks, ..._novels.all];
    _account = _session.obtain(
      'account',
      () => PixivSessionAccountStore(
        prefs,
        onSwitched: () {
          for (final list in lists) {
            list.clear();
          }
        },
      ),
    );
    _account.check();
  }

  Future<void> _warmUp() async {
    if (!mounted) return;
    await context.read<PixivMuteStore>().load();
    if (!mounted || !_hasToken) return;
    // Warm the token once so the first feed call does not serialise behind
    // a cold refresh, and concurrent tab loads share one in-flight refresh.
    unawaited(context.read<PixivClient>().ensureAccessToken());
    _ensureTabLoaded(_state.section);
  }

  int get _startSection =>
      pixivStartSectionIndex(PrefService.of(context, listen: false).get<String>(optionPluginPixivStartSection));

  bool get _hasToken =>
      (PrefService.of(context, listen: false).get<String>(optionPluginPixivRefreshToken) ?? '').trim().isNotEmpty;

  PixivIllustPageLoader _bookmarksLoader(String restrict, {String? tag}) {
    final client = context.read<PixivClient>();
    return ({nextUrl}) async {
      // Prefer the stored id — verify() always hits the token endpoint and made
      // the Bookmarks tab feel like it loaded forever on every open.
      final userId = await client.ensureUserId();
      return client.bookmarks(userId: userId, restrict: restrict, tag: tag, nextUrl: nextUrl);
    };
  }

  @override
  void dispose() {
    if (_state.signingIn) _view.select(_state.copyWith(signingIn: false));
    for (final controller in [
      _recommendedScroll,
      _rankingScroll,
      _favoritesScroll,
      _moreScroll,
      _novelRankingScroll,
      _novelFavoritesScroll,
    ]) {
      controller.dispose();
    }
    _session.dispose();
    super.dispose();
  }

  /// The list the reader sees in [section] now; Search keeps its own lists
  /// and is reached through the route's primary controller.
  ScrollController? _scrollControllerFor(int section) => switch (section) {
    0 when _state.novelMode || _state.homeSource == PixivHomeSource.following => widget.scrollController,
    1 when _state.novelMode => _novelRankingScroll,
    2 when _state.novelMode => _novelFavoritesScroll,
    0 => _recommendedScroll,
    1 => _rankingScroll,
    2 => _favoritesScroll,
    4 => _moreScroll,
    _ => PrimaryScrollController.maybeOf(context),
  };

  /// Tapping the section or sub-tab already shown brings its list back to the top.
  Future<void> _scrollToTop() =>
      scrollToTop(context, pluginInnerScrollController(context, _scrollControllerFor(_state.section)));

  /// After a sign-in, switch or sign-out: lists loaded for another account
  /// are emptied, and the shown one loads for the account now in use.
  void _onAuthChanged() {
    if (!mounted) return;
    _account.check();
    _ensureTabLoaded(_state.section);
    _view.select(_state.copyWith());
  }

  /// An account changed somewhere this screen did not hear of, such as the
  /// plugin's page in Settings.
  void _followAccount() {
    if (mounted && _account.behind) _onAuthChanged();
  }

  List<PixivPagedListStore<Object>> _storesFor(int section) => switch (section) {
    _ when _state.novelMode => _novels.storesFor(section),
    0 => _home.storesFor(_state.homeSource),
    1 => [_ranking],
    2 => [_bookmarks],
    _ => const [],
  };

  void _ensureTabLoaded(int index) {
    if (!_hasToken) return;
    for (final store in _storesFor(index)) {
      if (store.state.isEmpty) store.refresh();
    }
  }

  void _selectTab(int index) {
    if (_state.section == index) {
      _scrollToTop();
      return;
    }
    _view.select(_state.copyWith(section: index));
    _ensureTabLoaded(index);
  }

  /// Switches the sections between illustrations and novels; each mode keeps its own lists and choices.
  void _changeMode(PixivContentMode mode) {
    _view.select(_state.copyWith(mode: mode));
    _ensureTabLoaded(_state.section);
  }

  void _selectHomeSource(PixivHomeSource source) {
    if (source == _state.homeSource) {
      _scrollToTop();
      return;
    }
    _view.select(_state.copyWith(homeSource: source));
    _ensureTabLoaded(0);
  }

  void _useFollowRestrict(String restrict) =>
      _home.following.useLoader(({nextUrl}) => _api.following(restrict: restrict, nextUrl: nextUrl));

  Future<void> _changeFollowRestrict(String restrict) async {
    if (restrict == _state.followRestrict) return;
    _view.select(_state.copyWith(followRestrict: restrict));
    _useFollowRestrict(restrict);
    await _home.following.refresh();
  }

  List<PixivRankingMode> get _rankingModes => _visibleRankingModes(_rankingPins, context.read<PixivClient>());

  String get _rankingMode => pixivEffectiveRankingMode(_state.rankingMode, _rankingModes);

  void _useRankingLoader() =>
      _ranking.useLoader(_rankingLoader(_api, context.read<PixivClient>(), _view, _rankingPins));

  Future<void> _reloadRanking() async {
    _useRankingLoader();
    await _ranking.refresh();
  }

  Future<void> _changeRankingMode(String mode) async {
    if (mode == _state.rankingMode) {
      await _scrollToTop();
      return;
    }
    _view.select(_state.copyWith(rankingMode: mode));
    await _reloadRanking();
  }

  /// Runs after any build that finds the shown board without its chip:
  /// unpinned, or hidden by Show R-18 going off from whichever settings entry.
  void _scheduleRankingSync() {
    if (_rankingMode == _state.rankingMode && !_novels.rankingBehind) return;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _syncRankingMode();
    });
  }

  /// Moves to the first chip and drops the old board's works or novels; they
  /// reload now if Rankings is on screen, else when it is next opened.
  void _syncRankingMode() {
    final mode = _rankingMode;
    final illustMoved = mode != _state.rankingMode;
    if (illustMoved) {
      _view.select(_state.copyWith(rankingMode: mode));
      _useRankingLoader();
    }
    final novelMoved = _novels.syncRankingMode();
    final shownMoved = _state.novelMode ? novelMoved : illustMoved;
    if (shownMoved && _state.section == 1) _ensureTabLoaded(1);
  }

  Future<void> _editRankingModes() {
    final offered = pixivRankingModesOffered(pixivIllustRankingModes, showR18: context.read<PixivClient>().showR18);
    return showPixivRankingModeSheet(context, pins: _rankingPins, offered: offered);
  }

  Future<void> _changeRankingDate(DateTime? date) async {
    if (!mounted || (date == null && _state.rankingDate == null)) return;
    _view.select(date == null ? _state.copyWith(clearRankingDate: true) : _state.copyWith(rankingDate: date));
    await _reloadRanking();
  }

  Future<void> _filterBookmarks(PixivBookmarkFilter filter) async {
    if (filter.restrict == _state.bookmarksRestrict && filter.tag == _state.bookmarkTag) {
      await _scrollToTop();
      return;
    }
    _view.select(
      _state.copyWith(
        bookmarksRestrict: filter.restrict,
        bookmarkTag: filter.tag,
        clearBookmarkTag: filter.tag == null,
      ),
    );
    _bookmarks.useLoader(_bookmarksLoader(filter.restrict, tag: filter.tag));
    await _bookmarks.refresh();
  }

  Future<void> _signIn() async {
    _view.select(_state.copyWith(signingIn: true));
    try {
      await runPixivSignIn(context);
      _onAuthChanged();
    } finally {
      if (mounted) _view.select(_state.copyWith(signingIn: false));
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_view.restore(context, 'pixiv')) {
      _bookmarks.useLoader(_bookmarksLoader(_state.bookmarksRestrict, tag: _state.bookmarkTag));
      if (_state.followRestrict != 'all') _useFollowRestrict(_state.followRestrict);
    }
    final prefs = PrefService.of(context);
    final hasToken = (prefs.get<String>(optionPluginPixivRefreshToken) ?? '').trim().isNotEmpty;
    if (_account.behind) WidgetsBinding.instance.addPostFrameCallback((_) => _followAccount());
    _scheduleRankingSync();

    return Scaffold(
      primary: !PluginEmbedded.maybeOf(context),
      body: ScopedBuilder<PluginViewStore<PixivViewState>, PixivViewState>(
        store: _view,
        onState: (context, state) => Column(
          children: [
            PixivHomeChrome(index: state.section, onSelect: _selectTab, mode: state.mode, onMode: _changeMode),
            const Divider(height: 1),
            Expanded(
              child: !hasToken && state.section != 4
                  ? PixivSignInBody(signingIn: state.signingIn, onSignIn: _signIn)
                  : PluginLazyTabs(
                      onSelected: _selectTab,
                      index: state.section,
                      children: state.novelMode ? _novelSections(state) : _sections(state),
                    ),
            ),
          ],
        ),
      ),
    );
  }

  List<WidgetBuilder> _sections(PixivViewState state) => [
    (_) => PixivHomeSection(
      source: state.homeSource,
      onSource: _selectHomeSource,
      followRestrict: state.followRestrict,
      onFollowRestrict: _changeFollowRestrict,
      stores: _home,
      scrollController: widget.scrollController,
      recommendedScrollController: _recommendedScroll,
    ),
    (_) => ScopedBuilder<PixivRankingPinsStore, List<String>>(
      store: _rankingPins,
      onState: (_, _) {
        _scheduleRankingSync();
        return PixivRankingSection(
          modes: _rankingModes,
          mode: _rankingMode,
          date: state.rankingDate,
          onMode: _changeRankingMode,
          onEditModes: _editRankingModes,
          onDate: _changeRankingDate,
          feed: PixivIllustFeed(
            store: _ranking,
            emptyMessage: L10n.of(context).plugin_pixiv_ranking_empty,
            scrollController: _rankingScroll,
          ),
        );
      },
    ),
    (_) => PixivFavoritesSection(
      restrict: state.bookmarksRestrict,
      tag: state.bookmarkTag,
      onFilter: _filterBookmarks,
      store: _bookmarks,
      scrollController: _favoritesScroll,
    ),
    (_) => const PixivSearchScreen(embedded: true),
    (_) => PixivMorePane(onAuthChanged: _onAuthChanged, scrollController: _moreScroll),
  ];

  /// Novel mode's sections; Search and More are the illustration ones until novel search lands.
  List<WidgetBuilder> _novelSections(PixivViewState state) => [
    for (final body in _novelBodies(state)) (context) => _novelStorage(body(context)),
    ..._sections(state).skip(3),
  ];

  /// Lists under the section's storage key share their saved offsets, so each
  /// novel body gets a key of its own: without it, switching mode opened the
  /// novel list at the illustration list's offset, and back.
  Widget _novelStorage(Widget body) =>
      KeyedSubtree(key: const PageStorageKey<PixivContentMode>(PixivContentMode.novel), child: body);

  List<WidgetBuilder> _novelBodies(PixivViewState state) => [
    (_) => PixivNovelHomeSection(
      session: _novels,
      view: state.novel,
      onReselect: _scrollToTop,
      scrollController: widget.scrollController,
    ),
    (_) => PixivNovelRankingSection(
      session: _novels,
      view: state.novel,
      onReselect: _scrollToTop,
      onPinsChanged: _scheduleRankingSync,
      scrollController: _novelRankingScroll,
    ),
    (_) => PixivNovelFavoritesSection(
      session: _novels,
      view: state.novel,
      onReselect: _scrollToTop,
      scrollController: _novelFavoritesScroll,
    ),
  ];
}

/// Icon tabs matching Flare's Home / Rankings / Favorites / Search / More,
/// sitting under the Start strip instead of a nested AppBar that never painted.
class PixivHomeChrome extends StatelessWidget {
  final int index;
  final ValueChanged<int> onSelect;

  /// Whether the sections show illustrations or novels.
  final PixivContentMode mode;

  /// Switches [mode]; without it there is no mode button.
  final ValueChanged<PixivContentMode>? onMode;

  const PixivHomeChrome({
    super.key,
    required this.index,
    required this.onSelect,
    this.mode = PixivContentMode.illust,
    this.onMode,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return PluginHomeChrome(
      title: l10n.plugin_pixiv_title,
      mark: pluginMark(PixivPlugin(), size: 24),
      accent: const Color(0xFF0096FA),
      actions: [if (onMode case final onMode?) _modeButton(context, onMode)],
      // At phone widths the mode button would fold the five tabs into the
      // section picker; the mark makes room for it instead.
      markGivesWay: true,
      tabs: [
        PluginHomeTab(icon: Icons.home_outlined, label: l10n.home, selected: index == 0, onTap: () => onSelect(0)),
        PluginHomeTab(
          icon: Icons.bar_chart,
          label: l10n.plugin_pixiv_tab_ranking,
          selected: index == 1,
          onTap: () => onSelect(1),
        ),
        PluginHomeTab(
          icon: Icons.favorite_border,
          label: l10n.plugin_pixiv_tab_favorites,
          selected: index == 2,
          onTap: () => onSelect(2),
        ),
        PluginHomeTab(icon: Icons.search, label: l10n.search, selected: index == 3, onTap: () => onSelect(3)),
        PluginHomeTab(
          icon: Icons.menu,
          label: l10n.plugin_pixiv_tab_more,
          selected: index == 4,
          onTap: () => onSelect(4),
        ),
      ],
    );
  }

  /// One 48 dp button naming the mode it switches to, so Home's dock can list
  /// it in its sheet. Lit while novels show.
  IconButton _modeButton(BuildContext context, ValueChanged<PixivContentMode> onMode) {
    final l10n = L10n.of(context);
    final novels = mode == PixivContentMode.novel;
    return IconButton(
      key: const ValueKey('pixiv-mode-toggle'),
      tooltip: novels ? l10n.plugin_pixiv_mode_illusts : l10n.plugin_pixiv_mode_novels,
      color: novels ? Theme.of(context).colorScheme.primary : null,
      icon: Icon(novels ? Icons.image_outlined : Icons.menu_book_outlined),
      onPressed: () => onMode(novels ? PixivContentMode.illust : PixivContentMode.novel),
    );
  }
}
