import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/subscriptions/group_ungrouped_model.dart';
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
  var _applying = false;

  @override
  void initState() {
    super.initState();
    _model = GroupUngroupedModel();
    WidgetsBinding.instance.addPostFrameCallback((_) => _start());
  }

  @override
  void dispose() {
    _model.destroy();
    super.dispose();
  }

  Future<void> _start() async {
    final groups = context.read<GroupsModel>();
    final subscriptions = context.read<SubscriptionsModel>();
    final prefs = PrefService.of(context, listen: false);
    await _model.buildPlan(
      subscriptions: subscriptions.state,
      groups: groups.state,
      members: await groups.listGroupMembers(),
      ai: AiConfig.fromPrefs(prefs),
    );
  }

  Future<void> _apply() async {
    if (_applying) return;
    setState(() => _applying = true);
    final messenger = ScaffoldMessenger.of(context);
    final l10n = L10n.of(context);
    final groups = context.read<GroupsModel>();
    try {
      final count = await groups.applyUngroupedPlan(_model.state.plan);
      if (!mounted) return;
      messenger.showSnackBar(
        SnackBar(content: Text(l10n.sort_ungrouped_applied(count))),
      );
      Navigator.pop(context);
    } catch (error, stack) {
      if (!mounted) return;
      setState(() => _applying = false);
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
        onState: (context, state) =>
            _PlanBody(state: state, applying: _applying, onApply: _apply),
      ),
    );
  }
}

class _PlanBody extends StatelessWidget {
  final GroupUngroupedState state;
  final bool applying;
  final VoidCallback onApply;

  const _PlanBody({
    required this.state,
    required this.applying,
    required this.onApply,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final plan = state.plan;
    if (plan.isEmpty) {
      return ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text(l10n.sort_ungrouped_description),
          const SizedBox(height: 16),
          Text(l10n.sort_ungrouped_empty),
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
                    : l10n.sort_ungrouped_heuristic_note,
              ),
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
              ],
            ],
          ),
        ),
        SafeArea(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
            child: FilledButton(
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
        ),
      ],
    );
  }
}
