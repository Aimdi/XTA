import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/substack/substack_client.dart';
import 'package:xta/plugins/substack/substack_opml.dart';
import 'package:xta/plugins/substack/substack_store.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/utils/file_dialogs.dart';

/// Where OPML files come from and go: the system's file dialogs, unless a
/// test swaps them.
class SubstackOpmlFiles {
  const SubstackOpmlFiles();

  static SubstackOpmlFiles of(BuildContext context) {
    try {
      return context.read<SubstackOpmlFiles>();
    } catch (_) {
      return const SubstackOpmlFiles();
    }
  }

  /// Where [text] was saved, or null when the reader backed out.
  Future<String?> save(String fileName, String text) => saveWithDialog(
    fileName: fileName,
    data: Uint8List.fromList(utf8.encode(text)),
    mimeTypes: const ['text/x-opml', 'application/xml'],
  );

  /// The text of the file the reader picks, or null.
  Future<String?> pick() async {
    final path = await pickFileWithDialog();
    return path == null ? null : File(path).readAsString();
  }
}

/// The settings rows that export the follows to OPML and import them back.
class SubstackOpmlSection extends StatefulWidget {
  const SubstackOpmlSection({super.key});

  @override
  State<SubstackOpmlSection> createState() => _SubstackOpmlSectionState();
}

class _SubstackOpmlSectionState extends State<SubstackOpmlSection> {
  late final _import = SubstackOpmlImportStore(
    context.read<SubstackClient>(),
    context.read<SubstackPublicationsStore>(),
  );

  @override
  void dispose() {
    _import.destroy();
    super.dispose();
  }

  Future<void> _export() async {
    final l10n = L10n.of(context);
    final messenger = ScaffoldMessenger.of(context);
    final files = SubstackOpmlFiles.of(context);
    final publications = context.read<SubstackPublicationsStore>();
    if (publications.state.isEmpty) await publications.load();
    final opml = substackOpml(publications.state, title: l10n.plugin_substack_title);
    final path = await files.save('substack-follows.opml', opml);
    if (path == null) return;
    messenger
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(l10n.data_exported_to_fileName(path))));
  }

  Future<void> _importFile() async {
    final l10n = L10n.of(context);
    final messenger = ScaffoldMessenger.of(context);
    final files = SubstackOpmlFiles.of(context);
    final publications = context.read<SubstackPublicationsStore>();
    SubscriptionsModel? subscriptions;
    try {
      subscriptions = context.read<SubscriptionsModel>();
    } catch (_) {}
    final text = await files.pick();
    if (text == null) return;
    final sites = substackOpmlSites(text);
    if (sites.isEmpty) {
      messenger
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text(l10n.plugin_substack_opml_empty)));
      return;
    }
    if (publications.state.isEmpty) await publications.load();
    final result = await _import.run(sites);
    if (result.added > 0) await subscriptions?.reloadSubscriptions();
    messenger
      ..hideCurrentSnackBar()
      ..showSnackBar(
        SnackBar(content: Text(l10n.plugin_substack_opml_result(result.added, result.alreadyFollowed, result.failed))),
      );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return ScopedBuilder<SubstackOpmlImportStore, SubstackOpmlImport?>(
      store: _import,
      onState: (context, progress) {
        final running = progress != null && progress.done < progress.total;
        return Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.upload_file),
              title: Text(l10n.plugin_substack_opml_export),
              subtitle: Text(l10n.plugin_substack_opml_export_description),
              onTap: running ? null : _export,
            ),
            ListTile(
              leading: const Icon(Icons.download),
              title: Text(l10n.plugin_substack_opml_import),
              subtitle: Text(
                running
                    ? l10n.plugin_substack_opml_importing(progress.done, progress.total)
                    : l10n.plugin_substack_opml_import_description,
              ),
              onTap: running ? null : _importFile,
            ),
            if (running) LinearProgressIndicator(value: progress.done / progress.total),
          ],
        );
      },
    );
  }
}
