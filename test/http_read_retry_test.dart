import 'dart:async';
import 'dart:io' show HttpDate, SocketException;

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:xta/catcher/exceptions.dart' as errors;
import 'package:xta/utils/http_read.dart';
import 'package:xta/utils/read_request_scope.dart';

void main() {
  testWidgets('GET retries temporary server failures without changing URL or headers', (tester) async {
    final requests = <http.Request>[];
    final client = MockClient((request) async {
      requests.add(request);
      return http.Response(requests.length < 3 ? 'temporary' : 'posts', requests.length < 3 ? 503 : 200);
    });
    final uri = Uri.parse('https://example.org/feed?cursor=next');
    final result = client.getWithReadRetry(
      uri,
      headers: {'Authorization': 'Bearer test'},
      timeout: const Duration(seconds: 8),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    await tester.pump(const Duration(milliseconds: 1200));
    expect((await result).body, 'posts');
    expect(requests, hasLength(3));
    for (final request in requests) {
      expect(request.method, 'GET');
      expect(request.url, uri);
      expect(request.headers['Authorization'], 'Bearer test');
    }
  });

  testWidgets('network errors and interrupted response bodies recover quietly', (tester) async {
    var calls = 0;
    final client = MockClient.streaming((_, _) async {
      calls++;
      if (calls == 1) throw const SocketException('temporary');
      if (calls == 2) return http.StreamedResponse(Stream.error(http.ClientException('body interrupted')), 200);
      return http.StreamedResponse(Stream.value('posts'.codeUnits), 200);
    });
    final result = client.getWithReadRetry(Uri.parse('https://example.org'), timeout: const Duration(seconds: 8));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    await tester.pump(const Duration(milliseconds: 1200));
    expect((await result).body, 'posts');
    expect(calls, 3);
  });

  test('authentication, missing content and rate-limit responses are returned without retries', () async {
    for (final code in [200, 400, 401, 403, 404, 429, 501]) {
      var calls = 0;
      final client = MockClient((_) async {
        calls++;
        return http.Response('body', code);
      });
      final response = await client.getWithReadRetry(
        Uri.parse('https://example.org'),
        timeout: const Duration(seconds: 8),
      );
      expect(response.statusCode, code);
      expect(response.body, 'body');
      expect(calls, 1);
    }
  });

  testWidgets('Retry-After is a minimum wait and does not extend the deadline', (tester) async {
    var calls = 0;
    final client = MockClient((_) async {
      calls++;
      return calls == 1 ? http.Response('', 503, headers: {'retry-after': '2'}) : http.Response('ok', 200);
    });
    final result = client.getWithReadRetry(Uri.parse('https://example.org'), timeout: const Duration(seconds: 8));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 1999));
    expect(calls, 1);
    await tester.pump(const Duration(milliseconds: 1));
    expect((await result).statusCode, 200);
    expect(calls, 2);

    calls = 0;
    final unavailable = MockClient((_) async {
      calls++;
      return http.Response('later', 503, headers: {'retry-after': '120'});
    });
    expect(
      (await unavailable.getWithReadRetry(
        Uri.parse('https://example.org'),
        timeout: const Duration(seconds: 8),
      )).statusCode,
      503,
    );
    expect(calls, 1);
  });

  test('Retry-After HTTP dates beyond the request budget are not retried early', () async {
    var calls = 0;
    final client = MockClient((_) async {
      calls++;
      return http.Response(
        '',
        503,
        headers: {'retry-after': HttpDate.format(DateTime.now().add(const Duration(minutes: 5)))},
      );
    });
    expect(
      (await client.getWithReadRetry(Uri.parse('https://example.org'), timeout: const Duration(seconds: 8))).statusCode,
      503,
    );
    expect(calls, 1);
  });

  testWidgets('HTTP deadline aborts a stalled request and prevents late retries', (tester) async {
    final client = _StalledClient();
    final result = client.getWithReadRetry(Uri.parse('https://example.org'), timeout: const Duration(seconds: 1));
    final check = expectLater(result, throwsA(isA<TimeoutException>()));
    await tester.pump(const Duration(seconds: 1));
    await check;
    expect(client.aborted, isTrue);
    client.pending.complete(http.StreamedResponse(Stream.value([]), 503));
    await tester.pump(const Duration(seconds: 5));
    expect(client.calls, 1);
  });

  testWidgets('a cancelled outer read stops HTTP backoff before another send', (tester) async {
    final scope = ReadRequestScope();
    var calls = 0;
    final client = MockClient((_) async {
      calls++;
      return http.Response('', 503);
    });
    final check = expectLater(
      scope.start(
        () => client.getWithReadRetry(Uri.parse('https://example.org'), timeout: const Duration(seconds: 8)),
        timeout: const Duration(seconds: 8),
      ),
      throwsA(isA<ReadCancelled>()),
    );
    await tester.pump();
    scope.cancel();
    await check;
    await tester.pump();
    expect(calls, 1);
  });

  testWidgets('HTTP and its enclosing page share two retries', (tester) async {
    var calls = 0;
    final client = MockClient((_) async {
      calls++;
      return http.Response('busy', 503);
    });
    final check = expectLater(
      ReadRequestScope().start(() async {
        final response = await client.getWithReadRetry(
          Uri.parse('https://example.org'),
          timeout: const Duration(seconds: 8),
        );
        throw errors.HttpException(response);
      }, timeout: const Duration(seconds: 10)),
      throwsA(isA<errors.HttpException>().having((error) => error.statusCode, 'status', 503)),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    await tester.pump(const Duration(milliseconds: 1200));
    await check;
    expect(calls, 3);
  });

  testWidgets('cancelling a page aborts an active GET without waiting for its deadline', (tester) async {
    final scope = ReadRequestScope();
    final client = _StalledClient();
    final check = expectLater(
      scope.start(
        () => client.getWithReadRetry(Uri.parse('https://example.org'), timeout: const Duration(seconds: 8)),
        timeout: const Duration(seconds: 10),
      ),
      throwsA(isA<ReadCancelled>()),
    );
    await tester.pump();
    scope.cancel();
    await check;
    await tester.pump();
    expect(client.aborted, isTrue);
    client.pending.complete(http.StreamedResponse(Stream.value([]), 503));
    await tester.pump();
    expect(client.calls, 1);
  });
}

class _StalledClient extends http.BaseClient {
  final pending = Completer<http.StreamedResponse>();
  var calls = 0;
  var aborted = false;

  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) {
    calls++;
    final abortable = request as http.Abortable;
    abortable.abortTrigger!.then((_) => aborted = true);
    return pending.future;
  }
}
