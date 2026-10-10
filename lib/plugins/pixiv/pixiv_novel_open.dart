import 'package:flutter/widgets.dart';
import 'package:xta/plugins/pixiv/pixiv_history_store.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_api.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_models.dart';
import 'package:xta/utils/urls.dart';

/// Every way into a novel. It joins the reading history, then opens the way
/// the novel's page does, which is the browser until the novel reader takes
/// over these two functions.
Future<void> openPixivNovel(BuildContext context, PixivNovel novel) {
  recordPixivNovelVisit(context, novel);
  return openUri(context, novel.url);
}

/// Opens novel [id] once Pixiv has handed over its card fields, which the
/// history keeps; false when Pixiv could not, so a link can fall back to the
/// browser or say so.
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
