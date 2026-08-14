import 'dart:convert';

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

  group('parseSubstackRecommendations', () {
    test('reads recommendedPublication and blurb from _preloads HTML', () {
      final html = _preloadsHtml({
        'recommendations': [
          {
            'description': 'Why I read this',
            'recommendedPublication': {
              'name': 'The Power Law',
              'subdomain': 'peterwildeford',
              'hero_text': 'Forecasts',
              'logo_url': 'https://example.org/l.png',
              'custom_domain': 'blog.peterwildeford.com',
            },
          },
          {
            'recommendedPublication': {'name': 'Missing handle'},
          },
        ],
      });

      final recs = parseSubstackRecommendationsHtml(html);
      expect(recs, hasLength(1));
      expect(recs.single.publication.name, 'The Power Law');
      expect(recs.single.publication.subdomain, 'peterwildeford');
      expect(
        recs.single.publication.baseUrl,
        'https://blog.peterwildeford.com',
      );
      expect(recs.single.blurb, 'Why I read this');
    });

    test('returns empty on missing or reshaped HTML', () {
      expect(parseSubstackRecommendationsHtml(''), isEmpty);
      expect(parseSubstackRecommendationsHtml('<html></html>'), isEmpty);
      expect(parseSubstackRecommendationsJson(null), isEmpty);
      expect(parseSubstackRecommendationsJson('nope'), isEmpty);
    });
  });

  group('mergeSubstackSimilar', () {
    const seed = SubstackPublication(
      subdomain: 'platformer',
      baseUrl: 'https://platformer.substack.com',
      name: 'Platformer',
    );

    test('drops the seed, prefers recs, then pads with search', () {
      final merged = mergeSubstackSimilar(
        seed: seed,
        recommended: [
          const SubstackRecommendation(
            publication: SubstackPublication(
              subdomain: 'bigtechnology',
              baseUrl: 'https://bigtechnology.substack.com',
              name: 'Big Technology',
            ),
            blurb: 'Casey',
          ),
        ],
        searched: [
          seed,
          const SubstackPublication(
            subdomain: 'bigtechnology',
            baseUrl: 'https://bigtechnology.substack.com',
            name: 'Big Technology',
          ),
          const SubstackPublication(
            subdomain: 'user-mag',
            baseUrl: 'https://www.usermag.co',
            name: 'User Mag',
          ),
        ],
      );

      expect(merged.map((e) => e.publication.id), [
        'bigtechnology',
        'user-mag',
      ]);
      expect(merged.first.blurb, 'Casey');
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

String _preloadsHtml(Map<String, dynamic> preloads) {
  return '<html><script>window._preloads = JSON.parse(${jsonEncode(jsonEncode(preloads))});</script></html>';
}
