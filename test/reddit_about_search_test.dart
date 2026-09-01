import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_html.dart';

http.Response _json(Object body, int status) => http.Response(
  jsonEncode(body),
  status,
  headers: {'content-type': 'application/json'},
);

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
              'community_icon':
                  'https://styles.redditmedia.com/t5_2s30g/styles/communityIcon.png',
              'icon_img': '',
            },
          }, 200);
        }),
      );

      final about = await client.fetchSubredditAbout(
        'dartlang',
        clientId: 'id',
      );

      expect(about.name, 'dartlang');
      expect(about.title, 'The Dart Programming Language');
      expect(about.description, 'All things Dart.');
      expect(about.subscribers, 104532);
      expect(about.activeUsers, 1204);
      expect(
        about.iconUrl,
        'https://styles.redditmedia.com/t5_2s30g/styles/communityIcon.png',
      );
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
      final asked = <Uri>[];
      final client = RedditClient(
        httpClient: MockClient((request) async {
          asked.add(request.url);
          return http.Response(
            jsonEncode({
              'kind': 'Listing',
              'data': {
                'children': [
                  {
                    'kind': 't3',
                    'data': {
                      'id': 'abc',
                      'title': 'Flutter',
                      'subreddit': 'dartlang',
                      'permalink': '/r/dartlang/comments/abc/flutter/',
                    },
                  },
                ],
              },
            }),
            200,
            headers: {'content-type': 'application/json'},
          );
        }),
      );

      final posts = await client.searchPosts(
        'flutter',
        subreddit: 'dartlang',
        searchSort: 'comments',
      );

      expect(asked.first.path, '/r/dartlang/search.json');
      expect(asked.first.queryParameters['sort'], 'comments');
      expect(asked.first.queryParameters['restrict_sr'], 'on');
      expect(posts.single.title, 'Flutter');
    });

    test('OAuth search is used when a client id is present', () async {
      final hosts = <String>[];
      final client = RedditClient(
        httpClient: MockClient((request) async {
          hosts.add(request.url.host);
          if (request.url.path.contains('access_token')) {
            return _json(_tokenBody(), 200);
          }
          expect(request.url.host, 'oauth.reddit.com');
          expect(request.url.path, '/search');
          return _json({
            'kind': 'Listing',
            'data': {
              'children': [
                {
                  'kind': 't3',
                  'data': {
                    'id': 'xyz',
                    'title': 'Hu Tao',
                    'subreddit': 'Genshin_Impact',
                    'permalink': '/r/Genshin_Impact/comments/xyz/hu_tao/',
                    'num_comments': 9,
                  },
                },
              ],
            },
          }, 200);
        }),
      );

      final posts = await client.searchPosts('hu tao', clientId: 'id');

      expect(hosts, contains('oauth.reddit.com'));
      expect(posts.single.title, 'Hu Tao');
      expect(posts.single.commentCount, 9);
    });
  });
}
