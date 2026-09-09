import 'package:flutter/material.dart';
import 'package:xta/plugins/plugin_bookmarks.dart';
import 'package:xta/saved/saved_source_filter.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/plugins/mastodon/mastodon_store.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/mastodon/mastodon_plugin.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/plugins/plugin_marks.dart';

List<String> mastodonSectionLabels(BuildContext context) {
  final l10n = L10n.of(context);
  return [
    l10n.plugin_mastodon_tab_explore,
    l10n.plugin_mastodon_tab_local,
    l10n.plugin_mastodon_tab_federated,
    l10n.plugin_mastodon_tab_following,
  ];
}

const mastodonSectionIcons = [Icons.explore_outlined, Icons.home_outlined, Icons.public, Icons.people_outline];

class MastodonNavigation extends StatelessWidget {
  final int selected;
  final ValueChanged<int> onSelected;
  const MastodonNavigation({super.key, required this.selected, required this.onSelected});

  @override
  Widget build(BuildContext context) {
    const order = [3, 0, 1, 2];
    final labels = mastodonSectionLabels(context);
    return NavigationBar(
      key: const ValueKey('mastodon-navigation'),
      height: (MediaQuery.textScalerOf(context).scale(14) * 2 + 48).clamp(80, double.infinity),
      selectedIndex: order.indexOf(selected),
      onDestinationSelected: (index) => onSelected(order[index]),
      destinations: [
        for (final index in order)
          NavigationDestination(
            key: ValueKey('mastodon-destination-$index'),
            icon: Icon(mastodonSectionIcons[index]),
            label: labels[index],
          ),
      ],
    );
  }
}

class MastodonClientBar extends StatelessWidget implements PreferredSizeWidget {
  final double height;
  final int selected;
  final VoidCallback onSearch;
  final VoidCallback onSettings;
  const MastodonClientBar({
    super.key,
    this.height = 64,
    required this.selected,
    required this.onSearch,
    required this.onSettings,
  });

  @override
  Size get preferredSize => Size.fromHeight(height);

  @override
  Widget build(BuildContext context) => AppBar(
    toolbarHeight: height,
    titleSpacing: 8,
    title: Row(
      children: [
        pluginMark(MastodonPlugin(), size: 28),
        const SizedBox(width: 10),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _MastodonSourceLabel(selected: selected),
              Text(
                mastodonSectionLabels(context)[selected],
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700),
              ),
            ],
          ),
        ),
      ],
    ),
    actions: mastodonActions(context, onSearch: onSearch, onSettings: onSettings),
  );
}

List<Widget> mastodonActions(
  BuildContext context, {
  required VoidCallback onSearch,
  required VoidCallback onSettings,
}) => [
  IconButton(key: const ValueKey('mastodon-bookmarks'), icon: const Icon(Icons.bookmark_border),
    tooltip: L10n.of(context).saved, onPressed: () => openPluginBookmarks(context, SavedSource.mastodon)),
  IconButton(
    key: const ValueKey('mastodon-search'),
    icon: const Icon(Icons.search),
    tooltip: L10n.of(context).plugin_mastodon_search,
    onPressed: onSearch,
  ),
  IconButton(
    key: const ValueKey('mastodon-settings'),
    icon: const Icon(Icons.tune),
    tooltip: L10n.of(context).settings,
    onPressed: onSettings,
  ),
];

/// A single row inside Home; never adds a second bottom navigation bar.
class MastodonCompactBar extends StatelessWidget {
  final int selected;
  final ValueChanged<int> onSelected;
  final VoidCallback onSearch;
  final VoidCallback onSettings;
  const MastodonCompactBar({
    super.key,
    required this.selected,
    required this.onSelected,
    required this.onSearch,
    required this.onSettings,
  });

  @override
  Widget build(BuildContext context) {
    final labels = mastodonSectionLabels(context);
    return SafeArea(
      top: !PluginEmbedded.maybeOf(context),
      bottom: false,
      child: Material(
        color: Theme.of(context).scaffoldBackgroundColor,
        child: Row(
          key: const ValueKey('mastodon-compact-controls'),
          children: [
            Expanded(
              child: PopupMenuButton<int>(
                key: const ValueKey('mastodon-section-picker'),
                tooltip: L10n.of(context).plugin_mastodon_title,
                initialValue: selected,
                onSelected: onSelected,
                itemBuilder: (context) => [
                  for (var index = 0; index < 4; index++)
                    PopupMenuItem(
                      value: index,
                      child: Row(
                        children: [
                          Icon(mastodonSectionIcons[index]),
                          const SizedBox(width: 12),
                          Expanded(child: Text(labels[index])),
                          if (selected == index) const Icon(Icons.check, size: 18),
                        ],
                      ),
                    ),
                ],
                child: Padding(
                  padding: const EdgeInsetsDirectional.fromSTEB(16, 14, 8, 14),
                  child: Row(
                    children: [
                      Icon(mastodonSectionIcons[selected], size: 22),
                      const SizedBox(width: 10),
                      Flexible(
                        child: Text(
                          labels[selected],
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: Theme.of(context).textTheme.titleSmall,
                        ),
                      ),
                      const Icon(Icons.expand_more, size: 20),
                    ],
                  ),
                ),
              ),
            ),
            ...mastodonActions(context, onSearch: onSearch, onSettings: onSettings),
          ],
        ),
      ),
    );
  }
}

class _MastodonSourceLabel extends StatelessWidget {
  final int selected;
  const _MastodonSourceLabel({required this.selected});

  @override
  Widget build(BuildContext context) {
    Widget label(String value) =>
        Text(value, maxLines: 1, overflow: TextOverflow.ellipsis, style: Theme.of(context).textTheme.labelMedium);
    final title = L10n.of(context).plugin_mastodon_title;
    if (selected != 1 && selected != 2) return label(title);
    final MastodonPublicFeedStore store = selected == 1
        ? context.read<MastodonLocalStore>()
        : context.read<MastodonFederatedStore>();
    return ScopedBuilder<MastodonPublicFeedStore, List<MastodonPost>>(
      store: store,
      onState: (context, _) => label(mastodonInstanceDomain(store.instance ?? '') ?? title),
      onLoading: (_) => label(title),
      onError: (_, _) => label(title),
    );
  }
}
