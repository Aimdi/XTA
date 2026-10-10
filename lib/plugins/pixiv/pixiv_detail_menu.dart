import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_button.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_editor.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_sheet.dart';
import 'package:xta/plugins/pixiv/pixiv_page_actions.dart';
import 'package:xta/plugins/pixiv/pixiv_page_surface.dart';
import 'package:xta/utils/urls.dart';

/// One entry of a work's overflow menu. A feature adds its entry to
/// [pixivDetailMenuEntries] rather than to the screen.
class PixivDetailMenuEntry {
  /// Stable name; the menu item is keyed `pixiv-illust-menu-<id>`.
  final String id;
  final IconData icon;
  final String Function(L10n l10n) label;
  final Future<void> Function(PixivPageSurface surface) run;
  final bool Function(PixivIllust illust) offeredFor;

  const PixivDetailMenuEntry({
    required this.id,
    required this.icon,
    required this.label,
    required this.run,
    this.offeredFor = _always,
  });
}

bool _always(PixivIllust _) => true;

bool _manyPages(PixivIllust illust) => illust.viewerUrls.length > 1;

Future<void> _downloadAll(PixivPageSurface surface) =>
    surface.runPageAction(PixivPageAction.downloadAll, surface.currentPage);

Future<void> _editBookmark(PixivPageSurface surface) => showPixivBookmarkEditor(surface.context, surface.pageIllust);

Future<void> _copyLink(PixivPageSurface surface) =>
    surface.runPageAction(PixivPageAction.copyLink, surface.currentPage);

Future<void> _openOnPixiv(PixivPageSurface surface) => openUri(surface.context, surface.pageIllust.url);

Future<void> _mute(PixivPageSurface surface) => showPixivMuteSheet(surface.context, surface.pageIllust);

Future<void> _moreActions(PixivPageSurface surface) => surface.runPageAction(PixivPageAction.more, surface.currentPage);

final pixivDetailMenuEntries = <PixivDetailMenuEntry>[
  PixivDetailMenuEntry(
    id: 'downloadAll',
    icon: Icons.download_for_offline_outlined,
    label: (l10n) => l10n.plugin_pixiv_download_all,
    run: _downloadAll,
    offeredFor: _manyPages,
  ),
  PixivDetailMenuEntry(
    id: 'bookmark',
    icon: Icons.edit_outlined,
    label: (l10n) => l10n.plugin_pixiv_bookmark_edit,
    run: _editBookmark,
  ),
  PixivDetailMenuEntry(id: 'copyLink', icon: Icons.link, label: (l10n) => l10n.plugin_pixiv_copy_link, run: _copyLink),
  PixivDetailMenuEntry(
    id: 'open',
    icon: Icons.open_in_new,
    label: (l10n) => l10n.plugin_pixiv_open_on_pixiv,
    run: _openOnPixiv,
  ),
  PixivDetailMenuEntry(
    id: 'mute',
    icon: Icons.volume_off_outlined,
    label: (l10n) => l10n.plugin_pixiv_mute_illust,
    run: _mute,
  ),
  PixivDetailMenuEntry(
    id: 'more',
    icon: Icons.more_horiz,
    label: (l10n) => l10n.plugin_pixiv_more_actions,
    run: _moreActions,
  ),
];

/// A work's AppBar actions: bookmark, save this page, and the overflow menu.
List<Widget> pixivDetailActions(BuildContext context, PixivPageSurface surface) => [
  PixivBookmarkButton(illust: surface.pageIllust),
  IconButton(
    key: const ValueKey('pixiv-illust-download'),
    tooltip: L10n.of(context).plugin_pixiv_download_page,
    onPressed: () => surface.runPageAction(PixivPageAction.downloadPage, surface.currentPage),
    icon: const Icon(Icons.download_outlined),
  ),
  PixivDetailMenu(surface: surface),
];

/// The overflow menu built from [pixivDetailMenuEntries].
class PixivDetailMenu extends StatelessWidget {
  final PixivPageSurface surface;

  const PixivDetailMenu({super.key, required this.surface});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return PopupMenuButton<PixivDetailMenuEntry>(
      key: const ValueKey('pixiv-illust-menu'),
      onSelected: (entry) => entry.run(surface),
      itemBuilder: (_) => [
        for (final entry in pixivDetailMenuEntries)
          if (entry.offeredFor(surface.pageIllust)) _item(l10n, entry),
      ],
    );
  }

  PopupMenuItem<PixivDetailMenuEntry> _item(L10n l10n, PixivDetailMenuEntry entry) => PopupMenuItem(
    key: ValueKey('pixiv-illust-menu-${entry.id}'),
    value: entry,
    child: Row(
      children: [
        Icon(entry.icon, size: 20),
        const SizedBox(width: 12),
        Flexible(child: Text(entry.label(l10n))),
      ],
    ),
  );
}
