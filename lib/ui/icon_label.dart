import 'package:flutter/material.dart';

/// Small symbol beside a label — the same row language as plugin chips.
class IconLabel extends StatelessWidget {
  final IconData icon;
  final String label;
  final double iconSize;

  const IconLabel({
    super.key,
    required this.icon,
    required this.label,
    this.iconSize = 16,
  });

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final text = Text(
          label,
          maxLines: 1,
          softWrap: false,
          overflow: TextOverflow.ellipsis,
        );
        // Scrollable TabBars give this row infinite width. A Flexible child
        // throws there; a min-row overflows when a parent *does* constrain it.
        final children = [
          Icon(icon, size: iconSize),
          const SizedBox(width: 6),
          if (constraints.hasBoundedWidth) Flexible(child: text) else text,
        ];
        return Row(mainAxisSize: MainAxisSize.min, children: children);
      },
    );
  }
}
