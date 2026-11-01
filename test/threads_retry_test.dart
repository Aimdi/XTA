import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/threads/threads_client.dart';
import 'package:xta/plugins/threads/threads_direct_client.dart';
import 'package:xta/ui/rate_limit_retry.dart';
import 'package:xta/ui/read_failure_kind.dart';

ThreadsDirectClient _client(http.Response Function(http.Request request) answer, {bool session = false}) =>
    ThreadsDirectClient(
      PrefServiceCache(
        cache: {
          optionPluginThreadsDirectCookies: session ? 'sessionid=s; csrftoken=c; ds_user_id=1; mid=m; ig_did=g' : '',
          optionPluginThreadsDirectBearer: '',
          optionPluginThreadsDirectCooldownUntil: '',
          optionPluginThreadsUserIds: '{}',
        },
      ),
      minGap: Duration.zero,
      httpClient: MockClient((request) async => answer(request)),
    );

Future<ThreadsException> _failure(Future<Object?> read) async {
  try {
    await read;
  } on ThreadsException catch (error) {
    return error;
  }
  fail('the read did not fail');
}

/// docs/specs/threads-x-parity.md, Reliability.
void main() {
  test("a session throttle waits for the cooldown Meta's answer armed, and so does the next read", () async {
    final client = _client((_) => http.Response('{}', 429), session: true);

    final throttled = await _failure(client.searchUsers('maya'));
    expect(throttled.kind, ThreadsErrorKind.throttled);
    expect(readRetryOf(throttled), ReadRetry.atReset);
    expect(throttled.retryAt!.difference(DateTime.now()).inMinutes, inInclusiveRange(29, 30));
    expect(await readRateLimitReset(throttled), throttled.retryAt);

    final parked = await _failure(client.searchUsers('maya'));
    expect(parked.kind, ThreadsErrorKind.sessionSuspended);
    expect(readRetryOf(parked), ReadRetry.atReset);
    expect(parked.retryAt, throttled.retryAt);
  });

  test('a lost session waits for the reader', () async {
    final client = _client((_) => http.Response('{"message":"login_required"}', 403), session: true);

    final lost = await _failure(client.searchUsers('maya'));
    expect(lost.kind, ThreadsErrorKind.sessionSuspended);
    expect(readRetryOf(lost), ReadRetry.manual);
    expect(recoverableReadFailure(lost), isNull);
  });

  test('a guest throttle waits as long as any limit without a stated reset', () async {
    final client = _client((_) => http.Response('', 429));

    final throttled = await _failure(client.searchGuestPosts('kiln'));
    expect(throttled.kind, ThreadsErrorKind.throttled);
    expect(readRetryOf(throttled), ReadRetry.atReset);
    expect(throttled.retryAt!.difference(DateTime.now()).inMinutes, inInclusiveRange(14, 15));
  });

  test('a host that did not answer is tried again on the usual backoff', () async {
    final client = _client((_) => http.Response('down', 503));

    final unreachable = await _failure(client.searchGuestPosts('kiln'));
    expect(unreachable.kind, ThreadsErrorKind.unreachable);
    expect(readRetryOf(unreachable), ReadRetry.backoff);
    expect(readRetryOf(ThreadsException(ThreadsErrorKind.noSuchFeed, 'no posts')), ReadRetry.backoff);
  });

  test('without a setup, or with the session refused, nothing retries on its own', () {
    for (final kind in [ThreadsErrorKind.notConfigured, ThreadsErrorKind.unauthorized]) {
      expect(readRetryOf(ThreadsException(kind, 'no')), ReadRetry.manual, reason: '$kind');
    }
  });
}
