import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_favorites_section.dart';
import 'package:xta/plugins/pixiv/pixiv_home_section.dart';
import 'package:xta/plugins/pixiv/pixiv_more_pane.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_plugin.dart';
import 'package:xta/plugins/pixiv/pixiv_ranking_section.dart';
import 'package:xta/plugins/pixiv/pixiv_search_screen.dart';
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

class _PixivScreenState extends State<PixivScreen> {
  late final PluginSessionLease _session;
  late final PluginViewStore<PixivViewState> _view;
  late final PixivIllustListStore _recommended;
  late final PixivIllustListStore _ranking;
  late final PixivIllustListStore _bookmarks;

  /// The account the lists were loaded for; another one empties them.
  late final PluginViewStore<int> _account;
  final _recommendedScroll = ScrollController();
  final _rankingScroll = ScrollController();
  final _favoritesScroll = ScrollController();
  final _moreScroll = ScrollController();
  PixivViewState get _state => _view.state;

  @override
  void initState() {
    super.initState();
    _session = PluginSessionLease(context, 'pixiv');
    _view = _session.obtain('view', () => PluginViewStore<PixivViewState>(PixivViewState(section: _startSection)));
    final view = _view;
    final client = context.read<PixivClient>();
    _account = _session.obtain('account', () => PluginViewStore<int>(client.storedUserId ?? 0));
    final mute = context.read<PixivMuteStore>();
    _recommended = _session.obtain(
      'recommended',
      () => PixivIllustListStore(({nextUrl}) => client.recommended(nextUrl: nextUrl), filter: mute.filter),
    );
    _ranking = _session.obtain(
      'ranking',
      () => PixivIllustListStore(
        ({nextUrl}) => client.ranking(
          mode: view.state.rankingMode,
          date: pixivRankingDateParam(view.state.rankingDate),
          nextUrl: nextUrl,
        ),
        filter: mute.filter,
      ),
    );
    _bookmarks = _session.obtain(
      'bookmarks',
      () => PixivIllustListStore(_bookmarksLoader(_state.bookmarksRestrict), filter: mute.filter),
    );
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      if (!mounted) return;
      await mute.load();
      if (!mounted || !_hasToken) return;
      // Warm the token once so the first feed call does not serialise behind
      // a cold refresh, and concurrent tab loads share one in-flight refresh.
      unawaited(context.read<PixivClient>().ensureAccessToken());
      _ensureTabLoaded(_state.section);
    });
  }

  int get _startSection =>
      pixivStartSectionIndex(PrefService.of(context, listen: false).get<String>(optionPluginPixivStartSection));

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
    for (final controller in [_recommendedScroll, _rankingScroll, _favoritesScroll, _moreScroll]) {
      controller.dispose();
    }
    _session.dispose();
    super.dispose();
  }

  /// The list the reader sees in [section] now; Search keeps its own lists
  /// and is reached through the route's primary controller.
  ScrollController? _scrollControllerFor(int section) => switch (section) {
    0 when _state.homeSource == PixivHomeSource.following => widget.scrollController,
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
    final client = context.read<PixivClient>();
    final now = client.storedUserId ?? 0;
    if (now != _account.state) {
      _account.select(now);
      _recommended.useLoader(({nextUrl}) => client.recommended(nextUrl: nextUrl));
      _bookmarks.useLoader(_bookmarksLoader(_state.bookmarksRestrict));
      _ranking.useLoader(_rankingLoader());
    }
    _ensureTabLoaded(_state.section);
    _view.select(_state.copyWith());
  }

  void _ensureTabLoaded(int index) {
    if (!_hasToken) return;
    final store = switch (index) {
      0 when _state.homeSource == PixivHomeSource.following => context.read<PixivFeedStore>(),
      0 => _recommended,
      1 => _ranking,
      2 => _bookmarks,
      _ => null,
    };
    if (store != null && store.state.isEmpty) {
      store.refresh();
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

  void _selectHomeSource(PixivHomeSource source) {
    if (source == _state.homeSource) {
      _scrollToTop();
      return;
    }
    _view.select(_state.copyWith(homeSource: source));
    _ensureTabLoaded(0);
  }

  PixivIllustPageLoader _rankingLoader() {
    final client = context.read<PixivClient>();
    final mode = _state.rankingMode;
    final date = pixivRankingDateParam(_state.rankingDate);
    return ({nextUrl}) => client.ranking(mode: mode, date: date, nextUrl: nextUrl);
  }

  Future<void> _reloadRanking() async {
    _ranking.useLoader(_rankingLoader());
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

  Future<void> _changeRankingDate(DateTime? date) async {
    if (!mounted || (date == null && _state.rankingDate == null)) return;
    _view.select(date == null ? _state.copyWith(clearRankingDate: true) : _state.copyWith(rankingDate: date));
    await _reloadRanking();
  }

  Future<void> _changeBookmarksRestrict(String restrict) async {
    if (restrict == _state.bookmarksRestrict) {
      await _scrollToTop();
      return;
    }
    _view.select(_state.copyWith(bookmarksRestrict: restrict));
    _bookmarks.useLoader(_bookmarksLoader(restrict));
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
      _bookmarks.useLoader(_bookmarksLoader(_state.bookmarksRestrict));
    }
    final prefs = PrefService.of(context);
    final hasToken = (prefs.get<String>(optionPluginPixivRefreshToken) ?? '').trim().isNotEmpty;

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
    (context) => PixivHomeSection(
      source: state.homeSource,
      onSource: _selectHomeSource,
      following: context.read<PixivFeedStore>(),
      recommended: _recommended,
      scrollController: widget.scrollController,
      recommendedScrollController: _recommendedScroll,
    ),
    (_) => PixivRankingSection(
      mode: state.rankingMode,
      date: state.rankingDate,
      onMode: _changeRankingMode,
      onDate: _changeRankingDate,
      store: _ranking,
      scrollController: _rankingScroll,
    ),
    (_) => PixivFavoritesSection(
      restrict: state.bookmarksRestrict,
      onRestrict: _changeBookmarksRestrict,
      store: _bookmarks,
      scrollController: _favoritesScroll,
    ),
    (_) => const PixivSearchScreen(embedded: true),
    (_) => PixivMorePane(onAuthChanged: _onAuthChanged, scrollController: _moreScroll),
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
