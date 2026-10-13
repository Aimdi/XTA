import 'dart:io';

import 'package:extended_image/extended_image.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:path/path.dart' as path;
import 'package:path_provider/path_provider.dart';

/// How long a downloaded picture stays in the image disk cache, which X and
/// every plugin share. Without an age the cache only ever grew.
const imageDiskCacheMaxAge = Duration(days: 30);

/// Deletes pictures cached longer ago than [imageDiskCacheMaxAge]. Run once
/// per launch, in the background.
///
/// Goes by when each file was written: the image library's own age check
/// reads the time a file's metadata last changed, which more than a write
/// resets.
Future<void> trimImageDiskCache() async {
  try {
    final folder = Directory(path.join((await getTemporaryDirectory()).path, cacheImageFolderName));
    if (!await folder.exists()) return;
    final cutoff = DateTime.now().subtract(imageDiskCacheMaxAge);
    await for (final entity in folder.list()) {
      if (entity is File && (await entity.lastModified()).isBefore(cutoff)) await entity.delete();
    }
  } on Exception {
    // A cache that cannot be listed is trimmed on the next launch.
  }
}

/// How many bytes the image disk cache holds; null until measured.
class ImageDiskCacheStore extends Store<int?> {
  final Future<int> Function() _measure;
  final Future<void> Function() _empty;

  ImageDiskCacheStore({Future<int> Function()? measure, Future<void> Function()? empty})
    : _measure = measure ?? getCachedSizeBytes,
      _empty = empty ?? _emptyImageCaches,
      super(null);

  Future<void> load() => execute(_measure, delay: Duration.zero);

  /// Empties the disk cache and the pictures decoded in memory.
  Future<void> clear() async {
    await _empty();
    await load();
  }
}

Future<void> _emptyImageCaches() async {
  await clearDiskCachedImages();
  clearMemoryImageCache();
}
