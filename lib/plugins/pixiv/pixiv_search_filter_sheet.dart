import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:intl/intl.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_search_filters.dart';
import 'package:xta/utils/number_locale.dart';
import 'package:xta/utils/reader_value_store.dart';

/// What the filter sheet hands back: the filter, and whether to keep it.
typedef PixivFilterChoice = ({PixivSearchFilter filter, bool remember});

String pixivSearchTargetLabel(L10n l10n, PixivSearchTarget target) => switch (target) {
  PixivSearchTarget.partialTags => l10n.plugin_pixiv_search_target_partial,
  PixivSearchTarget.exactTags => l10n.plugin_pixiv_search_target_exact,
  PixivSearchTarget.titleCaption => l10n.plugin_pixiv_search_target_title,
  PixivSearchTarget.text => l10n.plugin_pixiv_search_target_text,
  PixivSearchTarget.keyword => l10n.plugin_pixiv_search_target_keyword,
};

String pixivSearchSortLabel(L10n l10n, PixivSearchSort sort) => switch (sort) {
  PixivSearchSort.newest => l10n.plugin_pixiv_search_sort_newest,
  PixivSearchSort.oldest => l10n.plugin_pixiv_search_sort_oldest,
  PixivSearchSort.popular => l10n.plugin_pixiv_search_sort_popular,
  PixivSearchSort.popularMale => l10n.plugin_pixiv_search_sort_popular_male,
  PixivSearchSort.popularFemale => l10n.plugin_pixiv_search_sort_popular_female,
};

String pixivUgoiraFilterLabel(L10n l10n, PixivUgoiraFilter filter) => switch (filter) {
  PixivUgoiraFilter.all => l10n.plugin_pixiv_search_ugoira_all,
  PixivUgoiraFilter.only => l10n.plugin_pixiv_search_ugoira_only,
  PixivUgoiraFilter.none => l10n.plugin_pixiv_search_ugoira_none,
};

String pixivDatePresetLabel(L10n l10n, PixivDatePreset preset) => switch (preset) {
  PixivDatePreset.any => l10n.plugin_pixiv_search_date_any,
  PixivDatePreset.day => l10n.plugin_pixiv_search_date_day,
  PixivDatePreset.week => l10n.plugin_pixiv_search_date_week,
  PixivDatePreset.month => l10n.plugin_pixiv_search_date_month,
  PixivDatePreset.halfYear => l10n.plugin_pixiv_search_date_half_year,
  PixivDatePreset.year => l10n.plugin_pixiv_search_date_year,
  PixivDatePreset.custom => l10n.plugin_pixiv_search_date_custom,
};

String _count(BuildContext context, int value) =>
    NumberFormat.decimalPattern(numberFormatLocale(context)).format(value);

String pixivUsersIriLabel(BuildContext context, int threshold) =>
    L10n.of(context).plugin_pixiv_search_users_iri(_count(context, threshold));

String pixivBookmarkRangeLabel(BuildContext context, PixivBookmarkRange range) {
  final l10n = L10n.of(context);
  final max = range.max;
  return max == null
      ? l10n.plugin_pixiv_search_bookmarks_min(_count(context, range.min))
      : l10n.plugin_pixiv_search_bookmarks_range(_count(context, range.min), _count(context, max));
}

/// The bar over search results: the filter sheet, posting dates, popularity
/// and, for Premium searching works, a bookmark-count bracket. Each control is
/// tinted while it narrows the search.
class PixivSearchFilterBar extends StatelessWidget {
  final PixivSearchFilter filter;
  final bool isPremium;
  final PixivSearchKind kind;

  /// False while the results cannot be narrowed by date, which hides the menu.
  final bool datesApply;
  final ValueChanged<PixivSearchFilter> onChanged;
  final VoidCallback onOpenSheet;

  /// What the sheet's choices are compared with to tint its button.
  final PixivSearchFilter base;

  const PixivSearchFilterBar({
    super.key,
    required this.filter,
    required this.isPremium,
    required this.onChanged,
    required this.onOpenSheet,
    this.kind = PixivSearchKind.works,
    this.datesApply = true,
    this.base = const PixivSearchFilter(),
  });

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      padding: const EdgeInsets.fromLTRB(12, 4, 12, 4),
      child: Row(
        spacing: 8,
        children: [
          _FilterButton(
            key: const ValueKey('pixiv-search-filters'),
            icon: Icons.tune,
            label: l10n.filters,
            active: filter.sheetDiffersFrom(base),
            onPressed: onOpenSheet,
          ),
          if (datesApply) _dateMenu(context, l10n),
          _popularityMenu(context, l10n),
          if (isPremium && kind.isWorks) _bookmarksMenu(context, l10n),
        ],
      ),
    );
  }

  Widget _dateMenu(BuildContext context, L10n l10n) {
    final preset = filter.datePreset;
    final range = filter.customRange;
    final label = switch (preset) {
      PixivDatePreset.any => l10n.plugin_pixiv_search_date,
      PixivDatePreset.custom when range != null => _rangeLabel(context, range),
      _ => pixivDatePresetLabel(l10n, preset),
    };
    return PopupMenuButton<PixivDatePreset>(
      key: const ValueKey('pixiv-search-date'),
      tooltip: l10n.plugin_pixiv_search_date,
      initialValue: preset,
      onSelected: (choice) => _pickDate(context, choice),
      itemBuilder: (_) => [
        for (final choice in PixivDatePreset.values)
          PopupMenuItem(value: choice, child: Text(pixivDatePresetLabel(l10n, choice))),
      ],
      child: _FilterButton(icon: Icons.calendar_month_outlined, label: label, active: preset != PixivDatePreset.any),
    );
  }

  String _rangeLabel(BuildContext context, PixivDateRange range) {
    final material = MaterialLocalizations.of(context);
    return L10n.of(
      context,
    ).plugin_pixiv_search_date_range(material.formatCompactDate(range.start), material.formatCompactDate(range.end));
  }

  Future<void> _pickDate(BuildContext context, PixivDatePreset preset) async {
    if (preset != PixivDatePreset.custom) {
      onChanged(filter.withDates(preset));
      return;
    }
    final now = DateTime.now();
    final current = filter.customRange;
    final picked = await showDateRangePicker(
      context: context,
      firstDate: pixivSearchEarliestDate,
      lastDate: now,
      initialDateRange: current == null ? null : DateTimeRange(start: current.start, end: current.end),
    );
    if (picked != null) onChanged(filter.withDates(preset, custom: PixivDateRange(picked.start, picked.end)));
  }

  Widget _popularityMenu(BuildContext context, L10n l10n) {
    final threshold = filter.usersIri;
    return PopupMenuButton<int>(
      key: const ValueKey('pixiv-search-popularity'),
      tooltip: l10n.plugin_pixiv_search_popularity,
      initialValue: threshold ?? 0,
      onSelected: (choice) => onChanged(filter.withUsersIri(choice == 0 ? null : choice)),
      itemBuilder: (_) => [
        PopupMenuItem(value: 0, child: Text(l10n.plugin_pixiv_search_popularity_any)),
        for (final value in pixivUsersIriThresholds)
          PopupMenuItem(value: value, child: Text(pixivUsersIriLabel(context, value))),
      ],
      child: _FilterButton(
        icon: Icons.local_fire_department_outlined,
        label: threshold == null ? l10n.plugin_pixiv_search_popularity : pixivUsersIriLabel(context, threshold),
        active: threshold != null,
      ),
    );
  }

  Widget _bookmarksMenu(BuildContext context, L10n l10n) {
    final range = filter.bookmarks;
    return PopupMenuButton<int>(
      key: const ValueKey('pixiv-search-bookmarks'),
      tooltip: l10n.plugin_pixiv_search_bookmarks,
      initialValue: range == null ? -1 : pixivBookmarkRanges.indexOf(range),
      onSelected: (index) => onChanged(filter.withBookmarks(index < 0 ? null : pixivBookmarkRanges[index])),
      itemBuilder: (_) => [
        PopupMenuItem(value: -1, child: Text(l10n.plugin_pixiv_search_bookmarks_any)),
        for (var index = 0; index < pixivBookmarkRanges.length; index++)
          PopupMenuItem(value: index, child: Text(pixivBookmarkRangeLabel(context, pixivBookmarkRanges[index]))),
      ],
      child: _FilterButton(
        icon: Icons.bookmark_border,
        label: range == null ? l10n.plugin_pixiv_search_bookmarks : pixivBookmarkRangeLabel(context, range),
        active: range != null,
      ),
    );
  }
}

/// A 48 dp pill: tonal while its filter narrows the search, outlined while not.
/// Without [onPressed] it only draws, for a menu button that handles the tap.
class _FilterButton extends StatelessWidget {
  final IconData icon;
  final String label;
  final bool active;
  final VoidCallback? onPressed;

  const _FilterButton({super.key, required this.icon, required this.label, required this.active, this.onPressed});

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final foreground = active ? scheme.onSecondaryContainer : scheme.onSurfaceVariant;
    final pill = Container(
      constraints: const BoxConstraints(minHeight: 40, maxWidth: 240),
      padding: const EdgeInsets.symmetric(horizontal: 12),
      decoration: BoxDecoration(
        color: active ? scheme.secondaryContainer : null,
        border: Border.all(color: active ? scheme.secondaryContainer : scheme.outlineVariant),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        spacing: 6,
        children: [
          Icon(icon, size: 18, color: active ? scheme.primary : foreground),
          Flexible(
            child: Text(
              label,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.labelLarge?.copyWith(color: foreground),
            ),
          ),
        ],
      ),
    );
    final target = ConstrainedBox(
      constraints: const BoxConstraints(minHeight: kMinInteractiveDimension),
      child: Center(widthFactor: 1, child: pill),
    );
    final tap = onPressed;
    if (tap == null) return target;
    return Semantics(
      button: true,
      selected: active,
      child: InkWell(borderRadius: BorderRadius.circular(24), onTap: tap, child: target),
    );
  }
}

/// The sheet behind Filters: where to look, the order, AI works, ugoira for
/// works, and whether to keep the choices. Each [kind] offers its own places
/// to look and orders.
Future<PixivFilterChoice?> showPixivSearchFilterSheet(
  BuildContext context, {
  required PixivSearchFilter filter,
  required bool remembered,
  required bool isPremium,
  PixivSearchKind kind = PixivSearchKind.works,
}) => showModalBottomSheet<PixivFilterChoice>(
  context: context,
  isScrollControlled: true,
  showDragHandle: true,
  builder: (_) =>
      PixivSearchFilterSheet(initial: (filter: filter, remember: remembered), isPremium: isPremium, kind: kind),
);

class PixivSearchFilterSheet extends StatefulWidget {
  final PixivFilterChoice initial;
  final bool isPremium;
  final PixivSearchKind kind;

  const PixivSearchFilterSheet({
    super.key,
    required this.initial,
    required this.isPremium,
    this.kind = PixivSearchKind.works,
  });

  @override
  State<PixivSearchFilterSheet> createState() => _PixivSearchFilterSheetState();
}

class _PixivSearchFilterSheetState extends State<PixivSearchFilterSheet> {
  late final _draft = ReaderValueStore<PixivFilterChoice>(widget.initial);

  @override
  void dispose() {
    _draft.destroy();
    super.dispose();
  }

  void _edit(PixivSearchFilter filter) => _draft.update((filter: filter, remember: _draft.state.remember));

  @override
  Widget build(BuildContext context) => ScopedBuilder<ReaderValueStore<PixivFilterChoice>, PixivFilterChoice>(
    store: _draft,
    onState: (context, draft) => SafeArea(
      child: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: _sections(context, draft),
        ),
      ),
    ),
  );

  List<Widget> _sections(BuildContext context, PixivFilterChoice draft) {
    final l10n = L10n.of(context);
    final filter = draft.filter;
    final kind = widget.kind;
    final sorts = pixivSearchSorts(isPremium: widget.isPremium, kind: kind);
    return [
      Text(l10n.plugin_pixiv_search_filters_title, style: Theme.of(context).textTheme.titleLarge),
      _heading(context, l10n.plugin_pixiv_search_target),
      _choices(kind.targets, filter.target, (t) => pixivSearchTargetLabel(l10n, t), (t) {
        _edit(filter.copyWith(target: t));
      }),
      _heading(context, l10n.plugin_pixiv_search_sort),
      _choices(sorts, filter.sort, (s) => pixivSearchSortLabel(l10n, s), (s) => _edit(filter.copyWith(sort: s))),
      if (!widget.isPremium)
        _note(
          context,
          kind.isWorks ? l10n.plugin_pixiv_search_premium_note : l10n.plugin_pixiv_novel_search_premium_note,
        ),
      if (kind.isWorks) ...[
        _heading(context, l10n.plugin_pixiv_ugoira),
        _choices(PixivUgoiraFilter.values, filter.ugoira, (u) => pixivUgoiraFilterLabel(l10n, u), (u) {
          _edit(filter.copyWith(ugoira: u));
        }),
      ],
      const SizedBox(height: 8),
      SwitchListTile(
        contentPadding: EdgeInsets.zero,
        title: Text(l10n.plugin_pixiv_hide_ai),
        value: filter.hideAi,
        onChanged: (hide) => _edit(filter.copyWith(hideAi: hide)),
      ),
      SwitchListTile(
        contentPadding: EdgeInsets.zero,
        title: Text(l10n.plugin_pixiv_search_remember),
        value: draft.remember,
        onChanged: (keep) => _draft.update((filter: filter, remember: keep)),
      ),
      const SizedBox(height: 8),
      _actions(context, l10n, draft),
    ];
  }

  Widget _heading(BuildContext context, String text) => Padding(
    padding: const EdgeInsets.only(top: 16, bottom: 8),
    child: Text(text, style: Theme.of(context).textTheme.titleSmall),
  );

  Widget _note(BuildContext context, String text) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.only(top: 8),
      child: Text(text, style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
    );
  }

  Widget _choices<T>(List<T> values, T selected, String Function(T value) label, ValueChanged<T> onSelected) => Wrap(
    spacing: 8,
    runSpacing: 4,
    children: [
      for (final value in values)
        ChoiceChip(label: Text(label(value)), selected: value == selected, onSelected: (_) => onSelected(value)),
    ],
  );

  Widget _actions(BuildContext context, L10n l10n, PixivFilterChoice draft) => Wrap(
    alignment: WrapAlignment.end,
    spacing: 8,
    children: [
      TextButton(onPressed: () => Navigator.pop(context), child: Text(l10n.cancel)),
      FilledButton(
        key: const ValueKey('pixiv-search-filters-apply'),
        onPressed: () => Navigator.pop(context, draft),
        child: Text(l10n.sort_ungrouped_apply),
      ),
    ],
  );
}
