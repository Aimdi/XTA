/// Read-only E-Hentai / ExHentai client (HTML + gdata).
library;

import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/ehviewer/eh_models.dart';
import 'package:xta/plugins/ehviewer/eh_parse.dart';

enum EhErrorKind {
  notConfigured,
  network,
  unauthorized,
  rateLimited,
  notFound,
  badResponse,
  ban,
}

class EhException implements Exception {
  final EhErrorKind kind;
  final String message;

  EhException(this.kind, this.message);

  @override
  String toString() => 'EhException{$kind: $message}';
}

class EhClient {
  final http.Client httpClient;
  final BasePrefService prefs;

  EhClient(this.prefs, {http.Client? httpClient})
    : httpClient = httpClient ?? http.Client();

  static const _timeout = Duration(seconds: 25);
  static const _userAgent =
      'Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 '
      '(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36';
  static const apiUrl = 'https://api.e-hentai.org/api.php';

  bool get useExhentai => prefs.get<bool>(optionPluginEhUseExhentai) == true;

  String get host {
    if (useExhentai && hasCookies) return 'https://exhentai.org';
    return 'https://e-hentai.org';
  }

  String get cookies => (prefs.get<String>(optionPluginEhCookies) ?? '').trim();

  bool get hasCookies => cookies.isNotEmpty;

  Set<EhCategory> get includedCategories {
    final raw = prefs.get<String>(optionPluginEhCategories) ?? '';
    if (raw.trim().isEmpty) return EhCategory.values.toSet();
    try {
      final decoded = jsonDecode(raw);
      if (decoded is List) {
        return {
          for (final name in decoded.whereType<String>())
            ?EhCategory.tryParse(name),
        };
      }
    } catch (_) {}
    return EhCategory.values.toSet();
  }

  Future<EhGalleryPage> popular({String? pageUrl}) =>
      _list(pageUrl ?? '$host/popular');

  Future<EhGalleryPage> frontPage({String? pageUrl}) => _list(
    pageUrl ?? '$host/?f_cats=${EhCategory.excludeMask(includedCategories)}',
  );

  Future<EhGalleryPage> search(
    String query, {
    String? pageUrl,
    Set<EhCategory>? categories,
  }) {
    if (pageUrl != null) return _list(pageUrl);
    final cats = categories ?? includedCategories;
    final uri = Uri.parse(host).replace(
      path: '/',
      queryParameters: {
        'f_search': query.trim(),
        'f_cats': '${EhCategory.excludeMask(cats)}',
        'f_apply': 'Apply Filter',
      },
    );
    return _list(uri.toString());
  }

  Future<EhGalleryPage> _list(String url) async {
    final response = await _get(Uri.parse(url));
    final body = response.body;
    _throwIfBanned(body, url);
    return parseEhGalleryList(body);
  }

  Future<EhGalleryDetail> galleryDetail({
    required int gid,
    required String token,
  }) async {
    final uri = Uri.parse('$host/g/$gid/$token/');
    final response = await _get(uri);
    _throwIfBanned(response.body, uri.toString());
    final detail = parseEhGalleryDetail(response.body, gid: gid, token: token);
    if (detail == null) {
      throw EhException(EhErrorKind.badResponse, 'Could not parse gallery');
    }
    return detail;
  }

  Future<EhGallery?> galleryMeta({
    required int gid,
    required String token,
  }) async {
    final decoded = await _postApi({
      'method': 'gdata',
      'gidlist': [
        [gid, token],
      ],
      'namespace': 1,
    });
    final list = parseEhGdata(decoded);
    return list.isEmpty ? null : list.first;
  }

  Future<EhImagePage> imagePage({
    required int gid,
    required String pageToken,
    required int page,
  }) async {
    final uri = Uri.parse('$host/s/$pageToken/$gid-$page');
    final response = await _get(uri);
    _throwIfBanned(response.body, uri.toString());
    final parsed = parseEhImagePage(response.body, page: page);
    if (parsed == null) {
      throw EhException(EhErrorKind.badResponse, 'Could not parse image page');
    }
    return parsed;
  }

  Future<Object?> _postApi(Map<String, Object?> body) async {
    try {
      final response = await httpClient
          .post(
            Uri.parse(apiUrl),
            headers: {
              'User-Agent': _userAgent,
              'Content-Type': 'application/json',
              'Accept': 'application/json',
              if (hasCookies) 'Cookie': cookies,
            },
            body: jsonEncode(body),
          )
          .timeout(_timeout);
      return _decodeJson(response, Uri.parse(apiUrl));
    } catch (e) {
      if (e is EhException) rethrow;
      throw EhException(EhErrorKind.network, '$e');
    }
  }

  Future<http.Response> _get(Uri uri) async {
    try {
      final response = await httpClient
          .get(
            uri,
            headers: {
              'User-Agent': _userAgent,
              'Accept':
                  'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
              if (hasCookies) 'Cookie': cookies,
            },
          )
          .timeout(_timeout);
      _throwIfHttpError(response, uri);
      return response;
    } catch (e) {
      if (e is EhException) rethrow;
      throw EhException(EhErrorKind.network, '$e');
    }
  }

  void _throwIfHttpError(http.Response response, Uri uri) {
    if (response.statusCode == 401 || response.statusCode == 403) {
      throw EhException(
        EhErrorKind.unauthorized,
        '$uri: ${response.statusCode}',
      );
    }
    if (response.statusCode == 404) {
      throw EhException(EhErrorKind.notFound, '$uri: 404');
    }
    if (response.statusCode == 429) {
      throw EhException(EhErrorKind.rateLimited, '$uri: 429');
    }
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw EhException(
        EhErrorKind.badResponse,
        '$uri: ${response.statusCode}',
      );
    }
  }

  void _throwIfBanned(String body, String url) {
    final lower = body.toLowerCase();
    if (lower.contains('your ip has been temporarily banned') ||
        lower.contains('this ip address has been banned')) {
      throw EhException(EhErrorKind.ban, url);
    }
  }

  Object? _decodeJson(http.Response response, Uri uri) {
    _throwIfHttpError(response, uri);
    try {
      return jsonDecode(response.body);
    } catch (e) {
      throw EhException(EhErrorKind.badResponse, '$uri: invalid JSON ($e)');
    }
  }
}
