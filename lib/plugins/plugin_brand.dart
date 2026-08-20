import 'package:flutter/material.dart';
import 'package:xta/plugins/plugin.dart';
import 'package:xta/plugins/plugin_category.dart';

/// Groups [plugins] under each category that has at least one entry.
List<({PluginCategory category, List<XtaPlugin> plugins})>
groupPluginsByCategory(Iterable<XtaPlugin> plugins) {
  final byCategory = {
    for (final category in pluginCategoryOrder) category: <XtaPlugin>[],
  };
  for (final plugin in plugins) {
    byCategory[plugin.category]!.add(plugin);
  }
  return [
    for (final category in pluginCategoryOrder)
      if (byCategory[category]!.isNotEmpty)
        (category: category, plugins: byCategory[category]!),
  ];
}

/// Whether a store row matches what the reader typed.
bool pluginMatchesStoreQuery({
  required String query,
  required String id,
  required String title,
  required String description,
  required String category,
}) {
  final q = query.trim().toLowerCase();
  if (q.isEmpty) {
    return true;
  }
  final compact = q.replaceAll(RegExp(r'\s+'), '');
  final idLower = id.toLowerCase();
  final initials = title
      .trim()
      .split(RegExp(r'\s+'))
      .where((word) => word.isNotEmpty)
      .map((word) => word[0].toLowerCase())
      .join();
  return idLower.contains(q) ||
      idLower.contains(compact) ||
      initials == q ||
      title.toLowerCase().contains(q) ||
      description.toLowerCase().contains(q) ||
      category.toLowerCase().contains(q);
}

/// Store layout: what is already on the device, then the rest by category.
({
  List<XtaPlugin> installed,
  List<({PluginCategory category, List<XtaPlugin> plugins})>
  availableByCategory,
})
pluginStoreSections(
  Iterable<XtaPlugin> plugins, {
  required bool Function(XtaPlugin plugin) isInstalled,
}) {
  final installed = [
    for (final plugin in plugins)
      if (isInstalled(plugin)) plugin,
  ];
  final availableByCategory = groupPluginsByCategory([
    for (final plugin in plugins)
      if (!isInstalled(plugin)) plugin,
  ]);
  return (installed: installed, availableByCategory: availableByCategory);
}

/// Leading mark for the plugin store: brand tint + the plugin's icon.
Widget pluginBrandIcon(
  BuildContext context,
  XtaPlugin plugin, {
  double size = 40,
}) {
  final scheme = Theme.of(context).colorScheme;
  final brand = readableBrandColor(context, plugin.brandColor);
  return Container(
    width: size,
    height: size,
    alignment: Alignment.center,
    decoration: BoxDecoration(
      color: brand.withValues(
        alpha: Theme.of(context).brightness == Brightness.dark ? 0.22 : 0.14,
      ),
      borderRadius: BorderRadius.circular(10),
    ),
    child: Icon(
      plugin.icon,
      size: size * 0.55,
      color: brand == scheme.onSurface ? scheme.primary : brand,
    ),
  );
}

/// Near-black brand colours vanish on a dark surface; lighten them there.
Color readableBrandColor(BuildContext context, Color brand) {
  if (Theme.of(context).brightness == Brightness.dark &&
      brand.computeLuminance() < 0.12) {
    return Color.lerp(brand, Colors.white, 0.72)!;
  }
  return brand;
}
