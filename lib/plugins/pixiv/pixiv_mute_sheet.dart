import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_confirm.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';

/// Something the reader can mute, worded as the action it takes.
typedef PixivMuteChoice = ({IconData icon, String label, Future<void> Function(PixivMuteStore store) mute});

/// Asks before muting; true once [choice] was muted.
Future<bool> confirmPixivMute(BuildContext context, PixivMuteChoice choice) async {
  final store = context.read<PixivMuteStore>();
  if (!await confirmPixivAction(context, choice.label, choice.label) || !context.mounted) return false;
  await choice.mute(store);
  return true;
}

/// What a work offers to mute: its author, the work itself and each tag.
List<PixivMuteChoice> pixivMuteChoices(L10n l10n, PixivIllust illust) => [
  (
    icon: Icons.person_off_outlined,
    label: l10n.plugin_pixiv_mute_author,
    mute: (store) => store.muteAuthor(illust.userId),
  ),
  (icon: Icons.hide_image_outlined, label: l10n.plugin_pixiv_mute_illust, mute: (store) => store.muteIllust(illust.id)),
  for (final tag in illust.tags) pixivTagMuteChoice(l10n, tag),
];

PixivMuteChoice pixivTagMuteChoice(L10n l10n, PixivTag tag) => (
  icon: Icons.label_off_outlined,
  label: l10n.plugin_pixiv_mute_tag(tag.displayName),
  mute: (store) => store.muteTag(tag.name),
);

/// Offers [pixivMuteChoices] and, once one is confirmed, leaves the work's
/// screen, since whatever was muted now hides it.
Future<void> showPixivMuteSheet(BuildContext context, PixivIllust illust) async {
  final l10n = L10n.of(context);
  final navigator = Navigator.of(context);
  final choices = pixivMuteChoices(l10n, illust);
  final chosen = await showModalBottomSheet<PixivMuteChoice>(
    context: context,
    builder: (sheetContext) => SafeArea(
      child: ListView(
        shrinkWrap: true,
        children: [
          for (final choice in choices)
            ListTile(
              leading: Icon(choice.icon),
              title: Text(choice.label),
              onTap: () => Navigator.pop(sheetContext, choice),
            ),
        ],
      ),
    ),
  );
  if (chosen == null || !context.mounted) return;
  if (await confirmPixivMute(context, chosen) && context.mounted) navigator.pop();
}
