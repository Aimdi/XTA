import 'dart:math' as math;
import 'package:xta/plugins/plugin_home_reading_controls.dart';
import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';

/// A presentation contribution. Reader stores and callbacks stay with the reader.
class PluginDockContent {
  final Widget? section;
  final double sectionWidth;
  final List<Widget> actions;
  final Widget? search;
  final Widget? leading;
  final List<Widget> trailing;
  const PluginDockContent({
    this.section,
    this.sectionWidth = 112,
    this.actions = const [],
    this.search,
    this.leading,
    this.trailing = const [],
  });
}

class PluginDockEntry {
  final Object owner;
  final PluginDockContent content;
  const PluginDockEntry(this.owner, this.content);
}

class PluginHomeDockStore extends Store<Map<String, PluginDockEntry>> {
  bool _closed = false;
  final controls = HomeReadingControlsStore();
  PluginHomeDockStore() : super(const {});

  void publish(String source, String slot, Object owner, PluginDockContent content) {
    if (_closed) return;
    update({...state, '$source/$slot': PluginDockEntry(owner, content)});
  }

  void remove(String source, String slot, Object owner) {
    final key = '$source/$slot';
    if (_closed || !identical(state[key]?.owner, owner)) return;
    update({...state}..remove(key));
  }

  PluginDockContent? content(String source, String slot) => state['$source/$slot']?.content;

  @override
  Future<void> destroy() async {
    _closed = true;
    await controls.destroy();
    await super.destroy();
  }
}

/// Always occupies the same position above Home; changing source never reparents the feed.
class PluginHomeDockScope extends InheritedWidget {
  final PluginHomeDockStore store;
  final String source;
  final bool enabled;
  final String openClientLabel;
  final VoidCallback onOpenClient;
  const PluginHomeDockScope({
    super.key,
    required this.store,
    required this.source,
    required this.enabled,
    required this.openClientLabel,
    required this.onOpenClient,
    required super.child,
  });

  static PluginHomeDockScope? maybeOf(BuildContext context) {
    final scope = context.dependOnInheritedWidgetOfExactType<PluginHomeDockScope>();
    return scope?.enabled == true ? scope : null;
  }

  @override
  bool updateShouldNotify(PluginHomeDockScope oldWidget) =>
      store != oldWidget.store ||
      source != oldWidget.source ||
      enabled != oldWidget.enabled ||
      openClientLabel != oldWidget.openClientLabel;
}

/// Publish after layout, never notify a parent Store during a child's build.
/// Ownership prevents a departing pane from removing its replacement's controls.
class PluginDockContribution extends StatefulWidget {
  final String slot;
  final PluginDockContent content;
  final Widget fallback;
  const PluginDockContribution({super.key, required this.slot, required this.content, required this.fallback});
  @override
  State<PluginDockContribution> createState() => _PluginDockContributionState();
}

class _PluginDockContributionState extends State<PluginDockContribution> {
  final Object _owner = Object();
  PluginHomeDockScope? _scope;
  bool _queued = false;

  void _remove(PluginHomeDockScope? scope, String slot) {
    if (scope == null) return;
    WidgetsBinding.instance.addPostFrameCallback((_) => scope.store.remove(scope.source, slot, _owner));
  }

  void _publish() {
    if (_queued) return;
    _queued = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _queued = false;
      final scope = _scope;
      if (mounted && scope != null) scope.store.publish(scope.source, widget.slot, _owner, widget.content);
    });
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final next = PluginHomeDockScope.maybeOf(context);
    if (_scope?.source != next?.source || _scope?.store != next?.store) _remove(_scope, widget.slot);
    _scope = next;
    _publish();
  }

  @override
  void didUpdateWidget(PluginDockContribution oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.slot != widget.slot) _remove(_scope, oldWidget.slot);
    _publish();
  }

  @override
  void dispose() {
    _remove(_scope, widget.slot);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => _scope == null ? widget.fallback : const SizedBox.shrink();
}

class PluginDockActions extends StatelessWidget {
  final PluginHomeDockStore store;
  final String source;
  const PluginDockActions({super.key, required this.store, required this.source});
  @override
  Widget build(BuildContext context) => ScopedBuilder<PluginHomeDockStore, Map<String, PluginDockEntry>>(
    store: store,
    onState: (context, _) {
      final navigation = store.content(source, 'navigation');
      final reading = store.content(source, 'reading');
      return KeyedSubtree(
        key: ValueKey('home-dock-actions-$source'),
        child: IconButtonTheme(
          data: const IconButtonThemeData(style: pluginActionButtonStyle),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [if (reading?.search != null) reading!.search!, ...?navigation?.actions],
          ),
        ),
      );
    },
  );
}

/// One compact context row; wrap service controls instead of reducing touch targets.
class PluginDockRow extends StatelessWidget {
  final PluginHomeDockStore store;
  final String source;
  final Widget? services;
  final double servicesWidth;
  const PluginDockRow({super.key, required this.store, required this.source, this.services, this.servicesWidth = 0});

  @override
  Widget build(BuildContext context) => ScopedBuilder<PluginHomeDockStore, Map<String, PluginDockEntry>>(
    store: store,
    onState: (context, _) {
      final navigation = store.content(source, 'navigation');
      final reading = store.content(source, 'reading');
      final extras = store.content(source, 'following');
      final trailing = [...?extras?.trailing, ...?reading?.trailing];
      final leading = reading?.leading ?? extras?.leading;
      return KeyedSubtree(
        key: ValueKey('home-context-$source'),
        child: Material(
          key: const ValueKey('home-plugin-context'),
          color: Theme.of(context).scaffoldBackgroundColor,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 4),
            child: LayoutBuilder(
              builder: (context, constraints) {
                final sectionWidth = navigation?.sectionWidth ?? 112;
                final split =
                    services != null && servicesWidth + sectionWidth + trailing.length * 48 + 8 > constraints.maxWidth;
                Widget controls({required bool includeServices}) => ConstrainedBox(
                  constraints: const BoxConstraints(minHeight: 48),
                  child: Row(
                    children: [
                      if (includeServices && services != null) SizedBox(width: servicesWidth, child: services),
                      Expanded(child: navigation?.section ?? const SizedBox.shrink()),
                      if (leading != null) Flexible(child: leading),
                      ...trailing,
                    ],
                  ),
                );
                return Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    if (split) Align(alignment: AlignmentDirectional.centerStart, child: services!),
                    controls(includeServices: !split),
                  ],
                );
              },
            ),
          ),
        ),
      );
    },
  );
}

/// Existing plugin menus keep their actions and add the full-client entry in Home.
class PluginHomeMenu extends StatelessWidget {
  final PopupMenuItemBuilder<String> itemBuilder;
  final PopupMenuItemSelected<String>? onSelected;
  final ButtonStyle? style;
  final String? tooltip;
  const PluginHomeMenu({super.key, required this.itemBuilder, this.onSelected, this.style, this.tooltip});

  @override
  Widget build(BuildContext context) {
    final scope = PluginHomeDockScope.maybeOf(context);
    return PopupMenuButton<String>(
      style: style,
      tooltip: tooltip,
      onOpened: () => scope?.store.controls.reveal(),
      onSelected: (value) async {
        if (!context.mounted || scope?.source != PluginHomeDockScope.maybeOf(context)?.source) return;
        if (value == 'xta:pin-controls') {
          final controls = scope?.store.controls;
          if (controls != null) await controls.setPinned(!controls.state.pinned);
        } else if (value == 'xta:open-client') {
          if (context.mounted) scope?.onOpenClient();
        } else {
          onSelected?.call(value);
        }
      },
      itemBuilder: (context) => [
        ...itemBuilder(context),
        if (scope != null) ...[
          const PopupMenuDivider(),
          CheckedPopupMenuItem<String>(
            key: const ValueKey('home-pin-controls'),
            value: 'xta:pin-controls',
            checked: scope.store.controls.state.pinned,
            child: Text(L10n.of(context).home_keep_controls_visible),
          ),
          PopupMenuItem(
            key: ValueKey('open-client-${scope.source}'),
            value: 'xta:open-client',
            child: Text(scope.openClientLabel),
          ),
        ],
      ],
    );
  }
}

class PluginDockFilterButton extends StatelessWidget {
  final int activeCount;
  final VoidCallback onPressed;
  const PluginDockFilterButton({super.key, required this.activeCount, required this.onPressed});
  @override
  Widget build(BuildContext context) => IconButton(
    style: pluginActionButtonStyle,
    tooltip: L10n.of(context).filters,
    onPressed: onPressed,
    icon: Badge.count(count: activeCount, isLabelVisible: activeCount > 0, child: const Icon(Icons.tune)),
  );
}

/// Current-label width, not all possible tabs, determines the compact fallback.
double pluginDockSectionWidth(BuildContext context, String label) {
  final painter = TextPainter(
    text: TextSpan(
      text: label,
      style: Theme.of(context).textTheme.labelLarge?.copyWith(fontWeight: FontWeight.w700),
    ),
    textDirection: Directionality.of(context),
    textScaler: MediaQuery.textScalerOf(context),
    maxLines: 1,
  )..layout();
  final width = math.max(96.0, painter.width + 64);
  painter.dispose();
  return width;
}
