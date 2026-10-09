import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:xta/utils/shared_links.dart';

void main() {
  test('extracts X links from captions and surrounding punctuation', () {
    for (final host in ['x.com', 'twitter.com', 'mobile.twitter.com', 'www.x.com', 'fxtwitter.com', 'fixupx.com']) {
      expect(
        extractSharedXLink('A post worth reading: (https://$host/reader/status/123?s=20).')?.toString(),
        'https://$host/reader/status/123?s=20',
      );
    }
    expect(extractSharedXLink('https://example.com first\nhttps://x.com/reader')?.host, 'x.com');
  });

  test('rejects unsupported shares, deceptive hosts and non-web URLs', () {
    for (final text in [
      '',
      'a note',
      'content://x.com/reader',
      'javascript:alert(1)',
      'https://x.com.evil.example/reader',
      'https://evil.example/x.com/reader',
      'https://x.com@evil.example/reader',
      'https://evil@x.com/reader',
      'https://x.com:8080/reader',
      'https://notwitter.com/reader',
    ]) {
      expect(extractSharedXLink(text), isNull, reason: text);
    }
  });

  test('resolves a shortened X link without automatic redirects', () async {
    final client = MockClient((request) async {
      expect(request.followRedirects, isFalse);
      return http.Response('', 302, headers: {'location': 'https://x.com/reader/status/123'});
    });
    expect((await resolveSharedXLink('Read https://t.co/short', client: client))?.path, '/reader/status/123');
  });

  test('rejects short links to other websites and redirect loops', () async {
    for (final destination in ['https://example.com/reader', 'file:///secret', 'https://t.co/loop']) {
      var calls = 0;
      final client = MockClient((request) async {
        calls++;
        return http.Response('', 302, headers: {'location': destination});
      });
      expect(await resolveSharedXLink('https://t.co/short', client: client), isNull);
      expect(calls, lessThanOrEqualTo(3));
    }
  });

  test('ordinary X shares do not need a network request to resolve', () async {
    final client = MockClient((_) async => throw StateError('unexpected request'));
    expect((await resolveSharedXLink('https://twitter.com/reader', client: client))?.path, '/reader');
  });

  group('sharedTargetOf', () {
    test('an X link opens on the X screens, even after another link', () {
      final target = sharedTargetOf('About https://example.com/story via https://x.com/reader/status/1');
      expect(target, isA<SharedXTarget>());
      expect((target as SharedXTarget).link.path, '/reader/status/1');
    });

    test('another network\'s link is handed to the link opener, caption and all', () {
      for (final url in [
        'https://bsky.app/profile/reader.bsky.social/post/3k',
        'https://mastodon.social/@reader/111',
        'https://www.threads.net/@reader/post/C1',
        'https://www.reddit.com/r/flutter/comments/abc/title/',
        'https://reader.substack.com/p/a-post',
      ]) {
        final target = sharedTargetOf('Worth a read: $url.');
        expect(target, isA<SharedWebTarget>(), reason: url);
        expect((target as SharedWebTarget).link.toString(), url, reason: url);
      }
    });

    test('a link with credentials in it is not opened', () {
      expect(sharedTargetOf('https://user@example.com/page'), isA<SharedSearchTarget>());
    });

    test('text without a link becomes a search, its whitespace collapsed', () {
      final target = sharedTargetOf('  flutter\n  release   notes ');
      expect((target as SharedSearchTarget).query, 'flutter release notes');
    });

    test('a long share is cut to what X search accepts, without splitting a character', () {
      final query = (sharedTargetOf('😀' * 600) as SharedSearchTarget).query;
      expect(query.runes.length, 500);
      expect(query, '😀' * 500);
    });

    test('an empty share opens nothing', () {
      expect(sharedTargetOf(''), isA<SharedNothing>());
      expect(sharedTargetOf(' \n\t'), isA<SharedNothing>());
    });
  });
}
