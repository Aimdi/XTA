import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_discovery_api.dart';
import 'package:xta/plugins/pixiv/pixiv_discovery_models.dart';
import 'package:xta/plugins/pixiv/pixiv_grid.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_recommended_users_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:xta/plugins/pixiv/pixiv_user_list_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_view_state.dart';
import 'package:xta/plugins/pixiv/pixiv_watchlist.dart';
import 'package:xta/plugins/pixiv/pixivision_list_screen.dart';
import 'package:xta/plugins/plugin_filter_row.dart';
import 'package:xta/plugins/plugin_session.dart';

/// Recommended works whose every refresh, pull-to-refresh included, also
/// refreshes the parts shown above them: Pixivision articles and creators.
class PixivRecommendedStore extends PixivIllustListStore {
  final List<PixivPagedListStore<Object>> companions;

  PixivRecommendedStore(super.loader, {super.filter, required this.companions});

  @override
  Future<void> refresh() async {
    await Future.wait([super.refresh(), for (final store in companions) store.refresh()]);
  }
}

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
  }) {
    final users = session.obtain('recommendedUsers', () => pixivRecommendedUsersStore(api, mute));
    final articles = session.obtain('spotlight', () => pixivSpotlightStore(api));
    return PixivHomeStores(
      following: following,
      recommended: session.obtain(
        'recommended',
        () => PixivRecommendedStore(
          ({nextUrl}) => client.recommended(nextUrl: nextUrl),
          filter: mute.filter,
          companions: [articles, users],
        ),
      ),
      manga: session.obtain(
        'manga',
        () => PixivIllustListStore(({nextUrl}) => api.mangaRecommended(nextUrl: nextUrl), filter: mute.filter),
      ),
      watchlist: session.obtain('watchlist', () => pixivMangaWatchlistStore(api)),
      users: users,
      articles: articles,
    );
  }

  /// Every list, for emptying them all when the account changes.
  List<PixivPagedListStore<Object>> get all => [following, recommended, manga, watchlist, users, articles];

  /// The lists [source] shows. Recommended's own refresh also loads the
  /// articles and creators above its works.
  List<PixivPagedListStore<Object>> storesFor(PixivHomeSource source) => switch (source) {
    PixivHomeSource.following => [following],
    PixivHomeSource.recommended => [recommended],
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

  /// Each other source's own, so tapping its chip again scrolls it to the top.
  final ScrollController? recommendedScrollController;
  final ScrollController? mangaScrollController;
  final ScrollController? watchlistScrollController;

  const PixivHomeSection({
    super.key,
    required this.source,
    required this.onSource,
    required this.followRestrict,
    required this.onFollowRestrict,
    required this.stores,
    required this.scrollController,
    this.recommendedScrollController,
    this.mangaScrollController,
    this.watchlistScrollController,
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
              key: const ValueKey('pixiv-home-following-list'),
              tooltip: l10n.following,
              icon: const Icon(Icons.people_outline),
              onPressed: () => openPixivUserList(context, PixivUserListKind.following),
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

  /// Tapping the chosen chip again scrolls its list to the top.
  Widget _chip(String label, PixivHomeSource value) => ChoiceChip(
    key: ValueKey('pixiv-home-${value.name}'),
    label: Text(label),
    selected: source == value,
    onSelected: (_) => onSource(value),
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
      scrollController: recommendedScrollController,
      leadingSlivers: [
        SliverToBoxAdapter(
          child: PixivRecommendedHeader(articles: stores.articles, users: stores.users),
        ),
      ],
    ),
    PixivHomeSource.manga => PixivIllustFeed(
      store: stores.manga,
      emptyMessage: l10n.plugin_pixiv_manga_empty,
      scrollController: mangaScrollController,
    ),
    PixivHomeSource.watchlist => PixivMangaWatchlistFeed(
      store: stores.watchlist,
      scrollController: watchlistScrollController,
    ),
  };
}

/// Whose works the Following feed shows — all, public or private follows — as
/// one icon button with a menu, so the source chips beside it keep their room.
class PixivFollowRestrictControl extends StatelessWidget {
  final String restrict;
  final ValueChanged<String> onChanged;

  const PixivFollowRestrictControl({super.key, required this.restrict, required this.onChanged});

  static IconData _icon(String restrict) => switch (restrict) {
    'public' => Icons.public,
    'private' => Icons.lock_outline,
    _ => Icons.all_inclusive,
  };

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final labels = {
      'all': l10n.plugin_pixiv_follow_restrict_all,
      'public': l10n.plugin_pixiv_follow_restrict_public,
      'private': l10n.plugin_pixiv_follow_restrict_private,
    };
    return PopupMenuButton<String>(
      key: const ValueKey('pixiv-follow-restrict'),
      tooltip: labels[restrict],
      icon: Icon(_icon(restrict)),
      iconColor: restrict == 'all' ? null : Theme.of(context).colorScheme.primary,
      initialValue: restrict,
      onSelected: onChanged,
      itemBuilder: (_) => [
        for (final MapEntry(key: value, value: label) in labels.entries)
          PopupMenuItem(
            key: ValueKey('pixiv-follow-restrict-$value'),
            value: value,
            child: ListTile(
              contentPadding: EdgeInsets.zero,
              leading: Icon(_icon(value)),
              title: Text(label),
              selected: value == restrict,
              trailing: value == restrict ? const Icon(Icons.check) : null,
            ),
          ),
      ],
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
    // Sideways scrolls stay in the header: the works feed below asks for its
    // next page on any scroll it hears, whatever its axis.
    return NotificationListener<ScrollNotification>(
      onNotification: (notification) => notification.metrics.axis == Axis.horizontal,
      child: Column(
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
      ),
    );
  }

  Widget _users(BuildContext context, List<PixivUserPreview> previews) => previews.isEmpty
      ? const SizedBox.shrink()
      : _part(
          context,
          title: L10n.of(context).plugin_pixiv_recommended_users,
          onSeeAll: () => openPixivRecommendedUsers(context),
          child: PixivRecommendedUsersStrip(users: [for (final preview in previews) preview.user]),
        );

  Widget _part(BuildContext context, {required String title, required VoidCallback onSeeAll, required Widget child}) =>
      Padding(
        padding: const EdgeInsets.only(bottom: 8),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 4, 4, 0),
              // See all may shrink and ellipsize, so large text never pushes it off the row.
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Expanded(flex: 3, child: Text(title, style: Theme.of(context).textTheme.titleSmall)),
                  Flexible(
                    flex: 2,
                    child: TextButton(
                      onPressed: onSeeAll,
                      child: Text(L10n.of(context).plugin_pixiv_see_all, maxLines: 1, overflow: TextOverflow.ellipsis),
                    ),
                  ),
                ],
              ),
            ),
            child,
          ],
        ),
      );
}
