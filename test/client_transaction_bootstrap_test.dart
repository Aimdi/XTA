import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:logging/logging.dart';
import 'package:visibility_detector/visibility_detector.dart';
import 'package:xta/catcher/exceptions.dart' show TransactionIdUnavailableException;
import 'package:xta/client/client_regular_account.dart';
import 'package:xta/client/headers.dart';
import 'package:xta/client/http_client.dart';
import 'package:xta/client/x_client_transaction_id/client_transaction.dart';
import 'package:xta/ui/read_failure_kind.dart';
import 'package:xta/utils/read_recovery.dart';

const _bundle = 'https://abs.twimg.com/responsive-web/client-web/ondemand.s.abcdef1234567890a.js';
const _runtime = 'e=>({1:"other",59924:"ondemand.s"}[e]||e)+"."+{1:"1234567",59924:"abcdef1234567890"}[e]+"a.js"';
const _indices = 'f(a[1],16);f(a[2],16);f(a[3],16);f(a[4],16);';
const _loggedOut =
    '<html data-app-env="prod"><script src="https://abs.twimg.com/x-web/entry-client-logged-out-abc.js"></script></html>';
final _keyBytes = List.generate(48, (index) => index);

String _appShell({String runtime = _runtime, bool key = true, int frames = 4, String? script, List<int>? keyBytes}) {
  final rows = List.filled(16, '20 40 60 80 100 120 140 160 180 200 220').join('C');
  return '''<html><head>
    ${key ? '<meta name="twitter-site-verification" content="${base64Encode(keyBytes ?? _keyBytes)}">' : ''}
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
      expect(
        request.headers['cookie'],
        request.url.host == 'x.com' ? 'auth_token=test-session; ct0=test-csrf' : isNull,
      );
      expect(request.headers, isNot(contains('authorization')));
      expect(request.headers, isNot(contains('x-csrf-token')));
      if (request.url.path == '/home') return http.Response(_loggedOut, 200);
      return http.Response(request.url.path == '/search' ? _appShell() : _indices, 200);
    });
    final account = XRegularAccount();
    addTearDown(account.dispose);

    final response = await account.fetch(
      timeline,
      log: Logger('bootstrap-test'),
      authHeader: {'Cookie': 'auth_token=test-session; ct0=test-csrf', 'x-csrf-token': 'test-csrf'},
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

  test('failed anonymous and account A bootstraps cannot block account B timeline reads', () async {
    final timeline = Uri.https('x.com', '/i/api/graphql/test/HomeLatestTimeline');
    final account = XRegularAccount();
    addTearDown(account.dispose);
    final bootstrapCookies = <String?>[];
    respond((request) {
      if (request.url == timeline) {
        expect(request.headers['cookie'], 'auth_token=account-b');
        expect(request.headers['x-client-transaction-id'], isNotEmpty);
        return http.Response('account B timeline', 200);
      }
      expect(request.headers, isNot(contains('authorization')));
      expect(request.headers, isNot(contains('x-csrf-token')));
      if (request.url.host == 'abs.twimg.com') {
        expect(request.headers, isNot(contains('cookie')));
        return http.Response(_indices, 200);
      }
      final cookie = request.headers['cookie'];
      bootstrapCookies.add(cookie);
      return http.Response(cookie == 'auth_token=account-b' ? _appShell() : _loggedOut, 200);
    });

    await expectLater(
      TwitterHeaders.getXClientTransactionIdHeader(timeline),
      throwsA(isA<TransactionIdUnavailableException>()),
    );
    await expectLater(
      account.fetch(timeline, log: Logger('context-test'), authHeader: {'Cookie': 'auth_token=account-a'}),
      throwsA(isA<TransactionIdUnavailableException>()),
    );
    for (var attempt = 0; attempt < 2; attempt++) {
      final result = await account.fetch(
        timeline,
        log: Logger('context-test'),
        authHeader: {'cOoKiE': 'auth_token=account-b'},
      );
      expect(result.body, 'account B timeline');
    }
    expect(bootstrapCookies, [null, null, 'auth_token=account-a', 'auth_token=account-a', 'auth_token=account-b']);
    expect(requests.where((uri) => uri == timeline), hasLength(2));
  });

  test('each account timeline keeps its own derived key and reuses only its matching cookie context', () async {
    final timeline = Uri.https('x.com', '/i/api/graphql/test/HomeLatestTimeline');
    final account = XRegularAccount();
    addTearDown(account.dispose);
    final bootstrapCookies = <String?>[];
    respond((request) {
      final cookie = request.headers['cookie'];
      if (request.url == timeline) {
        final encoded = base64.decode(base64.normalize(request.headers['x-client-transaction-id']!));
        expect(encoded[1] ^ encoded.first, cookie == 'auth_token=account-a' ? 17 : 34);
        return http.Response('loaded', 200);
      }
      if (request.url.host == 'abs.twimg.com') {
        expect(cookie, isNull);
        return http.Response(_indices, 200);
      }
      bootstrapCookies.add(cookie);
      final key = [..._keyBytes];
      key[0] = cookie == 'auth_token=account-a' ? 17 : 34;
      return http.Response(_appShell(keyBytes: key), 200);
    });

    for (final accountId in ['a', 'b', 'a', 'b']) {
      final result = await account.fetch(
        timeline,
        log: Logger('context-test'),
        authHeader: {'Cookie': 'auth_token=account-$accountId'},
      );
      expect(result.statusCode, 200);
    }
    expect(bootstrapCookies, ['auth_token=account-a', 'auth_token=account-b']);
    expect(requests.where((uri) => uri.host == 'abs.twimg.com'), hasLength(2));
    expect(requests.where((uri) => uri == timeline), hasLength(4));
  });

  testWidgets('automatic recovery retries a transient bootstrap failure and sends a signed timeline request', (
    tester,
  ) async {
    VisibilityDetectorController.instance.updateInterval = Duration.zero;
    ReadRecovery.online = false;
    final signals = StreamController<bool>.broadcast();
    final changes = ValueNotifier(0);
    final account = XRegularAccount();
    addTearDown(() {
      VisibilityDetectorController.instance.updateInterval = const Duration(milliseconds: 500);
      ReadRecovery.online = false;
      account.dispose();
      changes.dispose();
    });
    addTearDown(signals.close);
    final timeline = Uri.https('x.com', '/i/api/graphql/test/HomeLatestTimeline');
    var offline = true;
    var loading = false;
    Object? failure;
    http.Response? result;
    respond((request) {
      if (offline) throw const SocketException('offline at startup');
      if (request.url == timeline) {
        expect(request.headers['cookie'], 'auth_token=test-session; ct0=test-csrf');
        expect(request.headers['x-csrf-token'], 'test-csrf');
        expect(request.headers['x-client-transaction-id'], isNotEmpty);
        return http.Response('timeline loaded', 200);
      }
      expect(
        request.headers['cookie'],
        request.url.host == 'x.com' ? 'auth_token=test-session; ct0=test-csrf' : isNull,
      );
      return http.Response(request.url.path == '/home' ? _appShell() : _indices, 200);
    });
    Future<void> load() async {
      loading = true;
      changes.value++;
      try {
        result = await account.fetch(
          timeline,
          log: Logger('recovery-test'),
          authHeader: {'cookie': 'auth_token=test-session; ct0=test-csrf', 'x-csrf-token': 'test-csrf'},
        );
        failure = null;
      } catch (error) {
        failure = error;
      } finally {
        loading = false;
        changes.value++;
      }
    }

    final initial = load();
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));
    await initial;
    expect(readFailureKind(failure), ReadFailureKind.connection);
    expect(requests.map((uri) => uri.path), ['/home', '/home']);

    offline = false;
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: ReadRecovery(
            networkEvents: signals.stream,
            changes: changes,
            isLoading: () => loading,
            recoverableFailure: () => recoverableReadFailure(failure),
            retry: () => unawaited(load()),
            child: const SizedBox.expand(),
          ),
        ),
      ),
    );
    await tester.pump();
    signals.add(true);
    await tester.pump();
    await tester.pump(const Duration(seconds: 2));
    await tester.pump();

    expect(result?.body, 'timeline loaded');
    expect(failure, isNull);
    expect(requests.skip(2), [Uri.https('x.com', '/home'), Uri.parse(_bundle), timeline]);
    await tester.pumpWidget(const SizedBox.shrink());
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
