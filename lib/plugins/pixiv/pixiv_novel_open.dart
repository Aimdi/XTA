import 'package:flutter/widgets.dart';
import 'package:xta/plugins/pixiv/pixiv_link_open.dart';
import 'package:xta/plugins/pixiv/pixiv_links.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_models.dart';

/// Every way into a novel. It opens the way a novel link does, which is the
/// browser until the novel reader routes those links.
Future<void> openPixivNovel(BuildContext context, PixivNovel novel) => openPixivNovelById(context, novel.id);

Future<void> openPixivNovelById(BuildContext context, int id) => openPixivLinkRef(context, PixivNovelLinkRef(id));
