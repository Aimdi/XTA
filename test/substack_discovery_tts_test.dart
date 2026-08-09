import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/substack/substack_html.dart';
import 'package:xta/plugins/substack/substack_models.dart';

void main() {
  group('publicationFromDiscoveryJson', () {
    test('maps category / search publication fields', () {
      final pub = publicationFromDiscoveryJson({
        'subdomain': 'bytebytego',
        'name': 'ByteByteGo Newsletter',
        'logo_url': 'https://example.com/logo.png',
        'hero_text': 'Explain complex systems',
        'custom_domain': 'blog.bytebytego.com',
        'base_url': 'https://blog.bytebytego.com',
      });

      expect(pub, isNotNull);
      expect(pub!.subdomain, 'bytebytego');
      expect(pub.name, 'ByteByteGo Newsletter');
      expect(pub.baseUrl, 'https://blog.bytebytego.com');
      expect(pub.description, 'Explain complex systems');
      expect(pub.logoUrl, 'https://example.com/logo.png');
    });

    test('builds a substack.com base when only subdomain is present', () {
      final pub = publicationFromDiscoveryJson({
        'subdomain': 'platformer',
        'name': 'Platformer',
      });

      expect(pub?.baseUrl, 'https://platformer.substack.com');
      expect(pub?.name, 'Platformer');
    });

    test('returns null when nothing identifies the publication', () {
      expect(publicationFromDiscoveryJson({'name': 'Orphan'}), isNull);
    });
  });

  group('buildSubstackSpeakText', () {
    test('speaks title and excerpt when there is no body yet', () {
      final spoken = buildSubstackSpeakText(
        title: 'A paid teaser',
        subtitle: 'The opening line',
        publicationName: 'Example Pub',
      );

      expect(spoken, contains('A paid teaser'));
      expect(spoken, contains('Example Pub'));
      expect(spoken, contains('The opening line'));
    });

    test('prefers plain body over HTML when both are given', () {
      final spoken = buildSubstackSpeakText(
        title: 'Title',
        bodyHtml: '<p>From HTML</p>',
        bodyPlain: 'From the live page',
      );

      expect(spoken, contains('From the live page'));
      expect(spoken, isNot(contains('From HTML')));
    });

    test('strips markup for speech', () {
      final spoken = buildSubstackSpeakText(
        title: 'Title',
        bodyHtml: '<p>Hello <strong>world</strong>.</p>',
      );

      expect(spoken, contains('Hello world.'));
      expect(spoken, isNot(contains('<strong>')));
    });
  });

  group('substackHtmlToPlainText', () {
    test('keeps paragraph breaks', () {
      final plain = substackHtmlToPlainText('<p>One.</p><p>Two.</p>');
      expect(plain, contains('One.'));
      expect(plain, contains('Two.'));
      expect(plain, contains('\n'));
    });
  });
}
