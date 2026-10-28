import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/profile/profile.dart';
import 'package:xta/subscriptions/group_ungrouped.dart';
import 'package:xta/subscriptions/group_ungrouped_screen.dart';
import 'package:xta/subscriptions/group_ungrouped_sorter.dart';
import 'package:xta/subscriptions/group_ungrouped_sorter_screen.dart';
import 'package:xta/subscriptions/users_model.dart';

UserSubscription _user(String id, String handle, String name) => UserSubscription(
  id: id,
  screenName: handle,
  name: name,
  profileImageUrlHttps: null,
  verified: false,
  createdAt: DateTime(2020),
  inFeed: true,
);

SubscriptionGroup _group(String id, String name) =>
    SubscriptionGroup(id: id, name: name, icon: '', color: null, numberOfMembers: 0, createdAt: DateTime(2020));

final _alpha = _user('a', 'alpha_1', 'Alpha');
final _beta = _user('b', 'beta_2', 'Beta');

/// Memberships in memory; writes throw while [failing].
class _FakeGroups extends GroupsModel {
  _FakeGroups(super.prefs, List<SubscriptionGroup> groups) {
    update(groups);
  }

  final members = <SubscriptionGroupMember>[];
  final removed = <String>[];
  var failing = false;
  GroupUngroupedPlan? applied;

  List<String> groupsOf(String profile) => [
    for (final member in members)
      if (member.profile == profile) member.group,
  ];

  @override
  Future<void> addToGroups(String profile, Iterable<String> groupIds) async {
    if (failing) throw StateError('disk full');
    members.addAll([for (final group in groupIds) SubscriptionGroupMember(group: group, profile: profile)]);
  }

  @override
  Future<void> removeFromGroups(String profile, Iterable<String> groupIds) async {
    removed.add('$profile:${groupIds.join(',')}');
    members.removeWhere((member) => member.profile == profile && groupIds.contains(member.group));
  }

  @override
  Future<String> createGroup(String name) async {
    final id = 'new-$name';
    update([...state, _group(id, name)]);
    return id;
  }

  @override
  Future<List<SubscriptionGroupMember>> listGroupMembers() async => List.of(members);

  @override
  Future<int> applyUngroupedPlan(GroupUngroupedPlan plan) async {
    applied = plan;
    members.addAll([
      for (final row in plan.assign) SubscriptionGroupMember(group: row.groupId, profile: row.accountId),
    ]);
    return plan.moveCount;
  }
}

class _Harness {
  final prefs = PrefServiceCache(defaults: {optionDisableAnimations: true});
  late final _FakeGroups groups;
  late final SubscriptionsModel subscriptions;

  _Harness({List<SubscriptionGroup>? groups, List<Subscription> subscriptions = const []}) {
    this.groups = _FakeGroups(prefs, groups ?? [_group('art', 'Art'), _group('news', 'News')]);
    this.subscriptions = SubscriptionsModel(prefs, this.groups)..update(subscriptions);
  }

  Future<void> pump(WidgetTester tester, Widget home) async {
    await tester.pumpWidget(
      PrefService(
        service: prefs,
        child: MultiProvider(
          providers: [
            Provider<GroupsModel>.value(value: groups),
            Provider<SubscriptionsModel>.value(value: subscriptions),
          ],
          child: MaterialApp(
            localizationsDelegates: const [
              L10n.delegate,
              GlobalMaterialLocalizations.delegate,
              GlobalWidgetsLocalizations.delegate,
              GlobalCupertinoLocalizations.delegate,
            ],
            supportedLocales: L10n.delegate.supportedLocales,
            onGenerateRoute: (settings) => MaterialPageRoute(
              builder: (_) =>
                  Scaffold(body: Text('${settings.name} ${(settings.arguments as ProfileScreenArguments?)?.id}')),
            ),
            home: home,
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
  }
}

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
      final harness = _Harness();
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
      final harness = _Harness();
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
      final harness = _Harness();
      await harness.pump(tester, UngroupedSorterScreen(accounts: [_alpha]));
      await _tapKey(tester, 'sorter-several');
      await tester.tap(find.widgetWithText(CheckboxListTile, 'Art'));
      await tester.tap(find.widgetWithText(CheckboxListTile, 'News'));
      await tester.pump();
      await tester.tap(find.widgetWithText(FilledButton, 'Save'));
      await tester.pumpAndSettle();
      expect(harness.groups.groupsOf('a'), ['art', 'news']);
      expect(find.text('Placed 1 subscription'), findsOneWidget);
    });

    testWidgets('closing Several groups with nothing ticked changes nothing', (tester) async {
      final harness = _Harness();
      await harness.pump(tester, UngroupedSorterScreen(accounts: [_alpha]));
      await _tapKey(tester, 'sorter-several');
      await tester.tap(find.widgetWithText(FilledButton, 'Save'));
      await tester.pumpAndSettle();
      expect(harness.groups.members, isEmpty);
      expect(find.text('Alpha'), findsOneWidget);
    });

    testWidgets('one group needs no Several groups', (tester) async {
      final harness = _Harness(groups: [_group('art', 'Art')]);
      await harness.pump(tester, UngroupedSorterScreen(accounts: [_alpha]));
      expect(find.byKey(const ValueKey('sorter-several')), findsNothing);
      expect(find.byKey(const ValueKey('sorter-create')), findsOneWidget);
    });

    testWidgets('Create group asks for a name and puts the account in the new group', (tester) async {
      final harness = _Harness();
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
      final harness = _Harness();
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
      final harness = _Harness()..groups.failing = true;
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
      final harness = _Harness();
      await harness.pump(tester, UngroupedSorterScreen(accounts: [_alpha, _beta]));
      await _tapKey(tester, 'sorter-group-art');
      await _tapKey(tester, 'sorter-skip');
      expect(taps, ['HapticFeedbackType.selectionClick']);
    });

    testWidgets('Open profile leads to the account', (tester) async {
      final harness = _Harness();
      await harness.pump(tester, UngroupedSorterScreen(accounts: [_alpha]));
      await _tapKey(tester, 'sorter-open-profile');
      expect(find.text('$routeProfile a'), findsOneWidget);
    });

    testWidgets('with nobody to sort it says everyone is in a group', (tester) async {
      final harness = _Harness();
      await harness.pump(tester, const UngroupedSorterScreen(accounts: []));
      expect(find.text('Every subscription is already in a group.'), findsOneWidget);
      expect(find.byKey(const ValueKey('sorter-skip')), findsNothing);
      expect(find.textContaining(' of '), findsNothing);
    });
  });

  group('Sort ungrouped', () {
    final nasa = _user('nasa', 'nasa', 'NASA');
    final esa = _user('esa', 'esa', 'ESA Space');
    final mika = _user('mk', 'mk_t0927', 'Mika');

    _Harness harnessWith(List<Subscription> subscriptions) {
      final harness = _Harness(groups: [_group('space', 'Space')], subscriptions: subscriptions);
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
