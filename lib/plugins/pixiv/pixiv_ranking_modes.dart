import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_filter_row.dart';

/// One ranking board: Pixiv's mode name, its label, and the gates it sits behind.
class PixivRankingMode {
  final String id;
  final String Function(L10n l10n) label;

  /// Offered only while Show R-18 is on.
  final bool r18;

  /// An AI board, which keeps its AI works whatever Hide AI says.
  final bool ai;

  const PixivRankingMode(this.id, this.label, {this.r18 = false, this.ai = false});
}

/// Every illustration board in the order the chips show them. `day_manga` is
/// an XTA extra; the rest are the boards Pixiv's own app offers.
final pixivIllustRankingModes = <PixivRankingMode>[
  PixivRankingMode('day', (l10n) => l10n.plugin_pixiv_ranking_day),
  PixivRankingMode('week', (l10n) => l10n.plugin_pixiv_ranking_week),
  PixivRankingMode('month', (l10n) => l10n.plugin_pixiv_ranking_month),
  PixivRankingMode('day_male', (l10n) => l10n.plugin_pixiv_ranking_day_male),
  PixivRankingMode('day_female', (l10n) => l10n.plugin_pixiv_ranking_day_female),
  PixivRankingMode('week_original', (l10n) => l10n.plugin_pixiv_ranking_week_original),
  PixivRankingMode('week_rookie', (l10n) => l10n.plugin_pixiv_ranking_rookie),
  PixivRankingMode('day_manga', (l10n) => l10n.plugin_pixiv_ranking_day_manga),
  PixivRankingMode('day_ai', (l10n) => l10n.plugin_pixiv_ranking_day_ai, ai: true),
  PixivRankingMode('day_r18', (l10n) => l10n.plugin_pixiv_ranking_day_r18, r18: true),
  PixivRankingMode('day_r18_ai', (l10n) => l10n.plugin_pixiv_ranking_day_r18_ai, r18: true, ai: true),
  PixivRankingMode('week_r18', (l10n) => l10n.plugin_pixiv_ranking_week_r18, r18: true),
  PixivRankingMode('week_r18g', (l10n) => l10n.plugin_pixiv_ranking_week_r18g, r18: true),
];

/// The boards pinned until the reader picks their own: the set XTA always had.
const pixivDefaultRankingPins = [
  'day',
  'week',
  'month',
  'day_male',
  'day_female',
  'week_original',
  'week_rookie',
  'day_manga',
];

bool pixivRankingModeIsAi(String id) => pixivIllustRankingModes.any((mode) => mode.id == id && mode.ai);

/// The boards a reader may pin: R-18 boards only while Show R-18 is on.
List<PixivRankingMode> pixivRankingModesOffered(List<PixivRankingMode> table, {required bool showR18}) =>
    table.where((mode) => showR18 || !mode.r18).toList();

/// The chips the row shows: pinned boards the reader may see, in table order,
/// and never none.
List<PixivRankingMode> pixivVisibleRankingModes(
  List<String> pins,
  List<PixivRankingMode> table, {
  required bool showR18,
}) {
  final visible = pixivRankingModesOffered(table, showR18: showR18).where((mode) => pins.contains(mode.id)).toList();
  return visible.isEmpty ? [table.first] : visible;
}

/// The board to show: the chosen one while its chip is there, else the first chip.
String pixivEffectiveRankingMode(String chosen, List<PixivRankingMode> visible) =>
    visible.any((mode) => mode.id == chosen) ? chosen : visible.first.id;

/// Pinned ids from the stored JSON, unknown and repeated ids dropped; the
/// defaults when nothing usable is stored.
List<String> parsePixivRankingPins(String? raw, List<PixivRankingMode> table, List<String> defaults) {
  final known = {for (final mode in table) mode.id};
  final List<Object?> decoded;
  try {
    decoded = switch (jsonDecode(raw ?? '')) {
      final List<Object?> list => list,
      _ => const [],
    };
  } on FormatException {
    return defaults;
  }
  final pins = decoded.whereType<String>().where(known.contains).toSet().toList();
  return pins.isEmpty ? defaults : pins;
}

/// The pinned boards, saved as a JSON list under [prefKey]. Pins hidden by
/// Show R-18 stay saved and come back when it is turned on again.
class PixivRankingPinsStore extends Store<List<String>> {
  final BasePrefService prefs;
  final String prefKey;
  final List<PixivRankingMode> table;

  PixivRankingPinsStore(
    this.prefs, {
    this.prefKey = optionPluginPixivRankingModes,
    List<PixivRankingMode>? table,
    List<String> defaults = pixivDefaultRankingPins,
  }) : table = table ?? pixivIllustRankingModes,
       super(parsePixivRankingPins(prefs.get<String>(prefKey), table ?? pixivIllustRankingModes, defaults));

  bool isPinned(String id) => state.contains(id);

  /// Pins or unpins [id], keeping the table's order.
  Future<void> toggle(String id) async {
    final next = [
      for (final mode in table)
        if (mode.id == id ? !isPinned(id) : isPinned(mode.id)) mode.id,
    ];
    await prefs.set(prefKey, jsonEncode(next));
    update(next);
  }
}

/// The pinned boards as choice chips, then an Edit chip. Novel rankings use
/// the same row over their own table.
class PixivRankingModeChips extends StatelessWidget {
  final List<PixivRankingMode> modes;
  final String selected;
  final ValueChanged<String> onSelected;
  final VoidCallback onEdit;

  const PixivRankingModeChips({
    super.key,
    required this.modes,
    required this.selected,
    required this.onSelected,
    required this.onEdit,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    // Its own storage slot, so the board below does not open at this row's sideways offset.
    return PluginFilterRow(
      key: const PageStorageKey<String>('pixiv-ranking-modes'),
      children: [
        for (final mode in modes)
          ChoiceChip(
            key: ValueKey('pixiv-ranking-mode-${mode.id}'),
            label: Text(mode.label(l10n)),
            selected: mode.id == selected,
            onSelected: (_) => onSelected(mode.id),
          ),
        ActionChip(
          key: const ValueKey('pixiv-ranking-edit'),
          avatar: const Icon(Icons.tune, size: 18),
          label: Text(l10n.plugin_pixiv_ranking_edit),
          onPressed: onEdit,
        ),
      ],
    );
  }
}

/// A sheet of every board the reader may pin; each tap saves at once. The
/// last visible pin cannot be removed, so the row never empties.
Future<void> showPixivRankingModeSheet(
  BuildContext context, {
  required PixivRankingPinsStore pins,
  required List<PixivRankingMode> offered,
}) => showModalBottomSheet<void>(
  context: context,
  showDragHandle: true,
  isScrollControlled: true,
  builder: (_) => _RankingModeSheet(pins: pins, offered: offered),
);

class _RankingModeSheet extends StatelessWidget {
  final PixivRankingPinsStore pins;
  final List<PixivRankingMode> offered;

  const _RankingModeSheet({required this.pins, required this.offered});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    return SafeArea(
      child: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(l10n.plugin_pixiv_ranking_edit_title, style: theme.textTheme.titleMedium),
            const SizedBox(height: 4),
            Text(
              l10n.plugin_pixiv_ranking_edit_hint,
              style: theme.textTheme.bodySmall!.copyWith(color: theme.colorScheme.onSurfaceVariant),
            ),
            const SizedBox(height: 12),
            ScopedBuilder<PixivRankingPinsStore, List<String>>(
              store: pins,
              onState: (context, pinned) => Wrap(spacing: 8, runSpacing: 4, children: _chips(l10n, pinned)),
            ),
          ],
        ),
      ),
    );
  }

  List<Widget> _chips(L10n l10n, List<String> pinned) {
    final visiblePins = offered.where((mode) => pinned.contains(mode.id)).length;
    return [
      for (final mode in offered)
        FilterChip(
          key: ValueKey('pixiv-ranking-pin-${mode.id}'),
          label: Text(mode.label(l10n)),
          selected: pinned.contains(mode.id),
          onSelected: pinned.contains(mode.id) && visiblePins <= 1 ? null : (_) => pins.toggle(mode.id),
        ),
    ];
  }
}
