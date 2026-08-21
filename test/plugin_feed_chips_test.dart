import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/subscriptions/_groups.dart';

SubscriptionGroup _group(String id, String name) => SubscriptionGroup(
  id: id,
  name: name,
  icon: defaultGroupIcon,
  color: null,
  numberOfMembers: 4,
  createdAt: DateTime.utc(2026),
);

class _FakeGroupsModel extends GroupsModel {
  _FakeGroupsModel(super.prefs, List<SubscriptionGroup> groups) {
    update(groups);
  }
}

Map<String, Object> _enabledPluginsHiddenTabs() => {
  optionPluginThreadsEnabled: true,
  optionPluginThreadsShowTab: false,
  optionPluginBlueskyEnabled: true,
  optionPluginBlueskyShowTab: false,
  optionPluginMastodonEnabled: true,
  optionPluginMastodonShowTab: false,
  optionPluginTiktokEnabled: true,
  optionPluginTiktokShowTab: false,
  optionPluginRedditEnabled: true,
  optionPluginRedditShowTab: false,
  optionPluginSubstackEnabled: true,
  optionPluginSubstackShowTab: false,
  optionPluginPixivEnabled: true,
  optionPluginPixivShowTab: false,
  optionPluginBooruEnabled: true,
  optionPluginBooruShowTab: false,
};

PrefServiceCache _prefs([Map<String, Object> extra = const {}]) =>
    PrefServiceCache(
      cache: {
        optionSubscriptionGroupsOrderByField: 'name',
        optionSubscriptionGroupsOrderByAscending: true,
        optionSubscriptionGroupsLayout: subscriptionGroupsLayoutBoard,
        optionDisableAnimations: true,
        ...extra,
      },
    );

Widget _page(BasePrefService prefs, List<SubscriptionGroup> groups) {
  return PrefService(
    service: prefs,
    child: Provider<GroupsModel>(
      create: (_) => _FakeGroupsModel(prefs, groups),
      child: MaterialApp(
        localizationsDelegates: const [
          L10n.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        supportedLocales: L10n.delegate.supportedLocales,
        home: Scaffold(
          body: SubscriptionGroupsPage(scrollController: ScrollController()),
        ),
      ),
    ),
  );
}

void main() {
  final groups = [_group('a', 'Ai'), _group('b', 'Ai Art')];

  testWidgets('enabled plugins are not listed on the Groups board', (
    tester,
  ) async {
    await tester.pumpWidget(_page(_prefs(_enabledPluginsHiddenTabs()), groups));
    await tester.pump();

    for (final name in [
      'Threads',
      'Bluesky',
      'Mastodon',
      'TikTok',
      'Reddit',
      'Substack',
      'Pixiv',
      'Booru',
    ]) {
      expect(find.text(name), findsNothing, reason: name);
    }
    expect(find.byType(Wrap), findsNothing);
    expect(find.text('Ai'), findsOneWidget);
    expect(find.text('Ai Art'), findsOneWidget);
  });

  testWidgets('the board is not wrapped in AnimatedSwitcher', (tester) async {
    await tester.pumpWidget(_page(_prefs(_enabledPluginsHiddenTabs()), groups));
    await tester.pump();

    expect(find.byType(AnimatedSwitcher), findsNothing);
  });
}
