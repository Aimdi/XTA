import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_detail_split.dart';
import 'package:xta/plugins/pixiv/pixiv_grid_columns.dart';
import 'package:xta/plugins/pixiv/pixiv_image_source.dart';
import 'package:xta/plugins/pixiv/pixiv_quality.dart';
import 'package:xta/plugins/pixiv/pixiv_settings_content.dart';
import 'package:xta/plugins/plugin_view_store.dart';
import 'package:xta/settings/settings_view_store.dart';

/// How works look: the image server, image sizes, grid columns, the detail's layout,
/// swiping between works and the AI badge.
class PixivViewingSettings extends StatelessWidget {
  const PixivViewingSettings({super.key});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(l10n.plugin_pixiv_viewing_section, style: Theme.of(context).textTheme.titleSmall),
        const SizedBox(height: 8),
        const PixivImageHostSetting(),
        for (final slot in PixivQualitySlot.values)
          PixivPrefChoice<String>(
            key: ValueKey('pixiv-quality-${slot.name}'),
            pref: slot.pref,
            title: _qualityTitle(l10n, slot),
            fallback: slot.fallback.name,
            options: [for (final quality in slot.choices) (quality.name, pixivQualityLabel(l10n, quality))],
          ),
        for (final orientation in Orientation.values)
          PixivPrefChoice<int>(
            key: ValueKey('pixiv-columns-${orientation.name}'),
            pref: pixivGridColumnsPref(orientation),
            title: orientation == Orientation.portrait
                ? l10n.plugin_pixiv_columns_portrait
                : l10n.plugin_pixiv_columns_landscape,
            fallback: pixivGridColumnsAuto,
            options: [
              (pixivGridColumnsAuto, l10n.plugin_pixiv_columns_auto),
              for (final count in pixivGridColumnChoices) (count, '$count'),
            ],
          ),
        PixivPrefChoice<String>(
          key: const ValueKey('pixiv-detail-layout'),
          pref: optionPluginPixivDetailLayout,
          title: l10n.plugin_pixiv_detail_layout,
          fallback: PixivDetailLayout.auto.name,
          options: [for (final layout in PixivDetailLayout.values) (layout.name, _layoutLabel(l10n, layout))],
        ),
        PixivPrefSwitch(
          key: const ValueKey('pixiv-swipe-works'),
          pref: optionPluginPixivSwipeBetweenWorks,
          title: l10n.plugin_pixiv_swipe_works,
          subtitle: l10n.plugin_pixiv_swipe_works_description,
        ),
        PixivPrefSwitch(
          key: const ValueKey('pixiv-ai-badge'),
          pref: optionPluginPixivAiBadge,
          title: l10n.plugin_pixiv_ai_badge,
          subtitle: l10n.plugin_pixiv_ai_badge_description,
          defaultValue: true,
        ),
      ],
    );
  }

  String _qualityTitle(L10n l10n, PixivQualitySlot slot) => switch (slot) {
    PixivQualitySlot.feed => l10n.plugin_pixiv_quality_feed,
    PixivQualitySlot.detail => l10n.plugin_pixiv_quality_detail,
    PixivQualitySlot.reader => l10n.plugin_pixiv_quality_reader,
  };

  String _layoutLabel(L10n l10n, PixivDetailLayout layout) => switch (layout) {
    PixivDetailLayout.auto => l10n.plugin_pixiv_detail_layout_auto,
    PixivDetailLayout.vertical => l10n.plugin_pixiv_detail_layout_vertical,
    PixivDetailLayout.split => l10n.plugin_pixiv_detail_layout_split,
  };
}

/// Where artwork images load from, shown as the host itself and changed in a dialog.
class PixivImageHostSetting extends StatefulWidget {
  const PixivImageHostSetting({super.key});

  @override
  State<PixivImageHostSetting> createState() => _PixivImageHostSettingState();
}

class _PixivImageHostSettingState extends State<PixivImageHostSetting> {
  final _revision = SettingsRevisionStore();

  @override
  void dispose() {
    _revision.destroy();
    super.dispose();
  }

  Future<void> _edit(BasePrefService prefs) async {
    final host = await showDialog<String>(
      context: context,
      builder: (_) => PixivImageHostDialog(initial: pixivImageHostSetting(prefs)),
    );
    if (host == null) return;
    await prefs.set(optionPluginPixivImageHost, host);
    _revision.refresh();
  }

  @override
  Widget build(BuildContext context) {
    final prefs = PrefService.of(context, listen: false);
    return ScopedBuilder<SettingsRevisionStore, int>(
      store: _revision,
      onState: (context, _) => ListTile(
        key: const ValueKey('pixiv-image-host'),
        contentPadding: EdgeInsets.zero,
        title: Text(L10n.of(context).plugin_pixiv_image_host),
        subtitle: Text(pixivImageHostSetting(prefs)),
        trailing: const Icon(Icons.edit_outlined),
        onTap: () => _edit(prefs),
      ),
    );
  }
}

/// Pixiv, the mirror, or a server of the reader's own, which must be a usable address.
/// Closes with the host to keep, or null when cancelled.
class PixivImageHostDialog extends StatefulWidget {
  final String initial;

  const PixivImageHostDialog({super.key, required this.initial});

  @override
  State<PixivImageHostDialog> createState() => _PixivImageHostDialogState();
}

class _PixivImageHostDialogState extends State<PixivImageHostDialog> {
  late final _choice = PluginViewStore<PixivImageHostChoice>(pixivImageHostChoice(widget.initial));
  late final _custom = TextEditingController(
    text: _choice.state == PixivImageHostChoice.custom ? widget.initial.trim() : '',
  );

  @override
  void dispose() {
    _choice.destroy();
    _custom.dispose();
    super.dispose();
  }

  /// The host to keep, or null while the custom address is unusable.
  String? _host(PixivImageHostChoice choice, String custom) => switch (choice) {
    PixivImageHostChoice.pixiv => pixivImageHost,
    PixivImageHostChoice.mirror => pixivMirrorHost,
    PixivImageHostChoice.custom => parsePixivImageHost(custom) == null ? null : custom.trim(),
  };

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return ScopedBuilder<PluginViewStore<PixivImageHostChoice>, PixivImageHostChoice>(
      store: _choice,
      onState: (context, choice) => ValueListenableBuilder<TextEditingValue>(
        valueListenable: _custom,
        builder: (context, custom, _) {
          final host = _host(choice, custom.text);
          return AlertDialog(
            scrollable: true,
            title: Text(l10n.plugin_pixiv_image_host),
            content: _choices(l10n, choice, custom.text),
            actions: [
              TextButton(
                key: const ValueKey('pixiv-image-host-reset'),
                onPressed: () => Navigator.pop(context, pixivImageHost),
                child: Text(l10n.plugin_pixiv_image_host_reset),
              ),
              TextButton(onPressed: () => Navigator.pop(context), child: Text(l10n.cancel)),
              FilledButton(
                key: const ValueKey('pixiv-image-host-save'),
                onPressed: host == null ? null : () => Navigator.pop(context, host),
                child: Text(l10n.save),
              ),
            ],
          );
        },
      ),
    );
  }

  Widget _choices(L10n l10n, PixivImageHostChoice choice, String custom) {
    final editing = choice == PixivImageHostChoice.custom;
    return RadioGroup<PixivImageHostChoice>(
      groupValue: choice,
      onChanged: (value) {
        if (value != null) _choice.select(value);
      },
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(l10n.plugin_pixiv_image_host_description),
          RadioListTile(
            value: PixivImageHostChoice.pixiv,
            title: Text(l10n.plugin_pixiv_image_host_pixiv),
            subtitle: const Text(pixivImageHost),
          ),
          RadioListTile(
            value: PixivImageHostChoice.mirror,
            title: Text(l10n.plugin_pixiv_image_host_mirror),
            subtitle: const Text(pixivMirrorHost),
          ),
          RadioListTile(value: PixivImageHostChoice.custom, title: Text(l10n.plugin_pixiv_image_host_custom)),
          TextField(
            key: const ValueKey('pixiv-image-host-field'),
            controller: _custom,
            enabled: editing,
            keyboardType: TextInputType.url,
            autocorrect: false,
            decoration: InputDecoration(
              labelText: l10n.plugin_pixiv_image_host_address,
              helperText: l10n.plugin_pixiv_image_host_example,
              helperMaxLines: 2,
              errorMaxLines: 3,
              errorText: editing && custom.trim().isNotEmpty && parsePixivImageHost(custom) == null
                  ? l10n.plugin_pixiv_image_host_invalid
                  : null,
            ),
          ),
        ],
      ),
    );
  }
}
