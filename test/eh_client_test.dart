import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/ehviewer/eh_client.dart';

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
}
