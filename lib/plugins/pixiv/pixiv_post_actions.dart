import 'package:flutter/material.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/plugin_link_post.dart';

void showPixivPostActions(BuildContext context, PixivIllust illust) {
  showPluginLinkPostActions(
    context,
    source: 'pixiv',
    url: illust.url,
    author: illust.userName,
    text: [illust.title, illust.caption].where((value) => value.trim().isNotEmpty).join('\n\n'),
    images: illust.viewerUrls,
  );
}
