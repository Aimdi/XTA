import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:logging/logging.dart';
import 'package:xta/client/client_unauthenticated.dart';
import 'package:xta/client/headers.dart';
import 'package:xta/constants.dart';

// Temporary probe: are QuaX's community query ids known to X, and can a guest
// read them? Not part of the suite.
void main() {
  const enabled = bool.fromEnvironment('RUN_LIVE', defaultValue: false);
  const communityId = '1472249315724771329';
  const ids = {
    'CommunityQuery': '-ElI1vg3dYbttVMhBhGdLw',
    'CommunityTweetsTimeline': 'wXey4HQTUdYUlWWXltWMpg',
    'membersSliceTimeline_Query': 'woAp_YdzAdqnWDrqLTNpAw',
    'CommunitiesSearchQuery': 'HKgnJbw10TZW41Na4ToaSw',
  };
  const realVariables = {
    'CommunityQuery': {'communityId': communityId},
    'CommunityTweetsTimeline': {
      'communityId': communityId,
      'count': 20,
      'displayLocation': 'Community',
      'rankingMode': 'Relevance',
      'withCommunity': true,
    },
    'membersSliceTimeline_Query': {'communityId': communityId},
    'CommunitiesSearchQuery': {'query': 'flutter'},
  };

  test('community ids', () async {
    final log = Logger('probe');
    final client = http.Client();
    final guestToken = await getToken(log);
    Future<http.Response> get(Uri uri) async {
      final headers = await TwitterHeaders.getHeaders(uri, null);
      return client.get(uri, headers: {...headers, 'authorization': guestBearerToken, 'x-guest-token': guestToken});
    }

    final report = <String>[];
    final probeParams = {
      'variables': jsonEncode({'userId': '783214', 'count': 1}),
      'features': jsonEncode(const <String, bool>{}),
    };
    for (final (label, id) in [
      ('made-up', 'AAAAAAAAAAAAAAAAAAAAAA'),
      ('control UserTweetsAndReplies', 'M9Tz6NGTHeRzqHOPdj9I7g'),
      for (final MapEntry(:key, :value) in ids.entries) (key, value),
    ]) {
      final uri = Uri.https('x.com', '/i/api/graphql/$id/UserTweetsAndReplies', probeParams);
      final response = await get(uri);
      final body = response.body.length > 160 ? response.body.substring(0, 160) : response.body;
      report.add('as UserTweetsAndReplies  $label: ${response.statusCode} $body');
      await Future.delayed(const Duration(seconds: 2));
    }
    for (final MapEntry(:key, :value) in ids.entries) {
      final uri = Uri.https('x.com', '/i/api/graphql/$value/$key', {
        'variables': jsonEncode(realVariables[key]),
        'features': jsonEncode(const {
          'c9s_list_members_action_api_enabled': false,
          'c9s_superc9s_indication_enabled': false,
        }),
      });
      final response = await get(uri);
      final body = response.body.length > 300 ? response.body.substring(0, 300) : response.body;
      report.add('guest $key: ${response.statusCode} $body');
      await Future.delayed(const Duration(seconds: 2));
    }
    // ignore: avoid_print
    print('\n${report.join('\n')}\n');
  }, timeout: const Timeout(Duration(minutes: 3)), skip: enabled ? false : 'live only');
}
