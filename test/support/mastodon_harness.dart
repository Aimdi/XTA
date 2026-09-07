import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/mastodon/mastodon_client.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_plugin.dart';
import 'package:xta/plugins/mastodon/mastodon_store.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/plugins/plugin_session.dart';
import 'package:xta/ui/x_look_theme.dart';

const sampleProfile = MastodonProfile(
  id: 'maya',
  acct: 'maya@studio.example',
  username: 'maya',
  displayName: 'Maya Chen',
  note: 'Designer, cyclist, and collector of small observations.',
  url: 'https://studio.example/@maya',
  followersCount: 1284,
  followingCount: 218,
  statusesCount: 906,
);

final samplePosts = List.generate(
  36,
  (index) => MastodonPost(
    id: 'post-$index',
    acct: index.isEven ? 'maya@studio.example' : 'alex@social.example',
    authorName: index.isEven ? 'Maya Chen' : 'Alex Morgan',
    text: switch (index % 4) {
      0 =>
        'Took the long way home. A quiet street, an open window, and someone practising the piano. More of this, please.',
      1 =>
        'A small collection of independent places on the web. The kind you find through a friend, and keep coming back to.',
      2 => 'What are you making this weekend? I am finally turning those sketches into something I can hold.',
      _ => 'Field notes from an ordinary day. Leave a little room for a detour.',
    },
    url: 'https://studio.example/@maya/$index',
    publishedAt: DateTime(2026, 9, 7, 9, index),
    repliesCount: 8 + index,
    reblogsCount: 12,
    favouritesCount: 42,
    linkCard: index == 1
        ? const MastodonLinkCard(
            url: 'https://example.com/garden',
            title: 'A smaller, more personal web',
            description: 'Notes from the independent web, and a few places to begin.',
          )
        : null,
  ),
);

const sampleTags = [
  MastodonTrendingTag(name: 'photography', uses: 284),
  MastodonTrendingTag(name: 'opensource', uses: 173),
  MastodonTrendingTag(name: 'art', uses: 92),
  MastodonTrendingTag(name: 'cycling'),
  MastodonTrendingTag(name: 'books'),
  MastodonTrendingTag(name: 'design'),
];

class MastodonFixtureClient extends MastodonClient {
  int publicReads = 0;
  int followingReads = 0;
  int searches = 0;
  @override
  Future<List<MastodonTrendingTag>> getTrendingTagsAnywhere(List<String> instances, {int limit = 20}) async =>
      sampleTags;
  @override
  Future<List<MastodonPost>> getTrendingStatusesAnywhere(List<String> instances, {int limit = 20}) async => samplePosts;
  @override
  Future<List<MastodonPost>> getPublicTimeline(
    String instance, {
    bool local = false,
    int limit = 30,
    String? maxId,
  }) async {
    publicReads++;
    return maxId == null ? samplePosts : [];
  }

  @override
  Future<List<MastodonPost>> fetchAccountAnywhere(List<String> instances, String acct, {int limit = 20}) async {
    followingReads++;
    return samplePosts;
  }

  @override
  Future<MastodonSearchPage> searchAnywhere(List<String> instances, String q, {int limit = 20}) async {
    searches++;
    return MastodonSearchPage(accounts: [sampleProfile], posts: samplePosts, tags: sampleTags);
  }

  @override
  Future<MastodonProfile> lookupAnywhere(List<String> instances, String acct) async => acct == sampleProfile.acct
      ? sampleProfile
      : MastodonProfile(
          id: acct,
          acct: acct,
          username: 'new',
          displayName: 'New Reader',
          note: '',
          url: 'https://studio.example/@new',
        );
  @override
  Future<({MastodonProfile profile, List<MastodonPost> posts, Set<String> pinnedIds, String instance})> profileAnywhere(
    List<String> instances,
    String acct,
  ) async => (
    profile: sampleProfile,
    posts: samplePosts.take(3).toList(),
    pinnedIds: <String>{},
    instance: 'https://studio.example',
  );
  @override
  Future<List<MastodonPost>> getStatuses(
    String instance,
    String id, {
    int limit = 20,
    bool excludeReplies = true,
    bool onlyMedia = false,
    bool pinned = false,
    String? maxId,
  }) async => [];
  @override
  Future<MastodonThread> fetchThreadAnywhere(List<String> instances, MastodonPost seed) async =>
      MastodonThread(status: seed, descendants: [samplePosts[2]]);
}

class MastodonFixtureAccounts extends MastodonAccountsStore {
  @override
  Future<void> add(MastodonAccount account) async =>
      update([...state.where((entry) => entry.acct != account.acct), account]);
  @override
  Future<void> remove(String acct) async => update(state.where((entry) => entry.acct != acct).toList());
}

class MastodonHarness {
  final MastodonFixtureClient client;
  final prefs = PrefServiceCache(
    defaults: {
      optionPluginMastodonInstance: 'https://studio.example',
      optionPluginMastodonInstances: '[]',
      optionZenMode: false,
      optionCalmMode: false,
      optionDisableAnimations: true,
      optionThemeTrueBlack: false,
      optionThemeTrueBlackTweetCards: false,
    },
  );
  final accounts = MastodonFixtureAccounts();
  final scroll = ScrollController();
  final session = PluginSessionStore();
  late final following = MastodonFeedStore(client, prefs, accounts);
  late final explore = MastodonExploreStore(client, prefs);
  late final local = MastodonLocalStore(client, prefs);
  late final federated = MastodonFederatedStore(client, prefs);

  MastodonHarness({bool populated = true, MastodonFixtureClient? client}) : client = client ?? MastodonFixtureClient() {
    if (populated) {
      accounts.update([
        sampleProfile.toAccount(),
        const MastodonAccount(acct: 'alex@social.example', name: 'Alex Morgan'),
      ]);
      explore.update(MastodonExplorePage(tags: sampleTags, posts: samplePosts));
    }
  }

  Widget app({bool embedded = false, Widget? child, bool dark = false, double scale = 1, bool rtl = false, bool reducedMotion = true}) =>
      PrefService(
        service: prefs,
        child: MultiProvider(
          providers: [
            Provider<MastodonClient>.value(value: client),
            Provider<MastodonAccountsStore>.value(value: accounts),
            Provider<MastodonFeedStore>.value(value: following),
            Provider<MastodonExploreStore>.value(value: explore),
            Provider<MastodonLocalStore>.value(value: local),
            Provider<MastodonFederatedStore>.value(value: federated),
            Provider<PluginSessionStore>.value(value: session),
          ],
          child: MaterialApp(
            debugShowCheckedModeBanner: false,
            theme: dark ? xLookLightsOutTheme(null) : xLookLightTheme(null),
            localizationsDelegates: const [
              L10n.delegate,
              GlobalMaterialLocalizations.delegate,
              GlobalWidgetsLocalizations.delegate,
              GlobalCupertinoLocalizations.delegate,
            ],
            supportedLocales: L10n.delegate.supportedLocales,
            builder: (context, child) => RepaintBoundary(
              key: const ValueKey('mastodon-window'),
              child: MediaQuery(
                data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(scale), disableAnimations: reducedMotion),
                child: Directionality(textDirection: rtl ? TextDirection.rtl : TextDirection.ltr, child: child!),
              ),
            ),
            home: RepaintBoundary(
              key: const ValueKey('mastodon-render'),
              child:
                  child ??
                  (embedded
                      ? PluginEmbedded(
                          child: PrimaryScrollController(
                            controller: scroll,
                            child: MastodonPlugin().homeScreen(scrollController: scroll),
                          ),
                        )
                      : MastodonPlugin().clientScreen(scrollController: scroll)),
            ),
          ),
        ),
      );

  Future<void> close(WidgetTester tester) async {
    await tester.pumpWidget(const SizedBox());
    await tester.pump();
    scroll.dispose();
    await session.destroy();
    await accounts.destroy();
    await following.destroy();
    await explore.destroy();
    await local.destroy();
    await federated.destroy();
    client.httpClient.close();
  }
}
