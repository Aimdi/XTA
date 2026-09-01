import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/combined_groups.dart';
import 'package:xta/group/feed_switcher_menu.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/home/_feed.dart';
import 'package:xta/subscriptions/_groups_edit.dart' show openSubscriptionGroupDialog;
import 'package:xta/subscriptions/group_identity.dart';

/// The feed title as a button that opens a group picker, so you can hop between
/// groups without going back to the Groups tab.
class GroupSwitcherTitle extends StatelessWidget {
  final String name;
  final String currentGroupId;
  final ValueChanged<SubscriptionGroup> onSwitch;

  const GroupSwitcherTitle({super.key, required this.name, required this.currentGroupId, required this.onSwitch});

  /// Opens the short menu, and does whatever it came back with.
  ///
  /// A feed means leaving this route for the home screen it lives on; a pinned
  /// group swaps in place; Groups opens the full list.
  Future<void> _open(BuildContext context) async {
    final choice = await showFeedSwitcherMenu(context, currentGroupId: currentGroupId);
    if (choice == null || !context.mounted) {
      return;
    }

    switch (choice) {
      case FeedTabChoice(:final tab):
        context.read<FeedTabStore>().select(tab);
        Navigator.popUntil(context, ModalRoute.withName(routeHome));
      case GroupJumpChoice(:final group):
        onSwitch(group);
      case AllGroupsChoice():
        await showGroupSwitcher(context, currentGroupId: currentGroupId, onSwitch: onSwitch);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Tooltip(
      message: L10n.of(context).switch_group,
      child: InkWell(
        borderRadius: BorderRadius.circular(9999),
        onTap: () => _open(context),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Flexible(
                child: Text(
                  name,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: theme.appBarTheme.titleTextStyle ?? theme.textTheme.titleLarge,
                ),
              ),
              // Says the feed is more than the group it is named after, which
              // nothing else on the screen would.
              ScopedBuilder<CombinedGroupsStore, Set<String>>(
                store: context.read<CombinedGroupsStore>(),
                onState: (context, alsoRead) {
                  final extra = alsoRead.where((e) => e != currentGroupId).length;
                  if (extra == 0) {
                    return const SizedBox.shrink();
                  }
                  return Padding(
                    padding: const EdgeInsets.only(left: 6),
                    child: Text('+$extra', style: TextStyle(color: theme.colorScheme.primary)),
                  );
                },
              ),
              const SizedBox(width: 2),
              Icon(Icons.expand_more, size: 20, color: theme.appBarTheme.foregroundColor),
            ],
          ),
        ),
      ),
    );
  }
}

/// Says how to put groups together, and how many currently are.
///
/// A row rather than a hint buried in settings: holding a list item is not
/// something anyone finds by accident, and a combination that cannot be seen is
/// a combination nobody remembers making.
class _CombineHint extends StatelessWidget {
  final int count;
  final VoidCallback onClear;

  const _CombineHint({required this.count, required this.onClear});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);

    if (count == 0) {
      return Padding(
        padding: const EdgeInsets.fromLTRB(24, 0, 24, 8),
        child: Text(l10n.group_combine_hint, style: theme.textTheme.bodySmall),
      );
    }

    return ListTile(
      leading: Icon(Icons.playlist_add_check, color: theme.colorScheme.primary),
      title: Text(l10n.group_combined_count(count), style: TextStyle(color: theme.colorScheme.primary)),
      trailing: TextButton(onPressed: onClear, child: Text(l10n.group_combine_clear)),
    );
  }
}

/// Closes the list of groups by offering another one, the way the reference
/// menus end with what you can do rather than only what you can pick.
class _NewGroupRow extends StatelessWidget {
  final VoidCallback onTap;

  const _NewGroupRow({required this.onTap});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const Divider(),
        ListTile(
          leading: Icon(Icons.add, color: theme.colorScheme.primary),
          title: Text(L10n.of(context).create_subscription_group, style: TextStyle(color: theme.colorScheme.primary)),
          onTap: onTap,
        ),
      ],
    );
  }
}

/// Lists the groups in the order the Groups tab shows them (pinned first), with
/// the current one marked. Picking one calls [onSwitch]; holding one reads it
/// alongside instead of instead of.
Future<void> showGroupSwitcher(
  BuildContext context, {
  required String currentGroupId,
  required ValueChanged<SubscriptionGroup> onSwitch,
}) {
  final groupsModel = context.read<GroupsModel>();

  return showModalBottomSheet(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    showDragHandle: true,
    builder: (sheetContext) {
      return ScopedBuilder<GroupsModel, List<SubscriptionGroup>>(
        store: groupsModel,
        onState: (_, groups) {
          if (groups.isEmpty) {
            return Padding(
              padding: const EdgeInsets.fromLTRB(24, 8, 24, 32),
              child: Text(L10n.of(sheetContext).no_subscription_groups_yet),
            );
          }

          final combined = sheetContext.read<CombinedGroupsStore>();

          return ConstrainedBox(
            constraints: BoxConstraints(maxHeight: MediaQuery.of(sheetContext).size.height * 0.7),
            child: ScopedBuilder<CombinedGroupsStore, Set<String>>(
              store: combined,
              onState: (_, alsoRead) => ListView.builder(
                shrinkWrap: true,
                padding: const EdgeInsets.only(bottom: 16),
                // The hint above, the groups, and a way to make another one at
                // the end — the list of groups is exactly where wanting a new
                // one occurs to you.
                itemCount: groups.length + 2,
                itemBuilder: (context, index) {
                  if (index == 0) {
                    return _CombineHint(count: alsoRead.length, onClear: combined.clear);
                  }
                  if (index == groups.length + 1) {
                    return _NewGroupRow(
                      onTap: () {
                        Navigator.pop(sheetContext);
                        openSubscriptionGroupDialog(context, null, '', defaultGroupIcon);
                      },
                    );
                  }

                  final group = groups[index - 1];
                  final isCurrent = group.id == currentGroupId;
                  final isCombined = alsoRead.contains(group.id) && !isCurrent;

                  return ListTile(
                    // Groups being read together wear their own colour, not one
                    // shared highlight: which four are combined is the thing
                    // worth seeing, and four identical orange rows do not say
                    // it. The colour is already how a group is recognised in
                    // the mark beside it.
                    selectedColor: readableGroupColor(group, Theme.of(context)),
                    leading: GroupMark.forGroup(group, size: 36),
                    title: Text(group.name, maxLines: 1, overflow: TextOverflow.ellipsis),
                    subtitle: Text(L10n.of(context).subscription_group_member_count(group.numberOfMembers)),
                    trailing: isCurrent
                        ? const Icon(Icons.check)
                        : isCombined
                        ? const Icon(Icons.playlist_add_check)
                        : (group.pinned ? const Icon(Icons.push_pin, size: 16) : null),
                    selected: isCurrent || isCombined,
                    onTap: () {
                      Navigator.pop(sheetContext);
                      if (!isCurrent) {
                        onSwitch(group);
                      }
                    },
                    // Held rather than tapped, so the ordinary use of this sheet
                    // — hopping to one group — keeps costing one tap.
                    onLongPress: isCurrent ? null : () => combined.toggle(group.id),
                  );
                },
              ),
            ),
          );
        },
      );
    },
  );
}
