import 'dart:convert';
import 'dart:io';

/// What the stand-in for X sends back for one request.
class MockAnswer {
  final int status;
  final String body;

  /// The fixture [body] came from; null when the stand-in wrote it itself.
  final String? fixture;

  const MockAnswer(this.status, this.body, {this.fixture});

  bool get answered => status == 200;
}

/// The recorded answers of one X operation, picked by the variable that names
/// what was asked for.
class MockRoute {
  /// The variable naming the account, post or community; null when one
  /// fixture answers every request.
  final String? key;

  /// Paths under test/fixtures by the lowercased value of [key], or under `*`
  /// for any value.
  final Map<String, String> fixtures;

  /// X's answer for something that does not exist, sent for a value without a
  /// fixture. Without one, such a request is not answered.
  final Object? missing;

  const MockRoute(this.fixtures, {this.key, this.missing});
}

const _flutterCommunity = '1472249315724771329';

const _emptyUserTimeline = {
  'data': {
    'user': {
      'result': {
        '__typename': 'User',
        'timeline': {
          'timeline': {'instructions': <Object>[]},
        },
      },
    },
  },
};

/// Everything the flows of .maestro/ open, answered from test/fixtures.
const mockRoutes = <String, MockRoute>{
  'UserByScreenName': MockRoute(
    {
      'quax_tests': 'UserByScreenName/modern.json',
      'quax_tests_2': 'UserByScreenName/quax_tests_2.json',
      'x': 'UserByScreenName/ok.json',
    },
    key: 'screen_name',
    missing: {'data': <String, Object>{}},
  ),
  'UserOriginalsTimeline': MockRoute({'*': 'UserOriginalsTimeline/page.json'}),
  'UserTweetsAndReplies': MockRoute({'*': 'UserOriginalsTimeline/page.json'}),
  // The Posts tab reads reposts beside the posts; the recorded accounts repost nothing.
  'UserRepostsTimeline': MockRoute({}, missing: _emptyUserTimeline),
  'TweetDetail': MockRoute(
    {'2105589900078641579': 'TweetDetail/subscriber_preview.json'},
    key: 'focalTweetId',
    missing: {
      'data': {
        'threaded_conversation_with_injections_v2': {'instructions': <Object>[]},
      },
    },
  ),
  'CommunityQuery': MockRoute(
    {_flutterCommunity: 'Community/community.json'},
    key: 'communityId',
    missing: {
      'data': {'communityResults': <String, Object>{}},
    },
  ),
  'CommunityTweetsTimeline': MockRoute({_flutterCommunity: 'Community/timeline.json'}, key: 'communityId'),
  'membersSliceTimeline_Query': MockRoute({_flutterCommunity: 'Community/members.json'}, key: 'communityId'),
  'CommunitiesSearchQuery': MockRoute({'*': 'Community/search.json'}),
};

/// The lists a later page of a timeline fills; the page after the last
/// recorded one comes back with them empty, which ends the list.
const _pagedLists = {'entries', 'moduleItems', 'items_results'};

final _graphql = RegExp(r'^(?:/i/api)?/graphql/[\w-]+/(\w+)$');

/// A stand-in for X that answers from recorded responses. Anything it was not
/// given an answer for gets a 501 saying what is missing.
class MockX {
  final Directory fixtures;
  final Map<String, MockRoute> routes;

  MockX(this.fixtures, {this.routes = mockRoutes});

  MockAnswer answer(String method, Uri url) {
    if (method == 'POST' && url.path == '/1.1/guest/activate.json') {
      return const MockAnswer(200, '{"guest_token":"1"}');
    }
    final operation = _graphql.firstMatch(url.path)?.group(1);
    final route = routes[operation];
    if (operation == null || route == null) {
      return MockAnswer(501, 'The stand-in for X has no answer for $method $url. It answers ${_known()}.');
    }
    final variables = _variables(url);
    final value = route.key == null ? null : '${variables[route.key] ?? ''}'.toLowerCase();
    final path = route.fixtures[value] ?? route.fixtures['*'];
    if (path == null) {
      final missing = route.missing;
      return missing == null
          ? MockAnswer(501, 'No $operation recorded for ${route.key} "$value": only ${route.fixtures.keys.join(', ')}.')
          : MockAnswer(200, jsonEncode(missing));
    }
    final recorded = jsonDecode(File('${fixtures.path}/$path').readAsStringSync());
    final laterPage = variables['cursor'] != null;
    return MockAnswer(200, jsonEncode(laterPage ? _endOfList(recorded) : recorded), fixture: path);
  }

  String _known() => [...routes.keys, 'the guest token'].join(', ');
}

Map<String, Object?> _variables(Uri url) {
  try {
    final decoded = jsonDecode(url.queryParameters['variables'] ?? '{}');
    return decoded is Map<String, Object?> ? decoded : const {};
  } on FormatException {
    return const {};
  }
}

Object? _endOfList(Object? json) => switch (json) {
  Map() => {for (final MapEntry(:key, :value) in json.entries) key: _pagedLists.contains(key) ? [] : _endOfList(value)},
  List() => [for (final item in json) _endOfList(item)],
  _ => json,
};
