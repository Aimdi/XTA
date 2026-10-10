import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/booru/booru_endpoints.dart';
import 'package:xta/plugins/booru/booru_engines.dart';
import 'package:xta/plugins/booru/booru_labels.dart';
import 'package:xta/plugins/booru/booru_popular.dart';
import 'package:xta/plugins/plugin_filter_row.dart';

/// The day, week or month whose popular posts [child] shows. A host without
/// a popular list gets a note instead: [child] then holds its top scores.
class BooruPopularTab extends StatelessWidget {
  final BooruEngine engine;
  final BooruPopularQuery query;
  final ValueChanged<BooruPopularQuery> onChanged;
  final Widget child;

  const BooruPopularTab({
    super.key,
    required this.engine,
    required this.query,
    required this.onChanged,
    required this.child,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Column(
      children: [
        if (booruHasPopularList(engine)) _controls(context, l10n) else _note(context, l10n),
        Expanded(child: child),
      ],
    );
  }

  Widget _controls(BuildContext context, L10n l10n) => PluginFilterRow(
    children: [
      for (final scale in BooruPopularScale.values)
        ChoiceChip(
          key: ValueKey('booru-popular-${scale.name}'),
          label: Text(booruPopularScaleLabel(l10n, scale)),
          selected: query.scale == scale,
          materialTapTargetSize: MaterialTapTargetSize.padded,
          onSelected: (_) => onChanged(query.withScale(scale)),
        ),
      IconButton(
        tooltip: l10n.plugin_booru_popular_earlier,
        icon: const Icon(Icons.chevron_left),
        onPressed: () => onChanged(query.step(-1)),
      ),
      TextButton(
        key: const ValueKey('booru-popular-date'),
        onPressed: () => _pickDate(context),
        child: Text(booruPopularDateLabel(query, Localizations.localeOf(context).toString())),
      ),
      IconButton(
        tooltip: l10n.plugin_booru_popular_later,
        icon: const Icon(Icons.chevron_right),
        onPressed: query.isLatest() ? null : () => onChanged(query.step(1)),
      ),
    ],
  );

  Widget _note(BuildContext context, L10n l10n) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
      child: Row(
        children: [
          Icon(Icons.trending_up, size: 18, color: theme.colorScheme.onSurfaceVariant),
          const SizedBox(width: 8),
          Expanded(child: Text(l10n.plugin_booru_popular_top, style: theme.textTheme.bodySmall)),
        ],
      ),
    );
  }

  Future<void> _pickDate(BuildContext context) async {
    final today = DateTime.now();
    final picked = await showDatePicker(
      context: context,
      helpText: L10n.of(context).plugin_booru_pick_date,
      initialDate: query.date,
      firstDate: DateTime(2005),
      lastDate: today,
    );
    if (picked != null) onChanged(BooruPopularQuery(scale: query.scale, date: picked));
  }
}

String booruPopularDateLabel(BooruPopularQuery query, String locale) => switch (query.scale) {
  BooruPopularScale.month => DateFormat.yMMMM(locale).format(query.date),
  _ => DateFormat.yMMMd(locale).format(query.date),
};
