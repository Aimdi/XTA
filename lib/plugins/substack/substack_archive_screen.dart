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
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/ui/errors.dart';

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
    if ((current.description?.trim().isNotEmpty ?? false) &&
        (current.logoUrl?.isNotEmpty ?? false)) {
      return;
    }
    try {
      final fresh = await context.read<SubstackClient>().fetchPublication(
        Uri.parse(current.baseUrl),
      );
      if (!mounted) return;
      setState(() {
        _enriched = SubstackPublication(
          subdomain: current.subdomain,
          baseUrl: current.baseUrl,
          name: fresh.name.isNotEmpty ? fresh.name : current.name,
          description: fresh.description ?? current.description,
          logoUrl: fresh.logoUrl ?? current.logoUrl,
        );
      });
    } catch (_) {
      // Keep the stub header; the archive list still loads.
    }
  }

  Widget _searchResults(BuildContext context) {
    if (_searching) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_searchError != null) {
      return FullPageErrorWidget(
        error: _searchError,
        stackTrace: null,
        prefix: L10n.of(context).plugin_substack_load_error,
        onRetry: () => _search(_query),
      );
    }
    final results = _results ?? const <SubstackPost>[];
    if (results.isEmpty) {
      return Center(child: Text(L10n.of(context).plugin_substack_feed_empty));
    }
    return ListView.builder(
      padding: const EdgeInsets.only(bottom: 24),
      itemCount: results.length,
      itemBuilder: (context, index) => SubstackPostCard(
        post: results[index],
        showSourceBadge: false,
        logoUrl: _publication.logoUrl,
      ),
    );
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
                      pub.name,
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
    if (store == null) {
      return Scaffold(
        appBar: AppBar(title: Text(widget.publication.name)),
        body: const Center(child: CircularProgressIndicator()),
      );
    }

    return Scaffold(
      appBar: AppBar(title: Text(_publication.name)),
      body: Column(
        children: [
          _hero(context),
          Padding(
            padding: const EdgeInsets.fromLTRB(12, 0, 12, 4),
            child: XSearchField(
              controller: _searchController,
              hintText: L10n.of(context).search,
              onChanged: _search,
            ),
          ),
          Expanded(
            child: _query.isNotEmpty
                ? _searchResults(context)
                : ScopedBuilder<SubstackArchiveStore, SubstackFeedSnapshot>(
                    store: store,
                    onError: (_, error) => FullPageErrorWidget(
                      error: error,
                      stackTrace: null,
                      prefix: L10n.of(context).plugin_substack_load_error,
                      onRetry: store.refresh,
                    ),
                    onLoading: (_) =>
                        const Center(child: CircularProgressIndicator()),
                    onState: (context, snapshot) {
                      if (snapshot.posts.isEmpty) {
                        return Center(
                          child: Text(
                            L10n.of(context).plugin_substack_feed_empty,
                          ),
                        );
                      }
                      return RefreshIndicator(
                        onRefresh: store.refresh,
                        child: ListView.builder(
                          padding: const EdgeInsets.only(bottom: 24),
                          itemCount:
                              snapshot.posts.length +
                              (snapshot.canLoadMore ? 1 : 0),
                          itemBuilder: (context, index) {
                            if (index >= snapshot.posts.length) {
                              return Padding(
                                padding: const EdgeInsets.all(16),
                                child: Center(
                                  child: OutlinedButton(
                                    onPressed: store.loadMore,
                                    child: Text(
                                      L10n.of(
                                        context,
                                      ).plugin_substack_load_more,
                                    ),
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
                      );
                    },
                  ),
          ),
        ],
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
