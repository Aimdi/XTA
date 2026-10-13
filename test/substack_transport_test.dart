import 'dart:async';
import 'dart:io' show HttpDate;
import 'dart:math';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:xta/plugins/substack/substack_transport.dart';

import 'support/substack_clock.dart';

/// Jitter at its low end, so every wait is exactly half its base.
class _LowJitter implements Random {
  @override
  double nextDouble() => 0;
  @override
  int nextInt(int max) => 0;
  @override
  bool nextBool() => false;
}

final _feed = Uri.parse('https://fieldnotes.substack.com/api/v1/posts');
final _other = Uri.parse('https://harbour.substack.com/api/v1/posts');
final _custom = Uri.parse('https://www.example.com/api/v1/posts');

({SubstackTransport transport, FakeSubstackClock clock, List<Uri> sent}) _transport(
  FutureOr<http.Response> Function(http.Request request, int index) respond,
) {
  final sent = <Uri>[];
  final clock = FakeSubstackClock();
  final transport = SubstackTransport(
    MockClient((request) async {
      sent.add(request.url);
      return respond(request, sent.length - 1);
    }),
    clock: clock,
    random: _LowJitter(),
  );
  return (transport: transport, clock: clock, sent: sent);
}

Future<http.Response> _get(SubstackTransport transport, Uri uri) => transport.get(uri, headers: const {});

void main() {
  test('Substack hosts share one rate bucket; other hosts have their own', () {
    expect(substackRateBucket('fieldnotes.substack.com'), 'substack.com');
    expect(substackRateBucket('SUBSTACK.COM'), 'substack.com');
    expect(substackRateBucket('open.substack.com'), 'substack.com');
    expect(substackRateBucket('www.example.com'), 'example.com');
    expect(substackRateBucket('newsletter.example.com'), 'newsletter.example.com');
  });

  test('Retry-After is read as seconds or as an HTTP date, and nothing else', () {
    final now = DateTime.utc(2026, 10, 13, 12);
    expect(substackRetryAfter('3', now), const Duration(seconds: 3));
    expect(substackRetryAfter(HttpDate.format(now.add(const Duration(seconds: 7))), now), const Duration(seconds: 7));
    expect(substackRetryAfter(HttpDate.format(now.subtract(const Duration(minutes: 1))), now), Duration.zero);
    expect(substackRetryAfter('soon', now), isNull);
    expect(substackRetryAfter(null, now), isNull);
  });

  test('a 429 backs off with growing jittered waits, then reports the rate limit', () async {
    final run = _transport((request, _) => http.Response('', 429, request: request));

    final failure = await _get(run.transport, _feed).then<Object?>((_) => null, onError: (Object error) => error);

    expect(failure, isA<SubstackRateLimitedException>());
    expect((failure! as SubstackRateLimitedException).retryAfter, const Duration(seconds: 2));
    expect(run.sent, hasLength(SubstackTransport.retries + 1));
    expect(run.clock.sleeps, [const Duration(milliseconds: 500), const Duration(seconds: 1)]);
  });

  test('a Retry-After answer is waited out, and the next answer is read', () async {
    final run = _transport(
      (request, index) => index == 0
          ? http.Response('', 429, request: request, headers: {'retry-after': '3'})
          : http.Response('[]', 200, request: request),
    );

    final response = await _get(run.transport, _feed);

    expect(response.statusCode, 200);
    expect(run.clock.sleeps, [const Duration(seconds: 3)]);
  });

  test('a long wait fails at once and holds back every host in the bucket', () async {
    final run = _transport(
      (request, _) => request.url.host == _custom.host
          ? http.Response('[]', 200, request: request)
          : http.Response('', 429, request: request, headers: {'retry-after': '120'}),
    );

    await expectLater(_get(run.transport, _feed), throwsA(isA<SubstackRateLimitedException>()));
    await expectLater(_get(run.transport, _other), throwsA(isA<SubstackRateLimitedException>()));
    expect(run.sent, [_feed], reason: 'the second Substack host must not be asked while the bucket waits');

    expect((await _get(run.transport, _custom)).statusCode, 200);
    run.clock.advance(const Duration(minutes: 3));
    await _get(run.transport, _other).catchError((Object _) => http.Response('', 429));
    expect(run.sent, [_feed, _custom, _other]);
  });

  test('a server error is retried once after a short jittered pause', () async {
    final recovering = _transport(
      (request, index) => http.Response(index == 0 ? '' : '[]', index == 0 ? 503 : 200, request: request),
    );
    expect((await _get(recovering.transport, _feed)).statusCode, 200);
    expect(recovering.clock.sleeps, [const Duration(milliseconds: 500)]);

    final down = _transport((request, _) => http.Response('', 502, request: request));
    expect((await _get(down.transport, _feed)).statusCode, 502);
    expect(down.sent, hasLength(2));
  });

  test('a success clears the strikes, so the next rate limit starts small again', () async {
    final run = _transport(
      (request, index) =>
          index == 1 ? http.Response('[]', 200, request: request) : http.Response('', 429, request: request),
    );

    await _get(run.transport, _feed);
    await _get(run.transport, _feed).catchError((Object _) => http.Response('', 429));

    expect(run.clock.sleeps.take(2), [const Duration(milliseconds: 500), const Duration(milliseconds: 500)]);
  });

  test('no more than four requests are in flight per bucket', () async {
    final gate = Completer<void>();
    var active = 0;
    var most = 0;
    final transport = SubstackTransport(
      MockClient((request) async {
        most = max(most, ++active);
        await gate.future;
        active--;
        return http.Response('[]', 200, request: request);
      }),
      clock: FakeSubstackClock(),
    );

    final requests = [
      for (var i = 0; i < 6; i++) _get(transport, Uri.parse('https://pub$i.substack.com/api/v1/posts')),
      _get(transport, _custom),
    ];
    await Future<void>.delayed(Duration.zero);
    expect(active, SubstackTransport.perBucket + 1, reason: 'four Substack requests and the custom domain');
    gate.complete();
    await Future.wait(requests);
    expect(most, SubstackTransport.perBucket + 1);
  });
}
