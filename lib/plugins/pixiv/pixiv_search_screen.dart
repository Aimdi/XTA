import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_links.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_saucenao_sheet.dart';
import 'package:xta/plugins/pixiv/pixiv_search_api.dart';
import 'package:xta/plugins/pixiv/pixiv_search_filters.dart';
import 'package:xta/plugins/pixiv/pixiv_search_landing.dart';
import 'package:xta/plugins/pixiv/pixiv_search_results.dart';
import 'package:xta/plugins/pixiv/pixiv_search_shortcuts.dart';
import 'package:xta/plugins/pixiv/pixiv_search_store.dart';
import 'package:xta/plugins/plugin_search_history.dart';
import 'package:xta/ui/reader_tab_view.dart';

/// Recent Pixiv searches, newest first; one differing only in case replaces
/// the older. One instance serves the app, so a search made on a pushed screen
/// already shows on the search screen underneath.
class PixivSearchHistory extends PluginSearchHistoryStore {
  PixivSearchHistory(BasePrefService prefs)
    : super(prefs, optionPluginPixivSearchHistory, identity: (query) => query.toLowerCase());
}

/// Tag / keyword / user search — Pixez's second home. Its state lives in a
/// [PixivSearchStore]; this widget owns the text field and the tabs.
class PixivSearchScreen extends StatefulWidget {
  final String? initialQuery;
  final bool embedded;

  const PixivSearchScreen({super.key, this.initialQuery, this.embedded = false});

  @override
  State<PixivSearchScreen> createState() => _PixivSearchScreenState();
}

class _PixivSearchScreenState extends State<PixivSearchScreen> with SingleTickerProviderStateMixin {
  late final TextEditingController _query;
  late final TabController _tabs;
  late final PluginSearchHistoryStore _history;
  late final PixivSearchStore _store;

  @override
  void initState() {
    super.initState();
    _query = TextEditingController(text: widget.initialQuery ?? '');
    _tabs = TabController(length: 2, vsync: this);
    _history = context.read<PixivSearchHistory>();
    _store = PixivSearchStore(
      api: PixivSearchApi.of(context),
      prefs: PrefService.of(context, listen: false),
      mute: context.read<PixivMuteStore>(),
      history: _history,
    );
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      _history.load();
      final searching = (widget.initialQuery ?? '').trim().isNotEmpty;
      unawaited(searching ? _submit() : _store.loadLanding());
    });
  }

  @override
  void dispose() {
    _store.destroy();
    _query.dispose();
    _tabs.dispose();
    super.dispose();
  }

  void _show(String text) {
    _query.value = TextEditingValue(
      text: text,
      selection: TextSelection.collapsed(offset: text.length),
    );
  }

  /// Opens a link, or searches the field's words. A bare number is searched
  /// too: the shortcuts above the suggestions are how to open it as an id.
  Future<void> _submit([String? raw]) async {
    final word = (raw ?? _query.text).trim();
    if (word.isEmpty) return;
    final link = pixivNumericQuery(word) == null ? parsePixivLink(word) : null;
    if (link != null) return openPixivLinkOrSay(context, link);
    FocusScope.of(context).unfocus();
    _show(word);
    await _store.search(word);
  }

  void _pick(PixivTrendTag tag) {
    final text = _store.pick(tag);
    _show(text);
    if (text == tag.name) FocusScope.of(context).unfocus();
  }

  /// Puts the clipboard in the field and opens it at once when it names a
  /// Pixiv work or creator.
  Future<void> _paste() async {
    final text = (await Clipboard.getData(Clipboard.kTextPlain))?.text?.trim() ?? '';
    if (text.isEmpty || !mounted) return;
    _show(text);
    _store.type(text);
    final link = parsePixivLink(text);
    if (link != null) await openPixivLinkOrSay(context, link);
  }

  @override
  Widget build(BuildContext context) => ScopedBuilder<PixivSearchStore, PixivSearchState>(
    store: _store,
    distinct: (state) => [
      state.word,
      state.picking,
      state.numericId,
      state.suggestions,
      state.filter,
      state.remembered,
      state.popular,
      state.historyExpanded,
    ],
    onState: (context, state) => widget.embedded ? _embedded(state) : _scaffold(state),
  );

  Widget _scaffold(PixivSearchState state) => Scaffold(
    appBar: AppBar(title: _field(), bottom: _showsTabs(state) ? _tabBar() : null),
    body: _body(state),
  );

  Widget _embedded(PixivSearchState state) => Column(
    children: [
      Padding(padding: const EdgeInsets.fromLTRB(16, 4, 0, 0), child: _field()),
      if (_showsTabs(state)) _tabBar(),
      Expanded(child: _body(state)),
    ],
  );

  bool _showsTabs(PixivSearchState state) => state.searched && !state.picking;

  TabBar _tabBar() {
    final l10n = L10n.of(context);
    return TabBar(
      controller: _tabs,
      tabs: [
        Tab(text: l10n.plugin_pixiv_tab_illusts),
        Tab(text: l10n.plugin_pixiv_tab_users),
      ],
    );
  }

  Widget _field() {
    final l10n = L10n.of(context);
    return TextField(
      key: const ValueKey('pixiv-search-field'),
      controller: _query,
      textInputAction: TextInputAction.search,
      autofocus: !widget.embedded && (widget.initialQuery ?? '').isEmpty,
      decoration: InputDecoration(
        hintText: l10n.plugin_pixiv_search_hint,
        border: InputBorder.none,
        suffixIcon: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            IconButton(
              tooltip: l10n.plugin_pixiv_search_paste,
              onPressed: _paste,
              icon: const Icon(Icons.content_paste),
            ),
            IconButton(
              tooltip: l10n.plugin_pixiv_saucenao_title,
              onPressed: () => showPixivSauceNaoSheet(context),
              icon: const Icon(Icons.image_search),
            ),
            IconButton(tooltip: l10n.search, onPressed: _submit, icon: const Icon(Icons.search)),
          ],
        ),
      ),
      onChanged: _store.type,
      onSubmitted: (_) => _submit(),
    );
  }

  Widget _body(PixivSearchState state) {
    if (state.picking) {
      return PixivSearchPicker(numericId: state.numericId, suggestions: state.suggestions, onPick: _pick);
    }
    if (!state.searched) {
      return PixivSearchLanding(
        store: _store,
        history: _history,
        historyExpanded: state.historyExpanded,
        onSearch: _submit,
      );
    }
    return ReaderTabView(
      controller: _tabs,
      children: [
        PixivSearchWorks(store: _store, state: state),
        PixivSearchUsers(store: _store.users),
      ],
    );
  }
}
