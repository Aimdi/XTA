import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/downloads/download_destination.dart';
import 'package:xta/downloads/download_entry.dart';
import 'package:xta/downloads/download_store.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/plugin_post_media.dart';
import 'package:xta/ui/snack_bar_policy.dart';
import 'package:xta/utils/download_directory.dart';

const pixivDownloadSource = 'pixiv';

/// One page as the shared plugin media path sees it: shown large, saved original.
PluginMediaItem pixivPageMedia(PixivIllust illust, int page) =>
    PluginMediaItem(url: illust.viewerUrls[page], downloadUrl: illust.downloadUrlAt(page));

/// Every page of [illust] queued for [destination], named like Pixiv's own files.
List<DownloadRequest> pixivPageRequests(PixivIllust illust, DownloadDestination destination) => [
  for (var page = 0; page < illust.viewerUrls.length; page++)
    DownloadRequest(
      uri: Uri.parse(illust.downloadUrlAt(page)),
      fileName: pluginMediaFileName(pixivPageMedia(illust, page), pixivDownloadSource),
      treeUri: destination.treeUri,
      background: destination.background,
    ),
];

/// Where Pixiv pages are saved: the app's download queue unless a test swaps it.
class PixivDownloader {
  const PixivDownloader();

  static PixivDownloader of(BuildContext context) => context.read<PixivDownloader?>() ?? const PixivDownloader();

  /// One page, with the same prompts and messages as any other plugin image.
  Future<void> savePage(BuildContext context, PixivIllust illust, int page) =>
      downloadPluginMediaItem(context, pixivPageMedia(illust, page), sourceName: pixivDownloadSource);

  /// One page of a batch; true once the file is saved.
  Future<bool> save(DownloadRequest request) async {
    try {
      final entry = await DownloadStore.shared.enqueue(
        uri: request.uri,
        fileName: request.fileName,
        treeUri: request.treeUri,
        background: request.background,
      );
      return entry.status == DownloadStatus.completed;
    } catch (_) {
      return false;
    }
  }

  void cancel(Uri uri) {
    final store = DownloadStore.shared;
    for (final entry in store.state.entries.where((entry) => entry.active && entry.uri == uri)) {
      unawaited(store.cancel(entry.id));
    }
  }

  /// The configured destination, else a folder asked for once for the whole
  /// work; null when the user backs out of choosing one.
  Future<DownloadDestination?> batchDestination(BasePrefService prefs) async {
    final configured = DownloadDestination.fromPrefs(prefs);
    if (!configured.asks) return configured;
    final treeUri = await DownloadDirectory.pick();
    return treeUri == null ? null : DownloadDestination.folder(treeUri);
  }
}

class PixivDownloadProgress {
  final int total;
  final int done;
  final int saved;
  final bool cancelled;

  const PixivDownloadProgress({required this.total, this.done = 0, this.saved = 0, this.cancelled = false});

  int get failed => done - saved;

  /// The 1-based page being fetched now.
  int get current => (done + 1).clamp(1, total < 1 ? 1 : total);

  PixivDownloadProgress copyWith({int? done, int? saved, bool? cancelled}) => PixivDownloadProgress(
    total: total,
    done: done ?? this.done,
    saved: saved ?? this.saved,
    cancelled: cancelled ?? this.cancelled,
  );
}

/// Saves a work's pages one after another; a failed page never stops the rest.
class PixivDownloadStore extends Store<PixivDownloadProgress> {
  final Future<bool> Function(DownloadRequest request) save;
  final void Function(Uri uri) cancelActive;
  DownloadRequest? _active;

  PixivDownloadStore({required this.save, required this.cancelActive, required int total})
    : super(PixivDownloadProgress(total: total));

  Future<PixivDownloadProgress> run(List<DownloadRequest> requests) async {
    for (final request in requests) {
      if (state.cancelled) break;
      _active = request;
      final saved = await save(request);
      _active = null;
      update(state.copyWith(done: state.done + 1, saved: state.saved + (saved ? 1 : 0)));
    }
    return state;
  }

  void cancel() {
    if (state.cancelled) return;
    update(state.copyWith(cancelled: true));
    final active = _active;
    if (active != null) cancelActive(active.uri);
  }
}

/// Saves every page of [illust], showing progress with a cancel button.
Future<void> downloadAllPixivPages(BuildContext context, PixivIllust illust) async {
  final downloader = PixivDownloader.of(context);
  final messenger = ScaffoldMessenger.of(context);
  final l10n = L10n.of(context);
  final destination = await downloader.batchDestination(PrefService.of(context, listen: false));
  if (destination == null || !messenger.mounted) return;
  final requests = pixivPageRequests(illust, destination);
  final store = PixivDownloadStore(save: downloader.save, cancelActive: downloader.cancel, total: requests.length);
  messenger
    ..clearSnackBars()
    ..removeCurrentSnackBar();
  final progress = messenger.showSnackBar(pixivDownloadSnackBar(store));
  final result = await store.run(requests);
  unawaited(progress.closed.whenComplete(store.destroy));
  if (!messenger.mounted) return;
  messenger.hideCurrentSnackBar(reason: SnackBarClosedReason.hide);
  messenger.showSnackBar(SnackBar(content: Text(l10n.downloads_batch_result(result.saved, result.total))));
}

SnackBar pixivDownloadSnackBar(PixivDownloadStore store) => WorkingSnackBar(
  content: ScopedBuilder<PixivDownloadStore, PixivDownloadProgress>(
    store: store,
    onState: (context, progress) => PixivDownloadProgressRow(progress: progress, onCancel: store.cancel),
  ),
);

class PixivDownloadProgressRow extends StatelessWidget {
  final PixivDownloadProgress progress;
  final VoidCallback onCancel;

  const PixivDownloadProgressRow({super.key, required this.progress, required this.onCancel});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final fraction = progress.total == 0 ? null : progress.done / progress.total;
    return Row(
      children: [
        SizedBox.square(
          dimension: 16,
          child: CircularProgressIndicator(
            strokeWidth: 2,
            value: progress.done == 0 ? null : fraction,
            backgroundColor: Theme.of(context).colorScheme.onSurface.withValues(alpha: 0.16),
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Text(
            progress.cancelled
                ? l10n.downloads_cancelled
                : l10n.plugin_pixiv_downloading_page(progress.current, progress.total),
            style: const TextStyle(height: 1.5),
          ),
        ),
        if (!progress.cancelled) TextButton(onPressed: onCancel, child: Text(l10n.cancel)),
      ],
    );
  }
}
