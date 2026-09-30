import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:intl/intl.dart';
import 'package:logging/logging.dart';
import 'package:path_provider/path_provider.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:share_plus/share_plus.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/rss/rss_models.dart';
import 'package:xta/plugins/rss/rss_opml.dart';
import 'package:xta/plugins/rss/rss_store.dart';
import 'package:xta/settings/settings_chrome.dart';
import 'package:xta/subscriptions/users_model.dart';

final _log = Logger('RssOpml');

/// A file the reader picked, read only when asked.
class RssOpmlFile {
  final int size;
  final Stream<List<int>> Function() open;
  const RssOpmlFile({required this.size, required this.open});
}

/// Where OPML files come from and go to; tests provide their own through [RssOpmlIoScope].
class RssOpmlIo {
  final Future<RssOpmlFile?> Function() pick;
  final Future<bool> Function(String name, Uint8List data) save;
  final Future<void> Function(String name, Uint8List data) share;
  final Future<RssOpmlDocument> Function(String source) parse;
  const RssOpmlIo({required this.pick, required this.save, required this.share, required this.parse});

  static const platform = RssOpmlIo(pick: _pickFile, save: _saveFile, share: _shareFile, parse: _parseAside);

  static RssOpmlIo of(BuildContext context) => context.getInheritedWidgetOfExactType<RssOpmlIoScope>()?.io ?? platform;
}

class RssOpmlIoScope extends InheritedWidget {
  final RssOpmlIo io;
  const RssOpmlIoScope({super.key, required this.io, required super.child});

  @override
  bool updateShouldNotify(RssOpmlIoScope oldWidget) => io != oldWidget.io;
}

Future<RssOpmlFile?> _pickFile() async {
  final file = await FilePicker.pickFile(type: FileType.any);
  return file == null ? null : RssOpmlFile(size: file.size, open: file.readAsByteStream);
}

Future<bool> _saveFile(String name, Uint8List data) async =>
    await FilePicker.saveFile(fileName: name, bytes: data) != null;

Future<void> _shareFile(String name, Uint8List data) async {
  final directory = await getTemporaryDirectory();
  final file = await File('${directory.path}/$name').writeAsBytes(data, flush: true);
  await SharePlus.instance.share(
    ShareParams(
      files: [XFile(file.path, mimeType: 'text/x-opml')],
      fileNameOverrides: [name],
    ),
  );
}

Future<RssOpmlDocument> _parseAside(String source) => compute(parseRssOpml, source);

String rssOpmlFileName(DateTime now) => 'xta-feeds-${DateFormat('yyyy-MM-dd').format(now)}.opml';

/// The picked file as UTF-8 text. A file over [rssOpmlMaxBytes] is refused without reading the rest of it.
Future<String> readRssOpmlFile(RssOpmlFile file) async {
  if (file.size > rssOpmlMaxBytes) throw const RssOpmlException(RssOpmlProblem.tooLarge);
  final bytes = BytesBuilder(copy: false);
  await for (final chunk in file.open()) {
    bytes.add(chunk);
    if (bytes.length > rssOpmlMaxBytes) throw const RssOpmlException(RssOpmlProblem.tooLarge);
  }
  try {
    return utf8.decode(bytes.takeBytes());
  } on FormatException {
    throw const RssOpmlException(RssOpmlProblem.malformed);
  }
}

/// One import or export at a time for a feeds store; the state says whether one is running.
class RssOpmlTransfer extends Store<bool> {
  static final _instances = Expando<RssOpmlTransfer>();
  RssOpmlTransfer._() : super(false);

  factory RssOpmlTransfer.of(RssFeedsStore feeds) => _instances[feeds] ??= RssOpmlTransfer._();

  Future<void> run(Future<void> Function() work) async {
    if (state) return;
    update(true);
    try {
      await work();
    } finally {
      update(false);
    }
  }
}

class _Report {
  final ScaffoldMessengerState? messenger;
  final L10n l10n;
  _Report(BuildContext context) : messenger = ScaffoldMessenger.maybeOf(context), l10n = L10n.of(context);

  void show(String message, {SnackBarAction? action}) => messenger
    ?..hideCurrentSnackBar()
    ..showSnackBar(SnackBar(content: Text(message), action: action));

  void problem(Object error) => show(switch (error) {
    RssOpmlException(problem: RssOpmlProblem.tooLarge) => l10n.plugin_rss_opml_too_large,
    RssOpmlException() => l10n.plugin_rss_opml_invalid,
    _ => l10n.plugin_rss_opml_read_failed,
  });

  /// Groups could not see the feeds yet: say so and offer to try again until they can.
  void groupsPending(String message, RssFeedsStore feeds) => show(
    '$message\n${l10n.plugin_rss_opml_groups_pending}',
    action: SnackBarAction(
      label: l10n.retry,
      onPressed: () async {
        if (!await feeds.retryTableSync()) groupsPending(message, feeds);
      },
    ),
  );

  void imported(RssImportResult result, RssFeedsStore feeds) {
    if (!result.persisted) {
      show(l10n.plugin_rss_opml_save_failed);
    } else if (result.imported + result.duplicates + result.skipped == 0) {
      show(l10n.plugin_rss_opml_empty);
    } else if (!result.tableSynced) {
      groupsPending(l10n.plugin_rss_opml_imported(result.imported, result.duplicates, result.skipped), feeds);
    } else {
      show(l10n.plugin_rss_opml_imported(result.imported, result.duplicates, result.skipped));
    }
  }
}

/// Brings the rest of RSS up to date with feeds just imported: their folders as tags, the timeline and groups.
Future<void> Function(RssImportResult) _afterImport(BuildContext context) {
  final tags = context.read<RssTagsStore?>();
  final timeline = context.read<RssTimelineStore?>();
  final subscriptions = context.read<SubscriptionsModel?>();
  return (result) async {
    if (result.imported == 0) return;
    try {
      await tags?.adoptTags(result.tags);
      if (tags != null) timeline?.syncTags(tags.state);
      unawaited(timeline?.refresh(force: true));
      await subscriptions?.reloadSubscriptions();
    } catch (error) {
      _log.warning('Imported feeds are saved, but other views could not refresh: $error');
    }
  };
}

/// Lets the reader pick an OPML file, follows the feeds it lists that are not followed yet, and says what happened.
Future<void> importRssOpmlFile(BuildContext context) {
  final io = RssOpmlIo.of(context);
  final feeds = context.read<RssFeedsStore>();
  final after = _afterImport(context);
  final report = _Report(context);
  return RssOpmlTransfer.of(feeds).run(() async {
    final RssOpmlDocument document;
    try {
      final file = await io.pick();
      if (file == null) return;
      document = await io.parse(await readRssOpmlFile(file));
    } catch (error) {
      report.problem(error);
      return;
    }
    final result = await feeds.importOpml(document);
    await after(result);
    report.imported(result, feeds);
  });
}

/// Writes every followed feed to an OPML file, then saves it where the reader chooses or hands it to the share sheet.
Future<void> exportRssOpmlFile(BuildContext context, {required bool share}) {
  final io = RssOpmlIo.of(context);
  final feeds = context.read<RssFeedsStore>();
  final prefs = PrefService.of(context, listen: false);
  final report = _Report(context);
  return RssOpmlTransfer.of(feeds).run(() async {
    final name = rssOpmlFileName(DateTime.now());
    try {
      final followed = await feeds.saved();
      if (followed.isEmpty) {
        report.show(report.l10n.plugin_rss_following_empty);
        return;
      }
      final tags = rssTagsFromPrefs(prefs.get(optionPluginRssTags));
      final data = utf8.encode(exportRssOpml(followed, tags: tags));
      if (share) {
        await io.share(name, data);
      } else if (await io.save(name, data)) {
        report.show(report.l10n.data_exported_to_fileName(name));
      }
    } catch (_) {
      report.show(report.l10n.plugin_rss_opml_export_failed);
    }
  });
}

/// Import and export of the followed feeds, for the RSS settings.
class RssOpmlSection extends StatelessWidget {
  const RssOpmlSection({super.key});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return ScopedBuilder<RssOpmlTransfer, bool>(
      store: RssOpmlTransfer.of(context.read<RssFeedsStore>()),
      onState: (context, busy) => SettingsSection(
        title: l10n.plugin_rss_opml_section,
        children: [
          SettingsRow(
            key: const ValueKey('rss-opml-import'),
            icon: Icons.upload_file_outlined,
            title: l10n.plugin_rss_opml_import,
            description: l10n.plugin_rss_opml_import_description,
            enabled: !busy,
            onTap: () => importRssOpmlFile(context),
          ),
          SettingsRow(
            key: const ValueKey('rss-opml-export'),
            icon: Icons.save_alt,
            title: l10n.plugin_rss_opml_export,
            description: l10n.plugin_rss_opml_export_description,
            enabled: !busy,
            onTap: () => exportRssOpmlFile(context, share: false),
            trailing: IconButton(
              key: const ValueKey('rss-opml-share'),
              tooltip: l10n.plugin_rss_opml_share,
              icon: const Icon(Icons.share_outlined),
              onPressed: busy ? null : () => exportRssOpmlFile(context, share: true),
            ),
          ),
        ],
      ),
    );
  }
}

/// The import and export section on a page of its own, for Reader Tools.
class RssOpmlScreen extends StatelessWidget {
  const RssOpmlScreen({super.key});

  @override
  Widget build(BuildContext context) => SettingsPageScaffold(
    title: L10n.of(context).plugin_rss_opml_tool,
    body: const SettingsList(children: [RssOpmlSection()]),
  );
}
