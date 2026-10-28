import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/subscriptions/group_membership_sheet.dart';
import 'package:xta/subscriptions/group_pick.dart';
import 'package:xta/ui/quick_pick_sheet.dart';
import 'package:xta/user.dart';

import 'support/groups_harness.dart';

Finder _chip(String id) => find.byKey(ValueKey('group-pick-$id'));

bool _ticked(WidgetTester tester, String id) => tester.widget<QuickPickChip>(_chip(id)).selected;

String _status(WidgetTester tester) => tester.widget<Text>(find.byKey(kQuickPickStatusKey)).data!;

Future<void> _tap(WidgetTester tester, Finder finder) async {
  await tester.tap(finder);
  await tester.pumpAndSettle();
}

/// What the sheet answered once it closed.
class _Answer {
  List<String>? chosen;
  var closed = false;
}

/// Opens the sheet on a page of its own, keeping its answer in [answer].
Future<void> _open(
  WidgetTester tester,
  GroupsHarness harness,
  _Answer answer, {
  List<String> selected = const [],
}) async {
  await harness.pump(
    tester,
    Scaffold(
      body: Builder(
        builder: (context) => TextButton(
          onPressed: () async {
            answer.chosen = await showGroupMembershipSheet(context, groups: harness.groups.state, selected: selected);
            answer.closed = true;
          },
          child: const Text('open'),
        ),
      ),
    ),
  );
  await _tap(tester, find.text('open'));
}

List<String> _names(int count) => [for (var i = 0; i < count; i++) 'g$i'];

void main() {
  group('the order the chips open in', () {
    final groups = [
      for (final id in ['a', 'b', 'c', 'd']) testGroup(id, id.toUpperCase()),
    ];

    test('the account\'s groups lead, then the groups picked last, then the rest', () {
      expect(groupPickOrder(groups, selected: ['c'], recent: ['d', 'gone', 'c']), ['c', 'd', 'a', 'b']);
    });

    test('recent picks put the groups added first, twelve at most', () async {
      final prefs = PrefServiceCache(defaults: {optionSubscriptionGroupsRecentPicks: _names(12).join(',')});
      await rememberGroupPicks(prefs, ['new', 'g3']);
      expect(recentGroupPicks(prefs), ['new', 'g3', 'g0', 'g1', 'g2', 'g4', 'g5', 'g6', 'g7', 'g8', 'g9', 'g10']);
    });
  });

  group('the sheet', () {
    final three = [testGroup('art', 'Art'), testGroup('news', 'News'), testGroup('japan', 'Japan')];

    testWidgets('ticking changes the status line, and closing gives the choice', (tester) async {
      final harness = GroupsHarness(groups: three);
      final answer = _Answer();
      await _open(tester, harness, answer);
      expect(find.text('Add to group'), findsOneWidget);
      expect(_status(tester), 'Not in a group');

      await _tap(tester, _chip('art'));
      expect(_status(tester), 'In Art');
      await _tap(tester, _chip('news'));
      expect(_status(tester), 'In Art and News');
      await _tap(tester, _chip('japan'));
      expect(_status(tester), 'In 3 groups');
      await _tap(tester, _chip('japan'));
      expect(_ticked(tester, 'japan'), isFalse);
      expect(_status(tester), 'In Art and News');

      await closeSheet(tester);
      expect(answer.closed, isTrue);
      expect(answer.chosen, unorderedEquals(['art', 'news']));
      expect(recentGroupPicks(harness.prefs), unorderedEquals(['art', 'news']));
    });

    testWidgets('closing with the groups it opened with gives nothing', (tester) async {
      final harness = GroupsHarness(groups: three);
      final answer = _Answer();
      await _open(tester, harness, answer, selected: ['art']);
      expect(_ticked(tester, 'art'), isTrue);
      expect(_status(tester), 'In Art');

      await _tap(tester, _chip('news'));
      await _tap(tester, _chip('news'));
      await closeSheet(tester);
      expect(answer.closed, isTrue);
      expect(answer.chosen, isNull);
      expect(recentGroupPicks(harness.prefs), isEmpty);
    });

    testWidgets('a typed name creates a group, ticks it and empties the field', (tester) async {
      final harness = GroupsHarness(groups: three);
      final answer = _Answer();
      await _open(tester, harness, answer);
      await tester.enterText(find.byType(TextField), 'Kyoto');
      await tester.pumpAndSettle();
      expect(find.text('No group found'), findsOneWidget);

      await _tap(tester, find.byKey(kQuickPickCreateKey));
      expect(harness.groups.state.map((group) => group.name), contains('Kyoto'));
      expect(_ticked(tester, 'new-Kyoto'), isTrue);
      expect(tester.widget<TextField>(find.byType(TextField)).controller!.text, isEmpty);
      expect(tester.getTopLeft(_chip('new-Kyoto')).dx, lessThan(tester.getTopLeft(_chip('art')).dx));
      expect(_status(tester), 'In Kyoto');

      await closeSheet(tester);
      expect(answer.chosen, ['new-Kyoto']);
    });

    testWidgets('a name a group has offers no create row', (tester) async {
      final harness = GroupsHarness(groups: three);
      await _open(tester, harness, _Answer());
      await tester.enterText(find.byType(TextField), 'NEWS');
      await tester.pumpAndSettle();
      expect(find.text('Matches'), findsOneWidget);
      expect(_chip('news'), findsOneWidget);
      expect(_chip('art'), findsNothing);
      expect(find.byKey(kQuickPickCreateKey), findsNothing);
    });

    testWidgets('All groups shows the groups beyond the first six', (tester) async {
      final harness = GroupsHarness(groups: [for (final name in _names(8)) testGroup(name, name)]);
      await _open(tester, harness, _Answer());
      expect(_chip('g5'), findsOneWidget);
      expect(_chip('g6'), findsNothing);
      expect(find.text('Recently used'), findsOneWidget);

      await _tap(tester, find.byKey(kQuickPickAllKey));
      expect(_chip('g7'), findsOneWidget);
      expect(find.text('All groups'), findsOneWidget);
      expect(find.byKey(kQuickPickAllKey), findsNothing);
    });

    testWidgets('an account in more groups than six shows them all first', (tester) async {
      final harness = GroupsHarness(groups: [for (final name in _names(9)) testGroup(name, name)]);
      await _open(tester, harness, _Answer(), selected: ['g8', 'g7', 'g6', 'g5', 'g4', 'g3', 'g2']);
      for (final id in ['g2', 'g3', 'g4', 'g5', 'g6', 'g7', 'g8']) {
        expect(_ticked(tester, id), isTrue);
      }
      expect(_chip('g0'), findsNothing);
      expect(find.byKey(kQuickPickAllKey), findsOneWidget);
      expect(_status(tester), 'In 7 groups');
    });

    testWidgets('with no groups yet it says so and offers to create one', (tester) async {
      final harness = GroupsHarness(groups: const []);
      await _open(tester, harness, _Answer());
      expect(find.text('No groups yet'), findsOneWidget);
      await tester.enterText(find.byType(TextField), 'Art');
      await tester.pumpAndSettle();
      expect(find.byKey(kQuickPickCreateKey), findsOneWidget);
    });
  });

  group('Add to group', () {
    final user = testUser('u1', 'mk_t0927', 'Mika');

    Future<void> openFor(WidgetTester tester, GroupsHarness harness) async {
      await harness.pump(
        tester,
        Scaffold(
          body: Builder(
            builder: (context) => TextButton(
              onPressed: () => pickUserGroups(context, user: user, followed: false, groupsForUser: const []),
              child: const Text('open'),
            ),
          ),
        ),
      );
      await _tap(tester, find.text('open'));
    }

    testWidgets('follows a new account and saves its groups once the sheet closes changed', (tester) async {
      final harness = GroupsHarness();
      await openFor(tester, harness);
      await _tap(tester, _chip('art'));
      expect(harness.subscriptions.toggled, isEmpty);

      await closeSheet(tester);
      expect(harness.subscriptions.toggled, ['u1']);
      expect(harness.groups.saved['u1'], ['art']);
    });

    testWidgets('closing the sheet unchanged neither follows nor saves', (tester) async {
      final harness = GroupsHarness();
      await openFor(tester, harness);
      await closeSheet(tester);
      expect(harness.subscriptions.toggled, isEmpty);
      expect(harness.groups.saved, isEmpty);
    });
  });
}
