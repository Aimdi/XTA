import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:xta/plugins/reddit/reddit_client.dart';

// Temporary: dumps what Reddit's Atom feeds hold, to build the parser from.
void main() {
  const enabled = bool.fromEnvironment('RUN_LIVE', defaultValue: false);
  const skip = enabled ? false : 'Set RUN_LIVE=true to hit live Reddit hosts';

  test('dump feeds', () async {
    final client = http.Client();
    addTearDown(client.close);

    final first = await _dump(client, 'https://www.reddit.com/r/flutterdev/.rss?limit=3', entries: 3);
    final ids = RegExp(r'<id>(t3_[a-z0-9]+)</id>').allMatches(first).map((m) => m.group(1)!).toList();
    final links = RegExp(r'<link href="([^"]+/comments/[^"]+)"').allMatches(first).map((m) => m.group(1)!).toList();
    if (ids.isNotEmpty) {
      await _dump(client, 'https://www.reddit.com/r/flutterdev/.rss?limit=3&after=${ids.last}', entries: 1);
    }
    await _dump(client, 'https://www.reddit.com/r/flutterdev/new/.rss?limit=2', entries: 1);
    await _dump(client, 'https://www.reddit.com/r/flutterdev/top/.rss?t=week&limit=2', entries: 1);
    await _dump(client, 'https://www.reddit.com/r/flutterdev/hot.rss?limit=2', entries: 0);
    await _dump(client, 'https://www.reddit.com/r/aww/.rss?limit=8', entries: 8);
    await _dump(client, 'https://www.reddit.com/r/pics/.rss?limit=2', entries: 2);
    await _dump(client, 'https://www.reddit.com/r/flutterdev+dartlang/.rss?limit=4', entries: 0);
    for (final link in links.take(2)) {
      await _dump(client, '$link.rss?limit=6', entries: 6);
    }
    if (links.isNotEmpty) {
      await _dump(client, '${links.first}.rss?sort=new&limit=3', entries: 0);
      await _dump(client, '${links.first}.rss?limit=500', entries: 0);
    }
    await _dump(client, 'https://www.reddit.com/search.rss?q=flutter&sort=new&limit=2', entries: 2);
    await _dump(client, 'https://www.reddit.com/search/.rss?q=flutter&limit=2', entries: 0);
    await _dump(client, 'https://www.reddit.com/r/flutterdev/search.rss?q=dart&restrict_sr=on&limit=2', entries: 1);
    await _dump(client, 'https://www.reddit.com/subreddits/search.rss?q=flutter&limit=2', entries: 2);
    await _dump(client, 'https://www.reddit.com/user/spez/submitted/.rss?limit=2', entries: 1);
    await _dump(client, 'https://www.reddit.com/user/spez/.rss?limit=2', entries: 2);
    await _dump(client, 'https://www.reddit.com/.rss?limit=2', entries: 0);
    await _dump(client, 'https://www.reddit.com/r/popular/.rss?limit=2', entries: 0);
    await _dump(client, 'https://www.reddit.com/r/all/.rss?limit=2', entries: 0);
    await _dump(client, 'https://www.reddit.com/r/thissubdoesnotexist4815162342/.rss', entries: 0);
    await _dump(client, 'https://www.reddit.com/r/fatpeoplehate/.rss', entries: 0);
    await _dump(client, 'https://www.reddit.com/r/WTF/.rss?limit=2', entries: 0);
    await _dump(
      client,
      'https://www.reddit.com/r/flutterdev/.rss?limit=2',
      entries: 0,
      userAgent: RedditClient.userAgent,
    );
    await _dump(client, 'https://www.reddit.com/r/flutterdev/about/.rss', entries: 0);
    await _dump(client, 'https://www.reddit.com/r/flutterdev/wiki/index/.rss', entries: 0);
    await _dump(client, 'https://www.reddit.com/r/flutterdev/comments/.rss?limit=2', entries: 1);
  }, skip: skip);
}

const _headers = [
  'content-type',
  'x-ratelimit-remaining',
  'x-ratelimit-used',
  'x-ratelimit-reset',
  'cache-control',
  'set-cookie',
  'location',
];

/// Prints status, headers, the feed head and [entries] entries in 1500-char
/// lines prefixed with `| `, and returns the body.
Future<String> _dump(http.Client client, String url, {required int entries, String? userAgent}) async {
  final request = http.Request('GET', Uri.parse(url))
    ..followRedirects = false
    ..headers.addAll({
      'User-Agent': userAgent ?? RedditClient.publicUserAgent,
      'Accept': 'text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8',
      'Accept-Language': 'en-US,en;q=0.9',
    });
  final http.Response response;
  try {
    response = await http.Response.fromStream(await client.send(request)).timeout(const Duration(seconds: 20));
  } catch (e) {
    _print('=== $url -> error $e');
    return '';
  }
  final body = response.body;
  final count = '<entry>'.allMatches(body).length;
  _print('=== $url (ua ${userAgent == null ? 'browser' : 'app'})');
  _print('status ${response.statusCode}, ${body.length} chars, $count entries');
  for (final name in _headers) {
    if (response.headers[name] case final value?) {
      _print('header $name: ${name == 'set-cookie' ? value.replaceAll(RegExp(r'=[^;,]{8,}'), '=…') : value}');
    }
  }
  final start = body.indexOf('<entry>');
  final head = start < 0 ? body.substring(0, body.length.clamp(0, 3000)) : body.substring(0, start);
  _chunks(head);
  var from = start;
  for (var i = 0; i < entries && from >= 0; i++) {
    final end = body.indexOf('</entry>', from);
    if (end < 0) break;
    _print('--- entry ${i + 1}');
    _chunks(body.substring(from, end + '</entry>'.length));
    from = body.indexOf('<entry>', end);
  }
  return body;
}

void _chunks(String text) {
  for (var i = 0; i < text.length; i += 1500) {
    _print('| ${text.substring(i, (i + 1500).clamp(0, text.length))}');
  }
}

// ignore: avoid_print
void _print(String line) => print(line);
