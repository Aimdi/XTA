import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/substack/substack_html.dart';
import 'package:xta/plugins/substack/substack_models.dart';

SubstackPost _post({String? audience, String? body}) => SubstackPost.fromJson(
      {
        'id': 1,
        'slug': 'a-post',
        'title': 'A post',
        'audience': audience,
        'body_html': body,
        'canonical_url': 'https://example.substack.com/p/a-post',
      },
      publicationBaseUrl: 'https://example.substack.com',
      publicationName: 'Example',
    );

String _wrap({String? footer, String? link, String? label}) => wrapSubstackHtml(
      title: 'A post',
      body: '<p>The opening paragraphs.</p>',
      background: '#000000',
      foreground: '#ffffff',
      muted: '#888888',
      link: '#1d9bf0',
      isDark: true,
      footer: footer,
      footerLink: link,
      footerLinkLabel: label,
    );

void main() {
  group('what a paid post carries', () {
    test('a paid post can still arrive with the part it gives away', () {
      final post = _post(audience: 'only_paid', body: '<p>The opening paragraphs.</p>');

      expect(post.isPaywalled, isTrue);
      expect(post.bodyHtml, isNotNull,
          reason: 'the free opening is what the reader should be shown, not a lock');
    });

    test('a paid post with nothing attached has nothing to show', () {
      expect(_post(audience: 'only_paid').bodyHtml, isNull);
    });

    test('a free post is not marked paid', () {
      expect(_post(audience: 'everyone', body: '<p>All of it.</p>').isPaywalled, isFalse);
    });
  });

  group('the end-of-preview note', () {
    test('is rendered with the article when there is one', () {
      final html = _wrap(footer: 'The free part ends here.', link: 'https://example.com', label: 'Continue');

      expect(html, contains('<div class="preview-end">'));
      expect(html, contains('The free part ends here.'));
      expect(html, contains('href="https://example.com"'));
      expect(html, contains('Continue'));
    });

    test('a free article gets no note at all', () {
      // The class is styled in every document; what must be absent is an
      // element wearing it.
      expect(_wrap(), isNot(contains('<div class="preview-end">')));
    });

    test('a note with nowhere to continue is still shown', () {
      final html = _wrap(footer: 'The free part ends here.');

      expect(html, contains('The free part ends here.'));
      expect(html, isNot(contains('<a href="null"')));
    });
  });
}
