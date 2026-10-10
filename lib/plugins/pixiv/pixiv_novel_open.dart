import 'package:flutter/widgets.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_api.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_models.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_reader_screen.dart';

/// Every way into a novel: its reader, showing what the opener already has.
/// The reader adds the novel to the reading history once it has loaded.
Future<void> openPixivNovel(BuildContext context, PixivNovel novel) =>
    Navigator.push(context, pixivNovelReaderRoute(novel.id, novel: novel));

/// A novel the device only remembers, such as a history entry: the reader
/// fetches its detail, so the header is whole, and says itself when Pixiv no
/// longer has it.
Future<void> openRememberedPixivNovel(BuildContext context, int id) =>
    Navigator.push(context, pixivNovelReaderRoute(id));

/// Opens novel [id] in its reader once Pixiv has handed over its card fields;
/// false when Pixiv could not, so a link can fall back to the browser or say
/// so.
Future<bool> openPixivNovelById(BuildContext context, int id) async {
  final PixivNovel novel;
  try {
    novel = await PixivNovelApi.of(context).detail(id);
  } catch (_) {
    return false;
  }
  if (context.mounted) await openPixivNovel(context, novel);
  return true;
}
