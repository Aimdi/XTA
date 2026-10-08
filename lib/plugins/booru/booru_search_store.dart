import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/plugins/booru/booru_client.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_query.dart';
import 'package:xta/plugins/booru/booru_store.dart';
import 'package:xta/plugins/plugin_search_history.dart';

@immutable
class BooruSearchState {
  /// Finished tags, shown as chips.
  final List<String> tags;

  /// The tag still being typed.
  final String input;
  final List<BooruTagSuggestion> suggestions;

  /// Categories learned from suggestions, to colour the chips.
  final Map<String, BooruTagCategory> categories;

  /// The query whose results are loaded; null before the first search.
  final String? query;

  /// Whether the field is open over the results.
  final bool editing;

  const BooruSearchState({
    this.tags = const [],
    this.input = '',
    this.suggestions = const [],
    this.categories = const {},
    this.query,
    this.editing = true,
  });

  bool get showsResults => query != null && !editing;

  /// Everything in the field, including the tag still being typed.
  String get text => booruQueryText(appendBooruTokens(tags, booruQueryTokens(input)));

  BooruSearchState copyWith({
    List<String>? tags,
    String? input,
    List<BooruTagSuggestion>? suggestions,
    Map<String, BooruTagCategory>? categories,
    String? query,
    bool? editing,
  }) => BooruSearchState(
    tags: tags ?? this.tags,
    input: input ?? this.input,
    suggestions: suggestions ?? this.suggestions,
    categories: categories ?? this.categories,
    query: query ?? this.query,
    editing: editing ?? this.editing,
  );
}

/// The tag field, its suggestions and the search it runs. Every search that
/// runs is remembered in [history], whichever way it was started.
class BooruSearchStore extends Store<BooruSearchState> {
  final BooruClient client;
  final PluginSearchHistoryStore history;
  final Duration debounce;

  Timer? _suggestTimer;
  BooruFeedStore? _results;
  var _closed = false;

  /// With an [initialQuery] the field opens on its tags, ready for [search].
  BooruSearchStore(
    this.client,
    this.history, {
    String initialQuery = '',
    this.debounce = const Duration(milliseconds: 250),
  }) : super(BooruSearchState(tags: booruQueryTokens(initialQuery), editing: initialQuery.trim().isEmpty));

  BooruFeedStore? get results => _results;

  /// Takes the field's text; finished tags become chips. Returns what stays
  /// in the field.
  String type(String text) {
    final split = splitBooruInput(text);
    update(state.copyWith(tags: appendBooruTokens(state.tags, split.done), input: split.rest, editing: true));
    _suggest(split.rest);
    return split.rest;
  }

  void addTags(Iterable<String> tokens) => update(state.copyWith(tags: appendBooruTokens(state.tags, tokens)));

  /// Empties the field.
  void clear() {
    _suggestTimer?.cancel();
    update(state.copyWith(tags: const [], input: '', suggestions: const [], editing: true));
  }

  void removeTag(String tag) => update(
    state.copyWith(
      tags: [
        for (final t in state.tags)
          if (t != tag) t,
      ],
    ),
  );

  /// Backspace in an empty field. False when there was no chip to remove.
  bool removeLast() {
    if (state.input.isNotEmpty || state.tags.isEmpty) return false;
    update(state.copyWith(tags: state.tags.sublist(0, state.tags.length - 1)));
    return true;
  }

  /// Moves [tag] back into the field to be edited. Returns the field's text.
  String reopen(String tag) {
    final kept = appendBooruTokens(state.tags, booruQueryTokens(state.input));
    update(
      state.copyWith(
        tags: [
          for (final t in kept)
            if (t != tag) t,
        ],
        input: tag,
        editing: true,
      ),
    );
    _suggest(tag);
    return tag;
  }

  /// Adds a suggestion as a chip in place of the typed text, keeping a `-`
  /// or `~` typed before it.
  void pick(BooruTagSuggestion suggestion) {
    _suggestTimer?.cancel();
    final category = suggestion.category;
    update(
      state.copyWith(
        tags: appendBooruTokens(state.tags, [booruTokenFor(state.input, suggestion.name)]),
        input: '',
        suggestions: const [],
        categories: category == null ? null : {...state.categories, suggestion.name: category},
      ),
    );
  }

  void edit() {
    if (!state.editing) update(state.copyWith(editing: true));
  }

  /// Back to the results of the last search, dropping edits not searched.
  bool closeEditor() {
    final query = state.query;
    if (query == null || !state.editing) return false;
    _suggestTimer?.cancel();
    update(state.copyWith(tags: booruQueryTokens(query), input: '', suggestions: const [], editing: false));
    return true;
  }

  /// Replaces the field with [query] and runs it.
  Future<void> run(String query) {
    update(state.copyWith(tags: appendBooruTokens(const [], booruQueryTokens(query)), input: ''));
    return search();
  }

  /// Adds [token] to the current search and runs it again.
  Future<void> refine(String token) {
    addTags([token]);
    return search();
  }

  Future<void> search() async {
    final tags = appendBooruTokens(state.tags, booruQueryTokens(state.input));
    if (tags.isEmpty) return;
    final query = booruQueryText(tags);
    _suggestTimer?.cancel();
    // The previous results may still be loading; destroying them now would
    // let that load land in a disposed store, so they are left to the GC.
    final results = BooruFeedStore(client, ({required page}) => client.search(query, page: page));
    _results = results;
    update(state.copyWith(tags: tags, input: '', suggestions: const [], query: query, editing: false));
    await history.remember(query);
    if (!_closed) await results.refresh();
  }

  void _suggest(String input) {
    _suggestTimer?.cancel();
    final prefix = booruSuggestionPrefix(input);
    if (prefix == null) {
      if (state.suggestions.isNotEmpty) update(state.copyWith(suggestions: const []));
      return;
    }
    _suggestTimer = Timer(debounce, () => _fetchSuggestions(input, prefix));
  }

  Future<void> _fetchSuggestions(String input, String prefix) async {
    final suggestions = await client.suggestTags(prefix);
    if (_closed || state.input != input) return;
    update(state.copyWith(suggestions: suggestions));
  }

  @override
  Future<void> destroy() async {
    _closed = true;
    _suggestTimer?.cancel();
    await _results?.destroy();
    await super.destroy();
  }
}
