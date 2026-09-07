import 'package:flutter/material.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/group_chrome.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/motion.dart';

const double kHomeTimelineControlsHeight = 56;

/// Reclaims space while retaining the controls' selection and scroll state.
class HomeCollapsingControls extends StatelessWidget {
  final bool visible;
  final Widget child;

  const HomeCollapsingControls({super.key, required this.visible, required this.child});

  @override
  Widget build(BuildContext context) => IgnorePointer(
    ignoring: !visible,
    child: ExcludeFocus(
      excluding: !visible,
      child: ExcludeSemantics(
        excluding: !visible,
        child: TweenAnimationBuilder<double>(
          tween: Tween(begin: visible ? 1 : 0, end: visible ? 1 : 0),
          duration: xtaMotionDuration(context, kXtaMotionStandard),
          curve: Curves.easeInOut,
          child: child,
          builder: (_, factor, child) => ClipRect(
            child: Align(alignment: Alignment.topCenter, heightFactor: factor, child: child),
          ),
        ),
      ),
    ),
  );
}

class HomeTimelineTitle extends StatelessWidget {
  final String label;
  final Widget mark;
  final bool unread;
  final VoidCallback onPressed;

  const HomeTimelineTitle({
    super.key,
    required this.label,
    required this.mark,
    required this.onPressed,
    this.unread = false,
  });

  @override
  Widget build(BuildContext context) => Tooltip(
    message: L10n.of(context).home_networks,
    child: Semantics(
      button: true,
      label: unread ? '$label, ${L10n.of(context).group_has_unread}' : label,
      child: InkWell(
        key: const ValueKey('home-source-picker'),
        onTap: onPressed,
        borderRadius: BorderRadius.circular(12),
        child: ConstrainedBox(
          constraints: const BoxConstraints(minHeight: kTweetTouchTarget),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 4),
            child: ExcludeSemantics(
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Badge(isLabelVisible: unread, smallSize: 7, child: mark),
                  const SizedBox(width: 10),
                  Flexible(
                    child: Text(
                      label,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w700),
                    ),
                  ),
                  const SizedBox(width: 4),
                  Icon(Icons.expand_more, size: 20, color: tweetSecondaryColor(context)),
                ],
              ),
            ),
          ),
        ),
      ),
    ),
  );
}

/// Presentation stays one tap away; ordering and filters share one menu.
class HomeTimelineControls extends StatelessWidget {
  final SubscriptionGroupGet group;
  final bool mediaOnly;
  final ValueChanged<int> onOrderSelected;
  final VoidCallback onMediaToggle;
  final VoidCallback onFilters;

  const HomeTimelineControls({
    super.key,
    required this.group,
    required this.mediaOnly,
    required this.onOrderSelected,
    required this.onMediaToggle,
    required this.onFilters,
  });

  int get _order => group.custom ? 2 : (group.popular ? 1 : 0);

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final labels = [l10n.recent, l10n.popular, l10n.custom];
    return SizedBox(
      height: kHomeTimelineControlsHeight,
      child: DecoratedBox(
        decoration: BoxDecoration(border: Border(bottom: BorderSide(color: tweetDividerColor(context)))),
        child: Padding(
          padding: const EdgeInsetsDirectional.fromSTEB(8, 0, 8, 4),
          child: LayoutBuilder(
            builder: (context, constraints) => Row(
              children: [
                Expanded(
                  child: SingleChildScrollView(
                    scrollDirection: Axis.horizontal,
                    child: Row(
                      children: [
                        _modeTab(context, false, l10n.tweets),
                        _modeTab(context, true, l10n.media),
                      ],
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                _orderButton(
                  context,
                  labels,
                  compact: constraints.maxWidth < 350 || MediaQuery.textScalerOf(context).scale(14) > 18,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _modeTab(BuildContext context, bool media, String label) {
    final selected = mediaOnly == media;
    return Semantics(
      selected: selected,
      child: TextButton(
        key: ValueKey(media ? 'home-media-toggle' : 'home-posts-tab'),
        onPressed: () {
          if (!selected) onMediaToggle();
        },
        style: TextButton.styleFrom(
          minimumSize: const Size(72, kTweetTouchTarget),
          foregroundColor: selected ? tweetPrimaryColor(context) : tweetSecondaryColor(context),
          padding: EdgeInsets.zero,
          shape: const RoundedRectangleBorder(),
        ),
        child: AnimatedContainer(
          duration: xtaMotionDuration(context, kXtaMotionStandard),
          constraints: const BoxConstraints(minHeight: kTweetTouchTarget),
          padding: const EdgeInsets.symmetric(horizontal: 12),
          decoration: BoxDecoration(
            border: Border(bottom: BorderSide(color: selected ? tweetReadableAccentColor(context) : Colors.transparent, width: 3)),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              if (media) ...[const Icon(Icons.photo_library_outlined, size: 20), const SizedBox(width: 6)],
              Text(label, style: TextStyle(fontWeight: selected ? FontWeight.w700 : FontWeight.w500)),
            ],
          ),
        ),
      ),
    );
  }

  Widget _orderButton(BuildContext context, List<String> labels, {required bool compact}) {
    final filters = groupActiveFilterCount(group);
    return PopupMenuButton<int>(
      key: const ValueKey('home-order-menu'),
      initialValue: _order,
      tooltip: '${labels[_order]} · ${L10n.of(context).filters}',
      position: PopupMenuPosition.under,
      onSelected: (value) => value == 3 ? onFilters() : onOrderSelected(value),
      itemBuilder: (_) => [
        for (var index = 0; index < labels.length; index++)
          PopupMenuItem(
            value: index,
            height: kTweetTouchTarget,
            child: Row(
              children: [
                SizedBox(
                  width: 24,
                  child: index == _order ? Icon(Icons.check, color: tweetReadableAccentColor(context)) : null,
                ),
                const SizedBox(width: 12),
                Expanded(child: Text(labels[index])),
                if (index == 2) const Icon(Icons.tune, size: 20),
              ],
            ),
          ),
        const PopupMenuDivider(),
        PopupMenuItem(
          value: 3,
          height: kTweetTouchTarget,
          child: Row(
            children: [
              const Icon(Icons.build_outlined, size: 24),
              const SizedBox(width: 12),
              Expanded(child: Text(L10n.of(context).filters)),
              if (filters > 0) Badge.count(count: filters),
            ],
          ),
        ),
      ],
      child: Semantics(
        button: true,
        child: Container(
          constraints: const BoxConstraints(minHeight: kTweetTouchTarget, minWidth: kTweetTouchTarget),
          padding: EdgeInsets.symmetric(horizontal: compact ? 8 : 12),
          decoration: BoxDecoration(
            color: tweetSecondaryColor(context).withValues(alpha: 0.06),
            borderRadius: BorderRadius.circular(12),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(Icons.sort, size: 20),
              if (!compact) ...[const SizedBox(width: 8), Text(labels[_order], style: tweetLabelStyle(context))],
              if (filters > 0) ...[const SizedBox(width: 8), Badge.count(count: filters)],
              if (!compact) ...[const SizedBox(width: 4), const Icon(Icons.expand_more, size: 18)],
            ],
          ),
        ),
      ),
    );
  }
}
