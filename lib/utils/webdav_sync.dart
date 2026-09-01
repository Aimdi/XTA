/// Sync of the app's local state to a WebDAV server the reader controls.
///
/// Everything XTA knows — subscriptions, groups, saved posts, likes — lives
/// only on the device by design. That keeps it private and makes moving to a
/// new phone a manual export/import. This carries the same backup payload to a
/// Nextcloud, ownCloud or any other WebDAV target, chosen and hosted by the
/// reader, so no third party is introduced.
///
/// WebDAV needs no client library: uploading is a `PUT` and downloading a
/// `GET`, both with HTTP basic auth. `MKCOL` creates a missing parent
/// collection, which is the one WebDAV verb involved.
library;

import 'dart:convert';

import 'package:http/http.dart' as http;

class WebDavConfig {
  /// Full URL of the backup document, e.g.
  /// `https://cloud.example.org/remote.php/dav/files/me/xta/backup.json`.
  final String url;
  final String username;
  final String password;

  const WebDavConfig({required this.url, required this.username, required this.password});

  bool get isComplete => url.trim().isNotEmpty && username.isNotEmpty && password.isNotEmpty;

  Uri? get uri {
    final parsed = Uri.tryParse(url.trim());
    // Refuse plaintext: this payload can carry X session tokens.
    return parsed != null && parsed.isScheme('https') ? parsed : null;
  }

  Map<String, String> get authHeaders => {'authorization': 'Basic ${base64Encode(utf8.encode('$username:$password'))}'};
}

enum WebDavOutcome { success, notConfigured, insecureUrl, unauthorized, notFound, serverError, networkError }

class WebDavResult {
  final WebDavOutcome outcome;

  /// Body of the downloaded document, when [outcome] is success on a download.
  final String? body;

  /// Server detail worth showing when something failed.
  final String? detail;

  const WebDavResult(this.outcome, {this.body, this.detail});

  bool get isSuccess => outcome == WebDavOutcome.success;
}

WebDavOutcome outcomeForStatus(int status) => switch (status) {
  >= 200 && < 300 => WebDavOutcome.success,
  401 || 403 => WebDavOutcome.unauthorized,
  404 || 409 => WebDavOutcome.notFound,
  _ => WebDavOutcome.serverError,
};

class WebDavSync {
  final http.Client client;
  final Duration timeout;

  WebDavSync({http.Client? client, this.timeout = const Duration(seconds: 30)}) : client = client ?? http.Client();

  /// Writes [body] to the configured document.
  ///
  /// A 404/409 means the parent collection does not exist yet, which is the
  /// normal state on a first sync — the directory is created and the upload
  /// retried once rather than reported as a failure.
  Future<WebDavResult> upload(WebDavConfig config, String body) async {
    final uri = _validate(config);
    if (uri is WebDavResult) {
      return uri;
    }

    final target = uri as Uri;
    var result = await _put(config, target, body);
    if (result.outcome == WebDavOutcome.notFound) {
      final created = await _makeCollection(config, target);
      if (created.outcome != WebDavOutcome.success) {
        return created;
      }
      result = await _put(config, target, body);
    }
    return result;
  }

  Future<WebDavResult> download(WebDavConfig config) async {
    final uri = _validate(config);
    if (uri is WebDavResult) {
      return uri;
    }

    try {
      final response = await client.get(uri as Uri, headers: config.authHeaders).timeout(timeout);
      final outcome = outcomeForStatus(response.statusCode);
      return WebDavResult(
        outcome,
        body: outcome == WebDavOutcome.success ? response.body : null,
        detail: outcome == WebDavOutcome.success ? null : 'HTTP ${response.statusCode}',
      );
    } catch (e) {
      return WebDavResult(WebDavOutcome.networkError, detail: '$e');
    }
  }

  /// Either the validated [Uri] or the [WebDavResult] explaining why not.
  Object _validate(WebDavConfig config) {
    if (!config.isComplete) {
      return const WebDavResult(WebDavOutcome.notConfigured);
    }
    final uri = config.uri;
    if (uri == null) {
      return const WebDavResult(WebDavOutcome.insecureUrl);
    }
    return uri;
  }

  Future<WebDavResult> _put(WebDavConfig config, Uri uri, String body) async {
    try {
      final response = await client
          .put(uri, headers: {...config.authHeaders, 'content-type': 'application/json'}, body: body)
          .timeout(timeout);
      final outcome = outcomeForStatus(response.statusCode);
      return WebDavResult(outcome, detail: outcome == WebDavOutcome.success ? null : 'HTTP ${response.statusCode}');
    } catch (e) {
      return WebDavResult(WebDavOutcome.networkError, detail: '$e');
    }
  }

  Future<WebDavResult> _makeCollection(WebDavConfig config, Uri target) async {
    final parent = target.replace(pathSegments: target.pathSegments.sublist(0, target.pathSegments.length - 1));
    try {
      final request = http.Request('MKCOL', parent)..headers.addAll(config.authHeaders);
      final response = await http.Response.fromStream(await client.send(request)).timeout(timeout);
      // 405 means it already exists, which is exactly what we wanted.
      final ok = response.statusCode == 405 || outcomeForStatus(response.statusCode) == WebDavOutcome.success;
      return ok
          ? const WebDavResult(WebDavOutcome.success)
          : WebDavResult(outcomeForStatus(response.statusCode), detail: 'MKCOL HTTP ${response.statusCode}');
    } catch (e) {
      return WebDavResult(WebDavOutcome.networkError, detail: '$e');
    }
  }
}
