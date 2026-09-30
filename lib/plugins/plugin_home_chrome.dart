import 'package:xta/plugins/plugin_home_dock.dart';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/contrast.dart';
import 'package:xta/ui/reader_swipe_navigation.dart';

const pluginActionButtonStyle = ButtonStyle(
  minimumSize: WidgetStatePropertyAll(Size.square(48)),
  fixedSize: WidgetStatePropertyAll(Size.square(48)),
  visualDensity: VisualDensity.standard,
  tapTargetSize: MaterialTapTargetSize.padded,
);

double pluginToolbarHeight(BuildContext context) {
  final style = Theme.of(context).textTheme.titleMedium;
  final lineHeight = MediaQuery.textScalerOf(context).scale(style?.fontSize ?? 16) * (style?.height ?? 1.5);
  return math.max(52, lineHeight + 4);
}

/// Home already provides identity and the top safe area.
class PluginEmbedded extends InheritedWidget {
  const PluginEmbedded({super.key, required super.child});

  static bool maybeOf(BuildContext context) => context.dependOnInheritedWidgetOfExactType<PluginEmbedded>() != null;

  @override
  bool updateShouldNotify(covariant PluginEmbedded oldWidget) => false;
}

class PluginHomeTab {
  final Key? key;
  final IconData icon;
  final String label;
  final bool selected;
  final VoidCallback onTap;

  const PluginHomeTab({this.key, required this.icon, required this.label, required this.selected, required this.onTap});
}

/// Home contributes controls to its host; standalone readers use one slim row.
class PluginHomeChrome extends StatelessWidget {
  final String? title;
  final Widget? mark;
  final List<PluginHomeTab> tabs;
  final List<Widget> actions;
  final Widget? search;
  final Color? accent;

  const PluginHomeChrome({
    super.key,
    this.title,
    this.mark,
    this.tabs = const [],
    this.actions = const [],
    this.search,
    this.accent,
  });

  @override
  Widget build(BuildContext context) {
    final embedded = PluginEmbedded.maybeOf(context) || PluginHomeDockScope.maybeOf(context) != null;
    final hasIdentity = !embedded && title != null;
    if (hasIdentity) return _standaloneBar(context);
    final labelStyle = Theme.of(context).textTheme.labelLarge;
    final rowHeight = math.max(
      48.0,
      MediaQuery.textScalerOf(context).scale(labelStyle?.fontSize ?? 14) * (labelStyle?.height ?? 1.4) + 16,
    );
    final bar = Material(
      color: Theme.of(context).scaffoldBackgroundColor,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (tabs.isNotEmpty || actions.isNotEmpty)
            SizedBox(
              height: rowHeight,
              child: Row(
                children: [
                  Expanded(
                    child: LayoutBuilder(
                      builder: (context, constraints) {
                        // Inner filters keep their labelled rails.
                        if (embedded && title != null && tabs.length > 1 && !_tabsFit(context, constraints.maxWidth)) {
                          return PluginSectionPicker(tabs: tabs, accent: accent);
                        }
                        return ListView(
                          scrollDirection: Axis.horizontal,
                          primary: false,
                          padding: const EdgeInsets.symmetric(horizontal: 4),
                          children: [for (final tab in tabs) _TabButton(tab: tab, accent: accent)],
                        );
                      },
                    ),
                  ),
                  ...actions,
                ],
              ),
            ),
        ],
      ),
    );
    final controls = IconButtonTheme(
      data: IconButtonThemeData(
        style: IconButtonTheme.of(context).style?.merge(pluginActionButtonStyle) ?? pluginActionButtonStyle,
      ),
      child: bar,
    );
    final fallback = embedded ? controls : SafeArea(bottom: false, child: controls);
    if (!embedded || title == null) return fallback;
    final selected = tabs.where((tab) => tab.selected).firstOrNull ?? tabs.firstOrNull;
    return PluginDockContribution(
      slot: 'navigation',
      content: PluginDockContent(
        tabs: tabs,
        search: search,
        section: tabs.isEmpty ? null : PluginSectionPicker(tabs: tabs, accent: accent),
        sectionWidth: selected == null ? 0 : pluginDockSectionWidth(context, selected.label),
        actions: actions,
      ),
      fallback: fallback,
    );
  }

  Widget _standaloneBar(BuildContext context) => SafeArea(
    bottom: false,
    child: Material(
      color: Theme.of(context).scaffoldBackgroundColor,
      child: IconButtonTheme(
        data: const IconButtonThemeData(style: pluginActionButtonStyle),
        child: SizedBox(
          height: pluginToolbarHeight(context),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 4),
            child: Row(
              children: [
                if (Navigator.canPop(context)) const SizedBox.square(dimension: 48, child: BackButton()),
                if (mark != null)
                  SizedBox(
                    width: 40,
                    child: Tooltip(
                      message: title!,
                      child: Semantics(label: title, image: true, child: mark),
                    ),
                  ),
                Expanded(
                  child: tabs.isEmpty
                      ? Text(title!, maxLines: 1, overflow: TextOverflow.ellipsis)
                      : PluginCompactTabs(tabs: tabs, accent: accent),
                ),
                ...actions,
              ],
            ),
          ),
        ),
      ),
    ),
  );

  bool _tabsFit(BuildContext context, double width) {
    var requiredWidth = 8.0;
    for (final tab in tabs) {
      final painter = TextPainter(
        text: TextSpan(
          text: tab.label,
          style: Theme.of(
            context,
          ).textTheme.labelLarge?.copyWith(fontWeight: tab.selected ? FontWeight.w700 : FontWeight.w500),
        ),
        textDirection: Directionality.of(context),
        textScaler: MediaQuery.textScalerOf(context),
        maxLines: 1,
      )..layout();
      requiredWidth += math.max(48, painter.width + 52);
      painter.dispose();
    }
    return requiredWidth <= width;
  }
}

/// Icon navigation uses the space left by real actions, never shrinks targets.
class PluginCompactTabs extends StatelessWidget {
  final List<PluginHomeTab> tabs;
  final Color? accent;
  const PluginCompactTabs({super.key, required this.tabs, this.accent});

  @override
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, constraints) {
      if (tabs.isEmpty) return const SizedBox.shrink();
      if (constraints.maxWidth < tabs.length * 48) {
        return PluginSectionPicker(
          tabs: tabs,
          accent: accent,
          verticalPadding: 4,
          iconOnly: constraints.maxWidth < 104,
        );
      }
      final color = ensureContrast(
        accent ?? tweetReadableAccentColor(context),
        Theme.of(context).scaffoldBackgroundColor,
      );
      return _PluginSectionSwipe(
        tabs: tabs,
        child: Row(
          children: [
            for (final tab in tabs)
              Expanded(
                child: Semantics(
                  selected: tab.selected,
                  child: DecoratedBox(
                    decoration: BoxDecoration(
                      border: Border(bottom: BorderSide(width: 2, color: tab.selected ? color : Colors.transparent)),
                    ),
                    child: IconButton(
                      key: tab.key,
                      style: pluginActionButtonStyle,
                      tooltip: tab.label,
                      onPressed: tab.onTap,
                      icon: Icon(
                        tab.icon,
                        size: 22,
                        color: tab.selected ? color : Theme.of(context).colorScheme.onSurfaceVariant,
                      ),
                    ),
                  ),
                ),
              ),
          ],
        ),
      );
    },
  );
}

/// A compact, fully labelled alternative to an overflowing horizontal rail.
/// Selection remains owned by the caller's Store; opening this never loads a tab.
class PluginSectionPicker extends StatelessWidget {
  final List<PluginHomeTab> tabs;
  final Color? accent;
  final double verticalPadding;
  final bool iconOnly;

  const PluginSectionPicker({
    super.key,
    required this.tabs,
    this.accent,
    this.verticalPadding = 8,
    this.iconOnly = false,
  }) : assert(tabs.length > 0);

  @override
  Widget build(BuildContext context) {
    final selected = tabs.indexWhere((tab) => tab.selected);
    final current = selected < 0 ? null : tabs[selected];
    final label = current?.label ?? MaterialLocalizations.of(context).showMenuTooltip;
    final color = ensureContrast(
      accent ?? tweetReadableAccentColor(context),
      Theme.of(context).scaffoldBackgroundColor,
    );
    return _PluginSectionSwipe(
      tabs: tabs,
      child: PopupMenuButton<int>(
        tooltip: iconOnly ? label : MaterialLocalizations.of(context).showMenuTooltip,
        initialValue: selected < 0 ? null : selected,
        position: PopupMenuPosition.under,
        onSelected: (index) => tabs[index].onTap(),
        itemBuilder: (context) => [
          for (var index = 0; index < tabs.length; index++)
            PopupMenuItem<int>(
              value: index,
              height: 48,
              child: Semantics(
                selected: index == selected,
                child: Row(
                  children: [
                    Icon(tabs[index].icon, size: 20),
                    const SizedBox(width: 12),
                    Expanded(child: Text(tabs[index].label)),
                    if (index == selected) ...[const SizedBox(width: 8), const Icon(Icons.check, size: 18)],
                  ],
                ),
              ),
            ),
        ],
        child: ConstrainedBox(
          constraints: const BoxConstraints(minWidth: 48, minHeight: 48),
          child: Padding(
            padding: EdgeInsets.symmetric(horizontal: iconOnly ? 4 : 12, vertical: verticalPadding),
            child: Row(
              children: [
                Icon(current?.icon ?? Icons.menu, size: 20, color: color),
                if (!iconOnly) ...[
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      label,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(
                        context,
                      ).textTheme.labelLarge?.copyWith(color: tweetPrimaryColor(context), fontWeight: FontWeight.w700),
                    ),
                  ),
                  const SizedBox(width: 4),
                ],
                Icon(Icons.expand_more, size: iconOnly ? 16 : 20, color: tweetSecondaryColor(context)),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _PluginSectionSwipe extends StatelessWidget {
  final List<PluginHomeTab> tabs;
  final Widget child;
  const _PluginSectionSwipe({required this.tabs, required this.child});

  @override
  Widget build(BuildContext context) {
    final selected = tabs.indexWhere((tab) => tab.selected);
    return ReaderSwipeNavigation(
      index: selected,
      count: selected < 0 ? 0 : tabs.length,
      identity: tabs.map((tab) => tab.label).join('|'),
      onChanged: (index) {
        tabs[index].onTap();
        return true;
      },
      child: child,
    );
  }
}

AppBar pluginHomeTabAppBar({required Widget tabs, List<Widget> actions = const []}) =>
    AppBar(automaticallyImplyLeading: false, titleSpacing: 0, title: tabs, actions: actions);

class _TabButton extends StatelessWidget {
  final PluginHomeTab tab;
  final Color? accent;

  const _TabButton({required this.tab, this.accent});

  @override
  Widget build(BuildContext context) {
    final selectedColor = ensureContrast(
      accent ?? tweetReadableAccentColor(context),
      Theme.of(context).scaffoldBackgroundColor,
    );
    final foreground = tab.selected ? tweetPrimaryColor(context) : tweetSecondaryColor(context);
    return Semantics(
      button: true,
      selected: tab.selected,
      label: tab.label,
      excludeSemantics: true,
      child: Tooltip(
        message: tab.label,
        child: InkWell(
          onTap: tab.onTap,
          child: Container(
            constraints: const BoxConstraints(minWidth: 48, minHeight: 48),
            padding: const EdgeInsets.symmetric(horizontal: 12),
            decoration: BoxDecoration(
              border: Border(bottom: BorderSide(color: tab.selected ? selectedColor : Colors.transparent, width: 2)),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(tab.icon, size: 20, color: tab.selected ? selectedColor : foreground),
                const SizedBox(width: 8),
                Text(
                  tab.label,
                  maxLines: 1,
                  style: Theme.of(context).textTheme.labelLarge?.copyWith(
                    color: foreground,
                    fontWeight: tab.selected ? FontWeight.w700 : FontWeight.w500,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
