import 'package:dart_twitter_api/twitter_api.dart' show User;
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/client/client.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/group_discovery.dart';
import 'package:xta/group/group_discovery_follow.dart';
import 'package:xta/group/group_discovery_screen.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/saved/liked_tweet_model.dart';
import 'package:xta/saved/saved_tweet_model.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/tweet/tweet.dart';
import 'support/memory_json_store.dart';

TweetWithCard _post() => TweetWithCard()
  ..idStr = '900'
  ..fullText = 'A discovered post'
  ..lang = 'en'
  ..createdAt = DateTime(2026, 10, 6)
  ..favoriteCount = 3
  ..retweetCount = 1
  ..user = (User()
    ..idStr = '123'
    ..name = 'An artist'
    ..screenName = 'artist'
    ..verified = false);

DiscoverySupporter _by(String member) => DiscoverySupporter(
  memberId: member,
  handle: member,
  name: member,
  kind: DiscoverySignal.reposted,
  date: DateTime(2026, 10, 6),
);

final _artist = DiscoveryAccount(
  source: DiscoverySource.x,
  id: '123',
  handle: 'artist',
  name: 'An artist',
  text: 'A discovered post',
  postUrl: 'https://x.com/i/status/900',
  date: DateTime(2026, 10, 6),
  supportingPost: _post(),
  supporters: [_by('m1'), _by('m2'), _by('m3')],
);

class _Discovery extends GroupDiscoveryStore {
  _Discovery() : super(storage: MemoryJsonStore());
  bool failLoad = false;
  @override
  Future<void> load({
    required List<DiscoveryLoad> sources,
    required Set<String> followed,
    required String groupName,
    String? groupId,
  }) {
    if (failLoad) throw StateError('Unavailable');
    return super.load(
      sources: [
        DiscoveryLoad(DiscoverySource.x, members: 12, read: (_) async => DiscoveryBatch([_artist], read: 3)),
      ],
      followed: followed,
      groupName: groupName,
      groupId: groupId,
    );
  }
}

class _Writer implements DiscoveryGroupWriter {
  final followed = <String>[];
  final saved = <String, List<String>>{};

  @override
  Future<String> follow(DiscoveryAccount account) async {
    followed.add(account.key);
    return account.id;
  }

  @override
  Future<List<String>> listGroupsForUser(String id) async => ['other'];

  @override
  Future<void> saveUserGroupMembership(String id, List<String> memberships) async {
    saved[id] = memberships;
  }
}

void main() {
  late PrefServiceCache prefs;
  late GroupsModel groups;
  late SubscriptionsModel subscriptions;
  late LikedTweetModel liked;
  late SavedTweetModel saved;
  late _Discovery discovery;
  late _Writer writer;

  setUp(() {
    prefs = PrefServiceCache(
      defaults: {
        optionMediaDefaultMute: true,
        optionTweetsShowSubscribeBadge: false,
        optionUseAbsoluteTimestamp: true,
        optionShareBaseUrl: 'https://x.com',
        optionDisableAnimations: true,
        optionThemeTrueBlack: false,
        optionThemeTrueBlackTweetCards: false,
        optionLocale: 'en',
        optionZenMode: false,
        optionCalmMode: false,
        optionNonConfirmationBiasMode: false,
        optionGlobalIncludeReplies: true,
        optionGlobalIncludeRetweets: true,
      },
    );
    groups = GroupsModel(prefs);
    subscriptions = SubscriptionsModel(prefs, groups);
    liked = LikedTweetModel();
    saved = SavedTweetModel();
    discovery = _Discovery();
    writer = _Writer();
  });
  tearDown(() async {
    await subscriptions.destroy();
    await groups.destroy();
    await liked.destroy();
    await saved.destroy();
  });

  Widget app({bool scaffold = false}) {
    final pane = GroupDiscoveryPane(
      createStore: () => discovery,
      writer: writer,
      group: SubscriptionGroupGet(
        id: 'art',
        name: 'Art',
        icon: defaultGroupIcon,
        subscriptions: [],
        includeReplies: null,
        includeRetweets: null,
        popular: false,
      ),
    );
    return PrefService(
      service: prefs,
      child: MultiProvider(
        providers: [
          Provider<GroupsModel>.value(value: groups),
          Provider<SubscriptionsModel>.value(value: subscriptions),
          Provider<LikedTweetModel>.value(value: liked),
          Provider<SavedTweetModel>.value(value: saved),
        ],
        child: MaterialApp(
          localizationsDelegates: const [
            L10n.delegate,
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          supportedLocales: L10n.delegate.supportedLocales,
          // Pushed group routes supply a NestedScrollView, with no parent Scaffold.
          home: scaffold
              ? Scaffold(body: pane)
              : NestedScrollView(
                  headerSliverBuilder: (_, _) => [const SliverAppBar(title: Text('Art'))],
                  body: pane,
                ),
        ),
      ),
    );
  }

  testWidgets('inline Discovery renders compact rows with their reason in a pushed group route', (tester) async {
    await tester.pumpWidget(app());
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    expect(find.text('An artist'), findsOneWidget);
    expect(find.text('A discovered post'), findsOneWidget);
    expect(find.text('Reposted by @m1, @m2 and 1 more'), findsOneWidget);
    expect(find.text('3 of 12 members'), findsOneWidget);
    expect(find.byType(TweetTile), findsNothing);
  });

  testWidgets('a setup failure offers Retry and can recover', (tester) async {
    discovery.failLoad = true;
    await tester.pumpWidget(app());
    await tester.pumpAndSettle();
    expect(find.byType(CircularProgressIndicator), findsNothing);
    expect(find.text('Retry'), findsOneWidget);
    expect(tester.takeException(), isNull);
    discovery.failLoad = false;
    await tester.tap(find.text('Retry'));
    await tester.pumpAndSettle();
    expect(find.text('An artist'), findsOneWidget);
    expect(find.text('Retry'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Add to this group follows locally, keeps other memberships and marks the row in place', (tester) async {
    await tester.pumpWidget(app());
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Add to “Art”'));
    await tester.pumpAndSettle();
    expect(writer.followed, ['x:123']);
    expect(writer.saved['123'], ['other', 'art']);
    expect(find.text('Added'), findsOneWidget);
    expect(find.text('An artist'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('tapping the snippet expands the real post and the header collapses it again', (tester) async {
    await tester.pumpWidget(app());
    await tester.pumpAndSettle();
    await tester.tap(find.text('A discovered post'));
    await tester.pumpAndSettle();
    expect(find.byType(TweetTile), findsOneWidget);
    await tester.tap(find.text('An artist').first);
    await tester.pumpAndSettle();
    expect(find.byType(TweetTile), findsNothing);
    expect(find.text('A discovered post'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Hide removes the row and the snackbar undoes it', (tester) async {
    await tester.pumpWidget(app(scaffold: true));
    await tester.pumpAndSettle();
    await tester.tap(find.byIcon(Icons.more_vert).last);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Hide'));
    await tester.pumpAndSettle();
    expect(find.text('An artist'), findsNothing);
    expect(find.text('Suggestions updated'), findsOneWidget);
    await tester.tap(find.text('Undo'));
    await tester.pumpAndSettle();
    expect(find.text('An artist'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
