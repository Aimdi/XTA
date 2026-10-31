import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:intl/intl.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/booru/booru_engines.dart';
import 'package:xta/plugins/booru/booru_metatags.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_query.dart';
import 'package:xta/plugins/booru/booru_search_store.dart';
import 'package:xta/plugins/booru/booru_store.dart';
import 'package:xta/plugins/booru/booru_tag_style.dart';
import 'package:xta/plugins/plugin_search_history.dart';
import 'package:xta/utils/formatting_locale.dart';

/// What sits under the field while editing: suggestions for the tag being
/// typed, otherwise quick operators, saved searches and recent searches.
class BooruSearchLanding extends StatelessWidget {
  final BooruSearchStore store;
  final BooruSearchState state;
  final BooruTagsStore saved;
  final BooruEngine engine;
  final String host;
  final BooruRating maxRating;
  final ValueChanged<String> onRun;
  final ValueChanged<BooruTagSuggestion> onPick;

  /// A metatag's name with its colon, to be started in the field.
  final ValueChanged<String> onMetatag;
  final ValueChanged<String> onOperator;
  final VoidCallback onEditAsText;
  final VoidCallback onClear;

  const BooruSearchLanding({
    super.key,
    required this.store,
    required this.state,
    required this.saved,
    required this.engine,
    required this.host,
    required this.maxRating,
    required this.onRun,
    required this.onPick,
    required this.onMetatag,
    required this.onOperator,
    required this.onEditAsText,
    required this.onClear,
  });

  @override
  Widget build(BuildContext context) {
    if (state.input.isNotEmpty && state.suggestions.isNotEmpty) {
      return _Suggestions(state: state, onPick: onPick);
    }
    return ListView(
      keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
      padding: const EdgeInsets.only(bottom: 24),
      children: [
        _QueryTools(
          store: store,
          state: state,
          engine: engine,
          host: host,
          maxRating: maxRating,
          onOperator: onOperator,
          onEditAsText: onEditAsText,
          onClear: onClear,
        ),
        _Metatags(engine: engine, onTap: onMetatag),
        _SavedSearches(saved: saved, onRun: onRun),
        _RecentSearches(history: store.history, onRun: onRun),
      ],
    );
  }
}

class _Suggestions extends StatelessWidget {
  final BooruSearchState state;
  final ValueChanged<BooruTagSuggestion> onPick;

  const _Suggestions({required this.state, required this.onPick});

  @override
  Widget build(BuildContext context) {
    final prefix = booruSuggestionPrefix(state.input) ?? '';
    return ListView.builder(
      keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
      padding: const EdgeInsets.symmetric(vertical: 4),
      itemCount: state.suggestions.length,
      itemBuilder: (context, index) =>
          _SuggestionTile(suggestion: state.suggestions[index], prefix: prefix, onTap: onPick),
    );
  }
}

class _SuggestionTile extends StatelessWidget {
  final BooruTagSuggestion suggestion;
  final String prefix;
  final ValueChanged<BooruTagSuggestion> onTap;

  const _SuggestionTile({required this.suggestion, required this.prefix, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final color = booruTagColor(suggestion.category, theme.colorScheme);
    final muted = theme.colorScheme.onSurfaceVariant;
    final alias = suggestion.alias;
    final count = suggestion.postCount;
    return InkWell(
      onTap: () => onTap(suggestion),
      child: ConstrainedBox(
        constraints: const BoxConstraints(minHeight: 48),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
          child: Row(
            children: [
              Expanded(
                child: Text.rich(
                  TextSpan(
                    style: theme.textTheme.bodyLarge?.copyWith(color: color),
                    children: [
                      if (alias != null) ...[
                        _matched(alias, style: TextStyle(color: muted)),
                        WidgetSpan(
                          alignment: PlaceholderAlignment.middle,
                          child: Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 4),
                            child: Icon(Icons.arrow_right_alt, size: 18, color: muted),
                          ),
                        ),
                        TextSpan(text: suggestion.name),
                      ] else
                        _matched(suggestion.name),
                    ],
                  ),
                ),
              ),
              if (count != null)
                Padding(
                  padding: const EdgeInsetsDirectional.only(start: 12),
                  child: Text(
                    _postCount(context, count),
                    semanticsLabel: L10n.of(context).plugin_booru_tag_count(count),
                    style: theme.textTheme.bodyMedium?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }

  /// [text] with the word that starts with what is typed in bold.
  TextSpan _matched(String text, {TextStyle? style}) {
    final start = booruMatchStart(text, prefix);
    if (start == null) return TextSpan(text: text, style: style);
    final end = start + prefix.length;
    return TextSpan(
      style: style,
      children: [
        TextSpan(text: text.substring(0, start)),
        TextSpan(
          text: text.substring(start, end),
          style: const TextStyle(fontWeight: FontWeight.w700),
        ),
        TextSpan(text: text.substring(end)),
      ],
    );
  }
}

/// Short counts stay exact; some locales do not abbreviate thousands at all.
String _postCount(BuildContext context, int count) {
  final locale = formattingLocale(context);
  return count < 1000000
      ? NumberFormat.decimalPattern(locale).format(count)
      : NumberFormat.compact(locale: locale).format(count);
}

/// Operators and metatags that are awkward to type on a phone keyboard.
class _QueryTools extends StatelessWidget {
  final BooruSearchStore store;
  final BooruSearchState state;
  final BooruEngine engine;
  final String host;
  final BooruRating maxRating;
  final ValueChanged<String> onOperator;
  final VoidCallback onEditAsText;
  final VoidCallback onClear;

  const _QueryTools({
    required this.store,
    required this.state,
    required this.engine,
    required this.host,
    required this.maxRating,
    required this.onOperator,
    required this.onEditAsText,
    required this.onClear,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final ratings = [
      for (final rating in BooruRating.values)
        if (!rating.exceeds(maxRating)) ?booruRatingMetatag(engine, rating, host: host),
    ];
    final order = booruScoreOrderMetatag(engine);
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      padding: const EdgeInsets.fromLTRB(12, 8, 12, 4),
      child: Row(
        children: [
          _tool(label: Text(l10n.plugin_booru_exclude_tag), icon: Icons.remove, onPressed: () => onOperator('-')),
          if (booruSupportsEither(engine))
            _tool(label: Text(l10n.plugin_booru_either_tag), icon: Icons.alt_route, onPressed: () => onOperator('~')),
          for (final rating in ratings)
            _tool(label: Text(rating), selected: state.tags.contains(rating), onPressed: () => _toggle(rating)),
          _tool(
            label: Text(l10n.plugin_booru_order_score),
            icon: Icons.trending_up,
            selected: state.tags.contains(order),
            onPressed: () => _toggle(order),
          ),
          _tool(label: Text(l10n.plugin_booru_edit_as_text), icon: Icons.edit_note, onPressed: onEditAsText),
          if (state.tags.isNotEmpty)
            _tool(label: Text(l10n.plugin_booru_clear_tags), icon: Icons.clear_all, onPressed: onClear),
        ],
      ),
    );
  }

  void _toggle(String token) => state.tags.contains(token) ? store.removeTag(token) : store.addTags([token]);

  Widget _tool({required Widget label, IconData? icon, bool selected = false, required VoidCallback onPressed}) {
    return Padding(
      padding: const EdgeInsetsDirectional.only(end: 8),
      child: FilterChip(
        label: label,
        avatar: icon == null ? null : Icon(icon, size: 18),
        showCheckmark: false,
        selected: selected,
        materialTapTargetSize: MaterialTapTargetSize.padded,
        onSelected: (_) => onPressed(),
      ),
    );
  }
}

/// The metatags the site's engine understands; a tap starts one in the
/// field, where its known values are then offered.
class _Metatags extends StatelessWidget {
  final BooruEngine engine;
  final ValueChanged<String> onTap;

  const _Metatags({required this.engine, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Semantics(
            header: true,
            child: Text(L10n.of(context).plugin_booru_metatags, style: theme.textTheme.titleSmall),
          ),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            runSpacing: 4,
            children: [
              for (final name in booruEngineMetatags(engine))
                ActionChip(
                  label: Text('$name:'),
                  materialTapTargetSize: MaterialTapTargetSize.padded,
                  onPressed: () => onTap('$name:'),
                ),
            ],
          ),
        ],
      ),
    );
  }
}

class _SavedSearches extends StatelessWidget {
  final BooruTagsStore saved;
  final ValueChanged<String> onRun;

  const _SavedSearches({required this.saved, required this.onRun});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return ScopedBuilder<BooruTagsStore, List<String>>(
      store: saved,
      onState: (context, queries) {
        if (queries.isEmpty) return const SizedBox.shrink();
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            _SectionTitle(title: L10n.of(context).plugin_booru_saved_searches),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 12),
              child: Wrap(
                spacing: 8,
                children: [
                  for (final query in queries)
                    ActionChip(
                      key: ValueKey('booru-saved-$query'),
                      avatar: Icon(Icons.star_rounded, size: 18, color: theme.colorScheme.primary),
                      label: Text.rich(booruQuerySpan(query, theme)),
                      materialTapTargetSize: MaterialTapTargetSize.padded,
                      onPressed: () => onRun(query),
                    ),
                ],
              ),
            ),
          ],
        );
      },
    );
  }
}

class _RecentSearches extends StatelessWidget {
  final PluginSearchHistoryStore history;
  final ValueChanged<String> onRun;

  const _RecentSearches({required this.history, required this.onRun});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    return ScopedBuilder<PluginSearchHistoryStore, List<String>>(
      store: history,
      onState: (context, queries) => Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _SectionTitle(
            title: l10n.plugin_booru_search_history,
            trailing: queries.isEmpty
                ? null
                : IconButton(
                    tooltip: l10n.plugin_booru_clear_history,
                    icon: const Icon(Icons.delete_sweep_outlined),
                    onPressed: history.clear,
                  ),
          ),
          if (queries.isEmpty)
            ListTile(title: Text(l10n.plugin_booru_search_history_empty))
          else
            for (final query in queries)
              Dismissible(
                key: ValueKey('booru-history-$query'),
                onDismissed: (_) => history.forget(query),
                child: ListTile(
                  leading: const Icon(Icons.history),
                  title: Text.rich(booruQuerySpan(query, theme)),
                  trailing: IconButton(
                    tooltip: l10n.delete,
                    icon: const Icon(Icons.close),
                    onPressed: () => history.forget(query),
                  ),
                  onTap: () => onRun(query),
                ),
              ),
        ],
      ),
    );
  }
}

class _SectionTitle extends StatelessWidget {
  final String title;
  final Widget? trailing;

  const _SectionTitle({required this.title, this.trailing});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return ConstrainedBox(
      constraints: const BoxConstraints(minHeight: 48),
      child: Padding(
        padding: const EdgeInsetsDirectional.fromSTEB(16, 8, 8, 0),
        child: Row(
          children: [
            Expanded(
              child: Text(title, style: theme.textTheme.titleSmall?.copyWith(color: theme.colorScheme.primary)),
            ),
            ?trailing,
          ],
        ),
      ),
    );
  }
}
