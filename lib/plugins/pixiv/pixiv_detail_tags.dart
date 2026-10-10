import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:pref/pref.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_favorite_tags_store.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_sheet.dart';
import 'package:xta/plugins/pixiv/pixiv_search_screen.dart';

/// A work's tags, each searching Pixiv for itself; a long press offers to
/// mute, pin or copy it.
class PixivDetailTags extends StatelessWidget {
  final List<PixivTag> tags;

  const PixivDetailTags({super.key, required this.tags});

  @override
  Widget build(BuildContext context) {
    return Wrap(
      spacing: 6,
      runSpacing: 4,
      children: [
        for (final tag in tags)
          GestureDetector(
            key: ValueKey('pixiv-tag-${tag.name}'),
            onLongPress: () => showPixivTagSheet(context, tag),
            child: ActionChip(
              label: _label(context, tag),
              visualDensity: VisualDensity.compact,
              materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
              onPressed: () => Navigator.push(
                context,
                MaterialPageRoute<void>(builder: (_) => PixivSearchScreen(initialQuery: tag.name)),
              ),
            ),
          ),
      ],
    );
  }

  Widget _label(BuildContext context, PixivTag tag) => Text.rich(pixivTagSpan(context, tag));
}

/// The tag as Pixiv spells it, with its translation beside it when there is one.
TextSpan pixivTagSpan(BuildContext context, PixivTag tag) {
  final translated = tag.translatedName?.trim() ?? '';
  if (translated.isEmpty || translated == tag.name) return TextSpan(text: '#${tag.name}');
  final muted = Theme.of(context).colorScheme.onSurfaceVariant;
  return TextSpan(
    children: [
      TextSpan(text: '#${tag.name}'),
      TextSpan(
        text: '  $translated',
        style: TextStyle(color: muted),
      ),
    ],
  );
}

enum _PixivTagAction { mute, favorite, copy }

/// What a long-pressed tag offers: mute it, pin it to (or drop it from) the
/// favourite tags, or copy it.
Future<void> showPixivTagSheet(BuildContext context, PixivTag tag) async {
  final l10n = L10n.of(context);
  final favorite = readPixivFavoriteTags(
    PrefService.of(context, listen: false),
  ).any((kept) => pixivSameTag(kept.name, tag.name));
  final action = await showModalBottomSheet<_PixivTagAction>(
    context: context,
    showDragHandle: true,
    builder: (sheetContext) => SafeArea(
      child: ListView(
        shrinkWrap: true,
        children: [
          ListTile(title: Text.rich(pixivTagSpan(sheetContext, tag))),
          const Divider(height: 1),
          _sheetEntry(
            sheetContext,
            _PixivTagAction.mute,
            Icons.label_off_outlined,
            l10n.plugin_pixiv_mute_tag(tag.displayName),
          ),
          _sheetEntry(
            sheetContext,
            _PixivTagAction.favorite,
            favorite ? Icons.star : Icons.star_border,
            favorite ? l10n.plugin_pixiv_search_favorite_remove : l10n.plugin_pixiv_search_favorite_add,
          ),
          _sheetEntry(sheetContext, _PixivTagAction.copy, Icons.copy_outlined, l10n.plugin_pixiv_search_tag_copy),
        ],
      ),
    ),
  );
  if (action == null || !context.mounted) return;
  switch (action) {
    case _PixivTagAction.mute:
      await confirmPixivMute(context, pixivTagMuteChoice(l10n, tag));
    case _PixivTagAction.favorite:
      await _toggleFavorite(context, tag, favorite: favorite);
    case _PixivTagAction.copy:
      await copyPixivTag(context, tag.name);
  }
}

Widget _sheetEntry(BuildContext context, _PixivTagAction action, IconData icon, String label) => ListTile(
  key: ValueKey('pixiv-tag-action-${action.name}'),
  leading: Icon(icon),
  title: Text(label),
  onTap: () => Navigator.pop(context, action),
);

Future<void> _toggleFavorite(BuildContext context, PixivTag tag, {required bool favorite}) async {
  final l10n = L10n.of(context);
  final messenger = ScaffoldMessenger.of(context);
  final store = PixivFavoriteTagsStore(PrefService.of(context, listen: false));
  try {
    await (favorite ? store.remove(tag.name) : store.add(tag));
  } finally {
    await store.destroy();
  }
  final said = favorite ? l10n.plugin_pixiv_search_favorite_removed : l10n.plugin_pixiv_search_favorite_added;
  messenger.showSnackBar(SnackBar(content: Text(said)));
}

Future<void> copyPixivTag(BuildContext context, String name) async {
  final messenger = ScaffoldMessenger.of(context);
  final copied = L10n.of(context).plugin_pixiv_search_tag_copied;
  await Clipboard.setData(ClipboardData(text: name));
  messenger.showSnackBar(SnackBar(content: Text(copied)));
}
