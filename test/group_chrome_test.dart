import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:quax/constants.dart';
import 'package:quax/database/entities.dart';
import 'package:quax/generated/l10n.dart';
import 'package:quax/group/group_chrome.dart';
import 'package:quax/group/group_view_store.dart';
import 'package:quax/tweet/tweet_chrome.dart';
import 'package:quax/ui/x_look_theme.dart';

SubscriptionGroupGet _group({
  bool popular = false,
  bool custom = false,
  String contentFilter = contentFilterDefault,
  int minLikes = 0,
  int minRetweets = 0,
  List<String> mutedKeywords = const [],
}) {
  return SubscriptionGroupGet(
    id: 'group',
    name: 'Reading',
    icon: '',
    subscriptions: const [],
    includeReplies: true,
    includeRetweets: true,
    popular: popular,
    custom: custom,
    contentFilter: contentFilter,
    minLikes: minLikes,
    minRetweets: minRetweets,
    mutedKeywords: mutedKeywords,
  );
}

Widget _app(Widget child) => MaterialApp(
  theme: xLookLightTheme(null),
  localizationsDelegates: const [
    L10n.delegate,
    GlobalMaterialLocalizations.delegate,
    GlobalWidgetsLocalizations.delegate,
    GlobalCupertinoLocalizations.delegate,
  ],
  supportedLocales: L10n.delegate.supportedLocales,
  home: Scaffold(body: child),
);

void main() {
  test('Group media mode and route selection are Store-backed', () {
    final media = GroupMediaModeStore(false);
    final route = GroupRouteStore((id: 'a', name: 'Art'));
    addTearDown(media.destroy);
    addTearDown(route.destroy);

    media.toggle();
    route.switchTo(
      SubscriptionGroup(
        id: 'b',
        name: 'Books',
        icon: '',
        color: null,
        numberOfMembers: 2,
        createdAt: DateTime.utc(2026),
      ),
    );

    expect(media.state, isTrue);
    expect(route.state, (id: 'b', name: 'Books'));
  });

  test('active Group filter count includes every effective custom rule', () {
    expect(
      groupActiveFilterCount(
        _group(
          custom: true,
          contentFilter: contentFilterSfw,
          minLikes: 10,
          minRetweets: 5,
          mutedKeywords: const ['spoiler', 'sale'],
        ),
      ),
      5,
    );
    expect(groupActiveFilterCount(_group(contentFilter: contentFilterSfw)), 0);
  });

  testWidgets(
    'Group controls show order and media state in one token-sized row',
    (tester) async {
      await tester.pumpWidget(
        _app(
          GroupFeedControlBar(
            group: _group(popular: true),
            mediaOnly: true,
            onOrderSelected: (_) {},
            onMediaToggle: () {},
            onCustomSettings: () {},
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(
        tester.getSize(find.byType(GroupFeedControlBar)).height,
        kGroupControlBarHeight,
      );
      expect(find.text('Recent'), findsOneWidget);
      expect(find.text('Popular'), findsOneWidget);
      expect(find.text('Media'), findsOneWidget);
      for (final chip in tester.widgetList<FilterChip>(
        find.byType(FilterChip),
      )) {
        expect(
          tester.getSize(find.byWidget(chip)).height,
          greaterThanOrEqualTo(kTweetTouchTarget),
        );
      }
    },
  );
}
