import 'dart:convert';

import 'package:xta/database/entities.dart';

/// An account the sorter can place — id plus the names it is known by.
class AccountRef {
  final String id;
  final String handle;
  final String name;

  const AccountRef({
    required this.id,
    required this.handle,
    required this.name,
  });
}

class GroupRef {
  final String id;
  final String name;
  final List<AccountRef> members;

  const GroupRef({
    required this.id,
    required this.name,
    this.members = const [],
  });
}

class GroupAssignment {
  final String accountId;
  final String groupId;

  const GroupAssignment({required this.accountId, required this.groupId});
}

class SuggestedGroup {
  final String name;
  final List<String> accountIds;

  const SuggestedGroup({required this.name, required this.accountIds});
}

/// What to do with subscriptions that are not in any group.
class GroupUngroupedPlan {
  final List<GroupAssignment> assign;
  final List<SuggestedGroup> suggest;
  final List<String> leftoverIds;
  final bool usedAi;

  const GroupUngroupedPlan({
    this.assign = const [],
    this.suggest = const [],
    this.leftoverIds = const [],
    this.usedAi = false,
  });

  bool get isEmpty => assign.isEmpty && suggest.isEmpty;

  int get moveCount =>
      assign.length +
      suggest.fold<int>(0, (sum, g) => sum + g.accountIds.length);

  GroupUngroupedPlan copyWith({
    List<GroupAssignment>? assign,
    List<SuggestedGroup>? suggest,
    List<String>? leftoverIds,
    bool? usedAi,
  }) => GroupUngroupedPlan(
    assign: assign ?? this.assign,
    suggest: suggest ?? this.suggest,
    leftoverIds: leftoverIds ?? this.leftoverIds,
    usedAi: usedAi ?? this.usedAi,
  );
}

/// Subscriptions that are not a saved search and are in no group.
List<AccountRef> ungroupedSubscriptions(
  List<Subscription> subscriptions,
  Set<String> groupedIds,
) {
  return [
    for (final sub in subscriptions)
      if (sub is! SearchSubscription && !groupedIds.contains(sub.id))
        AccountRef(id: sub.id, handle: sub.screenName, name: sub.name),
  ];
}

List<GroupRef> groupRefsFrom({
  required List<SubscriptionGroup> groups,
  required List<Subscription> subscriptions,
  required List<SubscriptionGroupMember> members,
}) {
  final byId = {for (final sub in subscriptions) sub.id: sub};
  final membersByGroup = <String, List<AccountRef>>{};
  for (final member in members) {
    if (member.group == '-1') continue;
    final sub = byId[member.profile];
    final refs = membersByGroup.putIfAbsent(member.group, () => []);
    refs.add(
      AccountRef(
        id: member.profile,
        handle: sub?.screenName ?? member.profile,
        name: sub?.name ?? member.profile,
      ),
    );
  }
  return [
    for (final group in groups)
      if (group.id != '-1')
        GroupRef(
          id: group.id,
          name: group.name,
          members: membersByGroup[group.id] ?? const [],
        ),
  ];
}

Set<String> nameTokens(String text) => {
  for (final part in text.toLowerCase().split(RegExp(r'[^a-z0-9]+')))
    if (part.length >= 3) part,
};

Set<String> accountTokens(AccountRef account) => {
  ...nameTokens(account.handle),
  ...nameTokens(account.name),
};

int fitScore(AccountRef account, GroupRef group) {
  final tokens = {
    ...nameTokens(group.name),
    for (final member in group.members) ...accountTokens(member),
  };
  return accountTokens(account).intersection(tokens).length;
}

/// Place each ungrouped account in the best existing group, or cluster leftovers.
GroupUngroupedPlan planByNames(
  List<AccountRef> ungrouped,
  List<GroupRef> groups,
) {
  final assign = <GroupAssignment>[];
  final leftover = <AccountRef>[];
  for (final account in ungrouped) {
    final placed = _bestExisting(account, groups);
    if (placed == null) {
      leftover.add(account);
    } else {
      assign.add(placed);
    }
  }
  final suggested = suggestClusters(leftover);
  final clustered = {for (final group in suggested) ...group.accountIds};
  return GroupUngroupedPlan(
    assign: assign,
    suggest: suggested,
    leftoverIds: [
      for (final account in leftover)
        if (!clustered.contains(account.id)) account.id,
    ],
  );
}

GroupAssignment? _bestExisting(AccountRef account, List<GroupRef> groups) {
  GroupRef? best;
  var bestScore = 0;
  for (final group in groups) {
    final score = fitScore(account, group);
    if (score > bestScore) {
      best = group;
      bestScore = score;
    }
  }
  if (best == null || bestScore < 1) return null;
  return GroupAssignment(accountId: account.id, groupId: best.id);
}

/// Clusters leftover accounts that share a long token into suggested groups.
List<SuggestedGroup> suggestClusters(List<AccountRef> leftover) {
  if (leftover.length < 2) return const [];
  final parent = {for (final account in leftover) account.id: account.id};
  String root(String id) {
    while (parent[id] != id) {
      parent[id] = parent[parent[id]!]!;
      id = parent[id]!;
    }
    return id;
  }

  _unionSimilar(leftover, parent, root);
  return _clustersOf(leftover, parent, root);
}

void _unionSimilar(
  List<AccountRef> leftover,
  Map<String, String> parent,
  String Function(String) root,
) {
  for (var i = 0; i < leftover.length; i++) {
    for (var j = i + 1; j < leftover.length; j++) {
      final shared = accountTokens(
        leftover[i],
      ).intersection(accountTokens(leftover[j]));
      if (shared.every((token) => token.length < 4)) continue;
      parent[root(leftover[j].id)] = root(leftover[i].id);
    }
  }
}

List<SuggestedGroup> _clustersOf(
  List<AccountRef> leftover,
  Map<String, String> parent,
  String Function(String) root,
) {
  final buckets = <String, List<AccountRef>>{};
  for (final account in leftover) {
    buckets.putIfAbsent(root(account.id), () => []).add(account);
  }
  return [
    for (final bucket in buckets.values)
      if (bucket.length >= 2)
        SuggestedGroup(
          name: _clusterName(bucket),
          accountIds: [for (final account in bucket) account.id],
        ),
  ];
}

String _clusterName(List<AccountRef> accounts) {
  final counts = <String, int>{};
  for (final account in accounts) {
    for (final token in accountTokens(account).where((t) => t.length >= 4)) {
      counts[token] = (counts[token] ?? 0) + 1;
    }
  }
  if (counts.isEmpty) return accounts.first.handle;
  final best = counts.entries.reduce((a, b) => a.value >= b.value ? a : b).key;
  return '${best[0].toUpperCase()}${best.substring(1)}';
}

/// Prompt Grok (or any chat model) to assign leftover accounts.
String groupingPrompt(List<AccountRef> ungrouped, List<GroupRef> groups) {
  final payload = {
    'groups': [
      for (final group in groups)
        {
          'id': group.id,
          'name': group.name,
          'members': [
            for (final member in group.members.take(12))
              '${member.handle} ${member.name}',
          ],
        },
    ],
    'ungrouped': [
      for (final account in ungrouped.take(80))
        {'id': account.id, 'handle': account.handle, 'name': account.name},
    ],
  };
  return 'Sort these read-only subscriptions into groups. '
      'Prefer an existing group when the account is similar to its members. '
      'Suggest a new group only when several ungrouped accounts share a theme '
      'no existing group covers. Omit anyone you are unsure about. '
      'Reply with JSON only: '
      '{"assign":[{"id":"","groupId":""}],"suggest":[{"name":"","ids":[""]}]}\n'
      '${jsonEncode(payload)}';
}

/// Reads an AI reply into a plan. Unknown ids and group ids are dropped.
GroupUngroupedPlan parseGroupingReply(
  String reply, {
  required Set<String> accountIds,
  required Set<String> groupIds,
}) {
  final json = _jsonObject(reply);
  if (json == null) return const GroupUngroupedPlan();
  final assign = _parseAssign(json['assign'], accountIds, groupIds);
  final suggest = _parseSuggest(json['suggest'], accountIds);
  final placed = {
    for (final row in assign) row.accountId,
    for (final group in suggest) ...group.accountIds,
  };
  return GroupUngroupedPlan(
    assign: assign,
    suggest: suggest,
    leftoverIds: [
      for (final id in accountIds)
        if (!placed.contains(id)) id,
    ],
    usedAi: true,
  );
}

Map<String, dynamic>? _jsonObject(String reply) {
  final start = reply.indexOf('{');
  final end = reply.lastIndexOf('}');
  if (start < 0 || end <= start) return null;
  try {
    final decoded = jsonDecode(reply.substring(start, end + 1));
    return decoded is Map<String, dynamic> ? decoded : null;
  } catch (_) {
    return null;
  }
}

List<GroupAssignment> _parseAssign(
  Object? raw,
  Set<String> accountIds,
  Set<String> groupIds,
) {
  if (raw is! List) return const [];
  return [
    for (final row in raw)
      if (row is Map)
        if (accountIds.contains(row['id']) && groupIds.contains(row['groupId']))
          GroupAssignment(
            accountId: row['id'] as String,
            groupId: row['groupId'] as String,
          ),
  ];
}

List<SuggestedGroup> _parseSuggest(Object? raw, Set<String> accountIds) {
  if (raw is! List) return const [];
  return [
    for (final row in raw)
      if (row is Map)
        if ((row['name'] as String?)?.trim().isNotEmpty == true)
          SuggestedGroup(
            name: (row['name'] as String).trim(),
            accountIds: [
              for (final id
                  in (row['ids'] as List? ?? const []).whereType<String>())
                if (accountIds.contains(id)) id,
            ],
          ),
  ].where((group) => group.accountIds.length >= 2).toList(growable: false);
}

/// AI assignments win when they name a real group; heuristic fills the rest.
GroupUngroupedPlan mergeGroupingPlans(
  GroupUngroupedPlan heuristic,
  GroupUngroupedPlan ai,
) {
  final aiAssigned = {for (final row in ai.assign) row.accountId};
  final aiSuggested = {for (final group in ai.suggest) ...group.accountIds};
  final taken = {...aiAssigned, ...aiSuggested};
  return GroupUngroupedPlan(
    assign: [
      ...ai.assign,
      for (final row in heuristic.assign)
        if (!taken.contains(row.accountId)) row,
    ],
    suggest: [
      ...ai.suggest,
      for (final group in heuristic.suggest)
        SuggestedGroup(
          name: group.name,
          accountIds: [
            for (final id in group.accountIds)
              if (!taken.contains(id)) id,
          ],
        ),
    ].where((group) => group.accountIds.length >= 2).toList(growable: false),
    leftoverIds: [
      for (final id in {...heuristic.leftoverIds, ...ai.leftoverIds})
        if (!taken.contains(id)) id,
    ],
    usedAi: ai.usedAi,
  );
}
