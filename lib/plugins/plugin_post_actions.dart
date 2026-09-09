import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/saved/folder_picker.dart';
import 'package:xta/saved/saved_note_editor.dart';
import 'package:xta/saved/saved_tweet_model.dart';
import 'package:xta/tweet/tweet_footer.dart';
import 'package:xta/utils/urls.dart';

class PluginPostArchive {
  final String id;
  final String userId;
  final Map<String, dynamic> content;
  const PluginPostArchive({required this.id, required this.userId, required this.content});
}

enum _PostAction { bookmark, folder, note, reposts, quotes, browser }

Future<void> savePluginPost(BuildContext context, PluginPostArchive post) => fileSavedTweet(
  context,
  tweetId: post.id,
  userId: post.userId,
  content: post.content,
  folderId: rememberedSaveFolder(PrefService.of(context, listen: false)),
);

Future<void> editPluginPostNote(BuildContext context, PluginPostArchive post) async {
  final model = context.read<SavedTweetModel>();
  final note = model.state.where((row) => row.id == post.id).firstOrNull?.note;
  final prefs = PrefService.of(context, listen: false);
  final folderId = rememberedSaveFolder(prefs);
  await openSavedNoteEditor(
    context,
    note: note,
    onSave: (value) async {
      if (!model.isSaved(post.id)) {
        await model.saveTweet(post.id, post.userId, post.content, folderId: folderId);
        if (!model.isSaved(post.id)) throw StateError('Post could not be saved');
      }
      await model.setNote(post.id, value);
    },
  );
}

Future<void> showPluginPostActions(
  BuildContext context, {
  required PluginPostArchive post,
  required String url,
  VoidCallback? onReposts,
  VoidCallback? onQuotes,
}) async {
  final l10n = L10n.of(context);
  final model = context.read<SavedTweetModel?>();
  final saved = model?.isSaved(post.id) == true;
  final action = await showModalBottomSheet<_PostAction>(
    context: context,
    showDragHandle: true,
    isScrollControlled: true,
    builder: (context) => SafeArea(
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (model != null) ...[
              ListTile(
                leading: Icon(saved ? Icons.bookmark : Icons.bookmark_border),
                title: Text(saved ? l10n.unsave_from_this_device : l10n.save_on_this_device),
                onTap: () => Navigator.pop(context, _PostAction.bookmark),
              ),
              ListTile(
                leading: const Icon(Icons.folder_outlined),
                title: Text(l10n.save_to_folder),
                onTap: () => Navigator.pop(context, _PostAction.folder),
              ),
              ListTile(
                leading: const Icon(Icons.edit_note),
                title: Text(l10n.clip_note_hint),
                onTap: () => Navigator.pop(context, _PostAction.note),
              ),
            ],
            if (onReposts != null)
              ListTile(
                leading: const Icon(Icons.repeat),
                title: Text(l10n.plugin_post_reposted_by),
                onTap: () => Navigator.pop(context, _PostAction.reposts),
              ),
            if (onQuotes != null)
              ListTile(
                leading: const Icon(Icons.format_quote),
                title: Text(l10n.quotes),
                onTap: () => Navigator.pop(context, _PostAction.quotes),
              ),
            ListTile(
              leading: const Icon(Icons.open_in_new),
              title: Text(l10n.open_in_browser),
              onTap: () => Navigator.pop(context, _PostAction.browser),
            ),
          ],
        ),
      ),
    ),
  );
  if (!context.mounted || action == null) return;
  switch (action) {
    case _PostAction.bookmark:
      if (saved) {
        await model?.deleteSavedTweet(post.id);
      } else {
        await savePluginPost(context, post);
      }
    case _PostAction.folder:
      await showSaveToFolderSheet(context, tweetId: post.id, userId: post.userId, content: post.content);
    case _PostAction.note:
      await editPluginPostNote(context, post);
    case _PostAction.reposts:
      onReposts?.call();
    case _PostAction.quotes:
      onQuotes?.call();
    case _PostAction.browser:
      openUri(context, url);
  }
}

class PluginPostBookmark extends StatelessWidget {
  final PluginPostArchive post;
  const PluginPostBookmark({super.key, required this.post});
  @override
  Widget build(BuildContext context) {
    final model = context.read<SavedTweetModel?>();
    if (model == null) return const SizedBox.shrink();
    return ScopedBuilder<SavedTweetModel, List<SavedTweet>>(
      store: model,
      onState: (context, _) {
        final selected = model.isSaved(post.id);
        return GestureDetector(
          onLongPress: () =>
              showSaveToFolderSheet(context, tweetId: post.id, userId: post.userId, content: post.content),
          child: tweetFooterIconButton(
            context,
            selected ? Icons.bookmark : Icons.bookmark_border,
            selected ? Theme.of(context).colorScheme.primary : Theme.of(context).colorScheme.onSurfaceVariant,
            null,
            () => selected ? model.deleteSavedTweet(post.id) : savePluginPost(context, post),
            selected ? L10n.of(context).unsave_from_this_device : L10n.of(context).save_on_this_device,
          ),
        );
      },
    );
  }
}
