import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_favorite_tags_store.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_sheet.dart';
import 'package:xta/plugins/pixiv/pixiv_link_open.dart';
import 'package:xta/plugins/pixiv/pixiv_search_filters.dart';

/// A work's or a novel's tags, each searching Pixiv for itself among [kind];
/// a long press offers to mute, pin or copy it.
class PixivDetailTags extends StatelessWidget {
  final List<PixivTag> tags;
  final PixivSearchKind kind;

  const PixivDetailTags({super.key, required this.tags, this.kind = PixivSearchKind.works});

  @override
  Widget build(BuildContext context) {
    // Each chip keeps its padded 48 dp target, so the rows need no spacing of
    // their own; the long press is merged into the chip's labelled node.
    return Wrap(
      spacing: 6,
      children: [
        for (final tag in tags)
          MergeSemantics(
            child: GestureDetector(
              key: ValueKey('pixiv-tag-${tag.name}'),
              onLongPress: () => showPixivTagSheet(context, tag),
              child: ActionChip(
                label: _label(context, tag),
                onPressed: () => openPixivTagSearch(context, tag.name, kind: kind),
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
/// favourite tags, or copy it. A confirmed mute leaves the work's screen, as
/// the Mute sheet does, since the muted tag now hides the work.
Future<void> showPixivTagSheet(BuildContext context, PixivTag tag) async {
  final favorites = context.read<PixivFavoriteTagsStore>();
  final navigator = Navigator.of(context);
  final favorite = favorites.contains(tag.name);
  final action = await showModalBottomSheet<_PixivTagAction>(
    context: context,
    showDragHandle: true,
    builder: (_) => _PixivTagSheet(tag: tag, favorite: favorite),
  );
  if (action == null || !context.mounted) return;
  switch (action) {
    case _PixivTagAction.mute:
      final muted = await confirmPixivMute(context, pixivTagMuteChoice(L10n.of(context), tag));
      if (muted && context.mounted) navigator.pop();
    case _PixivTagAction.favorite:
      await _toggleFavorite(context, favorites, tag, favorite: favorite);
    case _PixivTagAction.copy:
      await copyPixivTag(context, tag.name);
  }
}

/// The tag with its translation over the actions it offers.
class _PixivTagSheet extends StatelessWidget {
  final PixivTag tag;
  final bool favorite;

  const _PixivTagSheet({required this.tag, required this.favorite});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return SafeArea(
      child: ListView(
        shrinkWrap: true,
        children: [
          ListTile(title: Text.rich(pixivTagSpan(context, tag))),
          const Divider(height: 1),
          _entry(context, _PixivTagAction.mute, Icons.label_off_outlined, l10n.plugin_pixiv_mute_tag(tag.displayName)),
          _entry(
            context,
            _PixivTagAction.favorite,
            favorite ? Icons.star : Icons.star_border,
            favorite ? l10n.plugin_pixiv_search_favorite_remove : l10n.plugin_pixiv_search_favorite_add,
          ),
          _entry(context, _PixivTagAction.copy, Icons.copy_outlined, l10n.plugin_pixiv_search_tag_copy),
        ],
      ),
    );
  }

  Widget _entry(BuildContext context, _PixivTagAction action, IconData icon, String label) => ListTile(
    key: ValueKey('pixiv-tag-action-${action.name}'),
    leading: Icon(icon),
    title: Text(label),
    onTap: () => Navigator.pop(context, action),
  );
}

Future<void> _toggleFavorite(
  BuildContext context,
  PixivFavoriteTagsStore favorites,
  PixivTag tag, {
  required bool favorite,
}) async {
  final l10n = L10n.of(context);
  final messenger = ScaffoldMessenger.of(context);
  await (favorite ? favorites.remove(tag.name) : favorites.add(tag));
  final said = favorite ? l10n.plugin_pixiv_search_favorite_removed : l10n.plugin_pixiv_search_favorite_added;
  messenger.showSnackBar(SnackBar(content: Text(said)));
}

Future<void> copyPixivTag(BuildContext context, String name) async {
  final messenger = ScaffoldMessenger.of(context);
  final copied = L10n.of(context).plugin_pixiv_search_tag_copied;
  await Clipboard.setData(ClipboardData(text: name));
  messenger.showSnackBar(SnackBar(content: Text(copied)));
}
