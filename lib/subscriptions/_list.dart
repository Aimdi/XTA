import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/search/search.dart';
import 'package:xta/subscriptions/_import.dart';
import 'package:xta/subscriptions/subscription_look.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/user.dart';
import 'package:provider/provider.dart';
import 'package:xta/ui/x_controls.dart';

class SubscriptionUsersPage extends StatefulWidget {
  final ScrollController scrollController;

  const SubscriptionUsersPage({super.key, required this.scrollController});

  @override
  State<SubscriptionUsersPage> createState() => _SubscriptionUsersPageState();
}

class _SubscriptionUsersPageState extends State<SubscriptionUsersPage> {
  final TextEditingController _searchController = TextEditingController();

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  Widget _emptyState(BuildContext context, {required String message, bool showImport = false}) {
    return ListView(
      controller: widget.scrollController,
      padding: const EdgeInsets.fromLTRB(24, 48, 24, 24),
      children: [
        Icon(Icons.people_outline, size: 48, color: Theme.of(context).colorScheme.outline),
        const SizedBox(height: 16),
        Text(
          message,
          textAlign: TextAlign.center,
          style: Theme.of(context).textTheme.titleMedium,
        ),
        if (showImport) ...[
          const SizedBox(height: 24),
          Center(
            child: FilledButton.icon(
              style: xPrimaryPillStyle(context),
              onPressed: () {
                Navigator.push(
                  context,
                  MaterialPageRoute(builder: (_) => const SubscriptionImportScreen()),
                );
              },
              icon: const Icon(Icons.cloud_download_outlined),
              label: Text(L10n.of(context).import_subscriptions),
            ),
          ),
          const SizedBox(height: 12),
          Center(
            child: TextButton.icon(
              onPressed: () {
                Navigator.pushNamed(
                  context,
                  routeSearch,
                  arguments: SearchArguments(1, focusInputOnOpen: true),
                );
              },
              icon: const Icon(Icons.search),
              label: Text(L10n.of(context).search),
            ),
          ),
        ],
      ],
    );
  }

  Widget _buildSearchBar(BuildContext context) {
    return ColoredBox(
      color: Theme.of(context).scaffoldBackgroundColor,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(12, 8, 12, 8),
        child: XSearchField(
          controller: _searchController,
          hintText: L10n.of(context).search_subscriptions,
          onChanged: (_) => setState(() {}),
        ),
      ),
    );
  }

  Widget _buildReorderableSliver(BuildContext context, List<Subscription> state) {
    final prefs = PrefService.of(context);
    final String orderCustom = prefs.get(optionSubscriptionOrderCustom);

    final subLst = <Subscription>[];
    if (orderCustom.isNotEmpty) {
      final byName = {for (final s in state) s.screenName: s};
      for (final sn in orderCustom.split(',')) {
        final match = byName[sn];
        if (match != null) subLst.add(match);
      }
      for (final s in state) {
        if (!subLst.contains(s)) subLst.add(s);
      }
    } else {
      subLst.addAll(state);
    }

    return SliverReorderableList(
      itemCount: subLst.length,
      itemBuilder: (context, i) => ReorderableDelayedDragStartListener(
        key: ValueKey(subLst[i].screenName),
        index: i,
        child: buildSubscriptionTile(context, subLst[i]),
      ),
      onReorderItem: (oldIndex, newIndex) async {
        final s = subLst.removeAt(oldIndex);
        subLst.insert(newIndex, s);
        final lst = subLst.map((s) => s.screenName).join(',');
        await prefs.set(optionSubscriptionOrderCustom, lst);
      },
    );
  }

  Widget _buildFilteredSliver(BuildContext context, List<Subscription> state, String query) {
    final filtered = state
        .where(
          (s) => s.name.toLowerCase().contains(query) || s.screenName.toLowerCase().contains(query),
        )
        .toList();
    if (filtered.isEmpty) {
      return SliverFillRemaining(
        hasScrollBody: false,
        child: Center(child: Text(L10n.of(context).no_results)),
      );
    }
    return SliverList.builder(
      itemCount: filtered.length,
      itemBuilder: (context, i) => buildSubscriptionTile(context, filtered[i]),
    );
  }

  @override
  Widget build(BuildContext context) {
    final model = context.read<SubscriptionsModel>();

    return ScopedBuilder<SubscriptionsModel, List<Subscription>>(
      store: model,
      onLoading: (_) => const Center(child: CircularProgressIndicator()),
      onError: (_, e) => FullPageErrorWidget(
        error: e,
        stackTrace: null,
        prefix: L10n.of(context).unable_to_refresh_the_subscriptions,
      ),
      onState: (_, state) {
        if (state.isEmpty) {
          return _emptyState(
            context,
            message: L10n.of(context).no_subscriptions_try_searching_or_importing_some,
            showImport: true,
          );
        }

        final query = _searchController.text.toLowerCase();
        return Scrollbar(
          controller: widget.scrollController,
          interactive: true,
          child: CustomScrollView(
            controller: widget.scrollController,
            slivers: [
              SliverPersistentHeader(
                pinned: true,
                delegate: _SearchHeaderDelegate(child: _buildSearchBar(context)),
              ),
              SliverPadding(
                padding: EdgeInsets.fromLTRB(8, 0, 8, 16 + MediaQuery.paddingOf(context).bottom),
                sliver: query.isEmpty
                    ? _buildReorderableSliver(context, state)
                    : _buildFilteredSliver(context, state, query),
              ),
            ],
          ),
        );
      },
    );
  }
}

class _SearchHeaderDelegate extends SliverPersistentHeaderDelegate {
  final Widget child;

  _SearchHeaderDelegate({required this.child});

  @override
  double get minExtent => 64;

  @override
  double get maxExtent => 64;

  @override
  Widget build(BuildContext context, double shrinkOffset, bool overlapsContent) => child;

  @override
  bool shouldRebuild(covariant _SearchHeaderDelegate oldDelegate) => child != oldDelegate.child;
}

/// Back-compat wrapper for the people list as a sliver group.
class SubscriptionUsers extends StatelessWidget {
  const SubscriptionUsers({super.key});

  @override
  Widget build(BuildContext context) {
    return const SliverToBoxAdapter(child: SizedBox.shrink());
  }
}

/// One row of the subscriptions list, whichever network it belongs to.
///
/// Everything that was not an X account used to land in the saved-search row
/// below: a followed subreddit wore a search icon, said it was a search term,
/// and opened X's search for its own name. Each row now carries its own
/// network's mark and leads back to that network.
Widget buildSubscriptionTile(BuildContext context, Subscription user) {
  if (user is UserSubscription) {
    return UserTile(key: Key(user.screenName), user: user);
  }

  final destination = subscriptionDestination(user);

  return ListTile(
    key: Key(user.screenName),
    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12.0)),
    leading: subscriptionAvatar(user),
    title: Text(user.name, maxLines: 1, overflow: TextOverflow.ellipsis),
    subtitle: Text(subscriptionSubtitle(user), maxLines: 1, overflow: TextOverflow.ellipsis),
    trailing: FollowButton(user: user),
    onTap: () {
      if (destination != null) {
        Navigator.push(context, MaterialPageRoute(builder: (_) => destination()));
        return;
      }
      Navigator.pushNamed(
        context,
        routeSearch,
        arguments: SearchArguments(0, focusInputOnOpen: false, query: user.id),
      );
    },
  );
}
