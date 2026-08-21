import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';

/// Label for a home-strip [Tab], with an unread dot when that feed is newer.
class FeedStripTab extends StatelessWidget {
  final String title;
  final bool unread;
  final IconData? icon;

  const FeedStripTab({
    super.key,
    required this.title,
    this.unread = false,
    this.icon,
  });

  @override
  Widget build(BuildContext context) {
    final label = unread
        ? '$title, ${L10n.of(context).group_has_unread}'
        : title;
    final color = Theme.of(context).colorScheme.onSurface;
    return Semantics(
      label: label,
      child: Badge(
        isLabelVisible: unread,
        smallSize: 8,
        child: ExcludeSemantics(
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              if (icon != null) ...[
                Icon(icon, size: 16, color: color.withValues(alpha: 0.8)),
                const SizedBox(width: 6),
              ],
              Text(title),
            ],
          ),
        ),
      ),
    );
  }
}
