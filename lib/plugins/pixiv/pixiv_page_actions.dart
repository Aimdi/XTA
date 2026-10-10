import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_download.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_post_actions.dart';
import 'package:xta/plugins/pixiv/pixiv_ugoira_export.dart';
import 'package:xta/plugins/pixiv/pixiv_ugoira_save.dart';

enum PixivPageAction { downloadPage, downloadAll, saveGif, saveZip, allPages, direction, copyLink, more }

/// How a page sheet was closed: a page to show, an action to run, or the pages to save.
class PixivPageChoice {
  final int? page;
  final PixivPageAction? action;
  final List<int>? pages;

  const PixivPageChoice.jump(int this.page) : action = null, pages = null;
  const PixivPageChoice.action(PixivPageAction this.action) : page = null, pages = null;
  const PixivPageChoice.save(List<int> this.pages) : page = null, action = null;
}

IconData pixivPageActionIcon(PixivPageAction action, {bool readVertically = true}) => switch (action) {
  PixivPageAction.downloadPage => Icons.download_outlined,
  PixivPageAction.downloadAll => Icons.download_for_offline_outlined,
  PixivPageAction.saveGif => Icons.gif_box_outlined,
  PixivPageAction.saveZip => Icons.folder_zip_outlined,
  PixivPageAction.allPages => Icons.grid_view_outlined,
  PixivPageAction.direction => readVertically ? Icons.arrow_downward : Icons.swipe_outlined,
  PixivPageAction.copyLink => Icons.link,
  PixivPageAction.more => Icons.more_horiz,
};

String pixivPageActionLabel(L10n l10n, PixivPageAction action, {bool readVertically = true}) => switch (action) {
  PixivPageAction.downloadPage => l10n.plugin_pixiv_download_page,
  PixivPageAction.downloadAll => l10n.plugin_pixiv_download_all,
  PixivPageAction.saveGif => l10n.plugin_pixiv_ugoira_save_gif,
  PixivPageAction.saveZip => l10n.plugin_pixiv_ugoira_save_zip,
  PixivPageAction.allPages => l10n.plugin_pixiv_all_pages,
  PixivPageAction.direction => readVertically ? l10n.plugin_pixiv_read_vertically : l10n.plugin_pixiv_read_horizontally,
  PixivPageAction.copyLink => l10n.plugin_pixiv_copy_link,
  PixivPageAction.more => l10n.plugin_pixiv_more_actions,
};

/// Runs the actions every Pixiv page surface shares; false leaves the rest to the screen.
Future<bool> runPixivPageAction(BuildContext context, PixivPageAction action, PixivIllust illust, int page) async {
  switch (action) {
    case PixivPageAction.downloadPage:
      await savePixivPages(context, illust, [page]);
    case PixivPageAction.downloadAll:
      await downloadAllPixivPages(context, illust);
    case PixivPageAction.saveGif:
      await savePixivUgoira(context, illust, PixivUgoiraFormat.gif);
    case PixivPageAction.saveZip:
      await savePixivUgoira(context, illust, PixivUgoiraFormat.zip);
    case PixivPageAction.copyLink:
      await copyPixivLink(context, illust);
    case PixivPageAction.more:
      await showPixivPostActions(context, illust, workActions: false);
    case PixivPageAction.allPages || PixivPageAction.direction:
      return false;
  }
  return true;
}

Future<void> copyPixivLink(BuildContext context, PixivIllust illust) async {
  final messenger = ScaffoldMessenger.of(context);
  final copied = L10n.of(context).plugin_pixiv_link_copied;
  await Clipboard.setData(ClipboardData(text: illust.url));
  messenger.showSnackBar(SnackBar(content: Text(copied)));
}

/// Long-press / overflow sheet for one page of a work.
Future<PixivPageAction?> showPixivPageActions(BuildContext context, {required PixivIllust illust, required int page}) {
  final pages = illust.viewerUrls.length;
  final actions = [
    PixivPageAction.downloadPage,
    if (pages > 1) ...[PixivPageAction.downloadAll, PixivPageAction.allPages],
    if (illust.isUgoira && page == 0) ...[PixivPageAction.saveGif, PixivPageAction.saveZip],
    PixivPageAction.copyLink,
    PixivPageAction.more,
  ];
  return showModalBottomSheet<PixivPageAction>(
    context: context,
    showDragHandle: true,
    isScrollControlled: true,
    builder: (context) {
      final l10n = L10n.of(context);
      return SafeArea(
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              if (pages > 1)
                Padding(
                  padding: const EdgeInsetsDirectional.fromSTEB(16, 0, 16, 8),
                  child: Text(
                    l10n.plugin_pixiv_current_page(page + 1, pages),
                    style: Theme.of(context).textTheme.titleSmall,
                  ),
                ),
              for (final action in actions)
                ListTile(
                  key: ValueKey('pixiv-page-action-${action.name}'),
                  leading: Icon(pixivPageActionIcon(action)),
                  title: Text(pixivPageActionLabel(l10n, action)),
                  subtitle: action == PixivPageAction.saveGif ? Text(l10n.plugin_pixiv_ugoira_slow) : null,
                  onTap: () => Navigator.pop(context, action),
                ),
            ],
          ),
        ),
      );
    },
  );
}
