import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/home/home_account_filter.dart';
import 'package:xta/home/home_group_drawer.dart';
import 'package:xta/home/home_group_filter.dart';
import 'package:xta/home/home_selection_store.dart';
import 'package:xta/ui/reader_failure.dart';

class HomeFilterDraft {
  final Set<String> accounts;
  final Set<String> groups;
  HomeFilterDraft(Set<String> accounts, Set<String> groups)
    : accounts = Set.unmodifiable(accounts),
      groups = Set.unmodifiable(groups);
}

class HomeFilterDraftStore extends Store<HomeFilterDraft> {
  HomeFilterDraftStore(Set<String> accounts, Set<String> groups) : super(HomeFilterDraft(accounts, groups));

  void account(String id, bool enabled, List<Account> accounts) {
    if (isLoading || (!enabled && !canDisableHomeAccount(id, accounts, state.accounts))) return;
    final next = {...state.accounts};
    enabled ? next.remove(id) : next.add(id);
    update(HomeFilterDraft(next, state.groups));
  }

  void group(String id, bool enabled) {
    if (isLoading) return;
    final next = {...state.groups};
    enabled ? next.remove(id) : next.add(id);
    update(HomeFilterDraft(state.accounts, next));
  }

  void reset() {
    if (!isLoading) update(HomeFilterDraft({}, {}));
  }

  Future<bool> apply(HomeAccountFilterStore accounts, HomeGroupFilterStore? groups) async {
    if (isLoading) return false;
    setLoading(true);
    final previous = homeFeedDisabledIdsToPrefs(accounts.state);
    try {
      await accounts.prefs.set(optionHomeFeedDisabledAccountIds, homeFeedDisabledIdsToPrefs(state.accounts));
      try {
        await groups?.prefs.set(optionHomeFeedDisabledGroupIds, homeFeedDisabledIdsToPrefs(state.groups));
      } catch (_) {
        await accounts.prefs.set(optionHomeFeedDisabledAccountIds, previous);
        rethrow;
      }
      accounts.publishDisabled(state.accounts);
      groups?.update(state.groups);
      return true;
    } catch (error) {
      setError(error, force: true);
      return false;
    } finally {
      setLoading(false);
    }
  }
}

class HomeFilterSheet extends StatefulWidget {
  final List<Account> accounts;
  final List<SubscriptionGroup> groups;
  final HomeAccountFilterStore accountsStore;
  final HomeGroupFilterStore? groupsStore;
  final VoidCallback onAddAccount;
  final VoidCallback? onChanged;
  const HomeFilterSheet({
    super.key,
    required this.accounts,
    required this.groups,
    required this.accountsStore,
    required this.groupsStore,
    required this.onAddAccount,
    this.onChanged,
  });
  @override
  State<HomeFilterSheet> createState() => _HomeFilterSheetState();
}

class _HomeFilterSheetState extends State<HomeFilterSheet> {
  final _section = HomeSelectionStore(false);
  final _query = HomeSelectionStore('');
  final _searchController = TextEditingController();
  late final _draft = HomeFilterDraftStore(widget.accountsStore.state, widget.groupsStore?.state ?? {});

  @override
  void dispose() {
    _section.destroy();
    _query.destroy();
    _draft.destroy();
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _apply() async {
    final applied = await _draft.apply(widget.accountsStore, widget.groupsStore);
    if (!mounted || !applied) return;
    widget.onChanged?.call();
    Navigator.pop(context);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return TripleBuilder<HomeFilterDraftStore, HomeFilterDraft>(
      store: _draft,
      builder: (context, triple) => PopScope(
        canPop: !triple.isLoading,
        child: SafeArea(
          top: false,
          child: LayoutBuilder(
            builder: (context, constraints) {
              final keyboard = MediaQuery.viewInsetsOf(context).bottom;
              final available = (constraints.maxHeight - keyboard).clamp(0.0, double.infinity).toDouble();
              return Padding(
                padding: EdgeInsets.only(bottom: keyboard),
                child: SizedBox(
                  height: available * .9,
                  child: Column(
                    children: [
                      Expanded(
                        child: IgnorePointer(
                          ignoring: triple.isLoading,
                          child: ScopedBuilder<HomeSelectionStore<bool>, bool>(
                            store: _section,
                            onState: (_, groups) => ScopedBuilder<HomeSelectionStore<String>, String>(
                              store: _query,
                              onState: (_, query) => _list(context, groups, query, triple.state),
                            ),
                          ),
                        ),
                      ),
                      if (triple.error != null)
                        ReaderFailureNotice(error: triple.error, onRetry: _apply, recoverAutomatically: false),
                      Padding(
                        padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
                        child: Row(
                          children: [
                            Expanded(
                              child: TextButton(
                                key: const ValueKey('home-filter-reset'),
                                onPressed: triple.isLoading ? null : _draft.reset,
                                child: Text(l10n.plugin_reader_reset_filters, textAlign: TextAlign.center),
                              ),
                            ),
                            const SizedBox(width: 12),
                            Expanded(
                              child: FilledButton(
                                key: const ValueKey('home-filter-apply'),
                                style: FilledButton.styleFrom(minimumSize: const Size(48, 48)),
                                onPressed: triple.isLoading ? null : _apply,
                                child: triple.isLoading
                                    ? const SizedBox.square(
                                        dimension: 20,
                                        child: CircularProgressIndicator(strokeWidth: 2),
                                      )
                                    : Text(l10n.sort_ungrouped_apply),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              );
            },
          ),
        ),
      ),
    );
  }

  Widget _list(BuildContext context, bool groupSection, String query, HomeFilterDraft draft) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    final accounts = widget.accounts
        .where((a) => (a.screenName ?? '').toLowerCase().contains(query.trim().toLowerCase()))
        .toList();
    final groups = drawerGroupsForQuery(widget.groups, query);
    final count = groupSection ? groups.length : accounts.length;
    final active = groupSection
        ? widget.groups.where((g) => !draft.groups.contains(g.id)).length
        : widget.accounts.where((a) => !draft.accounts.contains(a.id)).length;
    final total = groupSection ? widget.groups.length : widget.accounts.length;
    return CustomScrollView(
      key: const PageStorageKey('home-filter-scroll'),
      keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
      slivers: [
        SliverToBoxAdapter(
          child: Padding(
            padding: const EdgeInsetsDirectional.fromSTEB(20, 0, 8, 12),
            child: Row(
              children: [
                Expanded(
                  child: Text(
                    l10n.home_feed_accounts,
                    style: theme.textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w700),
                  ),
                ),
                IconButton(tooltip: l10n.close, icon: const Icon(Icons.close), onPressed: () => Navigator.pop(context)),
              ],
            ),
          ),
        ),
        if (widget.groupsStore != null)
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20),
              child: SegmentedButton<bool>(
                segments: [
                  ButtonSegment(
                    value: false,
                    icon: const Icon(Icons.account_circle_outlined),
                    label: Text(l10n.account, maxLines: 2, overflow: TextOverflow.ellipsis),
                  ),
                  ButtonSegment(
                    value: true,
                    icon: const Icon(Icons.folder_outlined),
                    label: Text(l10n.groups, maxLines: 2, overflow: TextOverflow.ellipsis),
                  ),
                ],
                selected: {groupSection},
                onSelectionChanged: (selection) => _section.select(selection.first),
              ),
            ),
          ),
        SliverToBoxAdapter(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(20, 16, 20, 12),
            child: TextField(
              key: const ValueKey('home-filter-search'),
              controller: _searchController,
              onChanged: _query.select,
              decoration: InputDecoration(
                hintText: l10n.search,
                prefixIcon: const Icon(Icons.search),
                filled: true,
                fillColor: theme.colorScheme.surfaceContainerLow,
                border: OutlineInputBorder(borderRadius: BorderRadius.circular(14), borderSide: BorderSide.none),
              ),
            ),
          ),
        ),
        SliverToBoxAdapter(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(20, 8, 20, 12),
            child: Text(
              '${groupSection ? l10n.home_feed_groups_description : l10n.home_feed_accounts_description}\n$active / $total',
            ),
          ),
        ),
        if (!groupSection && widget.accounts.isEmpty)
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Column(
                children: [
                  Text(l10n.home_feed_accounts_empty),
                  const SizedBox(height: 12),
                  FilledButton.icon(
                    onPressed: widget.onAddAccount,
                    icon: const Icon(Icons.add),
                    label: Text(l10n.add_account),
                  ),
                ],
              ),
            ),
          ),
        if (count == 0 && (groupSection || widget.accounts.isNotEmpty))
          SliverToBoxAdapter(
            child: Padding(padding: const EdgeInsets.all(24), child: Text(l10n.no_results)),
          ),
        SliverPadding(
          padding: const EdgeInsets.fromLTRB(12, 0, 12, 12),
          sliver: SliverList.builder(
            itemCount: count,
            itemBuilder: (_, index) => groupSection
                ? HomeGroupToggleTile(
                    group: groups[index],
                    disabled: draft.groups,
                    onChanged: (enabled) async => _draft.group(groups[index].id, enabled),
                  )
                : HomeAccountToggleTile(
                    account: accounts[index],
                    disabled: draft.accounts,
                    accounts: widget.accounts,
                    onChanged: (enabled) async => _draft.account(accounts[index].id, enabled, widget.accounts),
                  ),
          ),
        ),
      ],
    );
  }
}
