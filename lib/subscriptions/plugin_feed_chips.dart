import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:xta/plugins/plugin.dart';
import 'package:xta/plugins/plugin_brand.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/ui/group_board_tokens.dart';
import 'package:xta/ui/x_controls.dart';

/// Enabled plugins that hid their home tab — they only live under Groups.
List<XtaPlugin> pluginFeedsOnGroupsTab(BasePrefService prefs) => [
  for (final plugin in builtInPlugins)
    if (plugin.isEnabled(prefs) &&
        !plugin.showsHomeTab(prefs) &&
        plugin.homeTabPrefKey != null)
      plugin,
];

Key pluginFeedChipKey(String pluginId) => ValueKey('plugin-feed-$pluginId');

const pluginFeedRouteKey = ValueKey('plugin-feed-route');

/// Compact icon+name pill for a plugin feed on the Groups tab.
class PluginFeedChip extends StatelessWidget {
  final XtaPlugin plugin;
  final VoidCallback onTap;

  const PluginFeedChip({super.key, required this.plugin, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final tokens = GroupBoardTokens.resolve(context);
    final title = plugin.title(context);
    final shape = StadiumBorder(side: BorderSide(color: tokens.border));

    return Tooltip(
      message: title,
      child: Semantics(
        button: true,
        label: title,
        child: Material(
          color: xControlFill(context),
          shape: shape,
          child: InkWell(
            onTap: onTap,
            customBorder: const StadiumBorder(),
            child: Padding(
              padding: const EdgeInsets.fromLTRB(6, 6, 10, 6),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  pluginBrandIcon(context, plugin, size: 20),
                  const SizedBox(width: 5),
                  Text(
                    title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: theme.textTheme.labelLarge?.copyWith(
                      fontSize: 13,
                      fontWeight: FontWeight.w600,
                      height: 1.1,
                      color: tokens.onSurface,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
