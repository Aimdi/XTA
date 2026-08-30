import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:quax/generated/l10n.dart';
import 'package:quax/plugins/reddit/reddit_client.dart';
import 'package:quax/plugins/reddit/reddit_post_card.dart';
import 'package:quax/plugins/reddit/reddit_screen.dart' show redditErrorMessage;
import 'package:quax/plugins/reddit/reddit_store.dart';
import 'package:quax/ui/errors.dart';

/// Every followed subreddit, newest first.
///
/// The body of the Reddit tab, and of the Reddit entry in the home feed
/// switcher — the same list either way, because they are the same feed and
/// two copies of it would drift.
class RedditFeedList extends StatefulWidget {
  final ScrollController? scrollController;

  /// Offered by the empty state. Null leaves it out, for a place with nowhere
  /// to put a subreddit-adding dialog.
  final VoidCallback? onAddSubreddit;

  const RedditFeedList({super.key, this.scrollController, this.onAddSubreddit});

  @override
  State<RedditFeedList> createState() => _RedditFeedListState();
}

class _RedditFeedListState extends State<RedditFeedList> with AutomaticKeepAliveClientMixin {
  @override
  bool get wantKeepAlive => true;

  bool _isInitialLoad = true;
  bool _hasLoaded = false;

  @override
  void initState() {
    super.initState();
    // Only load subreddits on first init, but DON'T auto-refresh feed
    // This prevents constant reloading when switching between tabs
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      if (!mounted) return;
      await context.read<RedditSubredditsStore>().load();
      _isInitialLoad = false;
      if (mounted) {
        setState(() {});
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    final l10n = L10n.of(context);
    final feed = context.read<RedditFeedStore>();

    return RefreshIndicator(
      onRefresh: () async {
        // Only refresh when user explicitly pulls down
        await feed.refresh();
      },
      child: ScopedBuilder<RedditFeedStore, List<RedditPost>>.transition(
        store: feed,
        onError: (_, error) => FullPageErrorWidget(
          error: error,
          stackTrace: null,
          prefix: redditErrorMessage(l10n, error!),
          onRetry: feed.refresh,
        ),
        onLoading: (_) => const Center(child: CircularProgressIndicator()),
        onState: (_, posts) => posts.isEmpty ? _empty(context, l10n) : _list(posts),
      ),
    );
  }

  Widget _list(List<RedditPost> posts) {
    // Auto-refresh feed only on first load or when returning to top
    if (!_hasLoaded) {
      _hasLoaded = true;
      WidgetsBinding.instance.addPostFrameCallback((_) async {
        if (!mounted) return;
        final feed = context.read<RedditFeedStore>();
        if (feed.state.isEmpty) {
          await feed.refresh();
        }
      });
    }
    
    return ListView.builder(
      controller: widget.scrollController,
      itemCount: posts.length,
      itemBuilder: (context, index) => RedditPostCard(post: posts[index], showSourceBadge: false),
    );
  }

  Widget _empty(BuildContext context, L10n l10n) {
    final onAdd = widget.onAddSubreddit;

    return ListView(
      controller: widget.scrollController,
      padding: const EdgeInsets.fromLTRB(24, 48, 24, 24),
      children: [
        Icon(Icons.forum_outlined, size: 48, color: Theme.of(context).colorScheme.outline),
        const SizedBox(height: 16),
        Text(l10n.plugin_reddit_empty, textAlign: TextAlign.center),
        if (onAdd != null) ...[
          const SizedBox(height: 16),
          // Telling the reader to add a subreddit and then leaving the only
          // control in the app bar is how this screen managed to look broken
          // when it was merely empty.
          Center(
            child: FilledButton.icon(
              onPressed: onAdd,
              icon: const Icon(Icons.add),
              label: Text(l10n.plugin_reddit_add),
            ),
          ),
        ],
      ],
    );
  }
}
