import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:xta/plugins/websources/web_source_client.dart';
import 'package:xta/plugins/websources/web_source_definition.dart';

import 'support/web_source_fixtures.dart';

final _source = sourceFrom(linksSourceJson());

Matcher _failure(WebSourceFailureKind kind) =>
    throwsA(isA<WebSourceFailure>().having((failure) => failure.kind, 'kind', kind));

void main() {
  test('asks a listed site for the page, with GET only and its own name', () async {
    final asked = <http.Request>[];
    final client = WebSourceClient(
      client: MockClient((request) async {
        asked.add(request);
        return http.Response('[]', 200);
      }),
      spacing: Duration.zero,
    );

    expect(await client.page(_source, Uri.parse('https://lobste.rs/page/1.json'), WebSourcePageType.json), '[]');
    expect(asked.single.method, 'GET');
    expect(asked.single.headers['User-Agent'], webSourceUserAgent);
    expect(asked.single.headers['Accept'], 'application/json');
    expect(asked.single.headers.containsKey('cookie'), isFalse);
  });

  test('refuses an unlisted site and plain HTTP without asking', () async {
    var asked = 0;
    final client = WebSourceClient(
      client: MockClient((_) async {
        asked++;
        return http.Response('', 200);
      }),
      spacing: Duration.zero,
    );

    await expectLater(
      client.page(_source, Uri.parse('https://elsewhere.org/'), WebSourcePageType.json),
      _failure(WebSourceFailureKind.blocked),
    );
    await expectLater(
      client.page(_source, Uri.parse('http://lobste.rs/'), WebSourcePageType.json),
      _failure(WebSourceFailureKind.blocked),
    );
    expect(asked, 0);
  });

  test('follows a redirect on its sites, and not one that leaves them', () async {
    final client = WebSourceClient(
      client: MockClient(
        (request) async => switch (request.url.path) {
          '/old' => http.Response('', 301, headers: {'location': '/new'}),
          '/new' => http.Response('moved', 200),
          _ => http.Response('', 302, headers: {'location': 'https://elsewhere.org/'}),
        },
      ),
      spacing: Duration.zero,
    );

    expect(await client.page(_source, Uri.parse('https://lobste.rs/old'), WebSourcePageType.json), 'moved');
    await expectLater(
      client.page(_source, Uri.parse('https://lobste.rs/away'), WebSourcePageType.json),
      _failure(WebSourceFailureKind.blocked),
    );
  });

  test('stops after three redirects', () async {
    var asked = 0;
    final client = WebSourceClient(
      client: MockClient((request) async {
        asked++;
        return http.Response('', 302, headers: {'location': '/loop$asked'});
      }),
      spacing: Duration.zero,
    );

    await expectLater(
      client.page(_source, Uri.parse('https://lobste.rs/loop'), WebSourcePageType.json),
      _failure(WebSourceFailureKind.blocked),
    );
    expect(asked, 4);
  });

  test('reports another status, a page too large, and no answer', () async {
    WebSourceClient answering(Future<http.Response> Function(http.Request) handler) =>
        WebSourceClient(client: MockClient(handler), spacing: Duration.zero);
    final url = Uri.parse('https://lobste.rs/x');

    await expectLater(
      answering((_) async => http.Response('', 503)).page(_source, url, WebSourcePageType.json),
      throwsA(isA<WebSourceFailure>().having((failure) => failure.status, 'status', 503)),
    );
    await expectLater(
      answering(
        (_) async => http.Response('x' * (webSourceMaxPageBytes + 1), 200),
      ).page(_source, url, WebSourcePageType.json),
      _failure(WebSourceFailureKind.tooLarge),
    );
    await expectLater(
      answering((_) async => throw http.ClientException('offline')).page(_source, url, WebSourcePageType.json),
      _failure(WebSourceFailureKind.unreachable),
    );
  });

  test('waits between two requests of the same source', () async {
    final times = <DateTime>[];
    final client = WebSourceClient(
      client: MockClient((_) async {
        times.add(DateTime.now());
        return http.Response('[]', 200);
      }),
      spacing: const Duration(milliseconds: 120),
    );

    await Future.wait([
      client.page(_source, Uri.parse('https://lobste.rs/1'), WebSourcePageType.json),
      client.page(_source, Uri.parse('https://lobste.rs/2'), WebSourcePageType.json),
    ]);

    expect(times[1].difference(times[0]), greaterThanOrEqualTo(const Duration(milliseconds: 110)));
  });

  test('reads a source file from any HTTPS address, and not over HTTP', () async {
    final client = WebSourceClient(client: MockClient((_) async => http.Response('{}', 200)), spacing: Duration.zero);

    expect(await client.file(Uri.parse('https://example.org/source.json')), '{}');
    await expectLater(client.file(Uri.parse('http://example.org/source.json')), _failure(WebSourceFailureKind.blocked));
  });
}
