/// The only network code of web sources: GET requests, over HTTPS, to the
/// sites a source lists, within the limits of docs/specs/web-sources.md.
library;

import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:xta/plugins/websources/web_source_definition.dart';

const webSourceMaxPageBytes = 4 * 1024 * 1024;
const webSourceRequestTimeout = Duration(seconds: 20);
const webSourceRequestSpacing = Duration(seconds: 1);
const webSourceUserAgent = 'XTA web sources';
const _maxRedirects = 3;

enum WebSourceFailureKind {
  /// The address is not HTTPS, or not on a site the source lists.
  blocked,

  /// The site answered with something other than the page.
  status,

  /// The page is larger than a source may read.
  tooLarge,

  /// No answer: no connection, or not in time.
  unreachable,
}

class WebSourceFailure implements Exception {
  final WebSourceFailureKind kind;
  final Uri uri;
  final int? status;

  const WebSourceFailure(this.kind, this.uri, {this.status});

  @override
  String toString() => 'WebSourceFailure(${kind.name}, $uri${status == null ? '' : ', $status'})';
}

class WebSourceClient {
  final http.Client _client;
  final Duration spacing;
  final Map<String, Future<void>> _queues = {};

  WebSourceClient({http.Client? client, this.spacing = webSourceRequestSpacing}) : _client = client ?? http.Client();

  /// The page at [uri] for [source], one request at a time per source with
  /// [spacing] between them.
  Future<String> page(WebSourceDefinition source, Uri uri, WebSourcePageType type) =>
      _inTurn(source.id, () => _fetch(uri, type, allowed: source.allows, maxBytes: webSourceMaxPageBytes));

  /// A source file or the published list, from any HTTPS address: where it
  /// is published is the reader's choice, not the source's.
  Future<String> file(Uri uri) =>
      _fetch(uri, WebSourcePageType.json, allowed: _anyHttps, maxBytes: webSourceMaxFileBytes + 1);

  Future<T> _inTurn<T>(String key, Future<T> Function() request) {
    final result = (_queues[key] ?? Future<void>.value()).then((_) => request());
    _queues[key] = result.then<void>((_) {}, onError: (_) {}).then((_) => Future<void>.delayed(spacing));
    return result;
  }

  Future<String> _fetch(
    Uri start,
    WebSourcePageType type, {
    required bool Function(Uri) allowed,
    required int maxBytes,
  }) async {
    var uri = start;
    for (var hop = 0; hop <= _maxRedirects; hop++) {
      if (!allowed(uri)) throw WebSourceFailure(WebSourceFailureKind.blocked, uri);
      final response = await _send(uri, type);
      final location = response.headers['location'];
      if (_redirects.contains(response.statusCode) && location != null) {
        await response.stream.drain<void>();
        uri = uri.resolve(location);
        continue;
      }
      if (response.statusCode != 200) {
        await response.stream.drain<void>();
        throw WebSourceFailure(WebSourceFailureKind.status, uri, status: response.statusCode);
      }
      return utf8.decode(await _bounded(response, uri, maxBytes), allowMalformed: true);
    }
    throw WebSourceFailure(WebSourceFailureKind.blocked, uri);
  }

  Future<http.StreamedResponse> _send(Uri uri, WebSourcePageType type) async {
    final request = http.Request('GET', uri)
      ..followRedirects = false
      ..headers.addAll({'User-Agent': webSourceUserAgent, 'Accept': _accept[type]!});
    try {
      return await _client.send(request).timeout(webSourceRequestTimeout);
    } on Exception {
      // No connection, a refused handshake, no answer in time: all the same
      // to the reader, and none of them a broken source.
      throw WebSourceFailure(WebSourceFailureKind.unreachable, uri);
    }
  }

  static Future<List<int>> _bounded(http.StreamedResponse response, Uri uri, int maxBytes) async {
    if ((response.contentLength ?? 0) > maxBytes) throw WebSourceFailure(WebSourceFailureKind.tooLarge, uri);
    try {
      return await _collect(response.stream, uri, maxBytes).timeout(webSourceRequestTimeout);
    } on WebSourceFailure {
      rethrow;
    } on Exception {
      throw WebSourceFailure(WebSourceFailureKind.unreachable, uri);
    }
  }

  static Future<List<int>> _collect(Stream<List<int>> stream, Uri uri, int maxBytes) async {
    final bytes = <int>[];
    await for (final chunk in stream) {
      bytes.addAll(chunk);
      if (bytes.length > maxBytes) throw WebSourceFailure(WebSourceFailureKind.tooLarge, uri);
    }
    return bytes;
  }
}

const _redirects = {301, 302, 303, 307, 308};

const _accept = {
  WebSourcePageType.json: 'application/json',
  WebSourcePageType.html: 'text/html,application/xhtml+xml',
  WebSourcePageType.rss: 'application/rss+xml, application/atom+xml, application/xml, text/xml',
};

bool _anyHttps(Uri uri) => uri.isScheme('https') && uri.host.isNotEmpty && uri.userInfo.isEmpty;
