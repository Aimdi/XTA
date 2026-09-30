import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/reading/mixed_feed_definition.dart';
import 'package:xta/reading/mixed_feed_editor.dart';
import 'package:xta/reading/mixed_feed_store.dart';
import 'package:xta/reading/mixed_feed_view.dart';
import 'package:xta/settings/settings_chrome.dart';

/// The reader's mixes, for Reader Tools: add, edit and put them in the order the Home strip shows them.
class MixedFeedSettings extends StatelessWidget {
  final MixedFeedStore? store;
  const MixedFeedSettings({super.key, this.store});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final mixes = store ?? MixedFeedStore.forPrefs(PrefService.of(context, listen: false));
    return SettingsPageScaffold(
      title: l10n.mixed_feeds,
      floatingActionButton: FloatingActionButton.extended(
        key: const ValueKey('mix-new'),
        onPressed: () => openMixedFeedEditor(context),
        icon: const Icon(Icons.add),
        label: Text(l10n.mixed_feed_new),
      ),
      body: ScopedBuilder<MixedFeedStore, List<MixedFeedDefinition>>(
        store: mixes,
        onState: (context, saved) => saved.isEmpty
            ? Padding(padding: const EdgeInsets.all(24), child: Text(l10n.mixed_feed_list_empty))
            : ReorderableListView.builder(
                buildDefaultDragHandles: false,
                padding: const EdgeInsets.only(bottom: 96),
                itemCount: saved.length,
                onReorderItem: (from, to) => mixes.move(from, to),
                itemBuilder: (context, index) => ListTile(
                  key: ValueKey(saved[index].id),
                  leading: const Icon(mixedFeedIcon),
                  title: Text(saved[index].name, maxLines: 2, overflow: TextOverflow.ellipsis),
                  subtitle: Text(
                    saved[index].sources.map((source) => source.label).join(' · '),
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                  ),
                  trailing: ReorderableDragStartListener(
                    index: index,
                    child: const SizedBox.square(dimension: 48, child: Icon(Icons.drag_handle)),
                  ),
                  onTap: () => openMixedFeedEditor(context, mix: saved[index]),
                ),
              ),
      ),
    );
  }
}
