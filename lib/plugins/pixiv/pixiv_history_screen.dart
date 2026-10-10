import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_confirm.dart';
import 'package:xta/plugins/pixiv/pixiv_grid.dart';
import 'package:xta/plugins/pixiv/pixiv_history_store.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_settings_content.dart';
import 'package:xta/plugins/plugin_feed_insets.dart';
import 'package:xta/plugins/plugin_view_store.dart';
import 'package:xta/ui/empty_pane.dart';

/// The works opened on this device, newest first: a title or artist filter,
/// a long press to forget one, and Clear all.
class PixivHistoryScreen extends StatefulWidget {
  const PixivHistoryScreen({super.key});

  @override
  State<PixivHistoryScreen> createState() => _PixivHistoryScreenState();
}

class _PixivHistoryScreenState extends State<PixivHistoryScreen> {
  final _query = PluginViewStore<String>('');

  PixivHistoryStore get _history => context.read<PixivHistoryStore>();

  @override
  void initState() {
    super.initState();
    _history.load();
  }

  @override
  void dispose() {
    _query.destroy();
    super.dispose();
  }

  Future<void> _clearAll() async {
    final l10n = L10n.of(context);
    if (await confirmPixivAction(context, l10n.plugin_pixiv_history_clear_question, l10n.plugin_pixiv_history_clear)) {
      await _history.clear();
    }
  }

  Future<void> _forget(PixivIllust illust) async {
    final l10n = L10n.of(context);
    final title = illust.title.isEmpty ? '${illust.id}' : illust.title;
    if (await confirmPixivAction(context, l10n.plugin_pixiv_history_remove_question(title), l10n.delete)) {
      await _history.remove(illust.id);
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
      body: ScopedBuilder<PixivHistoryStore, List<PixivHistoryEntry>>(
        store: _history,
        onState: (context, entries) => ScopedBuilder<PluginViewStore<String>, String>(
          store: _query,
          onState: (context, query) => _body(context, entries, query),
        ),
      ),
    );
  }

  /// One scroll view whatever the filter leaves, so the filter field keeps
  /// its text and focus while the works under it come and go.
  Widget _body(BuildContext context, List<PixivHistoryEntry> entries, String query) {
    final l10n = L10n.of(context);
    final shown = [for (final entry in pixivHistoryFiltered(entries, query)) entry.toIllust()];
    return PixivIllustGrid(
      illusts: shown,
      hideMuted: false,
      padding: pluginFeedPadding(context, extra: const EdgeInsets.all(8)),
      tileBuilder: (context, illusts, index) => PixivIllustTile(
        illust: illusts[index],
        siblings: illusts,
        index: index,
        onLongPress: () => _forget(illusts[index]),
      ),
      leadingSlivers: [
        SliverToBoxAdapter(
          key: const ValueKey('pixiv-history-header'),
          child: _HistoryHeader(onQuery: _query.select),
        ),
        if (shown.isEmpty)
          SliverPadding(
            padding: const EdgeInsets.fromLTRB(32, 48, 32, 32),
            sliver: SliverToBoxAdapter(
              child: EmptyMessage(
                icon: Icons.history,
                message: entries.isEmpty ? l10n.plugin_pixiv_history_empty : l10n.plugin_pixiv_history_no_match,
              ),
            ),
          ),
      ],
    );
  }
}

/// The title or artist filter over the pause switch.
class _HistoryHeader extends StatelessWidget {
  final ValueChanged<String> onQuery;

  const _HistoryHeader({required this.onQuery});

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
    child: Column(
      children: [
        TextField(
          key: const ValueKey('pixiv-history-filter'),
          onChanged: onQuery,
          decoration: InputDecoration(
            prefixIcon: const Icon(Icons.search),
            hintText: L10n.of(context).plugin_pixiv_history_filter_hint,
            border: const OutlineInputBorder(),
          ),
        ),
        const _PausedNote(),
      ],
    ),
  );
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
