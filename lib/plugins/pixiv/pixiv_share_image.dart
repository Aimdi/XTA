import 'dart:io';
import 'dart:typed_data';

import 'package:extended_image/extended_image.dart';
import 'package:flutter/widgets.dart';
import 'package:http/http.dart' as http;
import 'package:path_provider/path_provider.dart';
import 'package:provider/provider.dart';
import 'package:share_plus/share_plus.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';

/// The name and type page [page] of [illust] is shared under, from the file [url] serves.
({String name, String mimeType}) pixivSharedImageName(PixivIllust illust, int page, String url) {
  final file = Uri.tryParse(url)?.pathSegments.lastOrNull ?? '';
  final extension = switch (file.contains('.') ? file.split('.').last.toLowerCase() : '') {
    'png' => 'png',
    'gif' => 'gif',
    'webp' => 'webp',
    _ => 'jpg',
  };
  return (name: '${illust.id}_p$page.$extension', mimeType: extension == 'jpg' ? 'image/jpeg' : 'image/$extension');
}

/// Hands a page's picture to the platform's share sheet, as a file named like Pixiv's own.
/// Screens find it through [of], so a test can watch what would be shared.
class PixivImageSharer {
  final http.Client client;
  final Future<Directory> Function() tempDir;

  const PixivImageSharer(this.client, {this.tempDir = getTemporaryDirectory});

  static PixivImageSharer of(BuildContext context) =>
      context.read<PixivImageSharer?>() ?? PixivImageSharer(context.read<PixivClient>().httpClient);

  /// Shares [url] as page [page] of [illust]; false when the picture could not be had.
  Future<bool> share(PixivIllust illust, int page, String url) async {
    final named = pixivSharedImageName(illust, page, url);
    try {
      final folder = Directory('${(await tempDir()).path}/pixiv_share');
      await folder.create(recursive: true);
      final file = await File('${folder.path}/${named.name}').writeAsBytes(await bytes(url));
      await send(XFile(file.path, mimeType: named.mimeType, name: named.name));
      return true;
    } catch (_) {
      return false;
    }
  }

  /// The picture the image cache already holds for [url], else a fresh download of it.
  Future<Uint8List> bytes(String url) async {
    final cached = await getCachedImageFile(url);
    if (cached != null) return cached.readAsBytes();
    final response = await client.get(Uri.parse(url), headers: pixivImageHeaders);
    if (response.statusCode != 200) throw HttpException('${response.statusCode}', uri: Uri.parse(url));
    return response.bodyBytes;
  }

  Future<void> send(XFile file) => SharePlus.instance.share(ShareParams(files: [file]));
}
