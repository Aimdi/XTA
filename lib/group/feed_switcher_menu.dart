import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/home/_feed.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/subscriptions/group_identity.dart';

/// What was picked out of the feed switcher.
sealed class FeedSwitcherChoice {
  const FeedSwitcherChoice();
}

/// One of the home feeds — Following, For you, or a plugin's.
class FeedTabChoice extends FeedSwitcherChoice {
  final FeedTab tab;

  const FeedTabChoice(this.tab);
}

/// A group reached straight from the menu, without the full list.
class GroupJumpChoice extends FeedSwitcherChoice {
  final SubscriptionGroup group;

  const GroupJumpChoice(this.group);
}

/// Every group, which is a sheet rather than a menu — see [showGroupSwitcher].
class AllGroupsChoice extends FeedSwitcherChoice {
  const AllGroupsChoice();
}

/// How many pinned groups the menu offers directly.
///
/// The point of the menu is that it is short. Pinned groups are the ones worth
/// a single tap; the rest are one row further, behind Groups.
const int kFeedSwitcherPinnedLimit = 5;

IconData _feedIcon(FeedTab tab) {
  if (tab == FeedTab.following) return Icons.people_outline;
  if (tab == FeedTab.foryou) return Icons.auto_awesome_outlined;
  return pluginById(tab.id)?.icon ?? Icons.extension_outlined;
}

/// The rect of [context]'s own box, in the overlay's coordinates — where the
/// menu should hang from.
RelativeRect? _anchorOf(BuildContext context) {
  final button = context.findRenderObject();
  final overlay = Navigator.of(context).overlay?.context.findRenderObject();
  if (button is! RenderBox || overlay is! RenderBox || !button.hasSize) {
    return null;
  }

  return RelativeRect.fromRect(
    Rect.fromPoints(
      button.localToGlobal(Offset.zero, ancestor: overlay),
      button.localToGlobal(
        button.size.bottomRight(Offset.zero),
        ancestor: overlay,
      ),
    ),
    Offset.zero & overlay.size,
  );
}

PopupMenuItem<FeedSwitcherChoice> _row({
  required Widget leading,
  required String label,
  required FeedSwitcherChoice value,
  Widget? trailing,
}) {
  return PopupMenuItem<FeedSwitcherChoice>(
    value: value,
    child: Row(
      children: [
        leading,
        const SizedBox(width: 12),
        Expanded(
          child: Text(label, maxLines: 1, overflow: TextOverflow.ellipsis),
        ),
        if (trailing != null) trailing,
      ],
    ),
  );
}

/// The switcher, hanging from whatever [context] belongs to — normally the feed
/// title, so it opens where it was asked for rather than sliding up from the
/// far edge of the screen.
///
/// Short by design: the feeds, the pinned groups, and a way into the rest.
Future<FeedSwitcherChoice?> showFeedSwitcherMenu(
  BuildContext context, {
  FeedTab? currentTab,
  String? currentGroupId,
}) {
  final position = _anchorOf(context);
  if (position == null) {
    return Future.value(null);
  }

  final l10n = L10n.of(context);
  final theme = Theme.of(context);
  final available = availableFeedTabs(PrefService.of(context, listen: false));
  final pinned = context
      .read<GroupsModel>()
      .state
      .where((e) => e.pinned && e.id != currentGroupId)
      .take(kFeedSwitcherPinnedLimit)
      .toList(growable: false);

  return showMenu<FeedSwitcherChoice>(
    context: context,
    position: position,
    items: [
      for (final feed in available)
        _row(
          leading: Icon(
            _feedIcon(feed.id),
            size: 20,
            color: theme.colorScheme.onSurfaceVariant,
          ),
          label: feed.titleBuilder(context),
          value: FeedTabChoice(feed.id),
          trailing: feed.id == currentTab
              ? const Icon(Icons.check, size: 18)
              : null,
        ),
      const PopupMenuDivider(),
      for (final group in pinned)
        _row(
          leading: GroupMark.forGroup(group, size: 24),
          label: group.name,
          value: GroupJumpChoice(group),
        ),
      // The full list is a sheet: it holds every group, and holding a row there
      // reads it alongside — neither of which fits in a menu.
      _row(
        leading: Icon(
          Icons.folder_outlined,
          size: 20,
          color: theme.colorScheme.onSurfaceVariant,
        ),
        label: l10n.groups,
        value: const AllGroupsChoice(),
        trailing: Icon(
          Icons.chevron_right,
          size: 18,
          color: theme.colorScheme.onSurfaceVariant,
        ),
      ),
    ],
  );
}
