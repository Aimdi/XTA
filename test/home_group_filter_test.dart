import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/home/home_account_filter.dart';
import 'package:xta/home/home_group_filter.dart';

UserSubscription _sub(String id, {bool inFeed = true}) {
  return UserSubscription(
    id: id,
    screenName: id,
    name: id,
    profileImageUrlHttps: null,
    verified: false,
    createdAt: DateTime.utc(2026),
    inFeed: inFeed,
  );
}

void main() {
  test('profileIdsExcludedByGroups unions members of disabled groups', () {
    final members = [
      SubscriptionGroupMember(group: 'nsfw', profile: 'a'),
      SubscriptionGroupMember(group: 'nsfw', profile: 'b'),
      SubscriptionGroupMember(group: 'sfw', profile: 'c'),
      SubscriptionGroupMember(group: 'sfw', profile: 'a'),
    ];
    expect(
      profileIdsExcludedByGroups(
        members: members,
        disabledGroupIds: {'nsfw'},
      ),
      {'a', 'b'},
    );
    expect(
      profileIdsExcludedByGroups(members: members, disabledGroupIds: const {}),
      isEmpty,
    );
  });

  test('turning off a parent also hides nested group members', () {
    final members = [
      SubscriptionGroupMember(group: 'art', profile: 'parent-only'),
      SubscriptionGroupMember(group: 'nsfw', profile: 'child-only'),
    ];
    expect(
      profileIdsExcludedByGroups(
        members: members,
        disabledGroupIds: {'art'},
        parentOf: {'nsfw': 'art', 'art': null},
      ),
      {'parent-only', 'child-only'},
    );
  });

  test('subscriptionAllowedInFollowing honours inFeed and group exclusion', () {
    expect(subscriptionAllowedInFollowing(_sub('a'), const {}), isTrue);
    expect(subscriptionAllowedInFollowing(_sub('a', inFeed: false), const {}), isFalse);
    expect(subscriptionAllowedInFollowing(_sub('a'), {'a'}), isFalse);
    expect(subscriptionAllowedInFollowing(_sub('a'), {'b'}), isTrue);
  });

  test('HomeGroupFilterStore persists disabled group ids', () async {
    final prefs = PrefServiceCache(cache: {optionHomeFeedDisabledGroupIds: '[]'});
    final store = HomeGroupFilterStore(prefs);
    await store.setEnabled('nsfw', false);
    expect(store.state, {'nsfw'});
    expect(prefs.get(optionHomeFeedDisabledGroupIds), '["nsfw"]');
    await store.setEnabled('nsfw', true);
    expect(store.state, isEmpty);
  });

  testWidgets('a group can be switched off Following', (tester) async {
    var enabled = true;
    final group = SubscriptionGroup(
      id: 'nsfw',
      name: 'Art NSFW',
      icon: defaultGroupIcon,
      color: null,
      numberOfMembers: 3,
      createdAt: DateTime.utc(2026),
    );
    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: const [
          L10n.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        supportedLocales: L10n.delegate.supportedLocales,
        locale: const Locale('en'),
        home: Scaffold(
          body: HomeGroupToggleTile(
            group: group,
            disabled: const {},
            onChanged: (value) async => enabled = value,
          ),
        ),
      ),
    );
    await tester.pump();

    expect(find.text('Art NSFW'), findsOneWidget);
    expect(find.text('Include in Following'), findsOneWidget);
    await tester.tap(find.byType(SwitchListTile));
    await tester.pump();
    expect(enabled, isFalse);
  });
}
