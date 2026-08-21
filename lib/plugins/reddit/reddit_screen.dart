import 'dart:async';

import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/plugins/reddit/reddit_actions.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_feed_list.dart';
import 'package:xta/plugins/reddit/reddit_sort_sheet.dart';
import 'package:xta/plugins/reddit/reddit_listing_body.dart';
import 'package:xta/plugins/reddit/reddit_plugin.dart';
import 'package:xta/plugins/reddit/reddit_saved_screen.dart';
import 'package:xta/plugins/reddit/reddit_store.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';

// The failure wording moved to reddit_states.dart, next to the widget that
// renders it. Re-exported so the several files that ask this screen for it
// keep working.
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

  late RedditFeedMode _mode;
  bool _loadedStores = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _mode = storedRedditFeedMode(PrefService.of(context, listen: false));
    if (!_loadedStores) {
      _loadedStores = true;
      unawaited(context.read<RedditSavedStore>().load());
    }
  }

  Future<void> _refreshCurrent() {
    return switch (_mode) {
      RedditFeedMode.following => context.read<RedditFeedStore>().refresh(
        force: true,
      ),
      RedditFeedMode.popular =>
        _popularKey.currentState?.refresh() ?? Future.value(),
      RedditFeedMode.all => _allKey.currentState?.refresh() ?? Future.value(),
    };
  }

  Future<void> _setMode(RedditFeedMode mode) async {
    await PrefService.of(
      context,
      listen: false,
    ).set(optionPluginRedditFeedMode, mode.name);
    if (mounted) {
      setState(() => _mode = mode);
    }
  }

  void _openSaved() {
    Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => const RedditSavedScreen()),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      primary: !PluginEmbedded.maybeOf(context),
      body: Column(
        children: [
          RedditHomeChrome(
            mode: _mode,
            onMode: _setMode,
            actions: [
              const RedditCommunitySwitcher(),
              IconButton(
                tooltip: L10n.of(context).saved,
                icon: const Icon(Icons.bookmark_border),
                onPressed: _openSaved,
              ),
              RedditFeedActions(onRefresh: _refreshCurrent),
            ],
          ),
          Expanded(child: _body()),
        ],
      ),
    );
  }

  Widget _body() => switch (_mode) {
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

/// Icon tabs for Following / Popular / All — same chrome as the other plugins.
class RedditHomeChrome extends StatelessWidget {
  final RedditFeedMode mode;
  final ValueChanged<RedditFeedMode> onMode;
  final List<Widget> actions;

  const RedditHomeChrome({
    super.key,
    required this.mode,
    required this.onMode,
    this.actions = const [],
  });

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return PluginHomeChrome(
      accent: RedditPlugin().brandColor,
      tabs: [
        PluginHomeTab(
          icon: Icons.home_outlined,
          label: l10n.plugin_reddit_feed_following,
          selected: mode == RedditFeedMode.following,
          onTap: () => onMode(RedditFeedMode.following),
        ),
        PluginHomeTab(
          icon: Icons.whatshot_outlined,
          label: l10n.plugin_reddit_feed_popular,
          selected: mode == RedditFeedMode.popular,
          onTap: () => onMode(RedditFeedMode.popular),
        ),
        PluginHomeTab(
          icon: Icons.public_outlined,
          label: l10n.plugin_reddit_feed_all,
          selected: mode == RedditFeedMode.all,
          onTap: () => onMode(RedditFeedMode.all),
        ),
      ],
      actions: actions,
    );
  }
}
