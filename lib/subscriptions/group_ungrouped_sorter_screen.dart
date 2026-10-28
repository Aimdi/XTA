import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/subscriptions/group_identity.dart';
import 'package:xta/subscriptions/group_membership_sheet.dart';
import 'package:xta/subscriptions/group_ungrouped_sorter.dart';
import 'package:xta/subscriptions/subscription_look.dart';
import 'package:xta/ui/action_feedback.dart';
import 'package:xta/ui/motion.dart';
import 'package:xta/ui/reading_column.dart';

const _avatarSize = 72.0;
const _groupMarkSize = 24.0;

/// How far, as a share of its width, a card slides as it comes in.
const _cardSlide = 0.08;

/// Goes through [accounts] one at a time: a tap on a group puts the account in
/// it and shows the next one.
class UngroupedSorterScreen extends StatefulWidget {
  final List<Subscription> accounts;

  const UngroupedSorterScreen({super.key, required this.accounts});

  @override
  State<UngroupedSorterScreen> createState() => _UngroupedSorterScreenState();
}

class _UngroupedSorterScreenState extends State<UngroupedSorterScreen> {
  late final UngroupedSorterStore _sorter;

  @override
  void initState() {
    super.initState();
    final groups = context.read<GroupsModel>();
    _sorter = UngroupedSorterStore(widget.accounts, place: groups.addToGroups, unplace: groups.removeFromGroups);
  }

  @override
  void dispose() {
    _sorter.destroy();
    super.dispose();
  }

  /// Runs [save], saying so when it fails; whether it went through.
  Future<bool> _saved(Future<bool> Function() save) async {
    final messenger = ScaffoldMessenger.of(context);
    final failed = L10n.of(context).sort_ungrouped_failed;
    try {
      return await save();
    } catch (error, stack) {
      messenger.showSnackBar(SnackBar(content: Text(failed)));
      debugPrint('$error\n$stack');
      return false;
    }
  }

  /// Puts the account in the groups [groupIds] names, with a click once it is
  /// saved.
  Future<void> _place(FutureOr<List<String>> Function() groupIds) async {
    final placed = await _saved(() async => _sorter.put(await groupIds()));
    if (placed && mounted) selectionHaptic(context);
  }

  Future<void> _pickSeveral() async {
    final groups = context.read<GroupsModel>().state;
    final chosen = await showGroupMembershipSheet(context, groups: groups, selected: const []);
    if (chosen == null || chosen.isEmpty || !mounted) return;
    await _place(() => chosen);
  }

  Future<void> _createGroup() async {
    final name = await showDialog<String>(context: context, builder: (_) => const _GroupNameDialog());
    if (name == null || !mounted) return;
    final groups = context.read<GroupsModel>();
    await _place(() async => [await groups.createGroup(name)]);
  }

  @override
  Widget build(BuildContext context) {
    return ScopedBuilder<UngroupedSorterStore, SorterState>(
      store: _sorter,
      onState: (context, state) => Scaffold(
        appBar: _bar(context, state),
        body: state.current == null
            ? _SorterEnd(state: state, onSortSkipped: _sorter.sortSkipped)
            : _sorting(context, state),
        bottomNavigationBar: _SorterFooter(state: state, onUndo: () => _saved(_sorter.undo), onSkip: _sorter.skip),
      ),
    );
  }

  AppBar _bar(BuildContext context, SorterState state) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    return AppBar(
      title: Text(l10n.sort_ungrouped),
      actions: [
        if (!state.finished)
          Padding(
            padding: const EdgeInsetsDirectional.only(end: 16),
            child: Center(
              child: Text(
                l10n.sort_one_progress(state.position + 1, state.accounts.length),
                style: theme.textTheme.bodyMedium!.copyWith(color: theme.colorScheme.onSurfaceVariant),
              ),
            ),
          ),
      ],
    );
  }

  /// The account's card at the top and the groups at the foot, where they
  /// stay put from one account to the next.
  Widget _sorting(BuildContext context, SorterState state) {
    return ReadingColumn(
      padding: const EdgeInsets.fromLTRB(16, 24, 16, 16),
      builder: (context, padding) => CustomScrollView(
        slivers: [
          SliverPadding(
            padding: padding.copyWith(bottom: 0),
            sliver: SliverToBoxAdapter(
              child: _CardSwitcher(
                state: state,
                child: _AccountCard(account: state.current!),
              ),
            ),
          ),
          SliverPadding(
            padding: padding.copyWith(top: 24),
            sliver: SliverFillRemaining(
              hasScrollBody: false,
              child: Align(
                alignment: Alignment.bottomCenter,
                child: ScopedBuilder<GroupsModel, List<SubscriptionGroup>>(
                  store: context.read<GroupsModel>(),
                  onState: (context, groups) => _GroupChips(
                    groups: groups,
                    onGroup: (id) => _place(() => [id]),
                    onSeveral: _pickSeveral,
                    onCreate: _createGroup,
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// [child], the current account's card, fading in and sliding a little from
/// the end of the reading direction, or from its start after an undo.
class _CardSwitcher extends StatelessWidget {
  final SorterState state;
  final Widget child;

  const _CardSwitcher({required this.state, required this.child});

  @override
  Widget build(BuildContext context) {
    final key = ValueKey(state.position);
    final reading = Directionality.of(context) == TextDirection.rtl ? -1.0 : 1.0;
    final forward = state.backward ? -1.0 : 1.0;
    return AnimatedSwitcher(
      duration: xtaMotionDuration(context, kXtaMotionNavigation),
      switchInCurve: Curves.easeOutCubic,
      switchOutCurve: Curves.easeInCubic,
      transitionBuilder: (child, animation) {
        // A card coming in starts on the side it moves from; one going out
        // leaves toward the other.
        final side = (child.key == key ? 1.0 : -1.0) * forward * reading;
        return FadeTransition(
          opacity: animation,
          child: SlideTransition(
            position: Tween(begin: Offset(side * _cardSlide, 0), end: Offset.zero).animate(animation),
            child: child,
          ),
        );
      },
      child: KeyedSubtree(key: key, child: child),
    );
  }
}

/// Who the account is: its avatar, name and handle, and a way to its profile.
class _AccountCard extends StatelessWidget {
  final Subscription account;

  const _AccountCard({required this.account});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Column(
      children: [
        _avatar(theme),
        const SizedBox(height: 12),
        Semantics(
          liveRegion: true,
          child: Text(
            account.name,
            textAlign: TextAlign.center,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: theme.textTheme.titleLarge!.copyWith(fontWeight: FontWeight.w700),
          ),
        ),
        const SizedBox(height: 2),
        Text(
          subscriptionSubtitle(account),
          textAlign: TextAlign.center,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: theme.textTheme.bodyMedium!.copyWith(color: theme.colorScheme.onSurfaceVariant),
        ),
        const SizedBox(height: 4),
        TextButton.icon(
          key: const ValueKey('sorter-open-profile'),
          onPressed: () => openSubscription(context, account),
          icon: const Icon(Icons.open_in_new, size: 18),
          label: Text(L10n.of(context).group_discovery_open_profile),
        ),
      ],
    );
  }

  /// An X account's picture on a disc, which shows while it loads or when
  /// there is none; other networks draw their own mark.
  Widget _avatar(ThemeData theme) {
    final avatar = subscriptionAvatar(account, size: _avatarSize);
    if (account is! UserSubscription) return avatar;
    return DecoratedBox(
      decoration: BoxDecoration(color: theme.colorScheme.surfaceContainerHighest, shape: BoxShape.circle),
      child: avatar,
    );
  }
}

/// The groups as chips with their marks, then Several groups and Create group.
///
/// They stay enabled while a choice is saved, so they do not flicker with
/// every tap; the sorter ignores a tap that comes meanwhile.
class _GroupChips extends StatelessWidget {
  final List<SubscriptionGroup> groups;
  final ValueChanged<String> onGroup;
  final VoidCallback onSeveral;
  final VoidCallback onCreate;

  const _GroupChips({required this.groups, required this.onGroup, required this.onSeveral, required this.onCreate});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Wrap(
      alignment: WrapAlignment.center,
      spacing: 8,
      runSpacing: 8,
      children: [
        for (final group in groups)
          ActionChip(
            key: ValueKey('sorter-group-${group.id}'),
            avatar: GroupMark.forGroup(group, size: _groupMarkSize),
            label: Text(group.name, maxLines: 1, overflow: TextOverflow.ellipsis),
            onPressed: () => onGroup(group.id),
          ),
        if (groups.length > 1)
          ActionChip(
            key: const ValueKey('sorter-several'),
            avatar: const Icon(Icons.checklist),
            label: Text(l10n.sort_one_several_groups),
            onPressed: onSeveral,
          ),
        ActionChip(
          key: const ValueKey('sorter-create'),
          avatar: const Icon(Icons.add),
          label: Text(l10n.create_subscription_group),
          onPressed: onCreate,
        ),
      ],
    );
  }
}

/// Undo at the start, Skip at the end while there is someone to skip.
class _SorterFooter extends StatelessWidget {
  final SorterState state;
  final VoidCallback onUndo;
  final VoidCallback onSkip;

  const _SorterFooter({required this.state, required this.onUndo, required this.onSkip});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return SafeArea(
      top: false,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Divider(height: 1),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
            // Side by side while they fit; one above the other with large text
            // on a narrow phone.
            child: OverflowBar(
              alignment: MainAxisAlignment.spaceBetween,
              children: [
                TextButton.icon(
                  key: const ValueKey('sorter-undo'),
                  onPressed: state.choices.isEmpty ? null : onUndo,
                  icon: const Icon(Icons.undo),
                  label: Text(l10n.reader_undo),
                ),
                if (!state.finished)
                  TextButton(key: const ValueKey('sorter-skip'), onPressed: onSkip, child: Text(l10n.skip)),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// How the round went, with Finish and, when some were skipped, another round
/// for those.
class _SorterEnd extends StatelessWidget {
  final SorterState state;
  final VoidCallback onSortSkipped;

  const _SorterEnd({required this.state, required this.onSortSkipped});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    final placed = state.placedCount;
    final skipped = state.skipped.length;
    return Center(
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.task_alt, size: 48, color: theme.colorScheme.primary),
            const SizedBox(height: 16),
            if (placed == 0 && skipped == 0)
              Text(l10n.sort_ungrouped_empty, textAlign: TextAlign.center, style: theme.textTheme.titleMedium),
            if (placed > 0)
              Text(
                l10n.sort_ungrouped_applied(placed),
                textAlign: TextAlign.center,
                style: theme.textTheme.titleMedium,
              ),
            if (skipped > 0)
              Text(
                l10n.sort_one_skipped(skipped),
                textAlign: TextAlign.center,
                style: theme.textTheme.bodyMedium!.copyWith(color: theme.colorScheme.onSurfaceVariant),
              ),
            const SizedBox(height: 24),
            FilledButton(onPressed: () => Navigator.maybePop(context), child: Text(l10n.finish)),
            if (skipped > 0) ...[
              const SizedBox(height: 8),
              TextButton(onPressed: onSortSkipped, child: Text(l10n.sort_one_sort_skipped)),
            ],
          ],
        ),
      ),
    );
  }
}

/// Asks for a new group's name; pops it, trimmed, or nothing when cancelled.
class _GroupNameDialog extends StatefulWidget {
  const _GroupNameDialog();

  @override
  State<_GroupNameDialog> createState() => _GroupNameDialogState();
}

class _GroupNameDialogState extends State<_GroupNameDialog> {
  final _name = TextEditingController();

  @override
  void dispose() {
    _name.dispose();
    super.dispose();
  }

  void _submit() {
    final name = _name.text.trim();
    if (name.isNotEmpty) Navigator.pop(context, name);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return AlertDialog(
      title: Text(l10n.create_subscription_group),
      content: TextField(
        controller: _name,
        autofocus: true,
        textCapitalization: TextCapitalization.sentences,
        decoration: InputDecoration(labelText: l10n.name),
        onSubmitted: (_) => _submit(),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: Text(l10n.cancel)),
        ValueListenableBuilder<TextEditingValue>(
          valueListenable: _name,
          builder: (context, value, _) =>
              FilledButton(onPressed: value.text.trim().isEmpty ? null : _submit, child: Text(l10n.create)),
        ),
      ],
    );
  }
}
