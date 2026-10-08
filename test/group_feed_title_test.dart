import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/combined_groups.dart';
import 'package:xta/group/group_feed_title.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/group/group_switcher.dart';
import 'package:xta/home/_feed.dart';
import 'package:xta/subscriptions/group_identity.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/x_look_theme.dart';

UserSubscription _member(String id) => UserSubscription(
  id: id,
  screenName: id,
  name: id,
  profileImageUrlHttps: null,
  verified: false,
  createdAt: DateTime.utc(2026),
  inFeed: true,
);

SubscriptionGroupGet _loaded(int members) => SubscriptionGroupGet(
  id: 'anime',
  name: 'Anime',
  icon: defaultGroupIcon,
  subscriptions: List.generate(members, (i) => _member('m$i')),
  includeReplies: true,
  includeRetweets: true,
  popular: false,
);

class _FakeGroupsModel extends GroupsModel {
  _FakeGroupsModel(super.prefs) {
    update([
      SubscriptionGroup(
        id: 'anime',
        name: 'Anime',
        icon: defaultGroupIcon,
        color: null,
        numberOfMembers: 2,
        createdAt: DateTime.utc(2026),
      ),
    ]);
  }
}

Widget _app(
  GroupModel model,
  Widget title, {
  double textScale = 1,
  CombinedGroupsStore? combined,
  PreferredSizeWidget Function(Widget title)? appBar,
}) {
  final prefs = PrefServiceCache(
    cache: {optionSubscriptionGroupsOrderByField: 'name', optionSubscriptionGroupsOrderByAscending: true},
  );
  return PrefService(
    service: prefs,
    child: MultiProvider(
      providers: [
        Provider<GroupModel>.value(value: model),
        Provider<GroupsModel>(create: (_) => _FakeGroupsModel(prefs)),
        Provider<CombinedGroupsStore>(create: (_) => combined ?? CombinedGroupsStore()),
        Provider<FeedTabStore>(create: (_) => FeedTabStore(FeedTab.following)),
      ],
      child: MaterialApp(
        theme: xLookLightTheme(null),
        localizationsDelegates: const [
          L10n.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        supportedLocales: L10n.delegate.supportedLocales,
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(textScale)),
          child: child!,
        ),
        home: Scaffold(appBar: appBar?.call(title) ?? AppBar(title: title)),
      ),
    ),
  );
}

Finder _inAppBar(Finder finder) => find.descendant(of: find.byType(AppBar), matching: finder);

void main() {
  // The real font: its line height, not the test font's, decides whether two lines fit the toolbar.
  setUpAll(() async {
    await (FontLoader('Inter')
          ..addFont(rootBundle.load('assets/fonts/Inter-Regular.ttf'))
          ..addFont(rootBundle.load('assets/fonts/Inter-Bold.ttf')))
        .load();
  });

  testWidgets('the member count and the group mark sit in the app bar', (tester) async {
    final model = GroupModel('anime', reader: () async => _loaded(2));
    addTearDown(model.destroy);
    await model.loadGroup();

    await tester.pumpWidget(_app(model, const GroupFeedTitle(name: 'Anime', groupId: 'anime')));
    await tester.pumpAndSettle();

    expect(_inAppBar(find.text('Anime')), findsOneWidget);
    expect(_inAppBar(find.text('2 subscriptions')), findsOneWidget);
    expect(_inAppBar(find.byType(GroupMark)), findsOneWidget);
  });

  testWidgets('a pushed group keeps the switcher, now with a full-height touch target', (tester) async {
    final model = GroupModel('anime', reader: () async => _loaded(2));
    addTearDown(model.destroy);
    await model.loadGroup();

    await tester.pumpWidget(_app(model, GroupFeedTitle(name: 'Anime', groupId: 'anime', onSwitch: (_) {})));
    await tester.pumpAndSettle();

    final switcher = find.byType(GroupSwitcherTitle);
    expect(switcher, findsOneWidget);
    expect(find.descendant(of: switcher, matching: find.text('2 subscriptions')), findsOneWidget);
    expect(
      tester.getSize(find.descendant(of: switcher, matching: find.byType(InkWell))).height,
      greaterThanOrEqualTo(kTweetTouchTarget),
    );
  });

  for (final switchable in [false, true]) {
    testWidgets('both lines fit the toolbar at the largest text size (switchable: $switchable)', (tester) async {
      final model = GroupModel('anime', reader: () async => _loaded(12));
      addTearDown(model.destroy);
      await model.loadGroup();

      await tester.pumpWidget(
        _app(
          model,
          GroupFeedTitle(
            name: 'A group with a name far too long for any phone toolbar',
            groupId: 'anime',
            onSwitch: switchable ? (_) {} : null,
          ),
          textScale: 3,
        ),
      );
      await tester.pumpAndSettle();

      expect(tester.takeException(), isNull);
      expect(tester.getSize(find.byType(GroupFeedTitle)).height, lessThanOrEqualTo(kToolbarHeight));
    });
  }

  testWidgets('the name does not move when the count arrives', (tester) async {
    final read = Completer<SubscriptionGroupGet>();
    final model = GroupModel('anime', reader: () => read.future);
    addTearDown(model.destroy);
    unawaited(model.loadGroup());

    await tester.pumpWidget(_app(model, GroupFeedTitle(name: 'Anime', groupId: 'anime', onSwitch: (_) {})));
    await tester.pump();
    final before = tester.getTopLeft(find.text('Anime'));
    expect(find.text('2 subscriptions'), findsNothing);

    read.complete(_loaded(2));
    await tester.pumpAndSettle();

    expect(find.text('2 subscriptions'), findsOneWidget);
    expect(tester.getTopLeft(find.text('Anime')), before);
  });

  testWidgets('a pushed group on a 360dp phone keeps its name and drops the mark instead', (tester) async {
    tester.view.physicalSize = const Size(360, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final model = GroupModel('anime', reader: () async => _loaded(12));
    addTearDown(model.destroy);
    await model.loadGroup();

    await tester.pumpWidget(
      _app(
        model,
        GroupFeedTitle(name: 'Anime', groupId: 'anime', onSwitch: (_) {}),
        appBar: (title) => AppBar(
          leading: const BackButton(),
          title: title,
          actions: [
            for (final icon in [Icons.search, Icons.build, Icons.arrow_upward, Icons.refresh]) Icon(icon),
          ].map((icon) => IconButton(onPressed: () {}, icon: icon)).toList(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
    expect(find.byType(GroupMark), findsNothing);
    expect(tester.getSize(find.text('Anime')).width, greaterThan(0));
  });

  testWidgets('a narrow deck column title does not overflow', (tester) async {
    tester.view.physicalSize = const Size(119, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final model = GroupModel('anime', reader: () async => _loaded(12));
    addTearDown(model.destroy);
    await model.loadGroup();

    await tester.pumpWidget(
      _app(
        model,
        const GroupFeedTitle(name: 'Anime', groupId: 'anime'),
        appBar: (title) => AppBar(leading: const BackButton(), title: title),
      ),
    );
    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
  });

  testWidgets('the combined-groups count matches the name it follows', (tester) async {
    final model = GroupModel('anime', reader: () async => _loaded(2));
    final combined = CombinedGroupsStore()..toggle('art');
    addTearDown(model.destroy);
    addTearDown(combined.destroy);
    await model.loadGroup();

    await tester.pumpWidget(
      _app(
        model,
        GroupFeedTitle(name: 'Anime', groupId: 'anime', onSwitch: (_) {}),
        combined: combined,
      ),
    );
    await tester.pumpAndSettle();

    final name = tester.renderObject<RenderParagraph>(find.text('Anime')).text.style;
    final extra = tester.renderObject<RenderParagraph>(find.text('+1')).text.style;
    expect(extra?.fontSize, name?.fontSize);
  });
}
