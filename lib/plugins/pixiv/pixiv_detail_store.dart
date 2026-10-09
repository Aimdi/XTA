import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';

/// One work as its detail shows it: the copy the list already had at once,
/// Pixiv's full record once it arrives. A failed fetch keeps the list's copy.
class PixivIllustDetailStore extends Store<PixivIllust> {
  final PixivClient client;

  PixivIllustDetailStore(this.client, super.seed);

  Future<void> load() => execute(() => client.illustDetail(state.id));
}

/// The works Pixiv relates to [seed], paged like any other list.
PixivIllustListStore pixivRelatedStore(PixivClient client, PixivIllust seed, {PixivIllustListFilter? filter}) =>
    PixivIllustListStore(
      ({nextUrl}) => client.related(
        seed.id,
        nextUrl: nextUrl,
        includeR18: pixivRelatedIncludeR18(seedIsR18: seed.isR18, showR18: client.showR18),
      ),
      filter: filter,
    );
