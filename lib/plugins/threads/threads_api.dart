import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:xta/plugins/threads/threads_models.dart';
import 'package:xta/utils/json.dart';

/// Why an Xy request could not be served.
enum ThreadsApiErrorKind {
  /// No server address or no token has been set.
  notConfigured,

  /// The server did not answer, or did not answer with JSON.
  unreachable,

  /// The token was missing, wrong, or has been rotated.
  unauthorized,

  /// No such profile — or one Threads will not serve to the server.
  notFound,

  /// The server answered with an error of its own, which it explained.
  upstream,
}

/// An Xy failure, carrying what the server said rather than a generic message.
///
/// The server reports `{error, message, upstream, details}` alongside a
/// non-200; [message] is the sentence written for a person, so that is what the
/// screens show.
class ThreadsApiException implements Exception {
  final ThreadsApiErrorKind kind;

  /// The server's own explanation, when it gave one.
  final String? message;

  /// Which upstream it was talking to when it failed, for diagnostics only.
  final String? upstream;

  final String detail;

  ThreadsApiException(this.kind, {this.message, this.upstream, this.detail = ''});

  @override
  String toString() => 'ThreadsApiException{$kind: ${message ?? detail}${upstream == null ? '' : ' ($upstream)'}}';
}

/// The reader's own Xy server: the profile half of Threads.
///
/// Nothing here talks to Meta. The server holds the doc ids, the cookies and
/// the geography that make a guest profile lookup work at all, and this only
/// has to ask it and read JSON.
class ThreadsApi {
  final http.Client httpClient;

  ThreadsApi({http.Client? httpClient}) : httpClient = httpClient ?? http.Client();

  static const _timeout = Duration(seconds: 20);

  static Uri endpoint(String base, String path) {
    final root = base.trim().replaceAll(RegExp(r'/+$'), '');
    return Uri.parse('$root$path');
  }

  /// Reads the server's error envelope, whatever else came back with it.
  static ThreadsApiException _errorFrom(int status, String body, String detail) {
    final kind = switch (status) {
      401 || 403 => ThreadsApiErrorKind.unauthorized,
      404 => ThreadsApiErrorKind.notFound,
      _ => ThreadsApiErrorKind.upstream,
    };

    Json envelope = const Json(null);
    try {
      envelope = Json(jsonDecode(body));
    } catch (_) {
      // A server that fell over without JSON still has a status worth naming.
    }

    return ThreadsApiException(
      kind,
      message: envelope['message'].string,
      upstream: envelope['upstream'].string,
      detail: detail,
    );
  }

  Future<Json> _get(String base, String token, String path, {bool authenticated = true}) async {
    if (base.trim().isEmpty || (authenticated && token.trim().isEmpty)) {
      throw ThreadsApiException(ThreadsApiErrorKind.notConfigured, detail: path);
    }

    final uri = endpoint(base, path);
    final http.Response response;
    try {
      response = await httpClient
          .get(
            uri,
            headers: {'Accept': 'application/json', if (authenticated) 'Authorization': 'Bearer ${token.trim()}'},
          )
          .timeout(_timeout);
    } catch (e) {
      throw ThreadsApiException(ThreadsApiErrorKind.unreachable, detail: '$uri: $e');
    }

    final body = utf8.decode(response.bodyBytes, allowMalformed: true);
    if (response.statusCode != 200) {
      throw _errorFrom(response.statusCode, body, '$uri: ${response.statusCode}');
    }

    try {
      return Json(jsonDecode(body));
    } catch (e) {
      throw ThreadsApiException(ThreadsApiErrorKind.unreachable, detail: '$uri: $e');
    }
  }

  /// Whether the server is up. The one endpoint that takes no token, so it
  /// answers "is the address right" separately from "is the token right".
  Future<bool> health(String base) async {
    final result = await _get(base, '', '/health', authenticated: false);
    return result['ok'].boolean ?? false;
  }

  Future<ThreadsProfile> profile(String base, String token, String username) async {
    final result = await _get(base, token, '/profile/${Uri.encodeComponent(username)}');
    return ThreadsProfile.fromJson(result.raw);
  }

  Future<String?> userId(String base, String token, String username) async {
    final result = await _get(base, token, '/user-id/${Uri.encodeComponent(username)}');
    return result['user_id'].string;
  }

  /// The numeric id behind a shortcode or a post URL.
  Future<String?> postId(String base, String token, String shortcodeOrUrl) async {
    final result = await _get(base, token, '/post-id/${Uri.encodeComponent(shortcodeOrUrl)}');
    return result['post_id'].string;
  }

  Future<String?> threadId(String base, String token, String postId) async {
    final result = await _get(base, token, '/thread-id/${Uri.encodeComponent(postId)}');
    return result['thread_id'].string;
  }
}
