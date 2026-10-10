import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_detail_tags.dart';
import 'package:xta/plugins/pixiv/pixiv_link_open.dart';
import 'package:xta/plugins/pixiv/pixiv_links.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';

/// One way to open a number typed into search, drawn as a tile.
typedef PixivNumericShortcut = Widget Function(BuildContext context, int id);

/// What a query of digits offers to open, in order. A feature with its own
/// numbered pages adds its tile here.
const List<PixivNumericShortcut> pixivNumericShortcuts = [pixivArtworkShortcut, pixivUserShortcut, pixivisionShortcut];

Widget pixivArtworkShortcut(BuildContext context, int id) => ListTile(
  key: ValueKey('pixiv-open-artwork-$id'),
  leading: const Icon(Icons.image_outlined),
  title: Text(L10n.of(context).plugin_pixiv_search_open_artwork('$id')),
  onTap: () => openPixivLinkOrSay(context, PixivLinkRef.artwork(id)),
);

Widget pixivUserShortcut(BuildContext context, int id) => ListTile(
  key: ValueKey('pixiv-open-user-$id'),
  leading: const Icon(Icons.person_outline),
  title: Text(L10n.of(context).plugin_pixiv_search_open_user('$id')),
  onTap: () => openPixivLinkOrSay(context, PixivLinkRef.user(id)),
);

Widget pixivisionShortcut(BuildContext context, int id) => ListTile(
  key: ValueKey('pixiv-open-pixivision-$id'),
  leading: const Icon(Icons.article_outlined),
  title: Text(L10n.of(context).plugin_pixiv_search_open_pixivision('$id')),
  onTap: () => openPixivLinkOrSay(context, PixivisionLinkRef(id)),
);

/// Opens [link], saying so when Pixiv could not find what it names.
Future<void> openPixivLinkOrSay(BuildContext context, PixivLinkRef link) async {
  final messenger = ScaffoldMessenger.of(context);
  final message = L10n.of(context).plugin_pixiv_open_link_failed;
  if (!await openPixivLinkRef(context, link)) {
    messenger.showSnackBar(SnackBar(content: Text(message)));
  }
}

/// What covers the results while the reader types: the id [shortcuts] for a
/// number, then tags completing the word being typed. A tap on a tag hands it
/// to [onPick]; a long press copies it.
class PixivSearchPicker extends StatelessWidget {
  final int? numericId;
  final List<PixivTrendTag> suggestions;
  final ValueChanged<PixivTrendTag> onPick;
  final List<PixivNumericShortcut> shortcuts;

  const PixivSearchPicker({
    super.key,
    required this.numericId,
    required this.suggestions,
    required this.onPick,
    this.shortcuts = pixivNumericShortcuts,
  });

  @override
  Widget build(BuildContext context) {
    final id = numericId;
    return ListView(
      children: [
        if (id != null) ...[for (final shortcut in shortcuts) shortcut(context, id), const Divider()],
        for (final tag in suggestions)
          ListTile(
            leading: const Icon(Icons.tag),
            title: Text(tag.name),
            subtitle: tag.translatedName == null ? null : Text(tag.translatedName!),
            onTap: () => onPick(tag),
            onLongPress: () => copyPixivTag(context, tag.name),
          ),
      ],
    );
  }
}
