import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/saved/saved_content_index.dart';

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

class SavedSourceButton extends StatelessWidget {
  final SavedSource selected;
  final ValueChanged<SavedSource> onSelected;
  const SavedSourceButton({super.key, required this.selected, required this.onSelected});
  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    String label(SavedSource source) => switch (source) {
      SavedSource.all => l10n.home_networks_all,
      SavedSource.x => l10n.source_x,
      SavedSource.reddit => l10n.plugin_reddit_title,
      SavedSource.other => l10n.plugin_post_other_sources,
      SavedSource.bluesky => l10n.plugin_bluesky_title,
      SavedSource.mastodon => l10n.plugin_mastodon_title,
    };
    return PopupMenuButton<SavedSource>(
      key: const ValueKey('saved-source-filter'),
      tooltip: l10n.home_networks,
      initialValue: selected,
      onSelected: onSelected,
      itemBuilder: (context) => [
        for (final source in SavedSource.values)
          CheckedPopupMenuItem(value: source, checked: source == selected, child: Text(label(source))),
      ],
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 12),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              Icons.public,
              size: 18,
              color: selected == SavedSource.all ? null : Theme.of(context).colorScheme.primary,
            ),
            const SizedBox(width: 6),
            Text(selected == SavedSource.all ? l10n.home_networks : label(selected)),
            const Icon(Icons.expand_more, size: 16),
          ],
        ),
      ),
    );
  }
}
