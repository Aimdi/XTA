import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_html.dart';

http.Response _json(Object body, int status) =>
    http.Response(jsonEncode(body), status, headers: {'content-type': 'application/json'});

Map<String, dynamic> _tokenBody() => {
  'access_token': 'tok_123',
  'token_type': 'bearer',
  'expires_in': 3600,
  'scope': '*',
};

const _sidebarPage = '''
<!doctype html><html><body>
  <div class="side">
    <div class="titlebox">
      <h1 class="redditname"><a href="/r/dartlang/">dartlang</a></h1>
      <span class="subscribers"><span class="number">104,532</span> readers</span>
      <p class="users-online"><span class="number">1,204</span> users here now</p>
      <form class="usertext"><div class="usertext-body"><div class="md"><p>All things Dart.</p></div></div></form>
    </div>
  </div>
</body></html>
''';

void main() {
  group('what a community says about itself', () {
    test('the old.reddit sidebar yields name, counts and description', () {
      final side = parseSubredditSidebar(_sidebarPage);

      expect(side.title, 'dartlang');
      expect(side.subscribers, 104532);
      expect(side.activeUsers, 1204);
      expect(side.description, 'All things Dart.');
    });

    test('a page with no sidebar yields nothing rather than throwing', () {
      final side = parseSubredditSidebar('<html><body><p>hi</p></body></html>');

      expect(side.title, isNull);
      expect(side.subscribers, isNull);
      expect(side.description, isNull);
    });

    test('with a client id, about.json is read over OAuth', () async {
      final client = RedditClient(
        httpClient: MockClient((request) async {
          if (request.url.path.contains('access_token')) {
            return _json(_tokenBody(), 200);
          }
          expect(request.url.path, '/r/dartlang/about.json');
          expect(request.headers['Authorization'], 'Bearer tok_123');
          return _json({
            'kind': 't5',
            'data': {
              'display_name': 'dartlang',
              'title': 'The Dart Programming Language',
              'public_description': 'All things Dart.',
              'subscribers': 104532,
              'active_user_count': 1204,
              'over18': false,
            },
          }, 200);
        }),
      );

      final about = await client.fetchSubredditAbout('dartlang', clientId: 'id');

      expect(about.name, 'dartlang');
      expect(about.title, 'The Dart Programming Language');
      expect(about.description, 'All things Dart.');
      expect(about.subscribers, 104532);
      expect(about.activeUsers, 1204);
    });

    test('without any credential, the sidebar is scraped instead', () async {
      Uri? asked;
      final client = RedditClient(
        httpClient: MockClient((request) async {
          asked = request.url;
          return http.Response(_sidebarPage, 200);
        }),
      );

      final about = await client.fetchSubredditAbout('dartlang', clientId: '');

      expect(asked!.path, '/r/dartlang/');
      expect(about.subscribers, 104532);
      expect(about.description, 'All things Dart.');
    });
  });

  group('search order', () {
    test('the chosen order and the community scope ride the query', () async {
      Uri? asked;
      final client = RedditClient(
        httpClient: MockClient((request) async {
          asked = request.url;
          return http.Response('<html><body><div id="siteTable"></div></body></html>', 200);
        }),
      );

      await client.searchPosts('flutter', subreddit: 'dartlang', searchSort: 'comments');

      expect(asked!.path, '/r/dartlang/search');
      expect(asked!.queryParameters['sort'], 'comments');
      expect(asked!.queryParameters['restrict_sr'], 'on');
    });
  });
}
