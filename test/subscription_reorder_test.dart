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

/// The subscriptions page, with the models above the app as in main.dart: a
/// dragged row is drawn in the overlay, outside the page.
Future<BasePrefService> _pumpPage(WidgetTester tester, List<String> ids) async {
  final prefs = PrefServiceCache(cache: {optionSubscriptionOrderCustom: ''});
  final groups = GroupsModel(prefs);
  final subscriptions = SubscriptionsModel(prefs, groups)
    ..update([for (final id in ids) SearchSubscription(id: id, createdAt: DateTime(2026))]);
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
  return prefs;
}

void main() {
  testWidgets('a subscription can be dragged to a new place', (tester) async {
    final prefs = await _pumpPage(tester, ['first', 'second', 'third']);

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
    expect(prefs.get<String>(optionSubscriptionOrderCustom), 'second,first,third');
  });
}
