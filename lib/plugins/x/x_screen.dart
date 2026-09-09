import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/home/_for_you.dart';
import 'package:xta/home/home_account_filter.dart';
import 'package:xta/home/home_selection_store.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/tweet/paginated_tweet_list.dart';

/// The X source retains the established For you reader and its local state.
class XTimelineView extends StatelessWidget {
  final TweetFeedController feed;
  final int revision;
  const XTimelineView({super.key, required this.feed, this.revision = 0});

  @override
  Widget build(BuildContext context) => Column(
    children: [
      PluginHomeChrome(
        title: L10n.of(context).source_x,
        mark: const Icon(Icons.close),
        tabs: [
          PluginHomeTab(
            icon: Icons.auto_awesome_outlined,
            label: L10n.of(context).foryou,
            selected: true,
            onTap: () {},
          ),
        ],
      ),
      Expanded(
        child: ForYouTweets(
          feed,
          key: ValueKey(revision),
          type: 'profile',
          includeReplies: false,
          includePluginPosts: false,
          pref: PrefService.of(context, listen: false),
        ),
      ),
    ],
  );
}

class XScreen extends StatefulWidget {
  final ScrollController scrollController;
  const XScreen({super.key, required this.scrollController});
  @override
  State<XScreen> createState() => _XScreenState();
}

class _XScreenState extends State<XScreen> {
  final _revision = HomeSelectionStore(0);
  TweetFeedController _feed = TweetFeedController();

  void _refresh() {
    if (!mounted) return;
    final previous = _feed;
    _feed = TweetFeedController();
    _revision.select(_revision.state + 1);
    WidgetsBinding.instance.addPostFrameCallback((_) => previous.dispose());
  }

  @override
  void dispose() {
    _feed.dispose();
    _revision.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: Text(L10n.of(context).source_x),
      actions: [
        IconButton(
          tooltip: MaterialLocalizations.of(context).refreshIndicatorSemanticLabel,
          icon: const Icon(Icons.refresh),
          onPressed: _refresh,
        ),
        IconButton(
          tooltip: L10n.of(context).home_feed_accounts,
          icon: const Icon(Icons.manage_accounts_outlined),
          onPressed: () => showHomeAccountFilterSheet(context, onChanged: _refresh),
        ),
      ],
    ),
    body: PrimaryScrollController(
      controller: widget.scrollController,
      child: PluginEmbedded(
        child: ScopedBuilder<HomeSelectionStore<int>, int>(
          store: _revision,
          onState: (_, revision) => XTimelineView(feed: _feed, revision: revision),
        ),
      ),
    ),
  );
}
