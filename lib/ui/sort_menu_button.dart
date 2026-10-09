import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/ui/conversation_sort.dart';

/// A compact "sort ▾" control that shows the current order and opens a menu
/// of the others. Sits at the top of a replies, quotes or reposts list; the
/// caller places it.
class SortMenuButton<T> extends StatelessWidget {
  final T value;
  final List<T> options;
  final String Function(L10n l10n, T option) labelOf;
  final IconData Function(T option) iconOf;
  final ValueChanged<T> onSelected;

  const SortMenuButton({
    super.key,
    required this.value,
    required this.options,
    required this.labelOf,
    required this.iconOf,
    required this.onSelected,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return PopupMenuButton<T>(
      initialValue: value,
      tooltip: l10n.sort_order,
      onSelected: (option) {
        if (option != value) onSelected(option);
      },
      itemBuilder: (context) => [
        for (final option in options)
          PopupMenuItem(
            value: option,
            child: Row(
              children: [
                Icon(iconOf(option), size: 20),
                const SizedBox(width: 12),
                Flexible(child: Text(labelOf(l10n, option))),
              ],
            ),
          ),
      ],
      child: _SortChip(icon: iconOf(value), label: labelOf(l10n, value)),
    );
  }
}

class _SortChip extends StatelessWidget {
  final IconData icon;
  final String label;

  const _SortChip({required this.icon, required this.label});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final color = theme.colorScheme.primary;
    return ConstrainedBox(
      constraints: const BoxConstraints(minHeight: 48),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 12),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 18, color: color),
            const SizedBox(width: 6),
            Flexible(
              child: Text(
                label,
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.labelLarge?.copyWith(color: color),
              ),
            ),
            Icon(Icons.arrow_drop_down, size: 20, color: color),
          ],
        ),
      ),
    );
  }
}

String replySortLabel(L10n l10n, ReplySort sort) => switch (sort) {
  ReplySort.relevant => l10n.sort_relevant,
  ReplySort.recent => l10n.recent,
  ReplySort.oldest => l10n.sort_oldest,
  ReplySort.mostLiked => l10n.sort_most_liked,
};

IconData replySortIcon(ReplySort sort) => switch (sort) {
  ReplySort.relevant => Icons.auto_awesome_outlined,
  ReplySort.recent => Icons.schedule,
  ReplySort.oldest => Icons.history,
  ReplySort.mostLiked => Icons.favorite_border,
};

String quoteSortLabel(L10n l10n, QuoteSort sort) => switch (sort) {
  QuoteSort.recent => l10n.recent,
  QuoteSort.top => l10n.sort_top,
  QuoteSort.oldest => l10n.sort_oldest,
  QuoteSort.mostLiked => l10n.sort_most_liked,
};

IconData quoteSortIcon(QuoteSort sort) => switch (sort) {
  QuoteSort.recent => Icons.schedule,
  QuoteSort.top => Icons.trending_up,
  QuoteSort.oldest => Icons.history,
  QuoteSort.mostLiked => Icons.favorite_border,
};

String reposterSortLabel(L10n l10n, ReposterSort sort) => switch (sort) {
  ReposterSort.recent => l10n.recent,
  ReposterSort.mostFollowers => l10n.sort_most_followers,
};

IconData reposterSortIcon(ReposterSort sort) => switch (sort) {
  ReposterSort.recent => Icons.schedule,
  ReposterSort.mostFollowers => Icons.people_outline,
};

/// The reply sort control for a network offering [options].
class ReplySortButton extends StatelessWidget {
  final ReplySort value;
  final List<ReplySort> options;
  final ValueChanged<ReplySort> onSelected;

  const ReplySortButton({
    super.key,
    required this.value,
    required this.options,
    required this.onSelected,
  });

  @override
  Widget build(BuildContext context) => SortMenuButton<ReplySort>(
    value: effectiveSort(value, options),
    options: options,
    labelOf: replySortLabel,
    iconOf: replySortIcon,
    onSelected: onSelected,
  );
}
