import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_listing_screen.dart';
import 'package:xta/plugins/reddit/reddit_post_card.dart';
import 'package:xta/plugins/reddit/reddit_read_session.dart';
import 'package:xta/plugins/reddit/reddit_screen.dart' show redditErrorMessage;
import 'package:xta/plugins/reddit/reddit_sort_sheet.dart';
import 'package:xta/plugins/reddit/reddit_search_html.dart';
import 'package:xta/ui/errors.dart';

/// Posts / communities / accounts for a query — used by the dedicated screen
/// and by Discover when the Reddit chip owns the hub.
class RedditSearchBody extends StatelessWidget {
  final String query;
  final String? subreddit;
  final String searchSort;
  final ValueChanged<String>? onSearchSort;

  const RedditSearchBody({
    super.key,
    required this.query,
    this.subreddit,
    this.searchSort = 'relevance',
    this.onSearchSort,
  });

  static const _searchSorts = ['relevance', 'top', 'new', 'comments'];

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return DefaultTabController(
      length: 3,
      child: Column(
        children: [
          TabBar(
            tabs: [
              Tab(text: l10n.tweets),
              Tab(text: l10n.plugin_reddit_search_subreddits),
              Tab(text: l10n.plugin_reddit_search_users),
            ],
          ),
          Expanded(
            child: TabBarView(
              children: [
                Column(
                  children: [
                    _sortChips(l10n),
                    Expanded(
                      child: RedditSearchTab<RedditPost>(
                        query: '$query·$searchSort',
                        search: (client, _) => _searchPosts(context, client),
                        itemBuilder: (context, post) =>
                            RedditPostCard(post: post, showSourceBadge: false),
                      ),
                    ),
                  ],
                ),
                RedditSearchTab<RedditSubredditResult>(
                  query: query,
                  search: (client, q) => _searchSubreddits(context, client, q),
                  leadingBuilder: (context, q) {
                    final name = normaliseSubreddit(q);
                    return RedditNameRow(
                      label: 'r/$q',
                      icon: Icons.travel_explore,
                      trailing: name == null
                          ? null
                          : RedditFollowIconButton(subreddit: name),
                      onTap: () =>
                          _open(context, RedditListingScreen.subreddit(q)),
                    );
                  },
                  itemBuilder: (context, result) =>
                      RedditSubredditRow(result: result),
                ),
                RedditSearchTab<RedditUserResult>(
                  query: query,
                  search: (client, q) => _searchUsers(context, client, q),
                  leadingBuilder: (context, q) => RedditNameRow(
                    label: 'u/$q',
                    icon: Icons.person_outline,
                    onTap: () => _open(context, RedditListingScreen.user(q)),
                  ),
                  itemBuilder: (context, result) => RedditNameRow(
                    label: 'u/${result.name}',
                    icon: Icons.person_outline,
                    onTap: () =>
                        _open(context, RedditListingScreen.user(result.name)),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  static void _open(BuildContext context, Widget screen) {
    Navigator.push(context, MaterialPageRoute(builder: (_) => screen));
  }

  Future<List<RedditPost>> _searchPosts(
    BuildContext context,
    RedditClient client,
  ) async {
    final prefs = PrefService.of(context, listen: false);
    final nsfwMode = storedRedditNsfwMode(prefs);
    final session = await RedditReadSession.resolve(prefs: prefs);
    final posts = await session.searchPosts(
      client,
      query,
      subreddit: subreddit,
      searchSort: searchSort,
    );
    return filterRedditPosts(posts, nsfwMode: nsfwMode);
  }

  Future<List<RedditSubredditResult>> _searchSubreddits(
    BuildContext context,
    RedditClient client,
    String q,
  ) async {
    final session = await RedditReadSession.resolve(
      prefs: PrefService.of(context, listen: false),
    );
    return session.searchSubreddits(client, q);
  }

  Future<List<RedditUserResult>> _searchUsers(
    BuildContext context,
    RedditClient client,
    String q,
  ) async {
    final session = await RedditReadSession.resolve(
      prefs: PrefService.of(context, listen: false),
    );
    return session.searchUsers(client, q);
  }

  Widget _sortChips(L10n l10n) {
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      padding: const EdgeInsets.fromLTRB(8, 8, 8, 4),
      child: Row(
        children: [
          if (subreddit != null)
            Padding(
              padding: const EdgeInsets.only(right: 10),
              child: Chip(
                avatar: const Icon(Icons.filter_alt_outlined, size: 16),
                label: Text('r/$subreddit'),
                visualDensity: VisualDensity.compact,
              ),
            ),
          for (final sort in _searchSorts)
            Padding(
              padding: const EdgeInsets.only(right: 6),
              child: ChoiceChip(
                label: Text(_searchSortLabel(l10n, sort)),
                selected: searchSort == sort,
                onSelected: (_) => onSearchSort?.call(sort),
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
class RedditSearchTab<T> extends StatefulWidget {
  final String query;
  final Future<List<T>> Function(RedditClient client, String query) search;
  final Widget Function(BuildContext context, T item) itemBuilder;
  final Widget Function(BuildContext context, String query)? leadingBuilder;

  const RedditSearchTab({
    super.key,
    required this.query,
    required this.search,
    required this.itemBuilder,
    this.leadingBuilder,
  });

  @override
  State<RedditSearchTab<T>> createState() => _RedditSearchTabState<T>();
}

class _RedditSearchTabState<T> extends State<RedditSearchTab<T>>
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
  void didUpdateWidget(RedditSearchTab<T> old) {
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

class RedditSubredditRow extends StatelessWidget {
  final RedditSubredditResult result;

  const RedditSubredditRow({super.key, required this.result});

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
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (subscribers != null) Text('$subscribers'),
          RedditFollowIconButton(subreddit: result.name),
        ],
      ),
      onTap: () => Navigator.push(
        context,
        MaterialPageRoute(
          builder: (_) => RedditListingScreen.subreddit(result.name),
        ),
      ),
    );
  }
}

class RedditNameRow extends StatelessWidget {
  final String label;
  final IconData icon;
  final VoidCallback onTap;
  final Widget? trailing;

  const RedditNameRow({
    super.key,
    required this.label,
    required this.icon,
    required this.onTap,
    this.trailing,
  });

  @override
  Widget build(BuildContext context) {
    return ListTile(
      leading: Icon(icon),
      title: Text(label),
      trailing: trailing,
      onTap: onTap,
    );
  }
}
