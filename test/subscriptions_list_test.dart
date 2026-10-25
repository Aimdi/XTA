import 'dart:async';

import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/main.dart';
import 'package:xta/subscriptions/_list.dart';
import 'package:xta/subscriptions/users_model.dart';

/// Reloads wait on [held] for the list they read, as one does while a change
/// is being saved.
class _HeldSubscriptions extends SubscriptionsModel {
  Completer<List<Subscription>>? held;

  _HeldSubscriptions(super.prefs, super.groupModel);

  @override
  Future<List<Subscription>> readSnapshot(Future<List<Subscription>> Function() read) => (held = Completer()).future;

  /// A reload a failed test left held never ends, since tear-downs run outside
  /// the test's fake clock: waiting on it would hang the run.
  @override
  Future destroy() async {
    if (held?.isCompleted ?? true) await super.destroy();
  }
}

/// A queued reload starts in a microtask, after the frame the first pump
/// draws: the second draws the list as it is while the change is saved.
Future<void> _startSaving(WidgetTester tester) async {
  await tester.pump();
  await tester.pump();
}

List<Subscription> _searches(Iterable<String> ids) => [
  for (final id in ids) SearchSubscription(id: id, createdAt: DateTime(2026)),
];

/// The subscriptions page, with the models above the app as in main.dart: a
/// dragged row is drawn in the overlay, outside the page.
Future<({BasePrefService prefs, _HeldSubscriptions subscriptions, ScrollController scroll})> _pumpPage(
  WidgetTester tester,
  List<String> ids,
) async {
  final prefs = PrefServiceCache(cache: {optionSubscriptionOrderCustom: ''});
  final groups = GroupsModel(prefs);
  final subscriptions = _HeldSubscriptions(prefs, groups)..update(_searches(ids));
  final scroll = ScrollController();
  addTearDown(() async {
    scroll.dispose();
    await subscriptions.destroy();
    await groups.destroy();
  });

  await tester.pumpWidget(
    PrefService(
      service: prefs,
      child: MultiProvider(
        providers: [
          Provider<GroupsModel>.value(value: groups),
          Provider<SubscriptionsModel>.value(value: subscriptions),
        ],
        child: MaterialApp(
          localizationsDelegates: xtaLocalizationsDelegates,
          supportedLocales: L10n.delegate.supportedLocales,
          home: Scaffold(body: SubscriptionUsersPage(scrollController: scroll)),
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
  return (prefs: prefs, subscriptions: subscriptions, scroll: scroll);
}

void main() {
  testWidgets('a subscription can be dragged to a new place', (tester) async {
    final page = await _pumpPage(tester, ['first', 'second', 'third']);

    final row = tester.getCenter(find.text('second')).dy - tester.getCenter(find.text('first')).dy;
    final drag = await tester.startGesture(tester.getCenter(find.text('first')));
    await tester.pump(kLongPressTimeout + const Duration(milliseconds: 100));
    // Into the second half of the next row: the two swap places.
    await drag.moveBy(Offset(0, row * 0.75));
    await tester.pump(const Duration(milliseconds: 100));

    expect(tester.takeException(), isNull, reason: 'the dragged row is drawn outside the page and its Material');
    expect(find.text('first'), findsWidgets, reason: 'the row being dragged is still shown');

    await drag.up();
    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
    expect(page.prefs.get<String>(optionSubscriptionOrderCustom), 'second,first,third');
  });

  testWidgets('saving a change keeps the list where the reader had it, with no spinner', (tester) async {
    final ids = [for (var i = 0; i < 40; i++) 'search $i'];
    final page = await _pumpPage(tester, ids);
    page.scroll.jumpTo(600);
    await tester.pump();

    final saved = page.subscriptions.reloadSubscriptions();
    await _startSaving(tester);
    expect(page.subscriptions.isLoading, isTrue);
    expect(find.byType(CircularProgressIndicator), findsNothing);
    expect(find.text('search 12'), findsOneWidget);
    expect(page.scroll.offset, 600);

    page.subscriptions.held!.complete(_searches(ids.skip(1)));
    await saved;
    await tester.pump();
    expect(find.text('search 0'), findsNothing);
    expect(find.text('search 12'), findsOneWidget);
    expect(page.scroll.offset, 600);
  });

  testWidgets('a first load shows the spinner until there is a list', (tester) async {
    final page = await _pumpPage(tester, const []);

    final loaded = page.subscriptions.reloadSubscriptions();
    await _startSaving(tester);
    expect(find.byType(CircularProgressIndicator), findsOneWidget);

    page.subscriptions.held!.complete(_searches(['first']));
    await loaded;
    await tester.pump();
    expect(find.byType(CircularProgressIndicator), findsNothing);
    expect(find.text('first'), findsOneWidget);
  });
}
