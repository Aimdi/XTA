import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/pixiv/pixiv_detail_caption.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_user_screen.dart';

import 'support/pixiv_reader_harness.dart';

PixivIllust _captioned(String html, {String? text}) => PixivIllust(
  id: 120,
  title: 'Sommerfest',
  caption: text ?? pixivCaptionToText(html),
  captionHtml: html,
  type: 'illust',
  thumbnailUrl: 'https://i.pximg.net/c/540x540_70/img-master/img/120_p0.jpg',
  pageCount: 1,
  userId: 42,
  userName: 'Mika',
  userAccount: 'mika',
);

void main() {
  group('pixivCaptionParts', () {
    test('keeps line breaks, bold and links, and reads other tags for their text', () {
      final parts = pixivCaptionParts(
        'Hello<br />see <a href="pixiv://users/42" target="_blank">my page</a> and <strong>bold</strong>'
        '<span> span</span>',
      );
      expect(parts.map((part) => part.text).join(), 'Hello\nsee my page and bold span');
      expect(parts.singleWhere((part) => part.text == 'my page').href, 'pixiv://users/42');
      expect(parts.singleWhere((part) => part.text == 'bold').bold, isTrue);
      expect(parts.where((part) => part.href != null), hasLength(1));
    });

    test('makes Pixiv paths absolute and unwraps jump.php', () {
      expect(pixivCaptionHref('/artworks/5'), 'https://www.pixiv.net/artworks/5');
      expect(pixivCaptionHref('/jump.php?https%3A%2F%2Fexample.com%2Fa'), 'https://example.com/a');
      expect(pixivCaptionHref('/jump.php?url=https%3A%2F%2Fexample.com'), 'https://example.com');
      expect(pixivCaptionHref('javascript:alert(1)'), isNull);
      expect(pixivCaptionHref(''), isNull);
      expect(pixivCaptionHref(null), isNull);
    });

    test('an empty or broken caption is no parts rather than an error', () {
      expect(pixivCaptionParts('  '), isEmpty);
      expect(pixivCaptionParts('<a href=">broken').map((part) => part.text).join(), isEmpty);
      expect(pixivCaptionParts('<b>open').single.bold, isTrue);
    });
  });

  testWidgets('a Pixiv link in the caption opens in XTA, and the caption is selectable', (tester) async {
    await pumpPixiv(
      tester,
      Scaffold(body: PixivDetailCaption(illust: _captioned('Follow <a href="pixiv://users/42">me</a>'))),
    );
    expect(find.byType(SelectionArea), findsOneWidget);

    final rich = tester.widget<RichText>(
      find.descendant(of: find.byType(PixivDetailCaption), matching: find.byType(RichText)).first,
    );
    final link = _spanWithText(rich.text, 'me')!;
    (link.recognizer! as TapGestureRecognizer).onTap!();
    await settlePixiv(tester);

    expect(find.byType(PixivUserScreen), findsOneWidget);
    await disposePixiv(tester);
  });

  testWidgets('a plain-text caption still shows when Pixiv sent no HTML', (tester) async {
    await pumpPixiv(
      tester,
      Scaffold(
        body: PixivDetailCaption(illust: _captioned('', text: 'Only text')),
      ),
    );
    expect(find.text('Only text', findRichText: true), findsOneWidget);
    await disposePixiv(tester);
  });
}

TextSpan? _spanWithText(InlineSpan span, String text) {
  TextSpan? found;
  span.visitChildren((child) {
    if (child is TextSpan && child.text == text) {
      found = child;
      return false;
    }
    return true;
  });
  return found;
}
