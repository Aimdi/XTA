import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/booru/booru_client.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_query.dart';
import 'package:xta/plugins/booru/booru_search_field.dart';
import 'package:xta/plugins/booru/booru_search_landing.dart';
import 'package:xta/plugins/booru/booru_search_results.dart';
import 'package:xta/plugins/booru/booru_search_store.dart';
import 'package:xta/plugins/booru/booru_store.dart';
import 'package:xta/plugins/plugin_search_history.dart';
import 'package:xta/ui/errors.dart';

/// Tag search: tags are added one by one as chips, every search that runs is
/// kept in the history, and a search can be saved to Following.
class BooruSearchScreen extends StatefulWidget {
  final String? initialQuery;

  const BooruSearchScreen({super.key, this.initialQuery});

  @override
  State<BooruSearchScreen> createState() => _BooruSearchScreenState();
}

class _BooruSearchScreenState extends State<BooruSearchScreen> {
  late final PluginSearchHistoryStore _history;
  late final BooruSearchStore _store;
  final _controller = TextEditingController();
  final _focus = FocusNode();

  @override
  void initState() {
    super.initState();
    final initial = widget.initialQuery?.trim() ?? '';
    _history = PluginSearchHistoryStore(
      PrefService.of(context, listen: false),
      optionPluginBooruSearchHistory,
      identity: booruTagSetKey,
    );
    _store = BooruSearchStore(context.read<BooruClient>(), _history, initialQuery: initial);
    _focus.addListener(_onFocusChanged);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final saved = context.read<BooruTagsStore>();
      if (saved.state.isEmpty) unawaited(saved.load());
      if (initial.isNotEmpty) unawaited(_store.search());
    });
  }

  @override
  void dispose() {
    _focus.removeListener(_onFocusChanged);
    _focus.dispose();
    _controller.dispose();
    unawaited(_store.destroy());
    unawaited(_history.destroy());
    super.dispose();
  }

  void _onFocusChanged() {
    if (_focus.hasFocus) _store.edit();
  }

  void _setInput(String text) {
    _controller.value = TextEditingValue(
      text: text,
      selection: TextSelection.collapsed(offset: text.length),
    );
  }

  Future<void> _search() {
    _focus.unfocus();
    _controller.clear();
    return _store.search();
  }

  Future<void> _run(String query) {
    _focus.unfocus();
    _controller.clear();
    return _store.run(query);
  }

  void _pick(BooruTagSuggestion suggestion) {
    _store.pick(suggestion);
    _controller.clear();
    _focus.requestFocus();
  }

  void _editTag(String tag) {
    _setInput(_store.reopen(tag));
    _focus.requestFocus();
  }

  void _applyOperator(String prefix) {
    final text = toggleBooruOperator(_controller.text, BooruTagOperator.of(prefix));
    _setInput(_store.type(text));
    _focus.requestFocus();
  }

  void _closeEditor() {
    if (!_store.closeEditor()) return;
    _focus.unfocus();
    _controller.clear();
  }

  Future<void> _editAsText() async {
    final text = await showDialog<String>(
      context: context,
      builder: (_) => _RawQueryDialog(initial: _store.state.text),
    );
    if (text == null || !mounted) return;
    if (booruQueryTokens(text).isEmpty) return _clear();
    await _run(text);
  }

  void _clear() {
    _controller.clear();
    _store.clear();
    _focus.requestFocus();
  }

  Future<void> _toggleSaved(bool saved) async {
    final query = normaliseBooruQuery(_store.state.text);
    if (query == null) return;
    final store = context.read<BooruTagsStore>();
    final l10n = L10n.of(context);
    final message = l10n.plugin_booru_search_saved(l10n.plugin_booru_tab_following);
    if (saved) {
      await store.remove(query);
      return;
    }
    await store.add(query);
    if (mounted) showSnackBar(context, icon: '⭐', message: message);
  }

  @override
  Widget build(BuildContext context) {
    return ScopedBuilder<BooruSearchStore, BooruSearchState>(
      store: _store,
      onState: (context, state) => PopScope(
        canPop: state.query == null || !state.editing,
        onPopInvokedWithResult: (didPop, _) {
          if (!didPop) _closeEditor();
        },
        child: Scaffold(
          body: SafeArea(
            child: Column(
              children: [
                _searchBar(context, state),
                const Divider(height: 1),
                Expanded(child: _body(context, state)),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _searchBar(BuildContext context, BooruSearchState state) {
    return Padding(
      padding: const EdgeInsetsDirectional.fromSTEB(4, 6, 4, 6),
      child: Row(
        children: [
          const BackButton(),
          Expanded(
            child: BooruTagField(
              store: _store,
              state: state,
              controller: _controller,
              focusNode: _focus,
              autofocus: (widget.initialQuery ?? '').trim().isEmpty,
              onSearch: _search,
              onEditTag: _editTag,
            ),
          ),
          _SaveSearchButton(text: state.text, onToggle: _toggleSaved),
        ],
      ),
    );
  }

  Widget _body(BuildContext context, BooruSearchState state) {
    final results = _store.results;
    if (state.showsResults && results != null) {
      return Provider<BooruSearchStore>.value(
        value: _store,
        child: BooruSearchResults(key: ObjectKey(results), store: _store, results: results),
      );
    }
    if (!state.editing) return const Center(child: CircularProgressIndicator());
    final client = context.read<BooruClient>();
    return BooruSearchLanding(
      store: _store,
      state: state,
      saved: context.read<BooruTagsStore>(),
      engine: client.engine,
      host: client.host,
      maxRating: client.maxRating,
      onRun: _run,
      onPick: _pick,
      onOperator: _applyOperator,
      onEditAsText: _editAsText,
      onClear: _clear,
    );
  }
}

/// Star for the current query: saved searches feed the Following tab.
class _SaveSearchButton extends StatelessWidget {
  final String text;
  final Future<void> Function(bool saved) onToggle;

  const _SaveSearchButton({required this.text, required this.onToggle});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final query = normaliseBooruQuery(text);
    return ScopedBuilder<BooruTagsStore, List<String>>(
      store: context.read<BooruTagsStore>(),
      onState: (context, savedQueries) {
        final saved = query != null && savedQueries.contains(query);
        return IconButton(
          tooltip: saved ? l10n.plugin_booru_unsave_search : l10n.plugin_booru_save_search,
          isSelected: saved,
          icon: const Icon(Icons.star_border_rounded),
          selectedIcon: const Icon(Icons.star_rounded),
          onPressed: query == null ? null : () => onToggle(saved),
        );
      },
    );
  }
}

class _RawQueryDialog extends StatefulWidget {
  final String initial;

  const _RawQueryDialog({required this.initial});

  @override
  State<_RawQueryDialog> createState() => _RawQueryDialogState();
}

class _RawQueryDialogState extends State<_RawQueryDialog> {
  late final _controller = TextEditingController(text: widget.initial);

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return AlertDialog(
      title: Text(l10n.plugin_booru_edit_as_text),
      content: TextField(
        controller: _controller,
        autofocus: true,
        autocorrect: false,
        enableSuggestions: false,
        minLines: 1,
        maxLines: 4,
        textInputAction: TextInputAction.search,
        decoration: InputDecoration(hintText: l10n.plugin_booru_search_hint),
        onSubmitted: (text) => Navigator.pop(context, text),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: Text(l10n.cancel)),
        FilledButton(onPressed: () => Navigator.pop(context, _controller.text), child: Text(l10n.search)),
      ],
    );
  }
}
