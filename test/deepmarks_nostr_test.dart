import 'dart:convert';

import 'package:bip340/bip340.dart' as bip340;
import 'package:crypto/crypto.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/deepmarks/nostr_event.dart';

// The key pair from the NIP-19 specification, so the bech32 decoding and the
// public-key derivation are checked against something outside this codebase.
const _nsec = 'nsec1vl029mgpspedva04g90vltkh6fvh240zqtv9k0t9af8935ke9laqsnlfe5';
const _secretHex = '67dea2ed018072d675f5415ecfaed7d2597555e202d85b3d65ea4e58d2d92ffa';
const _publicHex = '7e7e9c42a91bfef19fa929e5fda1b72e0ebc1a4c1141673e2794234d86addf4e';

void main() {
  group('normaliseNostrSecretKey', () {
    test('decodes the NIP-19 nsec test vector', () {
      expect(normaliseNostrSecretKey(_nsec), _secretHex);
    });

    test('accepts bare hex, in either case, with surrounding space', () {
      expect(normaliseNostrSecretKey('  ${_secretHex.toUpperCase()}  '), _secretHex);
    });

    test('rejects what is not a key, with a reason', () {
      for (final input in ['', 'hunter2', 'npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z', 'nsec1nonsense']) {
        expect(() => normaliseNostrSecretKey(input), throwsA(isA<NostrKeyException>()), reason: input);
      }
    });
  });

  group('nostrPublicKey', () {
    test('derives the public key from the NIP-19 vector', () {
      expect(nostrPublicKey(_secretHex), _publicHex);
    });
  });

  group('nostrEventId', () {
    test('is the sha256 of the canonical NIP-01 serialisation', () {
      final tags = [
        ['d', 'https://example.com/article'],
        ['title', 'An Article'],
      ];
      final expected = sha256
          .convert(utf8.encode(jsonEncode([0, _publicHex, 1700000000, kindWebBookmark, tags, ''])))
          .toString();

      expect(
        nostrEventId(pubkey: _publicHex, createdAt: 1700000000, kind: kindWebBookmark, tags: tags, content: ''),
        expected,
      );
    });
  });

  group('webBookmarkTags', () {
    test('puts the URL in the d tag, as NIP-B0 requires', () {
      final tags = webBookmarkTags(url: 'https://example.com/a');

      expect(tags.first, ['d', 'https://example.com/a']);
    });

    test('adds title, description and lower-cased topics', () {
      final tags = webBookmarkTags(
        url: 'https://example.com/a',
        title: '  An Article  ',
        description: 'A summary',
        topics: ['Reading', '  ', 'Bitcoin'],
      );

      expect(tags, [
        ['d', 'https://example.com/a'],
        ['title', 'An Article'],
        ['description', 'A summary'],
        ['t', 'reading'],
        ['t', 'bitcoin'],
      ]);
    });

    test('leaves out empty optional tags rather than sending blanks', () {
      final tags = webBookmarkTags(url: 'https://example.com/a', title: '   ', description: '');

      expect(tags, [
        ['d', 'https://example.com/a'],
      ]);
    });
  });

  group('signWebBookmark', () {
    test('produces an event whose signature verifies against its own id', () {
      final event = signWebBookmark(
        secretKeyHex: _secretHex,
        url: 'https://example.com/article',
        title: 'An Article',
        now: DateTime.utc(2026, 7, 25, 0, 0, 0),
      );

      expect(event.kind, kindWebBookmark);
      expect(event.pubkey, _publicHex);
      expect(event.createdAt, DateTime.utc(2026, 7, 25).millisecondsSinceEpoch ~/ 1000);
      expect(bip340.verify(event.pubkey, event.id, event.sig), isTrue);
    });

    test('the id matches the event it is sent with, so the server accepts it', () {
      final event = signWebBookmark(
        secretKeyHex: _secretHex,
        url: 'https://example.com/article',
        now: DateTime.utc(2026, 7, 25),
      );

      final recomputed = nostrEventId(
        pubkey: event.pubkey,
        createdAt: event.createdAt,
        kind: event.kind,
        tags: event.tags,
        content: event.content,
      );

      expect(event.id, recomputed);
    });

    test('serialises to the shape the API documents', () {
      final event = signWebBookmark(secretKeyHex: _secretHex, url: 'https://example.com/a');
      final json = event.toJson();

      expect(json.keys.toSet(), {'id', 'pubkey', 'created_at', 'kind', 'tags', 'content', 'sig'});
      expect(json['kind'], 39701);
      expect(json['content'], '');
      expect((json['tags'] as List).first, ['d', 'https://example.com/a']);
    });

    test('two saves of the same URL are separately valid events', () {
      final first = signWebBookmark(secretKeyHex: _secretHex, url: 'https://example.com/a');
      final second = signWebBookmark(secretKeyHex: _secretHex, url: 'https://example.com/a');

      expect(bip340.verify(first.pubkey, first.id, first.sig), isTrue);
      expect(bip340.verify(second.pubkey, second.id, second.sig), isTrue);
    });
  });
}
