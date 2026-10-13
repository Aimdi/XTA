import 'dart:async';
import 'dart:collection';
import 'dart:io' show HttpDate;
import 'dart:math';

import 'package:http/http.dart' as http;

class SubstackClientException implements Exception {
  final String message;
  SubstackClientException(this.message);
  @override
  String toString() => message;
}

/// The pasted address is a real site, but it is not a Substack publication.
class SubstackNotPublicationException extends SubstackClientException {
  SubstackNotPublicationException() : super('This is not a Substack publication');
}

/// A host asked for fewer requests, for longer than a reader should wait.
class SubstackRateLimitedException extends SubstackClientException {
  final Duration retryAfter;
  SubstackRateLimitedException(Uri uri, this.retryAfter) : super('Rate limited by ${uri.host}');
}

/// Time as the transport sees it, so tests can wait without waiting.
class SubstackClock {
  const SubstackClock();
  DateTime now() => DateTime.now();
  Future<void> sleep(Duration duration) => Future<void>.delayed(duration);
}

/// Every Substack host answers from the same service and is limited as one;
/// any other host is limited on its own.
String substackRateBucket(String host) {
  final name = host.toLowerCase();
  if (name == 'substack.com' || name.endsWith('.substack.com')) return 'substack.com';
  return name.startsWith('www.') ? name.substring(4) : name;
}

/// The wait a `Retry-After` header asks for: seconds, or an HTTP date.
Duration? substackRetryAfter(String? value, DateTime now) {
  final raw = value?.trim() ?? '';
  if (raw.isEmpty) return null;
  final seconds = int.tryParse(raw);
  if (seconds != null) return Duration(seconds: max(0, seconds));
  try {
    final wait = HttpDate.parse(raw).difference(now);
    return wait.isNegative ? Duration.zero : wait;
  } catch (_) {
    return null;
  }
}

class _Bucket {
  final int capacity;
  final _waiting = Queue<Completer<void>>();
  int _inFlight = 0;
  DateTime? blockedUntil;
  int strikes = 0;

  _Bucket(this.capacity);

  Future<void> acquire() {
    if (_inFlight < capacity) {
      _inFlight++;
      return Future.value();
    }
    final turn = Completer<void>();
    _waiting.add(turn);
    return turn.future;
  }

  void release() {
    if (_waiting.isNotEmpty) {
      _waiting.removeFirst().complete();
    } else {
      _inFlight--;
    }
  }
}

/// The one road every Substack request takes.
///
/// A rate limit blocks its whole bucket, so the next request waits instead of
/// asking again; a wait longer than [longestWait] fails at once rather than
/// keeping a screen spinning.
class SubstackTransport {
  static const perBucket = 4;
  static const retries = 2;
  static const requestTimeout = Duration(seconds: 20);
  static const longestWait = Duration(seconds: 10);
  static const longestBlock = Duration(minutes: 5);

  final http.Client client;
  final SubstackClock clock;
  final Random _random;
  final _buckets = <String, _Bucket>{};

  SubstackTransport(this.client, {this.clock = const SubstackClock(), Random? random}) : _random = random ?? Random();

  Future<http.Response> get(Uri uri, {required Map<String, String> headers}) async {
    final bucket = _buckets.putIfAbsent(substackRateBucket(uri.host), () => _Bucket(perBucket));
    await bucket.acquire();
    try {
      return await _send(uri, headers, bucket);
    } finally {
      bucket.release();
    }
  }

  Future<http.Response> _send(Uri uri, Map<String, String> headers, _Bucket bucket) async {
    for (var attempt = 0; ; attempt++) {
      await _waitOut(uri, bucket);
      final response = await client.get(uri, headers: headers).timeout(requestTimeout);
      final limit = _limitOf(response, bucket);
      if (limit != null) {
        _block(bucket, limit);
        if (attempt >= retries) throw SubstackRateLimitedException(uri, _remaining(bucket));
        continue;
      }
      bucket.strikes = 0;
      if (attempt == 0 && const [500, 502, 503, 504].contains(response.statusCode)) {
        await clock.sleep(_jittered(const Duration(seconds: 1)));
        continue;
      }
      return response;
    }
  }

  Future<void> _waitOut(Uri uri, _Bucket bucket) async {
    final wait = _remaining(bucket);
    if (wait <= Duration.zero) return;
    if (wait > longestWait) throw SubstackRateLimitedException(uri, wait);
    await clock.sleep(wait);
  }

  Duration _remaining(_Bucket bucket) => bucket.blockedUntil?.difference(clock.now()) ?? Duration.zero;

  /// The wait a response asks for, or null when it is not a rate limit.
  Duration? _limitOf(http.Response response, _Bucket bucket) {
    final asked = substackRetryAfter(response.headers['retry-after'], clock.now());
    if (response.statusCode != 429 && !(response.statusCode == 503 && asked != null)) return null;
    bucket.strikes++;
    final wait = asked ?? _jittered(const Duration(seconds: 1) * (1 << min(bucket.strikes - 1, 16)));
    return wait > longestBlock ? longestBlock : wait;
  }

  void _block(_Bucket bucket, Duration wait) {
    final until = clock.now().add(wait);
    final current = bucket.blockedUntil;
    if (current == null || until.isAfter(current)) bucket.blockedUntil = until;
  }

  /// Between half and all of [base], so clients that were limited together do
  /// not all come back at the same moment.
  Duration _jittered(Duration base) => base * (0.5 + _random.nextDouble() * 0.5);
}
