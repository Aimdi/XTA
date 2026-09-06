import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_feed_insets.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/plugins/plugin_marks.dart';
import 'package:xta/plugins/pixiv/pixiv_plugin.dart';
import 'package:xta/plugins/plugin_lazy_tabs.dart';
import 'package:xta/plugins/plugin_view_store.dart';
import 'package:xta/plugins/plugin_filter_row.dart';
import 'package:xta/plugins/pixiv/pixiv_view_state.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_grid.dart';
import 'package:xta/plugins/pixiv/pixiv_image.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_search_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_settings.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:xta/ui/empty_pane.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/plugins/plugin_feed_skeleton.dart';

/// Flare-style Pixiv home: Home / Rankings / Favorites / Search / More.
class PixivScreen extends StatefulWidget {
  final ScrollController scrollController;

  const PixivScreen({super.key, required this.scrollController});

  @override
  State<PixivScreen> createState() => _PixivScreenState();
}

class _PixivScreenState extends State<PixivScreen> {
  final _view = PluginViewStore<PixivViewState>(const PixivViewState(),
    snapshot: (state) => state.copyWith(signingIn: false));
  late final PixivIllustListStore _recommended;
  late final PixivIllustListStore _ranking;
  late final PixivIllustListStore _bookmarks;
  bool get _signingIn => _view.state.signingIn;
  String get _rankingMode => _view.state.rankingMode;
  DateTime? get _rankingDate => _view.state.rankingDate;
  String get _bookmarksRestrict => _view.state.bookmarksRestrict;
  int get _homeSource => _view.state.homeSource;

  /// Ranking modes Flare pins as first-class feeds, plus XTA's existing set.
  static const _rankingModes = [
    'day',
    'week',
    'month',
    'day_male',
    'day_female',
    'week_rookie',
    'week_original',
    'day_manga',
  ];

  @override
  void initState() {
    super.initState();
    final mute = context.read<PixivMuteStore>();
    _recommended = PixivIllustListStore(
      ({nextUrl}) => context.read<PixivClient>().recommended(nextUrl: nextUrl),
      filter: mute.filter,
    );
    _ranking = PixivIllustListStore(
      ({nextUrl}) => context.read<PixivClient>().ranking(
        mode: _rankingMode,
        date: _rankingDateParam,
        nextUrl: nextUrl,
      ),
      filter: mute.filter,
    );
    _bookmarks = PixivIllustListStore(
      _bookmarksLoader(_bookmarksRestrict),
      filter: mute.filter,
    );
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      if (!mounted) return;
      await mute.load();
      if (!mounted) return;
      final prefs = PrefService.of(context, listen: false);
      final hasToken = (prefs.get<String>(optionPluginPixivRefreshToken) ?? '')
          .trim()
          .isNotEmpty;
      if (hasToken) {
        // Warm the token once so the first feed call does not serialise behind
        // a cold refresh, and concurrent tab loads share one in-flight refresh.
        unawaited(context.read<PixivClient>().ensureAccessToken());
        _ensureTabLoaded(_view.state.section);
      }
    });
  }

  PixivIllustPageLoader _bookmarksLoader(String restrict) {
    return ({nextUrl}) async {
      final client = context.read<PixivClient>();
      // Prefer the stored id — verify() always hits the token endpoint and made
      // the Bookmarks tab feel like it loaded forever on every open.
      final userId = await client.ensureUserId();
      return client.bookmarks(
        userId: userId,
        restrict: restrict,
        nextUrl: nextUrl,
      );
    };
  }

  @override
  void dispose() {
    _view.destroy();
    _recommended.destroy();
    _ranking.destroy();
    _bookmarks.destroy();
    super.dispose();
  }

  void _ensureTabLoaded(int index) {
    final prefs = PrefService.of(context, listen: false);
    if ((prefs.get<String>(optionPluginPixivRefreshToken) ?? '')
        .trim()
        .isEmpty) {
      return;
    }
    switch (index) {
      case 0:
        if (_homeSource == 0) {
          if (context.read<PixivFeedStore>().state.isEmpty) {
            context.read<PixivFeedStore>().refresh();
          }
        } else if (_recommended.state.isEmpty) {
          _recommended.refresh();
        }
      case 1:
        if (_ranking.state.isEmpty) {
          _ranking.refresh();
        }
      case 2:
        if (_bookmarks.state.isEmpty) {
          _bookmarks.refresh();
        }
    }
  }

  /// `YYYY-MM-DD` for the archive request, or null for today's board.
  String? get _rankingDateParam {
    final date = _rankingDate;
    if (date == null) return null;
    String pad(int v) => '$v'.padLeft(2, '0');
    return '${date.year}-${pad(date.month)}-${pad(date.day)}';
  }

  /// Shaft-style archive picker: any past day's board, one call away.
  Future<void> _pickRankingDate() async {
    final now = DateTime.now();
    final picked = await showDatePicker(
      context: context,
      initialDate: _rankingDate ?? now.subtract(const Duration(days: 1)),
      // Rankings began in 2007; boards settle a day behind the calendar.
      firstDate: DateTime(2007, 9, 13),
      lastDate: now,
    );
    if (picked == null || !mounted) return;
    _view.select(_view.state.copyWith(rankingDate: picked));
    await _reloadRanking();
  }

  Future<void> _clearRankingDate() async {
    if (_rankingDate == null) return;
    _view.select(_view.state.copyWith(clearRankingDate: true));
    await _reloadRanking();
  }

  Future<void> _reloadRanking() async {
    _ranking.useLoader(
      ({nextUrl}) => context.read<PixivClient>().ranking(
        mode: _rankingMode,
        date: _rankingDateParam,
        nextUrl: nextUrl,
      ),
    );
    await _ranking.refresh();
  }

  Future<void> _changeRankingMode(String mode) async {
    if (mode == _rankingMode) return;
    _view.select(_view.state.copyWith(rankingMode: mode));
    await _reloadRanking();
  }

  Future<void> _changeBookmarksRestrict(String restrict) async {
    if (restrict == _bookmarksRestrict) return;
    _view.select(_view.state.copyWith(bookmarksRestrict: restrict));
    _bookmarks.useLoader(_bookmarksLoader(restrict));
    await _bookmarks.refresh();
  }

  void _selectTab(int index) {
    if (_view.state.section == index) return;
    _view.select(_view.state.copyWith(section: index));
    _ensureTabLoaded(index);
  }

  String _rankingLabel(L10n l10n, String mode) => switch (mode) {
    'week' => l10n.plugin_pixiv_ranking_week,
    'month' => l10n.plugin_pixiv_ranking_month,
    'day_male' => l10n.plugin_pixiv_ranking_day_male,
    'day_female' => l10n.plugin_pixiv_ranking_day_female,
    'week_rookie' => l10n.plugin_pixiv_ranking_rookie,
    'week_original' => l10n.plugin_pixiv_ranking_week_original,
    'day_manga' => l10n.plugin_pixiv_ranking_day_manga,
    _ => l10n.plugin_pixiv_ranking_day,
  };

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    if (_view.restore(context, 'pixiv')) {
      _bookmarks.useLoader(_bookmarksLoader(_bookmarksRestrict));
    }
    final prefs = PrefService.of(context);
    final hasToken = (prefs.get<String>(optionPluginPixivRefreshToken) ?? '')
        .trim()
        .isNotEmpty;

    return Scaffold(
      primary: !PluginEmbedded.maybeOf(context),
      body: ScopedBuilder<PluginViewStore<PixivViewState>, PixivViewState>(
        store: _view,
        onState: (context, _) => Column(
        children: [
          PixivHomeChrome(index: _view.state.section, onSelect: _selectTab),
          const Divider(height: 1),
          Expanded(
            child: !hasToken && _view.state.section != 4
                ? _signInBody(l10n)
                : PluginLazyTabs(
                    index: _view.state.section,
                    children: [
                      (_) => _homeTab(l10n),
                      (_) => _rankingTab(l10n),
                      (_) => _bookmarksTab(l10n),
                      (_) => const PixivSearchScreen(embedded: true),
                      (_) => PixivMorePane(
                        onAuthChanged: () {
                          if (mounted) _view.select(_view.state.copyWith());
                        },
                      ),
                    ],
                  ),
          ),
        ],
      ),
      ),
    );
  }

  Widget _homeTab(L10n l10n) {
    return Column(
      children: [
        PluginFilterRow(
            children: [
              ChoiceChip(
                label: Text(l10n.plugin_pixiv_tab_following),
                selected: _homeSource == 0,
                onSelected: (_) {
                  if (_homeSource == 0) return;
                  _view.select(_view.state.copyWith(homeSource: 0));
                  _ensureTabLoaded(0);
                },
              ),
              const SizedBox(width: 8),
              ChoiceChip(
                label: Text(l10n.plugin_pixiv_tab_recommended),
                selected: _homeSource == 1,
                onSelected: (_) {
                  if (_homeSource == 1) return;
                  _view.select(_view.state.copyWith(homeSource: 1));
                  _ensureTabLoaded(0);
                },
              ),
            ],
        ),
        Expanded(
          child: _homeSource == 0
              ? _feedTab(
                  store: context.read<PixivFeedStore>(),
                  empty: l10n.plugin_pixiv_empty,
                )
              : _feedTab(
                  store: _recommended,
                  empty: l10n.plugin_pixiv_recommended_empty,
                ),
        ),
      ],
    );
  }

  Widget _signInBody(L10n l10n) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(l10n.plugin_pixiv_not_configured, textAlign: TextAlign.center),
            const SizedBox(height: 20),
            FilledButton(
              onPressed: _signingIn
                  ? null
                  : () async {
                      _view.select(_view.state.copyWith(signingIn: true));
                      try {
                        final feed = context.read<PixivFeedStore>();
                        await runPixivSignIn(context);
                        if (mounted) {
                          _view.select(_view.state.copyWith());
                          await feed.refresh();
                        }
                      } finally {
                        if (mounted) _view.select(_view.state.copyWith(signingIn: false));
                      }
                    },
              child: _signingIn
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : Text(l10n.plugin_pixiv_sign_in),
            ),
          ],
        ),
      ),
    );
  }

  Widget _rankingTab(L10n l10n) {
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12),
          child: Row(children: [
            Expanded(child: PopupMenuButton<String>(
              initialValue: _rankingMode,
              onSelected: _changeRankingMode,
              itemBuilder: (_) => [
                for (final mode in _rankingModes)
                  CheckedPopupMenuItem(value: mode, checked: _rankingMode == mode,
                    child: Text(_rankingLabel(l10n, mode))),
              ],
              child: ConstrainedBox(
                constraints: const BoxConstraints(minHeight: 48),
                child: Row(children: [
                  const Icon(Icons.bar_chart, size: 20),
                  const SizedBox(width: 8),
                  Expanded(child: Text(_rankingLabel(l10n, _rankingMode),
                    maxLines: 1, overflow: TextOverflow.ellipsis)),
                  const Icon(Icons.expand_more),
                ]),
              ),
            )),
            const SizedBox(width: 8),
            if (_rankingDate != null)
              Flexible(child: Text(MaterialLocalizations.of(context)
                .formatCompactDate(_rankingDate!), maxLines: 1,
                overflow: TextOverflow.ellipsis)),
            IconButton(
              icon: const Icon(Icons.calendar_today),
              tooltip: l10n.plugin_pixiv_ranking_pick_date,
              onPressed: _pickRankingDate,
            ),
            if (_rankingDate != null)
              IconButton(
                icon: const Icon(Icons.close),
                tooltip: l10n.plugin_pixiv_ranking_back_to_today,
                onPressed: _clearRankingDate,
              ),
          ]),
        ),
        Expanded(
          child: _feedTab(
            store: _ranking,
            empty: l10n.plugin_pixiv_ranking_empty,
          ),
        ),
      ],
    );
  }

  Widget _bookmarksTab(L10n l10n) {
    return Column(
      children: [
        PluginFilterRow(
            children: [
              ChoiceChip(
                label: Text(l10n.plugin_pixiv_bookmarks_public),
                selected: _bookmarksRestrict == 'public',
                onSelected: (_) => _changeBookmarksRestrict('public'),
              ),
              const SizedBox(width: 8),
              ChoiceChip(
                label: Text(l10n.plugin_pixiv_bookmarks_private),
                selected: _bookmarksRestrict == 'private',
                onSelected: (_) => _changeBookmarksRestrict('private'),
              ),
            ],
        ),
        Expanded(
          child: _feedTab(
            store: _bookmarks,
            empty: _bookmarksRestrict == 'private'
                ? l10n.plugin_pixiv_bookmarks_private_empty
                : l10n.plugin_pixiv_bookmarks_empty,
          ),
        ),
      ],
    );
  }

  Widget _feedTab({
    required PixivIllustListStore store,
    required String empty,
  }) {
    final l10n = L10n.of(context);
    return ScopedBuilder<PixivIllustListStore, List<PixivIllust>>(
      store: store,
      onLoading: (context) {
        // Soft refresh keeps prior tiles; only the first load blanks the tab.
        if (store.state.isNotEmpty) {
          return _illustList(context, store, store.state);
        }
        return const PluginGridSkeleton(columns: 2);
      },
      onError: (context, error) {
        if (store.state.isNotEmpty) {
          return _illustList(context, store, store.state);
        }
        return Padding(
          padding: const EdgeInsets.all(24),
          child: FullPageErrorWidget(
            error: error,
            stackTrace: null,
            prefix: pixivErrorMessage(l10n, error ?? Exception()),
            onRetry: store.refresh,
          ),
        );
      },
      onState: (context, illusts) {
        if (illusts.isEmpty) {
          // Refreshable even when empty: re-selecting the tab does not reload,
          // so a transient empty page used to strand the reader with no
          // gesture that asks again.
          return EmptyPane(
            icon: Icons.photo_outlined,
            message: empty,
            onRefresh: store.refresh,
            action: FilledButton.icon(
              onPressed: store.refresh,
              icon: const Icon(Icons.refresh),
              label: Text(l10n.retry),
            ),
          );
        }
        return _illustList(context, store, illusts);
      },
    );
  }

  Widget _illustList(
    BuildContext context,
    PixivIllustListStore store,
    List<PixivIllust> illusts,
  ) {
    return _ThumbPrefetch(
      illusts: illusts,
      child: NotificationListener<ScrollNotification>(
        onNotification: (n) {
          // Prefetch the next API page well before the footer — Pixez-style.
          if (n.metrics.pixels > n.metrics.maxScrollExtent - 1400) {
            store.loadMore();
          }
          return false;
        },
        child: PixivIllustGrid(
          illusts: illusts,
          scrollController: store == context.read<PixivFeedStore>()
              ? widget.scrollController
              : null,
          padding: pluginFeedPadding(context, extra: const EdgeInsets.all(4)),
          onRefresh: store.refresh,
          loadingMore: store.loadingMore,
        ),
      ),
    );
  }
}

/// Prefetches thumbs only when the list grows — not on every mute/loading tick.
class _ThumbPrefetch extends StatefulWidget {
  final List<PixivIllust> illusts;
  final Widget child;

  const _ThumbPrefetch({required this.illusts, required this.child});

  @override
  State<_ThumbPrefetch> createState() => _ThumbPrefetchState();
}

class _ThumbPrefetchState extends State<_ThumbPrefetch> {
  var _lastCount = 0;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _maybePrefetch());
  }

  @override
  void didUpdateWidget(covariant _ThumbPrefetch oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.illusts.length != oldWidget.illusts.length) {
      WidgetsBinding.instance.addPostFrameCallback((_) => _maybePrefetch());
    }
  }

  void _maybePrefetch() {
    if (!mounted || widget.illusts.length <= _lastCount) {
      return;
    }
    final from = _lastCount;
    _lastCount = widget.illusts.length;
    unawaited(prefetchPixivThumbs(context, widget.illusts.skip(from)));
  }

  @override
  Widget build(BuildContext context) => widget.child;
}

/// Icon tabs matching Flare's Home / Rankings / Favorites / Search / More,
/// sitting under the Start strip instead of a nested AppBar that never painted.
class PixivHomeChrome extends StatelessWidget {
  final int index;
  final ValueChanged<int> onSelect;

  const PixivHomeChrome({
    super.key,
    required this.index,
    required this.onSelect,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return PluginHomeChrome(
      title: l10n.plugin_pixiv_title,
      mark: pluginMark(PixivPlugin(), size: 24),
      accent: const Color(0xFF0096FA),
      tabs: [
        PluginHomeTab(
          icon: Icons.home_outlined,
          label: l10n.home,
          selected: index == 0,
          onTap: () => onSelect(0),
        ),
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
        PluginHomeTab(
          icon: Icons.search,
          label: l10n.search,
          selected: index == 3,
          onTap: () => onSelect(3),
        ),
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
