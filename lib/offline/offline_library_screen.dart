import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/offline/offline_media_screen.dart';
import 'package:xta/offline/offline_store.dart';
import 'package:xta/offline/offline_ui.dart';
import 'package:xta/plugins/rss/rss_reader_screen.dart';
import 'package:xta/plugins/substack/substack_reader_screen.dart';
import 'package:xta/utils/browsers.dart';

class OfflineLibraryScreen extends StatelessWidget {
  final OfflineStore? store;
  const OfflineLibraryScreen({super.key, this.store});

  Future<void> _clear(BuildContext context, OfflineStore model) async {
    final l10n = L10n.of(context);
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(l10n.offline_clear_question),
        content: Text(l10n.offline_clear_description),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: Text(l10n.cancel)),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: Text(l10n.offline_clear)),
        ],
      ),
    );
    if (confirmed == true) await model.clear();
  }

  Future<void> _open(BuildContext context, OfflineStore model, OfflineEntry entry) async {
    if (entry.kind == OfflineKind.media) {
      if (entry.sensitive && !await _reveal(context)) return;
      if (!context.mounted) return;
      await Navigator.of(context).push(
        MaterialPageRoute<void>(
          builder: (_) => OfflineMediaScreen(entry: entry, store: model),
        ),
      );
      return;
    }
    final article = await model.article(entry.id);
    if (!context.mounted) return;
    final rss = article?.rssItem;
    final substack = article?.substackPost;
    if (rss != null) {
      await Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => RssReaderScreen(item: rss)));
    } else if (substack != null) {
      await Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => SubstackReaderScreen(post: substack)));
    } else {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(L10n.of(context).offline_unavailable)));
      await model.refresh();
    }
  }

  Future<bool> _reveal(BuildContext context) async {
    final l10n = L10n.of(context);
    return await showDialog<bool>(
          context: context,
          builder: (context) => AlertDialog(
            title: Text(l10n.offline_sensitive_open),
            content: Text(l10n.offline_sensitive_description),
            actions: [
              TextButton(onPressed: () => Navigator.pop(context, false), child: Text(l10n.cancel)),
              FilledButton(onPressed: () => Navigator.pop(context, true), child: Text(l10n.sensitive_media_show)),
            ],
          ),
        ) ??
        false;
  }

  @override
  Widget build(BuildContext context) {
    final model = store ?? OfflineStore.shared;
    model.load();
    final l10n = L10n.of(context);
    return ScopedBuilder<OfflineStore, OfflineState>(
      store: model,
      onState: (context, state) => Scaffold(
        appBar: AppBar(
          title: Text(l10n.offline_library_title),
          actions: [
            IconButton(
              icon: const Icon(Icons.refresh),
              tooltip: l10n.retry,
              onPressed: state.loading ? null : model.refresh,
            ),
            IconButton(
              icon: const Icon(Icons.delete_sweep_outlined),
              tooltip: l10n.offline_clear,
              onPressed: state.entries.isEmpty && state.busy.isEmpty ? null : () => _clear(context, model),
            ),
          ],
        ),
        body: SafeArea(
          top: false,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      l10n.offline_storage_used(
                        offlineBytes(context, state.bytes),
                        offlineBytes(context, model.storageLimit),
                      ),
                      style: Theme.of(context).textTheme.titleSmall,
                    ),
                    const SizedBox(height: 8),
                    LinearProgressIndicator(value: (state.bytes / model.storageLimit).clamp(0, 1).toDouble()),
                    const SizedBox(height: 8),
                    Text(
                      l10n.offline_storage_limit(offlineBytes(context, model.storageLimit)),
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ],
                ),
              ),
              if (state.storageError)
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  child: Text(l10n.offline_storage_error, style: TextStyle(color: Theme.of(context).colorScheme.error)),
                ),
              if (state.busy.isNotEmpty) const LinearProgressIndicator(),
              Expanded(
                child: state.loading
                    ? const Center(child: CircularProgressIndicator())
                    : state.entries.isEmpty
                    ? Center(
                        child: Padding(
                          padding: const EdgeInsets.all(24),
                          child: Text(l10n.offline_empty, textAlign: TextAlign.center),
                        ),
                      )
                    : ListView.separated(
                        itemCount: state.entries.length,
                        separatorBuilder: (_, _) => const Divider(height: 1),
                        itemBuilder: (context, index) => _entry(context, model, state.entries[index]),
                      ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _entry(BuildContext context, OfflineStore model, OfflineEntry entry) {
    final l10n = L10n.of(context);
    return ListTile(
      contentPadding: const EdgeInsetsDirectional.fromSTEB(16, 8, 8, 8),
      leading: Icon(
        entry.sensitive
            ? Icons.visibility_off_outlined
            : entry.kind == OfflineKind.article
            ? Icons.article_outlined
            : Icons.perm_media_outlined,
      ),
      title: Text(entry.title, maxLines: 2, overflow: TextOverflow.ellipsis),
      subtitle: Text(
        '${entry.source}\n${offlineStatus(l10n, entry)} · ${offlineBytes(context, entry.bytes)}\n${offlineDetails(l10n, entry)}',
      ),
      onTap: entry.canOpen ? () => _open(context, model, entry) : null,
      trailing: PopupMenuButton<String>(
        onSelected: (value) {
          if (value == 'remove') model.remove(entry.id);
          if (value == 'original' && entry.url != null) openExternally(entry.url!);
        },
        itemBuilder: (context) => [
          PopupMenuItem(value: 'remove', child: Text(l10n.offline_remove)),
          if (entry.url != null) PopupMenuItem(value: 'original', child: Text(l10n.open_in_browser)),
        ],
      ),
    );
  }
}
