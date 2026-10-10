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
import 'package:xta/plugins/pixiv/pixiv_novel_api.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_search_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_saucenao_sheet.dart';
import 'package:xta/plugins/pixiv/pixiv_search_api.dart';
import 'package:xta/plugins/pixiv/pixiv_search_filters.dart';
import 'package:xta/plugins/pixiv/pixiv_search_landing.dart';
import 'package:xta/plugins/pixiv/pixiv_search_results.dart';
import 'package:xta/plugins/pixiv/pixiv_search_shortcuts.dart';
import 'package:xta/plugins/pixiv/pixiv_search_store.dart';
import 'package:xta/plugins/pixiv/pixiv_view_state.dart';
import 'package:xta/plugins/plugin_search_history.dart';
import 'package:xta/ui/reader_tab_view.dart';

/// Recent Pixiv searches, newest first; one differing only in case replaces
/// the older. One instance serves the app, so a search made on a pushed screen
/// already shows on the search screen underneath.
class PixivSearchHistory extends PluginSearchHistoryStore {
  PixivSearchHistory(BasePrefService prefs)
    : super(prefs, optionPluginPixivSearchHistory, identity: (query) => query.toLowerCase());
}

/// [value] with [pasted] in place of its selection, or after its text when
/// it has none, and the cursor right after the pasted text.
TextEditingValue pixivPastedInto(TextEditingValue value, String pasted) {
  final length = value.text.length;
  final selection = value.selection;
  final start = selection.isValid ? selection.start.clamp(0, length) : length;
  final end = selection.isValid ? selection.end.clamp(start, length) : length;
  return TextEditingValue(
    text: value.text.replaceRange(start, end, pasted),
    selection: TextSelection.collapsed(offset: start + pasted.length),
  );
}

/// Tag / keyword / user search — Pixez's second home. Its state lives in a
/// [PixivSearchStore]; this widget owns the text field and the tabs. Of
/// [kind] novels it searches novels, with their own history, landing and id
/// shortcuts.
class PixivSearchScreen extends StatefulWidget {
  final String? initialQuery;
  final bool embedded;
  final PixivSearchKind kind;

  const PixivSearchScreen({super.key, this.initialQuery, this.embedded = false, this.kind = PixivSearchKind.works});

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
    final works = _works;
    _history = works ? context.read<PixivSearchHistory>() : context.read<PixivNovelSearchHistory>();
    _store = PixivSearchStore(
      api: PixivSearchApi.of(context),
      novelApi: works ? null : PixivNovelApi.of(context),
      kind: widget.kind,
      prefs: PrefService.of(context, listen: false),
      mute: context.read<PixivMuteStore>(),
      history: _history,
    );
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      _history.load();
      _start((widget.initialQuery ?? '').trim());
    });
  }

  /// A query handed in is searched, except a bare number: that offers its id
  /// shortcuts, with the landing behind them, since it most likely names a
  /// work or a creator.
  void _start(String query) {
    if (query.isNotEmpty && pixivNumericQuery(query) == null) {
      unawaited(_submit());
      return;
    }
    if (query.isNotEmpty) _store.type(query);
    unawaited(_store.loadLanding());
  }

  bool get _works => widget.kind.isWorks;

  /// What a pasted [text] opens: a bare number is a work, or in novel search a novel.
  PixivLinkRef? _linkOf(String text) => _works ? parsePixivLink(text) : pixivNovelSearchLink(text);

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

  /// Pastes the clipboard at the cursor. A Pixiv link or id takes the whole
  /// field instead, so a bare id keeps its shortcuts, and opens at once.
  Future<void> _paste() async {
    final pasted = (await Clipboard.getData(Clipboard.kTextPlain))?.text?.trim() ?? '';
    if (pasted.isEmpty || !mounted) return;
    final link = _linkOf(pasted);
    if (link == null) {
      _query.value = pixivPastedInto(_query.value, pasted);
    } else {
      _show(pasted);
    }
    _store.type(_query.text);
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
        Tab(text: _works ? l10n.plugin_pixiv_tab_illusts : l10n.plugin_pixiv_profile_novels),
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
        hintText: _works ? l10n.plugin_pixiv_search_hint : l10n.plugin_pixiv_novel_search_hint,
        border: InputBorder.none,
        suffixIcon: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            IconButton(
              tooltip: l10n.plugin_pixiv_search_paste,
              onPressed: _paste,
              icon: const Icon(Icons.content_paste),
            ),
            if (_works)
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
      return PixivSearchPicker(
        numericId: state.numericId,
        suggestions: state.suggestions,
        onPick: _pick,
        shortcuts: _works ? pixivNumericShortcuts : pixivNovelNumericShortcuts,
      );
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
        if (_works) PixivSearchWorks(store: _store, state: state) else PixivSearchNovels(store: _store, state: state),
        PixivSearchUsers(store: _store.users, previews: _works ? PixivContentMode.illust : PixivContentMode.novel),
      ],
    );
  }
}
