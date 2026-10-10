import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_saucenao.dart';

/// Two Pixiv matches (one linked through member_illust.php, one through
/// artworks/), a repeat of the first, and a match from another site.
const _html = '''
<html><body><div id="middle">
<div class="result"><table class="resulttable"><tr>
  <td class="resulttableimage"><div class="resultimage"><a href="https://www.pixiv.net/member_illust.php?mode=medium&amp;illust_id=81234567"><img src="https://img.saucenao.com/thumb1.jpg"></a></div></td>
  <td class="resulttablecontent">
    <div class="resultmatchinfo"><div class="resultsimilarityinfo">94.57%</div></div>
    <div class="resultcontent">
      <div class="resulttitle"><strong>夏の空</strong></div>
      <div class="resultcontentcolumn"><strong>Pixiv ID: </strong><a href="https://www.pixiv.net/member_illust.php?mode=medium&amp;illust_id=81234567" class="linkify">81234567</a><br/>
      <strong>Member: </strong><a href="https://www.pixiv.net/member.php?id=123" class="linkify">Hoshino</a></div>
    </div>
  </td></tr></table></div>
<div class="result"><table class="resulttable"><tr><td class="resulttablecontent">
  <div class="resultsimilarityinfo">71.2%</div>
  <div class="resulttitle">Night walk</div>
  <div class="resultcontentcolumn"><a href="https://www.pixiv.net/en/artworks/99887766">99887766</a> <a href="https://www.pixiv.net/users/456">Tsuki</a></div>
</td></tr></table></div>
<div class="result"><div class="resultsimilarityinfo">60.0%</div>
  <a href="https://www.pixiv.net/artworks/81234567">again</a></div>
<div class="result"><div class="resultsimilarityinfo">88.0%</div>
  <a href="https://danbooru.donmai.us/post/show/123?illust_id=5">elsewhere</a></div>
</div></body></html>
''';

void main() {
  group('reading SauceNAO answers', () {
    test('the HTML page gives each Pixiv work once, with similarity, title and artist', () {
      final matches = parsePixivSauceNao(_html);
      expect([for (final match in matches) match.illustId], [81234567, 99887766]);
      expect(matches.first.similarity, closeTo(94.57, 0.001));
      expect(matches.first.title, '夏の空');
      expect(matches.first.author, 'Hoshino');
      expect((matches.last.similarity, matches.last.title, matches.last.author), (71.2, 'Night walk', 'Tsuki'));
    });

    test('a page without result blocks still yields the Pixiv links on it', () {
      const bare =
          '<p><a href="https://www.pixiv.net/artworks/42">x</a><a href="https://example.com/artworks/43">y</a></p>';
      final matches = parsePixivSauceNao(bare);
      expect([for (final match in matches) match.illustId], [42]);
      expect(matches.single.similarity, isNull);
    });

    test('a JSON answer reads pixiv_id or an ext_url, and skips other sites', () {
      final body = jsonEncode({
        'header': {'status': 0},
        'results': [
          {
            'header': {'similarity': '93.10'},
            'data': {'pixiv_id': 555, 'title': 'Snow', 'member_name': 'Yuki'},
          },
          {
            'header': {'similarity': 80},
            'data': {
              'ext_urls': [
                'https://danbooru.donmai.us/posts/1',
                'https://www.pixiv.net/member_illust.php?illust_id=777',
              ],
              'author_name': 'Kaze',
            },
          },
          {
            'header': {'similarity': '70'},
            'data': {
              'ext_urls': ['https://danbooru.donmai.us/posts/2'],
            },
          },
          {'header': null, 'data': 'reshaped'},
        ],
      });
      final matches = parsePixivSauceNao(body);
      expect([for (final match in matches) match.illustId], [555, 777]);
      expect((matches.first.similarity, matches.first.title, matches.first.author), (93.1, 'Snow', 'Yuki'));
      expect(matches.last.author, 'Kaze');
    });

    test('broken JSON and empty pages give no matches', () {
      expect(parsePixivSauceNao('{"results": ['), isEmpty);
      expect(parsePixivSauceNao(''), isEmpty);
    });

    test('ids come from artworks/<id> or illust_id= on Pixiv only', () {
      expect(pixivIdFromSauceLink('https://www.pixiv.net/artworks/123'), 123);
      expect(pixivIdFromSauceLink('https://www.pixiv.net/en/artworks/124?foo=1'), 124);
      expect(pixivIdFromSauceLink('https://www.pixiv.net/member_illust.php?mode=medium&illust_id=125'), 125);
      expect(pixivIdFromSauceLink('https://pixiv.net/i/126'), isNull);
      expect(pixivIdFromSauceLink('https://evil.example/artworks/127'), isNull);
      expect(pixivIdFromSauceLink('https://www.pixiv.net/artworks/abc'), isNull);
      expect(pixivIdFromSauceLink('not a link'), isNull);
    });
  });

  group('uploading', () {
    test('posts the image as a multipart file to saucenao.com and parses the page', () async {
      late http.BaseRequest sent;
      late String body;
      final api = PixivSauceNaoApi(
        MockClient((request) async {
          sent = request;
          body = latin1.decode(request.bodyBytes);
          return http.Response(_html, 200, headers: {'content-type': 'text/html; charset=utf-8'});
        }),
      );

      final matches = await api.search(Uint8List.fromList([137, 80, 78, 71]));
      expect(sent.method, 'POST');
      expect(sent.url, Uri.parse('https://saucenao.com/search.php'));
      expect(sent.headers['content-type'], startsWith('multipart/form-data'));
      expect(sent.headers.containsKey('Authorization'), isFalse);
      expect(body, contains('name="file"; filename="image.png"'));
      expect(matches, hasLength(2));
    });

    test('a refusal becomes a Pixiv error the sheet can word', () async {
      PixivSauceNaoApi answering(int status) => PixivSauceNaoApi(MockClient((_) async => http.Response('', status)));
      await expectLater(
        answering(429).search(Uint8List(4)),
        throwsA(isA<PixivException>().having((e) => e.kind, 'kind', PixivErrorKind.rateLimited)),
      );
      await expectLater(
        answering(500).search(Uint8List(4)),
        throwsA(isA<PixivException>().having((e) => e.kind, 'kind', PixivErrorKind.badResponse)),
      );
      final offline = PixivSauceNaoApi(MockClient((_) async => throw http.ClientException('offline')));
      await expectLater(
        offline.search(Uint8List(4)),
        throwsA(isA<PixivException>().having((e) => e.kind, 'kind', PixivErrorKind.network)),
      );
    });
  });
}
