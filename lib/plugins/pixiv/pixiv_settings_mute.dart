import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';

/// Everything the reader muted, each one a chip that unmutes.
class PixivMuteSettings extends StatefulWidget {
  const PixivMuteSettings({super.key});

  @override
  State<PixivMuteSettings> createState() => _PixivMuteSettingsState();
}

class _PixivMuteSettingsState extends State<PixivMuteSettings> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) context.read<PixivMuteStore>().load();
    });
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final store = context.read<PixivMuteStore>();
    return ScopedBuilder<PixivMuteStore, PixivMuteState>.transition(
      store: store,
      onState: (context, state) => Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(l10n.plugin_pixiv_muted_section, style: Theme.of(context).textTheme.titleSmall),
          const SizedBox(height: 8),
          if (state.isEmpty)
            Text(l10n.plugin_pixiv_muted_empty)
          else
            Wrap(spacing: 8, runSpacing: 8, children: _chips(context, store, state)),
        ],
      ),
    );
  }

  List<Widget> _chips(BuildContext context, PixivMuteStore store, PixivMuteState state) => [
    for (final id in state.authorIds) _chip(context, Icons.person_off_outlined, '$id', () => store.unmuteAuthor(id)),
    for (final tag in state.tags) _chip(context, Icons.label_off_outlined, '#$tag', () => store.unmuteTag(tag)),
    for (final id in state.illustIds) _chip(context, Icons.hide_image_outlined, '$id', () => store.unmuteIllust(id)),
    for (final id in state.commentIds)
      _chip(context, Icons.comments_disabled_outlined, '$id', () => store.unmuteComment(id)),
    for (final id in state.novelIds) _chip(context, Icons.menu_book_outlined, '$id', () => store.unmuteNovel(id)),
  ];

  Widget _chip(BuildContext context, IconData icon, String label, VoidCallback onDeleted) => InputChip(
    avatar: Icon(icon, size: 18),
    label: Text(label),
    onDeleted: onDeleted,
    deleteButtonTooltipMessage: L10n.of(context).plugin_pixiv_unmute,
  );
}
