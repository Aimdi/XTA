import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';

/// What an overflow menu needs to show one entry.
abstract interface class PixivMenuItemSpec {
  /// Stable name; the item is keyed `<keyPrefix>-<id>`.
  String get id;
  IconData get icon;
  String Function(L10n l10n) get label;
}

/// An overflow menu of [entries], keyed [keyPrefix] and each item
/// `<keyPrefix>-<id>`, handing the chosen entry to [onSelected].
class PixivOverflowMenu<E extends PixivMenuItemSpec> extends StatelessWidget {
  final String keyPrefix;
  final List<E> entries;
  final ValueChanged<E> onSelected;

  const PixivOverflowMenu({super.key, required this.keyPrefix, required this.entries, required this.onSelected});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return PopupMenuButton<E>(
      key: ValueKey(keyPrefix),
      onSelected: onSelected,
      itemBuilder: (_) => [for (final entry in entries) _item(l10n, entry)],
    );
  }

  PopupMenuItem<E> _item(L10n l10n, E entry) => PopupMenuItem(
    key: ValueKey('$keyPrefix-${entry.id}'),
    value: entry,
    child: Row(
      children: [
        Icon(entry.icon, size: 20),
        const SizedBox(width: 12),
        Flexible(child: Text(entry.label(l10n))),
      ],
    ),
  );
}
