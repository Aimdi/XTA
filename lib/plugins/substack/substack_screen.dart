import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:quax/generated/l10n.dart';
import 'package:quax/plugins/substack/substack_add_screen.dart';
import 'package:quax/plugins/substack/substack_archive_screen.dart';
import 'package:quax/plugins/substack/substack_models.dart';
import 'package:quax/plugins/substack/substack_post_card.dart';
import 'package:quax/plugins/substack/substack_store.dart';
import 'package:quax/ui/errors.dart';

class SubstackScreen extends StatefulWidget {
  final ScrollController scrollController;

  const SubstackScreen({super.key, required this.scrollController});

  @override
  State<SubstackScreen> createState() => _SubstackScreenState();
}

class _SubstackScreenState extends State<SubstackScreen> {
  bool _isInitialLoad = true;
  bool _hasLoaded = false;

  @override
  void initState() {
    super.initState();
    // Only load publications and read state on first init
    // DON'T auto-refresh feed - only refresh on explicit pull-to-refresh
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      final pubs = context.read<SubstackPublicationsStore>();
      final read = context.read<SubstackReadStore>();
      await pubs.load();
      await read.load();
      _isInitialLoad = false;
      if (mounted) {
        setState(() {});
      }
    });
  }

  Future<void> _openAdd() async {
    final added = await Navigator.push<bool>(
      context,
      MaterialPageRoute(builder: (_) => const SubstackAddScreen()),
    );
    if (added == true && mounted) {
      await context.read<SubstackFeedStore>().refresh();
    }
  }

  @override
  Widget build(BuildContext context) {
    final pubs = context.read<SubstackPublicationsStore>();
    final feed = context.read<SubstackFeedStore>();

    return Scaffold(
      appBar: AppBar(
        title: Text(L10n.of(context).plugin_substack_title),
        actions: [
          IconButton(
            tooltip: L10n.of(context).plugin_substack_add,
            icon: const Icon(Icons.add),
            onPressed: _openAdd,
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () async {
          await pubs.load();
          await feed.refresh();
        },
        child: ScopedBuilder<SubstackPublicationsStore, List<SubstackPublication>>(
          store: pubs,
          onError: (_, error) => FullPageErrorWidget(
            error: error,
            stackTrace: null,
            prefix: L10n.of(context).plugin_substack_load_error,
            onRetry: pubs.load,
          ),
          onLoading: (_) => const Center(child: CircularProgressIndicator()),
          onState: (context, publications) {
            if (publications.isEmpty) {
              return ListView(
                controller: widget.scrollController,
                children: [
                  const SizedBox(height: 80),
                  Icon(Icons.newspaper_outlined, size: 48, color: Theme.of(context).colorScheme.outline),
                  const SizedBox(height: 16),
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 32),
                    child: Text(
                      L10n.of(context).plugin_substack_empty,
                      textAlign: TextAlign.center,
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                  ),
                  const SizedBox(height: 8),
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 32),
                    child: Text(
                      L10n.of(context).plugin_substack_empty_description,
                      textAlign: TextAlign.center,
                    ),
                  ),
                  const SizedBox(height: 24),
                  Center(
                    child: FilledButton.icon(
                      onPressed: _openAdd,
                      icon: const Icon(Icons.add),
                      label: Text(L10n.of(context).plugin_substack_add),
                    ),
                  ),
                ],
              );
            }

            return ScopedBuilder<SubstackFeedStore, SubstackFeedSnapshot>(
              store: feed,
              onError: (_, error) => FullPageErrorWidget(
                error: error,
                stackTrace: null,
                prefix: L10n.of(context).plugin_substack_load_error,
                onRetry: feed.refresh,
              ),
              onLoading: (_) => const Center(child: CircularProgressIndicator()),
              onState: (context, snapshot) {
                // Auto-refresh feed only on first load or when returning to top
                if (!_hasLoaded) {
                  _hasLoaded = true;
                  WidgetsBinding.instance.addPostFrameCallback((_) async {
                    if (!mounted) return;
                    if (snapshot.posts.isEmpty) {
                      await feed.refresh();
                    }
                  });
                }
                
                final children = <Widget>[
                  _FollowedStrip(
                    publications: publications,
                    onOpen: (pub) {
                      Navigator.push(
                        context,
                        MaterialPageRoute(builder: (_) => SubstackArchiveScreen(publication: pub)),
                      );
                    },
                    onRemove: (id) async {
                      await pubs.remove(id);
                      await feed.refresh();
                    },
                  ),
                ];

                if (snapshot.failedCount > 0) {
                  children.add(
                    ListTile(
                      leading: Icon(Icons.warning_amber_outlined, color: Theme.of(context).colorScheme.error),
                      title: Text(L10n.of(context).plugin_substack_partial_error(snapshot.failedCount)),
                    ),
                  );
                }

                if (snapshot.posts.isEmpty) {
                  children.addAll([
                    const SizedBox(height: 48),
                    Center(child: Text(L10n.of(context).plugin_substack_feed_empty)),
                  ]);
                  return ListView(
                    controller: widget.scrollController,
                    children: children,
                  );
                }

                return ListView.separated(
                  controller: widget.scrollController,
                  padding: const EdgeInsets.only(bottom: 24),
                  itemCount: 1 + snapshot.posts.length + (snapshot.canLoadMore ? 1 : 0),
                  // The cards carry their own hairline, as posts do everywhere else.
                  separatorBuilder: (_, _) => const SizedBox.shrink(),
                  itemBuilder: (context, index) {
                    if (index == 0) {
                      return Column(mainAxisSize: MainAxisSize.min, children: children);
                    }
                    final postIndex = index - 1;
                    if (postIndex < snapshot.posts.length) {
                      return SubstackPostCard(post: snapshot.posts[postIndex], showSourceBadge: false);
                    }
                    return Padding(
                      padding: const EdgeInsets.all(16),
                      child: Center(
                        child: OutlinedButton(
                          onPressed: feed.loadMore,
                          child: Text(L10n.of(context).plugin_substack_load_more),
                        ),
                      ),
                    );
                  },
                );
              },
            );
          },
        ),
      ),
    );
  }
}

class _FollowedStrip extends StatelessWidget {
  final List<SubstackPublication> publications;
  final Future<void> Function(String id) onRemove;
  final void Function(SubstackPublication publication) onOpen;

  const _FollowedStrip({
    required this.publications,
    required this.onRemove,
    required this.onOpen,
  });

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 64,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        itemCount: publications.length,
        separatorBuilder: (_, _) => const SizedBox(width: 8),
        itemBuilder: (context, index) {
          final pub = publications[index];
          return InputChip(
            avatar: pub.logoUrl == null
                ? const Icon(Icons.newspaper, size: 18)
                : ClipOval(
                    child: ExtendedImage.network(pub.logoUrl!, width: 24, height: 24, fit: BoxFit.cover),
                  ),
            label: Text(pub.name),
            onPressed: () => onOpen(pub),
            onDeleted: () => onRemove(pub.id),
            deleteIcon: const Icon(Icons.close, size: 16),
            deleteButtonTooltipMessage: L10n.of(context).plugin_substack_unfollow,
          );
        },
      ),
    );
  }
}
