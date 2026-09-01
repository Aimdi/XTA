import 'package:flutter_test/flutter_test.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/subscriptions/group_ungrouped.dart';
import 'package:xta/subscriptions/group_ungrouped_model.dart';
import 'package:xta/utils/ai_client.dart';

UserSubscription _user(String id, String handle, String name) =>
    UserSubscription(
      id: id,
      screenName: handle,
      name: name,
      profileImageUrlHttps: null,
      verified: false,
      createdAt: DateTime(2020),
      inFeed: true,
    );

SubscriptionGroup _group(String id, String name) => SubscriptionGroup(
  id: id,
  name: name,
  icon: '',
  color: null,
  numberOfMembers: 0,
  createdAt: DateTime(2020),
);

void main() {
  test('skips saved searches and anyone already in a group', () {
    final ungrouped = ungroupedSubscriptions(
      [
        _user('1', 'nasa', 'NASA'),
        _user('2', 'esa', 'ESA'),
        SearchSubscription(id: 'dart', createdAt: DateTime(2020)),
      ],
      {'2'},
    );
    expect(ungrouped.map((a) => a.id), ['1']);
  });

  test('assigns an ungrouped account to the group with shared name tokens', () {
    final plan = planByNames(
      [const AccountRef(id: '2', handle: 'esa', name: 'ESA Space')],
      [
        const GroupRef(
          id: 'space',
          name: 'Space',
          members: [AccountRef(id: '1', handle: 'nasa', name: 'NASA')],
        ),
      ],
    );
    expect(plan.assign, hasLength(1));
    expect(plan.assign.single.groupId, 'space');
    expect(plan.suggest, isEmpty);
  });

  test('suggests a new group when leftovers share a long token', () {
    final plan = planByNames([
      const AccountRef(id: '1', handle: 'flutterdev', name: 'Flutter'),
      const AccountRef(id: '2', handle: 'dart_lang', name: 'Dart Flutter'),
    ], const []);
    expect(plan.assign, isEmpty);
    expect(plan.suggest, hasLength(1));
    expect(plan.suggest.single.accountIds, ['1', '2']);
    expect(plan.suggest.single.name.toLowerCase(), 'flutter');
  });

  test('parses an AI reply and drops unknown ids', () {
    final plan = parseGroupingReply(
      'Sure.\n{"assign":[{"id":"1","groupId":"space"},{"id":"nope","groupId":"space"}],'
      '"suggest":[{"name":"Games","ids":["2","3","ghost"]}]}\n',
      accountIds: {'1', '2', '3'},
      groupIds: {'space'},
    );
    expect(plan.usedAi, isTrue);
    expect(plan.assign.single.accountId, '1');
    expect(plan.suggest.single.name, 'Games');
    expect(plan.suggest.single.accountIds, ['2', '3']);
    expect(plan.leftoverIds, isEmpty);
  });

  test('AI assignments win; heuristic fills the rest', () {
    const heuristic = GroupUngroupedPlan(
      assign: [
        GroupAssignment(accountId: '1', groupId: 'old'),
        GroupAssignment(accountId: '2', groupId: 'space'),
      ],
    );
    const ai = GroupUngroupedPlan(
      assign: [GroupAssignment(accountId: '1', groupId: 'space')],
      usedAi: true,
    );
    final merged = mergeGroupingPlans(heuristic, ai);
    expect(merged.usedAi, isTrue);
    expect(merged.assign.map((r) => '${r.accountId}:${r.groupId}'), [
      '1:space',
      '2:space',
    ]);
  });

  test(
    'the store prefers a valid AI plan and falls back on a blank reply',
    () async {
      final model = GroupUngroupedModel(
        chat: (config, prompt) async =>
            '{"assign":[{"id":"2","groupId":"space"}],"suggest":[]}',
      );
      await model.buildPlan(
        subscriptions: [
          _user('1', 'nasa', 'NASA'),
          _user('2', 'esa', 'ESA Space'),
        ],
        groups: [_group('space', 'Space')],
        members: [SubscriptionGroupMember(group: 'space', profile: '1')],
        ai: const AiConfig(
          baseUrl: aiGrokBaseUrl,
          apiKey: 'k',
          model: aiGrokModel,
        ),
      );
      expect(model.state.plan.usedAi, isTrue);
      expect(model.state.plan.assign.single.accountId, '2');

      final fallback = GroupUngroupedModel(
        chat: (config, prompt) async => 'no json',
      );
      await fallback.buildPlan(
        subscriptions: [
          _user('1', 'flutterdev', 'Flutter'),
          _user('2', 'dart_lang', 'Dart Flutter'),
        ],
        groups: const [],
        members: const [],
        ai: const AiConfig(
          baseUrl: aiGrokBaseUrl,
          apiKey: 'k',
          model: aiGrokModel,
        ),
      );
      expect(fallback.state.plan.usedAi, isFalse);
      expect(fallback.state.plan.suggest, isNotEmpty);
    },
  );
}
