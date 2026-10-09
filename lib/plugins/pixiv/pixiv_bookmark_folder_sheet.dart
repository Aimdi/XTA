import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_store.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_settings.dart';

/// Where a bookmark goes: public or private, and optionally into a folder.
typedef PixivBookmarkChoice = ({String restrict, String? folder});

/// Asks whether to bookmark publicly, privately or into one of [folders].
Future<PixivBookmarkChoice?> showPixivBookmarkFolderSheet(BuildContext context, List<String> folders) {
  final l10n = L10n.of(context);
  return showModalBottomSheet<PixivBookmarkChoice>(
    context: context,
    showDragHandle: true,
    builder: (sheetContext) {
      void choose(String restrict, [String? folder]) =>
          Navigator.pop(sheetContext, (restrict: restrict, folder: folder));
      return SafeArea(
        child: ListView(
          shrinkWrap: true,
          children: [
            ListTile(title: Text(l10n.plugin_pixiv_bookmark_folder)),
            ListTile(
              leading: const Icon(Icons.bookmark_border),
              title: Text(l10n.plugin_pixiv_bookmarks_public),
              onTap: () => choose('public'),
            ),
            ListTile(
              key: const ValueKey('pixiv-bookmark-private'),
              leading: const Icon(Icons.lock_outline),
              title: Text(l10n.plugin_pixiv_bookmarks_private),
              onTap: () => choose('private'),
            ),
            for (final folder in folders)
              ListTile(
                leading: const Icon(Icons.folder_outlined),
                title: Text(folder),
                onTap: () => choose('public', folder),
              ),
          ],
        ),
      );
    },
  );
}

/// Bookmarks [illust] wherever the reader picks; a failure says why in a snack bar.
Future<void> bookmarkPixivIllustIntoFolder(BuildContext context, PixivIllust illust) async {
  final l10n = L10n.of(context);
  final client = context.read<PixivClient>();
  final bookmarks = context.read<PixivBookmarkStore>();
  final messenger = ScaffoldMessenger.of(context);
  void fail(Object error) => messenger.showSnackBar(SnackBar(content: Text(pixivErrorMessage(l10n, error))));

  final List<String> folders;
  try {
    folders = await client.bookmarkFolders();
  } catch (error) {
    fail(error);
    return;
  }
  if (!context.mounted) return;
  final chosen = await showPixivBookmarkFolderSheet(context, folders);
  if (chosen == null || !context.mounted) return;
  try {
    await client.addBookmark(illust.id, restrict: chosen.restrict, folder: chosen.folder);
    bookmarks.update({...bookmarks.state, illust.id: true});
  } catch (error) {
    if (context.mounted) fail(error);
  }
}
