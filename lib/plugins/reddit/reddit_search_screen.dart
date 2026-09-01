import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_listing_screen.dart';
import 'package:xta/plugins/reddit/reddit_search_body.dart';

/// Searching Reddit for posts, subreddits and accounts.
///
/// Three separate searches rather than one ranked list, because they answer
/// different questions and Reddit serves them from different pages anyway.
class RedditSearchScreen extends StatefulWidget {
  /// When set, the post search is scoped to this community (`restrict_sr`).
  final String? subreddit;
  final String? initialQuery;

  const RedditSearchScreen({super.key, this.subreddit, this.initialQuery});

  @override
  State<RedditSearchScreen> createState() => _RedditSearchScreenState();
}

class _RedditSearchScreenState extends State<RedditSearchScreen> {
  late final TextEditingController _controller;
  late String _query;
  String _searchSort = 'relevance';

  @override
  void initState() {
    super.initState();
    final initial = (widget.initialQuery ?? '').trim();
    _controller = TextEditingController(text: initial);
    _query = initial;
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _search(String value) {
    final trimmed = value.trim();
    if (trimmed != _query) {
      setState(() => _query = trimmed);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final queryName = normaliseSubreddit(_query);

    return Scaffold(
      appBar: AppBar(
        title: TextField(
          controller: _controller,
          autofocus: (widget.initialQuery ?? '').trim().isEmpty,
          textInputAction: TextInputAction.search,
          decoration: InputDecoration(
            border: InputBorder.none,
            hintText: l10n.plugin_reddit_search_hint,
            suffixIcon: IconButton(
              icon: const Icon(Icons.search),
              tooltip: l10n.search,
              onPressed: () => _search(_controller.text),
            ),
          ),
          onSubmitted: _search,
        ),
        actions: [
          if (queryName != null) RedditFollowIconButton(subreddit: queryName),
        ],
      ),
      body: RedditSearchBody(
        query: _query,
        subreddit: widget.subreddit,
        searchSort: _searchSort,
        onSearchSort: (sort) {
          if (sort != _searchSort) setState(() => _searchSort = sort);
        },
      ),
    );
  }
}
