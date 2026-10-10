import 'dart:convert';
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/widgets.dart';
import 'package:html/dom.dart' as dom;
import 'package:html/parser.dart' as html_parser;
import 'package:http/http.dart' as http;
import 'package:provider/provider.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/utils/json.dart';

/// One SauceNAO match that points at a Pixiv work.
@immutable
class PixivSauceMatch {
  final int illustId;

  /// How alike SauceNAO judged the images, in percent.
  final double? similarity;
  final String title;
  final String author;

  const PixivSauceMatch({required this.illustId, this.similarity, this.title = '', this.author = ''});
}

/// The Pixiv work a link names, from `artworks/<id>` or `illust_id=<id>`;
/// null for any other site.
int? pixivIdFromSauceLink(String link) {
  final uri = Uri.tryParse(link.trim());
  final host = uri?.host.toLowerCase() ?? '';
  if (uri == null || !(host == 'pixiv.net' || host.endsWith('.pixiv.net'))) return null;
  final segments = uri.pathSegments;
  final at = segments.indexOf('artworks');
  final digits = at >= 0 && at + 1 < segments.length ? segments[at + 1] : uri.queryParameters['illust_id'];
  final id = int.tryParse(digits ?? '');
  return id != null && id > 0 ? id : null;
}

/// SauceNAO's answer, HTML from the public page or JSON from its API, as the
/// Pixiv works it found, best match first and each work once.
List<PixivSauceMatch> parsePixivSauceNao(String body) {
  final text = body.trimLeft();
  if (text.startsWith('{')) {
    try {
      return _fromJson(jsonDecode(text));
    } on FormatException {
      return const [];
    }
  }
  return _fromHtml(text);
}

List<PixivSauceMatch> _fromJson(Object? json) => _onceEach([
  for (final result in Json(json)['results'].list)
    if (_jsonId(result['data']) case final id?)
      PixivSauceMatch(
        illustId: id,
        similarity: result['header']['similarity'].number,
        title: result['data']['title'].string?.trim() ?? '',
        author: (result['data']['member_name'].string ?? result['data']['author_name'].string ?? '').trim(),
      ),
]);

int? _jsonId(Json data) {
  final direct = data['pixiv_id'].integer;
  if (direct != null && direct > 0) return direct;
  return data['ext_urls'].list.map((url) => pixivIdFromSauceLink(url.string ?? '')).nonNulls.firstOrNull;
}

List<PixivSauceMatch> _fromHtml(String html) {
  final document = html_parser.parse(html);
  final matches = [
    for (final block in document.querySelectorAll('.result'))
      if (_linkedId(block) case final id?)
        PixivSauceMatch(
          illustId: id,
          similarity: _percent(block.querySelector('.resultsimilarityinfo')?.text),
          title: block.querySelector('.resulttitle')?.text.trim() ?? '',
          author: _author(block),
        ),
  ];
  if (matches.isNotEmpty) return _onceEach(matches);
  return _onceEach([
    for (final anchor in document.querySelectorAll('a[href]'))
      if (pixivIdFromSauceLink(anchor.attributes['href'] ?? '') case final id?) PixivSauceMatch(illustId: id),
  ]);
}

int? _linkedId(dom.Element block) => block
    .querySelectorAll('a[href]')
    .map((anchor) => pixivIdFromSauceLink(anchor.attributes['href'] ?? ''))
    .nonNulls
    .firstOrNull;

String _author(dom.Element block) {
  for (final anchor in block.querySelectorAll('a[href]')) {
    final href = anchor.attributes['href'] ?? '';
    if (href.contains('member.php') || href.contains('/users/')) return anchor.text.trim();
  }
  return '';
}

double? _percent(String? text) => double.tryParse((text ?? '').replaceAll('%', '').trim());

List<PixivSauceMatch> _onceEach(List<PixivSauceMatch> matches) {
  final seen = <int>{};
  return [
    for (final match in matches)
      if (seen.add(match.illustId)) match,
  ];
}

/// [bytes] decoded and saved again as a PNG no wider than [maxWidth], so a
/// phone photo uploads in seconds and any format the device reads is sent in
/// one SauceNAO takes.
Future<Uint8List> pixivSauceImage(Uint8List bytes, {int maxWidth = 1000}) async {
  final buffer = await ui.ImmutableBuffer.fromUint8List(bytes);
  final descriptor = await ui.ImageDescriptor.encoded(buffer);
  final codec = await descriptor.instantiateCodec(targetWidth: descriptor.width > maxWidth ? maxWidth : null);
  try {
    final frame = await codec.getNextFrame();
    final data = await frame.image.toByteData(format: ui.ImageByteFormat.png);
    frame.image.dispose();
    if (data == null) throw const FormatException('image could not be encoded');
    return data.buffer.asUint8List();
  } finally {
    codec.dispose();
    descriptor.dispose();
    buffer.dispose();
  }
}

/// SauceNAO's reverse image search. The image goes to saucenao.com and
/// nowhere else; no Pixiv token travels with it.
class PixivSauceNaoApi {
  final http.Client httpClient;

  /// How long the whole exchange may take, upload included.
  final Duration timeout;

  const PixivSauceNaoApi(this.httpClient, {this.timeout = const Duration(seconds: 45)});

  static final endpoint = Uri.parse('https://saucenao.com/search.php');

  /// A test's fake when one is provided, else one over the Pixiv client's HTTP client.
  static PixivSauceNaoApi of(BuildContext context) =>
      context.read<PixivSauceNaoApi?>() ?? PixivSauceNaoApi(context.read<PixivClient>().httpClient);

  Future<List<PixivSauceMatch>> search(Uint8List png) async {
    final request = http.MultipartRequest('POST', endpoint)
      ..files.add(http.MultipartFile.fromBytes('file', png, filename: 'image.png'));
    final http.Response response;
    try {
      response = await _send(request).timeout(timeout);
    } catch (e) {
      throw PixivException(PixivErrorKind.network, 'saucenao: $e');
    }
    if (response.statusCode == 429) throw PixivException(PixivErrorKind.rateLimited, 'saucenao: 429');
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw PixivException(PixivErrorKind.badResponse, 'saucenao: ${response.statusCode}');
    }
    return parsePixivSauceNao(utf8.decode(response.bodyBytes, allowMalformed: true));
  }

  Future<http.Response> _send(http.BaseRequest request) async =>
      http.Response.fromStream(await httpClient.send(request));
}
