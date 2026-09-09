import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/home/home_account_filter.dart';
import 'package:xta/home/home_group_drawer.dart';
import 'package:xta/home/home_group_filter.dart';
import 'package:xta/home/home_selection_store.dart';
import 'package:xta/tweet/tweet_chrome.dart';

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

  @override
  void dispose() {
    _section.destroy();
    _query.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    return SafeArea(
      top: false,
      child: SizedBox(
        height: MediaQuery.sizeOf(context).height * 0.78,
        child: Padding(
          padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(context).bottom),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Padding(
                padding: const EdgeInsetsDirectional.fromSTEB(20, 0, 8, 12),
                child: Row(
                  children: [
                    Expanded(child: Text(l10n.home_feed_accounts, style: theme.textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w700))),
                    IconButton(tooltip: l10n.close, icon: const Icon(Icons.close), onPressed: () => Navigator.pop(context)),
                  ],
                ),
              ),
              ScopedBuilder<HomeSelectionStore<bool>, bool>(
                store: _section,
                onState: (_, groupSection) => Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 20),
                  child: SegmentedButton<bool>(
                    segments: [
                      ButtonSegment(value: false, icon: const Icon(Icons.account_circle_outlined), label: Text(l10n.account)),
                      if (widget.groupsStore != null)
                        ButtonSegment(value: true, icon: const Icon(Icons.folder_outlined), label: Text(l10n.groups)),
                    ],
                    selected: {groupSection},
                    onSelectionChanged: (selection) => _section.select(selection.first),
                  ),
                ),
              ),
              Padding(
                padding: const EdgeInsets.fromLTRB(20, 16, 20, 12),
                child: TextField(
                  key: const ValueKey('home-filter-search'),
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
              Expanded(
                child: ScopedBuilder<HomeSelectionStore<bool>, bool>(
                  store: _section,
                  onState: (_, groupSection) => ScopedBuilder<HomeSelectionStore<String>, String>(
                    store: _query,
                    onState: (_, query) => groupSection ? _groups(context, query) : _accounts(context, query),
                  ),
                ),
              ),
              Padding(
                padding: const EdgeInsets.fromLTRB(20, 8, 20, 12),
                child: FilledButton(
                  style: FilledButton.styleFrom(minimumSize: const Size(48, 48)),
                  onPressed: () => Navigator.pop(context),
                  child: Text(l10n.close),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _sectionList(BuildContext context, {required String description, required int active, required int total, required List<Widget> rows}) => ListView(
    padding: const EdgeInsets.fromLTRB(12, 0, 12, 12),
    keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
    children: [
      Padding(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
        child: Text(description, style: tweetMetadataStyle(context)),
      ),
      Padding(
        padding: const EdgeInsetsDirectional.fromSTEB(8, 4, 8, 12),
        child: Row(children: [
          Icon(Icons.check_circle_outline, size: 18, color: tweetReadableAccentColor(context)),
          const SizedBox(width: 8),
          Expanded(child: Text('$active / $total', style: Theme.of(context).textTheme.labelLarge)),
        ]),
      ),
      if (rows.isEmpty) Padding(padding: const EdgeInsets.all(24), child: Text(L10n.of(context).no_results)),
      ...rows,
    ],
  );

  Widget _accounts(BuildContext context, String query) {
    final l10n = L10n.of(context);
    if (widget.accounts.isEmpty) return Center(child: Padding(
      padding: const EdgeInsets.all(24),
      child: Column(mainAxisSize: MainAxisSize.min, children: [
        Text(l10n.home_feed_accounts_empty),
        const SizedBox(height: 12),
        FilledButton.icon(onPressed: widget.onAddAccount, icon: const Icon(Icons.add), label: Text(l10n.add_account)),
      ]),
    ));
    return ScopedBuilder<HomeAccountFilterStore, Set<String>>(
      store: widget.accountsStore,
      onState: (_, disabled) => _sectionList(
        context,
        description: l10n.home_feed_accounts_description,
        active: widget.accounts.where((account) => !disabled.contains(account.id)).length,
        total: widget.accounts.length,
        rows: [
          for (final account in widget.accounts)
            if ((account.screenName ?? '').toLowerCase().contains(query.trim().toLowerCase()))
              HomeAccountToggleTile(
                account: account,
                disabled: disabled,
                accounts: widget.accounts,
                onChanged: (enabled) async {
                  await widget.accountsStore.setEnabled(account.id, enabled, accounts: widget.accounts);
                  widget.onChanged?.call();
                },
              ),
        ],
      ),
    );
  }

  Widget _groups(BuildContext context, String query) => ScopedBuilder<HomeGroupFilterStore, Set<String>>(
    store: widget.groupsStore!,
    onState: (_, disabled) => _sectionList(
      context,
      description: L10n.of(context).home_feed_groups_description,
      active: widget.groups.where((group) => !disabled.contains(group.id)).length,
      total: widget.groups.length,
      rows: [
        for (final group in drawerGroupsForQuery(widget.groups, query))
          HomeGroupToggleTile(
            group: group,
            disabled: disabled,
            onChanged: (enabled) async {
              await widget.groupsStore!.setEnabled(group.id, enabled);
              widget.onChanged?.call();
            },
          ),
      ],
    ),
  );
}
