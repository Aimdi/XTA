import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/plugins/reddit/reddit_actions.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_feed_list.dart';
import 'package:xta/plugins/reddit/reddit_home_source.dart';
import 'package:xta/plugins/reddit/reddit_listing_body.dart';
import 'package:xta/plugins/reddit/reddit_plugin.dart';
import 'package:xta/plugins/reddit/reddit_saved_screen.dart';
import 'package:xta/plugins/reddit/reddit_store.dart';
import 'package:provider/provider.dart';

export 'package:xta/plugins/reddit/reddit_states.dart' show redditErrorMessage;

/// Account-free Reddit reading: the subreddits you follow, newest first.
class RedditScreen extends StatefulWidget {
  final ScrollController scrollController;

  const RedditScreen({super.key, required this.scrollController});

  @override
  State<RedditScreen> createState() => _RedditScreenState();
}

class _RedditScreenState extends State<RedditScreen> {
  final _popularKey = GlobalKey<RedditListingBodyState>();
  final _allKey = GlobalKey<RedditListingBodyState>();
  final _subredditKeys = <String, GlobalKey<RedditListingBodyState>>{};

  RedditHomeStore? _home;
  bool _loadedStores = false;

  RedditHomeStore get home => _home!;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _home ??= RedditHomeStore(PrefService.of(context, listen: false));
    if (!_loadedStores) {
      _loadedStores = true;
      unawaited(_prime());
    }
  }

  Future<void> _prime() async {
    await context.read<RedditSavedStore>().load();
    final subs = context.read<RedditSubredditsStore>();
    if (subs.state.isEmpty) {
      await subs.load();
    }
    if (mounted) {
      home.reconcileFollowed(subs.state);
    }
  }

  @override
  void dispose() {
    _home?.destroy();
    super.dispose();
  }

  GlobalKey<RedditListingBodyState> _subredditKey(String name) {
    return _subredditKeys.putIfAbsent(
      name.toLowerCase(),
      GlobalKey<RedditListingBodyState>.new,
    );
  }

  Future<void> _refreshCurrent() {
    final source = home.state;
    if (source.viewingSubreddit) {
      return _subredditKey(source.subreddit!).currentState?.refresh() ??
          Future.value();
    }
    return switch (source.mode) {
      RedditFeedMode.following => context.read<RedditFeedStore>().refresh(
        force: true,
      ),
      RedditFeedMode.popular =>
        _popularKey.currentState?.refresh() ?? Future.value(),
      RedditFeedMode.all => _allKey.currentState?.refresh() ?? Future.value(),
    };
  }

  void _openSaved() {
    Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => const RedditSavedScreen()),
    );
  }

  @override
  Widget build(BuildContext context) {
    final store = _home;
    if (store == null) {
      return const SizedBox.shrink();
    }

    return Scaffold(
      primary: !PluginEmbedded.maybeOf(context),
      body: ScopedBuilder<RedditHomeStore, RedditHomeSource>(
        store: store,
        onState: (context, source) => Column(
          children: [
            RedditHomeChrome(
              source: source,
              onMode: store.selectMode,
              actions: [
                IconButton(
                  tooltip: L10n.of(context).saved,
                  icon: const Icon(Icons.bookmark_border),
                  onPressed: _openSaved,
                ),
                RedditFeedActions(onRefresh: _refreshCurrent),
              ],
            ),
            RedditSubredditChips(home: store),
            Expanded(child: _body(source)),
          ],
        ),
      ),
    );
  }

  Widget _body(RedditHomeSource source) {
    final name = source.subreddit;
    if (name != null && name.isNotEmpty) {
      return RedditListingBody.subreddit(
        name,
        key: _subredditKey(name),
        scrollController: widget.scrollController,
        showSourceBadge: false,
      );
    }
    return switch (source.mode) {
      RedditFeedMode.following => RedditFeedList(
        scrollController: widget.scrollController,
      ),
      RedditFeedMode.popular => RedditListingBody.subreddit(
        'popular',
        key: _popularKey,
        scrollController: widget.scrollController,
        showSourceBadge: false,
      ),
      RedditFeedMode.all => RedditListingBody.subreddit(
        'all',
        key: _allKey,
        scrollController: widget.scrollController,
        showSourceBadge: false,
      ),
    };
  }
}

/// Icon tabs for Following / Popular / All — same chrome as the other plugins.
class RedditHomeChrome extends StatelessWidget {
  final RedditHomeSource source;
  final ValueChanged<RedditFeedMode> onMode;
  final List<Widget> actions;

  const RedditHomeChrome({
    super.key,
    required this.source,
    required this.onMode,
    this.actions = const [],
  });

  bool _railSelected(RedditFeedMode mode) =>
      !source.viewingSubreddit && source.mode == mode;

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return PluginHomeChrome(
      accent: RedditPlugin().brandColor,
      tabs: [
        PluginHomeTab(
          icon: Icons.home_outlined,
          label: l10n.plugin_reddit_feed_following,
          selected: _railSelected(RedditFeedMode.following),
          onTap: () => onMode(RedditFeedMode.following),
        ),
        PluginHomeTab(
          icon: Icons.whatshot_outlined,
          label: l10n.plugin_reddit_feed_popular,
          selected: _railSelected(RedditFeedMode.popular),
          onTap: () => onMode(RedditFeedMode.popular),
        ),
        PluginHomeTab(
          icon: Icons.public_outlined,
          label: l10n.plugin_reddit_feed_all,
          selected: _railSelected(RedditFeedMode.all),
          onTap: () => onMode(RedditFeedMode.all),
        ),
      ],
      actions: actions,
    );
  }
}

/// Followed communities as a chip strip, so r/foo and r/bar stay on this tab.
class RedditSubredditChips extends StatelessWidget {
  final RedditHomeStore home;

  const RedditSubredditChips({super.key, required this.home});

  @override
  Widget build(BuildContext context) {
    final subreddits = context.read<RedditSubredditsStore>();
    return ScopedBuilder<RedditSubredditsStore, List<String>>(
      store: subreddits,
      onState: (context, names) {
        if (names.isEmpty) {
          return const SizedBox.shrink();
        }
        return ScopedBuilder<RedditHomeStore, RedditHomeSource>(
          store: home,
          onState: (context, source) => _chipRow(context, names, source),
        );
      },
    );
  }

  Widget _chipRow(
    BuildContext context,
    List<String> names,
    RedditHomeSource source,
  ) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    return Semantics(
      label: l10n.plugin_reddit_followed_communities,
      child: SizedBox(
        height: 48,
        child: ListView(
          scrollDirection: Axis.horizontal,
          padding: const EdgeInsets.fromLTRB(12, 4, 12, 8),
          children: [
            for (final name in names)
              Padding(
                padding: const EdgeInsets.only(right: 6),
                child: ChoiceChip(
                  key: ValueKey('reddit-community-$name'),
                  label: Text('r/$name'),
                  selected: isSelectedRedditCommunity(source.subreddit, name),
                  showCheckmark: false,
                  visualDensity: VisualDensity.compact,
                  materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
                  selectedColor: RedditPlugin().brandColor.withValues(
                    alpha: theme.brightness == Brightness.dark ? 0.28 : 0.16,
                  ),
                  onSelected: (selected) {
                    if (selected) {
                      unawaited(home.selectSubreddit(name));
                    } else {
                      unawaited(home.selectMode(source.mode));
                    }
                  },
                ),
              ),
          ],
        ),
      ),
    );
  }
}
