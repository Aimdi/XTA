import 'package:flutter/material.dart';
import 'package:xta/plugins/plugin_brand.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/tweet/interleaved_items.dart';
import 'package:xta/ui/contrast.dart';

/// Brand tint for a plugin's provenance strip in a mixed feed.
Color provenanceAccentColor(BuildContext context, String pluginId) {
  final plugin = pluginById(pluginId);
  final brand = plugin?.brandColor ?? Theme.of(context).colorScheme.primary;
  final readable = readableBrandColor(context, brand);
  final surface = Theme.of(context).colorScheme.surface;
  return ensureContrast(readable, surface, minRatio: 2);
}

/// Misskey-style 2px leading strip marking where an interleaved card came from.
Widget provenanceAccent({required BuildContext context, required Color color, required Widget child}) {
  return DecoratedBox(
    decoration: BoxDecoration(
      border: Border(left: BorderSide(color: color, width: 2)),
    ),
    child: child,
  );
}

/// An [InterleavedItem] whose card carries the plugin's provenance strip.
InterleavedItem provenanceInterleavedItem({
  required DateTime date,
  required String pluginId,
  required WidgetBuilder build,
}) {
  return (
    date: date,
    build: (context) =>
        provenanceAccent(context: context, color: provenanceAccentColor(context, pluginId), child: build(context)),
  );
}
