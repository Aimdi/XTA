import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_confirm.dart';
import 'package:xta/plugins/pixiv/pixiv_grid.dart';
import 'package:xta/plugins/pixiv/pixiv_history_store.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_card.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_list.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_models.dart';
import 'package:xta/plugins/pixiv/pixiv_paged_feed.dart';
import 'package:xta/plugins/pixiv/pixiv_segmented_switch.dart';
import 'package:xta/plugins/pixiv/pixiv_settings_content.dart';
import 'package:xta/plugins/pixiv/pixiv_view_state.dart';
import 'package:xta/plugins/plugin_feed_insets.dart';
import 'package:xta/plugins/plugin_view_store.dart';
import 'package:xta/ui/empty_pane.dart';

/// A history lists what was opened, muted or not: a muted work still waits
/// behind its notice when reopened.
PixivMuteState _showingEverything(PixivMuteState _) => PixivMuteState.empty;

/// The works or novels opened on this device, newest first: an Illustrations
/// / Novels switch, a title or author filter, a long press to forget one, and
/// Clear all for the history shown.
class PixivHistoryScreen extends StatefulWidget {
  final PixivContentMode initialKind;

  const PixivHistoryScreen({super.key, this.initialKind = PixivContentMode.illust});

  @override
  State<PixivHistoryScreen> createState() => _PixivHistoryScreenState();
}

class _PixivHistoryScreenState extends State<PixivHistoryScreen> {
  final _query = PluginViewStore<String>('');
  late final _kind = PluginViewStore<PixivContentMode>(_novels == null ? PixivContentMode.illust : widget.initialKind);

  /// Kept by the screen so the filter's words stay when the history shown changes.
  final _filter = TextEditingController();

  PixivHistoryStore get _illusts => context.read<PixivHistoryStore>();

  /// Absent outside the app, where the screen keeps to works.
  PixivNovelHistoryStore? get _novels => context.read<PixivNovelHistoryStore?>();

  PixivHistoryStore _historyOf(PixivContentMode kind) =>
      kind == PixivContentMode.novel ? _novels ?? _illusts : _illusts;

  @override
  void initState() {
    super.initState();
    _illusts.load();
    _novels?.load();
  }

  @override
  void dispose() {
    _query.destroy();
    _kind.destroy();
    _filter.dispose();
    super.dispose();
  }

  Future<void> _clearAll() async {
    final l10n = L10n.of(context);
    final history = _historyOf(_kind.state);
    if (await confirmPixivAction(context, l10n.plugin_pixiv_history_clear_question, l10n.plugin_pixiv_history_clear)) {
      await history.clear();
    }
  }

  Future<void> _forget(int id, String title) async {
    final l10n = L10n.of(context);
    final history = _historyOf(_kind.state);
    final named = title.isEmpty ? '$id' : title;
    if (await confirmPixivAction(context, l10n.plugin_pixiv_history_remove_question(named), l10n.delete)) {
      await history.remove(id);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.plugin_pixiv_history),
        actions: [
          IconButton(
            key: const ValueKey('pixiv-history-clear'),
            tooltip: l10n.plugin_pixiv_history_clear,
            icon: const Icon(Icons.delete_sweep_outlined),
            onPressed: _clearAll,
          ),
        ],
      ),
      body: ScopedBuilder<PluginViewStore<PixivContentMode>, PixivContentMode>(
        store: _kind,
        onState: (context, kind) => ScopedBuilder<PixivHistoryStore, List<PixivHistoryEntry>>(
          key: ValueKey(kind),
          store: _historyOf(kind),
          onState: (context, entries) => ScopedBuilder<PluginViewStore<String>, String>(
            store: _query,
            onState: (context, query) => _body(context, kind, pixivHistoryFiltered(entries, query), entries.isEmpty),
          ),
        ),
      ),
    );
  }

  /// One scroll view whatever the filter leaves, so the filter field keeps
  /// its text and focus while the entries under it come and go.
  Widget _body(BuildContext context, PixivContentMode kind, List<PixivHistoryEntry> shown, bool none) {
    final leading = [
      SliverToBoxAdapter(
        key: const ValueKey('pixiv-history-header'),
        child: _HistoryHeader(
          kind: kind,
          onKind: _novels == null ? null : _kind.select,
          filter: _filter,
          onQuery: _query.select,
        ),
      ),
      if (shown.isEmpty) _empty(context, kind, none: none),
    ];
    return switch (kind) {
      PixivContentMode.illust => _works(context, shown, leading),
      PixivContentMode.novel => _novelList(context, [for (final entry in shown) entry.toNovel()], leading),
    };
  }

  Widget _empty(BuildContext context, PixivContentMode kind, {required bool none}) {
    final l10n = L10n.of(context);
    final novels = kind == PixivContentMode.novel;
    final message = switch ((novels, none)) {
      (false, true) => l10n.plugin_pixiv_history_empty,
      (false, false) => l10n.plugin_pixiv_history_no_match,
      (true, true) => l10n.plugin_pixiv_history_novels_empty,
      (true, false) => l10n.plugin_pixiv_history_novels_no_match,
    };
    return SliverPadding(
      padding: const EdgeInsets.fromLTRB(32, 48, 32, 32),
      sliver: SliverToBoxAdapter(
        child: EmptyMessage(icon: novels ? Icons.menu_book_outlined : Icons.history, message: message),
      ),
    );
  }

  Widget _works(BuildContext context, List<PixivHistoryEntry> shown, List<Widget> leading) {
    final illusts = [for (final entry in shown) entry.toIllust()];
    return PixivIllustGrid(
      illusts: illusts,
      hideMuted: false,
      padding: pluginFeedPadding(context, extra: const EdgeInsets.all(8)),
      tileBuilder: (context, illusts, index) => PixivIllustTile(
        illust: illusts[index],
        siblings: illusts,
        index: index,
        onLongPress: () => _forget(illusts[index].id, illusts[index].title),
      ),
      leadingSlivers: leading,
    );
  }

  Widget _novelList(BuildContext context, List<PixivNovel> novels, List<Widget> leading) => pixivScrollView(
    context,
    slivers: [
      ...leading,
      SliverPadding(
        padding: pluginFeedPadding(context, extra: const EdgeInsets.symmetric(vertical: 4)),
        sliver: PixivNovelSliver<PixivNovel>(
          items: novels,
          novelOf: (novel) => novel,
          mutes: _showingEverything,
          card: (novel) => PixivNovelCard(novel: novel, onLongPress: () => _forget(novel.id, novel.title)),
        ),
      ),
    ],
  );
}

/// The Illustrations / Novels switch, the title or author filter, and the pause switch.
class _HistoryHeader extends StatelessWidget {
  final PixivContentMode kind;

  /// Without it there is no switch: only the works' history is at hand.
  final ValueChanged<PixivContentMode>? onKind;
  final TextEditingController filter;
  final ValueChanged<String> onQuery;

  const _HistoryHeader({required this.kind, required this.onKind, required this.filter, required this.onQuery});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Column(
      children: [
        if (onKind case final onKind?)
          PixivContentModeSwitch(key: const ValueKey('pixiv-history-kind'), selected: kind, onSelected: onKind),
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
          child: Column(
            children: [
              TextField(
                key: const ValueKey('pixiv-history-filter'),
                controller: filter,
                onChanged: onQuery,
                decoration: InputDecoration(
                  prefixIcon: const Icon(Icons.search),
                  hintText: kind == PixivContentMode.novel
                      ? l10n.plugin_pixiv_history_novels_filter_hint
                      : l10n.plugin_pixiv_history_filter_hint,
                  border: const OutlineInputBorder(),
                ),
              ),
              const _PausedNote(),
            ],
          ),
        ),
      ],
    );
  }
}

/// The switch that stops, and resumes, adding opened works to the history.
class _PausedNote extends StatelessWidget {
  const _PausedNote();

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return PixivPrefSwitch(
      key: const ValueKey('pixiv-history-pause'),
      pref: optionPluginPixivHistoryPaused,
      title: l10n.plugin_pixiv_history_pause,
      subtitle: l10n.plugin_pixiv_history_pause_description,
    );
  }
}
