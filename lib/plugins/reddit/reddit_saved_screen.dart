import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_post_card.dart';
import 'package:xta/plugins/reddit/reddit_store.dart';
import 'package:xta/ui/feed_list.dart';

class RedditSavedScreen extends StatefulWidget {
  const RedditSavedScreen({super.key});

  @override
  State<RedditSavedScreen> createState() => _RedditSavedScreenState();
}

class _RedditSavedScreenState extends State<RedditSavedScreen> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        context.read<RedditSavedStore>().load();
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final store = context.read<RedditSavedStore>();

    return Scaffold(
      appBar: AppBar(title: Text(L10n.of(context).saved)),
      body: ScopedBuilder<RedditSavedStore, List<RedditPost>>(
        store: store,
        onLoading: (_) => const Center(child: CircularProgressIndicator()),
        onState: (context, posts) =>
            posts.isEmpty ? _empty(context) : _list(posts),
      ),
    );
  }

  Widget _empty(BuildContext context) {
    final theme = Theme.of(context);
    return ListView(
      padding: const EdgeInsets.fromLTRB(24, 72, 24, 24),
      children: [
        Icon(Icons.bookmark_border, size: 48, color: theme.colorScheme.outline),
        const SizedBox(height: 16),
        Text(
          L10n.of(context).plugin_reddit_saved_empty,
          textAlign: TextAlign.center,
        ),
      ],
    );
  }

  Widget _list(List<RedditPost> posts) {
    return FeedListView(
      itemCount: posts.length,
      itemBuilder: (context, index) =>
          RedditPostCard(post: posts[index], showSourceBadge: false),
    );
  }
}
