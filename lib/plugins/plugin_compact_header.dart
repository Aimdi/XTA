import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/plugins/plugin_home_dock.dart';
import 'package:xta/plugins/plugin_marks.dart';
import 'package:xta/plugins/plugin_top_bar_pins.dart';
import 'package:xta/ui/contrast.dart';

/// Substack's single-row presentation, backed by each reader's existing dock.
class PluginCompactHeader extends StatelessWidget {
  final XtaPlugin plugin;
  final PluginHomeDockStore store;
  final VoidCallback? onPickSource;
  final Widget? services;
  final bool showBack;
  final bool unread;

  const PluginCompactHeader({
    super.key,
    required this.plugin,
    required this.store,
    this.onPickSource,
    this.services,
    this.showBack = false,
    this.unread = false,
  });

  @override
  Widget build(BuildContext context) => SizedBox(
    height: pluginToolbarHeight(context),
    child: ScopedBuilder<PluginHomeDockStore, Map<String, PluginDockEntry>>(
      store: store,
      onState: (context, _) => PluginTopBarPinsListener(
        builder: (context) => LayoutBuilder(
          builder: (context, constraints) {
            final navigation = store.content(plugin.id, 'navigation');
            final reading = store.content(plugin.id, 'reading');
            final following = store.content(plugin.id, 'following');
            final tabs = navigation?.tabs ?? const <PluginHomeTab>[];
            final primary =
                reading?.search ?? navigation?.search ?? navigation?.actions.whereType<IconButton>().firstOrNull;
            final markWidth = onPickSource != null || !showBack ? 48.0 : 24.0;
            final reserved = 8 + markWidth + (showBack ? 48 : 0) + 48 + (tabs.isEmpty ? 0 : 48);
            final showPrimary = primary != null && constraints.maxWidth >= reserved + 48;
            final pinned = fitPinnedTools(
              context,
              pinnedDockTools(context, store, plugin.id, primary: primary),
              constraints.maxWidth - reserved - (showPrimary ? 48 : 0),
            );
            final background = Theme.of(context).scaffoldBackgroundColor;
            final color = ensureContrast(plugin.brandColor, background);
            final markColor = ensureContrast(pluginMarkTint(context, plugin), background);
            return IconButtonTheme(
              data: const IconButtonThemeData(style: pluginActionButtonStyle),
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 4),
                child: Row(
                  children: [
                    if (showBack) const SizedBox.square(dimension: 48, child: BackButton()),
                    if (onPickSource != null)
                      Semantics(
                        label: [plugin.title(context), if (unread) L10n.of(context).group_has_unread].join(', '),
                        child: IconButton(
                          key: ValueKey('plugin-source-picker-${plugin.id}'),
                          tooltip: L10n.of(context).home_networks,
                          onPressed: onPickSource,
                          icon: Badge(
                            isLabelVisible: unread,
                            smallSize: 7,
                            child: pluginMark(plugin, size: 24, color: markColor),
                          ),
                        ),
                      )
                    else
                      SizedBox(
                        width: markWidth,
                        child: Tooltip(
                          message: plugin.title(context),
                          child: Semantics(
                            label: plugin.title(context),
                            image: true,
                            child: pluginMark(plugin, size: 24, color: markColor),
                          ),
                        ),
                      ),
                    Expanded(
                      child: PluginCompactTabs(tabs: tabs, accent: color),
                    ),
                    if (showPrimary) primary,
                    ...pinned.widgets,
                    PluginDockOptionsButton(
                      store: store,
                      source: plugin.id,
                      services: services,
                      includeActions: true,
                      includeSearch: true,
                      pinnable: true,
                      attention: [navigation, reading, following].any((content) => content?.attention ?? false),
                    ),
                  ],
                ),
              ),
            );
          },
        ),
      ),
    ),
  );
}
