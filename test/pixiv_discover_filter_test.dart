import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/group/group_discovery.dart';
import 'package:xta/group/group_discovery_sources.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';

Map<String, Object?> _work(int id, {int restrict = 0, int ai = 1}) => {
  'id': id,
  'title': 'Work $id',
  'type': 'illust',
  'image_urls': {'medium': 'https://i.pximg.net/$id.jpg'},
  'user': {'id': 50},
  'x_restrict': restrict,
  'illust_ai_type': ai,
};

/// Group Discover suggests a creator through one of their preview works; the
/// work must be one the reader's Show R-18 and Hide AI choices let through.
void main() {
  var requests = 0;

  Future<List<DiscoveryAccount>> discover({
    required bool showR18,
    required bool hideAi,
    required int seed,
    int userId = 7,
  }) async {
    final prefs = PrefServiceCache(
      cache: {
        optionPluginPixivUserId: userId,
        optionPluginPixivRefreshToken: 'refresh-me',
        optionPluginPixivAccessToken: 'access-1',
        optionPluginPixivAccessExpiresAt: DateTime.now().add(const Duration(hours: 1)).toIso8601String(),
        optionPluginPixivShowR18: showR18,
        optionPluginPixivHideAi: hideAi,
      },
    );
    final client = PixivClient(
      prefs,
      httpClient: MockClient((request) async {
        expect(request.url.path, '/v1/user/related');
        requests++;
        return http.Response(
          jsonEncode({
            'user_previews': [
              {
                'user': {'id': 50, 'name': 'Painter', 'account': 'painter'},
                'illusts': [_work(1, restrict: 1), _work(2, ai: 2), _work(3)],
              },
            ],
          }),
          200,
          headers: {'content-type': 'application/json'},
        );
      }),
    );
    final member = UserSubscription(
      id: 'pixiv:$seed',
      screenName: 'member',
      name: 'Member',
      profileImageUrlHttps: null,
      verified: false,
      createdAt: DateTime(2026),
      inFeed: true,
    );
    final read = pixivDiscoveryReader(client, [member], PixivMuteState.empty);
    final batch = await read(DiscoveryScan(more: false, onPartial: (_) {}));
    return batch.accounts;
  }

  int? shownWork(List<DiscoveryAccount> accounts) => (accounts.single.supportingPost as PixivIllust?)?.id;

  test('hidden R-18 and AI previews never become the suggestion\'s work', () async {
    expect(shownWork(await discover(showR18: false, hideAi: true, seed: 101)), 3);
  });

  test('the reader\'s own choices let them back in', () async {
    expect(shownWork(await discover(showR18: true, hideAi: false, seed: 102)), 1);
    expect(shownWork(await discover(showR18: false, hideAi: false, seed: 103)), 2);
  });

  test('a choice changed since the last scan applies to the cached creators at once', () async {
    expect(shownWork(await discover(showR18: true, hideAi: false, seed: 104)), 1);
    final fetched = requests;
    expect(shownWork(await discover(showR18: false, hideAi: true, seed: 104)), 3);
    expect(requests, fetched, reason: 'the second scan reads the cache');
  });

  test('another account asks again rather than reading the last one\'s answers', () async {
    await discover(showR18: true, hideAi: false, seed: 105, userId: 7);
    final fetched = requests;
    await discover(showR18: true, hideAi: false, seed: 105, userId: 7);
    expect(requests, fetched);
    await discover(showR18: true, hideAi: false, seed: 105, userId: 8);
    expect(requests, fetched + 1);
  });
}
