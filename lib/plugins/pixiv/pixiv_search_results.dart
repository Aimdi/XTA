import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_grid.dart';
import 'package:xta/plugins/pixiv/pixiv_image.dart';
import 'package:xta/plugins/pixiv/pixiv_link_open.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_search_filter_sheet.dart';
import 'package:xta/plugins/pixiv/pixiv_search_filters.dart';
import 'package:xta/plugins/pixiv/pixiv_search_store.dart';
import 'package:xta/plugins/pixiv/pixiv_settings.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:xta/plugins/pixiv/pixiv_user_card.dart';
import 'package:xta/plugins/pixiv/pixiv_view_state.dart';
import 'package:xta/ui/empty_pane.dart';
import 'package:xta/ui/errors.dart';

/// The filter bar over a search's results, its Filters button opening the
/// sheet for the search's kind. A works search without Premium sorted by
/// popularity shows Pixiv's free preview, which takes no dates, so the date
/// menu goes.
class PixivSearchFilterHeader extends StatelessWidget {
  final PixivSearchStore store;
  final PixivSearchState state;

  const PixivSearchFilterHeader({super.key, required this.store, required this.state});

  Future<void> _openSheet(BuildContext context) async {
    final choice = await showPixivSearchFilterSheet(
      context,
      filter: state.filter,
      remembered: state.remembered,
      isPremium: state.isPremium,
      kind: store.kind,
    );
    if (choice != null) await store.applyFilter(choice.filter, remember: choice.remember);
  }

  @override
  Widget build(BuildContext context) => PixivSearchFilterBar(
    filter: state.filter,
    base: pixivFreshFilter(store.prefs),
    isPremium: state.isPremium,
    kind: store.kind,
    datesApply: !state.previewMode,
    onChanged: store.applyFilter,
    onOpenSheet: () => _openSheet(context),
  );
}

/// The works a search found, under the filter bar. A reader without Premium
/// who sorts by popularity gets Pixiv's free preview with a note saying so;
/// date-sorted results get the preview as a strip on top.
class PixivSearchWorks extends StatelessWidget {
  final PixivSearchStore store;
  final PixivSearchState state;

  const PixivSearchWorks({super.key, required this.store, required this.state});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Column(
      children: [
        PixivSearchFilterHeader(store: store, state: state),
        Expanded(
          child: PixivIllustFeed(
            store: store.results,
            emptyMessage: l10n.plugin_pixiv_search_empty,
            leadingSlivers: [
              if (state.previewMode) const SliverToBoxAdapter(child: PixivSearchPreviewNote()),
              if (state.showsPopularStrip) SliverToBoxAdapter(child: PixivPopularStrip(illusts: state.popular)),
            ],
          ),
        ),
      ],
    );
  }
}

/// Says the grid is Pixiv's free popular preview, and that posting dates do
/// not narrow it.
class PixivSearchPreviewNote extends StatelessWidget {
  const PixivSearchPreviewNote({super.key});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    final style = theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant);
    return Padding(
      padding: const EdgeInsets.fromLTRB(12, 4, 12, 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(Icons.workspace_premium_outlined, size: 18, color: theme.colorScheme.primary),
          const SizedBox(width: 8),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(l10n.plugin_pixiv_search_preview_note, style: style),
                Text(l10n.plugin_pixiv_search_preview_dates, style: style),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// One page of Pixiv's free popular preview as a row of thumbnails.
class PixivPopularStrip extends StatelessWidget {
  final List<PixivIllust> illusts;

  const PixivPopularStrip({super.key, required this.illusts});

  @override
  Widget build(BuildContext context) {
    final cacheWidth = (110 * MediaQuery.devicePixelRatioOf(context)).ceil();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(8, 4, 8, 4),
          child: Text(L10n.of(context).plugin_pixiv_popular_title, style: Theme.of(context).textTheme.titleSmall),
        ),
        SizedBox(
          height: 110,
          // Sideways scrolling here must not reach the grid's load-more listener.
          child: NotificationListener<ScrollNotification>(onNotification: (_) => true, child: _thumbs(cacheWidth)),
        ),
        const SizedBox(height: 8),
      ],
    );
  }

  Widget _thumbs(int cacheWidth) => ListView.separated(
    scrollDirection: Axis.horizontal,
    padding: const EdgeInsets.symmetric(horizontal: 8),
    itemCount: illusts.length,
    separatorBuilder: (_, _) => const SizedBox(width: 6),
    itemBuilder: (context, index) => Semantics(
      button: true,
      label: illusts[index].title,
      child: InkWell(
        onTap: () => openPixivIllustFromList(context, illusts, index),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(8),
          child: SizedBox(
            width: 110,
            child: PixivNetworkImage(url: illusts[index].thumbnailUrl, fit: BoxFit.cover, cacheWidth: cacheWidth),
          ),
        ),
      ),
    ),
  );
}

/// Creators a search found, each with recent works (or, for a novel search,
/// novels) and a follow button, loading the next page well before the end.
class PixivSearchUsers extends StatelessWidget {
  final PixivPagedListStore<PixivUserPreview> store;
  final PixivContentMode previews;

  const PixivSearchUsers({super.key, required this.store, this.previews = PixivContentMode.illust});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return ScopedBuilder<PixivPagedListStore<PixivUserPreview>, List<PixivUserPreview>>(
      store: store,
      onLoading: (context) =>
          store.state.isNotEmpty ? _list(context, store.state) : const Center(child: CircularProgressIndicator()),
      onError: (context, error) => store.state.isNotEmpty
          ? _list(context, store.state)
          : Padding(
              padding: const EdgeInsets.all(24),
              child: FullPageErrorWidget(
                error: error,
                stackTrace: null,
                prefix: pixivErrorMessage(l10n, error ?? Exception()),
                onRetry: store.refresh,
              ),
            ),
      onState: (context, users) => users.isEmpty
          ? EmptyPane(
              icon: Icons.person_search_outlined,
              message: l10n.plugin_pixiv_search_empty,
              onRefresh: store.refresh,
            )
          : _list(context, users),
    );
  }

  Widget _list(BuildContext context, List<PixivUserPreview> users) => NotificationListener<ScrollNotification>(
    onNotification: (notification) {
      if (notification.metrics.pixels > notification.metrics.maxScrollExtent - 600) store.loadMore();
      return false;
    },
    child: RefreshIndicator(
      onRefresh: store.refresh,
      child: ListView.separated(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.all(12),
        itemCount: users.length + (store.loadingMore ? 1 : 0),
        separatorBuilder: (_, _) => const SizedBox(height: 8),
        itemBuilder: (context, index) => index < users.length
            ? PixivUserPreviewCard(
                key: ValueKey('pixiv-search-user-${users[index].user.id}'),
                preview: users[index],
                previews: previews,
              )
            : const Padding(
                padding: EdgeInsets.all(16),
                child: Center(child: CircularProgressIndicator()),
              ),
      ),
    ),
  );
}
