import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_discovery_api.dart';
import 'package:xta/plugins/pixiv/pixiv_discovery_models.dart';
import 'package:xta/plugins/pixiv/pixiv_following_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_grid.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_recommended_users_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:xta/plugins/pixiv/pixiv_view_state.dart';
import 'package:xta/plugins/pixiv/pixiv_watchlist.dart';
import 'package:xta/plugins/pixiv/pixivision_list_screen.dart';
import 'package:xta/plugins/plugin_filter_row.dart';
import 'package:xta/plugins/plugin_session.dart';

/// The lists Home shows, kept for the Home session. Following is the app-wide feed.
class PixivHomeStores {
  final PixivIllustListStore following;
  final PixivIllustListStore recommended;
  final PixivIllustListStore manga;
  final PixivWatchlistStore watchlist;
  final PixivRecommendedUsersStore users;
  final PixivSpotlightStore articles;

  const PixivHomeStores({
    required this.following,
    required this.recommended,
    required this.manga,
    required this.watchlist,
    required this.users,
    required this.articles,
  });

  factory PixivHomeStores.obtain(
    PluginSessionLease session, {
    required PixivIllustListStore following,
    required PixivClient client,
    required PixivDiscoveryApi api,
    required PixivMuteStore mute,
  }) => PixivHomeStores(
    following: following,
    recommended: session.obtain(
      'recommended',
      () => PixivIllustListStore(({nextUrl}) => client.recommended(nextUrl: nextUrl), filter: mute.filter),
    ),
    manga: session.obtain(
      'manga',
      () => PixivIllustListStore(({nextUrl}) => api.mangaRecommended(nextUrl: nextUrl), filter: mute.filter),
    ),
    watchlist: session.obtain('watchlist', () => pixivMangaWatchlistStore(api)),
    users: session.obtain('recommendedUsers', () => pixivRecommendedUsersStore(api, mute)),
    articles: session.obtain('spotlight', () => pixivSpotlightStore(api)),
  );

  /// The lists [source] shows: Recommended also heads its works with
  /// Pixivision articles and suggested creators.
  List<PixivPagedListStore<Object>> storesFor(PixivHomeSource source) => switch (source) {
    PixivHomeSource.following => [following],
    PixivHomeSource.recommended => [recommended, articles, users],
    PixivHomeSource.manga => [manga],
    PixivHomeSource.watchlist => [watchlist],
  };
}

/// Home: the source chips over the chosen feed.
class PixivHomeSection extends StatelessWidget {
  final PixivHomeSource source;
  final ValueChanged<PixivHomeSource> onSource;

  /// `all`, `public` or `private`, for the Following feed.
  final String followRestrict;
  final ValueChanged<String> onFollowRestrict;
  final PixivHomeStores stores;

  /// The Home tab's own controller, so tapping Home scrolls Following to the top.
  final ScrollController scrollController;

  const PixivHomeSection({
    super.key,
    required this.source,
    required this.onSource,
    required this.followRestrict,
    required this.onFollowRestrict,
    required this.stores,
    required this.scrollController,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    // Scrollables under one PageStorageKey share a saved offset: without keys
    // of their own, a new feed opened at the chip row's sideways offset.
    return Column(
      children: [
        PluginFilterRow(
          key: const PageStorageKey<String>('pixiv-home-sources'),
          children: [
            _chip(l10n.plugin_pixiv_tab_following, PixivHomeSource.following),
            if (source == PixivHomeSource.following)
              PixivFollowRestrictControl(restrict: followRestrict, onChanged: onFollowRestrict),
            IconButton(
              tooltip: l10n.following,
              icon: const Icon(Icons.people_outline),
              onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const PixivFollowingScreen())),
            ),
            _chip(l10n.plugin_pixiv_tab_recommended, PixivHomeSource.recommended),
            _chip(l10n.plugin_pixiv_tab_manga, PixivHomeSource.manga),
            _chip(l10n.plugin_pixiv_tab_watchlist, PixivHomeSource.watchlist),
          ],
        ),
        // Keyed by source, each list also subscribes to its own store: a
        // ScopedBuilder keeps the store it was first built with.
        Expanded(
          child: KeyedSubtree(key: PageStorageKey<PixivHomeSource>(source), child: _feed(l10n)),
        ),
      ],
    );
  }

  Widget _chip(String label, PixivHomeSource value) => ChoiceChip(
    key: ValueKey('pixiv-home-${value.name}'),
    label: Text(label),
    selected: source == value,
    onSelected: (_) {
      if (source != value) onSource(value);
    },
  );

  Widget _feed(L10n l10n) => switch (source) {
    PixivHomeSource.following => PixivIllustFeed(
      store: stores.following,
      emptyMessage: l10n.plugin_pixiv_empty,
      scrollController: scrollController,
    ),
    PixivHomeSource.recommended => PixivIllustFeed(
      store: stores.recommended,
      emptyMessage: l10n.plugin_pixiv_recommended_empty,
      leadingSlivers: [
        SliverToBoxAdapter(
          child: PixivRecommendedHeader(articles: stores.articles, users: stores.users),
        ),
      ],
    ),
    PixivHomeSource.manga => PixivIllustFeed(store: stores.manga, emptyMessage: l10n.plugin_pixiv_manga_empty),
    PixivHomeSource.watchlist => PixivMangaWatchlistFeed(store: stores.watchlist),
  };
}

/// All / public / private follows for the Following feed, as icons with tooltips.
class PixivFollowRestrictControl extends StatelessWidget {
  final String restrict;
  final ValueChanged<String> onChanged;

  const PixivFollowRestrictControl({super.key, required this.restrict, required this.onChanged});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return SegmentedButton<String>(
      key: const ValueKey('pixiv-follow-restrict'),
      showSelectedIcon: false,
      segments: [
        ButtonSegment(
          value: 'all',
          icon: const Icon(Icons.all_inclusive),
          tooltip: l10n.plugin_pixiv_follow_restrict_all,
        ),
        ButtonSegment(
          value: 'public',
          icon: const Icon(Icons.public),
          tooltip: l10n.plugin_pixiv_follow_restrict_public,
        ),
        ButtonSegment(
          value: 'private',
          icon: const Icon(Icons.lock_outline),
          tooltip: l10n.plugin_pixiv_follow_restrict_private,
        ),
      ],
      selected: {restrict},
      onSelectionChanged: (selected) => onChanged(selected.first),
    );
  }
}

/// Above the Recommended works: Pixivision articles, then suggested creators,
/// each with a way to the full list. A part with nothing to show is left out.
class PixivRecommendedHeader extends StatelessWidget {
  final PixivSpotlightStore articles;
  final PixivRecommendedUsersStore users;

  const PixivRecommendedHeader({super.key, required this.articles, required this.users});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        ScopedBuilder<PixivSpotlightStore, List<PixivSpotlightArticle>>(
          store: articles,
          onState: (context, items) => items.isEmpty
              ? const SizedBox.shrink()
              : _part(
                  context,
                  title: l10n.plugin_pixiv_pixivision_articles,
                  onSeeAll: () => openPixivisionList(context),
                  child: PixivisionCarousel(articles: items),
                ),
        ),
        ScopedBuilder<PixivMuteStore, PixivMuteState>(
          store: context.read<PixivMuteStore>(),
          onState: (context, mute) => ScopedBuilder<PixivRecommendedUsersStore, List<PixivUserPreview>>(
            store: users,
            onState: (context, previews) => _users(context, pixivUnmutedPreviews(previews, mute)),
          ),
        ),
      ],
    );
  }

  Widget _users(BuildContext context, List<PixivUserPreview> previews) => previews.isEmpty
      ? const SizedBox.shrink()
      : _part(
          context,
          title: L10n.of(context).plugin_pixiv_recommended_users,
          onSeeAll: () => openPixivRecommendedUsers(context),
          child: PixivRecommendedUsersStrip(previews: previews),
        );

  Widget _part(BuildContext context, {required String title, required VoidCallback onSeeAll, required Widget child}) =>
      Padding(
        padding: const EdgeInsets.only(bottom: 8),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 4, 4, 0),
              child: Row(
                children: [
                  Expanded(child: Text(title, style: Theme.of(context).textTheme.titleSmall)),
                  TextButton(onPressed: onSeeAll, child: Text(L10n.of(context).plugin_pixiv_see_all)),
                ],
              ),
            ),
            child,
          ],
        ),
      );
}
