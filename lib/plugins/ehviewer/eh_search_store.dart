import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/plugins/ehviewer/eh_client.dart';
import 'package:xta/plugins/ehviewer/eh_models.dart';
import 'package:xta/plugins/ehviewer/eh_query.dart';
import 'package:xta/plugins/ehviewer/eh_store.dart';
import 'package:xta/plugins/plugin_search_history.dart';

@immutable
class EhSearchState {
  final Set<EhCategory> categories;

  /// 0 for any rating, else 2 to 5 stars.
  final int minRating;
  final EhSearchLanguage language;

  /// The search whose results are shown; null before the first one.
  final String? query;
  final List<EhTag> suggestions;

  const EhSearchState({
    required this.categories,
    this.minRating = 0,
    this.language = EhSearchLanguage.any,
    this.query,
    this.suggestions = const [],
  });

  EhSearchState copyWith({
    Set<EhCategory>? categories,
    int? minRating,
    EhSearchLanguage? language,
    String? query,
    List<EhTag>? suggestions,
  }) => EhSearchState(
    categories: categories ?? this.categories,
    minRating: minRating ?? this.minRating,
    language: language ?? this.language,
    query: query ?? this.query,
    suggestions: suggestions ?? this.suggestions,
  );
}

/// Filters, tag suggestions for the term being typed, and the search they
/// run. Every search is kept in [history].
class EhSearchStore extends Store<EhSearchState> {
  final EhClient client;
  final PluginSearchHistoryStore history;
  final Duration debounce;

  Timer? _suggestTimer;
  String _typed = '';
  EhSuggestionTerm? _suggested;
  EhFeedStore? _results;
  var _closed = false;

  EhSearchStore(this.client, this.history, {this.debounce = const Duration(milliseconds: 300)})
    : super(EhSearchState(categories: client.includedCategories));

  EhFeedStore? get results => _results;

  void type(String text) {
    _typed = text;
    _suggestTimer?.cancel();
    final terms = ehSuggestionTerms(text);
    if (terms.isEmpty) {
      if (state.suggestions.isNotEmpty) update(state.copyWith(suggestions: const []));
      return;
    }
    _suggestTimer = Timer(debounce, () => _suggest(text, terms));
  }

  /// The longest term the site has tags for wins.
  Future<void> _suggest(String text, List<EhSuggestionTerm> terms) async {
    for (final term in terms) {
      final tags = await client.suggestTags(term.prefix);
      if (_closed || _typed != text) return;
      if (tags.isNotEmpty) {
        _suggested = term;
        update(state.copyWith(suggestions: tags));
        return;
      }
    }
    update(state.copyWith(suggestions: const []));
  }

  /// The query with [tag] in place of the term it was suggested for.
  String pick(EhTag tag) {
    _suggestTimer?.cancel();
    final term = _suggested ?? (start: ehLastTermStart(_typed), prefix: '');
    _typed = ehInsertSuggestion(_typed, term, tag);
    _suggested = null;
    update(state.copyWith(suggestions: const []));
    return _typed;
  }

  /// At least one category stays on: the site treats none as all.
  void toggleCategory(EhCategory category) {
    final next = {...state.categories};
    if (!next.remove(category)) next.add(category);
    if (next.isNotEmpty) _filter(state.copyWith(categories: next));
  }

  /// Tapping the chosen rating again clears it.
  void setMinRating(int stars) => _filter(state.copyWith(minRating: stars == state.minRating ? 0 : stars));

  void setLanguage(EhSearchLanguage language) => _filter(state.copyWith(language: language));

  /// A changed filter runs the shown search again under it.
  void _filter(EhSearchState next) {
    update(next);
    final query = next.query;
    if (query != null) unawaited(search(query));
  }

  Future<void> search(String text) async {
    final query = text.trim();
    if (query.isEmpty) return;
    _suggestTimer?.cancel();
    _typed = text;
    final filters = state;
    // A search still loading keeps its store until the GC takes it, so its
    // late answer never lands in a destroyed one.
    final results = EhFeedStore(
      ({pageUrl}) => client.search(
        query,
        pageUrl: pageUrl,
        categories: filters.categories,
        minRating: filters.minRating,
        language: filters.language.tag,
      ),
    );
    _results = results;
    update(state.copyWith(query: query, suggestions: const []));
    await history.remember(query);
    if (!_closed) await results.refresh();
  }

  @override
  Future<void> destroy() async {
    _closed = true;
    _suggestTimer?.cancel();
    await _results?.destroy();
    await super.destroy();
  }
}
