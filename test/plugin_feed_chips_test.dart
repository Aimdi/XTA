import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/plugins/reddit/reddit_plugin.dart';
import 'package:xta/subscriptions/_groups.dart';
import 'package:xta/subscriptions/plugin_feed_chips.dart';

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

Map<String, Object> _hiddenTabPlugins() => {
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

Widget _chipApp(Widget child) {
  return MaterialApp(
    localizationsDelegates: const [
      L10n.delegate,
      GlobalMaterialLocalizations.delegate,
      GlobalWidgetsLocalizations.delegate,
      GlobalCupertinoLocalizations.delegate,
    ],
    supportedLocales: L10n.delegate.supportedLocales,
    home: Scaffold(body: child),
  );
}

void main() {
  final groups = [_group('a', 'Ai'), _group('b', 'Ai Art')];

  test(
    'pluginFeedsOnGroupsTab lists only enabled plugins that hid their tab',
    () {
      final listed = pluginFeedsOnGroupsTab(_prefs(_hiddenTabPlugins()));
      expect(listed.map((p) => p.id).toList(), [
        pluginIdThreads,
        pluginIdBluesky,
        pluginIdMastodon,
        pluginIdTiktok,
        pluginIdReddit,
        pluginIdSubstack,
        pluginIdPixiv,
        pluginIdBooru,
      ]);

      final withTab = pluginFeedsOnGroupsTab(
        _prefs({..._hiddenTabPlugins(), optionPluginRedditShowTab: true}),
      );
      expect(withTab.map((p) => p.id), isNot(contains(pluginIdReddit)));

      expect(pluginFeedsOnGroupsTab(_prefs()).map((p) => p.id), isEmpty);
    },
  );

  testWidgets('eight hidden-tab plugins render as chips, not rows', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(390 * 3, 800 * 3);
    tester.view.devicePixelRatio = 3;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    await tester.pumpWidget(_page(_prefs(_hiddenTabPlugins()), groups));
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
      expect(find.text(name), findsOneWidget);
    }
    expect(find.byIcon(Icons.chevron_right), findsNothing);
    expect(find.byType(ListTile), findsNothing);
    expect(find.byType(Wrap), findsOneWidget);
    expect(find.byKey(pluginFeedChipKey(pluginIdReddit)), findsOneWidget);
    expect(tester.getSize(find.byType(Wrap)).height, lessThan(180));
  });

  testWidgets('a plugin that still has a home tab is not on the Groups board', (
    tester,
  ) async {
    await tester.pumpWidget(
      _page(
        _prefs({
          optionPluginRedditEnabled: true,
          optionPluginRedditShowTab: true,
        }),
        groups,
      ),
    );
    await tester.pump();

    expect(find.text('Reddit'), findsNothing);
    expect(find.byType(Wrap), findsNothing);
  });

  testWidgets('no chips and no divider when every plugin is off', (
    tester,
  ) async {
    await tester.pumpWidget(_page(_prefs(), groups));
    await tester.pump();

    expect(find.byType(Wrap), findsNothing);
    expect(find.byType(Divider), findsNothing);
    expect(find.text('Ai'), findsOneWidget);
  });

  testWidgets('tapping a chip calls onTap', (tester) async {
    var taps = 0;
    await tester.pumpWidget(
      _chipApp(PluginFeedChip(plugin: RedditPlugin(), onTap: () => taps++)),
    );
    await tester.pump();

    await tester.tap(find.text('Reddit'));
    expect(taps, 1);
  });

  testWidgets('chip tooltip and semantics use the plugin title', (
    tester,
  ) async {
    await tester.pumpWidget(
      _chipApp(PluginFeedChip(plugin: RedditPlugin(), onTap: () {})),
    );
    await tester.pump();

    expect(find.byTooltip('Reddit'), findsOneWidget);
    final semantics = tester.getSemantics(find.text('Reddit'));
    expect(semantics.label, contains('Reddit'));
    expect(semantics.hasFlag(SemanticsFlag.isButton), isTrue);
  });

  testWidgets('chips sit in the scroll header above the group grid', (
    tester,
  ) async {
    await tester.pumpWidget(_page(_prefs(_hiddenTabPlugins()), groups));
    await tester.pump();

    expect(
      tester.getTopLeft(find.byType(Wrap)).dy,
      lessThan(tester.getTopLeft(find.text('Ai')).dy),
    );
  });
}
