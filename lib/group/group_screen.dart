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
import 'package:quax/group/_settings.dart';
import 'package:quax/group/feed_cache.dart';
import 'package:quax/group/feed_session_cache.dart';
import 'package:quax/group/group_chrome.dart';
import 'package:quax/group/group_custom_settings.dart';
import 'package:quax/group/group_model.dart';
import 'package:quax/group/group_switcher.dart';
import 'package:quax/group/group_view_store.dart';
import 'package:quax/subscriptions/_groups_edit.dart';
import 'package:quax/tweet/cached_tweet_list.dart';
import 'package:quax/tweet/tweet_context_scope.dart';
import 'package:quax/tweet/tweet_chrome.dart';
import 'package:quax/tweet/tweet_skeleton.dart';
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
  GroupRouteStore? _routeStore;

  @override
  void initState() {
    super.initState();
    _scrollController = ScrollController();
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final args =
        ModalRoute.of(context)!.settings.arguments as GroupScreenArguments;
    _routeStore ??= GroupRouteStore((id: args.id, name: args.name));
  }

  @override
  void dispose() {
    _scrollController.dispose();
    _routeStore?.destroy();
    super.dispose();
  }

  void _switchTo(SubscriptionGroup group) {
    if (_scrollController.hasClients) {
      _scrollController.jumpTo(0);
    }
    _routeStore!.switchTo(group);
  }

  @override
  Widget build(BuildContext context) {
    return ScopedBuilder<GroupRouteStore, GroupRouteSelection>(
      store: _routeStore!,
      onState: (_, route) => SubscriptionGroupScreen(
        key: ValueKey(route.id),
        scrollController: _scrollController,
        id: route.id,
        name: route.name,
        cacheKey: route.id,
        onSwitchGroup: _switchTo,
        actions: const [],
      ),
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
  late final GroupPreviewStore _previewStore;

  @override
  void initState() {
    super.initState();
    _previewStore = GroupPreviewStore();
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
    _previewStore.show(chains);
  }

  @override
  void dispose() {
    _previewStore.destroy();
    super.dispose();
  }

  Widget _loadingView(List<TweetChain>? preview) {
    if (preview != null && preview.isNotEmpty) {
      return TweetContextScope(child: CachedTweetList(preview));
    }
    return const TweetFeedSkeleton();
  }

  @override
  Widget build(BuildContext context) {
    return ScopedBuilder<GroupPreviewStore, List<TweetChain>?>(
      store: _previewStore,
      onState: (_, preview) =>
          ScopedBuilder<GroupModel, SubscriptionGroupGet>.transition(
            store: context.read<GroupModel>(),
            onLoading: (_) => _loadingView(preview),
            onError: (_, error) => ScaffoldErrorWidget(
              error: error,
              stackTrace: null,
              prefix: L10n.current.unable_to_load_the_group,
            ),
            onState: (_, group) {
              // Handle empty state with proper UI feedback
              if (group.id.isEmpty) {
                return TweetEmptyState(
                  message: L10n.of(context).group_not_found,
                );
              }
              // A group leaves each filter unset (null) to follow the global default.
              final prefs = PrefService.of(context);
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
              final members = filteredUsers.sorted(
                (a, b) => a.createdAt.compareTo(b.createdAt),
              );

              // Substack publications are members of the group but they are not
              // searched on X: they have their own source, and leaving them in a
              // search query would put an empty clause in it.
              final publications = members
                  .whereType<SubstackSubscription>()
                  .toList(growable: false);
              final subreddits = members.whereType<RedditSubscription>().toList(
                growable: false,
              );
              final users = members
                  .where(
                    (e) =>
                        e is! SubstackSubscription && e is! RedditSubscription,
                  )
                  .toList(growable: false);

              var chunks = partition(users, 16)
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
                publications: publications,
                subreddits: subreddits,
                includeReplies: includeReplies,
                includeRetweets: includeRetweets,
                mediaOnly: widget.mediaOnly,
                cacheKey: widget.cacheKey,
                initialPreview: preview,
              );
            },
          ),
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

  String get hash {
    var toHash =
        '${users.map((e) => e.id).join(', ')}$includeReplies$includeRetweets';

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
  late final GroupMediaModeStore _mediaStore;

  @override
  void initState() {
    super.initState();
    final cacheKey = widget.cacheKey;
    final initial = cacheKey == null
        ? false
        : context.read<FeedSessionCache>().readMediaOnly(cacheKey);
    _mediaStore = GroupMediaModeStore(initial);
  }

  @override
  void dispose() {
    _mediaStore.destroy();
    super.dispose();
  }

  void _toggleMediaOnly(bool mediaOnly) {
    _mediaStore.toggle();
    final cacheKey = widget.cacheKey;
    if (cacheKey != null) {
      context.read<FeedSessionCache>().saveMediaOnly(cacheKey, !mediaOnly);
    }
  }

  Future<void> _selectOrder(GroupModel model, int order) async {
    if (order == 2) {
      await model.toggleSubscriptionGroupCustom(true);
    } else {
      await model.toggleSubscriptionGroupPopular(order == 1);
    }
  }

  void _openCustomSettings(BuildContext context, GroupModel model) {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => GroupCustomSettingsScreen(model: model),
      ),
    );
  }

  void _handleOverflow(
    BuildContext context,
    GroupModel model,
    GroupOverflowAction action,
  ) {
    switch (action) {
      case GroupOverflowAction.filters:
        showFeedSettings(context, model);
        return;
      case GroupOverflowAction.subscriptions:
        openSubscriptionGroupDialog(
          context,
          widget.id,
          model.state.name,
          model.state.icon,
        );
        return;
      case GroupOverflowAction.settings:
        Navigator.pushNamed(context, routeSettings);
        return;
    }
  }

  @override
  Widget build(BuildContext context) {
    return ScopedBuilder<GroupMediaModeStore, bool>(
      store: _mediaStore,
      onState: (_, mediaOnly) => GroupFeedShell(
        scrollController: widget.scrollController,
        groupId: widget.id,
        usesFeedCache: widget.cacheKey != null,
        titleBuilder: (context) {
          final onSwitch = widget.onSwitchGroup;
          return GroupSwitcherTitle(
            name: widget.name,
            currentGroupId: widget.id,
            onSwitch: onSwitch,
          );
        },
        bottomBuilder: (context) => _GroupFeedControls(
          model: context.read<GroupModel>(),
          mediaOnly: mediaOnly,
          onOrderSelected: (order) =>
              _selectOrder(context.read<GroupModel>(), order),
          onMediaToggle: () => _toggleMediaOnly(mediaOnly),
          onCustomSettings: () =>
              _openCustomSettings(context, context.read<GroupModel>()),
        ),
        bodyBuilder: (context) => SubscriptionGroupScreenContent(
          id: widget.id,
          cacheKey: widget.cacheKey,
          mediaOnly: mediaOnly,
        ),
        actionsBuilder: (context) {
          final model = context.read<GroupModel>();
          return [
            ...defaultGroupActions(
              context,
              model: model,
              scrollToTopController: widget.scrollController,
              showMore: false,
              showSettings: false,
              extra: widget.actions ?? const [],
            ),
            GroupOverflowButton(
              onSelected: (action) => _handleOverflow(context, model, action),
            ),
          ];
        },
      ),
    );
  }
}

class _GroupFeedControls extends StatelessWidget
    implements PreferredSizeWidget {
  final GroupModel model;
  final bool mediaOnly;
  final ValueChanged<int> onOrderSelected;
  final VoidCallback onMediaToggle;
  final VoidCallback onCustomSettings;

  const _GroupFeedControls({
    required this.model,
    required this.mediaOnly,
    required this.onOrderSelected,
    required this.onMediaToggle,
    required this.onCustomSettings,
  });

  @override
  Size get preferredSize => const Size.fromHeight(kGroupControlBarHeight);

  @override
  Widget build(BuildContext context) {
    return ScopedBuilder<GroupModel, SubscriptionGroupGet>(
      store: model,
      onState: (_, group) => group.id.isEmpty
          ? const SizedBox(height: kGroupControlBarHeight)
          : GroupFeedControlBar(
              group: group,
              mediaOnly: mediaOnly,
              onOrderSelected: onOrderSelected,
              onMediaToggle: onMediaToggle,
              onCustomSettings: onCustomSettings,
            ),
    );
  }
}
