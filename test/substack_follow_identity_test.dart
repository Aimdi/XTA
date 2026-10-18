import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/substack/substack_archive_screen.dart';
import 'package:xta/plugins/substack/substack_client.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/plugins/substack/substack_store.dart';

const _theNewsletter = SubstackPublication(
  subdomain: 'newsletter',
  baseUrl: 'https://newsletter.substack.com',
  name: 'The Newsletter',
);

/// A post on a custom domain whose first label is another publication's
/// subdomain: its publication page starts from the guess `newsletter`.
const _engineering = SubstackPost(
  id: '9',
  title: 'Shipping',
  slug: 'shipping',
  publicationBaseUrl: 'https://newsletter.pragmaticengineer.com',
  publicationName: 'The Pragmatic Engineer',
);

class _Client extends SubstackClient {
  _Client() : super(httpClient: MockClient((_) async => http.Response('[]', 200)));

  @override
  Future<List<SubstackPost>> fetchPosts(SubstackPublication publication, {int limit = 12, int offset = 0}) async =>
      const [];

  @override
  Future<SubstackPublication> fetchPublication(Uri base, {String? subdomainHint}) async => SubstackPublication(
    subdomain: 'pragmaticengineer',
    baseUrl: base.origin,
    name: 'The Pragmatic Engineer',
    description: 'Big Tech and startups, from the inside.',
    logoUrl: 'https://example.com/logo.png',
  );

  @override
  Future<List<SubstackRecommendation>> fetchSimilarPublications(SubstackPublication publication) async => const [];
}

Widget _app(SubstackPublicationsStore follows, Widget home) {
  final prefs = PrefServiceCache(cache: {});
  return MultiProvider(
    providers: [
      Provider<SubstackClient>(create: (_) => _Client()),
      Provider<SubstackPublicationsStore>.value(value: follows),
      Provider(create: (_) => SubstackReadStore(prefs), dispose: (_, store) => store.destroy()),
      Provider(create: (_) => SubstackLikesStore(prefs), dispose: (_, store) => store.destroy()),
      Provider(create: (_) => SubstackSavedStore(prefs), dispose: (_, store) => store.destroy()),
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
  );
}

/// Lets the page's database writes, which run outside the test clock, land.
Future<void> _settle(WidgetTester tester) async {
  for (var i = 0; i < 5; i++) {
    await tester.runAsync(() => Future<void>.delayed(const Duration(milliseconds: 20)));
    await tester.pump(const Duration(milliseconds: 100));
  }
}

Future<SubstackPublicationsStore> _follows(List<SubstackPublication> publications) async {
  final store = SubstackPublicationsStore(PrefServiceCache(cache: {}));
  await store.load();
  for (final publication in publications) {
    await store.add(publication);
  }
  return store;
}

void main() {
  setUpAll(() async {
    sqfliteFfiInit();
    databaseFactory = databaseFactoryFfiNoIsolate;
    final directory = await Directory.systemTemp.createTemp('xta-substack-follows');
    await databaseFactory.setDatabasesPath(directory.path);
    await Repository().migrate();
  });

  setUp(() async {
    final database = await Repository.writable();
    await database.delete(tableSubstackSubscription);
  });

  testWidgets('BUG-16: a publication page never rewrites another follow that shares its guessed id', (tester) async {
    final follows = (await tester.runAsync(() => _follows([_theNewsletter])))!;

    await tester.pumpWidget(_app(follows, SubstackArchiveScreen(publication: publicationForPost(_engineering))));
    await _settle(tester);
    await tester.runAsync(follows.load);
    await tester.pump();

    expect(follows.state.map((pub) => pub.baseUrl), [_theNewsletter.baseUrl]);
    expect(find.text(L10n.current.plugin_substack_unfollow), findsNothing);
    expect(find.text(L10n.current.plugin_substack_follow), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('a publication page refreshes its own follow, under that follow\'s id', (tester) async {
    const followed = SubstackPublication(
      subdomain: 'pragmaticengineer',
      baseUrl: 'https://newsletter.pragmaticengineer.com',
      name: 'pragmaticengineer',
    );
    final follows = (await tester.runAsync(() => _follows([followed])))!;

    await tester.pumpWidget(_app(follows, SubstackArchiveScreen(publication: publicationForPost(_engineering))));
    await _settle(tester);
    await tester.runAsync(follows.load);
    await tester.pump();

    final follow = follows.state.single;
    expect(follow.id, 'pragmaticengineer');
    expect(follow.logoUrl, 'https://example.com/logo.png');
    expect(find.text(L10n.current.plugin_substack_unfollow), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  test('BUG-06: following two sites whose hosts share a first label keeps both', () async {
    final follows = await _follows([
      const SubstackPublication(subdomain: 'newsletter', baseUrl: 'https://newsletter.alpha.example', name: 'Alpha'),
      const SubstackPublication(subdomain: 'newsletter', baseUrl: 'https://newsletter.beta.example', name: 'Beta'),
    ]);

    expect(follows.state.map((pub) => pub.name), unorderedEquals(['Alpha', 'Beta']));
    expect(follows.state.map((pub) => pub.id).toSet(), hasLength(2));
  });

  test('following the same site again updates its follow in place', () async {
    final follows = await _follows([
      const SubstackPublication(subdomain: 'newsletter', baseUrl: 'https://newsletter.alpha.example', name: 'Alpha'),
      const SubstackPublication(subdomain: 'alpha', baseUrl: 'https://www.newsletter.alpha.example', name: 'Alpha 2'),
    ]);

    expect(follows.state.map((pub) => (pub.id, pub.name)), [('newsletter', 'Alpha 2')]);
  });

  group('substackFollowOf', () {
    SubstackPublication pub(String id, String base) => SubstackPublication(subdomain: id, baseUrl: base, name: id);

    test('the same host is the same follow, whatever the ids say', () {
      final follow = pub('pragmaticengineer', 'https://newsletter.pragmaticengineer.com');
      expect(substackFollowOf([follow], pub('newsletter', 'https://newsletter.pragmaticengineer.com')), follow);
      expect(substackFollowOf([follow], pub('x', 'https://www.newsletter.pragmaticengineer.com')), follow);
    });

    test('among follows on one host, the one with the same id wins', () {
      final legacy = pub('www', 'https://www.garbageday.email');
      final current = pub('garbageday', 'https://garbageday.email');
      expect(substackFollowOf([legacy, current], pub('garbageday', 'https://www.garbageday.email')), current);
    });

    test('equal ids on two hosts match only when neither is a guess', () {
      final platformer = pub('platformer', 'https://platformer.substack.com');
      final theNewsletter = pub('newsletter', 'https://newsletter.substack.com');
      expect(substackFollowOf([platformer], pub('platformer', 'https://www.platformer.news')), platformer);
      expect(substackFollowOf([theNewsletter], pub('newsletter', 'https://newsletter.example.com')), isNull);
      expect(substackFollowOf([pub('newsletter', 'https://newsletter.alpha.example')], theNewsletter), isNull);
      expect(substackFollowOf([pub('www', 'https://www.one.example')], pub('www', 'https://www.two.example')), isNull);
    });

    test('placing keeps the follow\'s id, or moves a taken id to the host', () {
      final theNewsletter = pub('newsletter', 'https://newsletter.substack.com');
      expect(
        placeSubstackFollow([theNewsletter], pub('newsletter', 'https://newsletter.example.com')).id,
        'newsletter.example.com',
      );
      expect(placeSubstackFollow([pub('pe', 'https://pe.example')], pub('other', 'https://www.pe.example')).id, 'pe');
      expect(
        placeSubstackFollow([pub('', 'https://legacy.example')], pub('legacy', 'https://legacy.example')).id,
        'legacy',
      );
      expect(placeSubstackFollow([theNewsletter], pub('fresh', 'https://fresh.substack.com')).id, 'fresh');
    });
  });

  test('a publication found under its own subdomain on another host is the same follow', () async {
    final follows = await _follows([
      const SubstackPublication(
        subdomain: 'platformer',
        baseUrl: 'https://platformer.substack.com',
        name: 'Platformer',
      ),
      const SubstackPublication(subdomain: 'platformer', baseUrl: 'https://www.platformer.news', name: 'Platformer'),
    ]);

    expect(follows.state.map((pub) => pub.baseUrl), ['https://www.platformer.news']);
  });
}
