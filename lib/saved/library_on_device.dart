import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/saved/saved_tab_order.dart';
import 'package:xta/ui/empty_pane.dart';

/// Which empty copy the Saved library should show.
enum SavedLibraryEmptyKind { search, likes, saved, folder, notes }

SavedLibraryEmptyKind savedLibraryEmptyKind({
  required String query,
  required String filter,
}) {
  if (query.isNotEmpty) {
    return SavedLibraryEmptyKind.search;
  }
  if (filter == savedTabFavorites) {
    return SavedLibraryEmptyKind.likes;
  }
  if (filter == savedTabNotes) {
    return SavedLibraryEmptyKind.notes;
  }
  if (filter == savedTabAll) {
    return SavedLibraryEmptyKind.saved;
  }
  return SavedLibraryEmptyKind.folder;
}

/// Quiet reminder that this library never writes back to X.
class SavedLibraryOnDeviceNotice extends StatelessWidget {
  final String filter;

  const SavedLibraryOnDeviceNotice({super.key, required this.filter});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final message = filter == savedTabFavorites
        ? l10n.likes_stay_on_device_notice
        : filter == savedTabNotes
        ? l10n.local_note_device_notice
        : l10n.saves_stay_on_device_notice;
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
      child: Text(
        message,
        style: Theme.of(
          context,
        ).textTheme.bodySmall?.copyWith(color: Theme.of(context).hintColor),
      ),
    );
  }
}

/// Empty Saved / Likes / folder / search / notes, with an icon instead of a lone line.
class SavedLibraryEmpty extends StatelessWidget {
  final SavedLibraryEmptyKind kind;
  final VoidCallback? onWriteNote;

  const SavedLibraryEmpty({super.key, required this.kind, this.onWriteNote});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final (:icon, :message) = switch (kind) {
      SavedLibraryEmptyKind.search => (
        icon: Icons.search_off,
        message: l10n.no_posts_match_your_search,
      ),
      SavedLibraryEmptyKind.likes => (
        icon: Icons.favorite_border,
        message: l10n.no_liked_posts_yet,
      ),
      SavedLibraryEmptyKind.saved => (
        icon: Icons.bookmark_border,
        message: l10n.you_have_not_saved_any_tweets_yet,
      ),
      SavedLibraryEmptyKind.folder => (
        icon: Icons.folder_open,
        message: l10n.folder_is_empty,
      ),
      SavedLibraryEmptyKind.notes => (
        icon: Icons.edit_note,
        message: l10n.local_note_empty,
      ),
    };
    return EmptyPane(
      icon: icon,
      message: message,
      action: kind == SavedLibraryEmptyKind.notes && onWriteNote != null
          ? FilledButton.icon(
              onPressed: onWriteNote,
              icon: const Icon(Icons.edit_note),
              label: Text(l10n.local_note_fab),
            )
          : null,
    );
  }
}