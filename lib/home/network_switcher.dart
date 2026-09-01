import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/home/feed_strip_add_sheet.dart';
import 'package:xta/home/network_recents_store.dart';
import 'package:xta/plugins/plugin.dart';
import 'package:xta/plugins/plugin_brand.dart';
import 'package:xta/plugins/plugin_registry.dart';

/// Plugin tabs that stay one tap on the home strip. The rest live in the
/// networks sheet so Following / For you are never squeezed off.
const kHomeStripRecentLimit = 6;

/// Extra plugin destinations on the bottom bar before they collapse into
/// one Networks slot. X pages are never collapsed.
const kBottomBarRecentPlugins = 1;

const Key homeNetworksButtonKey = ValueKey('home-networks');

const Key homeNetworksSheetKey = ValueKey('home-networks-sheet');

Key networkSwitcherRowKey(String pluginId) =>
    ValueKey('network-switcher-$pluginId');

/// Which pinned plugin tabs stay on the strip, in pin order.
///
/// [currentPluginId] is always kept so the open network does not vanish into
/// the overflow. Recency only decides *which* extras stay, not their order,
/// so tapping a visible tab does not shuffle the row.
List<String> recentPluginTabIds({
  required List<String> pinned,
  required List<String> recent,
  String? currentPluginId,
  int limit = kHomeStripRecentLimit,
}) {
  if (pinned.length <= limit) return List<String>.from(pinned);

  final chosen = <String>{};
  void take(String? id) {
    if (id == null || !pinned.contains(id)) return;
    chosen.add(id);
  }

  take(currentPluginId);
  for (final id in recent) {
    if (chosen.length >= limit) break;
    take(id);
  }
  for (final id in pinned) {
    if (chosen.length >= limit) break;
    take(id);
  }
  return [
    for (final id in pinned)
      if (chosen.contains(id)) id,
  ];
}

/// One slot on the bottom bar: a real page, or the Networks overflow.
class BottomBarSlot {
  final int? pageIndex;
  final bool isOverflow;

  const BottomBarSlot.page(this.pageIndex) : isOverflow = false;
  const BottomBarSlot.overflow() : pageIndex = null, isOverflow = true;
}

/// Keeps X destinations (and groups) on the pill; extra plugin pages collapse
/// into one Networks slot so the bar stays tappable.
List<BottomBarSlot> layoutBottomBar(
  List<String> pageIds, {
  String? recentPluginId,
  int pluginLimit = kBottomBarRecentPlugins,
}) {
  final core = <BottomBarSlot>[];
  final plugins = <int>[];
  for (var i = 0; i < pageIds.length; i++) {
    if (pluginById(pageIds[i]) != null) {
      plugins.add(i);
    } else {
      core.add(BottomBarSlot.page(i));
    }
  }
  if (plugins.isEmpty) return core;
  if (plugins.length <= pluginLimit) {
    return [...core, for (final i in plugins) BottomBarSlot.page(i)];
  }

  final recentIndex = recentPluginId == null
      ? null
      : plugins.where((i) => pageIds[i] == recentPluginId).firstOrNull;
  final kept = recentIndex ?? plugins.first;
  return [...core, BottomBarSlot.page(kept), const BottomBarSlot.overflow()];
}

int destinationIndexForPage(List<BottomBarSlot> slots, int pageIndex) {
  for (var i = 0; i < slots.length; i++) {
    if (slots[i].pageIndex == pageIndex) return i;
  }
  final overflow = slots.indexWhere((s) => s.isOverflow);
  return overflow >= 0 ? overflow : 0;
}

/// Pick a network from the ones already on the home strip / bar.
Future<String?> showNetworkSwitcherSheet(
  BuildContext context, {
  required List<XtaPlugin> plugins,
  required String? currentId,
  List<String> recentIds = const [],
}) {
  return showModalBottomSheet<String>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    showDragHandle: true,
    builder: (_) => _NetworkSwitcherSheet(
      plugins: plugins,
      currentId: currentId,
      recentIds: recentIds,
    ),
  );
}

class _NetworkSwitcherSheet extends StatelessWidget {
  final List<XtaPlugin> plugins;
  final String? currentId;
  final List<String> recentIds;

  const _NetworkSwitcherSheet({
    required this.plugins,
    required this.currentId,
    required this.recentIds,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final recent = [
      for (final id in recentIds) ...plugins.where((p) => p.id == id),
    ];
    final rest = [
      for (final plugin in plugins)
        if (!recentIds.contains(plugin.id)) plugin,
    ];

    return SizedBox(
      key: homeNetworksSheetKey,
      height: MediaQuery.sizeOf(context).height * 0.7,
      child: ListView(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 4),
            child: Text(
              l10n.home_networks,
              style: Theme.of(context).textTheme.titleLarge,
            ),
          ),
          if (recent.isNotEmpty) ...[
            _section(context, l10n.home_networks_recent),
            for (final plugin in recent) _row(context, plugin),
          ],
          if (rest.isNotEmpty) ...[
            _section(context, l10n.home_networks_all),
            for (final plugin in rest) _row(context, plugin),
          ],
          const Divider(height: 1),
          ListTile(
            leading: const Icon(Icons.add),
            title: Text(l10n.feed_strip_add),
            onTap: () {
              // The sheet's context dies with the pop. The navigator's
              // context is the parent route and can open the add sheet.
              final nav = Navigator.of(context);
              nav.pop();
              showFeedStripAddSheet(nav.context);
            },
          ),
        ],
      ),
    );
  }

  Widget _section(BuildContext context, String title) {
    return ListTile(
      title: Text(title, style: Theme.of(context).textTheme.titleSmall),
    );
  }

  Widget _row(BuildContext context, XtaPlugin plugin) {
    return ListTile(
      key: networkSwitcherRowKey(plugin.id),
      leading: pluginBrandIcon(context, plugin, size: 28),
      title: Text(plugin.title(context)),
      trailing: plugin.id == currentId ? const Icon(Icons.check) : null,
      onTap: () => Navigator.pop(context, plugin.id),
    );
  }
}

List<XtaPlugin> pluginsForSwitcher(Iterable<String> ids) => [
  for (final id in ids)
    if (pluginById(id) != null) pluginById(id)!,
];

Future<void> rememberNetwork(BuildContext context, String pluginId) async {
  try {
    await context.read<NetworkRecentsStore>().touch(pluginId);
  } on ProviderNotFoundException {
    // Tests and routes without recents still switch.
  }
}
