import 'dart:async';

import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/plugins/pixiv/pixiv_loads.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_api.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_content.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_models.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_parser.dart';

/// A novel ready to read: its card fields, its text with what the text
/// refers to, and that text as blocks.
class PixivNovelReading {
  final PixivNovel novel;
  final PixivNovelContent content;
  final List<PixivNovelBlock> blocks;

  /// The block each page starts at, for `[jump:N]`.
  final Map<int, int> pageStarts;

  PixivNovelReading({required this.novel, required this.content, required this.blocks})
    : pageStarts = pixivNovelPageStarts(blocks);
}

/// Fetches novel [novelId] to read. [seed] is the novel as the list it was
/// tapped in sent it; a novel opened by its id alone also fetches its detail.
/// Both requests go out together, and the first failure is what is reported.
Future<PixivNovelReading> loadPixivNovelReading(PixivNovelApi api, int novelId, {PixivNovel? seed}) async {
  final novel = seed == null ? api.detail(novelId) : Future.value(seed);
  final content = api.content(novelId);
  await Future.wait<Object>([novel, content]);
  final text = await content;
  return PixivNovelReading(
    novel: await novel,
    content: text,
    blocks: await pixivNovelParse(parsePixivNovelMarkup, text.text),
  );
}

/// Works a novel shows that its page did not carry a picture of, each
/// fetched once; a work that could not be fetched is held as null.
class PixivEmbeddedWorksStore extends Store<Map<int, PixivIllust?>> {
  final Future<PixivIllust> Function(int illustId) fetch;
  final _loads = PixivLoads();
  final _asked = <int>{};

  PixivEmbeddedWorksStore(this.fetch) : super(const {});

  /// Fetches [illustId] unless it was asked for already.
  void need(int illustId) {
    if (!_asked.add(illustId)) return;
    unawaited(_loads.track(_fetch(illustId)));
  }

  Future<void> _fetch(int illustId) async {
    PixivIllust? illust;
    try {
      illust = await fetch(illustId);
    } on Exception {
      illust = null;
    }
    update({...state, illustId: illust});
  }

  /// Lets the store go once the works on their way have landed.
  void destroyWhenSettled() => _loads.destroyAfter([this]);
}
