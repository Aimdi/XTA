import 'package:flutter/material.dart';
import 'package:quax/constants.dart';
import 'package:quax/database/entities.dart';
import 'package:quax/generated/l10n.dart';
import 'package:quax/group/custom_feed_rules.dart';
import 'package:quax/tweet/tweet_chrome.dart';
import 'package:quax/ui/x_look_theme.dart';

const double kGroupControlBarHeight = 56;
const double kGroupControlRadius = 12;

int groupActiveFilterCount(SubscriptionGroupGet group) {
  if (!group.custom) return 0;
  return (group.contentFilter == contentFilterDefault ? 0 : 1) +
      (group.minLikes > 0 ? 1 : 0) +
      (group.minRetweets > 0 ? 1 : 0) +
      group.mutedKeywords.length;
}

String groupContentFilterLabel(BuildContext context, String value) {
  final l10n = L10n.of(context);
  return switch (value) {
    contentFilterSfw => l10n.content_filter_sfw,
    contentFilterNsfw => l10n.content_filter_nsfw,
    _ => l10n.content_filter_default,
  };
}

class GroupFeedControlBar extends StatelessWidget
    implements PreferredSizeWidget {
  final SubscriptionGroupGet group;
  final bool mediaOnly;
  final ValueChanged<int> onOrderSelected;
  final VoidCallback onMediaToggle;
  final VoidCallback onCustomSettings;

  const GroupFeedControlBar({
    super.key,
    required this.group,
    required this.mediaOnly,
    required this.onOrderSelected,
    required this.onMediaToggle,
    required this.onCustomSettings,
  });

  @override
  Size get preferredSize => const Size.fromHeight(kGroupControlBarHeight);

  int get _order => group.custom ? 2 : (group.popular ? 1 : 0);

  @override
  Widget build(BuildContext context) {
    final filters = groupActiveFilterCount(group);
    final background =
        XLookTokens.maybeOf(context)?.background ??
        Theme.of(context).scaffoldBackgroundColor;
    return DecoratedBox(
      decoration: BoxDecoration(
        color: background,
        border: Border(
          bottom: BorderSide(
            color: tweetDividerColor(context),
            width: kTweetDividerThickness,
          ),
        ),
      ),
      child: SizedBox(
        height: kGroupControlBarHeight,
        child: ListView(
          scrollDirection: Axis.horizontal,
          padding: const EdgeInsets.symmetric(
            horizontal: kTweetHorizontalPadding,
            vertical: kTweetSpace1,
          ),
          children: [
            _GroupChoice(
              label: L10n.of(context).recent,
              selected: _order == 0,
              onSelected: () => onOrderSelected(0),
            ),
            const SizedBox(width: kTweetSpace2),
            _GroupChoice(
              label: L10n.of(context).popular,
              selected: _order == 1,
              onSelected: () => onOrderSelected(1),
            ),
            const SizedBox(width: kTweetSpace2),
            _GroupChoice(
              label: L10n.of(context).custom,
              selected: _order == 2,
              badgeCount: filters,
              onSelected: _order == 2
                  ? onCustomSettings
                  : () => onOrderSelected(2),
            ),
            const SizedBox(width: kTweetSpace2),
            _GroupChoice(
              label: L10n.of(context).media,
              icon: mediaOnly
                  ? Icons.photo_library
                  : Icons.photo_library_outlined,
              selected: mediaOnly,
              onSelected: onMediaToggle,
            ),
            if (group.custom &&
                group.contentFilter != contentFilterDefault) ...[
              const SizedBox(width: kTweetSpace2),
              _GroupChoice(
                label: groupContentFilterLabel(context, group.contentFilter),
                icon: Icons.filter_alt_outlined,
                selected: true,
                onSelected: onCustomSettings,
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _GroupChoice extends StatelessWidget {
  final String label;
  final IconData? icon;
  final bool selected;
  final int badgeCount;
  final VoidCallback onSelected;

  const _GroupChoice({
    required this.label,
    required this.selected,
    required this.onSelected,
    this.icon,
    this.badgeCount = 0,
  });

  @override
  Widget build(BuildContext context) {
    final chip = FilterChip(
      selected: selected,
      showCheckmark: icon == null,
      avatar: icon == null ? null : Icon(icon, size: kTweetActionIconSize),
      label: Text(badgeCount > 0 ? '$label ($badgeCount)' : label),
      labelStyle: tweetMetadataStyle(context).copyWith(
        color: selected
            ? tweetPrimaryColor(context)
            : tweetSecondaryColor(context),
        fontWeight: selected ? FontWeight.w700 : FontWeight.w500,
      ),
      side: BorderSide(
        color: selected
            ? tweetAccentColor(context)
            : tweetDividerColor(context),
      ),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(kGroupControlRadius),
      ),
      backgroundColor: Colors.transparent,
      selectedColor: tweetAccentColor(context).withValues(alpha: 0.12),
      onSelected: (_) => onSelected(),
    );
    return ConstrainedBox(
      constraints: const BoxConstraints(minHeight: kTweetTouchTarget),
      child: chip,
    );
  }
}

class GroupSettingsSection extends StatelessWidget {
  final IconData icon;
  final String title;
  final String? description;
  final Widget child;

  const GroupSettingsSection({
    super.key,
    required this.icon,
    required this.title,
    required this.child,
    this.description,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(
            kTweetHorizontalPadding,
            kTweetSpace4,
            kTweetHorizontalPadding,
            kTweetSpace2,
          ),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Icon(
                icon,
                size: kTweetActionIconSize,
                color: tweetAccentColor(context),
              ),
              const SizedBox(width: kTweetSpace2),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(title, style: tweetLabelStyle(context)),
                    if (description != null) ...[
                      const SizedBox(height: kTweetSpace1),
                      Text(description!, style: tweetMetadataStyle(context)),
                    ],
                  ],
                ),
              ),
            ],
          ),
        ),
        Padding(
          padding: const EdgeInsets.symmetric(
            horizontal: kTweetHorizontalPadding,
          ),
          child: child,
        ),
        const SizedBox(height: kTweetSpace4),
        tweetHairlineDivider(context),
      ],
    );
  }
}

enum GroupOverflowAction { filters, subscriptions, settings }

class GroupOverflowButton extends StatelessWidget {
  final ValueChanged<GroupOverflowAction> onSelected;

  const GroupOverflowButton({super.key, required this.onSelected});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return PopupMenuButton<GroupOverflowAction>(
      icon: const Icon(Icons.more_vert),
      onSelected: onSelected,
      itemBuilder: (_) => [
        _item(GroupOverflowAction.filters, Icons.tune, l10n.filters),
        _item(
          GroupOverflowAction.subscriptions,
          Icons.people_outline,
          l10n.subscriptions,
        ),
        _item(
          GroupOverflowAction.settings,
          Icons.settings_outlined,
          l10n.settings,
        ),
      ],
    );
  }

  PopupMenuItem<GroupOverflowAction> _item(
    GroupOverflowAction value,
    IconData icon,
    String label,
  ) {
    return PopupMenuItem(
      value: value,
      height: kTweetTouchTarget,
      child: Row(
        children: [
          Icon(icon, size: kTweetActionIconSize),
          const SizedBox(width: kTweetSpace3),
          Text(label),
        ],
      ),
    );
  }
}
