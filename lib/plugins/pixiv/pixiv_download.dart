import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/downloads/download_destination.dart';
import 'package:xta/downloads/download_entry.dart';
import 'package:xta/downloads/download_store.dart';
import 'package:xta/downloads/download_transfer.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_download_index.dart';
import 'package:xta/plugins/pixiv/pixiv_download_naming.dart';
import 'package:xta/plugins/pixiv/pixiv_image_source.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_resave_dialog.dart';
import 'package:xta/plugins/plugin_post_media.dart';
import 'package:xta/ui/snack_bar_policy.dart';
import 'package:xta/utils/download_directory.dart';

const pixivDownloadSource = 'pixiv';

/// A Pixiv picture as the shared plugin media path sees it: [url] shown, [downloadUrl]
/// (else [url]) saved, both fetched from [imageHost] when the reader picked another server.
PluginMediaItem pixivImageMedia(String url, {String? downloadUrl, String imageHost = pixivImageHost}) =>
    PluginMediaItem(
      url: pixivImageUrl(url, imageHost),
      downloadUrl: downloadUrl == null ? null : pixivImageUrl(downloadUrl, imageHost),
    );

/// One page as the shared plugin media path sees it: shown large, saved original.
PluginMediaItem pixivPageMedia(PixivIllust illust, int page, {String imageHost = pixivImageHost}) =>
    pixivImageMedia(illust.viewerUrls[page], downloadUrl: illust.downloadUrlAt(page), imageHost: imageHost);

/// Saves one Pixiv picture by its address, like any other plugin image: a
/// profile picture, a header image, a picture in a novel.
Future<void> savePixivImage(BuildContext context, String url) => downloadPluginMediaItem(
  context,
  pixivImageMedia(url, imageHost: pixivImageHostSetting(PrefService.of(context, listen: false))),
  sourceName: pixivDownloadSource,
);

/// [pages] of [illust] (every page by default) queued for [destination], named
/// and foldered as [naming] says, fetched from [imageHost].
List<DownloadRequest> pixivPageRequests(
  PixivIllust illust,
  DownloadDestination destination, {
  PixivSaveNaming naming = const PixivSaveNaming(),
  Iterable<int>? pages,
  String imageHost = pixivImageHost,
}) => [
  for (final page in pages ?? Iterable<int>.generate(illust.viewerUrls.length))
    DownloadRequest(
      uri: Uri.parse(pixivImageUrl(illust.downloadUrlAt(page), imageHost)),
      fileName: naming.pageName(illust, page),
      treeUri: destination.treeUri,
      background: destination.background,
      subfolder: naming.subfolder(illust),
    ),
];

/// Where Pixiv pages are saved: the app's download queue unless a test swaps it.
class PixivDownloader {
  const PixivDownloader();

  static PixivDownloader of(BuildContext context) => context.read<PixivDownloader?>() ?? const PixivDownloader();

  /// One page, with the same prompts and messages as any other plugin image;
  /// true once it is saved.
  Future<bool> savePage(BuildContext context, PixivIllust illust, int page) {
    final prefs = PrefService.of(context, listen: false);
    final naming = PixivSaveNaming.of(prefs);
    return downloadPluginMediaItem(
      context,
      pixivPageMedia(illust, page, imageHost: pixivImageHostSetting(prefs)),
      sourceName: pixivDownloadSource,
      fileName: naming.pageName(illust, page),
      subfolder: naming.subfolder(illust),
    );
  }

  /// One page of a batch; true once the file is saved.
  Future<bool> save(DownloadRequest request) async {
    try {
      final entry = await DownloadStore.shared.enqueue(
        uri: request.uri,
        fileName: request.fileName,
        treeUri: request.treeUri,
        background: request.background,
        subfolder: request.subfolder,
      );
      return entry.status == DownloadStatus.completed;
    } catch (_) {
      return false;
    }
  }

  /// A file made on the device, such as an ugoira export; true once saved.
  Future<bool> saveBytes({
    required String treeUri,
    required String fileName,
    required Uint8List bytes,
    String? subfolder,
  }) async {
    try {
      final saved = await DownloadTransfer.saveBytes(
        treeUri: treeUri,
        fileName: fileName,
        bytes: bytes,
        subfolder: subfolder,
      );
      return saved != null;
    } catch (_) {
      return false;
    }
  }

  /// How many pages of a batch download at once, as the download settings say.
  int get parallel => DownloadStore.shared.concurrency;

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

  /// The configured download folder, else one asked for once for the whole
  /// work. Files made on the device (an ugoira export) can only go to a folder.
  Future<String?> batchFolder(BasePrefService prefs) async {
    final treeUri = prefs.get<String>(optionDownloadTreeUri) ?? '';
    if (prefs.get(optionDownloadType) != optionDownloadTypeAsk && treeUri.isNotEmpty) return treeUri;
    return DownloadDirectory.pick();
  }
}

class PixivDownloadProgress {
  final int total;
  final int done;
  final bool cancelled;

  /// Which requests of the batch were saved, by their place in it.
  final List<int> savedIndexes;

  const PixivDownloadProgress({
    required this.total,
    this.done = 0,
    this.savedIndexes = const [],
    this.cancelled = false,
  });

  int get saved => savedIndexes.length;

  int get failed => done - saved;

  /// The 1-based page being fetched now.
  int get current => (done + 1).clamp(1, total < 1 ? 1 : total);

  PixivDownloadProgress copyWith({int? done, List<int>? savedIndexes, bool? cancelled}) => PixivDownloadProgress(
    total: total,
    done: done ?? this.done,
    savedIndexes: savedIndexes ?? this.savedIndexes,
    cancelled: cancelled ?? this.cancelled,
  );
}

/// Saves a work's pages [parallel] at a time; a failed page never stops the rest.
class PixivDownloadStore extends Store<PixivDownloadProgress> {
  final Future<bool> Function(DownloadRequest request) save;
  final void Function(Uri uri) cancelActive;
  final int parallel;
  final _active = <DownloadRequest>{};

  PixivDownloadStore({required this.save, required this.cancelActive, required int total, this.parallel = 1})
    : super(PixivDownloadProgress(total: total));

  Future<PixivDownloadProgress> run(List<DownloadRequest> requests) async {
    var next = 0;
    Future<void> worker() async {
      while (!state.cancelled && next < requests.length) {
        final index = next++;
        _active.add(requests[index]);
        final saved = await save(requests[index]);
        _active.remove(requests[index]);
        update(state.copyWith(done: state.done + 1, savedIndexes: saved ? [...state.savedIndexes, index] : null));
      }
    }

    final workers = parallel.clamp(1, requests.isEmpty ? 1 : requests.length);
    await Future.wait([for (var i = 0; i < workers; i++) worker()]);
    return state;
  }

  void cancel() {
    if (state.cancelled) return;
    update(state.copyWith(cancelled: true));
    for (final request in [..._active]) {
      cancelActive(request.uri);
    }
  }
}

/// Saves every page of [illust]; true once at least one page is saved.
Future<bool> downloadAllPixivPages(BuildContext context, PixivIllust illust) =>
    savePixivPages(context, illust, [for (var page = 0; page < illust.viewerUrls.length; page++) page]);

/// Saves [pages] of [illust], asking first when some are saved already. One
/// page goes the way any plugin image does; several share one folder and show
/// progress with a cancel button. True once at least one page is saved.
Future<bool> savePixivPages(BuildContext context, PixivIllust illust, List<int> pages) async {
  final chosen = await choosePixivPagesToSave(context, illust, pages);
  if (chosen.isEmpty || !context.mounted) return false;
  if (pages.length > 1) return _saveBatch(context, illust, chosen);
  final index = PixivDownloadIndex.maybeOf(context);
  final saved = await PixivDownloader.of(context).savePage(context, illust, chosen.single);
  if (saved) await index?.record(illust.id, chosen);
  return saved;
}

/// [pages], all or only the unsaved ones as the reader picks when some were
/// saved before; empty when they cancel.
Future<List<int>> choosePixivPagesToSave(BuildContext context, PixivIllust illust, List<int> pages) async {
  final saved = PixivDownloadIndex.maybeOf(context)?.savedAmong(illust, pages) ?? const <int>[];
  if (saved.isEmpty) return pages;
  final choice = await showPixivResaveDialog(context, saved: saved.length, total: pages.length);
  return switch (choice) {
    PixivResave.all => pages,
    PixivResave.newOnly => [
      for (final page in pages)
        if (!saved.contains(page)) page,
    ],
    null => const [],
  };
}

Future<bool> _saveBatch(BuildContext context, PixivIllust illust, List<int> pages) async {
  final downloader = PixivDownloader.of(context);
  final index = PixivDownloadIndex.maybeOf(context);
  final messenger = ScaffoldMessenger.of(context);
  final l10n = L10n.of(context);
  final prefs = PrefService.of(context, listen: false);
  final destination = await downloader.batchDestination(prefs);
  if (destination == null || !messenger.mounted) return false;
  final requests = pixivPageRequests(
    illust,
    destination,
    naming: PixivSaveNaming.of(prefs),
    pages: pages,
    imageHost: pixivImageHostSetting(prefs),
  );
  final store = PixivDownloadStore(
    save: downloader.save,
    cancelActive: downloader.cancel,
    total: requests.length,
    parallel: downloader.parallel,
  );
  final progress = showPixivWorkingSnackBar(messenger, pixivDownloadSnackBar(store));
  final result = await store.run(requests);
  unawaited(progress.closed.whenComplete(store.destroy));
  await index?.record(illust.id, [for (final saved in result.savedIndexes) pages[saved]]);
  if (messenger.mounted) {
    messenger.hideCurrentSnackBar(reason: SnackBarClosedReason.hide);
    messenger.showSnackBar(SnackBar(content: Text(l10n.downloads_batch_result(result.saved, result.total))));
  }
  return result.saved > 0;
}

/// Replaces whatever is on screen with a long-running [snackBar].
ScaffoldFeatureController<SnackBar, SnackBarClosedReason> showPixivWorkingSnackBar(
  ScaffoldMessengerState messenger,
  SnackBar snackBar,
) {
  messenger
    ..clearSnackBars()
    ..removeCurrentSnackBar();
  return messenger.showSnackBar(snackBar);
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
    return PixivWorkingRow(
      value: progress.done == 0 || progress.total == 0 ? null : progress.done / progress.total,
      label: progress.cancelled
          ? l10n.downloads_cancelled
          : l10n.plugin_pixiv_downloading_page(progress.current, progress.total),
      onCancel: progress.cancelled ? null : onCancel,
    );
  }
}

/// A small progress ring, what is happening, an optional [note] under it and
/// Cancel while it can still stop.
class PixivWorkingRow extends StatelessWidget {
  final double? value;
  final String label;
  final String? note;
  final VoidCallback? onCancel;

  const PixivWorkingRow({super.key, required this.label, this.value, this.note, this.onCancel});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final cancel = onCancel;
    return Row(
      children: [
        SizedBox.square(
          dimension: 16,
          child: CircularProgressIndicator(
            strokeWidth: 2,
            value: value,
            backgroundColor: theme.colorScheme.onSurface.withValues(alpha: 0.16),
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(label, style: const TextStyle(height: 1.5)),
              if (note case final note?) Text(note, style: const TextStyle(fontSize: 12, height: 1.4)),
            ],
          ),
        ),
        if (cancel != null) TextButton(onPressed: cancel, child: Text(L10n.of(context).cancel)),
      ],
    );
  }
}
