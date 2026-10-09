import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/plugins/plugin_home_dock.dart';
import 'package:xta/plugins/plugin_marks.dart';
import 'package:xta/plugins/plugin_top_bar_pins.dart';
import 'package:xta/plugins/substack/substack_plugin.dart';
import 'package:xta/ui/contrast.dart';

/// A single row for source, sections and actions, with full-size touch targets.
class SubstackCompactHeader extends StatelessWidget {
  final PluginHomeDockStore store;
  final VoidCallback? onPickSource;
  final Widget? services;
  final bool showBack;
  final bool unread;

  const SubstackCompactHeader({
    super.key,
    required this.store,
    this.onPickSource,
    this.services,
    this.showBack = false,
    this.unread = false,
  });

  @override
  Widget build(BuildContext context) => SizedBox(
    key: const ValueKey('substack-compact-header'),
    height: 52,
    child: ScopedBuilder<PluginHomeDockStore, Map<String, PluginDockEntry>>(
      store: store,
      onState: (context, _) => PluginTopBarPinsListener(
        builder: (context) => LayoutBuilder(
          builder: (context, constraints) {
            final navigation = store.content('substack', 'navigation');
            final reading = store.content('substack', 'reading');
            final tabs = navigation?.tabs ?? const <PluginHomeTab>[];
            final search = reading?.search ?? navigation?.search;
            final available = constraints.maxWidth - 8 - (showBack ? 24 : 0);
            final pinned = fitPinnedTools(
              context,
              pinnedDockTools(context, store, 'substack', primary: search),
              available - (2 + (tabs.isEmpty ? 0 : 1) + (search == null ? 0 : 1)) * 48,
            );
            final rest = available - pinned.width;
            // A pinned entry already gave up its room to search; otherwise search waits for every tab to fit.
            final showSearch = pinned.widgets.isNotEmpty || rest >= (tabs.length + 3) * 48;
            final showTabs = rest >= (tabs.length + 2 + (showSearch && search != null ? 1 : 0)) * 48;
            final color = ensureContrast(SubstackPlugin().brandColor, Theme.of(context).scaffoldBackgroundColor);
            return Padding(
              padding: const EdgeInsets.symmetric(horizontal: 4),
              child: IconButtonTheme(
                data: const IconButtonThemeData(style: pluginActionButtonStyle),
                child: Row(
                  children: [
                    if (showBack) const SizedBox.square(dimension: 48, child: BackButton()),
                    if (onPickSource != null)
                      Semantics(
                        label: [
                          L10n.of(context).plugin_substack_title,
                          if (unread) L10n.of(context).group_has_unread,
                        ].join(', '),
                        child: IconButton(
                          key: const ValueKey('substack-source-picker'),
                          tooltip: L10n.of(context).home_networks,
                          onPressed: onPickSource,
                          icon: Badge(
                            isLabelVisible: unread,
                            smallSize: 7,
                            child: pluginMark(SubstackPlugin(), size: 24, color: color),
                          ),
                        ),
                      )
                    else
                      SizedBox(
                        width: showBack ? 24 : 48,
                        child: Center(
                          child: Semantics(
                            label: L10n.of(context).plugin_substack_title,
                            image: true,
                            child: pluginMark(SubstackPlugin(), size: 24, color: color),
                          ),
                        ),
                      ),
                    Expanded(
                      child: showTabs
                          ? PluginCompactTabs(tabs: tabs, accent: color)
                          : tabs.isEmpty
                          ? const SizedBox.shrink()
                          : PluginSectionPicker(tabs: tabs, accent: color, verticalPadding: 4),
                    ),
                    if (showSearch && search != null) search,
                    ...pinned.widgets,
                    PluginDockOptionsButton(
                      store: store,
                      source: 'substack',
                      services: services,
                      includeActions: true,
                      includeSearch: true,
                      showSections: false,
                      pinnable: true,
                      attention: reading?.attention ?? false,
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
