import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/home/home_selection_store.dart';
import 'package:xta/subscriptions/group_identity.dart';
import 'package:xta/subscriptions/widgets/group_unread_badge.dart';
import 'package:xta/tweet/tweet_chrome.dart';

List<SubscriptionGroup> drawerGroupsForQuery(List<SubscriptionGroup> groups, String query) {
  final needle = query.trim().toLowerCase();
  final matches = groups.where((group) => group.name.toLowerCase().contains(needle));
  return [
    matches.where((group) => group.pinned),
    matches.where((group) => !group.pinned),
  ].expand((rows) => rows).toList();
}

class HomeGroupDrawer extends StatefulWidget {
  final Widget accountHeader;
  final List<SubscriptionGroup> groups;
  final Set<String> unreadIds;
  final VoidCallback onSearch;
  final VoidCallback onSettings;
  final ValueChanged<SubscriptionGroup> onGroup;
  const HomeGroupDrawer({
    super.key,
    required this.accountHeader,
    required this.groups,
    required this.unreadIds,
    required this.onSearch,
    required this.onSettings,
    required this.onGroup,
  });
  @override
  State<HomeGroupDrawer> createState() => _HomeGroupDrawerState();
}

class _HomeGroupDrawerState extends State<HomeGroupDrawer> {
  final _query = HomeSelectionStore('');
  @override
  void dispose() {
    _query.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    return Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(context).bottom),
      child: CustomScrollView(
        key: const PageStorageKey('home-group-drawer-scroll'),
        keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
        slivers: [
          SliverToBoxAdapter(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                widget.accountHeader,
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  child: Row(
                    children: [
                      Expanded(
                        child: OutlinedButton.icon(
                          onPressed: widget.onSearch,
                          icon: const Icon(Icons.search),
                          label: Text(l10n.search),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: OutlinedButton.icon(
                          onPressed: widget.onSettings,
                          icon: const Icon(Icons.settings_outlined),
                          label: Text(l10n.settings),
                        ),
                      ),
                    ],
                  ),
                ),
                Padding(
                  padding: const EdgeInsetsDirectional.fromSTEB(20, 24, 20, 12),
                  child: Row(
                    children: [
                      Expanded(
                        child: Semantics(
                          header: true,
                          child: Text(
                            l10n.groups,
                            style: theme.textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w700),
                          ),
                        ),
                      ),
                      Text(
                        '${widget.groups.length}',
                        style: theme.textTheme.labelLarge?.copyWith(color: tweetSecondaryColor(context)),
                      ),
                    ],
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
                  child: TextField(
                    key: const ValueKey('home-drawer-group-search'),
                    onChanged: _query.select,
                    decoration: InputDecoration(
                      hintText: l10n.search,
                      prefixIcon: const Icon(Icons.search, size: 20),
                      filled: true,
                      fillColor: theme.colorScheme.surfaceContainerLow,
                      border: OutlineInputBorder(borderRadius: BorderRadius.circular(14), borderSide: BorderSide.none),
                    ),
                  ),
                ),
                const Divider(height: 1),
              ],
            ),
          ),
          ScopedBuilder<HomeSelectionStore<String>, String>(
            store: _query,
            onState: (context, query) {
              final groups = drawerGroupsForQuery(widget.groups, query);
              if (groups.isEmpty)
                return SliverToBoxAdapter(
                  child: Padding(
                    padding: const EdgeInsets.all(24),
                    child: Text(widget.groups.isEmpty ? l10n.no_subscription_groups_yet : l10n.no_results),
                  ),
                );
              return SliverPadding(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
                sliver: SliverList.builder(
                  itemCount: groups.length,
                  itemBuilder: (context, index) => _groupTile(context, groups[index]),
                ),
              );
            },
          ),
        ],
      ),
    );
  }

  Widget _groupTile(BuildContext context, SubscriptionGroup group) => ListTile(
    key: ValueKey('drawer-group-${group.id}'),
    minTileHeight: 76,
    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
    leading: Semantics(
      label: widget.unreadIds.contains(group.id) ? L10n.of(context).group_has_unread : null,
      child: GroupUnreadBadge(unread: widget.unreadIds.contains(group.id), child: GroupMark.forGroup(group, size: 44)),
    ),
    title: Text(
      group.name,
      maxLines: 2,
      overflow: TextOverflow.ellipsis,
      style: const TextStyle(fontWeight: FontWeight.w600),
    ),
    subtitle: Text(
      L10n.of(context).subscription_group_member_count(group.numberOfMembers),
      style: tweetMetadataStyle(context),
    ),
    trailing: group.pinned
        ? Icon(Icons.push_pin_outlined, size: 18, color: tweetReadableAccentColor(context))
        : Icon(Icons.chevron_right, size: 20, color: tweetSecondaryColor(context)),
    onTap: () => widget.onGroup(group),
  );
}
