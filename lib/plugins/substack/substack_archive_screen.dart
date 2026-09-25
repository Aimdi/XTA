import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/ui/x_controls.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/plugins/substack/substack_post_card.dart';
import 'package:xta/plugins/substack/substack_store.dart';
import 'package:xta/plugins/substack/substack_group.dart';
import 'package:xta/plugins/substack/substack_similar_sheet.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/plugins/substack/substack_publication_store.dart';

SubstackPublication publicationForPost(SubstackPost post, {String? logoUrl}) {
  final pub = post.publication;
  return SubstackPublication(
    subdomain: pub.subdomain,
    baseUrl: pub.baseUrl,
    name: pub.displayName,
    description: pub.description,
    logoUrl: logoUrl ?? pub.logoUrl,
  );
}

void openSubstackPublication(BuildContext context, SubstackPublication publication) {
  Navigator.of(context).push(MaterialPageRoute(builder: (_) => SubstackArchiveScreen(publication: publication)));
}

class SubstackArchiveScreen extends StatefulWidget {
  final SubstackPublication publication;

  const SubstackArchiveScreen({super.key, required this.publication});

  @override
  State<SubstackArchiveScreen> createState() => _SubstackArchiveScreenState();
}

class _SubstackArchiveScreenState extends State<SubstackArchiveScreen> {
  late SubstackPublicationStore _store;
  final _searchController = TextEditingController();

  @override
  void initState() {
    super.initState();
    _createStore();
  }

  void _createStore() {
    _store = SubstackPublicationStore(context.read(), widget.publication);
    _store.refresh();
    _enrichHeader(_store);
  }

  Future<void> _enrichHeader(SubstackPublicationStore store) async {
    final publications = context.read<SubstackPublicationsStore>();
    final original = store.state.publication;
    final fresh = await store.enrich();
    if (!mounted || store != _store || fresh == null) return;
    if (!publications.state.any((pub) => pub.id == original.id || pub.id == fresh.id)) return;
    final pinned = publications.isPinned(original.id);
    await publications.add(fresh);
    if (original.id != fresh.id) {
      if (pinned && !publications.isPinned(fresh.id)) await publications.togglePinned(fresh.id);
      await publications.remove(original.id);
    }
  }

  @override
  void didUpdateWidget(SubstackArchiveScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.publication.baseUrl != widget.publication.baseUrl) {
      _store.destroy();
      _searchController.clear();
      _createStore();
    }
  }

  @override
  void dispose() {
    _searchController.dispose();
    _store.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => ScopedBuilder<SubstackPublicationStore, SubstackPublicationState>(
    store: _store,
    onState: (context, state) => Scaffold(
      appBar: AppBar(title: Text(state.publication.displayName)),
      body: ScopedBuilder<SubstackReadStore, Set<String>>(
        store: context.read<SubstackReadStore>(),
        onState: (context, read) => RefreshIndicator(
          onRefresh: _store.refresh,
          child: CustomScrollView(
            key: PageStorageKey('substack-publication-${widget.publication.baseUrl}'),
            physics: const AlwaysScrollableScrollPhysics(),
            slivers: [
              SliverToBoxAdapter(child: _hero(context, state)),
              SliverToBoxAdapter(child: _controls(context, state)),
              ..._posts(context, state, read),
            ],
          ),
        ),
      ),
    ),
  );

  Widget _controls(BuildContext context, SubstackPublicationState state) {
    final l10n = L10n.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          XSearchField(
            controller: _searchController,
            hintText: l10n.plugin_substack_search_publication,
            onChanged: _store.search,
          ),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            runSpacing: 4,
            children: [
              for (final filter in SubstackPublicationFilter.values)
                FilterChip(
                  selected: state.filter == filter,
                  label: Text(_filterLabel(l10n, filter)),
                  onSelected: (_) => _store.filter(filter),
                ),
            ],
          ),
          const SizedBox(height: 4),
          Align(
            alignment: AlignmentDirectional.centerStart,
            child: PopupMenuButton<SubstackPublicationOrder>(
              tooltip: l10n.plugin_mastodon_sort,
              initialValue: state.order,
              onSelected: _store.order,
              itemBuilder: (_) => [
                for (final order in SubstackPublicationOrder.values)
                  PopupMenuItem(value: order, child: Text(_orderLabel(l10n, order))),
              ],
              child: Padding(
                padding: const EdgeInsets.symmetric(vertical: 12),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Icon(Icons.sort, size: 20),
                    const SizedBox(width: 8),
                    Flexible(child: Text(_orderLabel(l10n, state.order))),
                    const Icon(Icons.arrow_drop_down),
                  ],
                ),
              ),
            ),
          ),
          Text(l10n.plugin_mastodon_loaded_controls, style: Theme.of(context).textTheme.bodySmall),
        ],
      ),
    );
  }

  List<Widget> _posts(BuildContext context, SubstackPublicationState state, Set<String> read) {
    final l10n = L10n.of(context);
    final page = state.page;
    final posts = visiblePublicationPosts(state, read);
    return [
      if (page.loading) const SliverToBoxAdapter(child: LinearProgressIndicator()),
      if (page.error != null && page.posts.isEmpty)
        SliverFillRemaining(
          hasScrollBody: false,
          child: FullPageErrorWidget(
            error: page.error,
            stackTrace: null,
            prefix: l10n.plugin_substack_load_error,
            onRetry: _store.retry,
          ),
        )
      else ...[
        if (posts.isEmpty && !page.loading)
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Column(
                children: [
                  Text(
                    state.filter == SubstackPublicationFilter.all
                        ? l10n.plugin_substack_feed_empty
                        : l10n.plugin_reader_empty_filter,
                    textAlign: TextAlign.center,
                  ),
                  if (state.filter != SubstackPublicationFilter.all)
                    TextButton(
                      onPressed: () => _store.filter(SubstackPublicationFilter.all),
                      child: Text(l10n.plugin_reader_reset_filters),
                    ),
                ],
              ),
            ),
          ),
        SliverList.builder(
          itemCount: posts.length,
          itemBuilder: (context, index) => SubstackPostCard(
            key: ValueKey(publicationPostKey(posts[index])),
            post: posts[index],
            showSourceBadge: false,
            logoUrl: state.publication.logoUrl,
          ),
        ),
        if (page.error != null)
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                children: [
                  Text(
                    page.failedMore ? l10n.plugin_mastodon_load_more_failed : l10n.plugin_mastodon_refresh_failed,
                    textAlign: TextAlign.center,
                  ),
                  TextButton.icon(onPressed: _store.retry, icon: const Icon(Icons.refresh), label: Text(l10n.retry)),
                ],
              ),
            ),
          )
        else if (page.canLoadMore)
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Center(
                child: OutlinedButton(
                  onPressed: page.loading ? null : _store.loadMore,
                  child: Text(l10n.plugin_substack_load_more),
                ),
              ),
            ),
          ),
      ],
      const SliverPadding(padding: EdgeInsets.only(bottom: 32)),
    ];
  }

  Widget _hero(BuildContext context, SubstackPublicationState state) {
    final theme = Theme.of(context);
    final l10n = L10n.of(context);
    final pub = state.publication;
    final description = pub.description?.trim();
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _identity(context, pub),
          if (description != null && description.isNotEmpty) ...[
            const SizedBox(height: 12),
            if (state.expanded || description.length <= 160)
              SelectableText(description, style: theme.textTheme.bodyMedium!.copyWith(height: 1.4))
            else
              Text(
                description,
                maxLines: 4,
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.bodyMedium!.copyWith(height: 1.4),
              ),
            if (description.length > 160)
              TextButton(
                onPressed: _store.toggleDescription,
                child: Text(state.expanded ? l10n.collapse_reposts : l10n.clickToShowMore),
              ),
          ],
          const SizedBox(height: 12),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              SubstackFollowButton(publication: pub),
              OutlinedButton.icon(
                onPressed: () => addSubstackPublicationToGroup(context, pub),
                icon: const Icon(Icons.group_add, size: 18),
                label: Text(l10n.add_to_group),
              ),
              OutlinedButton.icon(
                onPressed: () => showSubstackSimilarSheet(context, pub),
                icon: const Icon(Icons.person_search, size: 18),
                label: Text(l10n.plugin_substack_similar),
              ),
              _pin(context, pub),
            ],
          ),
        ],
      ),
    );
  }

  Widget _identity(BuildContext context, SubstackPublication pub) {
    final theme = Theme.of(context);
    final logo = ClipRRect(
      borderRadius: BorderRadius.circular(12),
      child: pub.logoUrl?.isNotEmpty != true
          ? Container(
              width: 64,
              height: 64,
              color: theme.colorScheme.surfaceContainerHighest,
              child: Icon(Icons.newspaper, size: 32, color: theme.colorScheme.onSurfaceVariant),
            )
          : ExtendedImage.network(
              pub.logoUrl!,
              width: 64,
              height: 64,
              fit: BoxFit.cover,
              cacheWidth: (64 * MediaQuery.devicePixelRatioOf(context)).ceil(),
            ),
    );
    final title = Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(pub.displayName, style: theme.textTheme.headlineSmall!.copyWith(fontWeight: FontWeight.w800)),
        const SizedBox(height: 4),
        SelectableText(
          Uri.tryParse(pub.baseUrl)?.host ?? pub.baseUrl,
          style: theme.textTheme.bodySmall!.copyWith(color: theme.colorScheme.onSurfaceVariant),
        ),
      ],
    );
    return LayoutBuilder(
      builder: (context, constraints) {
        if (constraints.maxWidth < 360 || MediaQuery.textScalerOf(context).scale(1) > 1.3) {
          return Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [logo, const SizedBox(height: 12), title],
          );
        }
        return Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            logo,
            const SizedBox(width: 12),
            Expanded(child: title),
          ],
        );
      },
    );
  }

  Widget _pin(BuildContext context, SubstackPublication publication) {
    final store = context.read<SubstackPublicationsStore>();
    final l10n = L10n.of(context);
    return ScopedBuilder<SubstackPublicationsStore, List<SubstackPublication>>(
      store: store,
      onState: (_, pubs) {
        if (!pubs.any((pub) => pub.id == publication.id)) return const SizedBox.shrink();
        final pinned = store.isPinned(publication.id);
        return OutlinedButton.icon(
          onPressed: () => store.togglePinned(publication.id),
          icon: Icon(pinned ? Icons.push_pin : Icons.push_pin_outlined, size: 18),
          label: Text(pinned ? l10n.unpin : l10n.pin),
        );
      },
    );
  }
}

String _filterLabel(L10n l10n, SubstackPublicationFilter value) => switch (value) {
  SubstackPublicationFilter.all => l10n.plugin_substack_filter_all,
  SubstackPublicationFilter.unread => l10n.plugin_substack_filter_unread,
  SubstackPublicationFilter.free => l10n.plugin_substack_filter_free,
  SubstackPublicationFilter.podcasts => l10n.plugin_substack_filter_podcast,
  SubstackPublicationFilter.videos => l10n.videos,
};

String _orderLabel(L10n l10n, SubstackPublicationOrder value) => switch (value) {
  SubstackPublicationOrder.newest => l10n.plugin_mastodon_order_newest,
  SubstackPublicationOrder.oldest => l10n.plugin_mastodon_order_oldest,
  SubstackPublicationOrder.popular => l10n.popular,
};

/// Follows or unfollows the publication, reflecting whichever it currently is.
class SubstackFollowButton extends StatelessWidget {
  final SubstackPublication publication;

  const SubstackFollowButton({super.key, required this.publication});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final store = context.read<SubstackPublicationsStore>();

    return ScopedBuilder<SubstackPublicationsStore, List<SubstackPublication>>(
      store: store,
      onState: (context, followed) {
        final isFollowed = followed.any((e) => e.id == publication.id);

        return FilledButton.tonalIcon(
          icon: Icon(isFollowed ? Icons.check : Icons.add, size: 18),
          label: Text(isFollowed ? l10n.plugin_substack_unfollow : l10n.plugin_substack_follow),
          onPressed: () async {
            final subscriptions = context.read<SubscriptionsModel>();
            isFollowed ? await store.remove(publication.id) : await store.add(publication);
            await subscriptions.reloadSubscriptions();
          },
        );
      },
    );
  }
}
