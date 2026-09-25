import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:logging/logging.dart';
import 'package:xta/client/client_regular_account.dart';
import 'package:xta/client/headers.dart';
import 'package:xta/client/http_client.dart';
import 'package:xta/client/x_client_transaction_id/client_transaction.dart';

const _bundle = 'https://abs.twimg.com/responsive-web/client-web/ondemand.s.abcdef1234567890a.js';
const _runtime = 'e=>({1:"other",59924:"ondemand.s"}[e]||e)+"."+{1:"1234567",59924:"abcdef1234567890"}[e]+"a.js"';
const _indices = 'f(a[1],16);f(a[2],16);f(a[3],16);f(a[4],16);';
const _loggedOut =
    '<html data-app-env="prod"><script src="https://abs.twimg.com/x-web/entry-client-logged-out-abc.js"></script></html>';
final _keyBytes = List.generate(48, (index) => index);

String _appShell({String runtime = _runtime, bool key = true, int frames = 4, String? script}) {
  final rows = List.filled(16, '20 40 60 80 100 120 140 160 180 200 220').join('C');
  return '''<html><head>
    ${key ? '<meta name="twitter-site-verification" content="${base64Encode(_keyBytes)}">' : ''}
    <script>$runtime</script>${script == null ? '' : '<script src="$script"></script>'}
    </head><body>${List.generate(frames, (index) => '<svg id="loading-x-anim-$index"><g><path/><path d="M0 0C0 0C$rows"/></g></svg>').join()}
    </body></html>''';
}

void _expectValidId(ClientTransaction transaction) {
  final id = transaction.generateTransactionId('GET', '/i/api/graphql/test/UserTweets');
  final encoded = base64.decode(base64.normalize(id));
  final unmasked = encoded.skip(1).map((value) => value ^ encoded.first).toList();
  expect(id, isNot(contains('=')));
  expect(unmasked.take(_keyBytes.length), _keyBytes);
  expect(unmasked.length, _keyBytes.length + 21);
  expect(unmasked.last, 3);
}

void main() {
  final requests = <Uri>[];

  void respond(http.Response Function(http.Request) handler) {
    xHttpClient = MockClient((request) async {
      requests.add(request.url);
      return handler(request);
    });
  }

  setUp(() {
    requests.clear();
    TwitterHeaders.resetForTesting();
  });
  tearDown(() {
    TwitterHeaders.resetForTesting();
    xHttpClient.close();
    xHttpClient = null;
  });

  test('a complete legacy homepage derives a real signing ID without a fallback', () async {
    respond((request) => http.Response(request.url.host == 'x.com' ? _appShell() : _indices, 200));

    _expectValidId(await ClientTransaction.initialize());

    expect(requests.map((uri) => uri.toString()), ['https://x.com/home', _bundle]);
  });

  test('the new logged-out homepage falls back to the public search shell', () async {
    respond((request) {
      if (request.url.path == '/home') return http.Response(_loggedOut, 200);
      if (request.url.path == '/search') return http.Response(_appShell(), 200);
      expect(request.url.toString(), _bundle);
      return http.Response(_indices, 200);
    });

    _expectValidId(await ClientTransaction.initialize());

    expect(requests.map((uri) => uri.toString()), ['https://x.com/home', 'https://x.com/search?q=AI&f=live', _bundle]);
  });

  for (final status in [403, 404]) {
    test('an unavailable homepage HTTP $status falls back without parsing its body', () async {
      respond((request) {
        if (request.url.path == '/home') return http.Response(_appShell(), status);
        return http.Response(request.url.path == '/search' ? _appShell() : _indices, 200);
      });

      _expectValidId(await ClientTransaction.initialize());

      expect(requests.map((uri) => uri.toString()), [
        'https://x.com/home',
        'https://x.com/search?q=AI&f=live',
        _bundle,
      ]);
    });
  }

  test('both public routes returning HTTP 403 stops after the single fallback', () async {
    respond((request) => http.Response(_appShell(), 403));

    await expectLater(
      ClientTransaction.initialize(),
      throwsA(isA<HttpException>().having((error) => error.uri?.path, 'failed route', '/search')),
    );

    expect(requests.map((uri) => uri.path), ['/home', '/search']);
  });

  test('an authenticated timeline fetch reaches X with its cookie and a real derived signer', () async {
    final timeline = Uri.https('x.com', '/i/api/graphql/test/HomeLatestTimeline');
    const responseBody = '{"data":{"home":{"home_timeline_urt":{"instructions":[]}}}}';
    respond((request) {
      if (request.url == timeline) {
        expect(request.headers['cookie'], 'auth_token=test-session; ct0=test-csrf');
        expect(request.headers['x-csrf-token'], 'test-csrf');
        final id = request.headers['x-client-transaction-id'];
        expect(id, isNotNull);
        final decoded = base64.decode(base64.normalize(id!));
        expect(decoded.skip(1).take(_keyBytes.length).map((byte) => byte ^ decoded.first), _keyBytes);
        return http.Response(responseBody, 200);
      }
      expect(request.headers, isNot(contains('cookie')));
      if (request.url.path == '/home') return http.Response(_loggedOut, 200);
      return http.Response(request.url.path == '/search' ? _appShell() : _indices, 200);
    });
    final account = XRegularAccount();
    addTearDown(account.dispose);

    final response = await account.fetch(
      timeline,
      log: Logger('bootstrap-test'),
      authHeader: {'cookie': 'auth_token=test-session; ct0=test-csrf', 'x-csrf-token': 'test-csrf'},
    );

    expect(response.statusCode, 200);
    expect(response.body, responseBody);
    expect(requests, [
      Uri.https('x.com', '/home'),
      Uri.https('x.com', '/search', {'q': 'AI', 'f': 'live'}),
      Uri.parse(_bundle),
      timeline,
    ]);
  });

  for (final incomplete in [
    (name: 'verification key', shell: _appShell(key: false)),
    (name: 'animation frames', shell: _appShell(frames: 1)),
  ]) {
    test('a homepage missing ${incomplete.name} uses the complete fallback page', () async {
      respond(
        (request) => http.Response(
          request.url.path == '/home'
              ? incomplete.shell
              : request.url.path == '/search'
              ? _appShell()
              : _indices,
          200,
        ),
      );

      _expectValidId(await ClientTransaction.initialize());
      expect(requests.map((uri) => uri.path), ['/home', '/search', Uri.parse(_bundle).path]);
    });
  }

  test('runtime maps support first entries, quoted keys, whitespace and either quote style', () async {
    const runtime = "e=>({'59924' : 'ondemand.s'}[e]||e)+'.'+{\"59924\" : 'abc_DEF-123'}[e]+'a.js'";
    respond((request) => http.Response(request.url.host == 'x.com' ? _appShell(runtime: runtime) : _indices, 200));

    _expectValidId(await ClientTransaction.initialize());

    expect(requests.last.toString(), 'https://abs.twimg.com/responsive-web/client-web/ondemand.s.abc_DEF-123a.js');
  });

  test('the runtime hash map may appear before the chunk names', () async {
    const runtime = 'const hashes={59924:"abcdef1234567890"};const names={59924:"ondemand.s"};';
    respond((request) => http.Response(request.url.host == 'x.com' ? _appShell(runtime: runtime) : _indices, 200));

    _expectValidId(await ClientTransaction.initialize());

    expect(requests.last.toString(), _bundle);
  });

  test('an explicitly linked signer is loaded only from the official static host', () async {
    respond(
      (request) => http.Response(request.url.host == 'x.com' ? _appShell(runtime: '', script: _bundle) : _indices, 200),
    );

    _expectValidId(await ClientTransaction.initialize());
    expect(requests.last.toString(), _bundle);
  });

  for (final untrusted in [
    'https://abs.twimg.com.attacker.invalid/responsive-web/client-web/ondemand.s.abcdefa.js',
    'http://abs.twimg.com/responsive-web/client-web/ondemand.s.abcdefa.js',
    'https://abs.twimg.com/responsive-web/client-web/ondemand.s.abcdefa.js?redirect=elsewhere',
  ]) {
    test('untrusted signing URL is never fetched: $untrusted', () async {
      respond((request) => http.Response(_appShell(runtime: '', script: untrusted), 200));

      await expectLater(ClientTransaction.initialize(), throwsA(isA<FormatException>()));

      expect(requests.map((uri) => uri.path), ['/home', '/search']);
      expect(requests.every((uri) => uri.host == 'x.com'), isTrue);
    });
  }

  test('neither page containing the signer produces a compatibility error after one fallback', () async {
    respond((request) => http.Response(_loggedOut, 200));

    await expectLater(
      ClientTransaction.initialize(),
      throwsA(
        isA<FormatException>().having(
          (error) => error.message,
          'message',
          'X pages did not contain transaction signing data',
        ),
      ),
    );

    expect(requests.length, 2);
  });

  test('an unsuccessful homepage is not parsed even if its body resembles the app', () async {
    respond((request) => http.Response(_appShell(), 429));

    await expectLater(ClientTransaction.initialize(), throwsA(isA<HttpException>()));

    expect(requests.map((uri) => uri.path), ['/home']);
  });

  for (final status in [500, 503]) {
    test('homepage HTTP $status remains terminal after existing transient retries', () async {
      respond((request) => http.Response(_appShell(), status));

      await expectLater(ClientTransaction.initialize(), throwsA(isA<HttpException>()));

      expect(requests.length, status == 503 ? 2 : 1);
      expect(requests.every((uri) => uri.path == '/home'), isTrue);
    });
  }

  test('an unsuccessful signing bundle never produces an ID from its body', () async {
    respond((request) => request.url.host == 'x.com' ? http.Response(_appShell(), 200) : http.Response(_indices, 404));

    await expectLater(ClientTransaction.initialize(), throwsA(isA<HttpException>()));

    expect(requests.length, 2);
  });

  testWidgets('a stalled homepage times out without starting another request', (tester) async {
    final pending = Completer<http.Response>();
    xHttpClient = MockClient((request) {
      requests.add(request.url);
      return pending.future;
    });
    final result = expectLater(ClientTransaction.initialize(), throwsA(isA<TimeoutException>()));

    await tester.pump(const Duration(seconds: 13));
    await result;

    expect(requests.map((uri) => uri.path), ['/home']);
    pending.complete(http.Response(_appShell(), 200));
    await tester.pump();
  });
}
