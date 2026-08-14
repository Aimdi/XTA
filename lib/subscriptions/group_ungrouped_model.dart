import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/subscriptions/group_ungrouped.dart';
import 'package:xta/utils/ai_client.dart';

class GroupUngroupedState {
  final GroupUngroupedPlan plan;
  final Map<String, AccountRef> accountsById;
  final Map<String, String> groupNames;

  const GroupUngroupedState({
    this.plan = const GroupUngroupedPlan(),
    this.accountsById = const {},
    this.groupNames = const {},
  });

  String handleOf(String id) => accountsById[id]?.handle ?? id;

  String groupNameOf(String id) => groupNames[id] ?? id;
}

typedef AiChat = Future<String> Function(AiConfig config, String prompt);

/// Builds a plan for subscriptions that are not in any group.
class GroupUngroupedModel extends Store<GroupUngroupedState> {
  GroupUngroupedModel({this.chat}) : super(const GroupUngroupedState());

  final AiChat? chat;

  Future<void> buildPlan({
    required List<Subscription> subscriptions,
    required List<SubscriptionGroup> groups,
    required List<SubscriptionGroupMember> members,
    required AiConfig ai,
  }) async {
    await execute(() async {
      final groupedIds = {
        for (final member in members)
          if (member.group != '-1') member.profile,
      };
      final ungrouped = ungroupedSubscriptions(subscriptions, groupedIds);
      final refs = groupRefsFrom(
        groups: groups,
        subscriptions: subscriptions,
        members: members,
      );
      final heuristic = planByNames(ungrouped, refs);
      final state = GroupUngroupedState(
        plan: heuristic,
        accountsById: {for (final account in ungrouped) account.id: account},
        groupNames: {for (final group in refs) group.id: group.name},
      );
      if (!ai.isConfigured || ungrouped.isEmpty) return state;
      return state.copyWith(plan: await _askAi(ai, ungrouped, refs, heuristic));
    });
  }

  Future<GroupUngroupedPlan> _askAi(
    AiConfig ai,
    List<AccountRef> ungrouped,
    List<GroupRef> refs,
    GroupUngroupedPlan heuristic,
  ) async {
    try {
      final reply = await (chat ?? aiChatCompletion)(
        ai,
        groupingPrompt(ungrouped, refs),
      );
      final parsed = parseGroupingReply(
        reply,
        accountIds: {for (final account in ungrouped) account.id},
        groupIds: {for (final group in refs) group.id},
      );
      if (parsed.isEmpty) return heuristic;
      return mergeGroupingPlans(heuristic, parsed);
    } catch (_) {
      return heuristic;
    }
  }
}

extension on GroupUngroupedState {
  GroupUngroupedState copyWith({GroupUngroupedPlan? plan}) =>
      GroupUngroupedState(
        plan: plan ?? this.plan,
        accountsById: accountsById,
        groupNames: groupNames,
      );
}
