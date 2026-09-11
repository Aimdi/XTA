import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/subscriptions/_groups_edit.dart';
import 'package:xta/subscriptions/users_model.dart';

class _Groups extends GroupsModel {
  _Groups(super.prefs);
  Future<SubscriptionGroupEdit> Function() load = () async => throw StateError('Read failed');

  @override
  Future<SubscriptionGroupEdit> loadGroupEdit(String? id) => load();
}

SubscriptionGroupEdit _emptyGroup() =>
    SubscriptionGroupEdit(id: null, name: '', icon: defaultGroupIcon, color: null, members: {});

void main() {
  late PrefServiceCache prefs;
  late _Groups groups;
  late SubscriptionsModel subscriptions;

  setUp(() {
    prefs = PrefServiceCache();
    groups = _Groups(prefs);
    subscriptions = SubscriptionsModel(prefs, groups);
  });

  tearDown(() async {
    await subscriptions.destroy();
    await groups.destroy();
  });

  Widget app() => PrefService(
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
        home: Scaffold(
          body: SubscriptionGroupEditDialog(id: null, name: '', icon: defaultGroupIcon),
        ),
      ),
    ),
  );

  testWidgets('read failure shows Retry and can load the editor again', (tester) async {
    await tester.pumpWidget(app());
    await tester.pumpAndSettle();
    expect(find.byType(CircularProgressIndicator), findsNothing);
    expect(find.text('Retry'), findsOneWidget);
    groups.load = () async => _emptyGroup();
    await tester.tap(find.text('Retry'));
    await tester.pumpAndSettle();
    expect(find.byType(Form), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('closing the editor during loading ignores the late result', (tester) async {
    final result = Completer<SubscriptionGroupEdit>();
    groups.load = () => result.future;
    await tester.pumpWidget(app());
    await tester.pumpWidget(const SizedBox());
    result.complete(_emptyGroup());
    await tester.pump();
    expect(tester.takeException(), isNull);
  });

  testWidgets('a stalled read leaves loading and offers Retry', (tester) async {
    groups.load = () => Completer<SubscriptionGroupEdit>().future;
    await tester.pumpWidget(app());
    await tester.pump(const Duration(seconds: 16));
    await tester.pumpAndSettle();
    expect(find.byType(CircularProgressIndicator), findsNothing);
    expect(find.text('Retry'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('idle stores can be disposed outside the widget clock', (tester) async {
    final idleGroups = _Groups(prefs);
    final idleSubscriptions = SubscriptionsModel(prefs, idleGroups);
    await tester.pump();
    await tester.runAsync(() async {
      await idleSubscriptions.destroy().timeout(const Duration(seconds: 1));
      await idleGroups.destroy().timeout(const Duration(seconds: 1));
    });
    expect(tester.takeException(), isNull);
  });
}
