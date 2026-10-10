import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_download.dart';
import 'package:xta/plugins/pixiv/pixiv_download_index.dart';
import 'package:xta/plugins/pixiv/pixiv_download_naming.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_ugoira_export.dart';
import 'package:xta/ui/snack_bar_policy.dart';

/// Saves [illust]'s animation as a GIF, or its frame archive as a ZIP, into
/// the download folder under the file-name template, with progress and Cancel
/// in a snackbar. True once the file is saved.
Future<bool> savePixivUgoira(BuildContext context, PixivIllust illust, PixivUgoiraFormat format) async {
  final downloader = PixivDownloader.of(context);
  final encoder = PixivGifEncoder.of(context);
  final client = context.read<PixivClient>();
  final index = PixivDownloadIndex.maybeOf(context);
  final messenger = ScaffoldMessenger.of(context);
  final l10n = L10n.of(context);
  final prefs = PrefService.of(context, listen: false);
  final folder = await downloader.batchFolder(prefs);
  if (folder == null || !messenger.mounted) return false;
  final naming = PixivSaveNaming.of(prefs);
  final store = PixivUgoiraExportStore(
    load: () => loadPixivUgoiraSource(client, illust.id),
    encodeGif: encoder.encode,
    save: (bytes) => downloader.saveBytes(
      treeUri: folder,
      fileName: naming.workName(illust, format.extension),
      subfolder: naming.subfolder(illust),
      bytes: bytes,
    ),
  );
  final bar = showPixivWorkingSnackBar(messenger, _progressSnackBar(store));
  final saved = await store.run(format);
  unawaited(bar.closed.whenComplete(store.destroy));
  // The export stands for the work's first page, as Pixiv names it.
  if (saved) await index?.record(illust.id, const [0]);
  if (messenger.mounted) _report(messenger, _resultMessage(l10n, store.state.phase, format));
  return saved;
}

void _report(ScaffoldMessengerState messenger, String message) {
  messenger.hideCurrentSnackBar(reason: SnackBarClosedReason.hide);
  messenger.showSnackBar(SnackBar(content: Text(message)));
}

String _resultMessage(L10n l10n, PixivExportPhase phase, PixivUgoiraFormat format) => switch (phase) {
  PixivExportPhase.saved =>
    format == PixivUgoiraFormat.gif ? l10n.plugin_pixiv_ugoira_saved_gif : l10n.plugin_pixiv_ugoira_saved_zip,
  PixivExportPhase.cancelled => l10n.downloads_cancelled,
  _ => l10n.plugin_pixiv_ugoira_export_failed,
};

SnackBar _progressSnackBar(PixivUgoiraExportStore store) => WorkingSnackBar(
  content: ScopedBuilder<PixivUgoiraExportStore, PixivExportProgress>(
    store: store,
    onState: (context, progress) => PixivExportProgressRow(progress: progress, onCancel: store.cancel),
  ),
);

class PixivExportProgressRow extends StatelessWidget {
  final PixivExportProgress progress;
  final VoidCallback onCancel;

  const PixivExportProgressRow({super.key, required this.progress, required this.onCancel});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final encoding = progress.phase == PixivExportPhase.encoding;
    return PixivWorkingRow(
      value: encoding && progress.frames > 0 ? progress.frame / progress.frames : null,
      label: switch (progress.phase) {
        PixivExportPhase.fetching => l10n.plugin_pixiv_ugoira_fetching,
        PixivExportPhase.encoding => l10n.plugin_pixiv_ugoira_encoding(
          (progress.frame + 1).clamp(1, progress.frames),
          progress.frames,
        ),
        PixivExportPhase.cancelled => l10n.downloads_cancelled,
        _ => l10n.plugin_pixiv_ugoira_saving,
      },
      note: encoding ? l10n.plugin_pixiv_ugoira_slow : null,
      onCancel: progress.phase == PixivExportPhase.fetching || encoding ? onCancel : null,
    );
  }
}
