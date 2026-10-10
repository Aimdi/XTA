import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_download_index.dart';
import 'package:xta/plugins/pixiv/pixiv_download_naming.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_settings_content.dart';
import 'package:xta/settings/settings_view_store.dart';

/// How saved pages are named and foldered, and the list of what was saved.
class PixivDownloadSettings extends StatelessWidget {
  const PixivDownloadSettings({super.key});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(l10n.plugin_pixiv_downloads_section, style: Theme.of(context).textTheme.titleSmall),
        const PixivFileNameSetting(),
        PixivPrefSwitch(
          key: const ValueKey('pixiv-folder-per-artist'),
          pref: optionPluginPixivFolderPerArtist,
          title: l10n.plugin_pixiv_folder_per_artist,
          subtitle: l10n.plugin_pixiv_folder_per_artist_description,
        ),
        PixivPrefSwitch(
          key: const ValueKey('pixiv-folder-r18'),
          pref: optionPluginPixivFolderR18,
          title: l10n.plugin_pixiv_folder_r18,
          subtitle: l10n.plugin_pixiv_folder_r18_description,
        ),
        const PixivDownloadIndexSetting(),
      ],
    );
  }
}

/// A stand-in work for the name preview, labelled in the reader's language.
PixivIllust pixivNameSample(L10n l10n) => PixivIllust(
  id: 104812345,
  title: l10n.plugin_pixiv_file_name_token_title,
  caption: '',
  type: 'illust',
  thumbnailUrl: 'https://i.pximg.net/img-original/img/104812345_p0.png',
  originalUrls: const ['https://i.pximg.net/img-original/img/104812345_p0.png'],
  pageCount: 1,
  userId: 2841234,
  userName: l10n.plugin_pixiv_file_name_token_user_name,
  userAccount: '',
);

/// The file-name template, opened in [PixivFileNameEditor].
class PixivFileNameSetting extends StatefulWidget {
  const PixivFileNameSetting({super.key});

  @override
  State<PixivFileNameSetting> createState() => _PixivFileNameSettingState();
}

class _PixivFileNameSettingState extends State<PixivFileNameSetting> {
  final _revision = SettingsRevisionStore();

  @override
  void dispose() {
    _revision.destroy();
    super.dispose();
  }

  Future<void> _edit(BasePrefService prefs) async {
    final template = await showDialog<String>(
      context: context,
      builder: (_) => PixivFileNameEditor(initial: PixivSaveNaming.of(prefs).template),
    );
    if (template == null) return;
    await prefs.set(optionPluginPixivFileNameTemplate, template);
    _revision.refresh();
  }

  @override
  Widget build(BuildContext context) {
    final prefs = PrefService.of(context, listen: false);
    final l10n = L10n.of(context);
    return ScopedBuilder<SettingsRevisionStore, int>(
      store: _revision,
      onState: (context, _) {
        final naming = PixivSaveNaming.of(prefs);
        return ListTile(
          key: const ValueKey('pixiv-file-name'),
          contentPadding: EdgeInsets.zero,
          title: Text(l10n.plugin_pixiv_file_name),
          subtitle: Text(naming.template),
          trailing: const Icon(Icons.edit_outlined),
          onTap: () => _edit(prefs),
        );
      },
    );
  }
}

String pixivNameTokenLabel(L10n l10n, PixivNameToken token) => switch (token) {
  PixivNameToken.illustId => l10n.plugin_pixiv_file_name_token_illust_id,
  PixivNameToken.part => l10n.plugin_pixiv_file_name_token_part,
  PixivNameToken.title => l10n.plugin_pixiv_file_name_token_title,
  PixivNameToken.userId => l10n.plugin_pixiv_file_name_token_user_id,
  PixivNameToken.userName => l10n.plugin_pixiv_file_name_token_user_name,
};

/// Edits a template with a chip per detail and a live preview; pops the new
/// template, which always carries `{part}`.
class PixivFileNameEditor extends StatefulWidget {
  final String initial;

  const PixivFileNameEditor({super.key, required this.initial});

  @override
  State<PixivFileNameEditor> createState() => _PixivFileNameEditorState();
}

class _PixivFileNameEditorState extends State<PixivFileNameEditor> {
  late final _controller = TextEditingController(text: widget.initial);
  late final _text = SettingsValueStore<String>(widget.initial);

  @override
  void dispose() {
    _controller.dispose();
    _text.destroy();
    super.dispose();
  }

  void _replace(String text, {int? cursor}) {
    _controller.value = TextEditingValue(
      text: text,
      selection: TextSelection.collapsed(offset: cursor ?? text.length),
    );
    _text.setValue(text);
  }

  /// Puts [token] where the cursor is, over any selected text.
  void _insert(String token) {
    final value = _controller.value;
    final selection = value.selection.isValid ? value.selection : TextSelection.collapsed(offset: value.text.length);
    _replace(
      selection.textBefore(value.text) + token + selection.textAfter(value.text),
      cursor: selection.start + token.length,
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return ScopedBuilder<SettingsValueStore<String>, String>(
      store: _text,
      onState: (context, text) => AlertDialog(
        title: Text(l10n.plugin_pixiv_file_name),
        scrollable: true,
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _field(l10n, text),
            const SizedBox(height: 12),
            Wrap(spacing: 8, runSpacing: 8, children: [for (final token in PixivNameToken.values) _chip(l10n, token)]),
            const SizedBox(height: 16),
            Text(
              l10n.plugin_pixiv_file_name_preview(pixivFileName(text, pixivNameSample(l10n), 0, extension: '.png')),
              key: const ValueKey('pixiv-file-name-preview'),
              style: Theme.of(context).textTheme.bodySmall,
            ),
          ],
        ),
        actions: _actions(l10n, text),
      ),
    );
  }

  Widget _field(L10n l10n, String text) => TextField(
    key: const ValueKey('pixiv-file-name-field'),
    controller: _controller,
    autofocus: true,
    inputFormatters: [FilteringTextInputFormatter.deny(RegExp(r'[/\\:*?"<>|\n]'))],
    onChanged: _text.setValue,
    decoration: InputDecoration(
      border: const OutlineInputBorder(),
      helperText: l10n.plugin_pixiv_file_name_help,
      helperMaxLines: 3,
      errorText: pixivTemplateHasPart(text) ? null : l10n.plugin_pixiv_file_name_part_required(pixivPartToken),
      errorMaxLines: 3,
    ),
  );

  Widget _chip(L10n l10n, PixivNameToken token) => ActionChip(
    key: ValueKey('pixiv-file-name-insert-${token.name}'),
    tooltip: token.token,
    label: Text(pixivNameTokenLabel(l10n, token)),
    onPressed: () => _insert(token.token),
  );

  List<Widget> _actions(L10n l10n, String text) => [
    TextButton(onPressed: () => _replace(pixivFileNameTemplateDefault), child: Text(l10n.plugin_pixiv_file_name_reset)),
    TextButton(onPressed: () => Navigator.pop(context), child: Text(l10n.cancel)),
    FilledButton(
      key: const ValueKey('pixiv-file-name-save'),
      onPressed: pixivTemplateHasPart(text) ? () => Navigator.pop(context, text.trim()) : null,
      child: Text(l10n.save),
    ),
  ];
}

/// How many saved pages are remembered, with a way to forget them.
class PixivDownloadIndexSetting extends StatelessWidget {
  const PixivDownloadIndexSetting({super.key});

  Future<void> _forget(BuildContext context, PixivDownloadIndex index) async {
    final l10n = L10n.of(context);
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        content: Text(l10n.plugin_pixiv_download_index_forget_question),
        actions: [
          TextButton(onPressed: () => Navigator.pop(dialogContext, false), child: Text(l10n.cancel)),
          FilledButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            child: Text(l10n.plugin_pixiv_download_index_forget),
          ),
        ],
      ),
    );
    if (confirmed == true) await index.clear();
  }

  @override
  Widget build(BuildContext context) {
    final index = PixivDownloadIndex.maybeOf(context);
    if (index == null) return const SizedBox.shrink();
    final l10n = L10n.of(context);
    return ScopedBuilder<PixivDownloadIndex, Set<String>>(
      store: index,
      onState: (context, saved) => ListTile(
        key: const ValueKey('pixiv-download-index'),
        contentPadding: EdgeInsets.zero,
        title: Text(l10n.plugin_pixiv_download_index),
        subtitle: Text(
          '${l10n.plugin_pixiv_download_index_description}\n${l10n.plugin_pixiv_download_index_count(saved.length)}',
        ),
        isThreeLine: true,
        trailing: TextButton(
          key: const ValueKey('pixiv-download-index-forget'),
          onPressed: saved.isEmpty ? null : () => _forget(context, index),
          child: Text(l10n.plugin_pixiv_download_index_forget),
        ),
      ),
    );
  }
}
