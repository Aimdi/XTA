import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/home/feed_strip_store.dart';
import 'package:xta/home/home_model.dart';
import 'package:xta/plugins/plugin.dart';
import 'package:xta/plugins/plugin_brand.dart';
import 'package:xta/plugins/plugin_storage.dart';

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
    return _PluginStoreTile(
      plugin: plugin,
      subtitle: plugin.description(context),
      trailing: FilledButton.tonal(
        onPressed: onInstall,
        style: FilledButton.styleFrom(
          visualDensity: VisualDensity.compact,
          tapTargetSize: MaterialTapTargetSize.shrinkWrap,
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

    return _PluginStoreTile(
      plugin: plugin,
      subtitleWidget: PluginFootprintText(plugin: plugin),
      onTap: settings == null
          ? null
          : () async {
              await Navigator.push(
                context,
                MaterialPageRoute(builder: (_) => settings),
              );
              onChanged();
            },
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (tabPref != null)
            _PluginIconButton(
              tooltip: l10n.plugin_show_as_tab,
              icon: plugin.showsHomeTab(prefs)
                  ? Icons.tab
                  : Icons.tab_unselected,
              selected: plugin.showsHomeTab(prefs),
              onPressed: () => _setShowsTab(context, prefs, tabPref),
            ),
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
          PopupMenuButton<String>(
            tooltip: l10n.plugin_uninstall,
            padding: EdgeInsets.zero,
            iconSize: 20,
            style: const ButtonStyle(
              visualDensity: VisualDensity.compact,
              tapTargetSize: MaterialTapTargetSize.shrinkWrap,
            ),
            onSelected: (value) {
              if (value == 'uninstall') {
                onUninstall();
              }
            },
            itemBuilder: (context) => [
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
    final strip = plugin.supportsFeedStrip
        ? context.read<FeedStripStore>()
        : null;
    await prefs.set(tabPref, next);

    // Asking for the tab back has to actually bring it back: the page list only
    // auto-selects a plugin tab it has never seeded, so that memory is cleared
    // here or the switch would turn on and nothing appear.
    if (next) {
      final seeded =
          prefs.getStringList(optionSeededPluginTabs) ?? const <String>[];
      await prefs.set(
        optionSeededPluginTabs,
        seeded.where((e) => e != plugin.id).toList(),
      );
    } else if (strip != null) {
      await strip.ensurePersisted();
      await strip.add(plugin.id);
    }

    if (!context.mounted) return;
    await home.loadPages();
    onChanged();
  }
}

/// Compact store row: brand chip, title, one subtitle line, trailing actions.
class _PluginStoreTile extends StatelessWidget {
  final XtaPlugin plugin;
  final String? subtitle;
  final Widget? subtitleWidget;
  final Widget trailing;
  final VoidCallback? onTap;

  const _PluginStoreTile({
    required this.plugin,
    required this.trailing,
    this.subtitle,
    this.subtitleWidget,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final caption = theme.textTheme.bodySmall!.copyWith(
      color: theme.colorScheme.onSurfaceVariant,
    );
    final below =
        subtitleWidget ??
        (subtitle == null
            ? null
            : Text(
                subtitle!,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: caption,
              ));

    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 8, 8, 8),
        child: Row(
          children: [
            pluginBrandIcon(context, plugin, size: 28),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    plugin.title(context),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: theme.textTheme.titleMedium!.copyWith(
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  if (below != null) ...[const SizedBox(height: 2), below],
                ],
              ),
            ),
            const SizedBox(width: 4),
            trailing,
          ],
        ),
      ),
    );
  }
}

class _PluginIconButton extends StatelessWidget {
  final String tooltip;
  final IconData icon;
  final VoidCallback onPressed;
  final bool selected;

  const _PluginIconButton({
    required this.tooltip,
    required this.icon,
    required this.onPressed,
    this.selected = false,
  });

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return IconButton(
      tooltip: tooltip,
      onPressed: onPressed,
      visualDensity: VisualDensity.compact,
      iconSize: 20,
      padding: EdgeInsets.zero,
      constraints: const BoxConstraints(minWidth: 36, minHeight: 36),
      icon: Icon(
        icon,
        color: selected ? scheme.primary : scheme.onSurfaceVariant,
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
