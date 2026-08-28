import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/home/feed_strip_store.dart';
import 'package:xta/plugins/plugin.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/settings/_plugin_store.dart';

/// Pick which installed plugin timelines sit next to For you.
///
/// Returns the plugin id that was just pinned (or selected from the pinned
/// list) so the caller can switch to it. Removing a pin returns null.
Future<String?> showFeedStripAddSheet(BuildContext context) {
  return showModalBottomSheet<String>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    builder: (_) => const _FeedStripAddSheet(),
  );
}

class _FeedStripAddSheet extends StatelessWidget {
  const _FeedStripAddSheet();

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final strip = context.read<FeedStripStore>();
    final prefs = PrefService.of(context, listen: false);
    final bottom = MediaQuery.viewInsetsOf(context).bottom;

    return Padding(
      padding: EdgeInsets.only(bottom: bottom),
      child: SizedBox(
        height: MediaQuery.sizeOf(context).height * 0.7,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 4),
              child: Text(
                l10n.feed_strip_add_title,
                style: Theme.of(context).textTheme.titleLarge,
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
              child: Text(
                l10n.feed_strip_add_intro,
                style: Theme.of(context).textTheme.bodyMedium,
              ),
            ),
            Expanded(
              child: ScopedBuilder<FeedStripStore, List<String>>(
                store: strip,
                onState: (context, pinned) {
                  final candidates = feedStripCandidates(prefs, pinned);
                  final pinnedPlugins = feedStripVisibleIds(prefs, pinned)
                      .map(pluginById)
                      .whereType<XtaPlugin>()
                      .where((p) => p.isEnabled(prefs) && p.supportsFeedStrip)
                      .toList(growable: false);

                  if (pinnedPlugins.isEmpty && candidates.isEmpty) {
                    return _FeedStripEmpty(l10n: l10n);
                  }

                  if (pinnedPlugins.isEmpty) {
                    return ListView(
                      children: _availableSection(context, l10n, candidates),
                    );
                  }

                  return ReorderableListView.builder(
                    buildDefaultDragHandles: false,
                    header: ListTile(
                      title: Text(
                        l10n.feed_strip_pinned,
                        style: Theme.of(context).textTheme.titleSmall,
                      ),
                    ),
                    footer: candidates.isEmpty
                        ? null
                        : Column(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              const Divider(height: 1),
                              ..._availableSection(
                                context,
                                l10n,
                                candidates,
                              ),
                            ],
                          ),
                    itemCount: pinnedPlugins.length,
                    onReorderItem: (oldIndex, newIndex) async {
                      await strip.ensurePersisted();
                      await strip.setPlugins(
                        reorderFeedStripIds(
                          [for (final plugin in pinnedPlugins) plugin.id],
                          oldIndex,
                          newIndex,
                        ),
                      );
                    },
                    itemBuilder: (context, i) {
                      final plugin = pinnedPlugins[i];
                      return _PinnedPluginTile(
                        key: ValueKey(plugin.id),
                        plugin: plugin,
                        index: i,
                        canRemove: plugin.showsHomeTab(prefs),
                        onSelect: () => Navigator.pop(context, plugin.id),
                        onRemove: () async {
                          await strip.ensurePersisted();
                          await strip.remove(plugin.id);
                        },
                      );
                    },
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }
}

Future<void> _pinAndClose(BuildContext context, String pluginId) async {
  final strip = context.read<FeedStripStore>();
  await strip.ensurePersisted();
  await strip.add(pluginId);
  if (context.mounted) Navigator.pop(context, pluginId);
}

List<Widget> _availableSection(
  BuildContext context,
  L10n l10n,
  List<XtaPlugin> candidates,
) {
  return [
    ListTile(
      title: Text(
        l10n.feed_strip_available,
        style: Theme.of(context).textTheme.titleSmall,
      ),
    ),
    for (final plugin in candidates)
      ListTile(
        leading: Icon(plugin.icon, color: plugin.brandColor),
        title: Text(plugin.title(context)),
        trailing: IconButton(
          tooltip: l10n.feed_strip_add,
          icon: const Icon(Icons.add_circle_outline),
          onPressed: () => _pinAndClose(context, plugin.id),
        ),
        onTap: () => _pinAndClose(context, plugin.id),
      ),
  ];
}

class _FeedStripEmpty extends StatelessWidget {
  final L10n l10n;

  const _FeedStripEmpty({required this.l10n});

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(24),
      children: [
        Text(l10n.feed_strip_add_empty, textAlign: TextAlign.center),
        const SizedBox(height: 16),
        Center(
          child: FilledButton.icon(
            onPressed: () {
              final nav = Navigator.of(context);
              final storeTitle = l10n.plugin_store;
              nav.pop();
              nav.push(
                MaterialPageRoute(
                  builder: (_) => Scaffold(
                    appBar: AppBar(title: Text(storeTitle)),
                    body: const SettingsPluginStoreFragment(),
                  ),
                ),
              );
            },
            icon: const Icon(Icons.extension_outlined),
            label: Text(l10n.plugin_store),
          ),
        ),
      ],
    );
  }
}

class _PinnedPluginTile extends StatelessWidget {
  final XtaPlugin plugin;
  final int index;
  final bool canRemove;
  final VoidCallback onSelect;
  final VoidCallback onRemove;

  const _PinnedPluginTile({
    super.key,
    required this.plugin,
    required this.index,
    required this.canRemove,
    required this.onSelect,
    required this.onRemove,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return ListTile(
      onTap: onSelect,
      leading: Icon(plugin.icon, color: plugin.brandColor),
      title: Text(plugin.title(context)),
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (canRemove)
            IconButton(
              tooltip: l10n.feed_strip_remove,
              icon: const Icon(Icons.remove_circle_outline),
              onPressed: onRemove,
            ),
          ReorderableDragStartListener(
            index: index,
            child: Tooltip(
              message: l10n.feed_strip_reorder,
              child: const Padding(
                padding: EdgeInsets.symmetric(horizontal: 8),
                child: Icon(Icons.drag_handle),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
