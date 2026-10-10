import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:xta/utils/shared_links.dart';
import 'package:xta/utils/urls.dart';

void main() {
  test('extracts X links from captions and surrounding punctuation', () {
    for (final host in ['x.com', 'twitter.com', 'mobile.twitter.com', 'www.x.com', 'fxtwitter.com', 'fixupx.com']) {
      expect(
        extractSharedLink('A post worth reading: (https://$host/reader/status/123?s=20).')?.toString(),
        'https://$host/reader/status/123?s=20',
      );
    }
    expect(extractSharedLink('https://example.com first\nhttps://x.com/reader')?.host, 'x.com');
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
      expect(extractSharedLink(text), isNull, reason: text);
    }
  });

  test('resolves a shortened X link without automatic redirects', () async {
    final client = MockClient((request) async {
      expect(request.followRedirects, isFalse);
      return http.Response('', 302, headers: {'location': 'https://x.com/reader/status/123'});
    });
    expect((await resolveSharedLink('Read https://t.co/short', client: client))?.path, '/reader/status/123');
  });

  test('rejects short links to other websites and redirect loops', () async {
    for (final destination in ['https://example.com/reader', 'file:///secret', 'https://t.co/loop']) {
      var calls = 0;
      final client = MockClient((request) async {
        calls++;
        return http.Response('', 302, headers: {'location': destination});
      });
      expect(await resolveSharedLink('https://t.co/short', client: client), isNull);
      expect(calls, lessThanOrEqualTo(3));
    }
  });

  test('takes Pixiv links only while the Pixiv plugin is on', () {
    const share = 'Look at this https://www.pixiv.net/artworks/123 !';
    expect(extractSharedLink(share), isNull);
    expect(extractSharedLink(share, pixiv: true)?.path, '/artworks/123');
    expect(extractSharedLink('https://pixiv.me/mika', pixiv: true)?.host, 'pixiv.me');
    expect(extractSharedLink('https://www.pixiv.net.evil.example/artworks/1', pixiv: true), isNull);
    expect(extractSharedLink('https://evil@www.pixiv.net/artworks/1', pixiv: true), isNull);
  });

  test('takes pixivision links with the Pixiv plugin, and never hands one to X', () {
    const share = 'Autumn cats https://www.pixivision.net/en/a/9876';
    expect(extractSharedLink(share), isNull);
    expect(extractSharedLink(share, pixiv: true)?.path, '/en/a/9876');
    expect(extractSharedLink('https://pixivision.net/ja/a/1', pixiv: true)?.host, 'pixivision.net');
    expect(extractSharedLink('https://www.pixivision.net.evil.example/en/a/1', pixiv: true), isNull);
    for (final url in ['https://www.pixivision.net/en/', 'https://www.pixivision.net/en/c/illustration']) {
      expect(readsAsXLink(Uri.parse(url)), isFalse, reason: url);
    }
  });

  test('a Pixiv page no plugin opened goes to the browser, never to X as a profile', () async {
    for (final url in [
      'https://www.pixiv.net/ranking.php',
      'https://www.pixiv.net/en/',
      'https://pixiv.net/discovery',
      'https://www.pixiv.net/bookmark_new_illust.php',
      'https://www.pixiv.net/artworks/123',
      'https://pixiv.me/mika',
    ]) {
      expect(readsAsXLink(Uri.parse(url)), isFalse, reason: url);
    }
    expect(
      await parseUri(Uri.parse('https://www.pixiv.net/en/')),
      isA<ProfileUriInfo>(),
      reason: 'why the gate exists',
    );
    for (final url in ['https://x.com/reader', 'https://t.co/short', 'https://fixupx.com/reader/status/1']) {
      expect(readsAsXLink(Uri.parse(url)), isTrue, reason: url);
    }
  });

  test('a share that is only a number is a Pixiv work id', () {
    expect(sharedPixivId(' 123456 '), '123456');
    expect(sharedPixivId('12 34'), isNull);
    expect(sharedPixivId('id 5'), isNull);
    expect(sharedPixivId(''), isNull);
  });

  test('Pixiv shares need no request to resolve; pixiv.me waits for where it opens', () async {
    final client = MockClient((_) async => throw StateError('unexpected request'));
    expect((await resolveSharedLink('https://pixiv.me/mika', pixiv: true, client: client))?.host, 'pixiv.me');
    expect(await resolveSharedLink('https://pixiv.me/mika', client: client), isNull);
  });

  test('ordinary X shares do not need a network request to resolve', () async {
    final client = MockClient((_) async => throw StateError('unexpected request'));
    expect((await resolveSharedLink('https://twitter.com/reader', client: client))?.path, '/reader');
  });
}
