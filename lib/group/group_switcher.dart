import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:quax/database/entities.dart';
import 'package:quax/generated/l10n.dart';
import 'package:quax/group/group_model.dart';
import 'package:quax/subscriptions/group_identity.dart';
import 'package:quax/tweet/tweet_chrome.dart';

/// The feed title as a button that opens a group picker, so you can hop between
/// groups without going back to the Groups tab.
class GroupSwitcherTitle extends StatelessWidget {
  final String name;
  final String currentGroupId;
  final ValueChanged<SubscriptionGroup>? onSwitch;

  const GroupSwitcherTitle({
    super.key,
    required this.name,
    required this.currentGroupId,
    this.onSwitch,
  });

  @override
  Widget build(BuildContext context) {
    final groupsModel = context.read<GroupsModel>();
    return ScopedBuilder<GroupsModel, List<SubscriptionGroup>>(
      store: groupsModel,
      onState: (_, groups) => _GroupSwitcherButton(
        name: _currentName(groups),
        group: _currentGroup(groups),
        onTap: onSwitch == null
            ? null
            : () => showGroupSwitcher(
                context,
                currentGroupId: currentGroupId,
                onSwitch: onSwitch!,
              ),
      ),
    );
  }

  SubscriptionGroup? _currentGroup(List<SubscriptionGroup> groups) {
    for (final group in groups) {
      if (group.id == currentGroupId) return group;
    }
    return null;
  }

  String _currentName(List<SubscriptionGroup> groups) =>
      _currentGroup(groups)?.name ?? name;
}

class _GroupSwitcherButton extends StatelessWidget {
  final String name;
  final SubscriptionGroup? group;
  final VoidCallback? onTap;

  const _GroupSwitcherButton({
    required this.name,
    required this.group,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final button = InkWell(
      borderRadius: BorderRadius.circular(12),
      onTap: onTap,
      child: ConstrainedBox(
        constraints: const BoxConstraints(minHeight: kTweetTouchTarget),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (group != null) ...[
              GroupMark.forGroup(group!, size: 32),
              const SizedBox(width: kTweetSpace2),
            ],
            Flexible(
              child: Text(
                name,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style:
                    theme.appBarTheme.titleTextStyle ??
                    theme.textTheme.titleLarge,
              ),
            ),
            if (onTap != null) ...[
              const SizedBox(width: kTweetSpace1),
              Icon(
                Icons.expand_more,
                size: kTweetActionIconSize,
                color: tweetSecondaryColor(context),
              ),
            ],
          ],
        ),
      ),
    );
    if (onTap == null) return button;
    return Tooltip(message: L10n.of(context).switch_group, child: button);
  }
}

/// Lists the groups in the order the Groups tab shows them (pinned first), with
/// the current one marked. Picking one calls [onSwitch].
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
      return ScopedBuilder<GroupsModel, List<SubscriptionGroup>>.transition(
        store: groupsModel,
        onLoading: (_) =>
            const SizedBox(height: 240, child: _GroupSwitcherSkeleton()),
        onError: (_, __) => SizedBox(
          height: 180,
          child: TweetStateTile(
            icon: Icons.error_outline,
            message: L10n.of(sheetContext).unable_to_refresh_the_subscriptions,
            onTap: groupsModel.reloadGroups,
          ),
        ),
        onState: (_, groups) {
          if (groups.isEmpty) {
            return SizedBox(
              height: 240,
              child: TweetEmptyState(
                message: L10n.of(sheetContext).no_subscription_groups_yet,
              ),
            );
          }

          return ConstrainedBox(
            constraints: BoxConstraints(
              maxHeight: MediaQuery.of(sheetContext).size.height * 0.7,
            ),
            child: ListView.builder(
              shrinkWrap: true,
              padding: const EdgeInsets.only(bottom: 16),
              itemCount: groups.length,
              itemBuilder: (context, index) {
                final group = groups[index];
                final isCurrent = group.id == currentGroupId;

                return ListTile(
                  minTileHeight: kTweetTouchTarget,
                  contentPadding: const EdgeInsets.symmetric(
                    horizontal: kTweetHorizontalPadding,
                  ),
                  leading: GroupMark.forGroup(group, size: 40),
                  title: Text(
                    group.name,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  subtitle: Text(
                    L10n.of(
                      context,
                    ).subscription_group_member_count(group.numberOfMembers),
                  ),
                  trailing: isCurrent
                      ? Icon(Icons.check, color: tweetAccentColor(context))
                      : (group.pinned
                            ? Icon(
                                Icons.push_pin,
                                size: 16,
                                color: tweetSecondaryColor(context),
                              )
                            : null),
                  selected: isCurrent,
                  selectedColor: tweetPrimaryColor(context),
                  selectedTileColor: tweetAccentColor(
                    context,
                  ).withValues(alpha: 0.08),
                  onTap: () {
                    Navigator.pop(sheetContext);
                    if (!isCurrent) {
                      onSwitch(group);
                    }
                  },
                );
              },
            ),
          );
        },
      );
    },
  );
}

class _GroupSwitcherSkeleton extends StatelessWidget {
  const _GroupSwitcherSkeleton();

  @override
  Widget build(BuildContext context) {
    final color = Theme.of(context).colorScheme.surfaceContainerHighest;
    return ListView.separated(
      padding: const EdgeInsets.symmetric(vertical: kTweetSpace2),
      itemCount: 3,
      separatorBuilder: (_, __) => const SizedBox(height: kTweetSpace1),
      itemBuilder: (_, __) => Padding(
        padding: const EdgeInsets.symmetric(
          horizontal: kTweetHorizontalPadding,
          vertical: kTweetSpace2,
        ),
        child: Row(
          children: [
            Container(
              width: 40,
              height: 40,
              decoration: BoxDecoration(color: color, shape: BoxShape.circle),
            ),
            const SizedBox(width: kTweetSpace3),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Container(width: 144, height: 12, color: color),
                  const SizedBox(height: kTweetSpace2),
                  Container(width: 96, height: 10, color: color),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
