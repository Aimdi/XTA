import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/contrast.dart';

const pluginActionButtonStyle = ButtonStyle(
  minimumSize: WidgetStatePropertyAll(Size.square(48)),
  fixedSize: WidgetStatePropertyAll(Size.square(48)),
  visualDensity: VisualDensity.standard,
  tapTargetSize: MaterialTapTargetSize.padded,
);

/// Home already provides identity and the top safe area.
class PluginEmbedded extends InheritedWidget {
  const PluginEmbedded({super.key, required super.child});

  static bool maybeOf(BuildContext context) => context.dependOnInheritedWidgetOfExactType<PluginEmbedded>() != null;

  @override
  bool updateShouldNotify(covariant PluginEmbedded oldWidget) => false;
}

class PluginHomeTab {
  final IconData icon;
  final String label;
  final bool selected;
  final VoidCallback onTap;

  const PluginHomeTab({required this.icon, required this.label, required this.selected, required this.onTap});
}

/// Home uses a named section picker when the complete section rail cannot fit.
/// Standalone clients retain their identity and scrollable section rail.
class PluginHomeChrome extends StatelessWidget {
  final String? title;
  final Widget? mark;
  final List<PluginHomeTab> tabs;
  final List<Widget> actions;
  final Color? accent;

  const PluginHomeChrome({
    super.key,
    this.title,
    this.mark,
    this.tabs = const [],
    this.actions = const [],
    this.accent,
  });

  @override
  Widget build(BuildContext context) {
    final embedded = PluginEmbedded.maybeOf(context);
    final hasIdentity = !embedded && title != null;
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
          if (hasIdentity)
            ConstrainedBox(
              constraints: const BoxConstraints(minHeight: 56),
              child: Row(
                children: [
                  if (Navigator.canPop(context)) const BackButton(),
                  Padding(
                    padding: const EdgeInsetsDirectional.only(start: 16, end: 10),
                    child: mark ?? const SizedBox.shrink(),
                  ),
                  Expanded(
                    child: Semantics(
                      header: true,
                      child: Text(
                        title!,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: Theme.of(context).textTheme.titleLarge,
                      ),
                    ),
                  ),
                  ...actions,
                ],
              ),
            ),
          if (tabs.isNotEmpty || (!hasIdentity && actions.isNotEmpty))
            SizedBox(
              height: rowHeight,
              child: Row(
                children: [
                  Expanded(
                    child: LayoutBuilder(
                      builder: (context, constraints) {
                        // Only the top-level embedded toolbar is condensed.
                        // Inner filters and standalone navigation keep their rails.
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
                  if (!hasIdentity) ...actions,
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
    return embedded ? controls : SafeArea(bottom: false, child: controls);
  }

  bool _tabsFit(BuildContext context, double width) {
    var requiredWidth = 8.0;
    for (final tab in tabs) {
      final painter = TextPainter(
        text: TextSpan(
          text: tab.label,
          style: Theme.of(context).textTheme.labelLarge?.copyWith(
            fontWeight: tab.selected ? FontWeight.w700 : FontWeight.w500,
          ),
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

/// A compact, fully labelled alternative to an overflowing horizontal rail.
/// Selection remains owned by the caller's Store; opening this never loads a tab.
class PluginSectionPicker extends StatelessWidget {
  final List<PluginHomeTab> tabs;
  final Color? accent;

  const PluginSectionPicker({super.key, required this.tabs, this.accent}) : assert(tabs.length > 0);

  @override
  Widget build(BuildContext context) {
    final selectedIndex = tabs.indexWhere((tab) => tab.selected);
    final selected = selectedIndex < 0 ? 0 : selectedIndex;
    final current = tabs[selected];
    final color = ensureContrast(
      accent ?? tweetReadableAccentColor(context),
      Theme.of(context).scaffoldBackgroundColor,
    );
    return PopupMenuButton<int>(
      tooltip: MaterialLocalizations.of(context).showMenuTooltip,
      initialValue: selected,
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
                  if (index == selected) ...[
                    const SizedBox(width: 8),
                    const Icon(Icons.check, size: 18),
                  ],
                ],
              ),
            ),
          ),
      ],
      child: ConstrainedBox(
        constraints: const BoxConstraints(minWidth: 48, minHeight: 48),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
          child: Row(
            children: [
              Icon(current.icon, size: 20, color: color),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  current.label,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.labelLarge?.copyWith(
                    color: tweetPrimaryColor(context),
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
              const SizedBox(width: 4),
              Icon(Icons.expand_more, size: 20, color: tweetSecondaryColor(context)),
            ],
          ),
        ),
      ),
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
