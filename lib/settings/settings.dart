import 'dart:async';
import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/settings/_about.dart';
import 'package:xta/settings/_ai.dart';
import 'package:xta/settings/_accessibility.dart';
import 'package:xta/settings/_account.dart';
import 'package:xta/settings/_data.dart';
import 'package:xta/settings/_general.dart';
import 'package:xta/settings/_home.dart';
import 'package:xta/settings/_media.dart';
import 'package:xta/settings/_plugin_store.dart';
import 'package:xta/settings/_posts.dart';
import 'package:xta/settings/_theme.dart';
import 'package:xta/settings/diagnostics_screen.dart';
import 'package:xta/settings/settings_chrome.dart';
import 'package:xta/speech/tts_settings.dart';
import 'package:xta/ui/x_controls.dart';
import 'package:package_info_plus/package_info_plus.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

/// One navigable settings section: its icon, its title, a plain description of
/// what it holds, and where it goes.
class _SettingsEntry {
  final IconData icon;
  final String title;
  final String description;
  final WidgetBuilder builder;

  const _SettingsEntry({
    required this.icon,
    required this.title,
    required this.description,
    required this.builder,
  });

  bool matches(String query) =>
      title.toLowerCase().contains(query) ||
      description.toLowerCase().contains(query);
}

class _SettingsScreenState extends State<SettingsScreen> {
  PackageInfo _packageInfo = PackageInfo(
    appName: '',
    packageName: '',
    version: '',
    buildNumber: '',
  );
  String _query = '';
  late final TextEditingController _searchController;

  @override
  void initState() {
    super.initState();
    _searchController = TextEditingController();

    Future.microtask(() async {
      try {
        var packageInfo = await PackageInfo.fromPlatform();
        if (mounted) setState(() => _packageInfo = packageInfo);
      } catch (_) {
        // Tests and headless runs have no platform channel for the version.
      }
    });
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  List<_SettingsEntry> _everydayEntries(BuildContext context, Key key) {
    final l10n = L10n.of(context);
    return [
      _SettingsEntry(
        icon: Icons.miscellaneous_services,
        title: l10n.general,
        description: l10n.settings_general_hint,
        builder: (context) => const SettingsGeneralFragment(),
      ),
      _SettingsEntry(
        icon: Icons.article,
        title: l10n.tweets,
        description: l10n.settings_posts_hint,
        builder: (context) => const SettingsPostsFragment(),
      ),
      _SettingsEntry(
        icon: Icons.perm_media,
        title: l10n.media,
        description: l10n.settings_media_hint,
        builder: (context) => const SettingsMediaFragment(),
      ),
      _SettingsEntry(
        icon: Icons.account_circle,
        title: l10n.account,
        description: l10n.settings_account_hint,
        builder: (context) => SettingsAccountFragment(key: key),
      ),
      _SettingsEntry(
        icon: Icons.home,
        title: l10n.home,
        description: l10n.settings_home_hint,
        builder: (context) => const SettingsHomeFragment(),
      ),
      _SettingsEntry(
        icon: Icons.palette,
        title: l10n.theme,
        description: l10n.settings_theme_hint,
        builder: (context) => const SettingsThemeFragment(),
      ),
      _SettingsEntry(
        icon: Icons.settings_accessibility,
        title: l10n.accessibility,
        description: l10n.settings_accessibility_hint,
        builder: (context) => const SettingsAccessibilityFragment(),
      ),
      _SettingsEntry(
        icon: Icons.record_voice_over_outlined,
        title: l10n.settings_speech,
        description: l10n.settings_speech_description,
        builder: (context) => const TtsSettingsScreen(),
      ),
      _SettingsEntry(
        icon: Icons.extension_outlined,
        title: l10n.plugin_store,
        description: l10n.plugin_store_description,
        builder: (context) => const SettingsPluginStoreFragment(),
      ),
    ];
  }

  _SettingsEntry _dataEntry(BuildContext context) {
    final l10n = L10n.of(context);
    return _SettingsEntry(
      icon: Icons.import_export,
      title: l10n.data,
      description: l10n.settings_data_hint,
      builder: (context) => SettingsPageScaffold(
        title: L10n.of(context).data,
        body: SettingsList(children: const [SettingsDataFragment()]),
      ),
    );
  }

  List<_SettingsEntry> _advancedEntries(BuildContext context) {
    final l10n = L10n.of(context);
    return [
      _SettingsEntry(
        icon: Icons.auto_awesome_outlined,
        title: l10n.ai_provider,
        description: l10n.ai_provider_description,
        builder: (context) => const SettingsAiFragment(),
      ),
      _SettingsEntry(
        icon: Icons.monitor_heart_outlined,
        title: l10n.diagnostics,
        description: l10n.diagnostics_description,
        builder: (context) => const DiagnosticsScreen(),
      ),
    ];
  }

  Widget _entryTile(BuildContext context, _SettingsEntry entry) {
    return SettingsNavigationRow(
      icon: entry.icon,
      title: entry.title,
      description: entry.description,
      onTap: () =>
          Navigator.push(context, MaterialPageRoute(builder: entry.builder)),
    );
  }

  Widget _searchField(L10n l10n) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
      child: XSearchField(
        controller: _searchController,
        hintText: l10n.search_settings,
        onChanged: (value) => setState(() => _query = value),
      ),
    );
  }

  Widget _advancedTile(BuildContext context, List<_SettingsEntry> advanced) {
    final theme = Theme.of(context);
    final l10n = L10n.of(context);
    return ExpansionTile(
      key: const Key('settings-advanced'),
      initiallyExpanded: false,
      leading: Icon(Icons.tune, color: theme.colorScheme.onSurfaceVariant),
      title: Text(
        l10n.settings_section_advanced,
        style: theme.textTheme.titleMedium!.copyWith(
          fontWeight: FontWeight.w700,
        ),
      ),
      children: [for (final entry in advanced) _entryTile(context, entry)],
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    var key = widget.key ?? const Key("Settings");
    var appVersion = 'v${_packageInfo.version}+${_packageInfo.buildNumber}';
    final query = _query.trim().toLowerCase();
    final everyday = _everydayEntries(context, key);
    final data = _dataEntry(context);
    final advanced = _advancedEntries(context);

    return SettingsPageScaffold(
      title: l10n.settings,
      body: SettingsList(
        children: [
          _searchField(l10n),
          if (query.isNotEmpty)
            for (final entry in [
              ...everyday,
              data,
              ...advanced,
            ].where((e) => e.matches(query)))
              _entryTile(context, entry)
          else ...[
            for (final entry in everyday) _entryTile(context, entry),
            _entryTile(context, data),
            _advancedTile(context, advanced),
            const Divider(height: 1),
            _SectionLabel(l10n.app_info),
            SettingsAboutFragment(appVersion: appVersion),
          ],
        ],
      ),
    );
  }
}

class _SectionLabel extends StatelessWidget {
  final String label;

  const _SectionLabel(this.label);

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
      child: Text(
        label,
        style: theme.textTheme.titleSmall!.copyWith(
          color: theme.colorScheme.onSurfaceVariant,
        ),
      ),
    );
  }
}
