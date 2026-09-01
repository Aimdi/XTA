import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/substack/substack_models.dart';

void main() {
  group('local like/save snapshots', () {
    test('round-trip omits body but keeps what the library needs', () {
      final post = SubstackPost(
        id: '42',
        title: 'Hello',
        slug: 'hello',
        publicationBaseUrl: 'https://example.substack.com',
        publicationName: 'Example',
        subtitle: 'A note',
        coverImage: 'https://img.example/c.jpg',
        bodyHtml: '<p>secret</p>',
        reactionCount: 3,
      );

      final raw = SubstackPost.listToPrefs([post]);
      expect(raw.contains('secret'), isFalse);

      final back = SubstackPost.listFromPrefs(raw).single;
      expect(back.id, '42');
      expect(back.title, 'Hello');
      expect(back.slug, 'hello');
      expect(back.publicationName, 'Example');
      expect(back.coverImage, contains('img.example'));
      expect(back.bodyHtml, isNull);
      expect(back.reactionCount, 3);
    });

    test('corrupt prefs become an empty library, not a throw', () {
      expect(SubstackPost.listFromPrefs('not-json'), isEmpty);
      expect(SubstackPost.listFromPrefs('{"x":1}'), isEmpty);
    });
  });
}
