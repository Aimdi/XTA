import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_grid.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_social_api.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:xta/plugins/pixiv/pixiv_user_info.dart';
import 'package:xta/plugins/pixiv/pixiv_user_list_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_user_profile.dart';
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

/// A works grid a profile tab owns: it loads when the tab is first shown and
/// is let go with the tab.
class PixivProfileFeed extends StatefulWidget {
  final PixivIllustPageLoader loader;
  final String emptyMessage;

  const PixivProfileFeed({super.key, required this.loader, required this.emptyMessage});

  @override
  State<PixivProfileFeed> createState() => _PixivProfileFeedState();
}

class _PixivProfileFeedState extends State<PixivProfileFeed> {
  late final PixivIllustListStore _works;
  late final Future<void> _loading;

  @override
  void initState() {
    super.initState();
    _works = PixivIllustListStore(widget.loader, filter: context.read<PixivMuteStore>().filter);
    _loading = _works.refresh();
  }

  @override
  void dispose() {
    // A load still in flight writes to the store when it lands; destroy it after.
    unawaited(_loading.whenComplete(_works.destroy));
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => PixivIllustFeed(store: _works, emptyMessage: widget.emptyMessage);
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
    return ScopedBuilder<PluginViewStore<PixivWorkType>, PixivWorkType>(
      store: _type,
      onState: (context, type) => Column(
        children: [
          if (widget.profile.hasBothWorkTypes) _switch(context, type),
          Expanded(
            child: PixivProfileFeed(
              key: ValueKey(type),
              loader: ({nextUrl}) => api.userWorks(widget.profile.id, type, nextUrl: nextUrl),
              emptyMessage: L10n.of(context).plugin_pixiv_profile_works_empty,
            ),
          ),
        ],
      ),
    );
  }

  Widget _switch(BuildContext context, PixivWorkType type) {
    final l10n = L10n.of(context);
    ButtonSegment<PixivWorkType> segment(PixivWorkType value, String label) => ButtonSegment(
      value: value,
      label: Text(label, maxLines: 1, overflow: TextOverflow.ellipsis),
    );
    return Padding(
      padding: const EdgeInsets.fromLTRB(12, 8, 12, 0),
      child: SizedBox(
        width: double.infinity,
        child: SegmentedButton<PixivWorkType>(
          key: const ValueKey('pixiv-profile-work-type'),
          segments: [
            segment(PixivWorkType.illust, l10n.plugin_pixiv_profile_illusts),
            segment(PixivWorkType.manga, l10n.plugin_pixiv_profile_manga),
          ],
          selected: {type},
          showSelectedIcon: false,
          onSelectionChanged: (selected) => _type.select(selected.first),
        ),
      ),
    );
  }
}

/// The Bookmarks tab: the creator's public bookmarks.
class PixivProfileBookmarks extends StatelessWidget {
  final int userId;

  const PixivProfileBookmarks({super.key, required this.userId});

  @override
  Widget build(BuildContext context) {
    final api = PixivSocialApi.of(context);
    return PixivProfileFeed(
      loader: ({nextUrl}) => api.userBookmarks(userId, nextUrl: nextUrl),
      emptyMessage: L10n.of(context).plugin_pixiv_profile_bookmarks_empty,
    );
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
