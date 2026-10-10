import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_avatar.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_content.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_reader_header.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_reader_store.dart';

/// The id of a profile's Novels tab, which the menu's author row opens on.
const pixivNovelReaderAuthorTab = 'novels';

enum PixivNovelMenuAction { author, shareAuthor, previous, next, appearance, export, share, shareSeries, openOnPixiv }

/// The reader's menu: the author, the chapters beside this one, and what can
/// be done with the novel. Answers with the entry chosen.
Future<PixivNovelMenuAction?> showPixivNovelReaderMenu(BuildContext context, PixivNovelReading reading) =>
    showModalBottomSheet<PixivNovelMenuAction>(
      context: context,
      showDragHandle: true,
      builder: (_) => PixivNovelReaderMenu(reading: reading),
    );

class PixivNovelReaderMenu extends StatelessWidget {
  final PixivNovelReading reading;

  const PixivNovelReaderMenu({super.key, required this.reading});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final content = reading.content;
    return SafeArea(
      child: ListView(
        shrinkWrap: true,
        children: [
          _author(context, l10n),
          const Divider(height: 1),
          ?_chapter(context, l10n, content.previous, PixivNovelMenuAction.previous, Icons.chevron_left),
          ?_chapter(context, l10n, content.next, PixivNovelMenuAction.next, Icons.chevron_right),
          _entry(context, PixivNovelMenuAction.appearance, Icons.text_fields, l10n.article_reader_appearance),
          _entry(context, PixivNovelMenuAction.export, Icons.description_outlined, l10n.plugin_pixiv_novel_export),
          _entry(context, PixivNovelMenuAction.share, Icons.share_outlined, l10n.share_link),
          if (reading.novel.series != null)
            _entry(
              context,
              PixivNovelMenuAction.shareSeries,
              Icons.collections_bookmark_outlined,
              l10n.plugin_pixiv_novel_share_series,
            ),
          _entry(context, PixivNovelMenuAction.openOnPixiv, Icons.open_in_new, l10n.plugin_pixiv_open_on_pixiv),
        ],
      ),
    );
  }

  Widget _author(BuildContext context, L10n l10n) {
    final user = reading.novel.user;
    return ListTile(
      key: const ValueKey('pixiv-novel-menu-author'),
      leading: PixivAvatar.user(user),
      title: Text(user.name, maxLines: 1, overflow: TextOverflow.ellipsis),
      subtitle: Text(l10n.plugin_pixiv_novel_author_novels),
      onTap: user.id == 0 ? null : () => Navigator.pop(context, PixivNovelMenuAction.author),
      trailing: IconButton(
        key: const ValueKey('pixiv-novel-menu-shareAuthor'),
        tooltip: l10n.plugin_pixiv_novel_share_author,
        icon: const Icon(Icons.share_outlined),
        onPressed: user.id == 0 ? null : () => Navigator.pop(context, PixivNovelMenuAction.shareAuthor),
      ),
    );
  }

  Widget? _chapter(
    BuildContext context,
    L10n l10n,
    PixivNovelNeighbour? chapter,
    PixivNovelMenuAction action,
    IconData icon,
  ) {
    if (chapter == null) return null;
    final name = pixivNovelNeighbourName(l10n, chapter);
    return ListTile(
      key: ValueKey('pixiv-novel-menu-${action.name}'),
      enabled: chapter.viewable,
      leading: Icon(icon),
      title: Text(
        action == PixivNovelMenuAction.previous ? l10n.plugin_pixiv_novel_previous : l10n.plugin_pixiv_novel_next,
      ),
      subtitle: name.isEmpty ? null : Text(name, maxLines: 2, overflow: TextOverflow.ellipsis),
      onTap: () => Navigator.pop(context, action),
    );
  }

  Widget _entry(BuildContext context, PixivNovelMenuAction action, IconData icon, String label) => ListTile(
    key: ValueKey('pixiv-novel-menu-${action.name}'),
    leading: Icon(icon),
    title: Text(label),
    onTap: () => Navigator.pop(context, action),
  );
}
