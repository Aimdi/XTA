import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_grid.dart';
import 'package:xta/plugins/pixiv/pixiv_loads.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_api.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_list.dart';
import 'package:xta/plugins/pixiv/pixiv_segmented_switch.dart';
import 'package:xta/plugins/pixiv/pixiv_social_api.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:xta/plugins/pixiv/pixiv_user_info.dart';
import 'package:xta/plugins/pixiv/pixiv_user_list_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_user_profile.dart';
import 'package:xta/plugins/pixiv/pixiv_view_state.dart';
import 'package:xta/plugins/plugin_view_store.dart';

/// One tab of a profile. A feature adds its tab to [pixivProfileTabs] rather
/// than to the screen; [offeredFor] hides it on profiles it has nothing for.
class PixivProfileTab {
  /// Stable name, for [PixivUserScreen.initialTab]; the tab is keyed `pixiv-profile-tab-<id>`.
  final String id;
  final String Function(L10n l10n) label;
  final Widget Function(PixivProfileScope scope) build;
  final bool Function(PixivProfileScope scope) offeredFor;

  const PixivProfileTab({required this.id, required this.label, required this.build, this.offeredFor = _always});
}

bool _always(PixivProfileScope _) => true;

final pixivProfileTabs = <PixivProfileTab>[
  PixivProfileTab(
    id: 'works',
    label: (l10n) => l10n.plugin_pixiv_profile_works,
    build: (scope) => PixivProfileWorks(profile: scope.profile),
  ),
  PixivProfileTab(
    id: 'novels',
    label: (l10n) => l10n.plugin_pixiv_profile_novels,
    build: (scope) => PixivProfileNovels(userId: scope.profile.id),
    offeredFor: (scope) => scope.own || scope.profile.totalNovels > 0,
  ),
  PixivProfileTab(
    id: 'bookmarks',
    label: (l10n) => l10n.plugin_pixiv_tab_bookmarks,
    build: (scope) => PixivProfileBookmarks(userId: scope.profile.id),
  ),
  PixivProfileTab(
    id: 'following',
    label: (l10n) => l10n.plugin_pixiv_profile_following,
    build: (scope) => PixivUserList(kind: PixivUserListKind.following, userId: scope.profile.id),
  ),
  PixivProfileTab(
    id: 'info',
    label: (l10n) => l10n.plugin_pixiv_profile_info,
    build: (scope) => PixivProfileInfo(profile: scope.profile),
  ),
];

List<PixivProfileTab> pixivProfileTabsFor(PixivProfileScope scope) => [
  for (final tab in pixivProfileTabs)
    if (tab.offeredFor(scope)) tab,
];

/// [mute] without the author mute on [creatorId]: a profile shows its own
/// creator's works, which is what Show anyway on a muted creator asks for.
/// Muted tags and works stay hidden.
PixivMuteState pixivMutesShowing(PixivMuteState mute, int creatorId) =>
    mute.authorIds.contains(creatorId) ? mute.copyWith(authorIds: {...mute.authorIds}..remove(creatorId)) : mute;

/// A works grid a profile tab owns: it loads when the tab is first shown and
/// is let go with the tab.
class PixivProfileFeed extends StatelessWidget {
  final PixivIllustPageLoader loader;
  final String emptyMessage;

  /// Whose profile this is; see [pixivMutesShowing].
  final int creatorId;

  const PixivProfileFeed({super.key, required this.loader, required this.emptyMessage, required this.creatorId});

  PixivMuteState _mutes(PixivMuteState mute) => pixivMutesShowing(mute, creatorId);

  @override
  Widget build(BuildContext context) => PixivOwnedFeed<PixivTrackedIllustStore>(
    create: (context) {
      final mute = context.read<PixivMuteStore>();
      return PixivTrackedIllustStore(loader, filter: (illusts) => _mutes(mute.state).filter(illusts));
    },
    feed: (works) => PixivIllustFeed(store: works, emptyMessage: emptyMessage, mutes: _mutes),
  );
}

/// One of a few lists of the creator's under a switch between them, such as
/// a tab's illustrations and manga. Without [showSwitch] the first choice
/// shows alone. Each list keeps its own scroll position and store.
class PixivSwitchedList<T> extends StatefulWidget {
  final T initial;
  final bool showSwitch;
  final Widget Function(T selected, ValueChanged<T> onSelected) switcher;
  final Widget Function(BuildContext context, T value) list;

  const PixivSwitchedList({
    super.key,
    required this.initial,
    required this.switcher,
    required this.list,
    this.showSwitch = true,
  });

  @override
  State<PixivSwitchedList<T>> createState() => _PixivSwitchedListState<T>();
}

class _PixivSwitchedListState<T> extends State<PixivSwitchedList<T>> {
  late final PluginViewStore<T> _value = PluginViewStore(widget.initial);

  @override
  void dispose() {
    _value.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => ScopedBuilder<PluginViewStore<T>, T>(
    store: _value,
    onState: (context, value) => Column(
      children: [
        if (widget.showSwitch) widget.switcher(value, _value.select),
        Expanded(
          child: KeyedSubtree(key: ValueKey(value), child: widget.list(context, value)),
        ),
      ],
    ),
  );
}

/// The Works tab: illustrations or manga, starting on whichever the creator
/// has more of, with a switch when they have both.
class PixivProfileWorks extends StatelessWidget {
  final PixivUserProfile profile;

  const PixivProfileWorks({super.key, required this.profile});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return PixivSwitchedList<PixivWorkType>(
      initial: profile.defaultWorkType,
      showSwitch: profile.hasBothWorkTypes,
      switcher: (selected, onSelected) => PixivSegmentedSwitch<PixivWorkType>(
        key: const ValueKey('pixiv-profile-work-type'),
        values: PixivWorkType.values,
        label: (value) => switch (value) {
          PixivWorkType.illust => l10n.plugin_pixiv_profile_illusts,
          PixivWorkType.manga => l10n.plugin_pixiv_profile_manga,
        },
        selected: selected,
        onSelected: onSelected,
      ),
      list: (context, type) => PixivProfileFeed(
        creatorId: profile.id,
        loader: ({nextUrl}) => PixivSocialApi.of(context).userWorks(profile.id, type, nextUrl: nextUrl),
        emptyMessage: l10n.plugin_pixiv_profile_works_empty,
      ),
    );
  }
}

/// The Bookmarks tab: the creator's public bookmarks, illustrations or novels.
class PixivProfileBookmarks extends StatelessWidget {
  final int userId;

  const PixivProfileBookmarks({super.key, required this.userId});

  @override
  Widget build(BuildContext context) => PixivSwitchedList<PixivContentMode>(
    initial: PixivContentMode.illust,
    switcher: (selected, onSelected) => PixivContentModeSwitch(
      key: const ValueKey('pixiv-profile-bookmark-kind'),
      selected: selected,
      onSelected: onSelected,
    ),
    list: (context, kind) => switch (kind) {
      PixivContentMode.illust => PixivProfileFeed(
        creatorId: userId,
        loader: ({nextUrl}) => PixivSocialApi.of(context).userBookmarks(userId, nextUrl: nextUrl),
        emptyMessage: L10n.of(context).plugin_pixiv_profile_bookmarks_empty,
      ),
      PixivContentMode.novel => PixivProfileNovelBookmarks(userId: userId),
    },
  );
}

/// What the Novels tab lists: the creator's own novels or their novel bookmarks.
enum PixivProfileNovelList { works, bookmarks }

/// The Novels tab: the creator's novels, and the novels they bookmarked
/// publicly, the same list as Bookmarks › Novels.
class PixivProfileNovels extends StatelessWidget {
  final int userId;

  const PixivProfileNovels({super.key, required this.userId});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return PixivSwitchedList<PixivProfileNovelList>(
      initial: PixivProfileNovelList.works,
      switcher: (selected, onSelected) => PixivSegmentedSwitch<PixivProfileNovelList>(
        key: const ValueKey('pixiv-profile-novel-list'),
        values: PixivProfileNovelList.values,
        label: (value) => switch (value) {
          PixivProfileNovelList.works => l10n.plugin_pixiv_profile_works,
          PixivProfileNovelList.bookmarks => l10n.plugin_pixiv_tab_bookmarks,
        },
        selected: selected,
        onSelected: onSelected,
      ),
      list: (context, list) => switch (list) {
        PixivProfileNovelList.works => PixivOwnedNovelFeed(
          loader: ({nextUrl}) => PixivNovelApi.of(context).userNovels(userId, nextUrl: nextUrl),
          emptyMessage: l10n.plugin_pixiv_profile_novels_empty,
          mutes: (mute) => pixivMutesShowing(mute, userId),
        ),
        PixivProfileNovelList.bookmarks => PixivProfileNovelBookmarks(userId: userId),
      },
    );
  }
}

/// The novels a creator bookmarked publicly, under the reader's filters with
/// the creator's own author mute lifted.
class PixivProfileNovelBookmarks extends StatelessWidget {
  final int userId;

  const PixivProfileNovelBookmarks({super.key, required this.userId});

  @override
  Widget build(BuildContext context) => PixivOwnedNovelFeed(
    loader: ({nextUrl}) => PixivNovelApi.of(context).bookmarks(userId: userId, nextUrl: nextUrl),
    emptyMessage: L10n.of(context).plugin_pixiv_profile_novel_bookmarks_empty,
    mutes: (mute) => pixivMutesShowing(mute, userId),
  );
}

/// Keeps a tab's list and scroll position while another tab is shown.
class PixivKeepAlive extends StatefulWidget {
  final Widget child;

  const PixivKeepAlive({super.key, required this.child});

  @override
  State<PixivKeepAlive> createState() => _PixivKeepAliveState();
}

class _PixivKeepAliveState extends State<PixivKeepAlive> with AutomaticKeepAliveClientMixin {
  @override
  bool get wantKeepAlive => true;

  @override
  Widget build(BuildContext context) {
    super.build(context);
    return widget.child;
  }
}
