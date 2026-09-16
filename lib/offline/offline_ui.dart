import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/offline/offline_models.dart';

String offlineBytes(BuildContext context, int bytes) {
  final locale = Localizations.localeOf(context).toString();
  if (bytes < 1024) return '${NumberFormat.decimalPattern(locale).format(bytes)} B';
  if (bytes < 1024 * 1024) return '${NumberFormat('0.#', locale).format(bytes / 1024)} KiB';
  return '${NumberFormat('0.#', locale).format(bytes / (1024 * 1024))} MiB';
}

String offlineStatus(L10n l10n, OfflineEntry entry) => switch (entry.availability) {
  OfflineAvailability.available => l10n.offline_available,
  OfflineAvailability.partial => l10n.offline_partial,
  OfflineAvailability.unavailable => l10n.offline_unavailable,
};

String offlineDetails(L10n l10n, OfflineEntry entry) => entry.hasArticle
    ? l10n.offline_article_detail(entry.files.length, entry.totalMedia)
    : l10n.offline_media_count(entry.files.length, entry.totalMedia);

Future<bool> confirmOfflineSensitive(BuildContext context) async {
  final l10n = L10n.of(context);
  return await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: Text(l10n.offline_sensitive_question),
          content: Text(l10n.offline_sensitive_description),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context, false), child: Text(l10n.cancel)),
            FilledButton(onPressed: () => Navigator.pop(context, true), child: Text(l10n.offline_keep)),
          ],
        ),
      ) ??
      false;
}
