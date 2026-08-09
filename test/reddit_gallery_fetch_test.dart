import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';

/// Reddit's comments endpoint is `[postListing, commentsListing]` — the same
/// shape the thread already reads, which is why the pictures can be had this
/// way without knowing anything about old.reddit's HTML.
Object _threadBody({Map<String, dynamic> postExtra = const {}}) => [
  {
    'kind': 'Listing',
    'data': {
      'children': [
        {
          'kind': 't3',
          'data': {
            'id': 'g1',
            'title': 'A gallery',
            'subreddit': 'pics',
            'permalink': '/r/pics/comments/g1/a_gallery/',
            'url': 'https://www.reddit.com/gallery/g1',
            ...postExtra,
          },
        },
      ],
    },
  },
  {
    'kind': 'Listing',
    'data': {'children': <Object>[]},
  },
];

const _gallery = {
  'gallery_data': {
    'items': [
      {'media_id': 'two'},
      {'media_id': 'one'},
    ],
  },
  'media_metadata': {
    'one': {
      's': {'u': 'https://preview.redd.it/one.jpg?width=640&amp;s=abc'},
    },
    'two': {
      's': {'u': 'https://preview.redd.it/two.jpg?width=640&amp;s=def'},
    },
  },
};

void main() {
  group('fetching the pictures a scraped gallery post is missing', () {
    test('reads them from the post\'s own public JSON, in order', () async {
      late Uri asked;
      final client = RedditClient(
        httpClient: MockClient((request) async {
          asked = request.url;
          return http.Response(
            jsonEncode(_threadBody(postExtra: _gallery)),
            200,
            headers: {'content-type': 'application/json'},
          );
        }),
      );

      final images = await client.fetchGalleryImages('/r/pics/comments/g1/a_gallery/');

      expect(images, [
        'https://preview.redd.it/two.jpg?width=640&s=def',
        'https://preview.redd.it/one.jpg?width=640&s=abc',
      ]);
      // No OAuth: this is the route that works with no account at all.
      expect(asked.path, endsWith('.json'));
    });

    test('a post that turns out not to be a gallery yields nothing', () async {
      final client = RedditClient(
        httpClient: MockClient(
          (_) async => http.Response(jsonEncode(_threadBody()), 200, headers: {'content-type': 'application/json'}),
        ),
      );

      expect(await client.fetchGalleryImages('/r/pics/comments/g1/a_gallery/'), isEmpty);
    });

    // A feed must not turn into an error screen because one card's extra
    // request was refused.
    test('a refusal yields nothing rather than throwing', () async {
      final client = RedditClient(httpClient: MockClient((_) async => http.Response('nope', 403)));

      expect(await client.fetchGalleryImages('/r/pics/comments/g1/a_gallery/'), isEmpty);
    });

    test('a body that is not JSON yields nothing rather than throwing', () async {
      final client = RedditClient(httpClient: MockClient((_) async => http.Response('<html>blocked</html>', 200)));

      expect(await client.fetchGalleryImages('/r/pics/comments/g1/a_gallery/'), isEmpty);
    });
  });
}
