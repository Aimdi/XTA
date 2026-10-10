import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_download_naming.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_parser.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_reader_store.dart';
import 'package:xta/utils/file_dialogs.dart';

/// What an exported file holds.
enum PixivNovelExportFormat {
  /// Pixiv's markup removed: paragraphs as plain lines, ruby as `base(ruby)`.
  plain,

  /// The text exactly as Pixiv stores it, `[newpage]` and the rest included.
  markup,
}

/// `<title>.txt`, the title stripped of what no file system accepts; the
/// novel's id names it when nothing of the title is left.
String pixivNovelExportFileName(String title, int novelId) {
  final stem = pixivSafeFileStem(title);
  return '${stem.isEmpty ? 'pixiv-novel-$novelId' : stem}.txt';
}

String pixivNovelExportText(PixivNovelReading reading, PixivNovelExportFormat format) => switch (format) {
  PixivNovelExportFormat.plain => pixivNovelPlainText(reading.blocks),
  PixivNovelExportFormat.markup => reading.content.text,
};

/// Where an exported novel goes: a file the reader places through the
/// system's save dialog, unless a test swaps it.
class PixivNovelExporter {
  const PixivNovelExporter();

  static PixivNovelExporter of(BuildContext context) =>
      context.read<PixivNovelExporter?>() ?? const PixivNovelExporter();

  /// True once saved, false when the reader backed out of the dialog.
  Future<bool> save(String fileName, String text) async {
    final path = await saveWithDialog(
      fileName: fileName,
      data: Uint8List.fromList(utf8.encode(text)),
      mimeTypes: const ['text/plain'],
    );
    return path != null;
  }
}

/// Asks which text to export, then saves it as `<title>.txt` and says how it went.
Future<void> exportPixivNovel(BuildContext context, PixivNovelReading reading) async {
  final format = await showModalBottomSheet<PixivNovelExportFormat>(
    context: context,
    showDragHandle: true,
    builder: (_) => const PixivNovelExportSheet(),
  );
  if (format == null || !context.mounted) return;
  final l10n = L10n.of(context);
  final messenger = ScaffoldMessenger.of(context);
  final name = pixivNovelExportFileName(reading.novel.title, reading.novel.id);
  final bool saved;
  try {
    saved = await PixivNovelExporter.of(context).save(name, pixivNovelExportText(reading, format));
  } on Exception {
    messenger.showSnackBar(SnackBar(content: Text(l10n.plugin_pixiv_novel_export_failed)));
    return;
  }
  if (saved) messenger.showSnackBar(SnackBar(content: Text(l10n.plugin_pixiv_novel_exported(name))));
}

/// The two ways to export, each saying what the file will hold.
class PixivNovelExportSheet extends StatelessWidget {
  const PixivNovelExportSheet({super.key});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return SafeArea(
      child: ListView(
        shrinkWrap: true,
        children: [
          ListTile(title: Text(l10n.plugin_pixiv_novel_export, style: Theme.of(context).textTheme.titleMedium)),
          _choice(
            context,
            PixivNovelExportFormat.plain,
            l10n.plugin_pixiv_novel_export_plain,
            l10n.plugin_pixiv_novel_export_plain_hint,
          ),
          _choice(
            context,
            PixivNovelExportFormat.markup,
            l10n.plugin_pixiv_novel_export_markup,
            l10n.plugin_pixiv_novel_export_markup_hint,
          ),
        ],
      ),
    );
  }

  Widget _choice(BuildContext context, PixivNovelExportFormat format, String title, String hint) => ListTile(
    key: ValueKey('pixiv-novel-export-${format.name}'),
    leading: Icon(format == PixivNovelExportFormat.plain ? Icons.notes : Icons.code),
    title: Text(title),
    subtitle: Text(hint),
    onTap: () => Navigator.pop(context, format),
  );
}
