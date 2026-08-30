import 'package:crypto/crypto.dart';
import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:quax/client/client.dart';
import 'package:quax/constants.dart';
import 'package:quax/database/entities.dart';
import 'package:quax/database/repository.dart';
import 'package:quax/generated/l10n.dart';
import 'package:quax/group/_feed.dart';
import 'package:quax/group/_feed_shell.dart';
import 'package:quax/group/feed_cache.dart';
import 'package:quax/group/feed_session_cache.dart';
import 'package:quax/group/group_model.dart';
import 'package:quax/group/group_switcher.dart';
import 'package:quax/tweet/cached_tweet_list.dart';
import 'package:quax/tweet/tweet_context_scope.dart';
import 'package:quax/ui/errors.dart';
import 'package:provider/provider.dart';
import 'package:quax/utils/iterables.dart';
import 'package:quiver/iterables.dart';

class GroupScreenArguments {
  final String id;
  final String name;

  GroupScreenArguments({required this.id, required this.name});

  @override
  String toString() {
    return 'GroupScreenArguments{id: $id, name: $name}';
  }
}

class GroupScreen extends StatefulWidget {
  const GroupScreen({super.key});

  @override
  State<GroupScreen> createState() => _GroupScreenState();
}

class _GroupScreenState extends State<GroupScreen> {
  late final ScrollController _scrollController;

  // Which group this route currently shows. Switching from the title swaps it
  // in place rather than pushing another route, so Back always returns to
  // wherever the first group was opened from, however many groups were visited.
  GroupScreenArguments? _current;

  @override
  void initState() {
    super.initState();
    _scrollController = ScrollController();
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _current ??= ModalRoute.of(context)!.settings.arguments as GroupScreenArguments;
  }

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  void _switchTo(SubscriptionGroup group) {
    if (_scrollController.hasClients) {
      _scrollController.jumpTo(0);
    }
    setState(() => _current = GroupScreenArguments(id: group.id, name: group.name));
  }

  @override
  Widget build(BuildContext context) {
    final args = _current!;
    return SubscriptionGroupScreen(
      // A new group needs its own shell state, model and feed; keying by id is
      // what makes the swap clean instead of half-updating the old one.
      key: ValueKey(args.id),
      scrollController: _scrollController,
      id: args.id,
      name: args.name,
      // Pushed routes persist their feed state across pop/push via the cache.
      // The cache key matches the groupId so re-pushing the same group restores
      // the previous tweets and scroll offset.
      cacheKey: args.id,
      onSwitchGroup: _switchTo,
      actions: const [],
    );
  }
}

class SubscriptionGroupScreenContent extends StatefulWidget {
  final String id;
  final String? cacheKey;
  final bool mediaOnly;

  const SubscriptionGroupScreenContent({super.key, required this.id, this.cacheKey, this.mediaOnly = false});

  @override
  State<SubscriptionGroupScreenContent> createState() => _SubscriptionGroupScreenContentState();
}

class _SubscriptionGroupScreenContentState extends State<SubscriptionGroupScreenContent> {
  // Cached tweets shown while the group's subscriptions load, so the feed
  // reveals its content instead of a full-screen spinner on cold start.
  List<TweetChain>? _preview;

  @override
  void initState() {
    super.initState();
    // Only the combined "All"/Following feed (id '-1') can preview every cached
    // chunk up front; a specific group needs its own chunk hashes (unknown until
    // loadGroup finishes) to avoid showing tweets from other groups.
    if (widget.id == '-1') {
      _loadPreview();
    }
  }

  Future<void> _loadPreview() async {
    var repository = await Repository.readOnly();
    var chains = await readAllCachedChains(repository);
    if (!mounted) return;
    setState(() => _preview = chains);
  }

  Widget _loadingView() {
    var preview = _preview;
    if (preview != null && preview.isNotEmpty) {
      return TweetContextScope(child: CachedTweetList(preview));
    }
    return const Center(child: CircularProgressIndicator());
  }

  @override
  Widget build(BuildContext context) {
    return ScopedBuilder<GroupModel, SubscriptionGroupGet>.transition(
      store: context.read<GroupModel>(),
      onLoading: (_) => _loadingView(),
      onError: (_, error) =>
          ScaffoldErrorWidget(error: error, stackTrace: null, prefix: L10n.current.unable_to_load_the_group),
      onState: (_, group) {
        // Handle empty state with proper UI feedback
        if (group.id.isEmpty) {
          return Center(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const Icon(Icons.group_outlined, size: 48, color: Colors.grey),
                const SizedBox(height: 16),
                Text(
                  L10n.of(context).group_not_found,
                  style: Theme.of(context).textTheme.bodyLarge,
                ),
                const SizedBox(height: 8),
                Text(
                  L10n.of(context).try_creating_new_group,
                  style: Theme.of(context).textTheme.bodySmall,
                ),
              ],
            ),
          );
        }
        // A group leaves each filter unset (null) to follow the global default.
        final prefs = PrefService.of(context);
        final includeReplies = group.includeReplies ?? prefs.get<bool>(optionGlobalIncludeReplies) ?? true;
        final includeRetweets = group.includeRetweets ?? prefs.get<bool>(optionGlobalIncludeRetweets) ?? true;

        // Split the users into chunks, oldest first, to prevent thrashing of all groups when a new user is added
        final filteredUsers = group.id == '-1' ? group.subscriptions.where((elm) => elm.inFeed) : group.subscriptions;
        final members = filteredUsers.sorted((a, b) => a.createdAt.compareTo(b.createdAt)).toList();

        // Substack publications are members of the group but they are not
        // searched on X: they have their own source, and leaving them in a
        // search query would put an empty clause in it.
        final publications = members.whereType<SubstackSubscription>().toList(growable: false);
        final subreddits = members.whereType<RedditSubscription>().toList(growable: false);
        final users =
            members.where((e) => e is! SubstackSubscription && e is! RedditSubscription).toList(growable: false);

        var chunks = partition(users, 16)
            .map((e) => SubscriptionGroupFeedChunk(e, includeReplies, includeRetweets))
            .toList();

        return SubscriptionGroupFeed(
          group: group,
          chunks: chunks,
          publications: publications,
          subreddits: subreddits,
          includeReplies: includeReplies,
          includeRetweets: includeRetweets,
          mediaOnly: widget.mediaOnly,
          cacheKey: widget.cacheKey,
          initialPreview: _preview,
        );
      },
    );
  }
}

class SubscriptionGroupFeedChunk {
  final List<Subscription> users;
  final bool includeReplies;
  final bool includeRetweets;

  SubscriptionGroupFeedChunk(this.users, this.includeReplies, this.includeRetweets);

  String get hash {
    var toHash = '${users.map((e) => e.id).join(', ')}$includeReplies$includeRetweets';

    return sha1.convert(toHash.codeUnits).toString();
  }
}

class SubscriptionGroupScreen extends StatefulWidget {
  final ScrollController scrollController;
  final String id;
  final String name;
  final List<Widget>? actions;
  // Forwarded to SubscriptionGroupFeed — see its docs. Null disables caching.
  final String? cacheKey;

  /// When set, the title becomes a group picker and this is called with the
  /// chosen group. Null leaves the title as plain text — the home Following tab
  /// already uses its own feed-tab dropdown there.
  final ValueChanged<SubscriptionGroup>? onSwitchGroup;

  const SubscriptionGroupScreen(
      {super.key,
      required this.scrollController,
      required this.id,
      required this.name,
      this.actions,
      this.cacheKey,
      this.onSwitchGroup});

  @override
  State<SubscriptionGroupScreen> createState() => _SubscriptionGroupScreenState();
}

class _SubscriptionGroupScreenState extends State<SubscriptionGroupScreen> {
  bool _mediaOnly = false;

  @override
  void initState() {
    super.initState();
    // Restore the filter together with the cached feed it was applied to, so a
    // re-pushed route never shows filtered tweets under an unfiltered toggle.
    final cacheKey = widget.cacheKey;
    if (cacheKey != null) {
      _mediaOnly = context.read<FeedSessionCache>().readMediaOnly(cacheKey);
    }
  }

  void _toggleMediaOnly() {
    setState(() => _mediaOnly = !_mediaOnly);
    final cacheKey = widget.cacheKey;
    if (cacheKey != null) {
      context.read<FeedSessionCache>().saveMediaOnly(cacheKey, _mediaOnly);
    }
  }

  Widget _mediaOnlyToggle(BuildContext context) => IconButton(
        isSelected: _mediaOnly,
        icon: const Icon(Icons.photo_library_outlined),
        selectedIcon: const Icon(Icons.photo_library),
        tooltip: L10n.of(context).only_show_posts_with_media,
        onPressed: _toggleMediaOnly,
      );

  @override
  Widget build(BuildContext context) {
    return GroupFeedShell(
      scrollController: widget.scrollController,
      groupId: widget.id,
      usesFeedCache: widget.cacheKey != null,
      titleBuilder: (context) {
        final onSwitch = widget.onSwitchGroup;
        if (onSwitch == null) {
          return Text(widget.name);
        }
        return GroupSwitcherTitle(name: widget.name, currentGroupId: widget.id, onSwitch: onSwitch);
      },
      bodyBuilder: (context) =>
          SubscriptionGroupScreenContent(id: widget.id, cacheKey: widget.cacheKey, mediaOnly: _mediaOnly),
      actionsBuilder: (context) => [
        _mediaOnlyToggle(context),
        ...defaultGroupActions(
          context,
          model: context.read<GroupModel>(),
          scrollToTopController: widget.scrollController,
          showSettings: false,
          extra: widget.actions ?? const [],
        ),
      ],
    );
  }
}
