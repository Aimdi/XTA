import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/threads/threads_models.dart';
import 'package:xta/plugins/threads/threads_parse.dart';
import 'package:xta/plugins/threads/threads_rich_text.dart';
import 'package:xta/utils/json.dart';

Map<String, Object?> _post(
  String pk, {
  String user = 'zuck',
  String text = 'hello',
  Map<String, Object?> extra = const {},
  Map<String, Object?> info = const {},
}) => {
  'pk': pk,
  'code': 'C$pk',
  'taken_at': 1720000000,
  'caption': {'text': text},
  'user': {'username': user, 'full_name': user.toUpperCase()},
  'text_post_app_info': info,
  ...extra,
};

String _page(List<List<Map<String, Object?>>> chains) {
  final blob = jsonEncode({
    'data': {
      'edges': [
        for (final chain in chains)
          {
            'node': {
              'thread_items': [
                for (final post in chain) {'post': post},
              ],
            },
          },
      ],
    },
  });
  return '<html><script type="application/json" data-sjs>$blob</script></html>';
}

void main() {
  group('media', () {
    test('a video keeps its poster and the largest playable rendition', () {
      final post = threadsPostFromApi(
        Json(
          _post(
            '1',
            extra: {
              'image_versions2': {
                'candidates': [
                  {'url': 'https://cdn/poster.jpg', 'width': 720, 'height': 1280},
                ],
              },
              'video_versions': [
                {'url': 'https://cdn/small.mp4', 'width': 360, 'height': 640},
                {'url': 'https://cdn/large.mp4', 'width': 720, 'height': 1280},
              ],
            },
          ),
        ),
      )!;

      expect(post.images, ['https://cdn/poster.jpg']);
      expect(post.videoUrls, ['https://cdn/large.mp4']);
      expect(post.hasVideo, isTrue);
      final item = post.mediaItems.single;
      expect(item.isVideo, isTrue);
      expect(item.videoUrl, 'https://cdn/large.mp4');
      expect(item.aspectRatio, closeTo(720 / 1280, 0.001));
    });

    test('a carousel mixes stills and videos, each with its description', () {
      final post = threadsPostFromApi(
        Json(
          _post(
            '2',
            extra: {
              'carousel_media': [
                {
                  'image_versions2': {
                    'candidates': [
                      {'url': 'https://cdn/a.jpg'},
                    ],
                  },
                  'accessibility_caption': 'A dog on a beach',
                },
                {
                  'image_versions2': {
                    'candidates': [
                      {'url': 'https://cdn/b.jpg'},
                    ],
                  },
                  'video_versions': [
                    {'url': 'https://cdn/b.mp4'},
                  ],
                },
              ],
            },
          ),
        ),
      )!;

      expect(post.images, ['https://cdn/a.jpg', 'https://cdn/b.jpg']);
      expect(post.imageAlts, ['A dog on a beach', null]);
      expect(post.videoUrls, [null, 'https://cdn/b.mp4']);
      expect(post.mediaItems.map((m) => m.isVideo), [false, true]);
      expect(post.mediaItems.first.alt, 'A dog on a beach');
    });
  });

  group('quotes, topics and counts', () {
    test('reads the quoted post one level deep', () {
      final post = threadsPostFromApi(
        Json(
          _post(
            '3',
            text: 'Agreed',
            info: {
              'quote_count': 5,
              'share_info': {
                'quoted_post': _post(
                  '4',
                  user: 'mosseri',
                  text: 'Original thought',
                  info: {
                    'share_info': {'quoted_post': _post('5', user: 'deep')},
                  },
                ),
              },
            },
          ),
        ),
      )!;

      expect(post.quoteCount, 5);
      expect(post.quoted?.handle, 'mosseri');
      expect(post.quoted?.text, 'Original thought');
      expect(post.quoted?.quoted, isNull);
    });

    test('a quote with no words of its own is still a post', () {
      final post = threadsPostFromApi(
        Json(
          _post(
            '6',
            text: '',
            info: {
              'share_info': {'quoted_post': _post('7', user: 'meta')},
            },
          ),
        ),
      );

      expect(post, isNotNull);
      expect(post!.quoted?.handle, 'meta');
    });

    test('reads the topic tag without its hash', () {
      final post = threadsPostFromApi(
        Json(
          _post(
            '8',
            info: {
              'tag_header': {'display_name': '#Books'},
            },
          ),
        ),
      )!;

      expect(post.topicTag, 'Books');
    });

    test('ids sent as numbers by the app API are kept', () {
      final post = threadsPostFromApi(
        Json({
          'pk': 3412345678901234567,
          'code': 'Num',
          'caption': {'text': 'numeric'},
          'user': {'username': 'zuck'},
        }),
      );

      expect(post?.id, '3412345678901234567');
    });
  });

  group('text fragments', () {
    Map<String, Object?> fragments(String caption, List<Map<String, Object?>> parts) => _post(
      '9',
      text: caption,
      info: {
        'text_fragments': {'fragments': parts},
      },
    );

    test('resolves mentions, unwrapped links and tags', () {
      final post = threadsPostFromApi(
        Json(
          fragments('Hi @mosseri see example.com/a… #Books', [
            {'fragment_type': 'plaintext', 'plaintext': 'Hi '},
            {
              'fragment_type': 'mention',
              'plaintext': '@mosseri',
              'mention_fragment': {
                'mentioned_user': {'username': 'mosseri'},
              },
            },
            {'fragment_type': 'plaintext', 'plaintext': ' see '},
            {
              'fragment_type': 'link',
              'plaintext': 'example.com/a…',
              'link_fragment': {'uri': 'https://l.threads.com/?u=https%3A%2F%2Fexample.com%2Farticle'},
            },
            {'fragment_type': 'plaintext', 'plaintext': ' '},
            {
              'fragment_type': 'tag',
              'plaintext': '#Books',
              'tag_fragment': {'display_name': 'Books'},
            },
          ]),
        ),
      )!;

      expect(post.fragments.map((f) => f.kind), [
        ThreadsFragmentKind.text,
        ThreadsFragmentKind.mention,
        ThreadsFragmentKind.text,
        ThreadsFragmentKind.link,
        ThreadsFragmentKind.text,
        ThreadsFragmentKind.tag,
      ]);
      expect(post.fragments[1].target, 'mosseri');
      expect(post.fragments[3].target, 'https://example.com/article');
      expect(post.fragments[5].target, 'Books');
    });

    test('a split that does not spell the caption is ignored', () {
      final post = threadsPostFromApi(
        Json(
          fragments('Hello world', [
            {
              'fragment_type': 'mention',
              'plaintext': '@someone',
              'mention_fragment': {
                'mentioned_user': {'username': 'someone'},
              },
            },
          ]),
        ),
      )!;

      expect(post.fragments, isEmpty);
    });

    test('plain-text-only fragments add nothing over the caption', () {
      final post = threadsPostFromApi(
        Json(
          fragments('Just words', [
            {'fragment_type': 'plaintext', 'plaintext': 'Just words'},
          ]),
        ),
      )!;

      expect(post.fragments, isEmpty);
    });

    test('caption runs fall back to patterns without fragments', () {
      final runs = threadsCaptionRuns('Hi @alice see https://a.co/x.', const []);

      expect(runs.map((r) => r.kind), [
        ThreadsFragmentKind.text,
        ThreadsFragmentKind.mention,
        ThreadsFragmentKind.text,
        ThreadsFragmentKind.link,
        ThreadsFragmentKind.text,
      ]);
      expect(runs[1].target, 'alice');
      expect(runs[3].target, 'https://a.co/x');
      expect(runs.map((r) => r.text).join(), 'Hi @alice see https://a.co/x.');
    });
  });

  group('self-threads', () {
    test('a profile bucket counts the author\'s continuation only', () {
      final posts = parseThreadsApiFeed({
        'threads': [
          {
            'thread_items': [
              {'post': _post('10')},
              {'post': _post('11')},
              {'post': _post('12')},
              {'post': _post('13', user: 'someone')},
              {'post': _post('14')},
            ],
          },
        ],
      });

      expect(posts.single.id, '10');
      expect(posts.single.selfThreadCount, 2);
    });

    test('SSR profile scrape keeps the chain length too', () {
      final posts = parseThreadsSsrHtml(
        _page([
          [_post('20'), _post('21')],
          [_post('30', user: 'other')],
        ]),
        'zuck',
      );

      expect(posts.map((p) => p.id), ['20']);
      expect(posts.single.selfThreadCount, 1);
    });
  });

  group('server-rendered pages', () {
    test('threadsSsrChains keeps each thread_items list as a chain', () {
      final chains = threadsSsrChains(
        _page([
          [_post('1'), _post('2')],
          [_post('3', user: 'a'), _post('4', user: 'b')],
          [_post('2'), _post('5', user: 'c')],
        ]),
      );

      expect(chains.map((c) => c.map((p) => p.id).toList()), [
        ['1', '2'],
        ['3', '4'],
        ['5'],
      ]);
    });

    test('the replies page keeps only the profile\'s own replies', () {
      final replies = parseThreadsSsrReplies(
        _page([
          [
            _post('1', user: 'meta'),
            _post(
              '2',
              info: {
                'is_reply': true,
                'reply_to_author': {'username': 'meta'},
              },
            ),
          ],
          [_post('3', user: 'other'), _post('4', user: 'another')],
        ]),
        'zuck',
      );

      expect(replies.map((p) => p.id), ['2']);
      expect(replies.single.replyToHandle, 'meta');
    });
  });

  group('snapshots', () {
    test('every new field survives a round trip, quotes one level deep', () {
      const post = ThreadsPost(
        id: '1',
        handle: 'zuck',
        authorName: 'Mark',
        text: 'Hi @meta',
        images: ['https://cdn/p.jpg'],
        imageAspects: [0.5],
        imageAlts: ['alt'],
        videoUrls: ['https://cdn/v.mp4'],
        url: 'https://www.threads.com/@zuck/post/ABC',
        quoteCount: 3,
        topicTag: 'Books',
        selfThreadCount: 2,
        fragments: [
          ThreadsTextFragment(ThreadsFragmentKind.text, 'Hi '),
          ThreadsTextFragment(ThreadsFragmentKind.mention, '@meta', 'meta'),
        ],
        quoted: ThreadsPost(
          id: '2',
          handle: 'meta',
          authorName: 'Meta',
          text: 'quoted',
          quoted: ThreadsPost(id: '3', handle: 'x', authorName: 'x', text: 'too deep'),
        ),
      );

      final back = ThreadsPost.listFromPrefs(ThreadsPost.listToPrefs([post])).single;

      expect(back.imageAlts, ['alt']);
      expect(back.videoUrls, ['https://cdn/v.mp4']);
      expect(back.hasVideo, isTrue);
      expect(back.quoteCount, 3);
      expect(back.topicTag, 'Books');
      expect(back.selfThreadCount, 2);
      expect(back.fragments.map((f) => f.target), [null, 'meta']);
      expect(back.quoted?.text, 'quoted');
      expect(back.quoted?.quoted, isNull);
      expect(back.shortcode, 'ABC');
    });

    test('an old snapshot without the new fields still reads', () {
      final back = ThreadsPost.fromSnapshot({
        'id': '1',
        'handle': 'zuck',
        'text': 'old',
        'images': ['https://cdn/p.jpg'],
      });

      expect(back.videoUrls, isEmpty);
      expect(back.mediaItems.single.isVideo, isFalse);
      expect(back.fragments, isEmpty);
      expect(back.selfThreadCount, 0);
    });
  });

  test('threadsShortcodeOf reads post and short links on either host', () {
    expect(threadsShortcodeOf('https://www.threads.net/@zuck/post/Dabc?igshid=1'), 'Dabc');
    expect(threadsShortcodeOf('https://threads.com/t/Xyz'), 'Xyz');
    expect(threadsShortcodeOf('https://www.threads.com/@zuck'), isNull);
    expect(threadsShortcodeOf('https://example.com/post/abc'), isNull);
  });
}
