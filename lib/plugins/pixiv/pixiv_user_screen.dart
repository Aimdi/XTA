import 'dart:math';

import 'package:extended_nested_scroll_view/extended_nested_scroll_view.dart';
import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_loads.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_settings.dart';
import 'package:xta/plugins/pixiv/pixiv_social_api.dart';
import 'package:xta/plugins/pixiv/pixiv_user_header.dart';
import 'package:xta/plugins/pixiv/pixiv_user_menu.dart';
import 'package:xta/plugins/pixiv/pixiv_user_profile.dart';
import 'package:xta/plugins/pixiv/pixiv_user_store.dart';
import 'package:xta/plugins/pixiv/pixiv_user_tabs.dart';
import 'package:xta/plugins/plugin_view_store.dart';
import 'package:xta/profile/profile_chrome.dart';
import 'package:xta/ui/empty_pane.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/ui/motion.dart';
import 'package:xta/ui/reader_tab_view.dart';

/// One Pixiv creator's profile: header, then Works, Bookmarks, Following and
/// Info tabs. A creator the reader muted shows a placeholder instead.
class PixivUserScreen extends StatefulWidget {
  final int userId;

  /// The [PixivProfileTab.id] to open on; the first tab when absent or not offered.
  final String? initialTab;

  const PixivUserScreen({super.key, required this.userId, this.initialTab});

  @override
  State<PixivUserScreen> createState() => _PixivUserScreenState();
}

class _PixivUserScreenState extends State<PixivUserScreen> {
  late final PixivUserStore _profile;
  final _loads = PixivLoads();

  /// Show anyway, for this visit only.
  final _revealed = PluginViewStore<bool>(false);

  @override
  void initState() {
    super.initState();
    _profile = PixivUserStore(PixivSocialApi.of(context), widget.userId);
    _load();
  }

  Future<void> _load() => _loads.track(_profile.load());

  @override
  void dispose() {
    _loads.destroyAfter([_profile]);
    _revealed.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => TripleBuilder<PixivUserStore, PixivUserProfile?>(
    store: _profile,
    builder: (context, triple) {
      final profile = triple.state;
      if (profile == null) return _pending(context, triple.isLoading ? null : triple.error);
      return ScopedBuilder<PixivMuteStore, PixivMuteState>(
        store: context.read<PixivMuteStore>(),
        onState: (context, mute) => ScopedBuilder<PluginViewStore<bool>, bool>(
          store: _revealed,
          onState: (context, revealed) {
            final scope = PixivProfileScope(
              profile: profile,
              own: profile.id == context.read<PixivClient>().storedUserId,
              muted: mute.authorIds.contains(profile.id),
            );
            if (scope.muted && !revealed) return _muted(context, scope);
            return PixivProfileView(scope: scope, initialTab: widget.initialTab);
          },
        ),
      );
    },
  );

  Widget _pending(BuildContext context, Object? error) => Scaffold(
    appBar: AppBar(),
    body: error == null
        ? const Center(child: CircularProgressIndicator())
        : Padding(
            padding: const EdgeInsets.all(24),
            child: FullPageErrorWidget(
              error: error,
              stackTrace: null,
              prefix: pixivErrorMessage(L10n.of(context), error),
              onRetry: _load,
            ),
          ),
  );

  Widget _muted(BuildContext context, PixivProfileScope scope) {
    final l10n = L10n.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(scope.profile.user.name), actions: pixivProfileActions(context, scope)),
      body: EmptyPane(
        key: const ValueKey('pixiv-profile-muted'),
        icon: Icons.person_off_outlined,
        message: l10n.plugin_pixiv_profile_muted,
        action: Wrap(
          alignment: WrapAlignment.center,
          spacing: 12,
          runSpacing: 8,
          children: [
            FilledButton(onPressed: () => _revealed.select(true), child: Text(l10n.plugin_pixiv_profile_show_anyway)),
            OutlinedButton(
              onPressed: () => context.read<PixivMuteStore>().unmuteAuthor(scope.profile.id),
              child: Text(l10n.plugin_pixiv_unmute),
            ),
          ],
        ),
      ),
    );
  }
}

/// A loaded profile: the header and tab bar collapse under the AppBar while
/// the tab below scrolls.
class PixivProfileView extends StatelessWidget {
  final PixivProfileScope scope;
  final String? initialTab;

  const PixivProfileView({super.key, required this.scope, this.initialTab});

  @override
  Widget build(BuildContext context) {
    final tabs = pixivProfileTabsFor(scope);
    return DefaultTabController(
      length: tabs.length,
      initialIndex: max(0, tabs.indexWhere((tab) => tab.id == initialTab)),
      child: Builder(builder: (context) => Scaffold(body: _scroll(context, tabs))),
    );
  }

  VoidCallback? _showTab(BuildContext context, TabController controller, List<PixivProfileTab> tabs, String id) {
    final index = tabs.indexWhere((tab) => tab.id == id);
    final duration = xtaMotionDuration(context, controller.animationDuration);
    return index < 0 ? null : () => controller.animateTo(index, duration: duration);
  }

  Widget _scroll(BuildContext context, List<PixivProfileTab> tabs) {
    final l10n = L10n.of(context);
    final controller = DefaultTabController.of(context);
    final pinned = MediaQuery.paddingOf(context).top + kToolbarHeight + kProfileTabHeight;
    return ExtendedNestedScrollView(
      onlyOneScrollInBody: true,
      pinnedHeaderSliverHeightBuilder: () => pinned,
      headerSliverBuilder: (context, _) => [
        SliverAppBar(
          pinned: true,
          title: Text(scope.profile.user.name, maxLines: 1, overflow: TextOverflow.ellipsis),
          actions: pixivProfileActions(context, scope),
        ),
        SliverToBoxAdapter(
          child: PixivUserHeader(profile: scope.profile, onWorks: _showTab(context, controller, tabs, 'works')),
        ),
        SliverPersistentHeader(
          pinned: true,
          delegate: ProfileTabsDelegate(
            ProfileTabsBar(
              controller: controller,
              tabs: [for (final tab in tabs) Tab(key: ValueKey('pixiv-profile-tab-${tab.id}'), text: tab.label(l10n))],
            ),
          ),
        ),
      ],
      body: SafeArea(
        top: false,
        child: ReaderTabView(
          controller: controller,
          children: [for (final tab in tabs) PixivKeepAlive(child: tab.build(scope))],
        ),
      ),
    );
  }
}
