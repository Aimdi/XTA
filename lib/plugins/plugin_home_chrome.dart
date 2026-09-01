import 'package:flutter/material.dart';

/// Marks a plugin screen as sitting under the home feed strip.
///
/// Those pins already name the plugin (Für dich / Reddit / Substack / …). A
/// second AppBar titled with the same name is just another chrome row. Screens
/// that read [maybeOf] drop that title and skip a top [SafeArea] — the parent
/// shell already cleared the status bar.
class PluginEmbedded extends InheritedWidget {
  const PluginEmbedded({super.key, required super.child});

  static bool maybeOf(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<PluginEmbedded>() != null;

  @override
  bool updateShouldNotify(covariant PluginEmbedded oldWidget) => false;
}

/// One destination on [PluginHomeChrome]. The [label] is a tooltip / semantic
/// name — the chrome itself is icon-only so long locales do not wrap.
class PluginHomeTab {
  final IconData icon;
  final String label;
  final bool selected;
  final VoidCallback onTap;

  const PluginHomeTab({
    required this.icon,
    required this.label,
    required this.selected,
    required this.onTap,
  });
}

/// Compact toolbar for a plugin already named by a parent tab.
///
/// One 48dp row: icon tabs on the left, actions on the right. Used instead of
/// a titled [AppBar] plus a second icon+label tab strip.
class PluginHomeChrome extends StatelessWidget {
  final List<PluginHomeTab> tabs;
  final List<Widget> actions;
  final Color? accent;

  const PluginHomeChrome({
    super.key,
    this.tabs = const [],
    this.actions = const [],
    this.accent,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final bar = Material(
      color: theme.colorScheme.surface,
      child: SizedBox(
        height: 48,
        child: Row(
          children: [
            Expanded(
              child: tabs.isEmpty
                  ? const SizedBox.shrink()
                  : ListView(
                      scrollDirection: Axis.horizontal,
                      primary: false,
                      padding: const EdgeInsets.symmetric(horizontal: 2),
                      children: [
                        for (final tab in tabs)
                          _TabButton(tab: tab, accent: accent),
                      ],
                    ),
            ),
            ...actions,
          ],
        ),
      ),
    );

    if (PluginEmbedded.maybeOf(context)) {
      return bar;
    }
    return SafeArea(bottom: false, child: bar);
  }
}

/// Title-less [AppBar] that puts a [TabBar] in the title slot so tabs and
/// actions share one row — the parent strip already named the plugin.
AppBar pluginHomeTabAppBar({
  required Widget tabs,
  List<Widget> actions = const [],
}) {
  return AppBar(
    automaticallyImplyLeading: false,
    titleSpacing: 0,
    title: tabs,
    actions: actions,
  );
}

class _TabButton extends StatelessWidget {
  final PluginHomeTab tab;
  final Color? accent;

  const _TabButton({required this.tab, this.accent});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final selectedColor = accent ?? theme.colorScheme.primary;
    final color = tab.selected
        ? selectedColor
        : theme.colorScheme.onSurfaceVariant;

    return Semantics(
      button: true,
      selected: tab.selected,
      label: tab.label,
      child: Tooltip(
        message: tab.label,
        child: InkWell(
          onTap: tab.onTap,
          child: SizedBox(
            width: 48,
            child: DecoratedBox(
              decoration: BoxDecoration(
                border: Border(
                  bottom: BorderSide(
                    color: tab.selected ? selectedColor : Colors.transparent,
                    width: 2,
                  ),
                ),
              ),
              child: Icon(tab.icon, size: 22, color: color),
            ),
          ),
        ),
      ),
    );
  }
}
