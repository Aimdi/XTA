import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/tweet/catch_up_split.dart';

/// Card closing a feed read in catch-up mode: the reader has reached the posts
/// they had already read, and the feed stops there.
///
/// [onReached] fires once, when the card is first built — the only moment the
/// app knows the reader got to the end of what was new, and the only moment it
/// is honest to move their reading position.
class CaughtUpEndCard extends StatefulWidget {
  final bool mayBeIncomplete;
  final bool nothingNew;
  final VoidCallback? onShowOlder;
  final VoidCallback? onReached;

  const CaughtUpEndCard({
    super.key,
    required this.mayBeIncomplete,
    required this.nothingNew,
    this.onShowOlder,
    this.onReached,
  });

  static String messageOf(BuildContext context, CatchUpMessage message) {
    switch (message) {
      case CatchUpMessage.caughtUp:
        return L10n.of(context).catch_up_end_message;
      case CatchUpMessage.nothingNew:
        return L10n.of(context).catch_up_nothing_new;
      case CatchUpMessage.mayBeIncomplete:
        return L10n.of(context).catch_up_end_may_be_incomplete;
    }
  }

  @override
  State<CaughtUpEndCard> createState() => _CaughtUpEndCardState();
}

class _CaughtUpEndCardState extends State<CaughtUpEndCard> {
  @override
  void initState() {
    super.initState();
    final onReached = widget.onReached;
    if (onReached != null) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) onReached();
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final hintColor = theme.hintColor;
    final message = catchUpMessageFor(mayBeIncomplete: widget.mayBeIncomplete, nothingNew: widget.nothingNew);
    final showOlder = widget.onShowOlder;
    // "You're caught up" is a claim, and a gap-limited load cannot make it —
    // there the message stands alone and says so.
    final incomplete = message == CatchUpMessage.mayBeIncomplete;

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 32, horizontal: 16),
      child: Column(
        children: [
          Icon(incomplete ? Icons.more_horiz : Icons.check_circle_outline, size: 36, color: hintColor),
          const SizedBox(height: 12),
          if (!incomplete) ...[
            Text(L10n.of(context).youre_caught_up, textAlign: TextAlign.center, style: theme.textTheme.titleMedium),
            const SizedBox(height: 4),
          ],
          Text(
            CaughtUpEndCard.messageOf(context, message),
            textAlign: TextAlign.center,
            style: theme.textTheme.bodyMedium?.copyWith(color: hintColor),
          ),
          if (showOlder != null) TextButton(onPressed: showOlder, child: Text(L10n.of(context).catch_up_show_older)),
        ],
      ),
    );
  }
}
