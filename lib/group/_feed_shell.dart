import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:quax/constants.dart';
import 'package:quax/database/entities.dart';
import 'package:quax/group/_settings.dart';
import 'package:quax/group/feed_refresh_controller.dart';
import 'package:quax/group/group_model.dart';
import 'package:quax/subscriptions/users_model.dart';
import 'package:quax/generated/l10n.dart';
import 'package:quax/ui/reader_chrome.dart';
import 'package:quax/ui/scroll_to_top.dart';

class GroupFeedShell extends StatefulWidget {
  final ScrollController scrollController;
  final String groupId;
  final WidgetBuilder titleBuilder;
  final WidgetBuilder bodyBuilder;
  final List<Widget> Function(BuildContext) actionsBuilder;
  final PreferredSizeWidget? Function(BuildContext)? bottomBuilder;
  // Whether the body's feed keeps its PagingController in the FeedSessionCache.
  // Only then does a subscription change require remounting the body (to drop
  // the just-invalidated cached controller); other feeds refresh on their own
  // when their group state actually changes.
  final bool usesFeedCache;

  const GroupFeedShell({
    super.key,
    required this.scrollController,
    required this.groupId,
    required this.titleBuilder,
    required this.bodyBuilder,
    required this.actionsBuilder,
    this.bottomBuilder,
    this.usesFeedCache = false,
  });

  @override
  State<GroupFeedShell> createState() => _GroupFeedShellState();
}

class _GroupFeedShellState extends State<GroupFeedShell>
    with AutomaticKeepAliveClientMixin<GroupFeedShell> {
  late final GroupModel _groupModel;
  final FeedRefreshController _feedRefreshController = FeedRefreshController();
  int _refreshCounter = 0;
  // Cached refs captured in didChangeDependencies — accessing the InheritedWidget
  // tree via context.read in dispose() triggers a framework warning, since
  // ancestors may already be unmounted by then.
  SubscriptionsModel? _subscriptionsModel;
  GroupsModel? _groupsModel;

  late final String _callbackKey =
      'GroupFeedShell-${widget.groupId}-${identityHashCode(this)}';

  @override
  bool get wantKeepAlive => true;

  @override
  void initState() {
    super.initState();
    _groupModel = GroupModel(widget.groupId)..loadGroup();
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final newSubs = context.read<SubscriptionsModel>();
    final newGroups = context.read<GroupsModel>();
    if (!identical(newSubs, _subscriptionsModel) ||
        !identical(newGroups, _groupsModel)) {
      _subscriptionsModel?.removeReloadListener(_callbackKey);
      _groupsModel?.removeReloadListener(_callbackKey);
      _subscriptionsModel = newSubs;
      _groupsModel = newGroups;
      _subscriptionsModel!.addReloadListener(_callbackKey, _onReload);
      _groupsModel!.addReloadListener(_callbackKey, _onReload);
    }
  }

  // What the feed actually shows; a reload only warrants remounting the body
  // when this changes, otherwise following someone unrelated would needlessly
  // reload the open timeline.
  String _fingerprint(SubscriptionGroupGet group) {
    final members = group.subscriptions
        .map((s) => '${s.id}:${s.inFeed}')
        .join(',');
    return '$members|${group.includeReplies}|${group.includeRetweets}|${group.popular}|${group.custom}|${group.customRules.cacheKey}';
  }

  // Triggered when subscriptions or group memberships change. A single user
  // action can fire this several times in a row (subscriptions and groups both
  // reload), so the reaction is debounced into one refresh. The body is only
  // remounted for cache-backed feeds whose content actually changed; everything
  // else just reloads its group state and the feed decides on its own.
  void _onReload() {
    _reloadDebounce?.cancel();
    _reloadDebounce = Timer(const Duration(milliseconds: 150), () async {
      if (!mounted) return;
      final before = _fingerprint(_groupModel.state);
      await _groupModel.loadGroup();
      if (!mounted) return;
      setState(() {
        if (widget.usesFeedCache && _fingerprint(_groupModel.state) != before) {
          _refreshCounter++;
        }
      });
    });
  }

  Timer? _reloadDebounce;

  @override
  void dispose() {
    _reloadDebounce?.cancel();
    _subscriptionsModel?.removeReloadListener(_callbackKey);
    _groupsModel?.removeReloadListener(_callbackKey);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    return XtaSystemBars(
      child: Provider<GroupModel>.value(
        value: _groupModel,
        builder: (context, child) {
          return Provider<FeedRefreshController>.value(
            value: _feedRefreshController,
            child: NestedScrollView(
              controller: widget.scrollController,
              floatHeaderSlivers: true,
              headerSliverBuilder: (context, innerBoxIsScrolled) {
                return [
                  SliverAppBar(
                    backgroundColor: Theme.of(context).colorScheme.surface,
                    surfaceTintColor: Colors.transparent,
                    elevation: 0,
                    scrolledUnderElevation: 0,
                    pinned: false,
                    snap: true,
                    floating: true,
                    centerTitle: false,
                    titleSpacing: 16,
                    title: widget.titleBuilder(context),
                    actions: widget.actionsBuilder(context),
                    bottom: widget.bottomBuilder?.call(context),
                  ),
                ];
              },
              body: KeyedSubtree(
                key: ValueKey(_refreshCounter),
                child: widget.bodyBuilder(context),
              ),
            ),
          );
        },
      ),
    );
  }
}

/// Builds the standard action-bar icons shared by group feeds:
/// optional "more" (group settings), optional "scroll-to-top", refresh, and
/// the global settings button.
List<Widget> defaultGroupActions(
  BuildContext context, {
  required GroupModel model,
  ScrollController? scrollToTopController,
  bool showMore = true,
  bool showRefresh = true,
  bool showSettings = true,
  VoidCallback? onRefresh,
  List<Widget> extra = const [],
}) {
  return [
    if (showMore)
      IconButton(
        tooltip: L10n.of(context).filters,
        icon: const Icon(Icons.build_outlined),
        onPressed: () => showFeedSettings(context, model),
      ),
    if (scrollToTopController != null)
      IconButton(
        tooltip: MaterialLocalizations.of(context).reorderItemToStart,
        icon: const Icon(Icons.arrow_upward),
        onPressed: () async =>
            await scrollToTop(context, scrollToTopController),
      ),
    if (showRefresh)
      IconButton(
        tooltip: MaterialLocalizations.of(
          context,
        ).refreshIndicatorSemanticLabel,
        icon: const Icon(Icons.refresh),
        onPressed:
            onRefresh ??
            () async => await context.read<FeedRefreshController>().refresh(),
      ),
    if (showSettings)
      IconButton(
        tooltip: L10n.of(context).settings,
        icon: const Icon(Icons.settings),
        onPressed: () => Navigator.pushNamed(context, routeSettings),
      ),
    ...extra,
  ];
}
