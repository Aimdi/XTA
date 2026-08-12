import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_listing_screen.dart';
import 'package:xta/plugins/reddit/reddit_post_card.dart';
import 'package:xta/plugins/reddit/reddit_screen.dart' show redditErrorMessage;
import 'package:xta/plugins/reddit/reddit_search_html.dart';
import 'package:xta/plugins/reddit/reddit_sort_sheet.dart';
import 'package:xta/ui/errors.dart';

/// Searching Reddit for posts, subreddits and accounts.
///
/// Three separate searches rather than one ranked list, because they answer
/// different questions and Reddit serves them from different pages anyway.
class RedditSearchScreen extends StatefulWidget {
  /// When set, the post search is scoped to this community (`restrict_sr`).
  final String? subreddit;

  const RedditSearchScreen({super.key, this.subreddit});

  @override
  State<RedditSearchScreen> createState() => _RedditSearchScreenState();
}

class _RedditSearchScreenState extends State<RedditSearchScreen> {
  final _controller = TextEditingController();
  String _query = '';
  String _searchSort = 'relevance';

  static const _searchSorts = ['relevance', 'top', 'new', 'comments'];

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

    return DefaultTabController(
      length: 3,
      child: Scaffold(
        appBar: AppBar(
          title: TextField(
            controller: _controller,
            autofocus: true,
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
          bottom: TabBar(
            tabs: [
              Tab(text: l10n.tweets),
              Tab(text: l10n.plugin_reddit_search_subreddits),
              Tab(text: l10n.plugin_reddit_search_users),
            ],
          ),
        ),
        body: TabBarView(
          children: [
            Column(
              children: [
                _sortChips(l10n),
                Expanded(
                  child: _RedditSearchTab<RedditPost>(
                    query: '$_query·$_searchSort',
                    search: (client, _) => _searchPosts(client, _query),
                    itemBuilder: (context, post) =>
                        RedditPostCard(post: post, showSourceBadge: false),
                  ),
                ),
              ],
            ),
            _RedditSearchTab<RedditSubredditResult>(
              query: _query,
              search: (client, q) => client.searchSubreddits(q),
              // Reddit's subreddit search misses exact names surprisingly
              // often, and the reader usually knows the one they want — so the
              // name they typed is always offered, whatever came back.
              leadingBuilder: (context, q) => _RedditNameRow(
                label: 'r/$q',
                icon: Icons.travel_explore,
                onTap: () => _open(context, RedditListingScreen.subreddit(q)),
              ),
              itemBuilder: (context, result) =>
                  _RedditSubredditRow(result: result),
            ),
            _RedditSearchTab<RedditUserResult>(
              query: _query,
              search: (client, q) => client.searchUsers(q),
              leadingBuilder: (context, q) => _RedditNameRow(
                label: 'u/$q',
                icon: Icons.person_outline,
                onTap: () => _open(context, RedditListingScreen.user(q)),
              ),
              itemBuilder: (context, result) => _RedditNameRow(
                label: 'u/${result.name}',
                icon: Icons.person_outline,
                onTap: () =>
                    _open(context, RedditListingScreen.user(result.name)),
              ),
            ),
          ],
        ),
      ),
    );
  }

  static void _open(BuildContext context, Widget screen) {
    Navigator.push(context, MaterialPageRoute(builder: (_) => screen));
  }

  Future<List<RedditPost>> _searchPosts(
    RedditClient client,
    String query,
  ) async {
    final nsfwMode = storedRedditNsfwMode(
      PrefService.of(context, listen: false),
    );
    final posts = await client.searchPosts(
      query,
      subreddit: widget.subreddit,
      searchSort: _searchSort,
    );
    return filterRedditPosts(posts, nsfwMode: nsfwMode);
  }

  Widget _sortChips(L10n l10n) {
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      padding: const EdgeInsets.fromLTRB(8, 8, 8, 4),
      child: Row(
        children: [
          if (widget.subreddit != null)
            Padding(
              padding: const EdgeInsets.only(right: 10),
              child: Chip(
                avatar: const Icon(Icons.filter_alt_outlined, size: 16),
                label: Text('r/${widget.subreddit}'),
                visualDensity: VisualDensity.compact,
              ),
            ),
          for (final sort in _searchSorts)
            Padding(
              padding: const EdgeInsets.only(right: 6),
              child: ChoiceChip(
                label: Text(_searchSortLabel(l10n, sort)),
                selected: _searchSort == sort,
                onSelected: (_) {
                  if (sort != _searchSort) setState(() => _searchSort = sort);
                },
              ),
            ),
        ],
      ),
    );
  }

  String _searchSortLabel(L10n l10n, String sort) => switch (sort) {
    'top' => l10n.plugin_reddit_sort_top,
    'new' => l10n.plugin_reddit_sort_new,
    'comments' => l10n.plugin_reddit_search_sort_comments,
    _ => l10n.plugin_reddit_search_sort_relevance,
  };
}

/// One tab's results: run the search when the query changes, then list them.
class _RedditSearchTab<T> extends StatefulWidget {
  final String query;
  final Future<List<T>> Function(RedditClient client, String query) search;
  final Widget Function(BuildContext context, T item) itemBuilder;

  /// Shown above the results, when there is a query. Used for the "go straight
  /// to this name" row.
  final Widget Function(BuildContext context, String query)? leadingBuilder;

  const _RedditSearchTab({
    super.key,
    required this.query,
    required this.search,
    required this.itemBuilder,
    this.leadingBuilder,
  });

  @override
  State<_RedditSearchTab<T>> createState() => _RedditSearchTabState<T>();
}

class _RedditSearchTabState<T> extends State<_RedditSearchTab<T>>
    with AutomaticKeepAliveClientMixin {
  List<T>? _results;
  Object? _error;
  String? _loaded;

  @override
  bool get wantKeepAlive => true;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _run();
  }

  @override
  void didUpdateWidget(_RedditSearchTab<T> old) {
    super.didUpdateWidget(old);
    _run();
  }

  Future<void> _run() async {
    final query = widget.query;
    if (query.isEmpty || query == _loaded) {
      return;
    }

    _loaded = query;
    setState(() {
      _error = null;
      _results = null;
    });

    try {
      final results = await widget.search(context.read<RedditClient>(), query);
      if (mounted && _loaded == query) {
        setState(() => _results = results);
      }
    } catch (e) {
      // A failed search must not lose the shortcut row: knowing the exact name
      // is often why someone opened this tab.
      if (mounted && _loaded == query) {
        setState(() => _error = e);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    final l10n = L10n.of(context);

    if (widget.query.isEmpty) {
      return const SizedBox.shrink();
    }

    final leading = widget.leadingBuilder?.call(context, widget.query);
    final results = _results;

    return ListView(
      children: [
        ?leading,
        if (_error != null)
          Padding(
            padding: const EdgeInsets.all(24),
            child: FullPageErrorWidget(
              error: _error,
              stackTrace: null,
              prefix: redditErrorMessage(l10n, _error!),
              onRetry: () {
                _loaded = null;
                _run();
              },
            ),
          )
        else if (results == null)
          const Padding(
            padding: EdgeInsets.all(32),
            child: Center(child: CircularProgressIndicator()),
          )
        else if (results.isEmpty && leading == null)
          Padding(
            padding: const EdgeInsets.all(32),
            child: Center(child: Text(l10n.no_results)),
          )
        else
          for (final item in results) widget.itemBuilder(context, item),
      ],
    );
  }
}

class _RedditSubredditRow extends StatelessWidget {
  final RedditSubredditResult result;

  const _RedditSubredditRow({required this.result});

  @override
  Widget build(BuildContext context) {
    final subscribers = result.subscribers;
    final description = result.description;

    return ListTile(
      leading: const Icon(Icons.travel_explore),
      title: Text('r/${result.name}'),
      subtitle: description == null
          ? null
          : Text(description, maxLines: 2, overflow: TextOverflow.ellipsis),
      trailing: subscribers == null ? null : Text('$subscribers'),
      onTap: () => Navigator.push(
        context,
        MaterialPageRoute(
          builder: (_) => RedditListingScreen.subreddit(result.name),
        ),
      ),
    );
  }
}

/// A bare `r/name` or `u/name` row.
class _RedditNameRow extends StatelessWidget {
  final String label;
  final IconData icon;
  final VoidCallback onTap;

  const _RedditNameRow({
    required this.label,
    required this.icon,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return ListTile(leading: Icon(icon), title: Text(label), onTap: onTap);
  }
}
