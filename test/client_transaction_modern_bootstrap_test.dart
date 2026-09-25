import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:xta/client/http_client.dart';
import 'package:xta/client/x_client_transaction_id/client_transaction.dart';

const _root = 'https://abs.twimg.com/x-web/client-web';
const _signer = '$_root/sign.o-a1b2c3.js';
const _indices = 'f(a[1],16);f(a[2],16);f(a[3],16);f(a[4],16);';
const _cookie = 'auth_token=test-session; ct0=test-csrf';
final _key = List.generate(48, (index) => index);

String _shell(String links) {
  final rows = List.filled(16, '20 40 60 80 100 120 140 160 180 200 220').join('C');
  return '<html><head><meta name="twitter-site-verification" content="${base64Encode(_key)}">$links</head>'
      '<body>${List.generate(4, (i) => '<svg id="loading-x-anim-$i"><g><path/><path d="M0 0C0 0C$rows"/></g></svg>').join()}</body></html>';
}

void _valid(ClientTransaction transaction) {
  final value = transaction.generateTransactionId('GET', '/i/api/graphql/test/HomeTimeline');
  final bytes = base64.decode(base64.normalize(value));
  expect(bytes.skip(1).take(_key.length).map((byte) => byte ^ bytes.first), _key);
  expect(bytes.length, _key.length + 22);
}

void main() {
  final requests = <http.Request>[];
  void respond(FutureOr<http.Response> Function(http.Request) handler) {
    xHttpClient = MockClient((request) async {
      requests.add(request);
      return handler(request);
    });
  }

  setUp(requests.clear);
  tearDown(() {
    xHttpClient.close();
    xHttpClient = null;
  });

  test('modern modulepreload locates a relative sign.o import and derives an ID', () async {
    respond((request) {
      if (request.url.host == 'x.com') {
        return http.Response(_shell('<link rel="modulepreload" href="$_root/app-a.js">'), 200);
      }
      return http.Response(
        request.url.toString() == _signer ? _indices : 'const sign = () => import("./sign.o-a1b2c3.js");',
        200,
      );
    });

    _valid(await ClientTransaction.initialize());

    expect(requests.map((request) => request.url.toString()), ['https://x.com/home', '$_root/app-a.js', _signer]);
  });

  test('a directly linked modern signer needs no entry bundle scan', () async {
    respond(
      (request) =>
          http.Response(request.url.host == 'x.com' ? _shell('<script src="$_signer"></script>') : _indices, 200),
    );

    _valid(await ClientTransaction.initialize());
    expect(requests.length, 2);
  });

  test('an entry script after many preloads is inspected before the preload limit', () async {
    respond((request) {
      if (request.url.host == 'x.com') {
        return http.Response(
          _shell(
            '${List.generate(20, (i) => '<link rel="modulepreload" href="$_root/preload-$i.js">').join()}'
            '<script type="module" src="$_root/entry-client-a.js"></script>',
          ),
          200,
        );
      }
      if (request.url.toString() == _signer) return http.Response(_indices, 200);
      return http.Response(
        request.url.path.contains('entry-client') ? 'import("./sign.o-a1b2c3.js")' : 'no signer',
        200,
      );
    });
    _valid(await ClientTransaction.initialize());
    expect(requests[1].url.toString(), '$_root/entry-client-a.js');
  });

  test('a signed-in bootstrap uses only its cookie and preserves it on same-origin redirects', () async {
    respond((request) {
      expect(request.followRedirects, isFalse);
      expect(request.headers, isNot(contains('authorization')));
      if (request.url.host == 'x.com') {
        expect(request.headers['cookie'], _cookie);
        if (request.url.path == '/home') return http.Response('', 302, headers: {'location': '/signed-in'});
        return http.Response(_shell('<script src="$_signer"></script>'), 200);
      }
      expect(request.headers, isNot(contains('cookie')));
      return http.Response(_indices, 200);
    });

    _valid(await ClientTransaction.initialize(cookie: _cookie));
    expect(requests.map((request) => request.url.path), ['/home', '/signed-in', '/x-web/client-web/sign.o-a1b2c3.js']);
  });

  for (final target in [
    'https://attacker.invalid/home',
    'http://x.com/home',
    'https://x.com:444/home',
    'https://user@x.com/home',
  ]) {
    test('bootstrap refuses unsafe redirect $target without forwarding cookies', () async {
      respond((request) => http.Response('', 302, headers: {'location': target}));

      await expectLater(ClientTransaction.initialize(cookie: _cookie), throwsA(isA<HttpException>()));
      expect(requests.length, 1);
      expect(requests.single.followRedirects, isFalse);
    });
  }

  test('same-origin redirect loops stop after three redirects', () async {
    respond((request) => http.Response('', 302, headers: {'location': '/home'}));
    await expectLater(ClientTransaction.initialize(cookie: _cookie), throwsA(isA<HttpException>()));
    expect(requests.length, 4);
  });

  test('legacy search fallback retains the account cookie only on x.com', () async {
    const legacy = 'https://abs.twimg.com/responsive-web/client-web/ondemand.s.olda.js';
    respond((request) {
      if (request.url.host == 'x.com') {
        expect(request.headers['cookie'], _cookie);
        return http.Response(
          request.url.path == '/home' ? '<html>signed out shell</html>' : _shell('<script src="$legacy"></script>'),
          200,
        );
      }
      expect(request.headers, isNot(contains('cookie')));
      return http.Response(_indices, 200);
    });
    _valid(await ClientTransaction.initialize(cookie: _cookie));
    expect(requests.map((request) => request.url.path), [
      '/home',
      '/search',
      '/responsive-web/client-web/ondemand.s.olda.js',
    ]);
  });

  test('untrusted links and imports never become asset requests', () async {
    respond((request) {
      if (request.url.host == 'x.com') {
        return http.Response(
          _shell('''
          <script src="https://attacker.invalid/x-web/app.js"></script>
          <script src="http://abs.twimg.com/x-web/app.js"></script>
          <script src="https://abs.twimg.com/x-web/app.js?next=bad"></script>
          <script src="https://abs.twimg.com/x-web/%2e%2e/private.js"></script>
          <link rel="modulepreload" href="$_root/app-a.js">
        '''),
          200,
        );
      }
      return http.Response('''
        import("https://attacker.invalid/x-web/sign.o-bad.js");
        import("http://abs.twimg.com/x-web/sign.o-bad.js");
        import("https://abs.twimg.com/elsewhere/sign.o-bad.js");
        import("./design.o-bad.js");
      ''', 200);
    });

    await expectLater(ClientTransaction.initialize(cookie: _cookie), throwsA(isA<FormatException>()));
    expect(requests.map((request) => request.url.toString()), [
      'https://x.com/home',
      '$_root/app-a.js',
      'https://x.com/search?q=AI&f=live',
    ]);
  });

  test('a CDN redirect is not followed and never receives a cookie', () async {
    respond((request) {
      if (request.url.host == 'x.com') return http.Response(_shell('<script src="$_signer"></script>'), 200);
      expect(request.headers, isNot(contains('cookie')));
      expect(request.followRedirects, isFalse);
      return http.Response('', 302, headers: {'location': 'https://attacker.invalid/sign.o-bad.js'});
    });

    await expectLater(ClientTransaction.initialize(cookie: _cookie), throwsA(isA<HttpException>()));
    expect(requests.length, 2);
  });

  test('asset scan deduplicates links and reserves one of sixteen reads for the signer', () async {
    respond((request) {
      if (request.url.host == 'x.com') {
        return http.Response(
          _shell(
            List.generate(
              30,
              (i) => '<script src="$_root/app-$i.js"></script><link rel="modulepreload" href="$_root/app-$i.js">',
            ).join(),
          ),
          200,
        );
      }
      return http.Response('no signing import', 200);
    });

    await expectLater(ClientTransaction.initialize(), throwsA(isA<FormatException>()));
    final assets = requests.where((request) => request.url.host == 'abs.twimg.com').toList();
    expect(assets.length, 15);
    expect(assets.map((request) => request.url).toSet().length, assets.length);
  });

  test('scan runs four at a time and stops starting entries after finding a signer', () async {
    final pending = <String, Completer<http.Response>>{};
    respond((request) {
      if (request.url.host == 'x.com') {
        return http.Response(_shell(List.generate(20, (i) => '<script src="$_root/app-$i.js"></script>').join()), 200);
      }
      if (request.url.toString() == _signer) return http.Response(_indices, 200);
      return (pending[request.url.path] = Completer<http.Response>()).future;
    });
    final initialized = ClientTransaction.initialize();
    while (pending.length < 4) {
      await Future<void>.delayed(Duration.zero);
    }
    expect(pending.length, 4);
    pending.values.first.complete(http.Response('import("./sign.o-a1b2c3.js")', 200));
    _valid(await initialized);
    for (final response in pending.values.skip(1)) {
      response.complete(http.Response('no signer', 200));
    }
    await Future<void>.delayed(Duration.zero);
    expect(pending.length, 4);
    expect(requests.length, 6);
  });

  testWidgets('a stalled modern asset scan shares the original initialization deadline', (tester) async {
    final pending = Completer<http.Response>();
    respond(
      (request) => request.url.host == 'x.com'
          ? http.Response(_shell('<script src="$_root/app-a.js"></script>'), 200)
          : pending.future,
    );
    final initialized = expectLater(ClientTransaction.initialize(), throwsA(isA<TimeoutException>()));
    await tester.pump();
    await tester.pump(const Duration(seconds: 13));
    await initialized;
    expect(requests.length, 2);
    pending.complete(http.Response('import("./sign.o-a1b2c3.js")', 200));
    await tester.pump();
    expect(requests.length, 2);
  });
}
