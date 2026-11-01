import 'dart:convert';

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
