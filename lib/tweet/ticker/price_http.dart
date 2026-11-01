import 'dart:convert';
import 'dart:math' as math;

import 'package:http/http.dart' as http;

enum TickerErrorKind { notFound, unavailable, badResponse }

class TickerException implements Exception {
  final TickerErrorKind kind;
  final String message;

  TickerException(this.kind, this.message);

  @override
  String toString() => 'TickerException{$kind: $message}';
}

/// One GET for every public price host: the decoded JSON, or a
/// [TickerException] in the terms the quote code acts on. A status in
/// [notFound] says the host has no such symbol; any other refusal, a timeout
/// or no connection says it is not answering for now.
Future<Object?> fetchPriceJson(
  http.Client client,
  Uri uri, {
  required String userAgent,
  Set<int> notFound = const {404},
  Duration timeout = const Duration(seconds: 15),
}) async {
  late http.Response response;
  try {
    response = await client.get(uri, headers: {'User-Agent': userAgent}).timeout(timeout);
  } catch (e) {
    throw TickerException(TickerErrorKind.unavailable, 'Could not reach ${uri.host}: $e');
  }
  if (notFound.contains(response.statusCode)) {
    throw TickerException(TickerErrorKind.notFound, 'No such symbol at ${uri.host}${uri.path}');
  }
  if (response.statusCode != 200) {
    throw TickerException(TickerErrorKind.unavailable, 'HTTP ${response.statusCode} for ${uri.host}${uri.path}');
  }
  try {
    return jsonDecode(response.body);
  } catch (e) {
    throw TickerException(TickerErrorKind.badResponse, 'Response from ${uri.host} was not JSON: $e');
  }
}

/// Which price hosts are resting after a refusal, and which coins a host
/// does not list. Shared by every client, so a host one screen found refusing
/// is left alone by the others too.
class PriceSourceHealth {
  final DateTime Function() _now;
  final _restUntil = <String, DateTime>{};
  final _refusals = <String, int>{};
  final _unlisted = <String>{};

  PriceSourceHealth({DateTime Function()? now}) : _now = now ?? DateTime.now;

  static final shared = PriceSourceHealth();

  static const firstRest = Duration(minutes: 2);
  static const longestRest = Duration(minutes: 30);

  bool resting(String source) => _restUntil[source]?.isAfter(_now()) ?? false;

  /// Rests [source] for [firstRest], twice as long after each refusal in a
  /// row, at most [longestRest].
  void refused(String source) {
    final refusals = (_refusals[source] ?? 0) + 1;
    _refusals[source] = refusals;
    final minutes = math.min(firstRest.inMinutes * math.pow(2, refusals - 1), longestRest.inMinutes).toInt();
    _restUntil[source] = _now().add(Duration(minutes: minutes));
  }

  void answered(String source) {
    _refusals.remove(source);
    _restUntil.remove(source);
  }

  bool lists(String source, String coin) => !_unlisted.contains('$source/$coin');

  void unlisted(String source, String coin) => _unlisted.add('$source/$coin');
}
