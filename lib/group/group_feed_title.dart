import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/group_chrome.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/group/group_switcher.dart';
import 'package:xta/subscriptions/group_identity.dart';
import 'package:xta/subscriptions/group_mark_style.dart';

/// A group feed's app bar title: the group's mark, its name and how many accounts it follows.
///
/// With [onSwitch] the title also opens the group picker.
class GroupFeedTitle extends StatelessWidget {
  final String name;
  final String groupId;
  final ValueChanged<SubscriptionGroup>? onSwitch;

  const GroupFeedTitle({super.key, required this.name, required this.groupId, this.onSwitch});

  @override
  Widget build(BuildContext context) {
    return ScopedBuilder<GroupModel, SubscriptionGroupGet>(
      store: context.read<GroupModel>(),
      onState: (context, group) {
        final mark = _mark(context, group);
        // An empty line while the group loads keeps the name from shifting when the count arrives.
        final count = group.id.isEmpty
            ? ''
            : L10n.of(context).subscription_group_member_count(group.subscriptions.length);
        final onSwitch = this.onSwitch;
        if (onSwitch == null) {
          return GroupTitleLabel(name: name, mark: mark, subtitle: count);
        }
        return GroupSwitcherTitle(name: name, currentGroupId: groupId, onSwitch: onSwitch, mark: mark, subtitle: count);
      },
    );
  }

  Widget _mark(BuildContext context, SubscriptionGroupGet group) {
    final meta = context.read<GroupsModel>().state.where((g) => g.id == groupId).firstOrNull;
    return GroupMark(
      name: name,
      seed: meta != null ? groupSeedColor(meta) : groupFallbackColor(name),
      emoji: meta?.emoji,
      icon: meta?.icon ?? group.icon,
      markStyle: meta?.markStyle ?? GroupMarkStyle.auto,
      size: kGroupTitleMarkSize,
    );
  }
}
