import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/subscriptions/group_ungrouped_model.dart';
import 'package:xta/subscriptions/group_ungrouped_sorter_screen.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/utils/ai_client.dart';

/// Reviews a plan that places subscriptions which are not in any group.
class SortUngroupedScreen extends StatefulWidget {
  const SortUngroupedScreen({super.key});

  @override
  State<SortUngroupedScreen> createState() => _SortUngroupedScreenState();
}

class _SortUngroupedScreenState extends State<SortUngroupedScreen> {
  late final GroupUngroupedModel _model;
  final _applying = GroupPlanApplyingStore();

  @override
  void initState() {
    super.initState();
    _model = GroupUngroupedModel();
    WidgetsBinding.instance.addPostFrameCallback((_) => _start());
  }

  @override
  void dispose() {
    _model.destroy();
    _applying.destroy();
    super.dispose();
  }

  Future<void> _start({bool useAi = false}) async {
    if (!mounted) return;
    final groups = context.read<GroupsModel>();
    final subscriptions = context.read<SubscriptionsModel>();
    final prefs = PrefService.of(context, listen: false);
    final members = await groups.listGroupMembers();
    if (!mounted) return;
    await _model.buildPlan(
      subscriptions: subscriptions.state,
      groups: groups.state,
      members: members,
      ai: AiConfig.fromPrefs(prefs),
      useAi: useAi,
    );
  }

  /// The subscriptions the plan leaves without a group, in its order.
  List<Subscription> _leftovers() {
    final byId = {for (final subscription in context.read<SubscriptionsModel>().state) subscription.id: subscription};
    return [for (final id in _model.state.plan.leftoverIds) ?byId[id]];
  }

  MaterialPageRoute<void> _sorterRoute() =>
      MaterialPageRoute(builder: (_) => UngroupedSorterScreen(accounts: _leftovers()));

  /// Sorts the leftovers by hand, then plans again from what is left.
  Future<void> _sortOneByOne() async {
    await Navigator.push(context, _sorterRoute());
    await _start();
  }

  Future<void> _apply() async {
    if (_applying.state) return;
    _applying.setApplying(true);
    final messenger = ScaffoldMessenger.of(context);
    final l10n = L10n.of(context);
    final groups = context.read<GroupsModel>();
    try {
      final count = await groups.applyUngroupedPlan(_model.state.plan);
      if (!mounted) return;
      messenger.showSnackBar(
        SnackBar(content: Text(l10n.sort_ungrouped_applied(count))),
      );
      // The accounts the plan could not place come next, one at a time.
      if (_model.state.plan.leftoverIds.isEmpty) {
        Navigator.pop(context);
      } else {
        Navigator.pushReplacement(context, _sorterRoute());
      }
    } catch (error, stack) {
      if (!mounted) return;
      _applying.setApplying(false);
      messenger.showSnackBar(
        SnackBar(content: Text(l10n.sort_ungrouped_failed)),
      );
      debugPrint('$error\n$stack');
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(l10n.sort_ungrouped_title)),
      body: ScopedBuilder<GroupUngroupedModel, GroupUngroupedState>(
        store: _model,
        onLoading: (_) => const Center(child: CircularProgressIndicator()),
        onError: (context, error) => FullPageErrorWidget(
          error: error,
          stackTrace: null,
          prefix: l10n.sort_ungrouped_title,
        ),
        onState: (context, state) => ScopedBuilder<GroupPlanApplyingStore, bool>(
          store: _applying,
          onState: (_, applying) => Column(children: [
            if (AiConfig.fromPrefs(PrefService.of(context)).isConfigured)
              Padding(padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
                child: FilledButton.tonalIcon(
                  icon: const Icon(Icons.auto_awesome),
                  label: Text(l10n.group_sort_ai),
                  onPressed: applying ? null : () => _start(useAi: true),
                ),
              ),
            Expanded(
              child: _PlanBody(state: state, applying: applying, onApply: _apply, onSortOneByOne: _sortOneByOne),
            ),
          ]),
        ),
      ),
    );
  }
}

class _PlanBody extends StatelessWidget {
  final GroupUngroupedState state;
  final bool applying;
  final VoidCallback onApply;
  final VoidCallback onSortOneByOne;

  const _PlanBody({
    required this.state,
    required this.applying,
    required this.onApply,
    required this.onSortOneByOne,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final plan = state.plan;
    if (plan.isEmpty) {
      return Column(
        children: [
          Expanded(
            child: ListView(
              padding: const EdgeInsets.all(16),
              children: [
                Text(l10n.sort_ungrouped_description),
                const SizedBox(height: 16),
                if (state.aiFailed) Text(l10n.group_ai_fallback),
                Text(plan.leftoverIds.isEmpty ? l10n.sort_ungrouped_empty : l10n.no_results),
                if (plan.leftoverIds.isNotEmpty) ...[
                  const SizedBox(height: 16),
                  Text(l10n.sort_ungrouped_leftover),
                  Text(plan.leftoverIds.map(state.handleOf).join(', ')),
                ],
              ],
            ),
          ),
          if (plan.leftoverIds.isNotEmpty)
            _planFooter(
              FilledButton(
                key: const ValueKey('sort-one-by-one'),
                onPressed: applying ? null : onSortOneByOne,
                child: Text(l10n.sort_one_by_one),
              ),
            ),
        ],
      );
    }

    return Column(
      children: [
        Expanded(
          child: ListView(
            padding: const EdgeInsets.all(16),
            children: [
              Text(l10n.sort_ungrouped_description),
              const SizedBox(height: 12),
              Text(
                plan.usedAi
                    ? l10n.sort_ungrouped_ai_note
                    : l10n.group_sort_local,
              ),
              if (state.aiFailed) Text(l10n.group_ai_fallback),
              if (plan.assign.isNotEmpty) ...[
                const SizedBox(height: 20),
                Text(
                  l10n.sort_ungrouped_assign,
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                ...[
                  for (final row in plan.assign)
                    ListTile(
                      contentPadding: EdgeInsets.zero,
                      title: Text(
                        l10n.sort_ungrouped_into(
                          state.handleOf(row.accountId),
                          state.groupNameOf(row.groupId),
                        ),
                      ),
                    ),
                ],
              ],
              if (plan.suggest.isNotEmpty) ...[
                const SizedBox(height: 12),
                Text(
                  l10n.sort_ungrouped_suggest,
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                ...[
                  for (final group in plan.suggest)
                    ListTile(
                      contentPadding: EdgeInsets.zero,
                      title: Text(group.name),
                      subtitle: Text(
                        group.accountIds
                            .map(state.handleOf)
                            .map((handle) => '@$handle')
                            .join(', '),
                      ),
                    ),
                ],
              ],
              if (plan.leftoverIds.isNotEmpty) ...[
                const SizedBox(height: 12),
                Text(
                  l10n.sort_ungrouped_leftover,
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                Text(
                  plan.leftoverIds
                      .map(state.handleOf)
                      .map((handle) => '@$handle')
                      .join(', '),
                ),
                Align(
                  alignment: AlignmentDirectional.centerStart,
                  child: TextButton.icon(
                    key: const ValueKey('sort-one-by-one'),
                    onPressed: applying ? null : onSortOneByOne,
                    icon: const Icon(Icons.style_outlined),
                    label: Text(l10n.sort_one_by_one),
                  ),
                ),
              ],
            ],
          ),
        ),
        _planFooter(
          FilledButton(
            onPressed: applying ? null : onApply,
            child: applying
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : Text(l10n.sort_ungrouped_apply),
          ),
        ),
      ],
    );
  }
}

/// The plan's main action, at the foot of the screen.
Widget _planFooter(Widget button) => SafeArea(
  child: Padding(padding: const EdgeInsets.fromLTRB(16, 8, 16, 16), child: button),
);
