import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';

/// Label for a home-strip [Tab], with an unread dot when that feed is newer.
class FeedStripTab extends StatelessWidget {
  final String title;
  final bool unread;

  const FeedStripTab({super.key, required this.title, this.unread = false});

  @override
  Widget build(BuildContext context) {
    final label = unread
        ? '$title, ${L10n.of(context).group_has_unread}'
        : title;
    return Semantics(
      label: label,
      child: Badge(
        isLabelVisible: unread,
        smallSize: 8,
        child: ExcludeSemantics(child: Text(title)),
      ),
    );
  }
}
