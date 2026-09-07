import 'package:flutter/material.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/group_chrome.dart';
import 'package:xta/tweet/tweet_chrome.dart';

const double kHomeTimelineControlsHeight = 56;

class HomeTimelineTitle extends StatelessWidget {
  final String label;
  final Widget mark;

  const HomeTimelineTitle({super.key, required this.label, required this.mark});

  @override
  Widget build(BuildContext context) => Semantics(
    header: true,
    child: Row(
      children: [
        ExcludeSemantics(child: mark),
        const SizedBox(width: 10),
        Expanded(
          child: Text(
            label,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w700),
          ),
        ),
      ],
    ),
  );
}

/// Reading choices operate on Following; source selection lives in the dock.
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
    final accent = tweetReadableAccentColor(context);
    final labels = [l10n.recent, l10n.popular, l10n.custom];
    return SizedBox(
      height: kHomeTimelineControlsHeight,
      child: Padding(
        padding: const EdgeInsetsDirectional.fromSTEB(12, 0, 8, 8),
        child: Row(
          children: [
            Expanded(
              child: SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                child: Row(
                  children: [
                    _orderButton(context, labels),
                    const SizedBox(width: 8),
                    Semantics(
                      selected: mediaOnly,
                      child: TextButton.icon(
                        key: const ValueKey('home-media-toggle'),
                        onPressed: onMediaToggle,
                        icon: Icon(mediaOnly ? Icons.photo_library : Icons.photo_library_outlined, size: 20),
                        label: Text(l10n.media),
                        style: TextButton.styleFrom(
                          minimumSize: const Size(48, 48),
                          foregroundColor: mediaOnly ? accent : tweetSecondaryColor(context),
                          backgroundColor: mediaOnly ? tweetAccentColor(context).withValues(alpha: 0.12) : null,
                          side: BorderSide(color: mediaOnly ? accent : tweetDividerColor(context)),
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                          padding: const EdgeInsets.symmetric(horizontal: 12),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(width: 8),
            SizedBox.square(
              dimension: kTweetTouchTarget,
              child: IconButton(tooltip: l10n.filters, icon: const Icon(Icons.build_outlined), onPressed: onFilters),
            ),
          ],
        ),
      ),
    );
  }

  Widget _orderButton(BuildContext context, List<String> labels) {
    final filters = groupActiveFilterCount(group);
    return PopupMenuButton<int>(
      key: const ValueKey('home-order-menu'),
      initialValue: _order,
      tooltip: labels[_order],
      position: PopupMenuPosition.under,
      onSelected: onOrderSelected,
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
      ],
      child: Semantics(
        button: true,
        child: Container(
          constraints: const BoxConstraints(minHeight: kTweetTouchTarget),
          padding: const EdgeInsets.symmetric(horizontal: 12),
          decoration: BoxDecoration(
            border: Border.all(color: tweetDividerColor(context)),
            borderRadius: BorderRadius.circular(12),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(Icons.sort, size: 20),
              const SizedBox(width: 8),
              Text(labels[_order], style: tweetLabelStyle(context)),
              if (filters > 0) ...[const SizedBox(width: 8), Badge.count(count: filters)],
              const SizedBox(width: 4),
              const Icon(Icons.expand_more, size: 18),
            ],
          ),
        ),
      ),
    );
  }
}
