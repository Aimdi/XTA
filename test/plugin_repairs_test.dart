import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/instagram/instagram_client.dart';
import 'package:xta/plugins/pixiv/pixiv_auth.dart';
import 'package:xta/plugins/plugin_repairs.dart';
import 'package:xta/plugins/plugin_repairs_registry.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/threads/threads_direct_client.dart';
import 'package:xta/plugins/tiktok/tiktok_client.dart';
import 'package:xta/tweet/ticker/ticker_client.dart';

String _file(Map<String, String> repairs) => jsonEncode({'format': 1, 'repairs': repairs});

void main() {
  tearDown(PluginRepairs.clear);

  group('a value fits its kind', () {
    test('ids and numbers', () {
      expect(fitsRepairKind(RepairKind.digits, '6232751443445612'), isTrue);
      expect(fitsRepairKind(RepairKind.digits, '12a'), isFalse);
      expect(fitsRepairKind(RepairKind.digits, ''), isFalse);
    });

    test('tokens', () {
      expect(fitsRepairKind(RepairKind.token, 'BarcelonaProfileThreadsTabQuery'), isTrue);
      expect(fitsRepairKind(RepairKind.token, 'a/b'), isFalse);
      expect(fitsRepairKind(RepairKind.token, 'evil.example'), isFalse);
    });

    test('versions', () {
      expect(fitsRepairKind(RepairKind.version, '5.0.234'), isTrue);
      expect(fitsRepairKind(RepairKind.version, '5'), isTrue);
      expect(fitsRepairKind(RepairKind.version, '5.0.'), isFalse);
      expect(fitsRepairKind(RepairKind.version, 'v5'), isFalse);
    });

    test('header text: one line of printable ASCII, no edge spaces', () {
      expect(fitsRepairKind(RepairKind.header, 'Mozilla/5.0 (Android) XTA'), isTrue);
      expect(fitsRepairKind(RepairKind.header, 'XTA\r\nCookie: stolen=1'), isFalse);
      expect(fitsRepairKind(RepairKind.header, ' XTA'), isFalse);
      expect(fitsRepairKind(RepairKind.header, 'XTA ✓'), isFalse);
      expect(fitsRepairKind(RepairKind.header, 'x' * 401), isFalse);
    });
  });

  group('a published file', () {
    test('puts known values that fit in force, and nothing else', () {
      final taken = PluginRepairs.apply({
        'threads.guest_doc_id': '1111111111111111',
        'pixiv.app_version': 'not a version',
        'threads.host': 'https://evil.example',
      });

      expect(taken, 1);
      expect(PluginRepairs.threadsGuestDocId.value, '1111111111111111');
      expect(PluginRepairs.pixivAppVersion.value, PluginRepairs.pixivAppVersion.shipped);
      expect(PluginRepairs.applied.keys, ['threads.guest_doc_id']);
    });

    test('replaces the repairs before it, so one withdrawn goes back to the shipped value', () {
      PluginRepairs.apply({'tiktok.aid': '2000'});
      PluginRepairs.apply({});

      expect(PluginRepairs.tiktokAid.value, '1988');
    });

    test('that does not read changes nothing', () {
      expect(parsePluginRepairs('oops'), isEmpty);
      expect(parsePluginRepairs('{"repairs": []}'), isEmpty);
      expect(parsePluginRepairs('{"repairs": {"tiktok.aid": 2000, "pixiv.app_version": "6.0.0"}}'), {
        'pixiv.app_version': '6.0.0',
      });
    });
  });

  group('PluginRepairsRegistry', () {
    test('applies the copy last read before reading again', () {
      final prefs = PrefServiceCache(
        cache: {
          optionPluginRepairsKept: _file({'tiktok.aid': '2000'}),
        },
      );

      expect(PluginRepairsRegistry(prefs).applyKept(), 1);
      expect(PluginRepairs.tiktokAid.value, '2000');
    });

    test('reads the file at most once a day, and keeps what it read', () async {
      final prefs = PrefServiceCache(cache: {});
      var asked = 0;
      final registry = PluginRepairsRegistry(
        prefs,
        client: MockClient((request) async {
          asked++;
          expect(request.url.toString(), defaultPluginRepairsUrl);
          return http.Response(_file({'pixiv.app_version': '6.1.0'}), 200);
        }),
      );
      final now = DateTime(2026, 10, 30, 8);

      expect(await registry.refresh(now: now), 1);
      expect(await registry.refresh(now: now.add(const Duration(hours: 3))), isNull);
      expect(asked, 1);
      expect(PluginRepairs.pixivAppVersion.value, '6.1.0');
      expect(parsePluginRepairs(prefs.get<String>(optionPluginRepairsKept)!), {'pixiv.app_version': '6.1.0'});
    });

    test('keeps what is in force when the file cannot be read', () async {
      PluginRepairs.apply({'tiktok.aid': '2000'});
      final registry = PluginRepairsRegistry(
        PrefServiceCache(cache: {}),
        client: MockClient((_) async => http.Response('', 503)),
      );

      expect(await registry.refresh(), isNull);
      expect(PluginRepairs.tiktokAid.value, '2000');
    });
  });

  test('repairs.json names only known values that fit, each the one this build ships', () {
    final published = parsePluginRepairs(File('repairs.json').readAsStringSync());
    final slots = {for (final slot in PluginRepairs.all) slot.key: slot};

    expect(published.keys.toSet(), slots.keys.toSet());
    for (final MapEntry(key: key, value: value) in published.entries) {
      expect(fitsRepairKind(slots[key]!.kind, value), isTrue, reason: key);
      expect(value, slots[key]!.shipped, reason: '$key: change repairs.json and plugin_repairs.dart together');
    }
  });

  test('every shipped value fits its own kind', () {
    for (final slot in PluginRepairs.all) {
      expect(fitsRepairKind(slot.kind, slot.shipped), isTrue, reason: slot.key);
    }
  });

  group('each network sends the value in force', () {
    test('Instagram: app id and user agent', () async {
      PluginRepairs.apply({'instagram.app_id': '111111111111111', 'instagram.user_agent': 'Repaired/1.0'});
      final sent = <http.Request>[];
      final client = InstagramClient(
        PrefServiceCache(cache: {}),
        httpClient: MockClient((request) async {
          sent.add(request);
          return http.Response('{}', 200);
        }),
      );

      // The answer is not a profile; only the request matters here.
      await client.profile('instagram').then((_) {}, onError: (_) {});

      final api = sent.where((request) => request.url.host == 'i.instagram.com').first;
      expect(api.headers['X-IG-App-ID'], '111111111111111');
      expect(api.headers['User-Agent'], 'Repaired/1.0');
    });

    test('TikTok: aid', () async {
      PluginRepairs.apply({'tiktok.aid': '2000'});
      final sent = <Uri>[];
      final client = TikTokClient(
        PrefServiceCache(cache: {}),
        httpClient: MockClient((request) async {
          sent.add(request.url);
          return http.Response('{"sug_list": []}', 200);
        }),
      );

      await client.suggestQueries('flutter').then((_) {}, onError: (_) {});

      expect(sent.first.queryParameters['aid'], '2000');
    });

    test('Threads, Pixiv, Reddit and Stocks read their slots', () {
      PluginRepairs.apply({
        'threads.guest_doc_id': '2222222222222222',
        'pixiv.client_id': 'RepairedClientId',
        'reddit.web_user_agent': 'Repaired/2.0',
        'stocks.user_agent': 'Repaired/3.0',
      });

      expect(threadsGuestProfileThreadsDocId, '2222222222222222');
      expect(PixivAuth.clientId, 'RepairedClientId');
      expect(RedditClient.publicUserAgent, 'Repaired/2.0');
      expect(TickerClient.userAgent, 'Repaired/3.0');
    });
  });
}
