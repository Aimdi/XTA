import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/_feed.dart';
import 'package:xta/group/_feed_shell.dart';
import 'package:xta/group/feed_cache.dart';
import 'package:xta/group/feed_session_cache.dart';
import 'package:xta/group/feed_chunk_hash.dart';
import 'package:xta/group/group_members.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/group/group_switcher.dart';
import 'package:xta/tweet/cached_tweet_list.dart';
import 'package:xta/tweet/tweet_context_scope.dart';
import 'package:xta/tweet/tweet_skeleton.dart';
import 'package:xta/ui/errors.dart';
import 'package:provider/provider.dart';
import 'package:xta/utils/iterables.dart';
import 'package:quiver/iterables.dart';

export 'package:xta/group/feed_chunk_hash.dart' show feedChunkSize;

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
    _current ??=
        ModalRoute.of(context)!.settings.arguments as GroupScreenArguments;
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
    setState(
      () => _current = GroupScreenArguments(id: group.id, name: group.name),
    );
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

  const SubscriptionGroupScreenContent({
    super.key,
    required this.id,
    this.cacheKey,
    this.mediaOnly = false,
  });

  @override
  State<SubscriptionGroupScreenContent> createState() =>
      _SubscriptionGroupScreenContentState();
}

class _SubscriptionGroupScreenContentState
    extends State<SubscriptionGroupScreenContent> {
  // Cached tweets shown while the group's subscriptions load, so the feed
  // reveals its content instead of a full-screen spinner on cold start.
  CachedChains? _preview;

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
    try {
      var repository = await Repository.readOnly();
      var cached = await readAllCachedChains(repository);
      if (!mounted) return;
      setState(() => _preview = cached);
    } catch (_) {
      // A bad cached chunk must not take the first Following frame down.
    }
  }

  Widget _loadingView() {
    var preview = _preview;
    if (preview != null && preview.chains.isNotEmpty) {
      return TweetContextScope(child: CachedTweetList(preview.chains));
    }
    // Post-shaped placeholders, like the paginated list's own first page — a
    // centred spinner was the one loading state left that didn't look like the
    // feed it was standing in for.
    return const TweetFeedSkeleton();
  }

  @override
  Widget build(BuildContext context) {
    return ScopedBuilder<GroupModel, SubscriptionGroupGet>(
      store: context.read<GroupModel>(),
      onLoading: (_) => _loadingView(),
      onError: (_, error) => ScaffoldErrorWidget(
        error: error,
        stackTrace: null,
        prefix: L10n.current.unable_to_load_the_group,
      ),
      onState: (_, group) {
        // TODO: This is pretty gross. Figure out how to have a "no data" state
        if (group.id.isEmpty) {
          return _loadingView();
        }
        // A group leaves each filter unset (null) to follow the global default.
        final prefs = PrefService.of(context, listen: false);
        final includeReplies =
            group.includeReplies ??
            prefs.get<bool>(optionGlobalIncludeReplies) ??
            true;
        final includeRetweets =
            group.includeRetweets ??
            prefs.get<bool>(optionGlobalIncludeRetweets) ??
            true;

        // Split the users into chunks, oldest first, to prevent thrashing of all groups when a new user is added
        final filteredUsers = group.id == '-1'
            ? group.subscriptions.where((elm) => elm.inFeed)
            : group.subscriptions;
        final members = filteredUsers
            .sorted((a, b) => a.createdAt.compareTo(b.createdAt))
            .toList();

        // Members belonging to a plugin are not searched on X: each source has
        // its own pagination, and leaving one in a search query puts a dangling
        // `OR` in it — or worse, searches `from:flutter` and paints an empty
        // tweet where a Reddit card should be.
        final split = splitGroupMembers(members);
        final pluginMembers = split.pluginMembers;
        final users = split.xMembers;

        var chunks = partition(users, feedChunkSize)
            .map(
              (e) => SubscriptionGroupFeedChunk(
                e,
                includeReplies,
                includeRetweets,
              ),
            )
            .toList();

        return SubscriptionGroupFeed(
          group: group,
          chunks: chunks,
          pluginMembers: pluginMembers,
          includeReplies: includeReplies,
          includeRetweets: includeRetweets,
          mediaOnly: widget.mediaOnly,
          cacheKey: widget.cacheKey,
          initialPreview: _preview?.chains,
          initialPreviewCachedAt: _preview?.cachedAt,
        );
      },
    );
  }
}

class SubscriptionGroupFeedChunk {
  final List<Subscription> users;
  final bool includeReplies;
  final bool includeRetweets;

  SubscriptionGroupFeedChunk(
    this.users,
    this.includeReplies,
    this.includeRetweets,
  );

  String get hash => feedChunkHash(
    users.map((e) => e.id).toList(),
    includeReplies: includeReplies,
    includeRetweets: includeRetweets,
  );
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

  const SubscriptionGroupScreen({
    super.key,
    required this.scrollController,
    required this.id,
    required this.name,
    this.actions,
    this.cacheKey,
    this.onSwitchGroup,
  });

  @override
  State<SubscriptionGroupScreen> createState() =>
      _SubscriptionGroupScreenState();
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
        return GroupSwitcherTitle(
          name: widget.name,
          currentGroupId: widget.id,
          onSwitch: onSwitch,
        );
      },
      bodyBuilder: (context) => SubscriptionGroupScreenContent(
        id: widget.id,
        cacheKey: widget.cacheKey,
        mediaOnly: _mediaOnly,
      ),
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
