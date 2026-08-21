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
    // No Flexible: this row sits in scrollable TabBars whose width is
    // unbounded, and a flex child throws there. Ellipsis still applies when
    // a parent *does* constrain the tab.
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, size: iconSize),
        const SizedBox(width: 6),
        Text(
          label,
          maxLines: 1,
          softWrap: false,
          overflow: TextOverflow.ellipsis,
        ),
      ],
    );
  }
}
