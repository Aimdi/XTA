import 'dart:convert';
import 'dart:io' show HttpException;
import 'dart:math';
import 'dart:typed_data';

import 'package:crypto/crypto.dart';
import 'package:html/dom.dart' as html_dom;
import 'package:html/parser.dart' as html_parser;
import 'package:xta/client/http_client.dart';
import 'package:xta/utils/request_budget.dart';

import 'constants.dart';
import 'cubic_curve.dart';
import 'interpolate.dart';
import 'rotation.dart';
import 'utils.dart';

class ClientTransaction {
  final List<int> _keyBytes;
  final String _animationKey;
  final String _randomKeyword;
  final int _randomNumber;

  ClientTransaction._({
    required this._keyBytes,
    required this._animationKey,
    required this._randomKeyword,
    required this._randomNumber,
  });

  /// Builds a generator from already-derived parts. Tests use this because
  /// [initialize] needs live requests to X and its static bundle host.
  ClientTransaction.forTesting({
    required List<int> keyBytes,
    required String animationKey,
    String randomKeyword = defaultKeyword,
    int randomNumber = additionalRandomNumber,
  })  : _keyBytes = keyBytes,
        _animationKey = animationKey,
        _randomKeyword = randomKeyword,
        _randomNumber = randomNumber;

  /// Fetches x.com and initializes the transaction ID generator.
  static Future<ClientTransaction> initialize({
    String randomKeyword = defaultKeyword,
    int randomNumber = additionalRandomNumber,
  }) async {
    final budget = RequestBudget(const Duration(seconds: 12));
    final (homePageDoc, ondemandUrl) = await _fetchBootstrapPage(budget);
    final ondemandResponse = await getXResponse(ondemandUrl, timeout: budget.remaining);
    if (ondemandResponse.statusCode < 200 || ondemandResponse.statusCode >= 300) {
      throw HttpException('X signing bundle returned HTTP ${ondemandResponse.statusCode}', uri: ondemandUrl);
    }
    final ondemandFileText = ondemandResponse.body;

    final (rowIndex, keyBytesIndices) = _getIndices(ondemandFileText);
    final key = _getKey(homePageDoc);
    final keyBytes = _getKeyBytes(key);
    final animationKey = _computeAnimationKey(
      keyBytes: keyBytes,
      rowIndex: rowIndex,
      keyBytesIndices: keyBytesIndices,
      homePageDoc: homePageDoc,
    );

    return ClientTransaction._(
      keyBytes: keyBytes,
      animationKey: animationKey,
      randomKeyword: randomKeyword,
      randomNumber: randomNumber,
    );
  }

  /// Generates the x-client-transaction-id for the given HTTP method and path.
  String generateTransactionId(String method, String path) {
    final timeNow =
        (DateTime.now().millisecondsSinceEpoch - 1682924400 * 1000) ~/ 1000;
    final timeNowBytes = List.generate(4, (i) => (timeNow >> (i * 8)) & 0xFF);

    final hashInput = '$method!$path!$timeNow$_randomKeyword$_animationKey';
    final hashBytes = sha256.convert(utf8.encode(hashInput)).bytes;

    final randomNum = Random().nextInt(256);
    final bytesArr = [
      ..._keyBytes,
      ...timeNowBytes,
      ...hashBytes.take(16),
      _randomNumber,
    ];
    final out = Uint8List(bytesArr.length + 1);
    out[0] = randomNum;
    for (int i = 0; i < bytesArr.length; i++) {
      out[i + 1] = bytesArr[i] ^ randomNum;
    }

    return base64.encode(out).replaceAll('=', '');
  }

  // --- Private helpers (static, mirroring Python class methods) ---

  static const _bootstrapHeaders = {
    'Accept-Language': 'en-US,en;q=0.9',
    'Cache-Control': 'no-cache',
    'Referer': 'https://x.com',
    'User-Agent':
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36',
    'X-Twitter-Active-User': 'yes',
    'X-Twitter-Client-Language': 'en',
  };

  static Future<(html_dom.Document, Uri)> _fetchBootstrapPage(RequestBudget budget) async {
    // X's logged-out homepage can omit the signer; its public search shell
    // still includes it: iSarabjitDhiman/XClientTransaction#45.
    final pages = [Uri.https('x.com', '/home'), Uri.https('x.com', '/search', {'q': 'AI', 'f': 'live'})];
    for (final uri in pages) {
      final response = await getXResponse(uri, timeout: budget.remaining, headers: _bootstrapHeaders);
      if (uri == pages.first && const [403, 404].contains(response.statusCode)) continue;
      if (response.statusCode < 200 || response.statusCode >= 300) {
        throw HttpException('X transaction bootstrap returned HTTP ${response.statusCode}', uri: uri);
      }
      final doc = html_parser.parse(response.body);
      final bundle = _getOndemandFileUrl(response.body, doc);
      if (bundle != null && _hasBootstrapData(doc)) return (doc, bundle);
    }
    throw const FormatException('X pages did not contain transaction signing data');
  }

  static bool _hasBootstrapData(html_dom.Document doc) {
    final key = doc.querySelector("meta[name='twitter-site-verification']")?.attributes['content'];
    if (key == null || key.isEmpty) return false;
    try {
      if (_getKeyBytes(key).length < 6) return false;
    } on FormatException {
      return false;
    }
    final frames = doc.querySelectorAll('[id^="loading-x-anim"]');
    return frames.length >= 4 &&
        frames.take(4).every((frame) {
          if (frame.children.isEmpty || frame.children[0].children.length < 2) return false;
          final path = frame.children[0].children[1].attributes['d'];
          return path != null && path.length > 9 && path.contains('C');
        });
  }

  static (int, List<int>) _getIndices(String ondemandFileText) {
    final indices = indicesRegex
        .allMatches(ondemandFileText)
        .map((m) => int.parse(m.group(2)!))
        .toList();
    if (indices.isEmpty) throw Exception("Couldn't get KEY_BYTE indices");
    return (indices[0], indices.sublist(1));
  }

  static String _getKey(html_dom.Document doc) {
    final element =
        doc.querySelector("meta[name='twitter-site-verification']");
    if (element == null) {
      throw Exception(
          "Couldn't get [twitter-site-verification] key from the page source");
    }
    return element.attributes['content']!;
  }

  static List<int> _getKeyBytes(String key) => base64.decode(key).toList();

  static Uri? _getOndemandFileUrl(String html, html_dom.Document doc) {
    for (final script in doc.querySelectorAll('script[src]')) {
      final uri = Uri.tryParse(script.attributes['src']!);
      if (uri != null && _isSigningBundle(uri)) return uri;
    }
    final indexMatch = onDemandFileRegex.firstMatch(html);
    if (indexMatch == null) return null;
    final fileIndex = indexMatch.group(1)!;
    final hashRegex = RegExp(
      '''(?:^|[,{])\\s*["']?${RegExp.escape(fileIndex)}["']?\\s*:\\s*["']([a-zA-Z0-9_-]+)["']''',
    );
    final hashMatch = hashRegex.firstMatch(html);
    if (hashMatch == null) return null;
    final filename = hashMatch.group(1)!;
    final uri = Uri.parse(onDemandFileUrlTemplate.replaceAll('{filename}', filename));
    return _isSigningBundle(uri) ? uri : null;
  }

  static bool _isSigningBundle(Uri uri) {
    return uri.scheme == 'https' &&
        uri.host == 'abs.twimg.com' &&
        uri.userInfo.isEmpty &&
        uri.port == 443 &&
        !uri.hasQuery &&
        !uri.hasFragment &&
        RegExp(r'^/responsive-web/client-web/ondemand\.s\.[a-zA-Z0-9_-]+\.js$').hasMatch(uri.path);
  }

  static List<List<int>> _get2dArray(
      List<int> keyBytes, html_dom.Document doc) {
    final frames = doc.querySelectorAll('[id^="loading-x-anim"]');
    final frame = frames[keyBytes[5] % 4];
    final pathElement = frame.children[0].children[1];
    final d = pathElement.attributes['d']!.substring(9);
    return d
        .split('C')
        .map((segment) {
          final cleaned = segment.replaceAll(RegExp(r'[^\d]+'), ' ').trim();
          if (cleaned.isEmpty) return <int>[];
          return cleaned
              .split(RegExp(r'\s+'))
              .where((s) => s.isNotEmpty)
              .map(int.parse)
              .toList();
        })
        .where((row) => row.isNotEmpty)
        .toList();
  }

  static double _solve(
      double value, double minVal, double maxVal, bool rounding) {
    final result = value * (maxVal - minVal) / 255.0 + minVal;
    return rounding ? result.floor().toDouble() : roundTo2(result);
  }

  static String _animate(List<int> frames, double targetTime) {
    final fromColor = [
      frames[0].toDouble(), frames[1].toDouble(),
      frames[2].toDouble(), 1.0,
    ];
    final toColor = [
      frames[3].toDouble(), frames[4].toDouble(),
      frames[5].toDouble(), 1.0,
    ];
    final fromRotation = [0.0];
    final toRotation = [_solve(frames[6].toDouble(), 60.0, 360.0, true)];

    final framesTail = frames.sublist(7);
    final curves = framesTail
        .asMap()
        .entries
        .map((e) => _solve(e.value.toDouble(), isOdd(e.key), 1.0, false))
        .toList();

    final cubic = Cubic(curves);
    final val = cubic.getValue(targetTime);

    var color = interpolate(fromColor, toColor, val);
    color = color.map((v) => v.clamp(0.0, 255.0)).toList();

    final rotation = interpolate(fromRotation, toRotation, val);
    final matrix = convertRotationToMatrix(rotation[0]);

    final strArr = <String>[];
    for (int i = 0; i < color.length - 1; i++) {
      strArr.add(color[i].round().toRadixString(16));
    }
    for (final value in matrix) {
      double rounded = roundTo2(value);
      if (rounded < 0) rounded = -rounded;
      final hexValue = floatToHex(rounded);
      if (hexValue.startsWith('.')) {
        strArr.add('0$hexValue'.toLowerCase());
      } else if (hexValue.isNotEmpty) {
        strArr.add(hexValue);
      } else {
        strArr.add('0');
      }
    }
    strArr.addAll(['0', '0']);

    return strArr.join().replaceAll(RegExp(r'[.\-]'), '');
  }

  static String _computeAnimationKey({
    required List<int> keyBytes,
    required int rowIndex,
    required List<int> keyBytesIndices,
    required html_dom.Document homePageDoc,
  }) {
    const totalTime = 4096;
    final frameRowIndex = keyBytes[rowIndex] % 16;
    final frameTimeProduct = keyBytesIndices
        .fold<int>(1, (acc, idx) => acc * (keyBytes[idx] % 16));
    final frameTime = jsRound(frameTimeProduct / 10.0) * 10;

    final arr = _get2dArray(keyBytes, homePageDoc);
    final frameRow = arr[frameRowIndex];
    final targetTime = frameTime / totalTime;
    return _animate(frameRow, targetTime.toDouble());
  }
}
