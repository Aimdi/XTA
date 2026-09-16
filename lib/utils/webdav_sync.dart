import 'dart:convert';
import 'package:crypto/crypto.dart';
import 'package:http/http.dart' as http;
import 'package:xta/utils/local_json_store.dart';

class WebDavConfig {
  final String url, username, password;
  const WebDavConfig({required this.url, required this.username, required this.password});
  bool get isComplete => url.trim().isNotEmpty && username.isNotEmpty && password.isNotEmpty;
  Uri? get uri {
    final parsed = Uri.tryParse(url.trim());
    return parsed != null && parsed.isScheme('https') && parsed.host.isNotEmpty ? parsed : null;
  }

  Map<String, String> get authHeaders => {'authorization': 'Basic ${base64Encode(utf8.encode('$username:$password'))}'};
}

enum WebDavOutcome {
  success,
  notConfigured,
  insecureUrl,
  unauthorized,
  notFound,
  serverError,
  networkError,
  conflict,
  unsafeServer,
}

class WebDavResult {
  final WebDavOutcome outcome;
  final String? body, detail, etag;
  const WebDavResult(this.outcome, {this.body, this.detail, this.etag});
  bool get isSuccess => outcome == WebDavOutcome.success;
}

WebDavOutcome outcomeForStatus(int status) => switch (status) {
  >= 200 && < 300 => WebDavOutcome.success,
  401 || 403 => WebDavOutcome.unauthorized,
  404 || 409 => WebDavOutcome.notFound,
  412 => WebDavOutcome.conflict,
  _ => WebDavOutcome.serverError,
};

String? strongWebDavEtag(String? value) => value != null && RegExp(r'^"[^"\r\n]*"$').hasMatch(value) ? value : null;
const _historyKey = '_xtaWebDavHistory';

class WebDavVersion {
  final String file;
  final DateTime savedAt;
  const WebDavVersion(this.file, this.savedAt);
  Map<String, String> toJson() => {'file': file, 'at': savedAt.toUtc().toIso8601String()};
}

List<WebDavVersion> webDavVersions(String? body) {
  try {
    final raw = jsonDecode(body ?? '');
    if (raw is! Map || raw[_historyKey] is! List) return [];
    final versions = <WebDavVersion>[];
    for (final row in (raw[_historyKey] as List).take(20)) {
      if (row is! Map || row['file'] is! String || row['at'] is! String) continue;
      final date = DateTime.tryParse(row['at']);
      if (date != null && RegExp(r'^\d+-[a-f0-9]{64}\.json$').hasMatch(row['file'])) {
        versions.add(WebDavVersion(row['file'], date));
      }
    }
    return versions;
  } catch (_) {
    return [];
  }
}

/// Conditional writes protect other devices; immutable versions preserve the
/// exact remote document before it is replaced. Baselines are local per target.
class WebDavSync {
  final http.Client client;
  final JsonStore storage;
  final Duration timeout;
  WebDavSync({http.Client? client, JsonStore? storage, this.timeout = const Duration(seconds: 30)})
    : client = client ?? http.Client(),
      storage = storage ?? LocalJsonStore.shared;
  void close() => client.close();
  String _key(WebDavConfig config) =>
      'webdav-baseline:${sha256.convert(utf8.encode('${config.uri}\n${config.username}'))}';
  Uri _versionUri(WebDavConfig config, String file) => config.uri!.replace(path: '${config.uri!.path}.versions/$file');

  WebDavResult? _validate(WebDavConfig config) => !config.isComplete
      ? const WebDavResult(WebDavOutcome.notConfigured)
      : config.uri == null
      ? const WebDavResult(WebDavOutcome.insecureUrl)
      : null;

  Future<WebDavResult> _get(WebDavConfig config, Uri uri) async {
    try {
      final response = await client.get(uri, headers: config.authHeaders).timeout(timeout);
      final outcome = outcomeForStatus(response.statusCode);
      return WebDavResult(
        outcome,
        body: outcome == WebDavOutcome.success ? response.body : null,
        etag: strongWebDavEtag(response.headers['etag']),
        detail: outcome == WebDavOutcome.success ? null : 'HTTP ${response.statusCode}',
      );
    } catch (_) {
      return const WebDavResult(WebDavOutcome.networkError);
    }
  }

  Future<WebDavResult> download(WebDavConfig config) async => _validate(config) ?? await _get(config, config.uri!);

  /// Called only after a confirmed import, never after merely viewing a preview.
  Future<void> acknowledge(WebDavConfig config, WebDavResult result) async {
    if (result.isSuccess && strongWebDavEtag(result.etag) != null) await storage.write(_key(config), result.etag);
  }

  Future<WebDavResult> downloadVersion(WebDavConfig config, WebDavVersion version) async {
    final invalid = _validate(config);
    if (invalid != null) return invalid;
    if (!RegExp(r'^\d+-[a-f0-9]{64}\.json$').hasMatch(version.file)) return const WebDavResult(WebDavOutcome.notFound);
    return _get(config, _versionUri(config, version.file));
  }

  Future<WebDavResult> upload(WebDavConfig config, String body) async {
    final invalid = _validate(config);
    if (invalid != null) return invalid;
    try {
      final data = jsonDecode(body);
      if (data is! Map<String, dynamic>) return const WebDavResult(WebDavOutcome.serverError);
      data.remove(_historyKey);
      final remote = await download(config);
      if (!remote.isSuccess && remote.outcome != WebDavOutcome.notFound) return remote;
      final versions = <WebDavVersion>[];
      if (remote.isSuccess) {
        if (remote.etag == null) return const WebDavResult(WebDavOutcome.unsafeServer);
        if (await storage.read(_key(config)) != remote.etag) return const WebDavResult(WebDavOutcome.conflict);
        final now = DateTime.now().toUtc();
        final file = '${now.microsecondsSinceEpoch}-${sha256.convert(utf8.encode(remote.body!))}.json';
        final archived = await _put(config, _versionUri(config, file), remote.body!, {'if-none-match': '*'});
        if (!archived.isSuccess) return archived;
        versions.add(WebDavVersion(file, now));
        versions.addAll(webDavVersions(remote.body));
      }
      data[_historyKey] = versions.take(20).map((version) => version.toJson()).toList();
      final payload = jsonEncode(data);
      final result = await _put(
        config,
        config.uri!,
        payload,
        remote.isSuccess ? {'if-match': remote.etag!} : {'if-none-match': '*'},
      );
      if (!result.isSuccess) return result;
      // A lost validator must never become permission to overwrite on retry.
      final verified = result.etag != null ? result : await download(config);
      if (!verified.isSuccess || verified.etag == null) return const WebDavResult(WebDavOutcome.unsafeServer);
      if (result.etag == null && verified.body != payload) return const WebDavResult(WebDavOutcome.conflict);
      await acknowledge(config, verified);
      for (final stale in versions.skip(20)) {
        try {
          await client.delete(_versionUri(config, stale.file), headers: config.authHeaders).timeout(timeout);
        } catch (_) {}
      }
      return const WebDavResult(WebDavOutcome.success);
    } catch (_) {
      return const WebDavResult(WebDavOutcome.networkError);
    }
  }

  Future<WebDavResult> _put(WebDavConfig config, Uri uri, String body, Map<String, String> condition) async {
    Future<http.Response> put() => client
        .put(uri, headers: {...config.authHeaders, 'content-type': 'application/json', ...condition}, body: body)
        .timeout(timeout);
    try {
      var response = await put();
      if (response.statusCode == 404 || response.statusCode == 409) {
        final parent = uri.replace(pathSegments: uri.pathSegments.sublist(0, uri.pathSegments.length - 1));
        final request = http.Request('MKCOL', parent)..headers.addAll(config.authHeaders);
        final made = await client.send(request).timeout(timeout);
        await made.stream.drain<void>().timeout(timeout);
        if (made.statusCode != 405 && outcomeForStatus(made.statusCode) != WebDavOutcome.success) {
          return WebDavResult(outcomeForStatus(made.statusCode), detail: 'MKCOL HTTP ${made.statusCode}');
        }
        response = await put();
      }
      return WebDavResult(
        outcomeForStatus(response.statusCode),
        etag: strongWebDavEtag(response.headers['etag']),
        detail: response.statusCode >= 400 ? 'HTTP ${response.statusCode}' : null,
      );
    } catch (_) {
      return const WebDavResult(WebDavOutcome.networkError);
    }
  }
}
