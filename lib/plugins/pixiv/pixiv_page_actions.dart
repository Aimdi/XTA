import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_actions.dart';
import 'package:xta/plugins/pixiv/pixiv_download.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_post_actions.dart';

enum PixivPageAction { downloadPage, downloadAll, allPages, direction, copyLink, more }

/// How a page sheet was closed: a page to show, or an action to run.
class PixivPageChoice {
  final int? page;
  final PixivPageAction? action;

  const PixivPageChoice.jump(int this.page) : action = null;
  const PixivPageChoice.action(PixivPageAction this.action) : page = null;
}

IconData pixivPageActionIcon(PixivPageAction action, {bool readVertically = true}) => switch (action) {
  PixivPageAction.downloadPage => Icons.download_outlined,
  PixivPageAction.downloadAll => Icons.download_for_offline_outlined,
  PixivPageAction.allPages => Icons.grid_view_outlined,
  PixivPageAction.direction => readVertically ? Icons.arrow_downward : Icons.swipe_outlined,
  PixivPageAction.copyLink => Icons.link,
  PixivPageAction.more => Icons.more_horiz,
};

String pixivPageActionLabel(L10n l10n, PixivPageAction action, {bool readVertically = true}) => switch (action) {
  PixivPageAction.downloadPage => l10n.plugin_pixiv_download_page,
  PixivPageAction.downloadAll => l10n.plugin_pixiv_download_all,
  PixivPageAction.allPages => l10n.plugin_pixiv_all_pages,
  PixivPageAction.direction => readVertically ? l10n.plugin_pixiv_read_vertically : l10n.plugin_pixiv_read_horizontally,
  PixivPageAction.copyLink => l10n.plugin_pixiv_copy_link,
  PixivPageAction.more => l10n.plugin_pixiv_more_actions,
};

/// Runs the actions every Pixiv page surface shares; false leaves the rest to the screen.
Future<bool> runPixivPageAction(BuildContext context, PixivPageAction action, PixivIllust illust, int page) async {
  switch (action) {
    case PixivPageAction.downloadPage || PixivPageAction.downloadAll:
      await _save(context, illust, page, all: action == PixivPageAction.downloadAll);
    case PixivPageAction.copyLink:
      await copyPixivLink(context, illust);
    case PixivPageAction.more:
      showPixivPostActions(context, illust);
    case PixivPageAction.allPages || PixivPageAction.direction:
      return false;
  }
  return true;
}

/// Saves one page or all of them, then bookmarks the work if the reader asked
/// for bookmark-after-save.
Future<void> _save(BuildContext context, PixivIllust illust, int page, {required bool all}) async {
  final saved = all
      ? await downloadAllPixivPages(context, illust)
      : await PixivDownloader.of(context).savePage(context, illust, page);
  if (saved && context.mounted) await bookmarkPixivAfterSave(context, illust);
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
                  onTap: () => Navigator.pop(context, action),
                ),
            ],
          ),
        ),
      );
    },
  );
}
