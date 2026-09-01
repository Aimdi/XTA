import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:quax/generated/l10n.dart';
import 'package:quax/settings/_about.dart';
import 'package:quax/settings/_accessibility.dart';
import 'package:quax/settings/_account.dart';
import 'package:quax/settings/_ai.dart';
import 'package:quax/settings/_data.dart';
import 'package:quax/settings/_general.dart';
import 'package:quax/settings/_home.dart';
import 'package:quax/settings/_media.dart';
import 'package:quax/settings/_plugin_store.dart';
import 'package:quax/settings/_posts.dart';
import 'package:quax/settings/_theme.dart';
import 'package:quax/settings/diagnostics_screen.dart';
import 'package:quax/settings/settings_chrome.dart';
import 'package:quax/settings/settings_view_store.dart';

class SettingsScreen extends StatefulWidget {
  final String? initialPage;

  const SettingsScreen({super.key, this.initialPage});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  late final SettingsPackageInfoStore _packageInfoStore;

  @override
  void initState() {
    super.initState();
    _packageInfoStore = SettingsPackageInfoStore()..load();
  }

  @override
  void dispose() {
    _packageInfoStore.destroy();
    super.dispose();
  }

  Future<void> _open(Widget screen) {
    return Navigator.push(
      context,
      MaterialPageRoute<void>(builder: (_) => screen),
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return SettingsPageScaffold(
      title: l10n.settings,
      body: ScopedBuilder<SettingsPackageInfoStore, PackageInfo?>(
        store: _packageInfoStore,
        onState: (_, info) => SettingsList(
          children: [
            SettingsSection(
              title: l10n.general,
              children: [
                SettingsNavigationRow(
                  icon: Icons.tune_outlined,
                  title: l10n.general,
                  description: l10n.language_subtitle,
                  onTap: () => _open(const SettingsGeneralFragment()),
                ),
                SettingsNavigationRow(
                  icon: Icons.article_outlined,
                  title: l10n.tweets,
                  description: l10n.always_show_full_tweet_contents_description,
                  onTap: () => _open(const SettingsPostsFragment()),
                ),
                SettingsNavigationRow(
                  icon: Icons.perm_media_outlined,
                  title: l10n.media,
                  description: l10n.save_bandwidth_using_smaller_images,
                  onTap: () => _open(const SettingsMediaFragment()),
                ),
                SettingsNavigationRow(
                  icon: Icons.home_outlined,
                  title: l10n.home,
                  description: l10n.reset_home_pages,
                  onTap: () => _open(const SettingsHomeFragment()),
                ),
                SettingsNavigationRow(
                  icon: Icons.palette_outlined,
                  title: l10n.theme,
                  description: l10n.theme_background_description,
                  onTap: () => _open(const SettingsThemeFragment()),
                ),
                SettingsNavigationRow(
                  icon: Icons.settings_accessibility_outlined,
                  title: l10n.accessibility,
                  description: l10n.text_scale_factor_description,
                  onTap: () => _open(const SettingsAccessibilityFragment()),
                ),
              ],
            ),
            SettingsSection(
              title: l10n.account,
              children: [
                SettingsNavigationRow(
                  icon: Icons.account_circle_outlined,
                  title: l10n.account,
                  onTap: () => _open(const SettingsAccountFragment()),
                ),
                SettingsNavigationRow(
                  icon: Icons.extension_outlined,
                  title: l10n.plugin_store,
                  description: l10n.plugin_store_description,
                  onTap: () => _open(const SettingsPluginStoreFragment()),
                ),
                SettingsNavigationRow(
                  icon: Icons.auto_awesome_outlined,
                  title: l10n.ai_provider,
                  description: l10n.ai_provider_description,
                  onTap: () => _open(const SettingsAiFragment()),
                ),
                SettingsNavigationRow(
                  icon: Icons.monitor_heart_outlined,
                  title: l10n.diagnostics,
                  description: l10n.diagnostics_description,
                  onTap: () => _open(const DiagnosticsScreen()),
                ),
              ],
            ),
            SettingsSection(
              title: l10n.data,
              children: const [SettingsDataFragment()],
            ),
            SettingsSection(
              title: l10n.app_info,
              children: [
                SettingsAboutFragment(
                  appVersion: info == null
                      ? ''
                      : 'v${info.version}+${info.buildNumber}',
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
