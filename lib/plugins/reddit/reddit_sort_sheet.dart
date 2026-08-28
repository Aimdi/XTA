import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';

/// What each sort is called, and the glyph Reddit's own apps use for it.
({String label, IconData icon}) redditSortLabel(
  BuildContext context,
  RedditSort sort,
) {
  final l10n = L10n.of(context);

  return switch (sort) {
    RedditSort.hot => (
      label: l10n.plugin_reddit_sort_hot,
      icon: Icons.local_fire_department_outlined,
    ),
    RedditSort.newest => (
      label: l10n.plugin_reddit_sort_new,
      icon: Icons.auto_awesome_outlined,
    ),
    RedditSort.top => (
      label: l10n.plugin_reddit_sort_top,
      icon: Icons.bar_chart,
    ),
    RedditSort.rising => (
      label: l10n.plugin_reddit_sort_rising,
      icon: Icons.trending_up,
    ),
    RedditSort.controversial => (
      label: l10n.plugin_reddit_sort_controversial,
      icon: Icons.bolt_outlined,
    ),
  };
}

String redditSortTitle(
  BuildContext context,
  RedditSort sort,
  RedditTimeFilter timeFilter,
) {
  final label = redditSortLabel(context, sort).label;
  return redditSortUsesTimeFilter(sort)
      ? '$label · ${redditTimeFilterLabel(context, timeFilter)}'
      : label;
}

String redditTimeFilterLabel(
  BuildContext context,
  RedditTimeFilter timeFilter,
) {
  final l10n = L10n.of(context);

  return switch (timeFilter) {
    RedditTimeFilter.hour => l10n.plugin_reddit_time_hour,
    RedditTimeFilter.day => l10n.plugin_reddit_time_day,
    RedditTimeFilter.week => l10n.plugin_reddit_time_week,
    RedditTimeFilter.month => l10n.plugin_reddit_time_month,
    RedditTimeFilter.year => l10n.plugin_reddit_time_year,
    RedditTimeFilter.all => l10n.plugin_reddit_time_all,
  };
}

/// The sort every Reddit listing uses. One stored choice rather than one per
/// screen: a reader who wants New wants it everywhere, and a per-screen setting
/// would only be somewhere else to look when the feed surprises them.
RedditSort storedRedditSort(BasePrefService prefs) =>
    redditSortFromName(prefs.get<String>(optionPluginRedditSort));

RedditTimeFilter storedRedditTimeFilter(BasePrefService prefs) =>
    redditTimeFilterFromName(prefs.get<String>(optionPluginRedditTimeFilter));

RedditNsfwMode storedRedditNsfwMode(BasePrefService prefs) =>
    redditNsfwModeFromName(prefs.get<String>(optionPluginRedditNsfwMode));

bool storedRedditShowSpoilers(BasePrefService prefs) =>
    prefs.get<bool>(optionPluginRedditShowSpoilers) == true;

RedditFeedMode storedRedditFeedMode(BasePrefService prefs) =>
    redditFeedModeFromName(prefs.get<String>(optionPluginRedditFeedMode));

/// Asks for a sort and stores it. Returns the choice, or null if dismissed.
Future<RedditSort?> openRedditSortSheet(BuildContext context) async {
  final prefs = PrefService.of(context, listen: false);
  final current = storedRedditSort(prefs);

  final chosen = await showModalBottomSheet<RedditSort>(
    context: context,
    showDragHandle: true,
    builder: (sheetContext) =>
        SafeArea(child: _RedditSortSheet(current: current)),
  );

  if (chosen != null) {
    await prefs.set(optionPluginRedditSort, chosen.name);
    if (context.mounted && redditSortUsesTimeFilter(chosen)) {
      final time = await showModalBottomSheet<RedditTimeFilter>(
        context: context,
        showDragHandle: true,
        builder: (sheetContext) => SafeArea(
          child: _RedditTimeFilterSheet(current: storedRedditTimeFilter(prefs)),
        ),
      );
      if (time != null) {
        await prefs.set(optionPluginRedditTimeFilter, time.name);
      }
    }
  }
  return chosen;
}

class _RedditSortSheet extends StatelessWidget {
  final RedditSort current;

  const _RedditSortSheet({required this.current});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Padding(
          padding: const EdgeInsets.only(bottom: 16),
          child: Text(
            L10n.of(context).plugin_reddit_sort,
            style: theme.textTheme.titleLarge,
          ),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
          child: Wrap(
            spacing: 10,
            runSpacing: 10,
            alignment: WrapAlignment.center,
            children: [
              for (final sort in RedditSort.values)
                _RedditSortChip(sort: sort, selected: sort == current),
            ],
          ),
        ),
      ],
    );
  }
}

class _RedditTimeFilterSheet extends StatelessWidget {
  final RedditTimeFilter current;

  const _RedditTimeFilterSheet({required this.current});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Padding(
          padding: const EdgeInsets.only(bottom: 16),
          child: Text(
            L10n.of(context).plugin_reddit_time_filter,
            style: theme.textTheme.titleLarge,
          ),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
          child: Wrap(
            spacing: 10,
            runSpacing: 10,
            alignment: WrapAlignment.center,
            children: [
              for (final filter in RedditTimeFilter.values)
                _RedditTimeFilterChip(
                  filter: filter,
                  selected: filter == current,
                ),
            ],
          ),
        ),
      ],
    );
  }
}

class _RedditTimeFilterChip extends StatelessWidget {
  final RedditTimeFilter filter;
  final bool selected;

  const _RedditTimeFilterChip({required this.filter, required this.selected});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final tint = selected
        ? theme.colorScheme.primary
        : theme.colorScheme.onSurface;

    return ActionChip(
      label: Text(
        redditTimeFilterLabel(context, filter),
        style: TextStyle(color: tint),
      ),
      shape: const StadiumBorder(),
      backgroundColor: theme.colorScheme.surfaceContainerHighest,
      side: BorderSide.none,
      onPressed: () => Navigator.pop(context, filter),
    );
  }
}

class _RedditSortChip extends StatelessWidget {
  final RedditSort sort;
  final bool selected;

  const _RedditSortChip({required this.sort, required this.selected});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final entry = redditSortLabel(context, sort);
    final tint = selected
        ? theme.colorScheme.primary
        : theme.colorScheme.onSurface;

    return ActionChip(
      avatar: Icon(entry.icon, size: 20, color: tint),
      label: Text(entry.label, style: TextStyle(color: tint)),
      shape: const StadiumBorder(),
      backgroundColor: theme.colorScheme.surfaceContainerHighest,
      side: BorderSide.none,
      onPressed: () => Navigator.pop(context, sort),
    );
  }
}
