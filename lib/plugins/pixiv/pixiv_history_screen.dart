import 'package:flutter/material.dart';
import 'package:flutter_staggered_grid_view/flutter_staggered_grid_view.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_history_store.dart';
import 'package:xta/plugins/pixiv/pixiv_image.dart';
import 'package:xta/plugins/pixiv/pixiv_link_open.dart';
import 'package:xta/plugins/pixiv/pixiv_settings_content.dart';
import 'package:xta/plugins/plugin_gallery_layout.dart';
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
    final confirmed = await _confirm(l10n.plugin_pixiv_history_clear_question, l10n.plugin_pixiv_history_clear);
    if (confirmed) await _history.clear();
  }

  Future<void> _forget(PixivHistoryEntry entry) async {
    final l10n = L10n.of(context);
    final title = entry.title.isEmpty ? '${entry.id}' : entry.title;
    if (await _confirm(l10n.plugin_pixiv_history_remove_question(title), l10n.delete)) await _history.remove(entry.id);
  }

  Future<bool> _confirm(String question, String action) async {
    final answer = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(question),
        actions: [
          TextButton(onPressed: () => Navigator.pop(dialogContext, false), child: Text(L10n.of(dialogContext).cancel)),
          FilledButton(onPressed: () => Navigator.pop(dialogContext, true), child: Text(action)),
        ],
      ),
    );
    return answer == true;
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

  Widget _body(BuildContext context, List<PixivHistoryEntry> entries, String query) {
    final l10n = L10n.of(context);
    final shown = pixivHistoryFiltered(entries, query);
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
          child: TextField(
            key: const ValueKey('pixiv-history-filter'),
            onChanged: _query.select,
            decoration: InputDecoration(
              prefixIcon: const Icon(Icons.search),
              hintText: l10n.plugin_pixiv_history_filter_hint,
              border: const OutlineInputBorder(),
            ),
          ),
        ),
        const _PausedNote(),
        Expanded(
          child: shown.isEmpty
              ? EmptyPane(
                  icon: Icons.history,
                  message: entries.isEmpty ? l10n.plugin_pixiv_history_empty : l10n.plugin_pixiv_history_no_match,
                )
              : _grid(context, shown),
        ),
      ],
    );
  }

  Widget _grid(BuildContext context, List<PixivHistoryEntry> entries) => LayoutBuilder(
    builder: (context, constraints) => MasonryGridView.count(
      padding: const EdgeInsets.all(8),
      crossAxisCount: pluginGalleryColumns(constraints.maxWidth, MediaQuery.textScalerOf(context)),
      mainAxisSpacing: 8,
      crossAxisSpacing: 8,
      itemCount: entries.length,
      itemBuilder: (context, index) => PixivHistoryTile(
        entry: entries[index],
        onOpen: () => openPixivIllust(context, entries[index].toIllust()),
        onForget: () => _forget(entries[index]),
      ),
    ),
  );
}

/// The switch that stops, and resumes, adding opened works to the history.
class _PausedNote extends StatelessWidget {
  const _PausedNote();

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: PixivPrefSwitch(
        key: const ValueKey('pixiv-history-pause'),
        pref: optionPluginPixivHistoryPaused,
        title: l10n.plugin_pixiv_history_pause,
        subtitle: l10n.plugin_pixiv_history_pause_description,
      ),
    );
  }
}

/// One remembered work: its thumbnail, title and artist.
class PixivHistoryTile extends StatelessWidget {
  final PixivHistoryEntry entry;
  final VoidCallback onOpen;
  final VoidCallback onForget;

  const PixivHistoryTile({super.key, required this.entry, required this.onOpen, required this.onForget});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Material(
      color: theme.colorScheme.surfaceContainerLow,
      borderRadius: BorderRadius.circular(8),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onOpen,
        onLongPress: onForget,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            AspectRatio(
              aspectRatio: 1,
              child: PixivNetworkImage(url: entry.thumbUrl, cacheWidth: _decodeWidth(context)),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(8, 6, 8, 8),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(entry.title, maxLines: 2, overflow: TextOverflow.ellipsis, style: theme.textTheme.bodyMedium),
                  Text(
                    entry.userName,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: theme.textTheme.bodySmall!.copyWith(color: theme.colorScheme.onSurfaceVariant),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  int _decodeWidth(BuildContext context) => (240 * MediaQuery.devicePixelRatioOf(context)).ceil();
}
