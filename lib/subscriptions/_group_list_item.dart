import 'package:dynamic_color/dynamic_color.dart';
import 'package:flutter/material.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/group/group_screen.dart';
import 'package:xta/subscriptions/group_identity.dart';
import 'package:xta/subscriptions/widgets/fallback_avatar.dart';
import 'package:xta/user.dart';
import 'package:provider/provider.dart';

/// How far each level of nesting moves a row to the right.
const double kGroupNestIndent = 20;

/// One group as a dense list row: colored avatar with the group's icon (or a
/// monogram when none was chosen), name, member count and a preview cluster of
/// member avatars. The group color lives only on the small avatar, so the text
/// keeps the theme's contrast.
class GroupListItem extends StatelessWidget {
  final SubscriptionGroup group;
  final VoidCallback? onLongPress;

  /// How deep this group is nested, which is how far the row is indented.
  final int depth;

  // When set, the row is part of a manually-ordered list and shows a drag
  // handle bound to this index.
  final int? reorderIndex;

  const GroupListItem({
    super.key,
    required this.group,
    this.onLongPress,
    this.reorderIndex,
    this.depth = 0,
  });

  Widget _buildTrailing(BuildContext context) {
    final l10n = L10n.of(context);
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        IconButton(
          icon: Icon(
            group.pinned ? Icons.push_pin : Icons.push_pin_outlined,
            size: 20,
            color: group.pinned
                ? Theme.of(context).colorScheme.primary
                : Theme.of(context).hintColor,
          ),
          tooltip: group.pinned ? l10n.unpin : l10n.pin,
          onPressed: () => context.read<GroupsModel>().toggleGroupPinned(
            group.id,
            !group.pinned,
          ),
        ),
        if (reorderIndex != null)
          ReorderableDragStartListener(
            index: reorderIndex!,
            child: Icon(Icons.drag_handle, color: Theme.of(context).hintColor),
          )
        else
          const Icon(Icons.chevron_right),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    // A colour the reader chose is used as chosen. Harmonising rotates it
    // towards the theme accent, which turned every pick into a variation of
    // orange — the generated fallback is harmonised, because that one is the
    // app's colour rather than theirs.
    final fill =
        group.color ??
        groupFallbackColor(group.name).harmonizeWith(theme.colorScheme.primary);
    final onFill = ThemeData.estimateBrightnessForColor(fill) == Brightness.dark
        ? Colors.white
        : Colors.black87;
    final hiddenMembers = group.numberOfMembers - group.memberPreviews.length;

    return ListTile(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12.0)),
      // Indent rather than hide: a nested group is still a group you can open.
      contentPadding: EdgeInsets.only(
        left: 16 + kGroupNestIndent * depth,
        right: 8,
      ),
      leading: CircleAvatar(
        radius: 20,
        backgroundColor: fill,
        child: group.icon == defaultGroupIcon
            ? Text(
                group.name.isEmpty
                    ? '?'
                    : group.name.characters.first.toUpperCase(),
                style: TextStyle(
                  color: onFill,
                  fontWeight: FontWeight.w700,
                  fontSize: 16,
                ),
              )
            : Icon(group.iconData, size: 20, color: onFill),
      ),
      title: Row(
        children: [
          if (group.nsfw) ...[
            Icon(
              Icons.visibility_off_outlined,
              size: 16,
              color: theme.hintColor,
            ),
            const SizedBox(width: 6),
          ],
          Expanded(
            child: Text(
              group.name,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          ),
        ],
      ),
      subtitle: Row(
        children: [
          Flexible(
            child: Text(
              L10n.of(
                context,
              ).subscription_group_member_count(group.numberOfMembers),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          ),
          if (group.memberPreviews.isNotEmpty) ...[
            const SizedBox(width: 8),
            ExcludeSemantics(
              child: _AvatarCluster(members: group.memberPreviews),
            ),
            if (hiddenMembers > 0) ...[
              const SizedBox(width: 4),
              Text('+$hiddenMembers', style: theme.textTheme.bodySmall),
            ],
          ],
        ],
      ),
      trailing: _buildTrailing(context),
      onTap: () => Navigator.pushNamed(
        context,
        routeGroup,
        arguments: GroupScreenArguments(id: group.id, name: group.name),
      ),
      onLongPress: onLongPress,
    );
  }
}

class _AvatarCluster extends StatelessWidget {
  static const double _size = 20;
  static const double _step = 13;
  static const double _ring = 1.5;

  final List<GroupMemberPreview> members;

  const _AvatarCluster({required this.members});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final ringColor = theme.colorScheme.surface;
    final diameter = _size + 2 * _ring;

    return SizedBox(
      height: diameter,
      width: (members.length - 1) * _step + diameter,
      child: Stack(
        children: [
          for (final (i, member) in members.indexed)
            Positioned(
              left: i * _step,
              child: Container(
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  border: Border.all(color: ringColor, width: _ring),
                ),
                child: member.avatarUrl == null
                    ? FallbackAvatar(
                        seed: member.id,
                        displayName: member.name,
                        size: _size,
                        accent: theme.colorScheme.primary,
                      )
                    : UserAvatar(uri: member.avatarUrl, size: _size),
              ),
            ),
        ],
      ),
    );
  }
}
