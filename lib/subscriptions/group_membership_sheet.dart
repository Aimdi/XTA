import 'package:flutter/material.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/subscriptions/group_identity.dart';
import 'package:xta/ui/x_controls.dart';

/// Picks which groups someone belongs to.
///
/// Replaces a third-party multi-select dialog, which is why this screen used to
/// look like nothing else in the app: tall rows with bare checkboxes, a pair of
/// text buttons, and a search field hidden behind an icon in the title bar —
/// present, but not somewhere anyone would find it.
///
/// Everything here is in service of picking quickly. The search field is simply
/// there. Rows are dense and carry each group's own mark, so a list of twenty
/// is scanned by colour rather than read. Groups the person is already in sort
/// to the top, because confirming one is the commonest reason to open this at
/// all.
///
/// Returns the chosen group ids, or null if dismissed.
Future<List<String>?> showGroupMembershipSheet(
  BuildContext context, {
  required List<SubscriptionGroup> groups,
  required List<String> selected,
}) {
  return showModalBottomSheet<List<String>>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    showDragHandle: true,
    builder: (_) => _GroupMembershipSheet(groups: groups, selected: selected),
  );
}

class _GroupMembershipSheet extends StatefulWidget {
  final List<SubscriptionGroup> groups;
  final List<String> selected;

  const _GroupMembershipSheet({required this.groups, required this.selected});

  @override
  State<_GroupMembershipSheet> createState() => _GroupMembershipSheetState();
}

class _GroupMembershipSheetState extends State<_GroupMembershipSheet> {
  final TextEditingController _search = TextEditingController();
  late final Set<String> _chosen = widget.selected.toSet();

  /// The order the list opens in, fixed once.
  ///
  /// Sorted here rather than on every build: rows must not jump out from under a
  /// finger the moment one is ticked.
  late final List<SubscriptionGroup> _ordered = [
    ...widget.groups.where((g) => _chosen.contains(g.id)),
    ...widget.groups.where((g) => !_chosen.contains(g.id)),
  ];

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  List<SubscriptionGroup> get _visible {
    final query = _search.text.trim().toLowerCase();
    if (query.isEmpty) {
      return _ordered;
    }
    return _ordered.where((g) => g.name.toLowerCase().contains(query)).toList(growable: false);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    final visible = _visible;

    return FractionallySizedBox(
      heightFactor: 0.85,
      child: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 0, 20, 8),
            child: Row(
              children: [
                Expanded(child: Text(l10n.select, style: theme.textTheme.titleLarge)),
                if (_chosen.isNotEmpty)
                  Text('${_chosen.length}', style: TextStyle(color: theme.colorScheme.primary)),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(12, 0, 12, 4),
            child: XSearchField(
              controller: _search,
              hintText: l10n.search,
              onChanged: (_) => setState(() {}),
            ),
          ),
          Expanded(
            child: visible.isEmpty
                ? Center(child: Text(l10n.no_subscription_groups_yet, textAlign: TextAlign.center))
                : ListView.builder(
                    padding: const EdgeInsets.only(bottom: 8),
                    itemCount: visible.length,
                    itemBuilder: (context, index) {
                      final group = visible[index];
                      final checked = _chosen.contains(group.id);

                      return CheckboxListTile(
                        value: checked,
                        dense: true,
                        controlAffinity: ListTileControlAffinity.trailing,
                        // The whole row is the target, not a 20px box at its edge.
                        secondary: GroupMark.forGroup(group, size: 32),
                        title: Text(
                          group.name,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: checked
                              ? TextStyle(
                                  color: readableGroupColor(group, theme), fontWeight: FontWeight.w600)
                              : null,
                        ),
                        onChanged: (_) => setState(() {
                          if (!_chosen.remove(group.id)) {
                            _chosen.add(group.id);
                          }
                        }),
                      );
                    },
                  ),
          ),
          const Divider(height: 1),
          Padding(
            padding: const EdgeInsets.all(12),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                TextButton(onPressed: () => Navigator.pop(context), child: Text(l10n.cancel)),
                const SizedBox(width: 8),
                FilledButton(
                  onPressed: () => Navigator.pop(context, _chosen.toList()),
                  child: Text(l10n.save),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
