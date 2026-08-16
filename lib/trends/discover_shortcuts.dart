import 'package:flutter/material.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/search/search.dart';

/// Ways off the Discover hub that are not “type a query in the bar”.
class DiscoverShortcuts extends StatelessWidget {
  const DiscoverShortcuts({super.key});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(12, 8, 12, 4),
      child: Wrap(
        spacing: 8,
        runSpacing: 8,
        children: [
          ActionChip(
            avatar: const Icon(Icons.person_search_outlined, size: 18),
            label: Text(l10n.discover_find_people),
            onPressed: () => Navigator.pushNamed(
              context,
              routeSearch,
              arguments: SearchArguments(3, focusInputOnOpen: true),
            ),
          ),
          ActionChip(
            avatar: const Icon(Icons.sensors_outlined, size: 18),
            label: Text(l10n.antenna_title),
            onPressed: () => Navigator.pushNamed(context, routeAntennas),
          ),
        ],
      ),
    );
  }
}
