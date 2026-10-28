import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/subscriptions/group_ungrouped_screen.dart';
import 'package:xta/subscriptions/group_ungrouped_sorter.dart';
import 'package:xta/subscriptions/group_ungrouped_sorter_screen.dart';

import 'support/groups_harness.dart';

final _alpha = testUser('a', 'alpha_1', 'Alpha');
final _beta = testUser('b', 'beta_2', 'Beta');

Future<void> _tapKey(WidgetTester tester, String key) async {
  await tester.tap(find.byKey(ValueKey(key)));
  await tester.pumpAndSettle();
}

void main() {
  group('the sorting state', () {
    test('placing and skipping move on, one choice for each account', () {
      final state = SorterState([_alpha, _beta]).decide(['art']).decide(const []);
      expect(state.finished, isTrue);
      expect(state.current, isNull);
      expect(state.placedCount, 1);
      expect(state.skipped, [_beta]);
    });

    test('undo makes the last account current again and forgets its choice', () {
      final state = SorterState([_alpha, _beta]).decide(['art']).undo();
      expect(state.current, _alpha);
      expect(state.choices, isEmpty);
      expect(state.backward, isTrue);
      expect(state.decide(['news']).backward, isFalse);
    });

    test('a round of the skipped holds only them, from the start', () {
      final round = SorterState([_alpha, _beta]).decide(const []).decide(['art']).skippedRound();
      expect(round.accounts, [_alpha]);
      expect(round.position, 0);
      expect(round.choices, isEmpty);
    });
  });

  group('the sorter store', () {
    late List<String> writes;

    UngroupedSorterStore store(List<Subscription> accounts, {MembershipWrite? place}) {
      final sorter = UngroupedSorterStore(
        accounts,
        place: place ?? (id, groups) async => writes.add('+$id:${groups.join(',')}'),
        unplace: (id, groups) async => writes.add('-$id:${groups.join(',')}'),
      );
      addTearDown(sorter.destroy);
      return sorter;
    }

    setUp(() => writes = []);

    test('only placing writes, and undo removes what its choice added', () async {
      final sorter = store([_alpha, _beta]);
      expect(await sorter.put(['art', 'news']), isTrue);
      sorter.skip();
      expect(sorter.state.finished, isTrue);

      expect(await sorter.undo(), isTrue);
      expect(writes, ['+a:art,news']);
      expect(await sorter.undo(), isTrue);
      expect(writes, ['+a:art,news', '-a:art,news']);
      expect(sorter.state.current, _alpha);
      expect(await sorter.undo(), isFalse);
    });

    test('a failed save throws and keeps the account', () async {
      final sorter = store([_alpha], place: (_, _) async => throw StateError('disk full'));
      await expectLater(sorter.put(['art']), throwsStateError);
      expect(sorter.state.current, _alpha);
      expect(sorter.state.saving, isFalse);
    });

    test('a tap while a choice is saved is ignored', () async {
      final gate = Completer<void>();
      final sorter = store(
        [_alpha, _beta],
        place: (id, _) {
          writes.add(id);
          return gate.future;
        },
      );
      final first = sorter.put(['art']);
      expect(await sorter.put(['news']), isFalse);
      sorter.skip();
      expect(await sorter.undo(), isFalse);
      expect(sorter.state.position, 0);

      gate.complete();
      expect(await first, isTrue);
      expect(writes, ['a']);
      expect(sorter.state.current, _beta);
    });

    test('nothing to place in puts nothing', () async {
      final sorter = store([_alpha]);
      expect(await sorter.put(const []), isFalse);
      expect(writes, isEmpty);
    });
  });

  group('the sorter screen', () {
    testWidgets('a tap on a group puts the account in it and shows the next', (tester) async {
      final harness = GroupsHarness();
      await harness.pump(tester, UngroupedSorterScreen(accounts: [_alpha, _beta]));
      expect(find.text('Alpha'), findsOneWidget);
      expect(find.text('@alpha_1'), findsOneWidget);
      expect(find.text('1 of 2'), findsOneWidget);

      await _tapKey(tester, 'sorter-group-art');
      expect(harness.groups.groupsOf('a'), ['art']);
      expect(find.text('Beta'), findsOneWidget);
      expect(find.text('Alpha'), findsNothing);
      expect(find.text('2 of 2'), findsOneWidget);
    });

    testWidgets('Skip leaves the account where it is; Undo takes each choice back', (tester) async {
      final harness = GroupsHarness();
      await harness.pump(tester, UngroupedSorterScreen(accounts: [_alpha, _beta]));
      await _tapKey(tester, 'sorter-group-art');
      await _tapKey(tester, 'sorter-skip');
      expect(find.text('Placed 1 subscription'), findsOneWidget);
      expect(find.text('Skipped 1 subscription'), findsOneWidget);
      expect(find.byKey(const ValueKey('sorter-skip')), findsNothing);

      await _tapKey(tester, 'sorter-undo');
      expect(find.text('Beta'), findsOneWidget);
      expect(harness.groups.removed, isEmpty);

      await _tapKey(tester, 'sorter-undo');
      expect(find.text('Alpha'), findsOneWidget);
      expect(harness.groups.removed, ['a:art']);
      expect(harness.groups.groupsOf('a'), isEmpty);
      expect(tester.widget<TextButton>(find.byKey(const ValueKey('sorter-undo'))).onPressed, isNull);
    });

    testWidgets('Several groups places the account in every group ticked', (tester) async {
      final harness = GroupsHarness();
      await harness.pump(tester, UngroupedSorterScreen(accounts: [_alpha]));
      await _tapKey(tester, 'sorter-several');
      await _tapKey(tester, 'group-pick-art');
      await _tapKey(tester, 'group-pick-news');
      await closeSheet(tester);
      expect(harness.groups.groupsOf('a'), ['art', 'news']);
      expect(find.text('Placed 1 subscription'), findsOneWidget);
    });

    testWidgets('closing Several groups with nothing ticked changes nothing', (tester) async {
      final harness = GroupsHarness();
      await harness.pump(tester, UngroupedSorterScreen(accounts: [_alpha]));
      await _tapKey(tester, 'sorter-several');
      await closeSheet(tester);
      expect(harness.groups.members, isEmpty);
      expect(find.text('Alpha'), findsOneWidget);
    });

    testWidgets('one group needs no Several groups', (tester) async {
      final harness = GroupsHarness(groups: [testGroup('art', 'Art')]);
      await harness.pump(tester, UngroupedSorterScreen(accounts: [_alpha]));
      expect(find.byKey(const ValueKey('sorter-several')), findsNothing);
      expect(find.byKey(const ValueKey('sorter-create')), findsOneWidget);
    });

    testWidgets('Create group asks for a name and puts the account in the new group', (tester) async {
      final harness = GroupsHarness();
      await harness.pump(tester, UngroupedSorterScreen(accounts: [_alpha, _beta]));
      await _tapKey(tester, 'sorter-create');
      final create = find.widgetWithText(FilledButton, 'Create');
      expect(tester.widget<FilledButton>(create).onPressed, isNull);

      await tester.enterText(find.byType(TextField), '  Japan ');
      await tester.pump();
      await tester.tap(create);
      await tester.pumpAndSettle();
      expect(harness.groups.groupsOf('a'), ['new-Japan']);
      expect(find.text('Beta'), findsOneWidget);
      expect(find.byKey(const ValueKey('sorter-group-new-Japan')), findsOneWidget);
    });

    testWidgets('the skipped ones can be sorted again', (tester) async {
      final harness = GroupsHarness();
      await harness.pump(tester, UngroupedSorterScreen(accounts: [_alpha, _beta]));
      await _tapKey(tester, 'sorter-skip');
      await _tapKey(tester, 'sorter-skip');
      expect(find.text('Skipped 2 subscriptions'), findsOneWidget);
      expect(find.textContaining('Placed'), findsNothing);

      await tester.tap(find.text('Sort the skipped ones'));
      await tester.pumpAndSettle();
      expect(find.text('Alpha'), findsOneWidget);
      expect(find.text('1 of 2'), findsOneWidget);
    });

    testWidgets('a failed save keeps the card and says so', (tester) async {
      final harness = GroupsHarness()..groups.failing = true;
      await harness.pump(tester, UngroupedSorterScreen(accounts: [_alpha, _beta]));
      await _tapKey(tester, 'sorter-group-art');
      expect(find.text('Could not apply the groups.'), findsOneWidget);
      expect(find.text('Alpha'), findsOneWidget);
      expect(find.text('1 of 2'), findsOneWidget);
    });

    testWidgets('placing an account clicks once; skipping does not', (tester) async {
      final taps = <Object?>[];
      tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(SystemChannels.platform, (call) async {
        if (call.method == 'HapticFeedback.vibrate') taps.add(call.arguments);
        return null;
      });
      addTearDown(() => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(SystemChannels.platform, null));
      final harness = GroupsHarness();
      await harness.pump(tester, UngroupedSorterScreen(accounts: [_alpha, _beta]));
      await _tapKey(tester, 'sorter-group-art');
      await _tapKey(tester, 'sorter-skip');
      expect(taps, ['HapticFeedbackType.selectionClick']);
    });

    testWidgets('Open profile leads to the account', (tester) async {
      final harness = GroupsHarness();
      await harness.pump(tester, UngroupedSorterScreen(accounts: [_alpha]));
      await _tapKey(tester, 'sorter-open-profile');
      expect(find.text('$routeProfile a'), findsOneWidget);
    });

    testWidgets('with nobody to sort it says everyone is in a group', (tester) async {
      final harness = GroupsHarness();
      await harness.pump(tester, const UngroupedSorterScreen(accounts: []));
      expect(find.text('Every subscription is already in a group.'), findsOneWidget);
      expect(find.byKey(const ValueKey('sorter-skip')), findsNothing);
      expect(find.textContaining(' of '), findsNothing);
    });
  });

  group('Sort ungrouped', () {
    final nasa = testUser('nasa', 'nasa', 'NASA');
    final esa = testUser('esa', 'esa', 'ESA Space');
    final mika = testUser('mk', 'mk_t0927', 'Mika');

    GroupsHarness harnessWith(List<Subscription> subscriptions) {
      final harness = GroupsHarness(groups: [testGroup('space', 'Space')], subscriptions: subscriptions);
      harness.groups.members.add(SubscriptionGroupMember(group: 'space', profile: 'nasa'));
      return harness;
    }

    testWidgets('Left ungrouped leads to the sorter, and the plan is made again after it', (tester) async {
      final harness = harnessWith([nasa, esa, mika]);
      await harness.pump(tester, const SortUngroupedScreen());
      expect(find.text('esa → Space'), findsOneWidget);
      expect(find.text('@mk_t0927'), findsOneWidget);

      await _tapKey(tester, 'sort-one-by-one');
      expect(find.text('Mika'), findsOneWidget);
      await _tapKey(tester, 'sorter-group-space');
      await tester.tap(find.widgetWithText(FilledButton, 'Finish'));
      await tester.pumpAndSettle();

      expect(harness.groups.groupsOf('mk'), ['space']);
      expect(find.text('esa → Space'), findsOneWidget);
      expect(find.byKey(const ValueKey('sort-one-by-one')), findsNothing);
      expect(harness.groups.applied, isNull);
    });

    testWidgets('with no match by name the main button sorts one by one', (tester) async {
      final harness = harnessWith([nasa, mika]);
      await harness.pump(tester, const SortUngroupedScreen());
      expect(find.widgetWithText(FilledButton, 'Apply'), findsNothing);

      await tester.tap(find.widgetWithText(FilledButton, 'Sort one by one'));
      await tester.pumpAndSettle();
      expect(find.text('Mika'), findsOneWidget);
      expect(find.text('1 of 1'), findsOneWidget);
    });

    testWidgets('Apply places the plan, then goes on to the accounts left over', (tester) async {
      final harness = harnessWith([nasa, esa, mika]);
      await harness.pump(tester, const SortUngroupedScreen());
      await tester.tap(find.widgetWithText(FilledButton, 'Apply'));
      await tester.pumpAndSettle();

      expect(harness.groups.applied?.assign.single.accountId, 'esa');
      expect(find.text('Placed 1 subscription'), findsOneWidget);
      expect(find.text('Mika'), findsOneWidget);
      expect(find.byType(SortUngroupedScreen), findsNothing);
    });

    testWidgets('Apply closes the screen when it placed everyone', (tester) async {
      final harness = harnessWith([nasa, esa]);
      await harness.pump(
        tester,
        Builder(
          builder: (context) => TextButton(
            onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const SortUngroupedScreen())),
            child: const Text('open'),
          ),
        ),
      );
      await tester.tap(find.text('open'));
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(FilledButton, 'Apply'));
      await tester.pumpAndSettle();

      expect(find.byType(SortUngroupedScreen), findsNothing);
      expect(find.byType(UngroupedSorterScreen), findsNothing);
      expect(find.text('open'), findsOneWidget);
    });
  });
}
