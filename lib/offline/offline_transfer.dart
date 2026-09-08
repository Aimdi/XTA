import 'dart:async';
import 'dart:io';

import 'package:http/http.dart' as http;
import 'package:xta/offline/offline_models.dart';

/// Accept only raster bytes when embedding images into a sanitized document.
String? offlineImageMime(List<int> bytes) {
  bool prefix(List<int> expected, [int start = 0]) =>
      bytes.length >= start + expected.length &&
      List.generate(expected.length, (i) => bytes[start + i] == expected[i]).every((v) => v);
  if (prefix([137, 80, 78, 71, 13, 10, 26, 10])) return 'image/png';
  if (prefix([255, 216, 255])) return 'image/jpeg';
  if (prefix([71, 73, 70, 56, 55, 97]) || prefix([71, 73, 70, 56, 57, 97])) return 'image/gif';
  if (prefix([82, 73, 70, 70]) && prefix([87, 69, 66, 80], 8)) return 'image/webp';
  if (prefix([102, 116, 121, 112], 4) && (prefix([97, 118, 105, 102], 8) || prefix([97, 118, 105, 115], 8)))
    return 'image/avif';
  return null;
}

String? _mediaMime(List<int> bytes, bool video) {
  if (!video) return offlineImageMime(bytes);
  if (bytes.length >= 12 && String.fromCharCodes(bytes.sublist(4, 8)) == 'ftyp') return 'video/mp4';
  if (bytes.length >= 4 && bytes[0] == 26 && bytes[1] == 69 && bytes[2] == 223 && bytes[3] == 163) return 'video/webm';
  return null;
}

Future<OfflineFile> retainOfflineMedia({
  required http.Client client,
  required OfflineMediaSource source,
  required File target,
  required int limit,
  required bool Function() cancelled,
}) async {
  final uri = Uri.tryParse(source.url);
  if (uri == null || !['http', 'https'].contains(uri.scheme) || uri.host.isEmpty || limit <= 0) {
    throw const FormatException('Unsupported offline media');
  }
  final request = http.Request('GET', uri)..maxRedirects = 3;
  final response = await client.send(request).timeout(const Duration(seconds: 20));
  final length = response.contentLength;
  if (response.statusCode != 200 || (length != null && length > limit)) {
    await response.stream.listen((_) {}).cancel();
    throw const HttpException('Offline media unavailable or too large');
  }
  var bytes = 0;
  final header = <int>[];
  final sink = target.openWrite();
  try {
    await for (final chunk in response.stream.timeout(const Duration(seconds: 20))) {
      if (cancelled() || bytes + chunk.length > limit) throw const FileSystemException('Offline transfer stopped');
      bytes += chunk.length;
      if (header.length < 32) header.addAll(chunk.take(32 - header.length));
      sink.add(chunk);
      // Flush each bounded chunk so the writer cannot buffer a whole video.
      await sink.flush();
    }
    await sink.close();
    final mime = _mediaMime(header, source.video);
    if (bytes == 0 || (length != null && length != bytes) || mime == null || cancelled()) {
      throw const FormatException('Incomplete or unsupported offline media');
    }
    return OfflineFile(name: target.uri.pathSegments.last, url: source.url, mime: mime, bytes: bytes);
  } catch (_) {
    await sink.close();
    if (await target.exists()) await target.delete();
    rethrow;
  }
}
