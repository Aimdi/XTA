import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/ui/x_controls.dart';
import 'package:xta/plugins/substack/substack_client.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/plugins/substack/substack_post_card.dart';
import 'package:xta/plugins/substack/substack_store.dart';
import 'package:xta/plugins/substack/substack_group.dart';
import 'package:xta/plugins/substack/substack_similar_sheet.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/ui/feed_list.dart';

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

void openSubstackPublication(
  BuildContext context,
  SubstackPublication publication,
) {
  Navigator.of(context).push(
    MaterialPageRoute(
      builder: (_) => SubstackArchiveScreen(publication: publication),
    ),
  );
}

class SubstackArchiveScreen extends StatefulWidget {
  final SubstackPublication publication;

  const SubstackArchiveScreen({super.key, required this.publication});

  @override
  State<SubstackArchiveScreen> createState() => _SubstackArchiveScreenState();
}

class _SubstackArchiveScreenState extends State<SubstackArchiveScreen> {
  SubstackArchiveStore? _store;
  final _searchController = TextEditingController();
  var _query = '';
  List<SubstackPost>? _results;
  Object? _searchError;
  var _searching = false;
  SubstackPublication? _enriched;

  SubstackPublication get _publication => _enriched ?? widget.publication;

  Future<void> _search(String query) async {
    final trimmed = query.trim();
    setState(() {
      _query = trimmed;
      _searchError = null;
      _results = null;
      _searching = trimmed.isNotEmpty;
    });
    if (trimmed.isEmpty) {
      return;
    }
    try {
      final results = await context.read<SubstackClient>().searchPosts(
        _publication,
        trimmed,
      );
      if (mounted && _query == trimmed) setState(() => _results = results);
    } catch (e) {
      if (mounted && _query == trimmed) setState(() => _searchError = e);
    } finally {
      if (mounted && _query == trimmed) setState(() => _searching = false);
    }
  }

  Future<void> _enrichHeader() async {
    final current = widget.publication;
    final alreadyRich =
        !publicationNameLooksGeneric(current.name) &&
        current.name.toLowerCase() != current.subdomain.toLowerCase() &&
        (current.description?.trim().isNotEmpty ?? false) &&
        (current.logoUrl?.isNotEmpty ?? false);
    if (alreadyRich) return;
    try {
      final fresh = await context.read<SubstackClient>().fetchPublication(
        Uri.parse(current.baseUrl),
      );
      if (!mounted) return;
      final merged = SubstackPublication(
        subdomain: fresh.subdomain.isNotEmpty
            ? fresh.subdomain
            : current.subdomain,
        baseUrl: current.baseUrl,
        name: publicationNameLooksGeneric(fresh.name)
            ? current.name
            : fresh.name,
        description: fresh.description ?? current.description,
        logoUrl: fresh.logoUrl ?? current.logoUrl,
      );
      final pubs = context.read<SubstackPublicationsStore>();
      final followed = pubs.state.any(
        (e) => e.id == current.id || e.id == merged.id,
      );
      if (followed &&
          (current.name != merged.name ||
              current.logoUrl != merged.logoUrl ||
              current.subdomain != merged.subdomain)) {
        await pubs.add(merged);
        if (current.id != merged.id) {
          await pubs.remove(current.id);
        }
      }
      if (!mounted) return;
      setState(() => _enriched = merged);
    } catch (_) {
      // Keep the stub header; the archive list still loads from host fallbacks.
    }
  }

  Widget _searchField(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(12, 0, 12, 4),
      child: XSearchField(
        controller: _searchController,
        hintText: L10n.of(context).search,
        onChanged: _search,
      ),
    );
  }

  List<Widget> _searchSlivers(BuildContext context) {
    if (_searching) {
      return const [
        SliverFillRemaining(
          hasScrollBody: false,
          child: Center(child: CircularProgressIndicator()),
        ),
      ];
    }
    if (_searchError != null) {
      return [
        SliverFillRemaining(
          hasScrollBody: false,
          child: FullPageErrorWidget(
            error: _searchError,
            stackTrace: null,
            prefix: L10n.of(context).plugin_substack_load_error,
            onRetry: () => _search(_query),
          ),
        ),
      ];
    }
    final results = _results ?? const <SubstackPost>[];
    if (results.isEmpty) {
      return [
        SliverFillRemaining(
          hasScrollBody: false,
          child: Center(
            child: Text(L10n.of(context).plugin_substack_feed_empty),
          ),
        ),
      ];
    }
    return [
      SliverPadding(
        padding: const EdgeInsets.only(bottom: 24),
        sliver: SliverList.builder(
          itemCount: results.length,
          itemBuilder: (context, index) => SubstackPostCard(
            post: results[index],
            showSourceBadge: false,
            logoUrl: _publication.logoUrl,
          ),
        ),
      ),
    ];
  }

  List<Widget> _feedSlivers(
    BuildContext context,
    SubstackArchiveStore store, {
    required SubstackFeedSnapshot snapshot,
  }) {
    if (snapshot.posts.isEmpty) {
      return [
        SliverFillRemaining(
          hasScrollBody: false,
          child: Center(
            child: Text(L10n.of(context).plugin_substack_feed_empty),
          ),
        ),
      ];
    }
    return [
      SliverPadding(
        padding: const EdgeInsets.only(bottom: 24),
        sliver: SliverList.builder(
          itemCount: snapshot.posts.length + (snapshot.canLoadMore ? 1 : 0),
          itemBuilder: (context, index) {
            if (index >= snapshot.posts.length) {
              return Padding(
                padding: const EdgeInsets.all(16),
                child: Center(
                  child: OutlinedButton(
                    onPressed: store.loadMore,
                    child: Text(L10n.of(context).plugin_substack_load_more),
                  ),
                ),
              );
            }
            return SubstackPostCard(
              post: snapshot.posts[index],
              showSourceBadge: false,
              logoUrl: _publication.logoUrl,
            );
          },
        ),
      ),
    ];
  }

  Widget _profileScroll(
    BuildContext context, {
    required List<Widget> bodySlivers,
    Future<void> Function()? onRefresh,
  }) {
    final scroll = CustomScrollView(
      physics: const AlwaysScrollableScrollPhysics(),
      slivers: [
        SliverToBoxAdapter(child: _hero(context)),
        SliverToBoxAdapter(child: _searchField(context)),
        ...bodySlivers,
      ],
    );
    if (onRefresh == null) return scroll;
    return RefreshIndicator(onRefresh: onRefresh, child: scroll);
  }

  Widget _hero(BuildContext context) {
    final theme = Theme.of(context);
    final pub = _publication;
    final description = pub.description?.trim();

    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              ClipRRect(
                borderRadius: BorderRadius.circular(12),
                child: pub.logoUrl == null
                    ? Container(
                        width: 72,
                        height: 72,
                        color: theme.colorScheme.surfaceContainerHighest,
                        child: Icon(
                          Icons.newspaper,
                          size: 32,
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                      )
                    : ExtendedImage.network(
                        pub.logoUrl!,
                        width: 72,
                        height: 72,
                        fit: BoxFit.cover,
                        cacheWidth:
                            (72 * MediaQuery.devicePixelRatioOf(context))
                                .ceil(),
                      ),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      pub.displayName,
                      style: theme.textTheme.headlineSmall!.copyWith(
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      Uri.tryParse(pub.baseUrl)?.host ?? pub.baseUrl,
                      style: theme.textTheme.bodySmall!.copyWith(
                        color: theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Wrap(
                      spacing: 8,
                      runSpacing: 8,
                      children: [
                        SubstackFollowButton(publication: pub),
                        OutlinedButton.icon(
                          onPressed: () =>
                              addSubstackPublicationToGroup(context, pub),
                          icon: const Icon(Icons.group_add, size: 18),
                          label: Text(L10n.of(context).add_to_group),
                        ),
                        OutlinedButton.icon(
                          onPressed: () =>
                              showSubstackSimilarSheet(context, pub),
                          icon: const Icon(Icons.person_search, size: 18),
                          label: Text(L10n.of(context).plugin_substack_similar),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ],
          ),
          if (description != null && description.isNotEmpty) ...[
            const SizedBox(height: 12),
            Text(
              description,
              maxLines: 4,
              overflow: TextOverflow.ellipsis,
              style: theme.textTheme.bodyMedium!.copyWith(height: 1.4),
            ),
          ],
        ],
      ),
    );
  }

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final store = SubstackArchiveStore(context.read(), widget.publication);
      setState(() => _store = store);
      store.refresh();
      _enrichHeader();
    });
  }

  @override
  void dispose() {
    _searchController.dispose();
    _store?.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final store = _store;
    final title = _publication.displayName;
    if (store == null) {
      return Scaffold(
        appBar: AppBar(title: Text(widget.publication.displayName)),
        body: const Center(child: CircularProgressIndicator()),
      );
    }

    return Scaffold(
      appBar: AppBar(title: Text(title)),
      body: _query.isNotEmpty
          ? _profileScroll(context, bodySlivers: _searchSlivers(context))
          : ScopedBuilder<SubstackArchiveStore, SubstackFeedSnapshot>(
              store: store,
              onError: (_, error) => _profileScroll(
                context,
                bodySlivers: [
                  SliverFillRemaining(
                    hasScrollBody: false,
                    child: FullPageErrorWidget(
                      error: error,
                      stackTrace: null,
                      prefix: L10n.of(context).plugin_substack_load_error,
                      onRetry: store.refresh,
                    ),
                  ),
                ],
              ),
              onLoading: (_) => _profileScroll(
                context,
                bodySlivers: const [
                  SliverFillRemaining(
                    hasScrollBody: false,
                    child: Center(child: CircularProgressIndicator()),
                  ),
                ],
              ),
              onState: (context, snapshot) => _profileScroll(
                context,
                onRefresh: store.refresh,
                bodySlivers: _feedSlivers(context, store, snapshot: snapshot),
              ),
            ),
    );
  }
}

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
          label: Text(
            isFollowed
                ? l10n.plugin_substack_unfollow
                : l10n.plugin_substack_follow,
          ),
          onPressed: () async {
            final subscriptions = context.read<SubscriptionsModel>();
            isFollowed
                ? await store.remove(publication.id)
                : await store.add(publication);
            await subscriptions.reloadSubscriptions();
          },
        );
      },
    );
  }
}
