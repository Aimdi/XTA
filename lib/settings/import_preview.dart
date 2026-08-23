import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/settings/backup_data.dart';

/// What the reader agreed to once they had seen what the file holds.
class ImportChoice {
  final bool includeReadPositions;

  const ImportChoice({required this.includeReadPositions});
}

/// Shows what a backup would restore and where it came from. Returns null when
/// the reader backs out, so nothing is written until they say yes.
Future<ImportChoice?> showImportPreview(
  BuildContext context,
  SettingsData data,
) {
  return showDialog<ImportChoice>(
    context: context,
    builder: (context) => _ImportPreview(data: data),
  );
}

class _ImportPreview extends StatefulWidget {
  final SettingsData data;

  const _ImportPreview({required this.data});

  @override
  State<_ImportPreview> createState() => _ImportPreviewState();
}

class _ImportPreviewState extends State<_ImportPreview> {
  bool _includeReadPositions = false;

  @override
  Widget build(BuildContext context) {
    final counts = backupCounts(widget.data);
    final origin = _origin(context, widget.data);

    return AlertDialog(
      title: Text(L10n.of(context).import_backup),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (origin != null) ...[
              Text(origin, style: Theme.of(context).textTheme.bodySmall),
              const SizedBox(height: 12),
            ],
            ...counts.entries.map(
              (entry) => _CountRow(
                label: _label(context, entry.key),
                count: entry.value,
              ),
            ),
            if (counts.containsKey(BackupCategory.readPositions))
              _readPositions(context),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: Text(L10n.of(context).cancel),
        ),
        FilledButton(
          onPressed: () => Navigator.pop(
            context,
            ImportChoice(includeReadPositions: _includeReadPositions),
          ),
          child: Text(L10n.of(context).import),
        ),
      ],
    );
  }

  Widget _readPositions(BuildContext context) {
    return CheckboxListTile(
      contentPadding: EdgeInsets.zero,
      value: _includeReadPositions,
      title: Text(L10n.of(context).import_read_positions),
      subtitle: Text(L10n.of(context).import_read_positions_description),
      onChanged: (value) =>
          setState(() => _includeReadPositions = value == true),
    );
  }
}

class _CountRow extends StatelessWidget {
  final String label;
  final int count;

  const _CountRow({required this.label, required this.count});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        children: [
          Expanded(child: Text(label)),
          Text('$count'),
        ],
      ),
    );
  }
}

/// Which build wrote the file and when, or null for one made before the header
/// existed — those say nothing about themselves, and a made-up date would be
/// worse than no line at all.
String? _origin(BuildContext context, SettingsData data) {
  final version = data.appVersion;
  final exportedAt = data.exportedAt;
  if (version == null || exportedAt == null) {
    return null;
  }

  final locale = Localizations.localeOf(context).toString();

  return L10n.of(context).import_preview_from(
    version,
    DateFormat.yMd(locale).add_Hm().format(exportedAt),
  );
}

String _label(BuildContext context, BackupCategory category) =>
    switch (category) {
      BackupCategory.settings => L10n.of(context).settings,
      BackupCategory.subscriptions => L10n.of(context).subscriptions,
      BackupCategory.substack => L10n.of(context).plugin_substack_title,
      BackupCategory.rss => L10n.of(context).plugin_rss_title,
      BackupCategory.subreddits => L10n.of(
        context,
      ).plugin_reddit_search_subreddits,
      BackupCategory.stocks => L10n.of(context).plugin_stocks_title,
      BackupCategory.threads => L10n.of(context).plugin_threads_title,
      BackupCategory.bluesky => L10n.of(context).plugin_bluesky_title,
      BackupCategory.mastodon => L10n.of(context).plugin_mastodon_title,
      BackupCategory.booruTags => L10n.of(context).plugin_booru_followed_tags,
      BackupCategory.ehFavorites => L10n.of(context).plugin_eh_tab_favorites,
      BackupCategory.ehHistory => L10n.of(context).plugin_eh_tab_history,
      BackupCategory.tiktokSubscriptions => L10n.of(
        context,
      ).plugin_tiktok_tab_accounts,
      BackupCategory.instagramSubscriptions => L10n.of(
        context,
      ).plugin_instagram_tab_accounts,
      BackupCategory.groups => L10n.of(context).groups,
      BackupCategory.groupMembers => L10n.of(context).group_members,
      BackupCategory.savedPosts => L10n.of(context).saved,
      BackupCategory.folders => L10n.of(context).folders,
      BackupCategory.likedPosts => L10n.of(context).favorites,
      BackupCategory.filters => L10n.of(context).filters,
      BackupCategory.readPositions => L10n.of(context).reading_positions,
      BackupCategory.upvotes => L10n.of(context).plugin_reddit_upvotes,
      BackupCategory.threadsLikes => L10n.of(context).plugin_threads_liked,
      BackupCategory.blueskyLikes => L10n.of(context).plugin_bluesky_liked,
      BackupCategory.accounts => L10n.of(context).account,
      BackupCategory.profileNotes => L10n.of(context).profile_note_title,
      BackupCategory.antennas => L10n.of(context).antenna_title,
    };
