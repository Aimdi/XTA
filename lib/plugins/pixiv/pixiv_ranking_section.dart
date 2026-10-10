import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_grid.dart';
import 'package:xta/plugins/pixiv/pixiv_ranking_modes.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';

/// `YYYY-MM-DD` for the archive request, or null for today's board.
String? pixivRankingDateParam(DateTime? date) {
  if (date == null) return null;
  String pad(int value) => '$value'.padLeft(2, '0');
  return '${date.year}-${pad(date.month)}-${pad(date.day)}';
}

/// Rankings: the pinned boards as chips and a past day's board, over the ranked works.
class PixivRankingSection extends StatelessWidget {
  /// The pinned boards the reader may see, in chip order.
  final List<PixivRankingMode> modes;
  final String mode;
  final DateTime? date;
  final ValueChanged<String> onMode;
  final VoidCallback onEditModes;

  /// A picked day, or null to go back to today's board.
  final ValueChanged<DateTime?> onDate;
  final PixivIllustListStore store;

  const PixivRankingSection({
    super.key,
    required this.modes,
    required this.mode,
    required this.date,
    required this.onMode,
    required this.onEditModes,
    required this.onDate,
    required this.store,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Column(
      children: [
        Row(
          children: [
            Expanded(
              child: PixivRankingModeChips(modes: modes, selected: mode, onSelected: onMode, onEdit: onEditModes),
            ),
            ..._dateControls(context, l10n),
            const SizedBox(width: 4),
          ],
        ),
        Expanded(
          child: PixivIllustFeed(store: store, emptyMessage: l10n.plugin_pixiv_ranking_empty),
        ),
      ],
    );
  }

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
