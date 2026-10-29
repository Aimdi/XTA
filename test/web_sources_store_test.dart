import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/plugins/websources/web_source_catalogue.dart';
import 'package:xta/plugins/websources/web_source_client.dart';
import 'package:xta/plugins/websources/web_source_definition.dart';
import 'package:xta/plugins/websources/web_source_feed_store.dart';
import 'package:xta/plugins/websources/web_source_follows_store.dart';
import 'package:xta/plugins/websources/web_sources_store.dart';

import 'support/web_source_fixtures.dart';

PrefServiceCache _prefs() => PrefServiceCache(
  cache: {
    optionPluginWebSourcesInstalled: '[]',
    optionPluginWebSourcesFollows: '[]',
    optionPluginWebSourcesCheckedAt: '',
  },
);

InstalledWebSource _installed({List<String> agreed = const ['lobste.rs'], WebSourceDefinition? waiting}) =>
    InstalledWebSource(
      source: sourceFrom(linksSourceJson()),
      agreedHosts: agreed,
      origin: Uri.parse('https://example.org/lobsters.json'),
      waiting: waiting,
    );

void main() {
  group('the published list', () {
    test('resolves files against the list and leaves out bad entries', () {
      final listings = parseWebSourceCatalogue(
        jsonEncode({
          'format': 1,
          'sources': [
            {'id': 'lobsters', 'name': 'Lobsters', 'description': 'Links', 'url': 'sources/lobsters.json'},
            {'id': 'Bad Id', 'name': 'x', 'url': 'x.json'},
            {'id': 'plain', 'name': 'Plain', 'url': 'http://example.org/plain.json'},
            {'id': 'nameless', 'url': 'n.json'},
          ],
        }),
        Uri.parse('https://raw.githubusercontent.com/Aimdi/XTA/HEAD/sources.json'),
      );

      expect(listings.map((listing) => listing.id), ['lobsters']);
      expect(listings.single.url, Uri.parse('https://raw.githubusercontent.com/Aimdi/XTA/HEAD/sources/lobsters.json'));
      expect(parseWebSourceCatalogue('oops', Uri.parse('https://x.org/')), isEmpty);
    });
  });

  group('an update', () {
    test('on agreed sites replaces the source', () {
      final next = updatedWebSource(_installed(), ReadWebSource(sourceFrom(linksSourceJson(version: 2))));

      expect(next?.source.version, 2);
      expect(next?.waiting, isNull);
    });

    test('asking for a new site waits for agreement', () {
      final newer = sourceFrom(linksSourceJson(version: 2, hosts: ['lobste.rs', 'cdn.lobste.rs']));
      final next = updatedWebSource(_installed(), ReadWebSource(newer));

      expect(next?.source.version, 1);
      expect(next?.waiting?.version, 2);
    });

    test('that is not newer, is another source, or does not read changes nothing', () {
      expect(updatedWebSource(_installed(), ReadWebSource(sourceFrom(linksSourceJson()))), isNull);
      expect(updatedWebSource(_installed(), ReadWebSource(sourceFrom(postsSourceJson()))), isNull);
      expect(updatedWebSource(_installed(), const RefusedWebSource(WebSourceRefusal.unreadable, '')), isNull);
      final waiting = sourceFrom(linksSourceJson(version: 2, hosts: ['lobste.rs', 'x.org']));
      expect(updatedWebSource(_installed(waiting: waiting), ReadWebSource(waiting)), isNull);
    });
  });

  group('WebSourcesStore', () {
    test('keeps installed sources, removes them and takes a waiting update', () async {
      final prefs = _prefs();
      final store = WebSourcesStore(prefs);

      await store.install(sourceFrom(linksSourceJson()), origin: Uri.parse('https://example.org/l.json'));
      await store.install(sourceFrom(htmlSourceJson()));
      expect(WebSourcesStore(prefs).state.map((entry) => entry.source.id), ['lobsters', 'tildes']);

      await store.remove('tildes');
      expect(readInstalledWebSources(prefs).map((entry) => entry.source.id), ['lobsters']);
      expect(readInstalledWebSources(prefs).single.agreedHosts, ['lobste.rs']);
    });

    test('reads sources again at most once a day, and holds back new sites', () async {
      final prefs = _prefs();
      final store = WebSourcesStore(prefs);
      await store.install(sourceFrom(linksSourceJson()), origin: Uri.parse('https://example.org/l.json'));
      var asked = 0;
      final client = WebSourceClient(
        client: MockClient((_) async {
          asked++;
          return http.Response(jsonEncode(linksSourceJson(version: 2, hosts: ['lobste.rs', 'x.org'])), 200);
        }),
        spacing: Duration.zero,
      );
      final now = DateTime(2026, 10, 29, 9);

      await store.checkForUpdates(client, now: now);
      await store.checkForUpdates(client, now: now.add(const Duration(hours: 2)));

      expect(asked, 1);
      expect(store.installed('lobsters')?.waiting?.version, 2);
      await store.agreeToUpdate('lobsters');
      expect(store.installed('lobsters')?.source.version, 2);
      expect(store.installed('lobsters')?.agreedHosts, ['lobste.rs', 'x.org']);
    });
  });

  group('WebSourceFollowsStore', () {
    setUpAll(() async {
      sqfliteFfiInit();
      databaseFactory = databaseFactoryFfi;
      final directory = await Directory.systemTemp.createTemp('xta_web_source_follows');
      await databaseFactory.setDatabasesPath(directory.path);
      await Repository().migrate();
    });

    test('follows lists as subscriptions and drops them with their source', () async {
      final prefs = _prefs();
      final store = WebSourceFollowsStore(prefs);
      final lobsters = sourceFrom(linksSourceJson());
      final tildes = sourceFrom(htmlSourceJson());

      await store.follow(lobsters, lobsters.feeds[0]);
      await store.follow(lobsters, lobsters.feeds[1]);
      await store.follow(tildes, tildes.feeds[0]);

      final follows = readWebSourceFollows(prefs);
      expect(follows.map((follow) => follow.id), [
        'websources:lobsters/hottest',
        'websources:lobsters/newest',
        'websources:tildes/all',
      ]);
      expect((follows.first.name, follows.first.screenName), ('Lobsters', 'Hottest'));
      expect(store.isFollowing('lobsters', 'newest'), isTrue);

      await store.unfollow('websources:lobsters/newest');
      expect(store.isFollowing('lobsters', 'newest'), isFalse);
      await store.removeSource('lobsters');
      expect(readWebSourceFollows(prefs).map((follow) => follow.id), ['websources:tildes/all']);
    });

    test('names a followed list by its parts', () {
      expect(webSourceFollowParts('lobsters/hottest'), (sourceId: 'lobsters', listId: 'hottest'));
      expect(webSourceFollowParts('nolist'), isNull);
      expect(webSourceFollowParts('/x'), isNull);
    });
  });

  group('WebSourceFeedStore', () {
    test('loads pages until one brings nothing new', () async {
      final source = sourceFrom(linksSourceJson());
      final asked = <Uri>[];
      final client = WebSourceClient(
        client: MockClient((request) async {
          asked.add(request.url);
          // Every page repeats the first, as a site past its last page can.
          return http.Response(linksPage, 200);
        }),
        spacing: Duration.zero,
      );
      final store = WebSourceFeedStore(client, source, source.feeds.first);

      await store.refresh();
      expect(store.state.map((item) => item.id), ['abc', 'def']);
      expect(store.hasMore, isTrue);

      await store.loadMore();
      expect(store.state, hasLength(2));
      expect(store.hasMore, isFalse);
      expect(asked.map((uri) => uri.path), ['/page/1.json', '/page/2.json']);
    });
  });
}
