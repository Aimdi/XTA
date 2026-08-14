import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/ehviewer/eh_client.dart';
import 'package:xta/plugins/ehviewer/eh_models.dart';

void main() {
  late PrefServiceCache prefs;

  setUp(() async {
    prefs = PrefServiceCache(
      cache: {
        optionPluginEhCookies: 'ipb_member_id=1; ipb_pass_hash=abc',
        optionPluginEhUseExhentai: false,
      },
    );
  });

  test('image pages ask EH for 2400px resamples with a desktop UA', () async {
    http.Request? sent;
    final client = EhClient(
      prefs,
      httpClient: MockClient((request) async {
        sent = request;
        return http.Response(
          '<img id="img" src="https://cdn.example/page.webp" />',
          200,
        );
      }),
    );

    final page = await client.imagePage(gid: 9, pageToken: 'tok', page: 1);

    expect(page.imageUrl, 'https://cdn.example/page.webp');
    expect(sent, isNotNull);
    expect(sent!.headers['User-Agent'], EhClient.userAgent);
    expect(sent!.headers['User-Agent'], isNot(contains('Mobile')));
    expect(sent!.headers['Cookie'], contains('uconfig='));
    expect(sent!.headers['Cookie'], contains('xr_2400'));
    expect(sent!.headers['Cookie'], contains('ipb_member_id=1'));
    expect(client.imageHeaders['Cookie'], contains('xr_2400'));
    expect(client.imageHeaders['Referer'], 'https://e-hentai.org/');
  });

  test('search and toplist hit the site query shapes EhViewer uses', () async {
    final urls = <Uri>[];
    final client = EhClient(
      prefs,
      httpClient: MockClient((request) async {
        urls.add(request.url);
        if (request.url.path.contains('/s/')) {
          return http.Response(
            '<img id="img" src="https://cdn.example/page.webp" />',
            200,
          );
        }
        return http.Response(
          '<table><tr><td class="gl1c glcat"><div class="cn ct2">Manga</div></td>'
          '<td class="gl2c"><div class="glthumb"><div><img src="https://ehgt.org/a.webp" /></div>'
          '<div><div>2 pages</div></div></div></td>'
          '<td class="gl3c glname"><a href="https://e-hentai.org/g/1/abc/">'
          '<div class="glink">T</div></a></td></tr></table>',
          200,
        );
      }),
    );

    await client.search('flan', minRating: 3, language: 'japanese');
    await client.toplist(EhToplistPeriod.yesterday);
    await client.watched();
    await client.imagePage(
      gid: 1,
      pageToken: 'tok',
      page: 2,
      reloadKey: 'nl-1',
    );

    expect(urls[0].queryParameters['f_search'], 'flan language:japanese');
    expect(urls[0].queryParameters['f_srdd'], '3');
    expect(urls[1].path, '/toplist.php');
    expect(urls[1].queryParameters['tl'], '11');
    expect(urls[2].path, '/watched');
    expect(urls[3].queryParameters['nl'], 'nl-1');
  });
}
