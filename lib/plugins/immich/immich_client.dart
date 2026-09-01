import 'dart:convert';
import 'dart:typed_data';

import 'package:http/http.dart' as http;

/// What the server did with an asset it was handed.
enum ImmichUploadOutcome {
  /// A new asset was created.
  created,

  /// Immich recognised the bytes by checksum and kept the one it had.
  duplicate,
}

class ImmichUploadResult {
  final ImmichUploadOutcome outcome;
  final String? assetId;

  const ImmichUploadResult(this.outcome, {this.assetId});
}

/// Why an upload could not happen, in terms the user can act on.
enum ImmichErrorKind { notConfigured, badServer, unauthorized, server, network }

class ImmichException implements Exception {
  final ImmichErrorKind kind;
  final String detail;

  const ImmichException(this.kind, this.detail);

  @override
  String toString() => 'ImmichException($kind): $detail';
}

final RegExp _hostPattern = RegExp(r'^[A-Za-z0-9]([A-Za-z0-9._-]*[A-Za-z0-9])?$');
final RegExp _ipAddressPattern = RegExp(r'^\d{1,3}(\.\d{1,3}){3}(:\d+)?$');

bool _looksLikeIpAddress(String text) => _ipAddressPattern.hasMatch(text);

/// Normalises whatever the user typed into the base origin of their instance.
///
/// Immich is nearly always reached by address and port on a home network, and
/// its own UI shows the address with `/api` on the end — so `192.168.1.10:2283`,
/// a trailing slash and `https://photos.example.org/api` all have to mean the
/// same thing. A path prefix is kept, because plenty of people put Immich behind
/// a reverse proxy under one.
Uri? parseImmichBaseUrl(String raw) {
  var text = raw.trim();
  if (text.isEmpty) {
    return null;
  }
  if (!text.contains('://')) {
    // An address typed without a scheme is guessed from what it looks like: a
    // bare IP is somebody's own network, where Immich is served over http and
    // assuming https would simply fail to connect. A name is assumed to be
    // reachable properly.
    text = '${_looksLikeIpAddress(text) ? 'http' : 'https'}://$text';
  }

  final uri = Uri.tryParse(text);
  if (uri == null || uri.host.isEmpty || (uri.scheme != 'http' && uri.scheme != 'https')) {
    return null;
  }
  // `Uri` accepts "not a url" once a scheme is in front of it, so the host has
  // to look like a host before the address is called usable.
  if (!_hostPattern.hasMatch(uri.host)) {
    return null;
  }

  final segments = uri.pathSegments.where((s) => s.isNotEmpty).toList();
  // Immich's own "server URL" field wants the `/api` on the end; the endpoints
  // below add it, so drop what was pasted rather than reaching `/api/api`.
  while (segments.isNotEmpty && segments.last == 'api') {
    segments.removeLast();
  }

  return Uri(
    scheme: uri.scheme,
    host: uri.host,
    port: uri.hasPort ? uri.port : null,
    pathSegments: segments,
  );
}

/// Write-only client for a self-hosted Immich instance: upload an asset, put it
/// in an album, and a probe so the settings screen can say whether the details
/// work.
///
/// Nothing here reads the user's library. XTA sends media it already has and
/// asks nothing back about what else is in there.
class ImmichClient {
  final http.Client httpClient;

  ImmichClient({http.Client? httpClient}) : httpClient = httpClient ?? http.Client();

  static const _timeout = Duration(seconds: 20);

  /// Uploading is a whole file over a home connection, so it gets its own.
  static const uploadTimeout = Duration(minutes: 5);

  /// Identifies this app as the source of an asset, which is what Immich shows
  /// in the asset's details.
  static const deviceId = 'xta';

  Uri _endpoint(Uri base, String path) => base.replace(
        pathSegments: [...base.pathSegments.where((s) => s.isNotEmpty), 'api', ...path.split('/')],
      );

  Map<String, String> _headers(String apiKey) => {
        'x-api-key': apiKey,
        'Accept': 'application/json',
      };

  /// Sends [bytes] as a new asset. A `duplicate` outcome is a success: Immich
  /// matched the checksum and kept what it already had.
  Future<ImmichUploadResult> upload({
    required String baseUrl,
    required String apiKey,
    required Uint8List bytes,
    required String fileName,
    required String deviceAssetId,
    required DateTime createdAt,
  }) async {
    final base = _requireBase(baseUrl, apiKey);
    final stamp = createdAt.toUtc().toIso8601String();

    // Immich renamed this endpoint; the older path is still what an instance a
    // release or two behind answers on.
    var response = await _sendUpload(base, apiKey, 'assets', bytes, fileName, deviceAssetId, stamp);
    if (response.statusCode == 404) {
      response = await _sendUpload(base, apiKey, 'asset/upload', bytes, fileName, deviceAssetId, stamp);
    }

    if ((response.statusCode == 200 || response.statusCode == 201) && _looksLikeJson(response)) {
      final body = _decode(response.body);
      return ImmichUploadResult(
        body?['status'] == 'duplicate' ? ImmichUploadOutcome.duplicate : ImmichUploadOutcome.created,
        assetId: body?['id'] as String?,
      );
    }

    throw _errorFor(response);
  }

  Future<http.Response> _sendUpload(
    Uri base,
    String apiKey,
    String path,
    Uint8List bytes,
    String fileName,
    String deviceAssetId,
    String stamp,
  ) async {
    final request = http.MultipartRequest('POST', _endpoint(base, path))
      ..headers.addAll(_headers(apiKey))
      ..fields['deviceAssetId'] = deviceAssetId
      ..fields['deviceId'] = deviceId
      ..fields['fileCreatedAt'] = stamp
      ..fields['fileModifiedAt'] = stamp
      ..files.add(http.MultipartFile.fromBytes('assetData', bytes, filename: fileName));

    return _send(() async => http.Response.fromStream(await httpClient.send(request)), timeout: uploadTimeout);
  }

  /// The id of the album named [name], creating it if the server has no such
  /// album. Returns null when the album could not be resolved, which must not
  /// fail an upload that has already succeeded.
  Future<String?> ensureAlbum({
    required String baseUrl,
    required String apiKey,
    required String name,
  }) async {
    try {
      return await _findAlbum(baseUrl: baseUrl, apiKey: apiKey, name: name) ??
          await _createAlbum(baseUrl: baseUrl, apiKey: apiKey, name: name);
    } catch (_) {
      return null;
    }
  }

  Future<String?> _findAlbum({required String baseUrl, required String apiKey, required String name}) async {
    final base = _requireBase(baseUrl, apiKey);
    final response = await _send(() => httpClient.get(_endpoint(base, 'albums'), headers: _headers(apiKey)));
    if (response.statusCode != 200 || !_looksLikeJson(response)) {
      return null;
    }

    final decoded = jsonDecode(response.body);
    if (decoded is! List) {
      return null;
    }
    for (final album in decoded) {
      if (album is Map && album['albumName'] == name && album['id'] is String) {
        return album['id'] as String;
      }
    }
    return null;
  }

  Future<String?> _createAlbum({required String baseUrl, required String apiKey, required String name}) async {
    final base = _requireBase(baseUrl, apiKey);
    final response = await _send(() => httpClient.post(
          _endpoint(base, 'albums'),
          headers: {..._headers(apiKey), 'Content-Type': 'application/json'},
          body: jsonEncode({'albumName': name}),
        ));
    if ((response.statusCode == 200 || response.statusCode == 201) && _looksLikeJson(response)) {
      return _decode(response.body)?['id'] as String?;
    }
    return null;
  }

  /// Files [assetIds] into [albumId]. Assets Immich already has in the album are
  /// reported per-asset rather than as a failure, so this only reports whether
  /// the request itself was accepted.
  Future<bool> addToAlbum({
    required String baseUrl,
    required String apiKey,
    required String albumId,
    required List<String> assetIds,
  }) async {
    if (assetIds.isEmpty) {
      return true;
    }
    final base = _requireBase(baseUrl, apiKey);
    final response = await _send(() => httpClient.put(
          _endpoint(base, 'albums/$albumId/assets'),
          headers: {..._headers(apiKey), 'Content-Type': 'application/json'},
          body: jsonEncode({'ids': assetIds}),
        ));
    return response.statusCode == 200 || response.statusCode == 201;
  }

  /// True when the server accepts these details. Throws an [ImmichException]
  /// describing what is wrong otherwise, so the settings screen can say.
  Future<bool> verify({required String baseUrl, required String apiKey}) async {
    final base = _requireBase(baseUrl, apiKey);

    var response = await _send(() => httpClient.get(_endpoint(base, 'users/me'), headers: _headers(apiKey)));
    if (response.statusCode == 404) {
      response = await _send(() => httpClient.get(_endpoint(base, 'user/me'), headers: _headers(apiKey)));
    }

    if (response.statusCode == 200 && _looksLikeJson(response)) {
      return true;
    }
    throw _errorFor(response);
  }

  Uri _requireBase(String baseUrl, String apiKey) {
    final base = parseImmichBaseUrl(baseUrl);
    if (base == null || apiKey.trim().isEmpty) {
      throw const ImmichException(ImmichErrorKind.notConfigured, 'Missing server URL or API key');
    }
    return base;
  }

  Future<http.Response> _send(Future<http.Response> Function() request, {Duration? timeout}) async {
    try {
      return await request().timeout(timeout ?? _timeout);
    } on ImmichException {
      rethrow;
    } catch (e) {
      throw ImmichException(ImmichErrorKind.network, '$e');
    }
  }

  ImmichException _errorFor(http.Response response) {
    final status = response.statusCode;
    if (status == 401 || status == 403) {
      return ImmichException(ImmichErrorKind.unauthorized, 'HTTP $status');
    }
    // A wrong address, or a reverse proxy that never reaches Immich, answers
    // with HTML or a 404 rather than the API's JSON error.
    if (status == 404 || !_looksLikeJson(response)) {
      return ImmichException(ImmichErrorKind.badServer, 'HTTP $status');
    }
    return ImmichException(ImmichErrorKind.server, 'HTTP $status: ${response.body}');
  }

  bool _looksLikeJson(http.Response response) {
    final type = response.headers['content-type'] ?? '';
    if (type.contains('application/json')) {
      return true;
    }
    final body = response.body.trimLeft();
    return body.startsWith('{') || body.startsWith('[');
  }

  Map<String, dynamic>? _decode(String body) {
    try {
      final decoded = jsonDecode(body);
      return decoded is Map<String, dynamic> ? decoded : null;
    } catch (_) {
      return null;
    }
  }
}
