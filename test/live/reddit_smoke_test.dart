import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:xta/plugins/reddit/reddit_client.dart';

/// Live checks of Reddit's account-free routes: what each host serves a reader
/// with no account and no client id, and whether the client still reads a
/// listing, a thread and a search through them. Opt in with:
///   fvm flutter test test/live/reddit_smoke_test.dart --dart-define=RUN_LIVE=true
void main() {
  const enabled = bool.fromEnvironment('RUN_LIVE', defaultValue: false);
  const skip = enabled ? false : 'Set RUN_LIVE=true to hit live Reddit hosts';

  test('each account-free route says what it serves', () async {
    final client = http.Client();
    addTearDown(client.close);
    for (final url in _routes) {
      // ignore: avoid_print
      print(await _describe(client, Uri.parse(url)));
    }
  }, skip: skip);

  test('a subreddit lists posts without an account', () async {
    final listing = await RedditClient().fetchSubreddit('flutterdev', clientId: '');
    expect(listing.posts, isNotEmpty);
    expect(listing.posts.first.title, isNotEmpty);
  }, skip: skip);

  test('a thread reads its comments without an account', () async {
    final client = RedditClient();
    final listing = await client.fetchSubreddit('flutterdev', clientId: '');
    final thread = await client.fetchComments(listing.posts.first.permalink, clientId: '');
    expect(thread.comments.isNotEmpty || thread.selfText != null || thread.postUrl != null, isTrue);
  }, skip: skip);

  test('a search finds posts without an account', () async {
    final posts = await RedditClient().searchPosts('flutter');
    expect(posts, isNotEmpty);
  }, skip: skip);
}

const _routes = [
  'https://old.reddit.com/r/flutterdev/hot',
  'https://www.reddit.com/r/flutterdev/hot.json?limit=5&raw_json=1',
  'https://old.reddit.com/r/flutterdev/hot.json?limit=5&raw_json=1',
  'https://www.reddit.com/r/flutterdev/',
  'https://www.reddit.com/r/flutterdev/hot/',
  'https://www.reddit.com/svc/shreddit/community-more-posts/hot/?name=flutterdev',
  'https://www.reddit.com/r/flutterdev/.rss',
  'https://old.reddit.com/r/flutterdev/.rss',
  'https://www.reddit.com/r/popular/',
  'https://www.reddit.com/search/?q=flutter&type=link',
  'https://www.reddit.com/r/flutterdev/about.json',
  'https://api.reddit.com/r/flutterdev/hot?limit=5',
];

const _markers = {
  'shreddit-post': '<shreddit-post',
  'shreddit-comment': '<shreddit-comment',
  'faceplate-partial': '<faceplate-partial',
  'thing': 'class=" thing',
  'json-listing': '"kind": "Listing"',
  'json-listing-compact': '"kind":"Listing"',
  'atom-entry': '<entry>',
  'login': '/login',
  'blocked': 'blocked by network security',
  'whoa': 'whoa there',
  'challenge': 'challenge',
  'captcha': 'captcha',
};

/// Status, redirect chain, type, size and what the body contains, without
/// the body itself.
Future<String> _describe(http.Client client, Uri start) async {
  final hops = <String>[];
  var uri = start;
  http.Response? response;
  for (var hop = 0; hop < 5; hop++) {
    final request = http.Request('GET', uri)
      ..followRedirects = false
      ..headers.addAll({
        'User-Agent': RedditClient.publicUserAgent,
        'Accept': 'text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8',
        'Accept-Language': 'en-US,en;q=0.9',
      });
    try {
      response = await http.Response.fromStream(await client.send(request)).timeout(const Duration(seconds: 20));
    } catch (e) {
      return '$start -> error: $e';
    }
    final location = response.headers['location'];
    if (response.statusCode >= 300 && response.statusCode < 400 && location != null) {
      hops.add('${response.statusCode} -> $location');
      uri = uri.resolve(location);
      continue;
    }
    break;
  }
  final body = response!.body;
  final title = RegExp(r'<title[^>]*>([^<]*)</title>', caseSensitive: false).firstMatch(body)?.group(1)?.trim();
  final found = [
    for (final entry in _markers.entries)
      if (body.toLowerCase().contains(entry.value.toLowerCase())) '${entry.key}x${entry.value.allMatches(body).length}',
  ];
  return [
    '$start',
    '  status ${response.statusCode}${hops.isEmpty ? '' : ' after ${hops.join(', ')}'}',
    '  type ${response.headers['content-type']}, ${body.length} chars, server ${response.headers['server']}',
    '  title ${title ?? '-'}',
    '  markers ${found.isEmpty ? '-' : found.join(' ')}',
  ].join('\n');
}
