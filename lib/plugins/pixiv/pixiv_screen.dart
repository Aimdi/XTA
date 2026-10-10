import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_discovery_api.dart';
import 'package:xta/plugins/pixiv/pixiv_favorites_section.dart';
import 'package:xta/plugins/pixiv/pixiv_home_section.dart';
import 'package:xta/plugins/pixiv/pixiv_more_pane.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_plugin.dart';
import 'package:xta/plugins/pixiv/pixiv_ranking_modes.dart';
import 'package:xta/plugins/pixiv/pixiv_ranking_section.dart';
import 'package:xta/plugins/pixiv/pixiv_search_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_session_account.dart';
import 'package:xta/plugins/pixiv/pixiv_settings.dart';
import 'package:xta/plugins/pixiv/pixiv_sign_in_body.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:xta/plugins/pixiv/pixiv_view_state.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/plugins/plugin_lazy_tabs.dart';
import 'package:xta/plugins/plugin_marks.dart';
import 'package:xta/plugins/plugin_session.dart';
import 'package:xta/plugins/plugin_view_store.dart';

/// Flare-style Pixiv home: Home / Rankings / Favorites / Search / More.
///
/// This shell owns the session stores and the section switch; each section
/// lives in its own file.
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
      () => PluginViewStore<PixivViewState>(const PixivViewState()),
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
      () => PixivIllustListStore(_bookmarksLoader(_state.bookmarksRestrict), filter: mute.filter),
    );
    _watchAccount(prefs);
  }

  /// Empties every session list when the reader signs out or switches account.
  void _watchAccount(BasePrefService prefs) {
    final lists = [..._home.all, _ranking, _bookmarks];
    _session.obtain(
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

  bool get _hasToken =>
      (PrefService.of(context, listen: false).get<String>(optionPluginPixivRefreshToken) ?? '').trim().isNotEmpty;

  PixivIllustPageLoader _bookmarksLoader(String restrict) {
    final client = context.read<PixivClient>();
    return ({nextUrl}) async {
      // Prefer the stored id — verify() always hits the token endpoint and made
      // the Bookmarks tab feel like it loaded forever on every open.
      final userId = await client.ensureUserId();
      return client.bookmarks(userId: userId, restrict: restrict, nextUrl: nextUrl);
    };
  }

  @override
  void dispose() {
    if (_state.signingIn) _view.select(_state.copyWith(signingIn: false));
    _session.dispose();
    super.dispose();
  }

  List<PixivPagedListStore<Object>> _storesFor(int section) => switch (section) {
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
    if (_state.section == index) return;
    _view.select(_state.copyWith(section: index));
    _ensureTabLoaded(index);
  }

  void _selectHomeSource(PixivHomeSource source) {
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
    if (mode == _state.rankingMode) return;
    _view.select(_state.copyWith(rankingMode: mode));
    await _reloadRanking();
  }

  /// Runs after any build that finds the shown board without its chip:
  /// unpinned, or hidden by Show R-18 going off from whichever settings entry.
  void _scheduleRankingSync() {
    if (_rankingMode == _state.rankingMode) return;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _syncRankingMode();
    });
  }

  /// Moves to the first chip and drops the old board's works; they reload now
  /// if Rankings is on screen, else when it is next opened.
  void _syncRankingMode() {
    final mode = _rankingMode;
    if (mode == _state.rankingMode) return;
    _view.select(_state.copyWith(rankingMode: mode));
    _useRankingLoader();
    if (_state.section == 1) _ensureTabLoaded(1);
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

  Future<void> _changeBookmarksRestrict(String restrict) async {
    if (restrict == _state.bookmarksRestrict) return;
    _view.select(_state.copyWith(bookmarksRestrict: restrict));
    _bookmarks.useLoader(_bookmarksLoader(restrict));
    await _bookmarks.refresh();
  }

  Future<void> _signIn() async {
    _view.select(_state.copyWith(signingIn: true));
    try {
      final feed = _home.following;
      await runPixivSignIn(context);
      if (mounted) {
        _view.select(_state.copyWith());
        // Signing out emptied every list, so the section on screen loads too.
        _ensureTabLoaded(_state.section);
        await feed.refresh();
      }
    } finally {
      if (mounted) _view.select(_state.copyWith(signingIn: false));
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_view.restore(context, 'pixiv')) {
      _bookmarks.useLoader(_bookmarksLoader(_state.bookmarksRestrict));
      if (_state.followRestrict != 'all') _useFollowRestrict(_state.followRestrict);
    }
    final prefs = PrefService.of(context);
    final hasToken = (prefs.get<String>(optionPluginPixivRefreshToken) ?? '').trim().isNotEmpty;
    _scheduleRankingSync();

    return Scaffold(
      primary: !PluginEmbedded.maybeOf(context),
      body: ScopedBuilder<PluginViewStore<PixivViewState>, PixivViewState>(
        store: _view,
        onState: (context, state) => Column(
          children: [
            PixivHomeChrome(index: state.section, onSelect: _selectTab),
            const Divider(height: 1),
            Expanded(
              child: !hasToken && state.section != 4
                  ? PixivSignInBody(signingIn: state.signingIn, onSignIn: _signIn)
                  : PluginLazyTabs(onSelected: _selectTab, index: state.section, children: _sections(state)),
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
          store: _ranking,
        );
      },
    ),
    (_) => PixivFavoritesSection(
      restrict: state.bookmarksRestrict,
      onRestrict: _changeBookmarksRestrict,
      store: _bookmarks,
    ),
    (_) => const PixivSearchScreen(embedded: true),
    (_) => PixivMorePane(
      onAuthChanged: () {
        if (mounted) _view.select(_state.copyWith());
      },
    ),
  ];
}

/// Icon tabs matching Flare's Home / Rankings / Favorites / Search / More,
/// sitting under the Start strip instead of a nested AppBar that never painted.
class PixivHomeChrome extends StatelessWidget {
  final int index;
  final ValueChanged<int> onSelect;

  const PixivHomeChrome({super.key, required this.index, required this.onSelect});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return PluginHomeChrome(
      title: l10n.plugin_pixiv_title,
      mark: pluginMark(PixivPlugin(), size: 24),
      accent: const Color(0xFF0096FA),
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
}
