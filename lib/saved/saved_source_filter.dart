import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_marks.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/saved/saved_content_index.dart';
import 'package:xta/ui/reader_swipe_navigation.dart';

enum SavedSource { all, x, reddit, mastodon, bluesky, other }

SavedSource savedSourceOf(SavedContent? content) => content?.plugin != null
    ? SavedSource.other
    : content?.bluesky != null
    ? SavedSource.bluesky
    : content?.mastodon != null
    ? SavedSource.mastodon
    : content?.reddit != null
    ? SavedSource.reddit
    : SavedSource.x;

bool matchesSavedSource(SavedContent? content, SavedSource source) =>
    source == SavedSource.all || savedSourceOf(content) == source;

bool savedContentHasMedia(SavedContent? content) =>
    (content?.plugin?.images.isNotEmpty ?? false) ||
    content?.bluesky?.hasMedia == true ||
    content?.mastodon?.hasMedia == true ||
    content?.reddit?.hasVisualMedia == true ||
    (content?.tweet?.extendedEntities?.media?.isNotEmpty ?? false) ||
    (content?.tweet?.entities?.media?.isNotEmpty ?? false);

class SavedSourceStore extends Store<SavedSource> {
  SavedSourceStore() : super(SavedSource.all);
  void select(SavedSource source) => update(source);
}

/// The network's own mark, so the filter reads at a glance like the source picker.
Widget savedSourceMark(SavedSource source, {double size = 20}) => switch (source) {
  SavedSource.all => markIcon(Icons.public, size: size),
  SavedSource.x => pluginMark(coreXPlugin, size: size),
  SavedSource.reddit => _pluginMarkOr(pluginIdReddit, size),
  SavedSource.mastodon => _pluginMarkOr(pluginIdMastodon, size),
  SavedSource.bluesky => _pluginMarkOr(pluginIdBluesky, size),
  SavedSource.other => markIcon(Icons.extension_outlined, size: size),
};

Widget _pluginMarkOr(String id, double size) => switch (pluginById(id)) {
  final plugin? => pluginMark(plugin, size: size),
  null => markIcon(Icons.extension_outlined, size: size),
};

String savedSourceLabel(L10n l10n, SavedSource source) => switch (source) {
  SavedSource.all => l10n.home_networks_all,
  SavedSource.x => l10n.source_x,
  SavedSource.reddit => l10n.plugin_reddit_title,
  SavedSource.other => l10n.plugin_post_other_sources,
  SavedSource.bluesky => l10n.plugin_bluesky_title,
  SavedSource.mastodon => l10n.plugin_mastodon_title,
};

class SavedSourceButton extends StatelessWidget {
  final SavedSource selected;
  final ValueChanged<SavedSource> onSelected;
  const SavedSourceButton({super.key, required this.selected, required this.onSelected});
  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return ReaderSwipeNavigation(
      index: selected.index,
      count: SavedSource.values.length,
      identity: SavedSource,
      onChanged: (index) {
        onSelected(SavedSource.values[index]);
        return true;
      },
      child: PopupMenuButton<SavedSource>(
        key: const ValueKey('saved-source-filter'),
        tooltip: l10n.home_networks,
        initialValue: selected,
        onSelected: onSelected,
        // Drops below the button instead of covering it and half the screen.
        position: PopupMenuPosition.under,
        constraints: const BoxConstraints(minWidth: 200, maxWidth: 280),
        itemBuilder: (context) => [
          for (final source in SavedSource.values)
            PopupMenuItem(
              key: ValueKey('saved-source-${source.name}'),
              value: source,
              height: 48,
              child: _SourceRow(source: source, selected: source == selected),
            ),
        ],
        child: ConstrainedBox(
          constraints: const BoxConstraints(minHeight: 48),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                savedSourceMark(selected, size: 18),
                const SizedBox(width: 8),
                Text(selected == SavedSource.all ? l10n.home_networks : savedSourceLabel(l10n, selected)),
                const Icon(Icons.expand_more, size: 16),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _SourceRow extends StatelessWidget {
  final SavedSource source;
  final bool selected;
  const _SourceRow({required this.source, required this.selected});

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    return Semantics(
      selected: selected,
      child: Row(
        children: [
          savedSourceMark(source),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              savedSourceLabel(L10n.of(context), source),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(fontWeight: selected ? FontWeight.w700 : FontWeight.w400),
            ),
          ),
          if (selected) Icon(Icons.check, size: 18, color: colors.primary),
        ],
      ),
    );
  }
}
