import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_confirm.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/plugin_view_store.dart';
import 'package:xta/ui/errors.dart';

/// The mute page on its own, as the More pane and a muted work's notice open it.
class PixivMuteScreen extends StatelessWidget {
  const PixivMuteScreen({super.key});

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: Text(L10n.of(context).plugin_pixiv_mute_settings)),
    body: ListView(padding: const EdgeInsets.all(16), children: const [PixivMuteSettings()]),
  );
}

/// One kind of mute, worded for the list.
typedef _MuteChip = ({String label, String? copy, Future<void> Function() unmute});

/// Everything the reader muted, grouped by kind. Tags can be added by typing;
/// tapping an entry asks before unmuting it, and a long press copies a tag.
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
          if (state.isEmpty) ...[const SizedBox(height: 8), Text(l10n.plugin_pixiv_muted_empty)],
          _MuteGroup(
            title: l10n.plugin_pixiv_mute_tags,
            icon: Icons.label_off_outlined,
            leading: const PixivMuteTagField(),
            chips: [
              for (final tag in state.tags.toList()..sort())
                (label: pixivMutePattern(tag) == null ? '#$tag' : tag, copy: tag, unmute: () => store.unmuteTag(tag)),
            ],
          ),
          ..._idGroups(l10n, store, state),
        ],
      ),
    );
  }

  List<Widget> _idGroups(L10n l10n, PixivMuteStore store, PixivMuteState state) => [
    _MuteGroup(
      title: l10n.plugin_pixiv_mute_users,
      icon: Icons.person_off_outlined,
      chips: [
        for (final id in state.authorIds.toList()..sort())
          (label: _authorLabel(state, id), copy: null, unmute: () => store.unmuteAuthor(id)),
      ],
    ),
    _idGroup(l10n.plugin_pixiv_mute_works, Icons.hide_image_outlined, state.illustIds, store.unmuteIllust),
    _idGroup(l10n.plugin_pixiv_mute_comments, Icons.comments_disabled_outlined, state.commentIds, store.unmuteComment),
    _idGroup(l10n.plugin_pixiv_mute_novels, Icons.menu_book_outlined, state.novelIds, store.unmuteNovel),
  ];

  String _authorLabel(PixivMuteState state, int id) => switch (state.authorNames[id]) {
    final name? when name.isNotEmpty => '$name · $id',
    _ => '$id',
  };

  Widget _idGroup(String title, IconData icon, Set<int> ids, Future<void> Function(int id) unmute) => _MuteGroup(
    title: title,
    icon: icon,
    chips: [for (final id in ids.toList()..sort()) (label: '$id', copy: null, unmute: () => unmute(id))],
  );
}

/// A titled group of muted entries; hidden while empty unless it has a [leading] field.
class _MuteGroup extends StatelessWidget {
  final String title;
  final IconData icon;
  final Widget? leading;
  final List<_MuteChip> chips;

  const _MuteGroup({required this.title, required this.icon, required this.chips, this.leading});

  @override
  Widget build(BuildContext context) {
    if (chips.isEmpty && leading == null) return const SizedBox.shrink();
    return Padding(
      padding: const EdgeInsets.only(top: 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title, style: Theme.of(context).textTheme.labelLarge),
          const SizedBox(height: 8),
          ?leading,
          if (leading != null && chips.isNotEmpty) const SizedBox(height: 8),
          Wrap(spacing: 8, runSpacing: 8, children: [for (final chip in chips) _chip(context, chip)]),
        ],
      ),
    );
  }

  Widget _chip(BuildContext context, _MuteChip chip) {
    final l10n = L10n.of(context);
    return GestureDetector(
      onLongPress: chip.copy == null ? null : () => _copy(context, chip.copy!),
      child: InputChip(
        avatar: Icon(icon, size: 18),
        label: Text(chip.label),
        onPressed: () => _confirmUnmute(context, chip),
        onDeleted: chip.unmute,
        deleteButtonTooltipMessage: l10n.plugin_pixiv_unmute,
      ),
    );
  }

  Future<void> _copy(BuildContext context, String text) async {
    final message = L10n.of(context).plugin_pixiv_copied;
    await Clipboard.setData(ClipboardData(text: text));
    if (context.mounted) showSnackBar(context, icon: '📋', message: message);
  }

  Future<void> _confirmUnmute(BuildContext context, _MuteChip chip) async {
    final l10n = L10n.of(context);
    if (await confirmPixivAction(context, l10n.plugin_pixiv_unmute_question(chip.label), l10n.plugin_pixiv_unmute)) {
      await chip.unmute();
    }
  }
}

/// Mutes a typed tag name or `r'pattern'`, refusing a pattern that does not compile.
class PixivMuteTagField extends StatefulWidget {
  const PixivMuteTagField({super.key});

  @override
  State<PixivMuteTagField> createState() => _PixivMuteTagFieldState();
}

class _PixivMuteTagFieldState extends State<PixivMuteTagField> {
  final _text = TextEditingController();
  final _invalid = PluginViewStore<bool>(false);

  @override
  void dispose() {
    _text.dispose();
    _invalid.destroy();
    super.dispose();
  }

  Future<void> _add() async {
    final entry = _text.text.trim();
    if (entry.isEmpty) return;
    final added = await context.read<PixivMuteStore>().muteTag(entry);
    _invalid.select(!added);
    if (added) _text.clear();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return ScopedBuilder<PluginViewStore<bool>, bool>(
      store: _invalid,
      onState: (context, invalid) => TextField(
        key: const ValueKey('pixiv-mute-tag-field'),
        controller: _text,
        autocorrect: false,
        textInputAction: TextInputAction.done,
        onChanged: (_) => _invalid.select(false),
        onSubmitted: (_) => _add(),
        decoration: InputDecoration(
          hintText: l10n.plugin_pixiv_mute_tag_hint,
          helperText: l10n.plugin_pixiv_mute_tag_help,
          helperMaxLines: 20,
          errorText: invalid ? l10n.plugin_pixiv_mute_tag_invalid : null,
          border: const OutlineInputBorder(),
          suffixIcon: IconButton(tooltip: l10n.plugin_pixiv_mute_tag_add, icon: const Icon(Icons.add), onPressed: _add),
        ),
      ),
    );
  }
}
