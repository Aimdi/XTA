import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/ehviewer/eh_client.dart';
import 'package:xta/plugins/ehviewer/eh_errors.dart';
import 'package:xta/plugins/ehviewer/eh_grid.dart';
import 'package:xta/plugins/ehviewer/eh_models.dart';
import 'package:xta/plugins/ehviewer/eh_search_store.dart';
import 'package:xta/plugins/ehviewer/eh_store.dart';
import 'package:xta/plugins/ehviewer/eh_tags.dart';
import 'package:xta/plugins/ehviewer/eh_ui.dart';
import 'package:xta/plugins/plugin_filter_row.dart';
import 'package:xta/plugins/plugin_search_history.dart';
import 'package:xta/plugins/plugin_tag_chip.dart';
import 'package:xta/ui/errors.dart';

/// Search with the site's own tag suggestions, category, rating and language
/// filters, and the searches run before.
class EhSearchScreen extends StatefulWidget {
  final String? initialQuery;

  const EhSearchScreen({super.key, this.initialQuery});

  @override
  State<EhSearchScreen> createState() => _EhSearchScreenState();
}

class _EhSearchScreenState extends State<EhSearchScreen> {
  late final PluginSearchHistoryStore _history;
  late final EhSearchStore _store;
  late final TextEditingController _controller;

  @override
  void initState() {
    super.initState();
    final initial = widget.initialQuery?.trim() ?? '';
    _controller = TextEditingController(
      text: initial.isEmpty ? '' : '$initial ',
    );
    _history = PluginSearchHistoryStore(
      PrefService.of(context, listen: false),
      optionPluginEhSearchHistory,
    );
    _store = EhSearchStore(context.read<EhClient>(), _history);
    if (initial.isNotEmpty) unawaited(_store.search(initial));
  }

  @override
  void dispose() {
    _controller.dispose();
    unawaited(_store.destroy());
    unawaited(_history.destroy());
    super.dispose();
  }

  void _run(String query) {
    FocusScope.of(context).unfocus();
    unawaited(_store.search(query));
  }

  void _pick(EhTag tag) {
    final text = _store.pick(tag);
    _controller.value = TextEditingValue(
      text: text,
      selection: TextSelection.collapsed(offset: text.length),
    );
  }

  void _fromHistory(String query) {
    _controller.text = query;
    _run(query);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return ScopedBuilder<EhSearchStore, EhSearchState>(
      store: _store,
      onState: (context, state) => Scaffold(
        appBar: AppBar(
          title: TextField(
            key: const ValueKey('eh-search-field'),
            controller: _controller,
            autofocus: (widget.initialQuery ?? '').trim().isEmpty,
            autocorrect: false,
            textInputAction: TextInputAction.search,
            decoration: InputDecoration(
              hintText: l10n.plugin_eh_search_hint,
              border: InputBorder.none,
              suffixIcon: IconButton(
                tooltip: l10n.search,
                icon: const Icon(Icons.search),
                onPressed: () => _run(_controller.text),
              ),
            ),
            onChanged: _store.type,
            onSubmitted: _run,
          ),
        ),
        body: Column(
          children: [
            _EhSearchFilters(store: _store, state: state),
            const Divider(height: 1),
            Expanded(child: _body(state)),
          ],
        ),
      ),
    );
  }

  Widget _body(EhSearchState state) {
    final results = _store.results;
    if (state.suggestions.isNotEmpty) {
      return _EhSuggestions(tags: state.suggestions, onPick: _pick);
    }
    if (results == null) {
      return _EhSearchHistory(history: _history, onRun: _fromHistory);
    }
    return _EhSearchResults(key: ObjectKey(results), results: results);
  }
}

/// Category, minimum rating and language, each one a tap away.
class _EhSearchFilters extends StatelessWidget {
  final EhSearchStore store;
  final EhSearchState state;

  const _EhSearchFilters({required this.store, required this.state});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Column(
      children: [
        PluginFilterRow(
          children: [
            for (final category in EhCategory.values)
              FilterChip(
                avatar: CircleAvatar(
                  radius: 6,
                  backgroundColor: ehCategoryColor(category),
                ),
                label: Text(category.label),
                selected: state.categories.contains(category),
                showCheckmark: false,
                onSelected: (_) => store.toggleCategory(category),
              ),
          ],
        ),
        PluginFilterRow(
          children: [
            Text(l10n.plugin_eh_min_rating),
            for (final stars in const [2, 3, 4, 5])
              ChoiceChip(
                key: ValueKey('eh-rating-$stars'),
                avatar: const Icon(Icons.star_rounded, size: 18),
                label: Text(
                  '$stars',
                  semanticsLabel: l10n.plugin_eh_rating('$stars'),
                ),
                selected: state.minRating == stars,
                showCheckmark: false,
                onSelected: (_) => store.setMinRating(stars),
              ),
            for (final language in EhSearchLanguage.values)
              ChoiceChip(
                label: Text(ehLanguageLabel(l10n, language)),
                selected: state.language == language,
                onSelected: (_) => store.setLanguage(language),
              ),
          ],
        ),
      ],
    );
  }
}

/// The site's tags for the term being typed, in their namespace colours.
class _EhSuggestions extends StatelessWidget {
  final List<EhTag> tags;
  final ValueChanged<EhTag> onPick;

  const _EhSuggestions({required this.tags, required this.onPick});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    return ListView.builder(
      keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
      itemCount: tags.length,
      itemBuilder: (context, index) {
        final tag = tags[index];
        final color = pluginTagKindColor(
          ehTagKind(tag.namespace),
          theme.colorScheme,
        );
        return ListTile(
          key: ValueKey('eh-suggestion-${tag.raw}'),
          title: Text(tag.name, style: TextStyle(color: color)),
          trailing: Text(
            ehNamespaceLabel(l10n, tag.namespace),
            style: theme.textTheme.labelMedium?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
          onTap: () => onPick(tag),
        );
      },
    );
  }
}

class _EhSearchHistory extends StatelessWidget {
  final PluginSearchHistoryStore history;
  final ValueChanged<String> onRun;

  const _EhSearchHistory({required this.history, required this.onRun});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return ScopedBuilder<PluginSearchHistoryStore, List<String>>(
      store: history,
      onState: (context, queries) => ListView(
        keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
        children: [
          if (queries.isNotEmpty)
            ListTile(
              title: Text(
                l10n.plugin_eh_tab_history,
                style: Theme.of(context).textTheme.titleSmall,
              ),
              trailing: IconButton(
                tooltip: l10n.plugin_eh_clear_history,
                icon: const Icon(Icons.delete_sweep_outlined),
                onPressed: history.clear,
              ),
            ),
          for (final query in queries)
            ListTile(
              key: ValueKey('eh-history-$query'),
              leading: const Icon(Icons.history),
              title: Text(query),
              trailing: IconButton(
                tooltip: l10n.delete,
                icon: const Icon(Icons.close),
                onPressed: () => history.forget(query),
              ),
              onTap: () => onRun(query),
            ),
        ],
      ),
    );
  }
}

class _EhSearchResults extends StatelessWidget {
  final EhFeedStore results;

  const _EhSearchResults({super.key, required this.results});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return ScopedBuilder<EhFeedStore, List<EhGallery>>(
      store: results,
      onLoading: (_) => results.state.isEmpty
          ? const Center(child: CircularProgressIndicator())
          : _grid(results.state),
      onError: (_, error) => FullPageErrorWidget(
        error: error,
        stackTrace: null,
        prefix: ehErrorMessage(l10n, error),
        onRetry: results.refresh,
      ),
      onState: (context, galleries) => galleries.isEmpty
          ? Center(child: Text(l10n.plugin_eh_empty_search))
          : _grid(galleries),
    );
  }

  Widget _grid(List<EhGallery> galleries) => EhGalleryGrid(
    galleries: galleries,
    onRefresh: results.refresh,
    loadingMore: results.loadingMore,
    onNearEnd: results.loadMore,
  );
}
