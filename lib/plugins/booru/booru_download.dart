/// Booru downloads: file names from the reader's template, and the posts a
/// feed has loaded queued at once, without the files the download history
/// already has.
library;

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:path/path.dart' as path;
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/downloads/download_destination.dart';
import 'package:xta/downloads/download_entry.dart';
import 'package:xta/downloads/download_naming.dart';
import 'package:xta/downloads/download_store.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/booru/booru_engines.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_post_actions.dart';
import 'package:xta/plugins/plugin_post_media.dart';

/// What a file name template may say, as the settings list them.
const booruFileNameTokens = ['{id}', '{md5}', '{site}', '{artist}', '{character}', '{copyright}', '{rating}'];

/// What [post] is saved as: [template] filled in, keeping the file's
/// extension; the file's own name when the template is empty or leaves
/// nothing.
String booruDownloadFileName(BooruPost post, String template) {
  final stem = safeFileStem(fillNameTemplate(template, _values(post)));
  return stem.isEmpty ? pluginMediaFileName(booruDownloadItem(post), pluginIdBooru) : '$stem${_extension(post)}';
}

Map<String, String> _values(BooruPost post) => {
  '{id}': post.id,
  '{md5}': post.md5 ?? '',
  '{site}': displayNameForBooruHost(post.host),
  '{artist}': _first(post, BooruTagCategory.artist),
  '{character}': _first(post, BooruTagCategory.character),
  '{copyright}': _first(post, BooruTagCategory.copyright),
  '{rating}': post.rating?.code ?? '',
};

String _first(BooruPost post, BooruTagCategory kind) =>
    post.tags.where((tag) => post.tagCategories[tag] == kind).firstOrNull ?? '';

/// The saved file's extension with its dot: the downloaded URL's, else the
/// one the host named.
String _extension(BooruPost post) {
  final fromUrl = path.extension(Uri.tryParse(post.downloadUrl)?.path ?? '').toLowerCase();
  if (RegExp(r'^\.[a-z0-9]{1,5}$').hasMatch(fromUrl)) return fromUrl;
  final named = (post.fileExt ?? '').toLowerCase();
  return named.isEmpty ? '' : '.$named';
}

/// What a batch queues: [posts] whose files are not [known] to the download
/// history or repeated in the batch, as many as the queue has [room] for,
/// named by [template]; and how many were skipped as already there.
({List<DownloadRequest> requests, int skipped}) booruDownloadPlan(
  List<BooruPost> posts, {
  required Set<Uri> known,
  required int room,
  required String template,
  required DownloadDestination destination,
}) {
  final seen = {...known};
  final fresh = [
    for (final post in posts)
      if (Uri.tryParse(post.downloadUrl) case final uri? when uri.hasScheme && seen.add(uri))
        DownloadRequest(
          uri: uri,
          fileName: booruDownloadFileName(post, template),
          treeUri: destination.treeUri,
          background: destination.background,
        ),
  ];
  return (requests: fresh.take(room < 0 ? 0 : room).toList(), skipped: posts.length - fresh.length);
}

/// Where Booru batches are queued: the app's download queue, unless a test
/// provides another.
class BooruDownloader {
  const BooruDownloader();

  static BooruDownloader of(BuildContext context) => context.read<BooruDownloader?>() ?? const BooruDownloader();

  /// Queues [posts] where the download setting says, asking for a folder
  /// once when it asks for each file. Null when the reader backs out.
  Future<({int queued, int skipped})?> downloadAll(BasePrefService prefs, List<BooruPost> posts) async {
    final destination = await batchDownloadDestination(prefs);
    if (destination == null) return null;
    final store = DownloadStore.shared;
    await store.initialize();
    final entries = store.state.entries;
    final plan = booruDownloadPlan(
      posts,
      known: {
        for (final entry in entries)
          if (entry.active || entry.status == DownloadStatus.completed) entry.uri,
      },
      room: maxActiveDownloads - entries.where((entry) => entry.active).length,
      template: prefs.get<String>(optionPluginBooruFileNameTemplate) ?? '',
      destination: destination,
    );
    unawaited(store.enqueueBatch(plan.requests));
    return (queued: plan.requests.length, skipped: plan.skipped);
  }
}

/// Asks before downloading the [posts] a feed has loaded, then says how
/// many were queued and how many were already downloaded.
Future<void> confirmBooruDownloadAll(BuildContext context, List<BooruPost> posts) async {
  if (posts.isEmpty) return;
  final l10n = L10n.of(context);
  final prefs = PrefService.of(context, listen: false);
  final downloader = BooruDownloader.of(context);
  final messenger = ScaffoldMessenger.of(context);
  final confirmed = await showDialog<bool>(
    context: context,
    builder: (context) => AlertDialog(
      title: Text(l10n.plugin_booru_download_all),
      content: Text(l10n.plugin_booru_download_all_body(posts.length)),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context, false), child: Text(l10n.cancel)),
        FilledButton(onPressed: () => Navigator.pop(context, true), child: Text(l10n.download)),
      ],
    ),
  );
  if (confirmed != true) return;
  final result = await downloader.downloadAll(prefs, posts);
  if (result == null || !messenger.mounted) return;
  messenger.showSnackBar(SnackBar(content: Text(l10n.plugin_booru_download_all_result(result.queued, result.skipped))));
}

/// The file name template, with the tokens it may use.
class BooruDownloadSettings extends StatefulWidget {
  const BooruDownloadSettings({super.key});

  @override
  State<BooruDownloadSettings> createState() => _BooruDownloadSettingsState();
}

class _BooruDownloadSettingsState extends State<BooruDownloadSettings> {
  late final BasePrefService _prefs = PrefService.of(context, listen: false);
  late final _template = TextEditingController(text: _prefs.get<String>(optionPluginBooruFileNameTemplate) ?? '');

  @override
  void dispose() {
    _template.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
      child: TextField(
        key: const ValueKey('booru-file-name-template'),
        controller: _template,
        autocorrect: false,
        enableSuggestions: false,
        decoration: InputDecoration(
          labelText: l10n.plugin_booru_file_name_template,
          helperText: l10n.plugin_booru_file_name_template_help(booruFileNameTokens.join(' ')),
          helperMaxLines: 3,
        ),
        onChanged: (value) => _prefs.set(optionPluginBooruFileNameTemplate, value.trim()),
      ),
    );
  }
}
