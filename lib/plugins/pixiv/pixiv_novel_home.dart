import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_list.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_session.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_watchlist.dart';
import 'package:xta/plugins/pixiv/pixiv_ranking_modes.dart';
import 'package:xta/plugins/pixiv/pixiv_ranking_section.dart';
import 'package:xta/plugins/pixiv/pixiv_segmented_switch.dart';
import 'package:xta/plugins/pixiv/pixiv_view_state.dart';
import 'package:xta/plugins/plugin_filter_row.dart';

/// Public or Private, for the reader's bookmarks or [follows]: tapping the one
/// shown again calls [onReselect] instead.
class PixivRestrictSwitch extends StatelessWidget {
  final String restrict;
  final ValueChanged<String> onChanged;
  final VoidCallback onReselect;
  final bool follows;

  const PixivRestrictSwitch({
    super.key,
    required this.restrict,
    required this.onChanged,
    required this.onReselect,
    this.follows = false,
  });

  String _label(L10n l10n, String value) => switch ((value == 'private', follows)) {
    (true, true) => l10n.plugin_pixiv_follow_private,
    (false, true) => l10n.plugin_pixiv_follow_public,
    (true, false) => l10n.plugin_pixiv_bookmarks_private,
    (false, false) => l10n.plugin_pixiv_bookmarks_public,
  };

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return PixivSegmentedSwitch<String>(
      values: const ['public', 'private'],
      label: (value) => _label(l10n, value),
      selected: restrict,
      onSelected: (value) => value == restrict ? onReselect() : onChanged(value),
    );
  }
}

/// Home in Novel mode: Recommended, Following (public or private follows)
/// and Watchlist.
class PixivNovelHomeSection extends StatelessWidget {
  final PixivNovelSession session;
  final PixivNovelView view;

  /// Tapping the source or restriction already shown brings its list back to the top.
  final VoidCallback onReselect;
  final ScrollController? scrollController;

  const PixivNovelHomeSection({
    super.key,
    required this.session,
    required this.view,
    required this.onReselect,
    this.scrollController,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final following = view.homeSource == PixivNovelHomeSource.following;
    return Column(
      children: [
        PluginFilterRow(
          key: const PageStorageKey<String>('pixiv-novel-home-sources'),
          children: [
            _chip(l10n.plugin_pixiv_tab_recommended, PixivNovelHomeSource.recommended),
            _chip(l10n.plugin_pixiv_tab_following, PixivNovelHomeSource.following),
            _chip(l10n.plugin_pixiv_tab_watchlist, PixivNovelHomeSource.watchlist),
          ],
        ),
        if (following)
          PixivRestrictSwitch(
            key: const ValueKey('pixiv-novel-follow-restrict'),
            restrict: view.followRestrict,
            onChanged: session.changeFollowRestrict,
            onReselect: onReselect,
            follows: true,
          ),
        // Each source keeps its own scroll offset and subscribes to its own store.
        Expanded(
          child: KeyedSubtree(key: PageStorageKey<PixivNovelHomeSource>(view.homeSource), child: _feed(l10n)),
        ),
      ],
    );
  }

  Widget _chip(String label, PixivNovelHomeSource value) => ChoiceChip(
    key: ValueKey('pixiv-novel-home-${value.name}'),
    label: Text(label),
    selected: view.homeSource == value,
    onSelected: (_) => value == view.homeSource ? onReselect() : session.selectHomeSource(value),
  );

  Widget _feed(L10n l10n) => switch (view.homeSource) {
    PixivNovelHomeSource.recommended => PixivNovelFeed(
      store: session.recommended,
      emptyMessage: l10n.plugin_pixiv_novel_recommended_empty,
      scrollController: scrollController,
    ),
    PixivNovelHomeSource.following => PixivNovelFeed(
      store: session.following,
      emptyMessage: l10n.plugin_pixiv_novel_following_empty,
      scrollController: scrollController,
    ),
    PixivNovelHomeSource.watchlist => PixivNovelWatchlistFeed(
      store: session.watchlist,
      scrollController: scrollController,
    ),
  };
}

/// Rankings in Novel mode: the pinned novel boards and the archive date over the ranked novels.
class PixivNovelRankingSection extends StatelessWidget {
  final PixivNovelSession session;
  final PixivNovelView view;
  final VoidCallback onReselect;

  /// Runs whenever the pins change, so the screen can move off a board that lost its chip.
  final VoidCallback onPinsChanged;
  final ScrollController? scrollController;

  const PixivNovelRankingSection({
    super.key,
    required this.session,
    required this.view,
    required this.onReselect,
    required this.onPinsChanged,
    this.scrollController,
  });

  @override
  Widget build(BuildContext context) => ScopedBuilder<PixivRankingPinsStore, List<String>>(
    store: session.rankingPins,
    onState: (context, _) {
      onPinsChanged();
      final shown = session.rankingMode;
      return PixivRankingSection(
        modes: session.rankingModes,
        mode: shown,
        date: view.rankingDate,
        onMode: (mode) => mode == shown ? onReselect() : session.changeRankingMode(mode),
        onEditModes: () => session.editRankingModes(context),
        onDate: session.changeRankingDate,
        feed: PixivNovelFeed(
          store: session.ranking,
          emptyMessage: L10n.of(context).plugin_pixiv_novel_ranking_empty,
          scrollController: scrollController,
        ),
      );
    },
  );
}

/// Favorites in Novel mode: the reader's own novel bookmarks, public or private.
class PixivNovelFavoritesSection extends StatelessWidget {
  final PixivNovelSession session;
  final PixivNovelView view;
  final VoidCallback onReselect;
  final ScrollController? scrollController;

  const PixivNovelFavoritesSection({
    super.key,
    required this.session,
    required this.view,
    required this.onReselect,
    this.scrollController,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final private = view.bookmarksRestrict == 'private';
    return Column(
      children: [
        PixivRestrictSwitch(
          key: const ValueKey('pixiv-novel-bookmarks-restrict'),
          restrict: view.bookmarksRestrict,
          onChanged: session.changeBookmarksRestrict,
          onReselect: onReselect,
        ),
        Expanded(
          child: PixivNovelFeed(
            store: session.bookmarks,
            emptyMessage: private
                ? l10n.plugin_pixiv_novel_bookmarks_private_empty
                : l10n.plugin_pixiv_novel_bookmarks_empty,
            scrollController: scrollController,
          ),
        ),
      ],
    );
  }
}
