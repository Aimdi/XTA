import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/client/client.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/group_discovery.dart';
import 'package:xta/group/group_discovery_follow.dart';
import 'package:xta/group/group_discovery_sources.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/bluesky/bluesky_post_card.dart';
import 'package:xta/plugins/bluesky/bluesky_profile_screen.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_post_card.dart';
import 'package:xta/plugins/mastodon/mastodon_profile_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_grid.dart';
import 'package:xta/plugins/pixiv/pixiv_image.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_user_screen.dart';
import 'package:xta/plugins/plugin_marks.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/profile/profile.dart';
import 'package:xta/tweet/tweet.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/tweet/tweet_context_scope.dart';
import 'package:xta/tweet/tweet_skeleton.dart';
import 'package:xta/ui/empty_pane.dart';
import 'package:xta/ui/reader_failure.dart';
import 'package:xta/user.dart';
import 'package:xta/utils/ai_client.dart';
import 'package:xta/utils/reader_value_store.dart';
import 'package:xta/utils/urls.dart';

Future<void> openGroupDiscovery(BuildContext context, {required String id, required String name, bool useAi = false}) =>
    Navigator.push(
      context,
      MaterialPageRoute<void>(
        builder: (_) => GroupDiscoveryScreen(id: id, name: name, useAi: useAi),
      ),
    );

/// Loads [store] for [group] from this context's sources; a setup failure
/// lands on the store like any other.
Future<void> loadGroupDiscovery(BuildContext context, GroupDiscoveryStore store, SubscriptionGroupGet group) async {
  try {
    await store.load(
      sources: groupDiscoverySources(context, group),
      followed: currentDiscoveryFollowedIds(context, group.subscriptions),
      groupName: group.name,
      groupId: group.id,
    );
  } catch (error) {
    store.fail(error);
  }
}

class GroupDiscoveryScreen extends StatefulWidget {
  final String id;
  final String name;
  final bool useAi;
  const GroupDiscoveryScreen({super.key, required this.id, required this.name, this.useAi = false});

  @override
  State<GroupDiscoveryScreen> createState() => _GroupDiscoveryScreenState();
}

class _GroupDiscoveryScreenState extends State<GroupDiscoveryScreen> {
  late final GroupModel _group = GroupModel(widget.id, prefs: PrefService.of(context, listen: false))..loadGroup();

  @override
  void dispose() {
    _group.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: Text('${L10n.of(context).discover} · ${widget.name}')),
    body: ScopedBuilder<GroupModel, SubscriptionGroupGet>(
      store: _group,
      onLoading: (_) => const Center(child: CircularProgressIndicator()),
      onError: (_, _) => Center(
        child: FilledButton.tonal(onPressed: () => _group.loadGroup(), child: Text(L10n.of(context).retry)),
      ),
      onState: (_, group) => group.id.isEmpty
          ? const Center(child: CircularProgressIndicator())
          : GroupDiscoveryPane(group: group, useAi: widget.useAi, showAiChip: true),
    ),
  );
}

class GroupDiscoveryPane extends StatefulWidget {
  final SubscriptionGroupGet group;
  final bool useAi;

  /// A store owned by the screen around the pane, which keeps it across
  /// rebuilds; without one the pane makes its own with [createStore].
  final GroupDiscoveryStore? store;
  final GroupDiscoveryStore Function() createStore;

  /// How "Add to this group" writes; tests hand in fakes.
  final DiscoveryGroupWriter? writer;

  /// Offers "Rank with AI" in the header. The group screen has the app-bar
  /// sparkle for that and leaves the row to the rows.
  final bool showAiChip;

  const GroupDiscoveryPane({
    super.key,
    required this.group,
    this.useAi = false,
    this.store,
    this.createStore = GroupDiscoveryStore.new,
    this.writer,
    this.showAiChip = false,
  });

  @override
  State<GroupDiscoveryPane> createState() => _GroupDiscoveryPaneState();
}

class _GroupDiscoveryPaneState extends State<GroupDiscoveryPane> {
  late final GroupDiscoveryStore _model = widget.store ?? widget.createStore();
  final _added = DiscoveryAddStore();
  final _expanded = ReaderValueStore<Set<String>>(const {});
  final _emptyScroll = ScrollController();

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _load();
    });
  }

  @override
  void didUpdateWidget(GroupDiscoveryPane old) {
    super.didUpdateWidget(old);
    // A membership change reaches the pane as a new group; the rows just added
    // keep saying so instead of vanishing.
    if (!identical(old.group.subscriptions, widget.group.subscriptions)) _excludeFollowed();
  }

  @override
  void dispose() {
    if (widget.store == null) _model.destroy();
    _added.destroy();
    _expanded.destroy();
    _emptyScroll.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    await loadGroupDiscovery(context, _model, widget.group);
    if (widget.useAi && mounted) await _rerank();
  }

  Future<void> _rerank() => _model.rerankWithAi(AiConfig.fromPrefs(PrefService.of(context, listen: false)));

  void _excludeFollowed() =>
      _model.excludeFollowed(
        currentDiscoveryFollowedIds(context, widget.group.subscriptions),
        keep: _added.state.added,
      );

  Future<void> _feedbackAction(Future<void> Function() action, {String? notice}) async {
    final messenger = ScaffoldMessenger.of(context);
    final l10n = L10n.of(context);
    try {
      await action();
    } catch (_) {
      messenger.showSnackBar(SnackBar(content: Text(l10n.oops_something_went_wrong)));
      return;
    }
    if (notice == null) return;
    messenger.showSnackBar(
      SnackBar(
        content: Text(notice),
        action: SnackBarAction(
          label: l10n.reader_undo,
          onPressed: () => _feedbackAction(() => _model.resetFeedback(undo: true)),
        ),
      ),
    );
  }

  Future<void> _add(DiscoveryAccount account) async {
    final messenger = ScaffoldMessenger.of(context);
    final l10n = L10n.of(context);
    final writer = widget.writer ?? ContextDiscoveryGroupWriter(context);
    final added = await _added.add(account.key, () => addDiscoveryToGroup(writer, account, widget.group.id));
    if (!added) messenger.showSnackBar(SnackBar(content: Text(l10n.group_add_member_failed)));
  }

  Future<void> _onAction(DiscoveryAccount account, _RowAction action) => switch (action) {
    _RowAction.moreLike => _feedbackAction(() => _model.feedback(account, 1)),
    _RowAction.lessLike => _feedbackAction(
      () => _model.feedback(account, -1),
      notice: L10n.of(context).group_discovery_feedback_saved,
    ),
    _RowAction.hide => _feedbackAction(
      () => _model.feedback(account, 0),
      notice: L10n.of(context).group_discovery_feedback_saved,
    ),
    _RowAction.otherGroups => pickDiscoveryGroups(context, account, widget.group.id),
    _RowAction.openProfile => _openProfile(account),
    _RowAction.openPost => openUri(context, account.link),
  };

  Future<void> _openProfile(DiscoveryAccount account) async {
    await openDiscoveryProfile(context, account);
    if (mounted) _excludeFollowed();
  }

  void _toggleExpanded(String key) {
    final next = {..._expanded.state};
    if (!next.remove(key)) next.add(key);
    _expanded.update(next);
  }

  Future<void> _showInfo() => showDialog<void>(
    context: context,
    builder: (context) => AlertDialog(
      title: Text(L10n.of(context).discover),
      content: Text(L10n.of(context).group_discovery_description),
      actions: [TextButton(onPressed: () => Navigator.pop(context), child: Text(L10n.of(context).close))],
    ),
  );

  @override
  Widget build(BuildContext context) {
    // GroupFeedShell embeds this pane without a Scaffold on pushed routes.
    // Ink reactions in the rows and post cards need their own Material ancestor.
    return Material(
      color: Theme.of(context).scaffoldBackgroundColor,
      child: ScopedBuilder<GroupDiscoveryStore, GroupDiscoveryState>(
        store: _model,
        onLoading: (_) => const _DiscoverySkeleton(),
        onError: (_, error) => EmptyPane(
          icon: Icons.error_outline,
          message: readFailureMessage(L10n.of(context), error),
          scrollController: _emptyScroll,
          action: FilledButton.tonal(onPressed: _load, child: Text(L10n.of(context).retry)),
        ),
        onState: (_, state) => RefreshIndicator(
          onRefresh: _load,
          child: TweetContextScope(
            child: ScopedBuilder<DiscoveryAddStore, DiscoveryAddState>(
              store: _added,
              onState: (_, added) => ScopedBuilder<ReaderValueStore<Set<String>>, Set<String>>(
                store: _expanded,
                onState: (context, expanded) => _body(context, state, added, expanded),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _body(BuildContext context, GroupDiscoveryState state, DiscoveryAddState added, Set<String> expanded) {
    if (state.accounts.isEmpty && !state.loadingMore) {
      final l10n = L10n.of(context);
      // Nothing read at all is a failure to report, not a group with quiet members.
      final nothingRead = state.failures.isNotEmpty && state.coverage.read == 0;
      return EmptyPane(
        icon: Icons.explore_outlined,
        message: nothingRead ? l10n.group_discovery_partial : l10n.group_discovery_empty,
        leading: _header(context, state, scanMore: false),
        leadingInset: 0,
        scrollController: _emptyScroll,
        action: TextButton(onPressed: _model.scanMore, child: Text(l10n.group_discovery_scan_more)),
      );
    }
    return ListView.builder(
      key: PageStorageKey('discovery-${widget.group.id}'),
      primary: false,
      physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.only(bottom: kTweetSpace6),
      itemCount: state.accounts.length + 1,
      itemBuilder: (context, index) =>
          index == 0 ? _header(context, state) : _row(state.accounts[index - 1], added, expanded),
    );
  }

  Widget _row(DiscoveryAccount account, DiscoveryAddState added, Set<String> expanded) => _DiscoveryRow(
    key: ValueKey(account.key),
    account: account,
    groupName: widget.group.name,
    added: added.added.contains(account.key),
    adding: added.adding.contains(account.key),
    expanded: expanded.contains(account.key),
    onAdd: () => _add(account),
    onToggle: () => _toggleExpanded(account.key),
    onOpenProfile: () => _openProfile(account),
    onAction: (action) => _onAction(account, action),
  );

  /// The status line with "Scan more" (unless the empty pane offers it), the
  /// header menu, the AI line and one notice per failed source.
  Widget _header(BuildContext context, GroupDiscoveryState state, {bool scanMore = true}) {
    final l10n = L10n.of(context);
    final chip = widget.showAiChip && AiConfig.fromPrefs(PrefService.of(context)).isConfigured;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.only(left: kTweetHorizontalPadding),
          child: Row(
            children: [
              Expanded(
                child: Text(
                  l10n.group_discovery_coverage(state.coverage.total, state.coverage.read),
                  style: tweetMetadataStyle(context),
                ),
              ),
              if (scanMore)
                TextButton(
                  onPressed: state.loadingMore ? null : _model.scanMore,
                  child: Text(l10n.group_discovery_scan_more),
                ),
              _HeaderMenu(
                canUndo: _model.canUndo,
                canReset: _model.hasFeedback,
                onInfo: _showInfo,
                onUndo: () => _feedbackAction(() => _model.resetFeedback(undo: true)),
                onReset: () => _feedbackAction(_model.resetFeedback),
              ),
            ],
          ),
        ),
        if (chip || state.usedAi || state.aiFailed) _aiLine(context, state, chip),
        for (final failure in state.failures.entries) _failure(context, failure.key, failure.value),
        if (state.loadingMore) const LinearProgressIndicator(minHeight: 2) else const SizedBox(height: 2),
        tweetHairlineDivider(context),
      ],
    );
  }

  /// A failed source, named by its mark: the compact notice itself only says
  /// which source inside its details.
  Widget _failure(BuildContext context, DiscoverySource source, Object error) => Row(
    children: [
      Padding(
        padding: const EdgeInsets.only(left: kTweetHorizontalPadding),
        child: discoverySourceMark(source, size: kTweetActionIconSize),
      ),
      Expanded(
        child: ReaderFailureNotice(
          compact: true,
          source: source.name,
          error: error,
          onRetry: _load,
          contextMessage: L10n.of(context).group_discovery_partial,
        ),
      ),
    ],
  );

  Widget _aiLine(BuildContext context, GroupDiscoveryState state, bool chip) {
    final l10n = L10n.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(kTweetHorizontalPadding, 0, kTweetHorizontalPadding, kTweetSpace2),
      child: Wrap(
        spacing: kTweetSpace2,
        runSpacing: kTweetSpace1,
        crossAxisAlignment: WrapCrossAlignment.center,
        children: [
          if (chip)
            ActionChip(
              avatar: const Icon(Icons.auto_awesome, size: kTweetActionIconSize),
              label: Text(l10n.group_discovery_ai),
              onPressed: state.loadingMore ? null : _rerank,
            ),
          if (state.usedAi) Text(l10n.sort_ungrouped_ai_note, style: tweetMetadataStyle(context)),
          if (state.aiFailed) Text(l10n.group_ai_fallback, style: tweetMetadataStyle(context)),
        ],
      ),
    );
  }
}

/// The mark of the network a candidate or a failure belongs to.
Widget discoverySourceMark(DiscoverySource source, {double size = 14}) {
  final plugin = source == DiscoverySource.x ? coreXPlugin : pluginById(source.name);
  return plugin == null ? SizedBox.square(dimension: size) : pluginMark(plugin, size: size);
}

/// Opens the candidate's profile on its own network.
Future<void> openDiscoveryProfile(BuildContext context, DiscoveryAccount account) => switch (account.source) {
  DiscoverySource.x => Navigator.pushNamed(
    context,
    routeProfile,
    arguments: ProfileScreenArguments(account.id, account.handle, null),
  ),
  DiscoverySource.bluesky => Navigator.push(
    context,
    MaterialPageRoute<void>(builder: (_) => BlueskyProfileScreen(actor: account.id)),
  ),
  DiscoverySource.mastodon => Navigator.push(
    context,
    MaterialPageRoute<void>(builder: (_) => MastodonProfileScreen(acct: account.id)),
  ),
  DiscoverySource.pixiv => Navigator.push(
    context,
    MaterialPageRoute<void>(builder: (_) => PixivUserScreen(userId: int.tryParse(account.id) ?? 0)),
  ),
};

/// Why a row is here, from the signal most of its supporters share:
/// "Reposted by @a, @b and 3 more", "Followed by 4 members".
String discoveryReason(L10n l10n, DiscoveryAccount account) {
  final byKind = <DiscoverySignal, List<DiscoverySupporter>>{};
  final seen = <String>{};
  for (final supporter in account.supporters) {
    if (seen.add(supporter.identity)) byKind.putIfAbsent(supporter.kind, () => []).add(supporter);
  }
  if (byKind.isEmpty) return '';
  final kind = byKind.keys.reduce((a, b) => byKind[b]!.length > byKind[a]!.length ? b : a);
  final members = byKind[kind]!;
  if (kind == DiscoverySignal.followed) return l10n.group_discovery_followed_by(members.length);
  final names = members.take(2).map((s) => s.handle.isEmpty ? s.name : '@${s.handle}').join(', ');
  final reason = switch (kind) {
    DiscoverySignal.reposted => l10n.group_discovery_reposted_by(names),
    DiscoverySignal.quoted => l10n.group_discovery_quoted_by(names),
    DiscoverySignal.replied => l10n.group_discovery_replied_by(names),
    DiscoverySignal.mentioned => l10n.group_discovery_mentioned_by(names),
    DiscoverySignal.suggested => l10n.group_discovery_suggested_for(names),
    DiscoverySignal.related => l10n.group_discovery_related_to(names),
    DiscoverySignal.followed => '',
  };
  final rest = members.length - 2;
  return rest > 0 ? '$reason ${l10n.group_discovery_and_more(rest)}' : reason;
}

enum _RowAction { moreLike, lessLike, hide, otherGroups, openProfile, openPost }

class _DiscoveryRow extends StatelessWidget {
  final DiscoveryAccount account;
  final String groupName;
  final bool added;
  final bool adding;
  final bool expanded;
  final VoidCallback onAdd;
  final VoidCallback onToggle;
  final VoidCallback onOpenProfile;
  final ValueChanged<_RowAction> onAction;

  const _DiscoveryRow({
    super.key,
    required this.account,
    required this.groupName,
    required this.added,
    required this.adding,
    required this.expanded,
    required this.onAdd,
    required this.onToggle,
    required this.onOpenProfile,
    required this.onAction,
  });

  /// Text under the name lines up with the name, past the avatar.
  static const double _textInset = kTweetHorizontalPadding + kTweetAvatarSize + kTweetSpace3;

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final reason = discoveryReason(l10n, account);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        InkWell(
          onTap: expanded ? onToggle : onOpenProfile,
          child: Padding(
            padding: const EdgeInsets.fromLTRB(kTweetHorizontalPadding, kTweetSpace2, kTweetSpace1, 0),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _avatar(context),
                const SizedBox(width: kTweetSpace3),
                Expanded(child: _identity(context)),
                _addButton(context),
                _RowMenu(hasPost: account.postUrl.isNotEmpty, onSelected: onAction),
              ],
            ),
          ),
        ),
        if (reason.isNotEmpty)
          Padding(
            padding: const EdgeInsets.fromLTRB(_textInset, kTweetSpace1, kTweetHorizontalPadding, 0),
            child: Text(reason, style: tweetMetadataStyle(context), maxLines: 2, overflow: TextOverflow.ellipsis),
          ),
        if (expanded) _post(context) else _snippet(context),
        tweetHairlineDivider(context),
      ],
    );
  }

  Widget _avatar(BuildContext context) {
    final url = account.avatarUrl;
    // Pixiv's image host refuses requests without its Referer.
    if (account.source == DiscoverySource.pixiv && url != null) {
      return ClipOval(
        child: SizedBox.square(
          dimension: kTweetAvatarSize,
          child: PixivNetworkImage(
            url: url,
            cacheWidth: (kTweetAvatarSize * MediaQuery.devicePixelRatioOf(context)).ceil(),
          ),
        ),
      );
    }
    return UserAvatar(uri: url, size: kTweetAvatarSize);
  }

  Widget _identity(BuildContext context) {
    final l10n = L10n.of(context);
    final accent = Theme.of(context).colorScheme.primary;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Flexible(
              child: Text(
                account.name.isEmpty ? account.handle : account.name,
                style: tweetDisplayNameStyle(context),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
            ),
            const SizedBox(width: kTweetSpace1),
            discoverySourceMark(account.source),
          ],
        ),
        Row(
          children: [
            Flexible(
              child: Text(
                '@${account.handle}',
                style: tweetMetadataStyle(context),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
            ),
            if (added) ...[
              const SizedBox(width: kTweetSpace2),
              Icon(Icons.check, size: 14, color: accent),
              const SizedBox(width: kTweetSpace1),
              Text(l10n.group_discovery_added, style: tweetMetadataStyle(context).copyWith(color: accent)),
            ],
          ],
        ),
      ],
    );
  }

  Widget _addButton(BuildContext context) {
    final l10n = L10n.of(context);
    if (added) {
      return SizedBox.square(
        dimension: kTweetTouchTarget,
        child: Tooltip(
          message: l10n.group_discovery_added,
          child: Icon(Icons.check_circle, color: Theme.of(context).colorScheme.primary),
        ),
      );
    }
    return IconButton.filledTonal(
      tooltip: l10n.group_discovery_add_to_group(groupName),
      onPressed: adding ? null : onAdd,
      icon: adding
          ? const SizedBox.square(dimension: 18, child: CircularProgressIndicator(strokeWidth: 2))
          : const Icon(Icons.group_add_outlined),
    );
  }

  Widget _snippet(BuildContext context) {
    final snippet = account.snippet;
    if (snippet.isEmpty) return const SizedBox(height: kTweetSpace2);
    return Semantics(
      button: true,
      label: L10n.of(context).group_discovery_show_post,
      child: InkWell(
        onTap: onToggle,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(_textInset, kTweetSpace1, kTweetHorizontalPadding, kTweetSpace2),
          child: Text(snippet, style: tweetBodyStyle(context), maxLines: 2, overflow: TextOverflow.ellipsis),
        ),
      ),
    );
  }

  Widget _post(BuildContext context) => switch (account.supportingPost) {
    TweetWithCard post => TweetTile(clickable: true, tweet: post),
    BlueskyPost post => BlueskyPostCard(post: post, showSourceBadge: true),
    MastodonPost post => MastodonPostCard(post: post, showSourceBadge: true),
    PixivIllust post => Padding(
      padding: const EdgeInsets.symmetric(horizontal: kTweetHorizontalPadding, vertical: kTweetSpace2),
      child: PixivIllustTile(illust: post),
    ),
    _ => Padding(
      padding: const EdgeInsets.fromLTRB(_textInset, kTweetSpace1, kTweetHorizontalPadding, kTweetSpace2),
      child: Text(account.snippet, style: tweetBodyStyle(context)),
    ),
  };
}

class _RowMenu extends StatelessWidget {
  final bool hasPost;
  final ValueChanged<_RowAction> onSelected;

  const _RowMenu({required this.hasPost, required this.onSelected});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return PopupMenuButton<_RowAction>(
      tooltip: MaterialLocalizations.of(context).showMenuTooltip,
      icon: const Icon(Icons.more_vert),
      onSelected: onSelected,
      itemBuilder: (_) => [
        _item(_RowAction.moreLike, Icons.thumb_up_outlined, l10n.reader_more_like),
        _item(_RowAction.lessLike, Icons.thumb_down_outlined, l10n.reader_less_like),
        _item(_RowAction.hide, Icons.visibility_off_outlined, l10n.hide),
        _item(_RowAction.otherGroups, Icons.folder_outlined, l10n.group_discovery_other_groups),
        _item(_RowAction.openProfile, Icons.person_outline, l10n.group_discovery_open_profile),
        if (hasPost) _item(_RowAction.openPost, Icons.open_in_new, l10n.open_post),
      ],
    );
  }

  PopupMenuItem<_RowAction> _item(_RowAction value, IconData icon, String label) => PopupMenuItem(
    value: value,
    height: kTweetTouchTarget,
    child: Row(
      children: [
        Icon(icon, size: kTweetActionIconSize),
        const SizedBox(width: kTweetSpace3),
        Flexible(child: Text(label)),
      ],
    ),
  );
}

enum _HeaderAction { info, undo, reset }

class _HeaderMenu extends StatelessWidget {
  final bool canUndo;
  final bool canReset;
  final VoidCallback onInfo;
  final VoidCallback onUndo;
  final VoidCallback onReset;

  const _HeaderMenu({
    required this.canUndo,
    required this.canReset,
    required this.onInfo,
    required this.onUndo,
    required this.onReset,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return PopupMenuButton<_HeaderAction>(
      tooltip: MaterialLocalizations.of(context).showMenuTooltip,
      icon: const Icon(Icons.more_vert),
      onSelected: (action) => switch (action) {
        _HeaderAction.info => onInfo(),
        _HeaderAction.undo => onUndo(),
        _HeaderAction.reset => onReset(),
      },
      itemBuilder: (_) => [
        _item(_HeaderAction.info, Icons.info_outline, l10n.more_info),
        if (canUndo) _item(_HeaderAction.undo, Icons.undo, l10n.reader_undo),
        _item(_HeaderAction.reset, Icons.restart_alt, l10n.group_discovery_reset, enabled: canReset),
      ],
    );
  }

  PopupMenuItem<_HeaderAction> _item(_HeaderAction value, IconData icon, String label, {bool enabled = true}) =>
      PopupMenuItem(
        value: value,
        enabled: enabled,
        height: kTweetTouchTarget,
        child: Row(
          children: [
            Icon(icon, size: kTweetActionIconSize),
            const SizedBox(width: kTweetSpace3),
            Flexible(child: Text(label)),
          ],
        ),
      );
}

/// Account-shaped placeholders for the first load: avatar, name, reason, snippet.
class _DiscoverySkeleton extends StatefulWidget {
  const _DiscoverySkeleton();

  @override
  State<_DiscoverySkeleton> createState() => _DiscoverySkeletonState();
}

class _DiscoverySkeletonState extends State<_DiscoverySkeleton> with SingleTickerProviderStateMixin {
  late final AnimationController _pulse = skeletonPulseController(this);

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    applySkeletonPulse(context, _pulse);
  }

  @override
  void dispose() {
    _pulse.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => ListView(
    primary: false,
    physics: const NeverScrollableScrollPhysics(),
    children: [for (var i = 0; i < 6; i++) AnimatedBuilder(animation: _pulse, builder: _row)],
  );

  Widget _row(BuildContext context, Widget? _) {
    final color = skeletonBoneColor(context, _pulse.value);
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: kTweetHorizontalPadding, vertical: kTweetSpace3),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SkeletonBone(width: kTweetAvatarSize, height: kTweetAvatarSize, radius: kTweetAvatarSize / 2, color: color),
          const SizedBox(width: kTweetSpace3),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                SkeletonBone(width: 140, height: 12, color: color),
                const SizedBox(height: kTweetSpace2),
                SkeletonBone(width: 200, height: 10, color: color),
                const SizedBox(height: kTweetSpace2),
                SkeletonBone(width: double.infinity, height: 12, color: color),
                const SizedBox(height: 6),
                SkeletonBone(width: 180, height: 12, color: color),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
