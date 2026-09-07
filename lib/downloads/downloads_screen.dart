import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/downloads/download_entry.dart';
import 'package:xta/downloads/download_store.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/utils/download_directory.dart';

class DownloadsScreen extends StatelessWidget {
  final DownloadStore? store;
  const DownloadsScreen({super.key, this.store});

  @override
  Widget build(BuildContext context) {
    final model = store ?? DownloadStore.shared;
    model.initialize();
    final l10n = L10n.of(context);
    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.downloads_title),
        actions: [
          IconButton(
            tooltip: l10n.downloads_clear_finished,
            icon: const Icon(Icons.playlist_remove),
            onPressed: model.clearFinished,
          ),
        ],
      ),
      body: SafeArea(
        top: false,
        child: ScopedBuilder<DownloadStore, DownloadCenterState>(
          store: model,
          onState: (context, state) {
            if (!state.ready) return const Center(child: CircularProgressIndicator());
            final entries = [...state.entries]
              ..sort((a, b) {
                if (a.active != b.active) return a.active ? -1 : 1;
                return a.active ? a.createdAt.compareTo(b.createdAt) : b.createdAt.compareTo(a.createdAt);
              });
            return Column(
              children: [
                if (state.storageError)
                  MaterialBanner(
                    content: Text(l10n.downloads_history_unavailable),
                    actions: [TextButton(onPressed: model.retryHistory, child: Text(l10n.retry))],
                  ),
                Expanded(
                  child: state.entries.isEmpty
                      ? Center(
                          child: Padding(
                            padding: const EdgeInsets.all(32),
                            child: Column(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Icon(
                                  Icons.download_outlined,
                                  size: 40,
                                  color: Theme.of(context).colorScheme.onSurfaceVariant,
                                ),
                                const SizedBox(height: 12),
                                Text(l10n.downloads_empty, textAlign: TextAlign.center),
                              ],
                            ),
                          ),
                        )
                      : ListView.separated(
                          padding: const EdgeInsets.only(bottom: 16),
                          itemCount: entries.length,
                          separatorBuilder: (_, _) => const Divider(height: 1),
                          itemBuilder: (context, index) => _DownloadRow(entry: entries[index], store: model),
                        ),
                ),
              ],
            );
          },
        ),
      ),
    );
  }
}

class _DownloadRow extends StatelessWidget {
  final DownloadEntry entry;
  final DownloadStore store;
  const _DownloadRow({required this.entry, required this.store});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final active = entry.status == DownloadStatus.downloading;
    return Padding(
      padding: const EdgeInsetsDirectional.fromSTEB(16, 10, 8, 10),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsetsDirectional.only(top: 4, end: 12),
            child: Icon(_icon(entry.status), size: 22),
          ),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  entry.fileName,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.titleSmall,
                ),
                const SizedBox(height: 3),
                Text(_status(l10n, entry.status), style: Theme.of(context).textTheme.bodySmall),
                if (active) ...[
                  const SizedBox(height: 8),
                  LinearProgressIndicator(
                    value: entry.total != null && entry.total! > 0
                        ? (entry.received / entry.total!).clamp(0.0, 1.0)
                        : null,
                  ),
                  const SizedBox(height: 4),
                  Text(
                    l10n.downloads_progress(_bytes(entry.received), entry.total == null ? '—' : _bytes(entry.total!)),
                    textDirection: TextDirection.ltr,
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
                ],
                if (entry.status == DownloadStatus.failed)
                  Text(l10n.downloads_retry_hint, style: Theme.of(context).textTheme.bodySmall),
              ],
            ),
          ),
          if (entry.canCancel)
            IconButton(tooltip: l10n.cancel, icon: const Icon(Icons.close), onPressed: () => store.cancel(entry.id)),
          if (entry.canRetry)
            IconButton(tooltip: l10n.retry, icon: const Icon(Icons.refresh), onPressed: () => store.retry(entry.id)),
          if (entry.status == DownloadStatus.completed && entry.savedUri != null)
            IconButton(
              tooltip: l10n.downloads_open_file,
              icon: const Icon(Icons.open_in_new),
              onPressed: () async {
                try {
                  await DownloadDirectory.openDocument(entry.savedUri!, entry.fileName);
                } catch (_) {
                  if (context.mounted)
                    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(l10n.downloads_open_failed)));
                }
              },
            ),
        ],
      ),
    );
  }
}

String _bytes(int value) {
  if (value >= 1048576) return '${(value / 1048576).toStringAsFixed(1)} MB';
  if (value >= 1024) return '${(value / 1024).toStringAsFixed(1)} KB';
  return '$value B';
}

IconData _icon(DownloadStatus status) => switch (status) {
  DownloadStatus.completed => Icons.download_done,
  DownloadStatus.failed || DownloadStatus.interrupted => Icons.error_outline,
  DownloadStatus.cancelled => Icons.cancel_outlined,
  _ => Icons.download_outlined,
};

String _status(L10n l10n, DownloadStatus status) => switch (status) {
  DownloadStatus.queued => l10n.downloads_queued,
  DownloadStatus.downloading => l10n.downloading_media,
  DownloadStatus.choosingLocation => l10n.downloads_choose_location,
  DownloadStatus.saving => l10n.downloads_saving,
  DownloadStatus.completed => l10n.downloads_completed,
  DownloadStatus.failed => l10n.downloads_failed,
  DownloadStatus.cancelled => l10n.downloads_cancelled,
  DownloadStatus.interrupted => l10n.downloads_interrupted,
};
