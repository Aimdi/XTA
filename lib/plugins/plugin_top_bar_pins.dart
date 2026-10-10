import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/plugins/plugin_home_dock.dart';
import 'package:xta/utils/pref_lists.dart';

/// Options-sheet entries the reader pinned to a plugin's top bar, in pin order.
class PluginTopBarPinsStore extends Store<Map<String, List<String>>> {
  final BasePrefService prefs;

  PluginTopBarPinsStore(this.prefs) : super(const {});

  List<String> pinned(String pluginId) =>
      state[pluginId] ?? stringListPref(prefs, pluginTopBarPinsKey(pluginId)) ?? const [];

  Future<void> toggle(String pluginId, String toolId) async {
    final current = pinned(pluginId);
    final next = current.contains(toolId) ? current.where((id) => id != toolId).toList() : [...current, toolId];
    update({...state, pluginId: next});
    await prefs.set(pluginTopBarPinsKey(pluginId), next);
  }
}

/// One thing the options sheet offers, described well enough to also sit in the top bar.
@immutable
class PluginDockTool {
  final String id;
  final String label;
  final Widget? icon;

  /// How the options sheet titles it, when that is richer than [label].
  final Widget? title;
  final Widget? subtitle;
  final Widget? trailing;
  final bool? checked;
  final VoidCallback? onPressed;

  /// A self-contained control that the top bar shows as is.
  final Widget? control;

  const PluginDockTool({
    required this.id,
    required this.label,
    this.icon,
    this.title,
    this.subtitle,
    this.trailing,
    this.checked,
    this.onPressed,
    this.control,
  });

  bool get pinnable => id.isNotEmpty && label.isNotEmpty;
}

String? _keyId(Key? key) => key is ValueKey<String> ? key.value : null;

String? _labelOf(Widget? widget) => switch (widget) {
  Text(:final data?) => data,
  ListTile(:final title) => _labelOf(title),
  _ => null,
};

/// [entry] of [menu], run from [context] the way the menu itself would run it.
PluginDockTool pluginMenuEntryTool(BuildContext context, PluginHomeMenu menu, PopupMenuItem<String> entry) {
  final tile = entry.child is ListTile ? entry.child as ListTile : null;
  return PluginDockTool(
    id: _keyId(entry.key) ?? (entry.value == null ? '' : 'menu:${entry.value}'),
    label: _labelOf(entry.child) ?? '',
    icon: tile?.leading,
    title: tile?.title ?? entry.child,
    subtitle: tile?.subtitle,
    trailing: tile?.trailing,
    checked: entry is CheckedPopupMenuItem<String> ? entry.checked : null,
    onPressed: entry.enabled
        ? () {
            entry.onTap?.call();
            if (entry.value != null) menu.select(context, entry.value!);
          }
        : null,
  );
}

/// A button-like [widget] the top bar can show unchanged.
PluginDockTool? pluginControlTool(BuildContext context, Widget widget) => switch (widget) {
  PluginDockFilterButton() => PluginDockTool(
    id: _keyId(widget.key) ?? 'xta:filters',
    label: L10n.of(context).filters,
    icon: const Icon(Icons.tune),
    control: widget,
  ),
  IconButton(:final tooltip?) => PluginDockTool(
    id: _keyId(widget.key) ?? tooltip,
    label: tooltip,
    icon: widget.icon,
    onPressed: widget.onPressed,
    control: widget,
  ),
  PluginHomeSecondaryAction(:final label) => PluginDockTool(
    id: _keyId(widget.key) ?? label,
    label: label,
    icon: widget.icon,
    onPressed: widget.onPressed,
  ),
  PopupMenuButton<String>(:final tooltip?) => PluginDockTool(
    id: _keyId(widget.key) ?? tooltip,
    label: tooltip,
    icon: widget.icon ?? const Icon(Icons.more_vert),
    control: widget,
  ),
  _ => null,
};

Iterable<PluginDockTool> _actionTools(BuildContext context, List<Widget> actions) => actions.expand(
  (action) => action is PluginHomeMenu
      ? action
            .itemBuilder(context)
            .whereType<PopupMenuItem<String>>()
            .map((entry) => pluginMenuEntryTool(context, action, entry))
      : [?pluginControlTool(context, action)],
);

PluginDockTool? pluginOpenClientTool(PluginHomeDockScope? scope) => scope?.onOpenClient == null
    ? null
    : PluginDockTool(
        id: 'xta:open-client',
        label: scope!.openClientLabel,
        icon: const Icon(Icons.open_in_new),
        onPressed: scope.onOpenClient,
      );

/// Everything in [source]'s options sheet that can be pinned, each once.
List<PluginDockTool> pluginDockTools(BuildContext context, PluginHomeDockStore store, String source) {
  final navigation = store.content(source, 'navigation');
  final reading = store.content(source, 'reading');
  final following = store.content(source, 'following');
  final tools = [
    ..._actionTools(context, navigation?.actions ?? const []),
    ..._actionTools(context, reading?.actions ?? const []),
    ...[...?following?.trailing, ...?reading?.trailing].map((widget) => pluginControlTool(context, widget)).nonNulls,
    ?pluginOpenClientTool(PluginHomeDockScope.maybeOf(context)),
  ];
  final seen = <String>{};
  return tools.where((tool) => tool.pinnable && seen.add(tool.id)).toList();
}

/// The pinned tools of [source] in pin order, leaving out the one the bar already shows as [primary].
List<PluginDockTool> pinnedDockTools(
  BuildContext context,
  PluginHomeDockStore store,
  String source, {
  Widget? primary,
}) {
  final pins = context.read<PluginTopBarPinsStore?>()?.pinned(source) ?? const [];
  if (pins.isEmpty) return const [];
  final tools = {for (final tool in pluginDockTools(context, store, source)) tool.id: tool};
  final primaryId = primary == null ? null : pluginControlTool(context, primary)?.id;
  return [
    for (final id in pins)
      if (tools[id] case final tool? when tool.id != primaryId && !identical(tool.control, primary)) tool,
  ];
}

double _toolWidth(BuildContext context, PluginDockTool tool) {
  if (tool.control != null || tool.icon != null) return 48;
  final painter = TextPainter(
    text: TextSpan(text: tool.label, style: Theme.of(context).textTheme.labelLarge),
    textDirection: Directionality.of(context),
    textScaler: MediaQuery.textScalerOf(context),
    maxLines: 1,
  )..layout();
  final width = (painter.width + 24).clamp(48.0, 120.0);
  painter.dispose();
  return width;
}

Widget _toolButton(BuildContext context, PluginDockTool tool, double width) {
  final key = ValueKey('top-bar-pin-${tool.id}');
  if (tool.control != null) return KeyedSubtree(key: key, child: tool.control!);
  if (tool.icon != null) {
    return IconButton(
      key: key,
      style: pluginActionButtonStyle,
      tooltip: tool.label,
      isSelected: tool.checked,
      icon: tool.icon!,
      onPressed: tool.onPressed,
    );
  }
  return SizedBox(
    width: width,
    height: 48,
    child: TextButton(
      key: key,
      style: TextButton.styleFrom(padding: const EdgeInsets.symmetric(horizontal: 12)),
      onPressed: tool.onPressed,
      child: Text(tool.label, maxLines: 1, overflow: TextOverflow.ellipsis),
    ),
  );
}

/// The leading run of [tools] that fits in [room]; the rest stay in the options sheet.
({List<Widget> widgets, double width}) fitPinnedTools(BuildContext context, List<PluginDockTool> tools, double room) {
  final widths = tools.map((tool) => _toolWidth(context, tool)).toList();
  var used = 0.0;
  final count = widths.takeWhile((width) => (used += width) <= math.max(0, room)).length;
  return (
    widgets: [for (var i = 0; i < count; i++) _toolButton(context, tools[i], widths[i])],
    width: widths.take(count).fold(0.0, (sum, width) => sum + width),
  );
}

/// Rebuilds [builder] when the reader pins or unpins something.
class PluginTopBarPinsListener extends StatelessWidget {
  final WidgetBuilder builder;
  const PluginTopBarPinsListener({super.key, required this.builder});

  @override
  Widget build(BuildContext context) {
    final pins = context.read<PluginTopBarPinsStore?>();
    if (pins == null) return builder(context);
    return ScopedBuilder<PluginTopBarPinsStore, Map<String, List<String>>>(
      store: pins,
      onState: (context, _) => builder(context),
    );
  }
}

/// Lets the reader choose which options-sheet entries also sit in the top bar.
class PluginTopBarPinsSheet extends StatelessWidget {
  final String source;
  final List<PluginDockTool> tools;
  final PluginTopBarPinsStore pins;
  const PluginTopBarPinsSheet({super.key, required this.source, required this.tools, required this.pins});

  @override
  Widget build(BuildContext context) => ScopedBuilder<PluginTopBarPinsStore, Map<String, List<String>>>(
    store: pins,
    onState: (context, _) {
      final pinned = pins.pinned(source);
      return SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(8, 0, 8, 16),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            ListTile(
              title: Text(L10n.of(context).plugin_top_bar_pins, style: Theme.of(context).textTheme.titleLarge),
              subtitle: Text(L10n.of(context).plugin_top_bar_pins_hint),
            ),
            for (final tool in tools)
              CheckboxListTile(
                key: ValueKey('top-bar-pin-option-${tool.id}'),
                secondary: tool.icon ?? const Icon(Icons.label_outline),
                title: Text(tool.label),
                value: pinned.contains(tool.id),
                onChanged: (_) => pins.toggle(source, tool.id),
              ),
          ],
        ),
      );
    },
  );
}

/// The options-sheet entry that opens [PluginTopBarPinsSheet], or nothing outside a pinnable bar.
class PluginTopBarPinsTile extends StatelessWidget {
  final BuildContext opener;
  final PluginHomeDockStore store;
  final String source;
  const PluginTopBarPinsTile({super.key, required this.opener, required this.store, required this.source});

  @override
  Widget build(BuildContext context) {
    final pins = opener.read<PluginTopBarPinsStore?>();
    final tools = opener.mounted ? pluginDockTools(opener, store, source) : const <PluginDockTool>[];
    if (pins == null || tools.isEmpty) return const SizedBox.shrink();
    return ListTile(
      key: const ValueKey('top-bar-pins'),
      minTileHeight: 48,
      leading: const Icon(Icons.push_pin_outlined),
      title: Text(L10n.of(context).plugin_top_bar_pins),
      onTap: () => showModalBottomSheet<void>(
        context: context,
        showDragHandle: true,
        isScrollControlled: true,
        useSafeArea: true,
        builder: (_) => PluginTopBarPinsSheet(source: source, tools: tools, pins: pins),
      ),
    );
  }
}
