import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:quax/generated/l10n.dart';
import 'package:quax/home/home_model.dart';
import 'package:quax/constants.dart';
import 'package:quax/plugins/plugin_registry.dart';
import 'package:quax/settings/settings_chrome.dart';
import 'package:quax/settings/settings_view_store.dart';

/// Lists built-in plugins and lets the user enable / disable them.
class SettingsPluginStoreFragment extends StatefulWidget {
  const SettingsPluginStoreFragment({super.key});

  @override
  State<SettingsPluginStoreFragment> createState() =>
      _SettingsPluginStoreFragmentState();
}

class _SettingsPluginStoreFragmentState
    extends State<SettingsPluginStoreFragment> {
  late final SettingsRevisionStore _viewStore;

  @override
  void initState() {
    super.initState();
    _viewStore = SettingsRevisionStore();
  }

  @override
  void dispose() {
    _viewStore.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final prefs = PrefService.of(context);

    return SettingsPageScaffold(
      title: L10n.of(context).plugin_store,
      body: ScopedBuilder<SettingsRevisionStore, int>(
        store: _viewStore,
        onState: (_, __) => SettingsList(
          children: [
            SettingsSection(
              description: L10n.of(context).plugin_store_description,
              children: [
                for (var index = 0; index < builtInPlugins.length; index++)
                  Builder(
                    builder: (context) {
                      final plugin = builtInPlugins[index];
                      final enabled = plugin.isEnabled(prefs);
                      // Two independent extras: a plugin can have its own settings screen,
                      // its own optional home tab, either, or neither.
                      final tabPref = plugin.homeTabPrefKey;
                      final hasSettings =
                          plugin.settingsScreen(context) != null;

                      final row = SwitchListTile(
                        secondary: Icon(plugin.icon),
                        title: Text(plugin.title(context)),
                        subtitle: Text(plugin.description(context)),
                        value: enabled,
                        onChanged: (value) async {
                          await plugin.setEnabled(prefs, value);
                          if (!context.mounted) return;
                          await context.read<HomeModel>().loadPages();
                          _viewStore.refresh();
                        },
                      );

                      if (tabPref == null && !hasSettings) {
                        return row;
                      }

                      return Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          row,
                          // Plugins whose feature is reachable from the Groups tab as well
                          // can give up their own tab.
                          if (tabPref != null)
                            SwitchListTile(
                              secondary: const SizedBox(width: 24),
                              title: Text(L10n.of(context).plugin_show_as_tab),
                              subtitle: Text(
                                L10n.of(context).plugin_show_as_tab_description,
                              ),
                              value: plugin.showsHomeTab(prefs),
                              onChanged: enabled
                                  ? (value) async {
                                      await prefs.set(tabPref, value);
                                      // Asking for the tab back has to actually bring it
                                      // back: the page list only auto-selects a plugin tab
                                      // it has never seeded, so that memory is cleared here
                                      // or the switch would turn on and nothing appear.
                                      if (value) {
                                        final seeded =
                                            prefs.getStringList(
                                              optionSeededPluginTabs,
                                            ) ??
                                            const <String>[];
                                        await prefs.set(
                                          optionSeededPluginTabs,
                                          seeded
                                              .where((e) => e != plugin.id)
                                              .toList(),
                                        );
                                      }
                                      if (!context.mounted) return;
                                      await context
                                          .read<HomeModel>()
                                          .loadPages();
                                      _viewStore.refresh();
                                    }
                                  : null,
                            ),
                          // A plugin that needs configuring gets a row of its own, only
                          // usable once it is on.
                          if (hasSettings)
                            ListTile(
                              enabled: enabled,
                              leading: const SizedBox(width: 24),
                              title: Text(L10n.of(context).settings),
                              trailing: const Icon(Icons.chevron_right),
                              onTap: () async {
                                await Navigator.push(
                                  context,
                                  MaterialPageRoute(
                                    builder: (_) =>
                                        plugin.settingsScreen(context)!,
                                  ),
                                );
                                if (mounted) _viewStore.refresh();
                              },
                            ),
                        ],
                      );
                    },
                  ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
