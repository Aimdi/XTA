import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/bluesky/bluesky_thread_screen.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_thread_screen.dart';
import 'package:xta/plugins/plugin_activity.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/tweet/quotes_screen.dart';
import 'package:xta/ui/conversation_sort.dart';
import 'package:xta/user.dart';

import 'support/bluesky_reading_harness.dart';
import 'support/mastodon_harness.dart';

BlueskyPost _blue(String id, String text, String at, int likes) =>
    BlueskyPost.fromSnapshot({
      ...bluePost(id, parent: 'root').toJson(),
      'text': text,
      'publishedAt': at,
      'likeCount': likes,
    });

final _blueThread = BlueskyThread(
  post: bluePost('root'),
  replies: [
    _blue('r1', 'early and popular', '2026-08-01T11:00:00Z', 40),
    _blue('r2', 'late and quiet', '2026-08-01T13:00:00Z', 1),
    _blue('r3', 'middle and liked', '2026-08-01T12:00:00Z', 7),
  ],
);

MastodonPost _toot(String id, int hour, int likes) => MastodonPost(
  id: id,
  replyToId: id == 'root' ? null : 'root',
  acct: 'writer@studio.example',
  authorName: 'writer',
  text: 'Toot $id',
  url: 'https://studio.example/@writer/$id',
  publishedAt: DateTime.utc(2026, 8, 1, hour),
  favouritesCount: likes,
);

class _TootClient extends MastodonFixtureClient {
  @override
  Future<MastodonThread> fetchThreadAnywhere(
    List<String> instances,
    MastodonPost seed,
  ) async => MastodonThread(
    status: _toot('root', 9, 0),
    descendants: [_toot('t1', 10, 2), _toot('t2', 11, 9), _toot('t3', 12, 4)],
  );
}

void _tallView(WidgetTester tester) {
  tester.view.physicalSize = const Size(600, 3000);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
}

/// Avatars keep loading in tests, so [WidgetTester.pumpAndSettle] may not end.
Future<void> _settle(WidgetTester tester) async {
  for (var i = 0; i < 10; i++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

List<String> _order(WidgetTester tester, List<String> texts) {
  final positions = {
    for (final text in texts) text: tester.getTopLeft(find.text(text)).dy,
  };
  return [...texts]..sort((a, b) => positions[a]!.compareTo(positions[b]!));
}

UserWithExtra _reposter(String id, int? followers) => UserWithExtra()
  ..idStr = id
  ..name = 'Person $id'
  ..screenName = 'person$id'
  ..verified = false
  ..createdAt = DateTime.utc(2020)
  ..followersCount = followers;

class _NoGroups extends GroupsModel {
  _NoGroups(super.prefs) {
    update([]);
  }
}

Widget _localized(ConversationSortStore sorts, Widget home) {
  final prefs = PrefServiceCache();
  final groups = _NoGroups(prefs);
  return PrefService(
    service: prefs,
    child: MultiProvider(
      providers: [
        Provider<ConversationSortStore>.value(value: sorts),
        Provider<GroupsModel>.value(value: groups),
        Provider<SubscriptionsModel>(
          create: (_) => SubscriptionsModel(prefs, groups),
        ),
      ],
      child: MaterialApp(
        localizationsDelegates: const [
          L10n.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        supportedLocales: L10n.delegate.supportedLocales,
        home: home,
      ),
    ),
  );
}

Future<void> _choose(WidgetTester tester, Finder control, String next) async {
  await tester.tap(control);
  await _settle(tester);
  await tester.tap(find.text(next).last);
  await _settle(tester);
}

typedef _Quote = ({String text, int at, int likes});

const _blueTexts = ['early and popular', 'late and quiet', 'middle and liked'];

void main() {
  testWidgets('the reply sort reorders a Bluesky thread for the session', (
    tester,
  ) async {
    _tallView(tester);
    final h = BlueReadingHarness();
    addTearDown(() => h.close(tester));
    h.client.nextThread = Completer<BlueskyThread>()..complete(_blueThread);
    final control = find.byKey(const ValueKey('bluesky-thread-sort'));

    await tester.pumpWidget(h.app(BlueskyThreadScreen(post: bluePost('root'))));
    await _settle(tester);
    expect(find.text('Relevant'), findsOneWidget);
    expect(_order(tester, _blueTexts), _blueTexts);
    expect(tester.getSize(control).height, greaterThanOrEqualTo(48));

    await _choose(tester, control, 'Recent');
    expect(h.sorts.state.replies, ReplySort.recent);
    expect(_order(tester, _blueTexts), [
      'late and quiet',
      'middle and liked',
      'early and popular',
    ]);

    await _choose(tester, control, 'Most liked');
    expect(_order(tester, _blueTexts), [
      'early and popular',
      'middle and liked',
      'late and quiet',
    ]);

    // A thread opened later in the session starts from the same choice.
    await tester.pumpWidget(const SizedBox());
    await tester.pumpWidget(h.app(BlueskyThreadScreen(post: bluePost('root'))));
    await _settle(tester);
    expect(find.text('Most liked'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Mastodon falls back to its own order and sorts on device', (
    tester,
  ) async {
    _tallView(tester);
    final h = MastodonHarness(client: _TootClient());
    addTearDown(() => h.close(tester));
    const texts = ['Toot t1', 'Toot t2', 'Toot t3'];

    await tester.pumpWidget(
      h.app(child: MastodonThreadScreen(post: _toot('root', 9, 0))),
    );
    await _settle(tester);
    // Relevance is the session default but not a Mastodon order.
    expect(find.text('Oldest'), findsOneWidget);
    expect(_order(tester, texts), texts);

    await _choose(
      tester,
      find.byKey(const ValueKey('mastodon-thread-sort')),
      'Most liked',
    );
    expect(h.sorts.state.replies, ReplySort.mostLiked);
    expect(_order(tester, texts), ['Toot t2', 'Toot t3', 'Toot t1']);
    expect(tester.takeException(), isNull);
  });

  testWidgets('a plugin quote list offers device orders above its items', (
    tester,
  ) async {
    _tallView(tester);
    final sorts = ConversationSortStore();
    addTearDown(sorts.destroy);
    const quotes = [
      (text: 'first quote', at: 1, likes: 3),
      (text: 'second quote', at: 2, likes: 8),
      (text: 'third quote', at: 3, likes: 1),
    ];
    const texts = ['first quote', 'second quote', 'third quote'];

    await tester.pumpWidget(
      _localized(
        sorts,
        Builder(
          builder: (context) => TextButton(
            onPressed: () => openPluginActivity<_Quote>(
              context,
              title: 'Quotes',
              postUrl: 'https://example.social/1',
              loader: (_) async => const PluginActivityPage(quotes),
              idOf: (quote) => quote.text,
              errorLabel: (_, _) => 'error',
              itemBuilder: (context, quote) =>
                  ListTile(title: Text(quote.text)),
              sort: pluginQuoteSort(
                postedAt: (quote) => DateTime.utc(2026, 1, quote.at),
                likes: (quote) => quote.likes,
              ),
            ),
            child: const Text('open'),
          ),
        ),
      ),
    );
    await tester.tap(find.text('open'));
    await _settle(tester);

    expect(find.text('Recent'), findsOneWidget);
    expect(_order(tester, texts), [
      'third quote',
      'second quote',
      'first quote',
    ]);
    expect(
      tester.getTopLeft(find.text('Recent')).dy,
      lessThan(tester.getTopLeft(find.text('third quote')).dy),
    );

    await _choose(tester, find.text('Recent'), 'Most liked');
    expect(sorts.state.quotes, QuoteSort.mostLiked);
    expect(_order(tester, texts), [
      'second quote',
      'first quote',
      'third quote',
    ]);
    expect(tester.takeException(), isNull);
  });

  testWidgets('X reposters offer follower order only with follower counts', (
    tester,
  ) async {
    _tallView(tester);
    final sorts = ConversationSortStore();
    addTearDown(sorts.destroy);
    const names = ['Person 1', 'Person 2', 'Person 3'];

    await tester.pumpWidget(
      _localized(
        sorts,
        Scaffold(
          body: RetweetersList(
            tweetId: '1',
            loadPage: (_) async => (
              items: [
                _reposter('1', 10),
                _reposter('2', 900),
                _reposter('3', null),
              ],
              nextCursor: null,
            ),
          ),
        ),
      ),
    );
    await _settle(tester);
    expect(find.text('Recent'), findsOneWidget);
    expect(_order(tester, names), names);

    await _choose(tester, find.text('Recent'), 'Most followers');
    expect(sorts.state.reposters, ReposterSort.mostFollowers);
    expect(_order(tester, names), ['Person 2', 'Person 1', 'Person 3']);

    await tester.pumpWidget(
      _localized(
        sorts,
        Scaffold(
          body: RetweetersList(
            key: const ValueKey('no-counts'),
            tweetId: '2',
            loadPage: (_) async => (
              items: [_reposter('4', null), _reposter('5', null)],
              nextCursor: null,
            ),
          ),
        ),
      ),
    );
    await _settle(tester);
    expect(find.text('Most followers'), findsNothing);
    expect(find.text('Recent'), findsNothing);
    expect(tester.takeException(), isNull);
  });
}
