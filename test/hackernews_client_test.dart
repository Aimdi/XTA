import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:xta/plugins/hackernews/hn_client.dart';
import 'package:xta/plugins/hackernews/hn_html.dart';
import 'package:xta/plugins/hackernews/hn_models.dart';

void main() {
  test('hnHtmlToText flattens paragraphs and keeps link text', () {
    expect(
      hnHtmlToText('Hello<p>next <i>bit</i> and <a href="https://x.com">X</a>'),
      'Hello\n\nnext bit and X',
    );
  });

  test('an Algolia hit becomes a story', () async {
    final client = HackerNewsClient(
      httpClient: MockClient((request) async {
        expect(request.url.host, hnAlgoliaHost);
        expect(request.url.queryParameters['tags'], 'front_page');
        return http.Response(
          jsonEncode({
            'nbPages': 2,
            'hits': [
              {
                'objectID': '8863',
                'title': 'My YC app: Dropbox',
                'url': 'http://www.paulgraham.com/dropbox.html',
                'author': 'pg',
                'points': 100,
                'num_comments': 12,
                'created_at_i': 1175714200,
                '_tags': ['story', 'front_page'],
              },
            ],
          }),
          200,
        );
      }),
    );

    final page = await client.feed(HnFeed.top);
    expect(page.hasMore, isTrue);
    expect(page.stories, hasLength(1));
    expect(page.stories.single.id, 8863);
    expect(page.stories.single.host, 'paulgraham.com');
    expect(page.stories.single.author, 'pg');
  });

  test('Best reads Firebase ids then items', () async {
    final client = HackerNewsClient(
      httpClient: MockClient((request) async {
        if (request.url.path.endsWith('beststories.json')) {
          return http.Response('[1, 2]', 200);
        }
        if (request.url.path.endsWith('/item/1.json')) {
          return http.Response(
            jsonEncode({
              'id': 1,
              'type': 'story',
              'title': 'Best',
              'by': 'alice',
              'score': 9,
              'descendants': 3,
              'time': 1175714200,
              'url': 'https://example.com/a',
            }),
            200,
          );
        }
        return http.Response(
          jsonEncode({'id': 2, 'type': 'comment', 'text': 'nope'}),
          200,
        );
      }),
    );

    final page = await client.feed(HnFeed.best);
    expect(page.stories.single.title, 'Best');
    expect(page.hasMore, isFalse);
  });

  test('a thread keeps nested comments', () async {
    final client = HackerNewsClient(
      httpClient: MockClient((request) async {
        return http.Response(
          jsonEncode({
            'id': 10,
            'title': 'Ask HN: tools',
            'author': 'bob',
            'points': 4,
            'type': 'story',
            'children': [
              {
                'id': 11,
                'author': 'carol',
                'text': 'Try <i>this</i>',
                'created_at_i': 1175714200,
                'children': [
                  {'id': 12, 'author': 'dan', 'text': 'ok', 'children': []},
                ],
              },
            ],
          }),
          200,
        );
      }),
    );

    final (story, comments) = await client.thread(10);
    expect(story.title, 'Ask HN: tools');
    expect(story.commentCount, 2);
    expect(comments.single.text, 'Try this');
    expect(comments.single.children.single.author, 'dan');
  });

  test('a Firebase user keeps karma and about', () async {
    final client = HackerNewsClient(
      httpClient: MockClient((request) async {
        expect(request.url.host, hnFirebaseHost);
        return http.Response(
          jsonEncode({
            'id': 'pg',
            'karma': 155000,
            'about': 'About <p>me',
            'created': 1175714200,
            'submitted': [1, 2, 3],
          }),
          200,
        );
      }),
    );

    final user = await client.user('pg');
    expect(user.id, 'pg');
    expect(user.karma, 155000);
    expect(user.about, contains('About'));
    expect(user.submittedCount, 3);
  });

  test('saved stories round-trip through JSON', () {
    final story = HnStory(
      id: 7,
      title: 'Hello',
      url: 'https://www.example.com/x',
      author: 'eve',
      score: 2,
      commentCount: 1,
      createdAt: DateTime.utc(2024, 1, 2),
    );
    final copy = HnStory.fromJson(story.toJson());
    expect(copy.id, 7);
    expect(copy.host, 'example.com');
    expect(copy.hnUrl, 'https://news.ycombinator.com/item?id=7');
  });
}
