import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:quax/generated/l10n.dart';
import 'package:quax/plugins/substack/substack_models.dart';
import 'package:quax/plugins/substack/substack_post_card.dart';
import 'package:quax/plugins/substack/substack_store.dart';
import 'package:quax/ui/errors.dart';

class SubstackArchiveScreen extends StatefulWidget {
  final SubstackPublication publication;

  const SubstackArchiveScreen({super.key, required this.publication});

  @override
  State<SubstackArchiveScreen> createState() => _SubstackArchiveScreenState();
}

class _SubstackArchiveScreenState extends State<SubstackArchiveScreen> {
  SubstackArchiveStore? _store;
  bool _hasLoaded = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final store = SubstackArchiveStore(context.read(), widget.publication);
      setState(() => _store = store);
      // DON'T auto-refresh on init - only refresh on explicit pull-to-refresh
    });
  }

  @override
  void dispose() {
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
      appBar: AppBar(title: Text(widget.publication.name)),
      body: ScopedBuilder<SubstackArchiveStore, SubstackFeedSnapshot>(
        store: store,
        onError: (_, error) => FullPageErrorWidget(
          error: error,
          stackTrace: null,
          prefix: L10n.of(context).plugin_substack_load_error,
          onRetry: store.refresh,
        ),
        onLoading: (_) => const Center(child: CircularProgressIndicator()),
        onState: (context, snapshot) {
          // Auto-refresh only on first load
          if (!_hasLoaded) {
            _hasLoaded = true;
            WidgetsBinding.instance.addPostFrameCallback((_) async {
              if (!mounted) return;
              if (snapshot.posts.isEmpty) {
                await store.refresh();
              }
            });
          }
          
          if (snapshot.posts.isEmpty) {
            return Center(child: Text(L10n.of(context).plugin_substack_feed_empty));
          }
          return RefreshIndicator(
            onRefresh: store.refresh,
            child: ListView.separated(
              padding: const EdgeInsets.only(bottom: 24),
              itemCount: snapshot.posts.length + (snapshot.canLoadMore ? 1 : 0),
              separatorBuilder: (_, _) => const Divider(height: 1),
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
                return SubstackPostCard(post: snapshot.posts[index], showSourceBadge: false);
              },
            ),
          );
        },
      ),
    );
  }
}
