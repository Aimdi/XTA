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
Future<void> showFeedStripAddSheet(BuildContext context) {
  return showModalBottomSheet<void>(
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
                  final pinnedPlugins = pinned
                      .map(pluginById)
                      .whereType<XtaPlugin>()
                      .where((p) => p.isEnabled(prefs) && p.supportsFeedStrip)
                      .toList(growable: false);

                  if (pinnedPlugins.isEmpty && candidates.isEmpty) {
                    return ListView(
                      padding: const EdgeInsets.all(24),
                      children: [
                        Text(
                          l10n.feed_strip_add_empty,
                          textAlign: TextAlign.center,
                        ),
                        const SizedBox(height: 16),
                        Center(
                          child: FilledButton.icon(
                            onPressed: () {
                              Navigator.pop(context);
                              Navigator.push(
                                context,
                                MaterialPageRoute(
                                  builder: (_) => Scaffold(
                                    appBar: AppBar(
                                      title: Text(l10n.plugin_store),
                                    ),
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

                  return ListView(
                    children: [
                      if (pinnedPlugins.isNotEmpty) ...[
                        ListTile(
                          title: Text(
                            l10n.feed_strip_pinned,
                            style: Theme.of(context).textTheme.titleSmall,
                          ),
                        ),
                        for (final plugin in pinnedPlugins)
                          ListTile(
                            leading: Icon(
                              plugin.icon,
                              color: plugin.brandColor,
                            ),
                            title: Text(plugin.title(context)),
                            trailing: IconButton(
                              tooltip: l10n.feed_strip_remove,
                              icon: const Icon(Icons.remove_circle_outline),
                              onPressed: () async {
                                await strip.ensurePersisted();
                                await strip.remove(plugin.id);
                              },
                            ),
                          ),
                        const Divider(height: 1),
                      ],
                      if (candidates.isNotEmpty) ...[
                        ListTile(
                          title: Text(
                            l10n.feed_strip_available,
                            style: Theme.of(context).textTheme.titleSmall,
                          ),
                        ),
                        for (final plugin in candidates)
                          ListTile(
                            leading: Icon(
                              plugin.icon,
                              color: plugin.brandColor,
                            ),
                            title: Text(plugin.title(context)),
                            trailing: IconButton(
                              tooltip: l10n.feed_strip_add,
                              icon: const Icon(Icons.add_circle_outline),
                              onPressed: () async {
                                await strip.ensurePersisted();
                                await strip.add(plugin.id);
                              },
                            ),
                            onTap: () async {
                              await strip.ensurePersisted();
                              await strip.add(plugin.id);
                            },
                          ),
                      ],
                    ],
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
