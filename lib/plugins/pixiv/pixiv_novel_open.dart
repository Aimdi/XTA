import 'package:flutter/widgets.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_models.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_reader_screen.dart';

/// Every way into a novel: its reader, showing what the opener already has.
Future<void> openPixivNovel(BuildContext context, PixivNovel novel) =>
    Navigator.push(context, pixivNovelReaderRoute(novel.id, novel: novel));

/// A novel known by its id alone; the reader fetches its detail.
Future<void> openPixivNovelById(BuildContext context, int id) => Navigator.push(context, pixivNovelReaderRoute(id));
