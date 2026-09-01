import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/substack/substack_models.dart';

SubstackPost _post(Map<String, Object?> extra) => SubstackPost.fromJson(
      {'id': 1, 'slug': 'a-post', 'title': 'A post', ...extra},
      publicationBaseUrl: 'https://example.substack.com',
      publicationName: 'Example',
    );

void main() {
  group('what a podcast post carries', () {
    test('the episode file makes it a podcast', () {
      final post = _post({'podcast_url': 'https://api.substack.com/feed/podcast/1/ep.mp3', 'type': 'podcast'});

      expect(post.isPodcast, isTrue);
      expect(post.audioUrl, contains('.mp3'));
    });

    test('a newsletter is not one, and an empty URL is no episode', () {
      expect(_post({}).isPodcast, isFalse);
      expect(_post({'podcast_url': ''}).isPodcast, isFalse);
    });
  });

  group('what a post has gathered', () {
    test('the totals are read when carried', () {
      final post = _post({'reaction_count': 42, 'comment_count': 7});

      expect(post.reactionCount, 42);
      expect(post.commentCount, 7);
    });

    test('the per-emoji map is summed when there is no total', () {
      expect(_post({'reactions': {'❤': 3, '😂': 2}}).reactionCount, 5);
    });

    test('nothing carried is null, which is not the same as zero', () {
      final post = _post({});

      expect(post.reactionCount, isNull);
      expect(post.commentCount, isNull);
    });
  });

  group('reading a discussion', () {
    test('the tree flattens in reading order, children under their parent', () {
      final comments = flattenSubstackComments({
        'comments': [
          {
            'id': 1,
            'name': 'ann',
            'body': 'First',
            'date': '2026-07-01T10:00:00Z',
            'children': [
              {'id': 2, 'name': 'ben', 'body': 'Reply', 'children': []},
            ],
          },
          {'id': 3, 'name': 'cay', 'body': 'Second'},
        ],
      });

      expect(comments.map((c) => c.body), ['First', 'Reply', 'Second']);
      expect(comments.map((c) => c.depth), [0, 1, 0]);
    });

    test('a deleted comment vanishes but its children keep their place', () {
      final comments = flattenSubstackComments({
        'comments': [
          {
            'id': 1,
            'body': '',
            'children': [
              {'id': 2, 'name': 'ben', 'body': 'Orphan'},
            ],
          },
        ],
      });

      expect(comments.single.body, 'Orphan');
      expect(comments.single.depth, 0, reason: 'no visible parent to sit under');
    });

    test('a payload that no longer fits is an empty discussion, not a throw', () {
      expect(flattenSubstackComments(null), isEmpty);
      expect(flattenSubstackComments('nonsense'), isEmpty);
      expect(flattenSubstackComments({'comments': 'nope'}), isEmpty);
      expect(flattenSubstackComments({'comments': [42, null]}), isEmpty);
    });

    test('a runaway thread stops indenting at the cap', () {
      Map<String, Object?> nest(int depth) => {
            'id': depth,
            'name': 'a',
            'body': 'level $depth',
            if (depth < 12) 'children': [nest(depth + 1)],
          };

      final comments = flattenSubstackComments({'comments': [nest(0)]});

      expect(comments, hasLength(9), reason: 'the cap ends the walk, not the list');
    });
  });
}
