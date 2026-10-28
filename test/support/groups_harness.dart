import 'package:flutter/material.dart';
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
import 'package:xta/subscriptions/users_model.dart';

UserSubscription testUser(String id, String handle, String name) => UserSubscription(
  id: id,
  screenName: handle,
  name: name,
  profileImageUrlHttps: null,
  verified: false,
  createdAt: DateTime(2020),
  inFeed: true,
);

SubscriptionGroup testGroup(String id, String name) =>
    SubscriptionGroup(id: id, name: name, icon: '', color: null, numberOfMembers: 0, createdAt: DateTime(2020));

/// Groups and memberships in memory; membership writes throw while [failing].
class FakeGroups extends GroupsModel {
  FakeGroups(super.prefs, List<SubscriptionGroup> groups) {
    update(groups);
  }

  final members = <SubscriptionGroupMember>[];
  final removed = <String>[];

  /// What [saveUserGroupMembership] was asked to save, by account.
  final saved = <String, List<String>>{};
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
    update([...state, testGroup(id, name)]);
    return id;
  }

  @override
  Future<List<SubscriptionGroupMember>> listGroupMembers() async => List.of(members);

  @override
  Future<List<String>> listGroupsForUser(String user) async => groupsOf(user);

  @override
  Future saveUserGroupMembership(String user, List<String> memberships) async {
    saved[user] = memberships;
    members
      ..removeWhere((member) => member.profile == user)
      ..addAll([for (final group in memberships) SubscriptionGroupMember(group: group, profile: user)]);
  }

  @override
  Future<int> applyUngroupedPlan(GroupUngroupedPlan plan) async {
    applied = plan;
    members.addAll([
      for (final row in plan.assign) SubscriptionGroupMember(group: row.groupId, profile: row.accountId),
    ]);
    return plan.moveCount;
  }
}

/// Follows in memory; [toggled] lists every account followed or unfollowed.
class FakeSubscriptions extends SubscriptionsModel {
  FakeSubscriptions(super.prefs, super.groupModel, List<Subscription> subscriptions) {
    update(subscriptions);
  }

  final toggled = <String>[];

  @override
  Future<void> toggleSubscribe(Subscription user, bool currentlyFollowed) async {
    toggled.add(user.id);
    update(currentlyFollowed ? state.where((item) => item.id != user.id).toList() : [...state, user]);
  }
}

/// The groups and follows a screen reads, in memory, and a way to pump it.
class GroupsHarness {
  final PrefServiceCache prefs;
  late final FakeGroups groups;
  late final FakeSubscriptions subscriptions;

  GroupsHarness({
    List<SubscriptionGroup>? groups,
    List<Subscription> subscriptions = const [],
    Map<String, dynamic> cache = const {},
  }) : prefs = PrefServiceCache(defaults: {optionDisableAnimations: true, ...cache}) {
    this.groups = FakeGroups(prefs, groups ?? [testGroup('art', 'Art'), testGroup('news', 'News')]);
    this.subscriptions = FakeSubscriptions(prefs, this.groups, subscriptions);
  }

  Future<void> pump(WidgetTester tester, Widget home, {ThemeData? theme, Locale? locale, Key? shotKey}) async {
    await tester.pumpWidget(
      PrefService(
        service: prefs,
        child: MultiProvider(
          providers: [
            Provider<GroupsModel>.value(value: groups),
            Provider<SubscriptionsModel>.value(value: subscriptions),
          ],
          child: MaterialApp(
            debugShowCheckedModeBanner: false,
            theme: theme,
            locale: locale,
            localizationsDelegates: const [
              L10n.delegate,
              GlobalMaterialLocalizations.delegate,
              GlobalWidgetsLocalizations.delegate,
              GlobalCupertinoLocalizations.delegate,
            ],
            supportedLocales: L10n.delegate.supportedLocales,
            builder: (context, child) => RepaintBoundary(key: shotKey, child: child),
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

/// Closes an open sheet the way a reader would, with a tap above it.
Future<void> closeSheet(WidgetTester tester) async {
  await tester.tapAt(const Offset(20, 20));
  await tester.pumpAndSettle();
}
