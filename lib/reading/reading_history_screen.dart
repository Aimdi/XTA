import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_marks.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/reading/reading_history_entry.dart';
import 'package:xta/reading/reading_history_hook.dart';
import 'package:xta/reading/reading_history_navigation.dart';
import 'package:xta/reading/reading_history_store.dart';
import 'package:xta/settings/settings_chrome.dart';
import 'package:xta/ui/dates.dart';

Future<void> openReadingHistory(BuildContext context) =>
    Navigator.push<void>(context, MaterialPageRoute(builder: (_) => const ReadingHistoryScreen()));

class _HistoryView {
  final String query;
  final String? source;
  const _HistoryView({this.query = '', this.source});
}

class _HistoryViewStore extends Store<_HistoryView> {
  _HistoryViewStore() : super(const _HistoryView());
  void query(String value) => update(_HistoryView(query: value, source: state.source));
  void source(String? value) => update(_HistoryView(query: state.query, source: value));
}

/// Everything read on this device, searchable, with a pause and a way to forget.
class ReadingHistoryScreen extends StatefulWidget {
  final ReadingHistoryStore? store;
  const ReadingHistoryScreen({super.key, this.store});

  @override
  State<ReadingHistoryScreen> createState() => _ReadingHistoryScreenState();
}

class _ReadingHistoryScreenState extends State<ReadingHistoryScreen> {
  final _view = _HistoryViewStore();
  ReadingHistoryStore? _store;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _store ??= (widget.store ?? readingHistoryOf(context))?..load();
  }

  @override
  void dispose() {
    _view.destroy();
    super.dispose();
  }

  void _say(String message) => ScaffoldMessenger.of(context)
    ..hideCurrentSnackBar()
    ..showSnackBar(SnackBar(content: Text(message)));

  Future<void> _clear(ReadingHistoryStore store) async {
    final l10n = L10n.of(context);
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        content: Text(l10n.history_clear_confirm),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: Text(l10n.cancel)),
          TextButton(onPressed: () => Navigator.pop(context, true), child: Text(l10n.history_clear)),
        ],
      ),
    );
    if (confirmed != true) return;
    final cleared = await store.clear();
    if (mounted) _say(cleared ? l10n.history_cleared : l10n.history_clear_failed);
  }

  Future<void> _remove(ReadingHistoryStore store, ReadingHistoryEntry entry) async {
    final message = L10n.of(context).history_clear_failed;
    if (!await store.remove(entry.key) && mounted) _say(message);
  }

  Future<void> _setEnabled(ReadingHistoryStore store, bool enabled) async {
    final message = L10n.of(context).history_save_failed;
    if (!await store.setEnabled(enabled) && mounted) _say(message);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final store = _store;
    if (store == null) return SettingsPageScaffold(title: l10n.history_title, body: const SizedBox.shrink());
    return ScopedBuilder<ReadingHistoryStore, ReadingHistoryState>(
      store: store,
      onState: (context, history) => Scaffold(
        appBar: AppBar(
          title: Text(l10n.history_title),
          actions: [
            IconButton(
              key: const ValueKey('history-clear'),
              tooltip: l10n.history_clear,
              icon: const Icon(Icons.delete_sweep_outlined),
              onPressed: history.entries.isEmpty ? null : () => _clear(store),
            ),
          ],
        ),
        body: SafeArea(
          child: ScopedBuilder<_HistoryViewStore, _HistoryView>(
            store: _view,
            onState: (context, view) => _body(context, store, history, view),
          ),
        ),
      ),
    );
  }

  Widget _body(BuildContext context, ReadingHistoryStore store, ReadingHistoryState history, _HistoryView view) {
    final l10n = L10n.of(context);
    final results = history.search(view.query, source: view.source);
    return CustomScrollView(
      slivers: [
        SliverToBoxAdapter(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              SettingsToggleRow(
                icon: Icons.history,
                title: l10n.history_enabled,
                description: history.enabled ? l10n.history_enabled_description : l10n.history_paused,
                value: history.enabled,
                onChanged: (enabled) => _setEnabled(store, enabled),
              ),
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
                child: TextField(
                  key: const ValueKey('history-search'),
                  decoration: InputDecoration(prefixIcon: const Icon(Icons.search), hintText: l10n.history_search_hint),
                  onChanged: _view.query,
                ),
              ),
              if (history.sources.length > 1) _sourceChips(context, history, view),
              if (!history.loaded) const LinearProgressIndicator(),
            ],
          ),
        ),
        if (results.isEmpty)
          SliverFillRemaining(
            hasScrollBody: false,
            child: Center(
              child: Padding(
                padding: const EdgeInsets.all(24),
                child: Text(
                  history.entries.isEmpty ? l10n.history_empty : l10n.no_results,
                  textAlign: TextAlign.center,
                ),
              ),
            ),
          )
        else
          SliverList.builder(
            itemCount: results.length,
            itemBuilder: (context, index) => _ReadingHistoryTile(
              key: ValueKey(results[index].key),
              entry: results[index],
              onRemove: () => _remove(store, results[index]),
            ),
          ),
      ],
    );
  }

  Widget _sourceChips(BuildContext context, ReadingHistoryState history, _HistoryView view) => SingleChildScrollView(
    scrollDirection: Axis.horizontal,
    padding: const EdgeInsets.symmetric(horizontal: 12),
    child: Row(
      children: [
        ChoiceChip(
          label: Text(L10n.of(context).all),
          selected: view.source == null,
          onSelected: (_) => _view.source(null),
        ),
        for (final source in history.sources)
          Padding(
            padding: const EdgeInsetsDirectional.only(start: 6),
            child: ChoiceChip(
              label: Text(readingHistorySourceLabel(context, source)),
              selected: view.source == source,
              onSelected: (_) => _view.source(source),
            ),
          ),
      ],
    ),
  );
}

class _ReadingHistoryTile extends StatelessWidget {
  final ReadingHistoryEntry entry;
  final VoidCallback onRemove;
  const _ReadingHistoryTile({super.key, required this.entry, required this.onRemove});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final plugin = pluginById(entry.source);
    final handle = entry.extra['handle'];
    final heading = entry.title.isNotEmpty ? entry.title : entry.author;
    final meta = [
      if (entry.title.isNotEmpty && entry.author.isNotEmpty) entry.author,
      if (handle != null && handle.isNotEmpty) '@$handle',
      readingHistorySourceLabel(context, entry.source),
      createRelativeDate(entry.viewedAt),
    ].join(' · ');
    return ListTile(
      minTileHeight: 56,
      leading: SizedBox.square(
        dimension: 40,
        child: Center(child: plugin == null ? const Icon(Icons.public) : pluginMark(plugin, size: 24)),
      ),
      title: Text(heading.isEmpty ? meta : heading, maxLines: 2, overflow: TextOverflow.ellipsis),
      subtitle: Text(
        [if (entry.text.isNotEmpty) entry.text, meta].join('\n'),
        maxLines: 3,
        overflow: TextOverflow.ellipsis,
      ),
      trailing: IconButton(tooltip: l10n.history_remove, icon: const Icon(Icons.close), onPressed: onRemove),
      onTap: () => openReadingHistoryEntry(context, entry),
    );
  }
}
