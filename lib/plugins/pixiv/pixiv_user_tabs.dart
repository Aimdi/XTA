import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_grid.dart';
import 'package:xta/plugins/pixiv/pixiv_loads.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
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

class _PixivProfileFeedStore extends PixivIllustListStore with PixivTrackedPages<PixivIllust> {
  _PixivProfileFeedStore(super.loader, {super.filter});
}

/// A works grid a profile tab owns: it loads when the tab is first shown and
/// is let go with the tab.
class PixivProfileFeed extends StatefulWidget {
  final PixivIllustPageLoader loader;
  final String emptyMessage;

  /// Whose profile this is; see [pixivMutesShowing].
  final int creatorId;

  const PixivProfileFeed({super.key, required this.loader, required this.emptyMessage, required this.creatorId});

  @override
  State<PixivProfileFeed> createState() => _PixivProfileFeedState();
}

class _PixivProfileFeedState extends State<PixivProfileFeed> {
  late final _PixivProfileFeedStore _works;

  PixivMuteState _mutes(PixivMuteState mute) => pixivMutesShowing(mute, widget.creatorId);

  @override
  void initState() {
    super.initState();
    final mute = context.read<PixivMuteStore>();
    _works = _PixivProfileFeedStore(widget.loader, filter: (illusts) => _mutes(mute.state).filter(illusts))..refresh();
  }

  @override
  void dispose() {
    _works.destroyWhenSettled();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) =>
      PixivIllustFeed(store: _works, emptyMessage: widget.emptyMessage, mutes: _mutes);
}

/// The Works tab: illustrations or manga, starting on whichever the creator
/// has more of, with a switch when they have both.
class PixivProfileWorks extends StatefulWidget {
  final PixivUserProfile profile;

  const PixivProfileWorks({super.key, required this.profile});

  @override
  State<PixivProfileWorks> createState() => _PixivProfileWorksState();
}

class _PixivProfileWorksState extends State<PixivProfileWorks> {
  late final PluginViewStore<PixivWorkType> _type = PluginViewStore(widget.profile.defaultWorkType);

  @override
  void dispose() {
    _type.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final api = PixivSocialApi.of(context);
    final l10n = L10n.of(context);
    final profile = widget.profile;
    return ScopedBuilder<PluginViewStore<PixivWorkType>, PixivWorkType>(
      store: _type,
      onState: (context, type) => Column(
        children: [
          if (profile.hasBothWorkTypes)
            PixivSegmentedSwitch<PixivWorkType>(
              key: const ValueKey('pixiv-profile-work-type'),
              values: PixivWorkType.values,
              label: (value) => switch (value) {
                PixivWorkType.illust => l10n.plugin_pixiv_profile_illusts,
                PixivWorkType.manga => l10n.plugin_pixiv_profile_manga,
              },
              selected: type,
              onSelected: _type.select,
            ),
          Expanded(
            child: PixivProfileFeed(
              key: ValueKey(type),
              creatorId: profile.id,
              loader: ({nextUrl}) => api.userWorks(profile.id, type, nextUrl: nextUrl),
              emptyMessage: l10n.plugin_pixiv_profile_works_empty,
            ),
          ),
        ],
      ),
    );
  }
}

/// The Bookmarks tab: the creator's public bookmarks, illustrations or novels.
class PixivProfileBookmarks extends StatefulWidget {
  final int userId;

  const PixivProfileBookmarks({super.key, required this.userId});

  @override
  State<PixivProfileBookmarks> createState() => _PixivProfileBookmarksState();
}

class _PixivProfileBookmarksState extends State<PixivProfileBookmarks> {
  final _kind = PluginViewStore(PixivContentMode.illust);

  @override
  void dispose() {
    _kind.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return ScopedBuilder<PluginViewStore<PixivContentMode>, PixivContentMode>(
      store: _kind,
      onState: (context, kind) => Column(
        children: [
          PixivSegmentedSwitch<PixivContentMode>(
            key: const ValueKey('pixiv-profile-bookmark-kind'),
            values: PixivContentMode.values,
            label: (value) => switch (value) {
              PixivContentMode.illust => l10n.plugin_pixiv_profile_illusts,
              PixivContentMode.novel => l10n.plugin_pixiv_profile_novels,
            },
            selected: kind,
            onSelected: _kind.select,
          ),
          Expanded(
            child: KeyedSubtree(key: ValueKey(kind), child: _list(context, kind)),
          ),
        ],
      ),
    );
  }

  Widget _list(BuildContext context, PixivContentMode kind) {
    final userId = widget.userId;
    final l10n = L10n.of(context);
    final social = PixivSocialApi.of(context);
    final novels = PixivNovelApi.of(context);
    return switch (kind) {
      PixivContentMode.illust => PixivProfileFeed(
        creatorId: userId,
        loader: ({nextUrl}) => social.userBookmarks(userId, nextUrl: nextUrl),
        emptyMessage: l10n.plugin_pixiv_profile_bookmarks_empty,
      ),
      PixivContentMode.novel => PixivOwnedNovelFeed(
        loader: ({nextUrl}) => novels.bookmarks(userId: userId, nextUrl: nextUrl),
        emptyMessage: l10n.plugin_pixiv_profile_novel_bookmarks_empty,
        mutes: (mute) => pixivMutesShowing(mute, userId),
      ),
    };
  }
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
