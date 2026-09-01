import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';

/// Divider drawn between new posts and ones the user has already seen.
class CaughtUpDivider extends StatelessWidget {
  const CaughtUpDivider({super.key});

  @override
  Widget build(BuildContext context) {
    final color = Theme.of(context).colorScheme.primary;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 16),
      child: Row(
        children: [
          Expanded(child: Divider(color: color)),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12),
            child: Text(
              L10n.of(context).youre_caught_up,
              style: TextStyle(color: color, fontWeight: FontWeight.bold),
            ),
          ),
          Expanded(child: Divider(color: color)),
        ],
      ),
    );
  }
}
