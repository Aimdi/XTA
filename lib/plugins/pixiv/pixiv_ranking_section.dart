import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_grid.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';

/// Ranking modes Flare pins as first-class feeds, plus XTA's existing set.
const pixivRankingModes = [
  'day',
  'week',
  'month',
  'day_male',
  'day_female',
  'week_rookie',
  'week_original',
  'day_manga',
];

String pixivRankingLabel(L10n l10n, String mode) => switch (mode) {
  'week' => l10n.plugin_pixiv_ranking_week,
  'month' => l10n.plugin_pixiv_ranking_month,
  'day_male' => l10n.plugin_pixiv_ranking_day_male,
  'day_female' => l10n.plugin_pixiv_ranking_day_female,
  'week_rookie' => l10n.plugin_pixiv_ranking_rookie,
  'week_original' => l10n.plugin_pixiv_ranking_week_original,
  'day_manga' => l10n.plugin_pixiv_ranking_day_manga,
  _ => l10n.plugin_pixiv_ranking_day,
};

/// `YYYY-MM-DD` for the archive request, or null for today's board.
String? pixivRankingDateParam(DateTime? date) {
  if (date == null) return null;
  String pad(int value) => '$value'.padLeft(2, '0');
  return '${date.year}-${pad(date.month)}-${pad(date.day)}';
}

/// Rankings: a mode picker and a past day's board, over the ranked works.
class PixivRankingSection extends StatelessWidget {
  final String mode;
  final DateTime? date;
  final ValueChanged<String> onMode;

  /// A picked day, or null to go back to today's board.
  final ValueChanged<DateTime?> onDate;
  final PixivIllustListStore store;

  const PixivRankingSection({
    super.key,
    required this.mode,
    required this.date,
    required this.onMode,
    required this.onDate,
    required this.store,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12),
          child: Row(
            children: [
              Expanded(child: _modePicker(l10n)),
              const SizedBox(width: 8),
              ..._dateControls(context, l10n),
            ],
          ),
        ),
        Expanded(
          child: PixivIllustFeed(store: store, emptyMessage: l10n.plugin_pixiv_ranking_empty),
        ),
      ],
    );
  }

  Widget _modePicker(L10n l10n) => PopupMenuButton<String>(
    initialValue: mode,
    onSelected: (next) {
      if (next != mode) onMode(next);
    },
    itemBuilder: (_) => [
      for (final value in pixivRankingModes)
        CheckedPopupMenuItem(value: value, checked: mode == value, child: Text(pixivRankingLabel(l10n, value))),
    ],
    child: ConstrainedBox(
      constraints: const BoxConstraints(minHeight: 48),
      child: Row(
        children: [
          const Icon(Icons.bar_chart, size: 20),
          const SizedBox(width: 8),
          Expanded(child: Text(pixivRankingLabel(l10n, mode), maxLines: 1, overflow: TextOverflow.ellipsis)),
          const Icon(Icons.expand_more),
        ],
      ),
    ),
  );

  List<Widget> _dateControls(BuildContext context, L10n l10n) {
    final picked = date;
    return [
      if (picked != null)
        Flexible(
          child: Text(
            MaterialLocalizations.of(context).formatCompactDate(picked),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
        ),
      IconButton(
        style: pluginActionButtonStyle,
        icon: const Icon(Icons.calendar_today),
        tooltip: l10n.plugin_pixiv_ranking_pick_date,
        onPressed: () => _pickDate(context),
      ),
      if (picked != null)
        IconButton(
          style: pluginActionButtonStyle,
          icon: const Icon(Icons.close),
          tooltip: l10n.plugin_pixiv_ranking_back_to_today,
          onPressed: () => onDate(null),
        ),
    ];
  }

  /// Shaft-style archive picker: any past day's board, one call away.
  Future<void> _pickDate(BuildContext context) async {
    final now = DateTime.now();
    final picked = await showDatePicker(
      context: context,
      initialDate: date ?? now.subtract(const Duration(days: 1)),
      // Rankings began in 2007; boards settle a day behind the calendar.
      firstDate: DateTime(2007, 9, 13),
      lastDate: now,
    );
    if (picked != null) onDate(picked);
  }
}
