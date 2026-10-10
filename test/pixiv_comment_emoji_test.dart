import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/pixiv/pixiv_comment_emoji.dart';

PixivCommentPart _text(String text) => PixivCommentTextPart(text);

PixivCommentPart _emoji(String name) => PixivCommentEmojiPart(name, pixivEmojiIds[name]!);

void main() {
  group('pixivEmojiIds', () {
    test('numbers every set the way Pixiv serves the images', () {
      expect(pixivEmojiIds.length, 38);
      expect(
        {
          for (final name in ['normal', 'cry', 'normal2', 'panic2', 'love3', 'sleep4', 'heart', 'star'])
            name: pixivEmojiIds[name],
        },
        {
          'normal': 101,
          'cry': 108,
          'normal2': 201,
          'panic2': 209,
          'love3': 310,
          'sleep4': 408,
          'heart': 501,
          'star': 503,
        },
      );
      expect(pixivEmojiUrl(501), 'https://s.pximg.net/common/images/emoji/501.png');
    });
  });

  group('pixivCommentParts', () {
    test('(normal) and (heart) become emoji between the words', () {
      expect(pixivCommentParts('Great (normal) work (heart)'), [
        _text('Great '),
        _emoji('normal'),
        _text(' work '),
        _emoji('heart'),
      ]);
    });

    test('an unknown code stays text', () {
      expect(pixivCommentParts('so (unknown) much (Heart)'), [_text('so (unknown) much (Heart)')]);
    });

    test('adjacent codes are separate emoji with nothing between', () {
      expect(pixivCommentParts('(heart)(heart)(star)'), [_emoji('heart'), _emoji('heart'), _emoji('star')]);
    });

    test('parentheses inside ordinary text are left alone', () {
      expect(pixivCommentParts('Nice pose (the second one) ((heart))'), [
        _text('Nice pose (the second one) ('),
        _emoji('heart'),
        _text(')'),
      ]);
      expect(pixivCommentParts('a ( heart ) b (heart'), [_text('a ( heart ) b (heart')]);
    });

    test('empty text has no parts and an emoji-only comment has no text part', () {
      expect(pixivCommentParts(''), isEmpty);
      expect(pixivCommentParts('(love3)'), [_emoji('love3')]);
    });

    test('an emoji part knows its code and image', () {
      final heart = pixivCommentParts('(heart)').single as PixivCommentEmojiPart;
      expect((heart.code, heart.url), ('(heart)', 'https://s.pximg.net/common/images/emoji/501.png'));
    });
  });
}
