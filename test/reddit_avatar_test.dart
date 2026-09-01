import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/reddit/reddit_avatar.dart';

void main() {
  group('choosing an avatar', () {
    test('the same name always gets the same one', () {
      // The whole point: an avatar that moved between runs would be worse than
      // no avatar at all.
      final first = redditAvatarFor('spez');
      final second = redditAvatarFor('spez');

      expect(first.face, same(second.face));
      expect(first.background, second.background);
      expect(first.accent, second.accent);
    });

    test('case does not make a different person', () {
      expect(redditAvatarFor('SomeOne').background, redditAvatarFor('someone').background);
      expect(redditAvatarFor('SomeOne').face, same(redditAvatarFor('someone').face));
    });

    test('different names generally look different', () {
      final names = List.generate(60, (i) => 'reader$i');
      final looks = names.map((n) {
        final a = redditAvatarFor(n);
        return '${redditAvatarFaces.indexOf(a.face)}/${a.background}/${a.accent}';
      }).toSet();

      // 12 faces × 12 backgrounds × 6 accents is plenty of room; a collision
      // rate this low means the hash is spreading rather than clumping.
      expect(looks.length, greaterThan(50));
    });

    test('every face and background gets used across enough names', () {
      final faces = <int>{};
      final backgrounds = <int>{};
      for (var i = 0; i < 400; i++) {
        final a = redditAvatarFor('user$i');
        faces.add(redditAvatarFaces.indexOf(a.face));
        backgrounds.add(redditAvatarBackgrounds.indexOf(a.background));
      }

      expect(faces.length, redditAvatarFaces.length, reason: 'a face nobody gets is dead weight');
      expect(backgrounds.length, redditAvatarBackgrounds.length);
    });

    test('an empty name still resolves rather than throwing', () {
      expect(() => redditAvatarFor(''), returnsNormally);
    });
  });

  group('the faces themselves', () {
    test('are all the same square grid', () {
      for (final face in redditAvatarFaces) {
        expect(face.length, 8, reason: face.join('|'));
        for (final row in face) {
          expect(row.length, 8, reason: row);
        }
      }
    });

    test('use only the three known cells', () {
      for (final face in redditAvatarFaces) {
        for (final row in face) {
          for (final cell in row.split('')) {
            expect('.#o', contains(cell), reason: 'unexpected "$cell" in $row');
          }
        }
      }
    });

    test('every face actually draws something', () {
      for (final face in redditAvatarFaces) {
        expect(face.join().replaceAll('.', ''), isNotEmpty);
      }
    });
  });

  group('ink', () {
    test('is dark on a light background and light on a dark one', () {
      expect(redditAvatarInk(const Color(0xFFE9E9E9)).computeLuminance(), lessThan(0.2));
      expect(redditAvatarInk(const Color(0xFF37474F)).computeLuminance(), greaterThan(0.8));
    });

    test('every background gets ink that contrasts with it', () {
      for (final background in redditAvatarBackgrounds) {
        final ink = redditAvatarInk(background);
        final difference = (ink.computeLuminance() - background.computeLuminance()).abs();
        expect(difference, greaterThan(0.25), reason: '$background');
      }
    });
  });
}
