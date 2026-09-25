import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/home/feed_strip_store.dart';
import 'package:xta/home/home_model.dart';
import 'package:xta/plugins/plugin.dart';
import 'package:xta/plugins/plugin_client_route.dart';
import 'package:xta/plugins/plugin_brand.dart';
import 'package:xta/plugins/plugin_storage.dart';
import 'package:xta/settings/plugin_store_tile.dart';
import 'package:xta/utils/pref_lists.dart';

/// A plugin on offer but not installed: one line of what it does, and Install.
class AvailablePluginRow extends StatelessWidget {
  final XtaPlugin plugin;
  final VoidCallback onInstall;

  const AvailablePluginRow({
    super.key,
    required this.plugin,
    required this.onInstall,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    return PluginStoreTile(
      leading: pluginBrandIcon(context, plugin, size: 28),
      title: Text(
        plugin.title(context),
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: theme.textTheme.titleMedium!.copyWith(fontWeight: FontWeight.w700),
      ),
      subtitle: Text(
        plugin.description(context),
        maxLines: 2,
        overflow: TextOverflow.ellipsis,
        style: theme.textTheme.bodySmall!.copyWith(color: theme.colorScheme.onSurfaceVariant),
      ),
      actions: FilledButton.tonal(
        onPressed: onInstall,
        style: FilledButton.styleFrom(
          minimumSize: const Size(kPluginStoreTouchTarget, kPluginStoreTouchTarget),
          padding: const EdgeInsets.symmetric(horizontal: 12),
        ),
        child: Text(l10n.plugin_install),
      ),
    );
  }
}

/// An installed plugin on one row: footprint, tab, settings, uninstall.
class InstalledPluginRow extends StatelessWidget {
  final XtaPlugin plugin;
  final VoidCallback onUninstall;

  /// The tab switch and the settings screen both change what the parent should
  /// draw, and neither owns the list.
  final VoidCallback onChanged;

  const InstalledPluginRow({
    super.key,
    required this.plugin,
    required this.onUninstall,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    final prefs = PrefService.of(context);
    final l10n = L10n.of(context);
    final tabPref = plugin.homeTabPrefKey;
    final settings = plugin.settingsScreen(context);
    final canOpen = plugin.homePage(context) != null;
    final theme = Theme.of(context);

    return PluginStoreTile(
      leading: pluginBrandIcon(context, plugin, size: 28),
      title: Text(
        plugin.title(context),
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: theme.textTheme.titleMedium!.copyWith(fontWeight: FontWeight.w700),
      ),
      subtitle: PluginFootprintText(plugin: plugin),
      onTap: canOpen ? () => openPluginClient(context, plugin) : settings == null
          ? null
          : () async {
              await Navigator.push(
                context,
                MaterialPageRoute(builder: (_) => settings),
              );
              onChanged();
            },
      actions: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (canOpen)
            TextButton(
              key: ValueKey('plugin-open-${plugin.id}'),
              style: TextButton.styleFrom(
                minimumSize: const Size(kPluginStoreTouchTarget, kPluginStoreTouchTarget),
              ),
              onPressed: () => openPluginClient(context, plugin),
              child: Text(l10n.plugin_open),
            ),
          if (canOpen && settings != null) const SizedBox(width: 8),
          if (settings != null)
            _PluginIconButton(
              tooltip: l10n.settings,
              icon: Icons.tune,
              onPressed: () async {
                await Navigator.push(
                  context,
                  MaterialPageRoute(builder: (_) => settings),
                );
                onChanged();
              },
            ),
          if (canOpen || settings != null) const SizedBox(width: 8),
          PopupMenuButton<String>(
            tooltip: MaterialLocalizations.of(context).showMenuTooltip,
            padding: EdgeInsets.zero,
            iconSize: 20,
            style: const ButtonStyle(
              minimumSize: WidgetStatePropertyAll(Size.square(kPluginStoreTouchTarget)),
            ),
            onSelected: (value) {
              if (value == 'tab' && tabPref != null) _setShowsTab(context, prefs, tabPref);
              if (value == 'uninstall') {
                onUninstall();
              }
            },
            itemBuilder: (context) => [
              if (tabPref != null) CheckedPopupMenuItem(value: 'tab',
                checked: plugin.showsHomeTab(prefs), child: Text(l10n.plugin_show_as_tab)),
              PopupMenuItem(
                value: 'uninstall',
                child: Text(l10n.plugin_uninstall),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Future<void> _setShowsTab(
    BuildContext context,
    BasePrefService prefs,
    String tabPref,
  ) async {
    final next = !plugin.showsHomeTab(prefs);
    final home = context.read<HomeModel>();
    await prefs.set(tabPref, next);

    // Asking for the tab back has to actually bring it back: the page list only
    // auto-selects a plugin tab it has never seeded, so that memory is cleared
    // here or the switch would turn on and nothing appear.
    if (next) {
      final seeded =
          stringListPref(prefs, optionSeededPluginTabs) ?? const <String>[];
      await prefs.set(
        optionSeededPluginTabs,
        seeded.where((e) => e != plugin.id).toList(),
      );
    } else if (context.mounted) {
      // Off the bottom bar → home strip, not a Groups chip.
      await pinPluginOnFeedStripIn(context, plugin.id);
    }

    if (!context.mounted) return;
    await home.loadPages();
    onChanged();
  }
}

class _PluginIconButton extends StatelessWidget {
  final String tooltip;
  final IconData icon;
  final VoidCallback onPressed;

  const _PluginIconButton({
    required this.tooltip,
    required this.icon,
    required this.onPressed,
  });

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return IconButton(
      tooltip: tooltip,
      onPressed: onPressed,
      iconSize: 20,
      padding: EdgeInsets.zero,
      constraints: const BoxConstraints(
        minWidth: kPluginStoreTouchTarget,
        minHeight: kPluginStoreTouchTarget,
      ),
      icon: Icon(
        icon,
        color: scheme.onSurfaceVariant,
      ),
    );
  }
}

/// Reads what a plugin is holding on the device, once, when it is first shown.
class PluginFootprintText extends StatefulWidget {
  final XtaPlugin plugin;

  const PluginFootprintText({super.key, required this.plugin});

  @override
  State<PluginFootprintText> createState() => _PluginFootprintTextState();
}

class _PluginFootprintTextState extends State<PluginFootprintText> {
  PluginFootprint? _footprint;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final footprint = await widget.plugin.footprint();
    if (mounted) {
      setState(() => _footprint = footprint);
    }
  }

  @override
  Widget build(BuildContext context) {
    final footprint = _footprint;
    final style = Theme.of(context).textTheme.bodySmall!.copyWith(
      color: Theme.of(context).colorScheme.onSurfaceVariant,
    );

    // Nothing until the answer is in: a "0 items" that turns into a real number
    // reads as the plugin having just been filled.
    if (footprint == null) {
      return const SizedBox.shrink();
    }

    if (footprint == emptyFootprint) {
      return Text(
        L10n.of(context).plugin_storage_empty,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: style,
      );
    }

    return Text(
      L10n.of(context).plugin_storage_used(
        '${footprint.items}',
        formatStorageSize(footprint.bytes),
      ),
      maxLines: 1,
      overflow: TextOverflow.ellipsis,
      style: style,
    );
  }
}
