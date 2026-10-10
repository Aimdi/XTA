import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
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
  bool _closed = false;
  HomeFilterDraftStore(Set<String> accounts, Set<String> groups) : super(HomeFilterDraft(accounts, groups));

  /// The draft may turn every account off on the way to picking a few; [apply] refuses to save that.
  void account(String id, bool enabled) {
    if (isLoading) return;
    final next = {...state.accounts};
    enabled ? next.remove(id) : next.add(id);
    update(HomeFilterDraft(next, state.groups));
  }

  /// Turns off every id in [disabled] and turns everything else on, for one section.
  void select({required bool groups, required Set<String> disabled}) {
    if (isLoading) return;
    update(groups ? HomeFilterDraft(state.accounts, disabled) : HomeFilterDraft(disabled, state.groups));
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

  bool keepsAnAccount(List<Account> accounts) =>
      accounts.isEmpty || accounts.any((account) => !state.accounts.contains(account.id));

  Future<bool> apply(HomeAccountFilterStore accounts, HomeGroupFilterStore? groups, List<Account> known) async {
    if (_closed || isLoading || !keepsAnAccount(known)) return false;
    final draft = state;
    setLoading(true);
    final previous = homeFeedDisabledIdsToPrefs(accounts.state);
    try {
      await accounts.prefs.set(optionHomeFeedDisabledAccountIds, homeFeedDisabledIdsToPrefs(draft.accounts));
      try {
        await groups?.prefs.set(optionHomeFeedDisabledGroupIds, homeFeedDisabledIdsToPrefs(draft.groups));
      } catch (_) {
        await accounts.prefs.set(optionHomeFeedDisabledAccountIds, previous);
        rethrow;
      }
      accounts.publishDisabled(draft.accounts);
      groups?.update(draft.groups);
      return true;
    } catch (error) {
      if (!_closed) setError(error, force: true);
      return false;
    } finally {
      if (!_closed) setLoading(false);
    }
  }

  @override
  Future<void> destroy() {
    _closed = true;
    return super.destroy();
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
    final applied = await _draft.apply(widget.accountsStore, widget.groupsStore, widget.accounts);
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
                                onPressed: triple.isLoading || !_draft.keepsAnAccount(widget.accounts) ? null : _apply,
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

  Set<String> _ids(bool groups) =>
      groups ? widget.groups.map((group) => group.id).toSet() : widget.accounts.map((account) => account.id).toSet();

  void _only(bool groups, String id) {
    HapticFeedback.selectionClick();
    _draft.select(groups: groups, disabled: _ids(groups)..remove(id));
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
            padding: const EdgeInsets.fromLTRB(20, 8, 20, 4),
            child: Text(
              '${groupSection ? l10n.home_feed_groups_description : l10n.home_feed_accounts_description} '
              '${l10n.home_feed_only_hint}',
              style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
            ),
          ),
        ),
        if (total > 0)
          SliverToBoxAdapter(
            child: _BulkBar(
              active: active,
              total: total,
              warning: !groupSection && active == 0 ? l10n.home_feed_keep_one_account : null,
              onAll: () => _draft.select(groups: groupSection, disabled: {}),
              onNone: () => _draft.select(groups: groupSection, disabled: _ids(groupSection)),
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
          padding: const EdgeInsets.fromLTRB(4, 0, 4, 12),
          sliver: SliverList.builder(
            itemCount: count,
            itemBuilder: (_, index) => groupSection
                ? HomeGroupToggleTile(
                    group: groups[index],
                    disabled: draft.groups,
                    onChanged: (enabled) async => _draft.group(groups[index].id, enabled),
                    onOnly: () => _only(true, groups[index].id),
                  )
                : HomeAccountToggleTile(
                    account: accounts[index],
                    disabled: draft.accounts,
                    accounts: widget.accounts,
                    keepOne: false,
                    onChanged: (enabled) async => _draft.account(accounts[index].id, enabled),
                    onOnly: () => _only(false, accounts[index].id),
                  ),
          ),
        ),
      ],
    );
  }
}

/// How many are on, with one-tap All and None for the visible section.
class _BulkBar extends StatelessWidget {
  final int active;
  final int total;
  final String? warning;
  final VoidCallback onAll;
  final VoidCallback onNone;
  const _BulkBar({
    required this.active,
    required this.total,
    required this.warning,
    required this.onAll,
    required this.onNone,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsetsDirectional.fromSTEB(20, 0, 12, 4),
      child: Row(
        children: [
          Expanded(
            child: Text(
              warning ?? '$active / $total',
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: theme.textTheme.labelLarge?.copyWith(color: warning == null ? null : theme.colorScheme.error),
            ),
          ),
          TextButton(
            key: const ValueKey('home-filter-all'),
            onPressed: active == total ? null : onAll,
            child: Text(l10n.all),
          ),
          TextButton(
            key: const ValueKey('home-filter-none'),
            onPressed: active == 0 ? null : onNone,
            child: Text(l10n.home_feed_select_none),
          ),
        ],
      ),
    );
  }
}
