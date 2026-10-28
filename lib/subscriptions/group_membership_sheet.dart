import 'dart:math';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/subscriptions/group_identity.dart';
import 'package:xta/subscriptions/group_pick.dart';
import 'package:xta/ui/action_feedback.dart';
import 'package:xta/ui/quick_pick_sheet.dart';
import 'package:xta/utils/quick_pick.dart';

const _markSize = 30.0;

/// Picks which groups someone belongs to: the groups as chips that tick with
/// a tap, a search that can create a group, and a line that says where the
/// account is. The choice applies as the sheet closes, however it is closed,
/// so there is nothing to save and nothing to cancel.
///
/// Returns the chosen group ids, or null when they are the ones it opened
/// with: closing it untouched follows no one and changes nothing.
Future<List<String>?> showGroupMembershipSheet(
  BuildContext context, {
  required List<SubscriptionGroup> groups,
  required List<String> selected,
}) async {
  final prefs = PrefService.of(context, listen: false);
  final selection = GroupMembershipStore(selected);
  final openOrder = groupPickOrder(groups, selected: selected, recent: recentGroupPicks(prefs));
  try {
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      showDragHandle: true,
      builder: (_) => _GroupMembershipSheet(
        selection: selection,
        openOrder: openOrder,
        shown: max(kQuickPickShown, selected.length),
      ),
    );
    final chosen = selection.state;
    final initial = selected.toSet();
    if (setEquals(chosen, initial)) return null;
    await rememberGroupPicks(prefs, chosen.difference(initial));
    return chosen.toList();
  } finally {
    selection.destroy();
  }
}

class _GroupMembershipSheet extends StatefulWidget {
  final GroupMembershipStore selection;

  /// The order the chips open in, fixed once, so a chip never moves from
  /// under a finger.
  final List<String> openOrder;

  /// How many chips show before All groups: enough for every group the
  /// account is in.
  final int shown;

  const _GroupMembershipSheet({required this.selection, required this.openOrder, required this.shown});

  @override
  State<_GroupMembershipSheet> createState() => _GroupMembershipSheetState();
}

class _GroupMembershipSheetState extends State<_GroupMembershipSheet> {
  final _view = QuickPickStore();
  final _field = TextEditingController();

  @override
  void dispose() {
    _view.destroy();
    _field.dispose();
    super.dispose();
  }

  void _toggle(String id) {
    widget.selection.toggle(id);
    selectionHaptic(context);
  }

  Future<void> _create() async {
    final name = _view.state.query.trim();
    if (name.isEmpty) return;
    final id = await context.read<GroupsModel>().createGroup(name);
    _field.clear();
    _view.created(id);
    widget.selection.add(id);
    if (mounted) selectionHaptic(context);
  }

  @override
  Widget build(BuildContext context) {
    return ScopedBuilder<GroupsModel, List<SubscriptionGroup>>(
      store: context.read<GroupsModel>(),
      onState: (context, groups) => ScopedBuilder<QuickPickStore, QuickPickView>(
        store: _view,
        onState: (context, view) => ScopedBuilder<GroupMembershipStore, Set<String>>(
          store: widget.selection,
          onState: (context, chosen) => _sheet(context, groups, view, chosen),
        ),
      ),
    );
  }

  Widget _sheet(BuildContext context, List<SubscriptionGroup> groups, QuickPickView view, Set<String> chosen) {
    final l10n = L10n.of(context);
    final ordered = quickPickOrdered(groups, widget.openOrder, view.createdIds, (group) => group.id);
    final chips = quickPickChips(ordered, view, (group) => group.name, shown: widget.shown);
    final placed = [
      for (final group in ordered)
        if (chosen.contains(group.id)) group.name,
    ];
    return QuickPickBody(
      title: QuickPickTitle(
        title: l10n.add_to_group,
        allLabel: l10n.group_sheet_all_groups,
        onShowAll: chips.hasMore ? _view.showAll : null,
      ),
      field: _field,
      onSearch: _view.search,
      sectionLabel: _sectionLabel(l10n, groups, chips),
      createRow: chips.canCreate
          ? QuickPickCreateRow(label: l10n.group_sheet_create(view.query.trim()), onTap: _create)
          : null,
      chips: [
        for (final group in chips.items)
          QuickPickChip(
            key: ValueKey('group-pick-${group.id}'),
            mark: GroupMark.forGroup(group, size: _markSize),
            label: group.name,
            selected: chosen.contains(group.id),
            onTap: () => _toggle(group.id),
          ),
      ],
      status: QuickPickStatusLine(
        placed: placed.isNotEmpty,
        icon: placed.isEmpty ? Icons.circle_outlined : Icons.check_circle_outline,
        text: _status(l10n, placed),
      ),
    );
  }

  String? _sectionLabel(L10n l10n, List<SubscriptionGroup> groups, QuickPickChips<SubscriptionGroup> chips) {
    if (groups.isEmpty && chips.section == QuickPickSection.recent) return l10n.no_subscription_groups_yet;
    if (chips.items.isEmpty && chips.section != QuickPickSection.noMatch) return null;
    return quickPickSectionText(
      l10n,
      chips.section,
      all: l10n.group_sheet_all_groups,
      noMatch: l10n.group_sheet_no_match,
    );
  }

  String _status(L10n l10n, List<String> names) => switch (names) {
    [] => l10n.group_sheet_in_none,
    [final one] => l10n.group_sheet_in_one(one),
    [final first, final second] => l10n.group_sheet_in_two(first, second),
    _ => l10n.group_sheet_in_many(names.length),
  };
}
