/// On-disk files for [LocalPost] media. Notes never upload these to X.
library;

import 'dart:convert';
import 'dart:io';

import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:xta/database/entities.dart';

Future<Directory> localPostMediaDir(String postId) async {
  final docs = await getApplicationDocumentsDirectory();
  return Directory(p.join(docs.path, 'local_post_media', postId));
}

Future<File> localPostMediaFile(String postId, String mediaId) async {
  final dir = await localPostMediaDir(postId);
  return File(p.join(dir.path, mediaId));
}

Future<void> writeLocalPostMediaBytes({
  required String postId,
  required String mediaId,
  required List<int> bytes,
}) async {
  final file = await localPostMediaFile(postId, mediaId);
  await file.parent.create(recursive: true);
  await file.writeAsBytes(bytes, flush: true);
}

Future<void> deleteLocalPostMediaDir(String postId) async {
  final dir = await localPostMediaDir(postId);
  if (await dir.exists()) {
    await dir.delete(recursive: true);
  }
}

Future<void> deleteRemovedLocalPostMedia(
  String postId,
  List<LocalPostMedia> keep,
) async {
  final dir = await localPostMediaDir(postId);
  if (!await dir.exists()) {
    return;
  }
  final keepIds = {for (final item in keep) item.id};
  await for (final entity in dir.list()) {
    if (entity is File && !keepIds.contains(p.basename(entity.path))) {
      await entity.delete();
    }
  }
}

Future<LocalPost> hydrateLocalPostMediaData(LocalPost post) async {
  if (post.media.isEmpty) {
    return post;
  }
  final next = <LocalPostMedia>[];
  for (final item in post.media) {
    if (item.data != null) {
      next.add(item);
      continue;
    }
    final file = await localPostMediaFile(post.id, item.id);
    if (!await file.exists()) {
      next.add(item);
      continue;
    }
    final bytes = await file.readAsBytes();
    next.add(item.withData(base64Encode(bytes)));
  }
  return post.copyWith(media: next);
}

Future<void> materializeLocalPostMedia(LocalPost post) async {
  for (final item in post.media) {
    if (item.data == null || item.data!.isEmpty) {
      continue;
    }
    try {
      await writeLocalPostMediaBytes(
        postId: post.id,
        mediaId: item.id,
        bytes: base64Decode(item.data!),
      );
    } catch (_) {
      // A corrupt backup entry must not block the rest of the import.
    }
  }
}
