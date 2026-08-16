import 'package:flutter/material.dart';

/// Accent dot on a group mark when that feed has newer cached posts.
class GroupUnreadBadge extends StatelessWidget {
  final bool unread;
  final Widget child;

  /// Inset from the child's top-right, so a rounded card does not clip the
  /// dot into the corner radius.
  final double inset;

  const GroupUnreadBadge({
    super.key,
    required this.unread,
    required this.child,
    this.inset = 0,
  });

  @override
  Widget build(BuildContext context) {
    if (!unread) {
      return child;
    }
    final scheme = Theme.of(context).colorScheme;
    return Stack(
      clipBehavior: Clip.none,
      children: [
        child,
        Positioned(
          top: inset,
          right: inset,
          child: Container(
            width: 8,
            height: 8,
            decoration: BoxDecoration(
              color: scheme.primary,
              shape: BoxShape.circle,
              border: Border.all(color: scheme.surface, width: 1.5),
            ),
          ),
        ),
      ],
    );
  }
}
