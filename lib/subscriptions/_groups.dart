import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart' show ScrollCacheExtent;
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/group/group_screen.dart';
import 'package:xta/group/group_tree.dart';
import 'package:xta/subscriptions/_group_list_item.dart';
import 'package:xta/subscriptions/_groups_edit.dart';
import 'package:xta/subscriptions/widgets/group_tile.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/ui/x_controls.dart';
import 'package:provider/provider.dart';
import 'package:xta/plugins/plugin.dart';
import 'package:xta/subscriptions/plugin_feed_chips.dart';

export 'package:xta/subscriptions/_groups_edit.dart'
    show openSubscriptionGroupDialog, SubscriptionGroupEditDialog;

/// Tiles past this index appear without the entrance stagger.
const _staggerLimit = 12;

/// The Groups tab: a board of member-faced tiles with search, plus a
/// drag-to-reorder list while custom ordering is active.
class SubscriptionGroupsPage extends StatefulWidget {
  final ScrollController scrollController;

  const SubscriptionGroupsPage({super.key, required this.scrollController});

  @override
  State<SubscriptionGroupsPage> createState() => _SubscriptionGroupsPageState();
}

class _SubscriptionGroupsPageState extends State<SubscriptionGroupsPage> {
  final TextEditingController _searchController = TextEditingController();

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  Widget _buildEmptyState(BuildContext context) {
    return ListView(
      controller: widget.scrollController,
      padding: const EdgeInsets.fromLTRB(24, 48, 24, 24),
      children: [
        Icon(
          Icons.workspaces_outlined,
          size: 48,
          color: Theme.of(context).colorScheme.outline,
        ),
        const SizedBox(height: 16),
        Text(
          L10n.of(context).no_subscription_groups_yet,
          textAlign: TextAlign.center,
          style: Theme.of(context).textTheme.titleMedium,
        ),
        const SizedBox(height: 8),
        Text(
          L10n.of(context).no_subscription_groups_description,
          textAlign: TextAlign.center,
        ),
        const SizedBox(height: 24),
        Center(
          child: FilledButton.icon(
            style: xPrimaryPillStyle(context),
            onPressed: () => openSubscriptionGroupDialog(
              context,
              null,
              '',
              defaultGroupIcon,
            ),
            icon: const Icon(Icons.add),
            label: Text(L10n.of(context).create_subscription_group),
          ),
        ),
      ],
    );
  }

  Widget _buildSearchBar(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(12, 8, 12, 8),
      child: XSearchField(
        controller: _searchController,
        hintText: L10n.of(context).search,
        onChanged: (_) => setState(() {}),
      ),
    );
  }

  /// The board: a compact grid of member-faced tiles.
  Widget _buildBoard(
    BuildContext context,
    List<SubscriptionGroup> groups, {
    required List<Widget> header,
    required bool animate,
  }) {
    final prefs = PrefService.of(context);
    final columns = (prefs.get<int>(optionSubscriptionGroupsColumns) ?? 2)
        .clamp(2, 3);

    // Large text needs taller tiles, or the title and count would squeeze the
    // avatar mosaic out of the tile entirely.
    final textScale = MediaQuery.textScalerOf(
      context,
    ).scale(1.0).clamp(1.0, 2.0);
    final baseRatio = columns == 2 ? 168 / 132 : 1.0;
    final aspectRatio = baseRatio / (1 + (textScale - 1) * 0.55);
    final parts = partitionNsfwGroups(groups, (g) => g.nsfw);

    SliverGrid gridFor(
      List<SubscriptionGroup> items, {
      required int staggerBase,
    }) {
      return SliverGrid(
        gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
          crossAxisCount: columns,
          mainAxisSpacing: 10,
          crossAxisSpacing: 10,
          childAspectRatio: aspectRatio,
        ),
        delegate: SliverChildBuilderDelegate((context, index) {
          final group = items[index];
          final staggerIndex = staggerBase + index;
          final tile = GroupTile(
            key: ValueKey(group.id),
            group: group,
            animate: animate,
            onTap: () => Navigator.pushNamed(
              context,
              routeGroup,
              arguments: GroupScreenArguments(id: group.id, name: group.name),
            ),
            onLongPress: () => openSubscriptionGroupDialog(
              context,
              group.id,
              group.name,
              group.icon,
            ),
          );

          if (!animate || staggerIndex >= _staggerLimit) {
            return tile;
          }
          return _StaggeredEntrance(
            delay: Duration(milliseconds: 20 * staggerIndex),
            child: tile,
          );
        }, childCount: items.length),
      );
    }

    return CustomScrollView(
      controller: widget.scrollController,
      // Build a row ahead so avatars decode before they scroll into view.
      scrollCacheExtent: const ScrollCacheExtent.pixels(300),
      slivers: [
        SliverToBoxAdapter(child: Column(children: header)),
        if (parts.safe.isNotEmpty)
          SliverPadding(
            padding: EdgeInsets.fromLTRB(
              12,
              10,
              12,
              parts.nsfw.isEmpty ? 24 : 8,
            ),
            sliver: gridFor(parts.safe, staggerBase: 0),
          ),
        if (parts.nsfw.isNotEmpty) ...[
          SliverToBoxAdapter(child: _CensoredSectionHeader()),
          SliverPadding(
            padding: const EdgeInsets.fromLTRB(12, 4, 12, 24),
            sliver: gridFor(parts.nsfw, staggerBase: parts.safe.length),
          ),
        ],
      ],
    );
  }

  Widget _buildReorderableList(
    BuildContext context,
    List<SubscriptionGroup> groups, {
    required List<Widget> header,
    required bool canReorder,
    Map<String, int> depths = const {},
  }) {
    final parts = partitionNsfwGroups(groups, (g) => g.nsfw);
    // One flat list so drag still works: safe groups, then a non-draggable
    // Censored header, then NSFW. Positions persist for groups only.
    final rows = <_ListRow>[
      for (final g in parts.safe) _ListRow.group(g),
      if (parts.nsfw.isNotEmpty) const _ListRow.header(),
      for (final g in parts.nsfw) _ListRow.group(g),
    ];

    return ReorderableListView.builder(
      scrollController: widget.scrollController,
      header: header.isEmpty ? null : Column(children: header),
      padding: const EdgeInsets.fromLTRB(8, 4, 8, 24),
      buildDefaultDragHandles: false,
      itemCount: rows.length,
      itemBuilder: (context, index) {
        final row = rows[index];
        if (row.isHeader) {
          return const _CensoredSectionHeader(key: ValueKey('censored-header'));
        }
        final group = row.group!;
        return GroupListItem(
          key: ValueKey(group.id),
          group: group,
          depth: depths[group.id] ?? 0,
          // Drag only within the same NSFW bucket so a pull cannot lift a
          // censored group above the section header.
          reorderIndex: canReorder ? index : null,
          onLongPress: () => openSubscriptionGroupDialog(
            context,
            group.id,
            group.name,
            group.icon,
          ),
        );
      },
      onReorderItem: (oldIndex, newIndex) {
        final headerAt = parts.nsfw.isEmpty ? -1 : parts.safe.length;
        if (oldIndex == headerAt) {
          return;
        }

        // onReorderItem already adjusts newIndex for the removed item. Keep
        // the drag inside its NSFW bucket so nothing crosses Censored.
        var to = newIndex;
        if (headerAt >= 0) {
          if (oldIndex < headerAt) {
            if (to > headerAt - 1) {
              to = headerAt - 1;
            }
          } else if (to <= headerAt) {
            to = headerAt + 1;
          }
        }

        final next = List<_ListRow>.from(rows);
        final moved = next.removeAt(oldIndex);
        if (moved.isHeader) {
          return;
        }
        next.insert(to.clamp(0, next.length), moved);
        final ids = next
            .where((r) => !r.isHeader)
            .map((r) => r.group!.id)
            .toList();
        context.read<GroupsModel>().saveGroupPositions(ids);
      },
    );
  }

  /// Feeds a plugin provides, listed with the groups so they are reachable from
  /// where feeds live. Only shown once a plugin has given up its own home tab,
  /// otherwise the same feed would have two entry points.
  List<Widget> _pluginFeedChips(BuildContext context) {
    final plugins = pluginFeedsOnGroupsTab(PrefService.of(context));
    if (plugins.isEmpty) {
      return const [];
    }

    return [
      Padding(
        padding: const EdgeInsets.fromLTRB(12, 4, 12, 8),
        child: Wrap(
          spacing: 6,
          runSpacing: 8,
          crossAxisAlignment: WrapCrossAlignment.center,
          children: [
            for (final plugin in plugins)
              PluginFeedChip(
                key: pluginFeedChipKey(plugin.id),
                plugin: plugin,
                onTap: () => Navigator.push(
                  context,
                  MaterialPageRoute<void>(
                    builder: (_) => _PluginFeedRoute(plugin: plugin),
                  ),
                ),
              ),
          ],
        ),
      ),
      const Divider(height: 1),
    ];
  }

  @override
  Widget build(BuildContext context) {
    return ScopedBuilder<GroupsModel, List<SubscriptionGroup>>.transition(
      store: context.read<GroupsModel>(),
      onError: (_, error) => FullPageErrorWidget(
        error: error,
        stackTrace: null,
        prefix: L10n.of(context).unable_to_load_the_group,
        onRetry: () => context.read<GroupsModel>().reloadGroups(),
      ),
      onState: (_, state) {
        if (state.isEmpty) {
          return _buildEmptyState(context);
        }

        final query = _searchController.text.toLowerCase();
        var groups = query.isEmpty
            ? state
            : state
                  .where((g) => g.name.toLowerCase().contains(query))
                  .toList(growable: false);

        // A nested group sits under its parent rather than beside it — but it
        // is still shown. Hiding it made "put inside group" look like a delete.
        // While searching the order is left alone: the reader is looking for one
        // group and should find it wherever it lives.
        final parents = {for (final g in state) g.id: g.parentId};
        if (query.isEmpty) {
          final byId = {for (final g in groups) g.id: g};
          groups = groupsInTreeOrder(
            byId.keys,
            parents,
          ).map((id) => byId[id]!).toList(growable: false);
        }
        final prefs = PrefService.of(context);
        final animate = prefs.get<bool>(optionDisableAnimations) != true;
        final asList =
            prefs.get<String>(optionSubscriptionGroupsLayout) ==
            subscriptionGroupsLayoutList;
        // Dragging tiles around a grid is far fiddlier than dragging rows, so
        // only the list carries drag handles — and only when the order it would
        // rearrange is the one being shown.
        final canReorder =
            context.read<GroupsModel>().orderGroupsBy == 'position' &&
            query.isEmpty;
        // How far each group is indented, so a nested one reads as nested.
        final depths = {for (final g in groups) g.id: depthOf(g.id, parents)};

        // The search field and the plugin feeds scroll away with the groups
        // rather than sitting above them. They used to be fixed children of a
        // Column with the grid in an Expanded below, so tiles slid under them
        // and were sliced off mid-card at the top of the viewport.
        final header = [
          if (state.length > 5) _buildSearchBar(context),
          ..._pluginFeedChips(context),
        ];

        return asList
            ? _buildReorderableList(
                context,
                groups,
                header: header,
                canReorder: canReorder,
                depths: depths,
              )
            : _buildBoard(context, groups, header: header, animate: animate);
      },
    );
  }
}

/// One row in the groups list: a real group, or the Censored section divider.
class _ListRow {
  final SubscriptionGroup? group;
  final bool isHeader;

  const _ListRow.group(this.group) : isHeader = false;
  const _ListRow.header() : group = null, isHeader = true;
}

/// Label above the NSFW group tiles / rows.
class _CensoredSectionHeader extends StatelessWidget {
  const _CensoredSectionHeader({super.key});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
      child: Row(
        children: [
          Icon(
            Icons.visibility_off_outlined,
            size: 18,
            color: theme.colorScheme.outline,
          ),
          const SizedBox(width: 8),
          Text(
            L10n.of(context).censored,
            style: theme.textTheme.titleSmall?.copyWith(
              color: theme.colorScheme.outline,
              fontWeight: FontWeight.w600,
              letterSpacing: 0.2,
            ),
          ),
        ],
      ),
    );
  }
}

/// Fades and lifts a tile into place once, shortly after first build.
class _StaggeredEntrance extends StatefulWidget {
  final Duration delay;
  final Widget child;

  const _StaggeredEntrance({required this.delay, required this.child});

  @override
  State<_StaggeredEntrance> createState() => _StaggeredEntranceState();
}

class _StaggeredEntranceState extends State<_StaggeredEntrance> {
  bool _shown = false;
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    _timer = Timer(widget.delay, () {
      if (mounted) setState(() => _shown = true);
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedSlide(
      offset: _shown ? Offset.zero : const Offset(0, 0.06),
      duration: const Duration(milliseconds: 220),
      curve: Curves.easeOutCubic,
      child: AnimatedOpacity(
        opacity: _shown ? 1 : 0,
        duration: const Duration(milliseconds: 220),
        curve: Curves.easeOutCubic,
        child: widget.child,
      ),
    );
  }
}

/// Legacy embed point; prefer [SubscriptionGroupsPage].
class SubscriptionGroups extends StatelessWidget {
  final ScrollController scrollController;

  const SubscriptionGroups({super.key, required this.scrollController});

  @override
  Widget build(BuildContext context) {
    return SubscriptionGroupsPage(scrollController: scrollController);
  }
}

/// Hosts a plugin's feed screen as a pushed route, for plugins that no longer
/// occupy a home tab. The screen brings its own app bar.
class _PluginFeedRoute extends StatefulWidget {
  final XtaPlugin plugin;

  const _PluginFeedRoute({required this.plugin})
    : super(key: pluginFeedRouteKey);

  @override
  State<_PluginFeedRoute> createState() => _PluginFeedRouteState();
}

class _PluginFeedRouteState extends State<_PluginFeedRoute> {
  final ScrollController _scrollController = ScrollController();

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return widget.plugin.homeScreen(scrollController: _scrollController) ??
        const SizedBox.shrink();
  }
}
