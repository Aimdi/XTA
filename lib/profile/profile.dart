import 'package:extended_nested_scroll_view/extended_nested_scroll_view.dart';
import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:intl/intl.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:share_plus/share_plus.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_links.dart';
import 'package:xta/profile/_follows.dart';
import 'package:xta/profile/_media_grid.dart';
import 'package:xta/profile/_saved.dart';
import 'package:xta/profile/_tweets.dart';
import 'package:xta/profile/archive_filter.dart';
import 'package:xta/profile/media_grid/media_grid_items/media_grid_item.dart';
import 'package:xta/profile/posts_filter.dart';
import 'package:xta/profile/profile_chrome.dart';
import 'package:xta/profile/profile_feed_settings.dart';
import 'package:xta/profile/profile_model.dart';
import 'package:xta/profile/profile_note.dart';
import 'package:xta/profile/profile_view_store.dart';
import 'package:xta/search/search.dart';
import 'package:xta/tweet/_media.dart';
import 'package:xta/tweet/sensitive_media_gate.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/tweet/tweet_context_scope.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/ui/motion.dart';
import 'package:xta/ui/reader_chrome.dart';
import 'package:xta/user.dart';
import 'package:xta/utils/rich_text.dart';
import 'package:xta/utils/urls.dart';

typedef TabTitleBuilder = String Function(BuildContext context);

class NavigationTab {
  final ProfileTabs id;
  final TabTitleBuilder titleBuilder;
  final IconData icon;

  const NavigationTab(this.id, this.titleBuilder, this.icon);
}

final List<NavigationTab> profileTabs = [
  NavigationTab(
    ProfileTabs.posts,
    (context) => L10n.of(context).tweets,
    Icons.article_outlined,
  ),
  NavigationTab(
    ProfileTabs.postsAndReplies,
    (context) => L10n.of(context).tweets_and_replies,
    Icons.mode_comment_outlined,
  ),
  NavigationTab(
    ProfileTabs.media,
    (context) => L10n.of(context).media,
    Icons.perm_media_outlined,
  ),
  NavigationTab(
    ProfileTabs.saved,
    (context) => L10n.of(context).saved,
    Icons.bookmark_border,
  ),
];

class ProfileScreenArguments {
  final String? id;
  final String? screenName;
  final int? tabIndex;

  ProfileScreenArguments(this.id, this.screenName, this.tabIndex);

  factory ProfileScreenArguments.fromId(String id, int? tabIndex) {
    return ProfileScreenArguments(id, null, tabIndex);
  }

  factory ProfileScreenArguments.fromScreenName(
    String screenName,
    int? tabIndex,
  ) {
    return ProfileScreenArguments(null, screenName, tabIndex);
  }
}

class ProfileScreen extends StatelessWidget {
  const ProfileScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final args =
        ModalRoute.of(context)!.settings.arguments as ProfileScreenArguments;

    return Provider(
      create: (context) {
        final model = ProfileModel();
        if (args.id != null) {
          model.loadProfileById(args.id!);
        } else {
          model.loadProfileByScreenName(args.screenName!);
        }
        return model;
      },
      child: _ProfileScreen(
        id: args.id,
        screenName: args.screenName,
        tabIndex: args.tabIndex,
      ),
    );
  }
}

class _ProfileScreen extends StatelessWidget {
  final String? id;
  final String? screenName;
  final int? tabIndex;

  const _ProfileScreen({
    required this.id,
    required this.screenName,
    required this.tabIndex,
  });

  @override
  Widget build(BuildContext context) {
    return XtaSystemBars(
      child: Scaffold(
        body: ScopedBuilder<ProfileModel, Profile>(
          store: context.read<ProfileModel>(),
          onError: (_, error) => XtaFadeIn(
            key: const ValueKey('profile-error'),
            child: FullPageErrorWidget(
              error: error,
              stackTrace: null,
              prefix: L10n.of(context).unable_to_load_the_profile,
              onRetry: () {
                if (id != null) {
                  return context.read<ProfileModel>().loadProfileById(id!);
                }
                return context.read<ProfileModel>().loadProfileByScreenName(
                  screenName!,
                );
              },
            ),
          ),
          onLoading: (_) => const ProfileLoadingSkeleton(),
          onState: (_, state) => XtaFadeIn(
            key: const ValueKey('profile-content'),
            child: ProfileScreenBody(
              profile: state,
              defaultTabIndex: tabIndex,
            ),
          ),
        ),
      ),
    );
  }
}

class ProfileScreenBody extends StatefulWidget {
  final Profile profile;
  final int? defaultTabIndex;

  const ProfileScreenBody({
    super.key,
    required this.profile,
    required this.defaultTabIndex,
  });

  @override
  State<ProfileScreenBody> createState() => _ProfileScreenBodyState();
}

class _ProfileScreenBodyState extends State<ProfileScreenBody>
    with TickerProviderStateMixin {
  final GlobalKey<NestedScrollViewState> _nestedScrollViewKey = GlobalKey();
  final ProfileViewStore _viewStore = ProfileViewStore();
  final ProfileScrollStore _scrollStore = ProfileScrollStore();
  final NumberFormat _numberFormat = NumberFormat.compact();

  late TabController _tabController;
  bool _tabControllerInitialized = false;
  List<RichTextPart> _descriptionParts = const [];
  String? _descriptionSource;
  Object? _descriptionEntitiesSource;
  Color? _descriptionLinkColor;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _nestedScrollViewKey.currentState?.innerController.addListener(
        _listenToScroll,
      );
    });
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _initializeTabController();
    _syncDescriptionParts();
  }

  @override
  void didUpdateWidget(covariant ProfileScreenBody oldWidget) {
    super.didUpdateWidget(oldWidget);
    _syncDescriptionParts();
  }

  void _initializeTabController() {
    if (_tabControllerInitialized) return;

    final storedName = PrefService.of(
      context,
    ).get<String>(optionDefaultProfileTab);
    final storedTab = ProfileTabs.values
        .where((tab) => tab.name == storedName)
        .firstOrNull;
    final storedIndex = profileTabs.indexWhere((tab) => tab.id == storedTab);
    final requested = widget.defaultTabIndex ?? storedIndex;
    final initialIndex = requested.clamp(0, profileTabs.length - 1).toInt();

    _tabController = TabController(
      length: profileTabs.length,
      vsync: this,
      initialIndex: initialIndex,
    );
    _tabControllerInitialized = true;
  }

  void _syncDescriptionParts() {
    final description = widget.profile.user.description;
    final entities = widget.profile.user.entities?.description;
    final linkColor = Theme.of(context).colorScheme.secondary;
    if (description == _descriptionSource &&
        identical(entities, _descriptionEntitiesSource) &&
        linkColor == _descriptionLinkColor) {
      return;
    }

    disposeRichTextParts(_descriptionParts);
    _descriptionSource = description;
    _descriptionEntitiesSource = entities;
    _descriptionLinkColor = linkColor;
    _descriptionParts = description == null || description.isEmpty
        ? const []
        : buildRichText(context, description, entities);
  }

  @override
  void dispose() {
    _nestedScrollViewKey.currentState?.innerController.removeListener(
      _listenToScroll,
    );
    disposeRichTextParts(_descriptionParts);
    if (_tabControllerInitialized) _tabController.dispose();
    _viewStore.destroy();
    _scrollStore.destroy();
    super.dispose();
  }

  void _listenToScroll() {
    final inner = _nestedScrollViewKey.currentState?.innerController;
    if (inner == null || !inner.hasClients) return;
    _scrollStore.showBackToTop(
      inner.positions.any((position) => position.pixels >= 400),
    );
  }

  void _scrollToTop() {
    _nestedScrollViewKey.currentState?.outerController.jumpTo(0);
  }

  void _openProfileMedia(String? url, String username) {
    if (url == null || url.isEmpty) return;
    pushTweetMediaViewer<void>(
      context,
      TweetMediaView(
        initialIndex: 0,
        media: [createMediaFromUrl(url, null)],
        username: username,
        tweetMedia: false,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final user = widget.profile.user;
    if (user.idStr == null) return const SizedBox.shrink();

    return ScopedBuilder<ProfileViewStore, ProfileViewState>(
      store: _viewStore,
      onState: (context, view) => _buildProfile(context, user, view),
    );
  }

  Widget _buildProfile(
    BuildContext context,
    UserWithExtra user,
    ProfileViewState view,
  ) {
    final prefs = PrefService.of(context, listen: false);
    final width = MediaQuery.sizeOf(context).width;
    final bannerHeight = (width / 3)
        .clamp(120.0, kProfileBannerHeight)
        .toDouble();
    final username = user.screenName ?? L10n.of(context).unknown;

    return Scaffold(
      body: ExtendedNestedScrollView(
        key: _nestedScrollViewKey,
        onlyOneScrollInBody: true,
        headerSliverBuilder: (context, innerBoxIsScrolled) => [
          _buildAppBar(context, user, view, innerBoxIsScrolled),
          SliverToBoxAdapter(
            child: _buildIdentityHeader(
              context,
              user,
              username,
              bannerHeight,
            ),
          ),
          SliverPersistentHeader(
            pinned: true,
            delegate: ProfileTabsDelegate(
              ProfileTabsBar(
                controller: _tabController,
                tabs: [
                  for (final tab in profileTabs)
                    Tab(
                      child: _ProfileTabLabel(
                        icon: tab.icon,
                        label: tab.titleBuilder(context),
                      ),
                    ),
                ],
              ),
            ),
          ),
        ],
        body: TweetContextScope(
          child: SafeArea(
            top: false,
            child: TabBarView(
              controller: _tabController,
              children: [
                ProfileTweets(
                  user: user,
                  type: 'profile',
                  includeReplies: false,
                  pinnedTweets: widget.profile.pinnedTweets,
                  pref: prefs,
                  filter: view.postsFilter,
                ),
                ProfileTweets(
                  user: user,
                  type: 'profile',
                  includeReplies: true,
                  pinnedTweets: widget.profile.pinnedTweets,
                  pref: prefs,
                ),
                ProfileMediaGrid(
                  user: user,
                  pref: prefs,
                  filter: view.mediaFilter,
                ),
                ProfileSaved(user: user, filter: view.archiveFilter),
              ],
            ),
          ),
        ),
      ),
      floatingActionButton: ScopedBuilder<ProfileScrollStore, bool>(
        store: _scrollStore,
        onState: (_, visible) => visible
            ? FloatingActionButton(
                onPressed: _scrollToTop,
                child: const Icon(Icons.arrow_upward),
              )
            : const SizedBox.shrink(),
      ),
    );
  }

  SliverAppBar _buildAppBar(
    BuildContext context,
    UserWithExtra user,
    ProfileViewState view,
    bool innerBoxIsScrolled,
  ) {
    final username = user.screenName ?? L10n.of(context).unknown;
    return SliverAppBar(
      pinned: true,
      floating: false,
      elevation: 0,
      scrolledUnderElevation: 0,
      surfaceTintColor: Colors.transparent,
      titleSpacing: 0,
      title: innerBoxIsScrolled
          ? Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  user.name ?? username,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: tweetLabelStyle(context),
                ),
                Text(
                  '@$username',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  textDirection: TextDirection.ltr,
                  style: tweetMetadataStyle(context).copyWith(fontSize: 11),
                ),
              ],
            )
          : null,
      actions: [
        AnimatedBuilder(
          animation: _tabController,
          builder: (context, _) =>
              _filterForCurrentTab(context, view) ?? const SizedBox.shrink(),
        ),
        IconButton(
          icon: const Icon(Icons.search),
          tooltip: L10n.of(context).search,
          onPressed: () => Navigator.pushNamed(
            context,
            routeSearch,
            arguments: SearchArguments(
              1,
              focusInputOnOpen: true,
              query: 'from:@$username ',
            ),
          ),
        ),
        IconButton(
          icon: const Icon(Icons.share_outlined),
          tooltip: L10n.of(context).share_link,
          onPressed: () => Share.share('${_shareBaseUrl(context)}/$username'),
        ),
      ],
    );
  }

  Widget _buildIdentityHeader(
    BuildContext context,
    UserWithExtra user,
    String username,
    double bannerHeight,
  ) {
    final bannerUrl = user.profileBannerUrl;
    final avatarUrl = user.profileImageUrlHttps?.replaceAll(
      '_normal',
      '_400x400',
    );

    return ProfileIdentityHeader(
      banner: ProfileBanner(
        uri: bannerUrl,
        height: bannerHeight,
        semanticLabel: user.name,
        onTap: bannerUrl == null
            ? null
            : () => _openProfileMedia(bannerUrl, username),
      ),
      avatar: ProfileAvatar(
        uri: user.profileImageUrlHttps,
        semanticLabel: user.name,
        onTap: avatarUrl == null
            ? null
            : () => _openProfileMedia(avatarUrl, username),
      ),
      actions: ProfileActionCluster(
        children: [
          ProfileFeedSettingsButton(
            user: user,
            color: tweetReadableAccentColor(context),
          ),
          FollowButton(
            user: UserSubscription.fromUser(user),
            color: tweetReadableAccentColor(context),
          ),
        ],
      ),
      name: user.name ?? username,
      handle: '@$username',
      verified: user.verified ?? false,
      protected: user.protected ?? false,
      protectedLabel: L10n.of(context).private_profile,
      bio: _descriptionParts.isEmpty
          ? null
          : SelectableText.rich(
              TextSpan(
                style: tweetBodyStyle(context).copyWith(height: 1.45),
                children: displayRichText(_descriptionParts),
              ),
            ),
      metadata: _profileMetadata(context, user),
      counts: _profileCounts(context, user),
      note: ProfileNoteCard(userId: user.idStr!),
    );
  }

  List<Widget> _profileMetadata(BuildContext context, UserWithExtra user) {
    final result = <Widget>[];
    final location = user.location;
    if (location != null && location.isNotEmpty) {
      result.add(
        ProfileMetadataItem(
          icon: Icons.location_on_outlined,
          child: Text(location),
        ),
      );
    }

    final link = _profileLink(user);
    if (link != null) {
      result.add(
        ProfileMetadataItem(
          icon: Icons.link,
          onTap: () => openLink(context, link.target),
          child: Text(
            link.label,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(color: tweetReadableAccentColor(context)),
          ),
        ),
      );
    }

    if (user.createdAt != null) {
      result.add(
        ProfileMetadataItem(
          icon: Icons.calendar_today_outlined,
          child: Text(
            L10n.of(context).joined(
              DateFormat('MMMM yyyy').format(user.createdAt!),
            ),
          ),
        ),
      );
    }
    return result;
  }

  List<Widget> _profileCounts(BuildContext context, UserWithExtra user) {
    final result = <Widget>[];
    if (user.friendsCount != null) {
      result.add(
        ProfileCountButton(
          count: _numberFormat.format(user.friendsCount),
          label: L10n.of(context).following,
          onTap: () => _openFollows(context, user, 'following'),
        ),
      );
    }
    if (user.followersCount != null) {
      result.add(
        ProfileCountButton(
          count: _numberFormat.format(user.followersCount),
          label: L10n.of(context).followers,
          onTap: () => _openFollows(context, user, 'followers'),
        ),
      );
    }
    return result;
  }

  void _openFollows(BuildContext context, UserWithExtra user, String type) {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => ProfileFollows(user: user, type: type),
      ),
    );
  }

  ({String label, String target})? _profileLink(UserWithExtra user) {
    final source = user.url;
    final urls = user.entities?.url?.urls;
    if (source == null || source.isEmpty || urls == null) return null;

    for (final url in urls) {
      if (url.url != source) continue;
      final label = url.displayUrl ?? url.url;
      final target = url.expandedUrl ?? url.url;
      if (label != null && target != null) {
        return (label: label, target: target);
      }
    }
    return null;
  }

  String _shareBaseUrl(BuildContext context) {
    final value = PrefService.of(
      context,
      listen: false,
    ).get<String>(optionShareBaseUrl);
    return value == null || value.isEmpty ? 'https://x.com' : value;
  }

  Widget? _filterForCurrentTab(
    BuildContext context,
    ProfileViewState view,
  ) {
    final tab = profileTabs[_tabController.index].id;
    return switch (tab) {
      ProfileTabs.posts => ProfileFilterMenu<PostsFilter>(
        selected: view.postsFilter,
        defaultValue: PostsFilter.all,
        options: [
          ProfileFilterOption(
            value: PostsFilter.all,
            label: L10n.of(context).all,
            icon: Icons.article_outlined,
          ),
          ProfileFilterOption(
            value: PostsFilter.posts,
            label: L10n.of(context).tweets,
            icon: Icons.notes_outlined,
          ),
          ProfileFilterOption(
            value: PostsFilter.retweets,
            label: L10n.of(context).retweets,
            icon: Icons.repeat,
          ),
        ],
        onSelected: _viewStore.selectPostsFilter,
      ),
      ProfileTabs.media => ProfileFilterMenu<MediaFilter>(
        selected: view.mediaFilter,
        defaultValue: MediaFilter.all,
        options: [
          ProfileFilterOption(
            value: MediaFilter.all,
            label: L10n.of(context).all,
            icon: Icons.perm_media_outlined,
          ),
          ProfileFilterOption(
            value: MediaFilter.photos,
            label: L10n.of(context).photos,
            icon: Icons.photo_library_outlined,
          ),
          ProfileFilterOption(
            value: MediaFilter.videos,
            label: L10n.of(context).videos,
            icon: Icons.video_library_outlined,
          ),
          ProfileFilterOption(
            value: MediaFilter.broadcasts,
            label: L10n.of(context).broadcasts,
            icon: Icons.live_tv_outlined,
          ),
        ],
        onSelected: _viewStore.selectMediaFilter,
      ),
      ProfileTabs.saved => ProfileFilterMenu<ArchiveFilter>(
        selected: view.archiveFilter,
        defaultValue: ArchiveFilter.all,
        options: [
          ProfileFilterOption(
            value: ArchiveFilter.all,
            label: L10n.of(context).all,
            icon: Icons.inventory_2_outlined,
          ),
          ProfileFilterOption(
            value: ArchiveFilter.likes,
            label: L10n.of(context).favorites,
            icon: Icons.favorite_border,
          ),
          ProfileFilterOption(
            value: ArchiveFilter.bookmarks,
            label: L10n.of(context).plugin_category_bookmarks,
            icon: Icons.bookmark_border,
          ),
        ],
        onSelected: _viewStore.selectArchiveFilter,
      ),
      ProfileTabs.postsAndReplies => null,
    };
  }
}

class _ProfileTabLabel extends StatelessWidget {
  final IconData icon;
  final String label;

  const _ProfileTabLabel({required this.icon, required this.label});

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, size: kTweetActionIconSize),
        const SizedBox(width: kTweetSpace2),
        Text(label, maxLines: 1, overflow: TextOverflow.ellipsis),
      ],
    );
  }
}

class TweetContextState extends ChangeNotifier {
  bool hideSensitive;

  TweetContextState(this.hideSensitive);

  factory TweetContextState.fromPrefs(BasePrefService prefs) =>
      TweetContextState(initialHideSensitive(prefs));

  void setHideSensitive(bool value) {
    hideSensitive = value;
    notifyListeners();
  }

  Future<void> alwaysShowSensitive(BasePrefService prefs) async {
    await prefs.set(optionAlwaysShowSensitiveMedia, true);
    setHideSensitive(false);
  }
}
