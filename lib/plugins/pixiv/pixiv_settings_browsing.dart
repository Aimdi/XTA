import 'package:android_intent_plus/android_intent.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_copy_info.dart';
import 'package:xta/plugins/pixiv/pixiv_history_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_settings_content.dart';
import 'package:xta/plugins/pixiv/pixiv_view_state.dart';
import 'package:xta/plugins/plugin_view_store.dart';

String pixivStartSectionLabel(L10n l10n, String section) => switch (section) {
  'ranking' => l10n.plugin_pixiv_tab_ranking,
  'favorites' => l10n.plugin_pixiv_tab_favorites,
  'search' => l10n.search,
  _ => l10n.home,
};

/// Android's "Open by default" page for XTA, where the reader lets it open
/// pixiv.net and pixiv.me links; the app's info page on systems without it.
Future<void> openPixivLinkSettings() async {
  final data = 'package:${(await PackageInfo.fromPlatform()).packageName}';
  const openByDefault = 'android.settings.APP_OPEN_BY_DEFAULT_SETTINGS';
  try {
    final intent = AndroidIntent(action: openByDefault, data: data);
    if (await intent.canResolveActivity() == true) {
      await intent.launch();
      return;
    }
    await AndroidIntent(action: 'android.settings.APPLICATION_DETAILS_SETTINGS', data: data).launch();
  } on PlatformException {
    // Nothing on this device opens either page; the tile simply does nothing.
  }
}

/// How the plugin opens and remembers: the start section, the viewing
/// history, the Copy info template and, on Android, opening Pixiv links here.
class PixivBrowsingSettings extends StatelessWidget {
  const PixivBrowsingSettings({super.key});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const PixivStartSectionSetting(),
        ListTile(
          contentPadding: EdgeInsets.zero,
          leading: const Icon(Icons.history),
          title: Text(l10n.plugin_pixiv_history),
          onTap: () => Navigator.push(context, MaterialPageRoute<void>(builder: (_) => const PixivHistoryScreen())),
        ),
        PixivPrefSwitch(
          pref: optionPluginPixivHistoryPaused,
          title: l10n.plugin_pixiv_history_pause,
          subtitle: l10n.plugin_pixiv_history_pause_description,
        ),
        ListTile(
          contentPadding: EdgeInsets.zero,
          leading: const Icon(Icons.content_copy_outlined),
          title: Text(l10n.plugin_pixiv_copy_template),
          onTap: () =>
              Navigator.push(context, MaterialPageRoute<void>(builder: (_) => const PixivCopyTemplateScreen())),
        ),
        if (defaultTargetPlatform == TargetPlatform.android)
          ListTile(
            key: const ValueKey('pixiv-open-links'),
            contentPadding: EdgeInsets.zero,
            leading: const Icon(Icons.link),
            title: Text(l10n.plugin_pixiv_open_links_in_app),
            subtitle: Text(l10n.plugin_pixiv_open_links_in_app_description),
            onTap: openPixivLinkSettings,
          ),
      ],
    );
  }
}

/// Which section the Pixiv screen opens on.
class PixivStartSectionSetting extends StatefulWidget {
  const PixivStartSectionSetting({super.key});

  @override
  State<PixivStartSectionSetting> createState() => _PixivStartSectionSettingState();
}

class _PixivStartSectionSettingState extends State<PixivStartSectionSetting> {
  late final PluginViewStore<String> _section;

  BasePrefService get _prefs => PrefService.of(context, listen: false);

  @override
  void initState() {
    super.initState();
    _section = PluginViewStore(
      pixivStartSections[pixivStartSectionIndex(_prefs.get<String>(optionPluginPixivStartSection))],
    );
  }

  @override
  void dispose() {
    _section.destroy();
    super.dispose();
  }

  Future<void> _pick() async {
    final l10n = L10n.of(context);
    final picked = await showDialog<String>(
      context: context,
      builder: (dialogContext) => SimpleDialog(
        title: Text(l10n.plugin_pixiv_start_section),
        children: [
          for (final section in pixivStartSections)
            SimpleDialogOption(
              onPressed: () => Navigator.pop(dialogContext, section),
              padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 14),
              child: Text(pixivStartSectionLabel(l10n, section)),
            ),
        ],
      ),
    );
    if (picked == null) return;
    await _prefs.set(optionPluginPixivStartSection, picked);
    _section.select(picked);
  }

  @override
  Widget build(BuildContext context) => ScopedBuilder<PluginViewStore<String>, String>(
    store: _section,
    onState: (context, section) => ListTile(
      key: const ValueKey('pixiv-start-section'),
      contentPadding: EdgeInsets.zero,
      leading: const Icon(Icons.start),
      title: Text(L10n.of(context).plugin_pixiv_start_section),
      subtitle: Text(pixivStartSectionLabel(L10n.of(context), section)),
      onTap: _pick,
    ),
  );
}
