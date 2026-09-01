import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:xta/plugins/deepmarks/nostr_event.dart';

/// Where Deepmarks runs unless the user points the plugin elsewhere.
const String deepmarksDefaultApiBase = 'https://api.deepmarks.org';

enum DeepmarksErrorKind {
  /// No API key or no secret key stored yet.
  notConfigured,

  /// The pasted secret key is not an nsec or hex key.
  badSecretKey,

  /// Bearer token rejected (401).
  unauthorized,

  /// API access is lifetime-tier only (402).
  notLifetimeMember,

  /// The signing key belongs to a different account than the API key (403).
  keyMismatch,

  /// The event was refused (400) — shape, tag or timestamp.
  rejected,

  /// Too many requests (429).
  rateLimited,

  /// Not a Deepmarks API at that address, or an upstream failure (503).
  badServer,

  /// Could not reach the server at all.
  network,
}

class DeepmarksException implements Exception {
  final DeepmarksErrorKind kind;
  final String detail;

  const DeepmarksException(this.kind, this.detail);

  @override
  String toString() => 'DeepmarksException($kind): $detail';
}

class DeepmarksPublishResult {
  final String eventId;
  final List<String> publishedTo;
  final List<String> failedRelays;

  const DeepmarksPublishResult({
    required this.eventId,
    this.publishedTo = const [],
    this.failedRelays = const [],
  });

  /// The API accepted the event but no relay took it, so the bookmark is not
  /// actually stored anywhere yet.
  bool get reachedNoRelay => publishedTo.isEmpty && failedRelays.isNotEmpty;
}

/// Client for the Deepmarks v1 API.
///
/// Deepmarks never signs on the user's behalf: writes carry a locally signed
/// Nostr event and the server only fans it out to relays, so this client takes
/// an already-signed [NostrEvent].
class DeepmarksClient {
  final http.Client httpClient;

  DeepmarksClient({http.Client? httpClient}) : httpClient = httpClient ?? http.Client();

  static const _timeout = Duration(seconds: 25);

  Uri _endpoint(String baseUrl, String path, {Map<String, String>? query}) {
    final base = Uri.parse(baseUrl.trim().isEmpty ? deepmarksDefaultApiBase : baseUrl.trim());
    final segments = [...base.pathSegments.where((s) => s.isNotEmpty), 'api', 'v1', ...path.split('/')];
    return base.replace(pathSegments: segments, queryParameters: query);
  }

  Map<String, String> _headers(String apiKey) => {
        'Authorization': 'Bearer ${apiKey.trim()}',
        'Content-Type': 'application/json',
        'Accept': 'application/json',
      };

  /// Publishes a signed `kind:39701` event.
  Future<DeepmarksPublishResult> publishBookmark({
    required String baseUrl,
    required String apiKey,
    required NostrEvent event,
  }) async {
    if (apiKey.trim().isEmpty) {
      throw const DeepmarksException(DeepmarksErrorKind.notConfigured, 'Missing API key');
    }

    final response = await _send(() => httpClient.post(
          _endpoint(baseUrl, 'bookmarks'),
          headers: _headers(apiKey),
          body: jsonEncode(event.toJson()),
        ));

    if (response.statusCode == 200 && _looksLikeJson(response)) {
      final decoded = jsonDecode(response.body);
      if (decoded is Map) {
        return DeepmarksPublishResult(
          eventId: decoded['eventId'] as String? ?? event.id,
          publishedTo: _stringList(decoded['publishedTo']),
          failedRelays: _stringList(decoded['failedRelays']),
        );
      }
    }

    throw _errorFor(response);
  }

  /// Checks the API key, and reports the pubkey Deepmarks associates with it
  /// when it can be told from the caller's own bookmarks.
  ///
  /// The API exposes no "who am I" route, so the owner is only knowable once
  /// the account has at least one public bookmark; null means "cannot tell",
  /// not "mismatch".
  Future<String?> verify({required String baseUrl, required String apiKey}) async {
    if (apiKey.trim().isEmpty) {
      throw const DeepmarksException(DeepmarksErrorKind.notConfigured, 'Missing API key');
    }

    final response = await _send(() => httpClient.get(
          _endpoint(baseUrl, 'bookmarks', query: {'limit': '1'}),
          headers: _headers(apiKey),
        ));

    if (response.statusCode == 200 && _looksLikeJson(response)) {
      final decoded = jsonDecode(response.body);
      if (decoded is Map) {
        final bookmarks = decoded['bookmarks'];
        if (bookmarks is List && bookmarks.isNotEmpty && bookmarks.first is Map) {
          return (bookmarks.first as Map)['pubkey'] as String?;
        }
        return null;
      }
    }

    throw _errorFor(response);
  }

  Future<http.Response> _send(Future<http.Response> Function() request) async {
    try {
      return await request().timeout(_timeout);
    } on DeepmarksException {
      rethrow;
    } catch (e) {
      throw DeepmarksException(DeepmarksErrorKind.network, '$e');
    }
  }

  DeepmarksException _errorFor(http.Response response) {
    final status = response.statusCode;
    final detail = 'HTTP $status: ${response.body}';

    return switch (status) {
      401 => DeepmarksException(DeepmarksErrorKind.unauthorized, detail),
      402 => DeepmarksException(DeepmarksErrorKind.notLifetimeMember, detail),
      403 => DeepmarksException(DeepmarksErrorKind.keyMismatch, detail),
      400 => DeepmarksException(DeepmarksErrorKind.rejected, detail),
      429 => DeepmarksException(DeepmarksErrorKind.rateLimited, detail),
      _ => DeepmarksException(DeepmarksErrorKind.badServer, detail),
    };
  }

  bool _looksLikeJson(http.Response response) {
    final type = response.headers['content-type'] ?? '';
    if (type.contains('application/json')) {
      return true;
    }
    final body = response.body.trimLeft();
    return body.startsWith('{') || body.startsWith('[');
  }

  List<String> _stringList(Object? value) =>
      value is List ? value.whereType<String>().toList(growable: false) : const [];
}
