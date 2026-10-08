import 'dart:convert';

import 'package:http/http.dart' as http;

/// Where a save attempt ended up.
enum KarakeepSaveOutcome {
  /// The bookmark was created (HTTP 201).
  saved,

  /// Karakeep already had this URL and returned the existing bookmark (HTTP 200).
  alreadySaved,
}

class KarakeepSaveResult {
  final KarakeepSaveOutcome outcome;
  final String? bookmarkId;

  const KarakeepSaveResult(this.outcome, {this.bookmarkId});
}

/// Why a save could not happen, in terms the user can act on.
enum KarakeepErrorKind { notConfigured, badServer, unauthorized, server, network }

class KarakeepException implements Exception {
  final KarakeepErrorKind kind;
  final String detail;

  const KarakeepException(this.kind, this.detail);

  @override
  String toString() => 'KarakeepException($kind): $detail';
}

final RegExp _hostPattern = RegExp(r'^[A-Za-z0-9]([A-Za-z0-9._-]*[A-Za-z0-9])?$');

/// Normalises whatever the user typed into the base origin of their instance.
///
/// People paste `karakeep.example.com`, a trailing slash, or the full
/// `/api/v1` path; all of those should work without a lecture.
Uri? parseKarakeepBaseUrl(String raw) {
  var text = raw.trim();
  if (text.isEmpty) {
    return null;
  }
  if (!text.contains('://')) {
    text = 'https://$text';
  }

  final uri = Uri.tryParse(text);
  if (uri == null || uri.host.isEmpty || (uri.scheme != 'http' && uri.scheme != 'https')) {
    return null;
  }
  // Uri happily accepts "not a url" once a scheme is prepended, so the host has
  // to look like a host before we tell the user their address is fine.
  if (!_hostPattern.hasMatch(uri.host)) {
    return null;
  }

  // Keep any path prefix (reverse proxies host Karakeep under /karakeep) but
  // drop a trailing api/v1 the user copied from the docs.
  final segments = uri.pathSegments.where((s) => s.isNotEmpty).toList();
  while (segments.isNotEmpty && (segments.last == 'v1' || segments.last == 'api')) {
    segments.removeLast();
  }

  return Uri(
    scheme: uri.scheme,
    host: uri.host,
    port: uri.hasPort ? uri.port : null,
    pathSegments: segments,
  );
}

/// Minimal read/write client for a self-hosted Karakeep instance.
///
/// Only what the "save this" flow needs: create a link bookmark, and a probe
/// used by the settings screen to tell the user their details work.
class KarakeepClient {
  final http.Client httpClient;

  KarakeepClient({http.Client? httpClient}) : httpClient = httpClient ?? http.Client();

  static const _timeout = Duration(seconds: 20);

  /// Karakeep caps bookmark titles; keep well inside it.
  static const maxTitleLength = 250;

  Uri _endpoint(Uri base, String path, {Map<String, String>? query}) {
    final segments = [...base.pathSegments.where((s) => s.isNotEmpty), 'api', 'v1', ...path.split('/')];
    return base.replace(pathSegments: segments, queryParameters: query);
  }

  Map<String, String> _headers(String apiKey) => {
        'Authorization': 'Bearer $apiKey',
        'Content-Type': 'application/json',
        'Accept': 'application/json',
      };

  /// Saves [url] as a link bookmark. A 200 means Karakeep already had it.
  Future<KarakeepSaveResult> saveLink({
    required String baseUrl,
    required String apiKey,
    required String url,
    String? title,
    String? note,
  }) async {
    final base = parseKarakeepBaseUrl(baseUrl);
    if (base == null || apiKey.trim().isEmpty) {
      throw const KarakeepException(KarakeepErrorKind.notConfigured, 'Missing server URL or API key');
    }

    final trimmedTitle = title?.trim();
    final body = <String, dynamic>{
      'type': 'link',
      'url': url,
      if (trimmedTitle != null && trimmedTitle.isNotEmpty)
        'title': trimmedTitle.length > maxTitleLength ? trimmedTitle.substring(0, maxTitleLength) : trimmedTitle,
      if (note != null && note.trim().isNotEmpty) 'note': note.trim(),
    };

    final response = await _send(
      () => httpClient.post(_endpoint(base, 'bookmarks'), headers: _headers(apiKey), body: jsonEncode(body)),
    );

    // A proxy that never reaches Karakeep can answer 200 with an HTML page;
    // reporting that as "already saved" would be a lie.
    if ((response.statusCode == 200 || response.statusCode == 201) && _looksLikeJson(response)) {
      return KarakeepSaveResult(
        response.statusCode == 200 ? KarakeepSaveOutcome.alreadySaved : KarakeepSaveOutcome.saved,
        bookmarkId: _idOf(response.body),
      );
    }

    throw _errorFor(response);
  }

  /// True when the server answers the current user endpoint with these details.
  Future<bool> verify({required String baseUrl, required String apiKey}) async {
    final base = parseKarakeepBaseUrl(baseUrl);
    if (base == null || apiKey.trim().isEmpty) {
      throw const KarakeepException(KarakeepErrorKind.notConfigured, 'Missing server URL or API key');
    }

    final response = await _send(
      () => httpClient.get(_endpoint(base, 'bookmarks', query: {'limit': '1'}), headers: _headers(apiKey)),
    );
    if (response.statusCode == 200 && _looksLikeJson(response)) {
      return true;
    }
    throw _errorFor(response);
  }

  Future<http.Response> _send(Future<http.Response> Function() request) async {
    try {
      return await request().timeout(_timeout);
    } on KarakeepException {
      rethrow;
    } catch (e) {
      throw KarakeepException(KarakeepErrorKind.network, '$e');
    }
  }

  KarakeepException _errorFor(http.Response response) {
    final status = response.statusCode;
    if (status == 401 || status == 403) {
      return KarakeepException(KarakeepErrorKind.unauthorized, 'HTTP $status');
    }
    // A wrong host or a reverse proxy that never reaches Karakeep answers with
    // HTML or a 404 rather than the API's JSON error.
    if (status == 404 || !_looksLikeJson(response)) {
      return KarakeepException(KarakeepErrorKind.badServer, 'HTTP $status');
    }
    return KarakeepException(KarakeepErrorKind.server, 'HTTP $status: ${response.body}');
  }

  bool _looksLikeJson(http.Response response) {
    final type = response.headers['content-type'] ?? '';
    if (type.contains('application/json')) {
      return true;
    }
    final body = response.body.trimLeft();
    return body.startsWith('{') || body.startsWith('[');
  }

  String? _idOf(String body) {
    try {
      final decoded = jsonDecode(body);
      if (decoded is Map && decoded['id'] is String) {
        return decoded['id'] as String;
      }
    } catch (_) {
      // The save succeeded; an unreadable body is not worth failing over.
    }
    return null;
  }
}
