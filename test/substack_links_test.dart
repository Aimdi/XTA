import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/substack/substack_links.dart';

void main() {
  group('parseSubstackPostLink', () {
    test('reads a post on a publication subdomain', () {
      final link = parseSubstackPostLink('https://astralcodexten.substack.com/p/the-media-very-rarely-lies')!;

      expect(link.publicationBase, Uri.parse('https://astralcodexten.substack.com'));
      expect(link.slug, 'the-media-very-rarely-lies');
    });

    test('reads the share host Substack uses in its emails', () {
      final link = parseSubstackPostLink('https://open.substack.com/pub/platformer/p/the-deal?utm_source=share')!;

      expect(link.publicationBase, Uri.parse('https://platformer.substack.com'));
      expect(link.slug, 'the-deal');
    });

    test('keeps the post when the link points at its comments', () {
      final link = parseSubstackPostLink('https://platformer.substack.com/p/the-deal/comments')!;

      expect(link.slug, 'the-deal');
    });

    test('ignores query strings and fragments', () {
      final link = parseSubstackPostLink('https://foo.substack.com/p/bar?utm_medium=email#footnote-1')!;

      expect(link.slug, 'bar');
    });

    test('a custom domain is only claimed when the reader follows it', () {
      const url = 'https://www.thefp.com/p/some-essay';

      expect(parseSubstackPostLink(url), isNull);

      final link = parseSubstackPostLink(url, knownBaseUrls: ['https://www.thefp.com'])!;
      expect(link.publicationBase, Uri.parse('https://www.thefp.com'));
      expect(link.slug, 'some-essay');
    });

    test('publication pages are not posts', () {
      for (final url in [
        'https://foo.substack.com',
        'https://foo.substack.com/',
        'https://foo.substack.com/archive',
        'https://foo.substack.com/about',
        'https://foo.substack.com/subscribe',
        'https://foo.substack.com/notes',
        'https://foo.substack.com/p',
      ]) {
        expect(parseSubstackPostLink(url), isNull, reason: url);
      }
    });

    test('Substack\'s own pages are not posts', () {
      for (final url in [
        'https://substack.com/@someone',
        'https://substack.com/@someone/note/c-12345',
        'https://www.substack.com/pricing',
        'https://open.substack.com/pub/platformer',
      ]) {
        expect(parseSubstackPostLink(url), isNull, reason: url);
      }
    });

    test('non-Substack links are left alone', () {
      for (final url in [
        'https://x.com/jack/status/20',
        'https://example.com/p/looks-like-substack',
        'https://notsubstack.com/p/nope',
        'mailto:someone@substack.com',
        'not a url at all',
        '',
      ]) {
        expect(parseSubstackPostLink(url), isNull, reason: url);
      }
    });

    test('a lookalike host cannot impersonate a publication', () {
      // Ends with "substack.com" as a string but is a different domain.
      expect(parseSubstackPostLink('https://evilsubstack.com/p/post'), isNull);
      expect(parseSubstackPostLink('https://foo.substack.com.evil.tld/p/post'), isNull);
    });
  });

  group('substackPostStub', () {
    test('carries what the reader needs to fetch the real post', () {
      final link = parseSubstackPostLink('https://platformer.substack.com/p/the-deal')!;
      final stub = substackPostStub(link);

      expect(stub.slug, 'the-deal');
      expect(stub.publicationBaseUrl, 'https://platformer.substack.com');
      expect(stub.publication.baseUrl, 'https://platformer.substack.com');
      expect(stub.canonicalUrl, 'https://platformer.substack.com/p/the-deal');
      expect(stub.publicationName, 'platformer');
      expect(stub.title, isNotEmpty, reason: 'the reader must not open with a blank title bar');
    });

    test('prefers the followed publication\'s own name when known', () {
      final link = parseSubstackPostLink('https://platformer.substack.com/p/the-deal')!;

      expect(substackPostStub(link, publicationName: 'Platformer').publicationName, 'Platformer');
    });
  });
}
