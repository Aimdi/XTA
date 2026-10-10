import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/plugins/pixiv/pixiv_grid.dart';
import 'package:xta/plugins/pixiv/pixiv_loads.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_card.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_models.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_store.dart';
import 'package:xta/plugins/pixiv/pixiv_paged_feed.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:xta/plugins/plugin_feed_skeleton.dart';

/// Novel cards between dividers, one sliver. A mute made while it is shown
/// hides its novels at once.
class PixivNovelSliver<T> extends StatelessWidget {
  final List<T> items;
  final PixivNovel Function(T item) novelOf;
  final Widget Function(T item) card;

  /// The reader's mutes as this list applies them; all of them when null.
  final PixivMuteView? mutes;

  const PixivNovelSliver({super.key, required this.items, required this.novelOf, required this.card, this.mutes});

  @override
  Widget build(BuildContext context) => ScopedBuilder<PixivMuteStore, PixivMuteState>(
    store: context.read<PixivMuteStore>(),
    onState: (context, mute) {
      final shown = (mutes?.call(mute) ?? mute).filterNovelsOf(items, novelOf);
      return SliverList.separated(
        itemCount: shown.length,
        separatorBuilder: (_, _) => const Divider(height: 1),
        itemBuilder: (context, index) => card(shown[index]),
      );
    },
  );
}

PixivNovel _itself(PixivNovel novel) => novel;

/// A paged novel list as a section shows it: skeleton first, kept through
/// soft refreshes and failed appends, retry when empty or failed, and the
/// next page asked for before the end.
class PixivNovelFeed extends StatelessWidget {
  final PixivPagedListStore<PixivNovel> store;
  final String emptyMessage;
  final ScrollController? scrollController;

  /// The reader's mutes as this list applies them; [store] filters its pages the same way.
  final PixivMuteView? mutes;

  const PixivNovelFeed({super.key, required this.store, required this.emptyMessage, this.scrollController, this.mutes});

  @override
  Widget build(BuildContext context) => PixivPagedFeed<PixivNovel>(
    store: store,
    emptyMessage: emptyMessage,
    emptyIcon: Icons.menu_book_outlined,
    placeholder: const PluginFeedSkeleton(count: 4),
    padding: const EdgeInsets.symmetric(vertical: 4),
    scrollController: scrollController,
    sliver: (context, novels) => PixivNovelSliver<PixivNovel>(
      items: novels,
      novelOf: _itself,
      card: (novel) => PixivNovelCard(novel: novel),
      mutes: mutes,
    ),
  );
}

/// A novel list a screen part owns, such as a profile tab: it loads when
/// first shown and is let go, once its loads settle, with the part.
class PixivOwnedNovelFeed extends StatelessWidget {
  final PixivPageLoader<PixivNovel> loader;
  final String emptyMessage;
  final PixivMuteView? mutes;

  const PixivOwnedNovelFeed({super.key, required this.loader, required this.emptyMessage, this.mutes});

  @override
  Widget build(BuildContext context) => PixivOwnedFeed<PixivTrackedNovelStore>(
    create: (context) {
      final mute = context.read<PixivMuteStore>();
      PixivMuteState shown() => mutes?.call(mute.state) ?? mute.state;
      return PixivTrackedNovelStore(loader, filter: (novels) => shown().filterNovels(novels));
    },
    feed: (novels) => PixivNovelFeed(store: novels, emptyMessage: emptyMessage, mutes: mutes),
  );
}
