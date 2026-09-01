import 'package:extended_nested_scroll_view/extended_nested_scroll_view.dart';
import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:intl/intl.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:quax/constants.dart';
import 'package:quax/database/entities.dart';
import 'package:quax/generated/l10n.dart';
import 'package:quax/home/home_chrome.dart';
import 'package:quax/profile/_follows.dart';
import 'package:quax/profile/_media_grid.dart';
import 'package:quax/profile/_saved.dart';
import 'package:quax/profile/_tweets.dart';
import 'package:quax/profile/media_grid/media_grid_items/media_grid_item.dart';
import 'package:quax/profile/profile_chrome.dart';
import 'package:quax/profile/profile_feed_settings.dart';
import 'package:quax/profile/profile_model.dart';
import 'package:quax/search/search.dart';
import 'package:quax/tweet/_media.dart';
import 'package:quax/tweet/tweet_chrome.dart';
import 'package:quax/tweet/tweet_context_scope.dart';
import 'package:quax/ui/errors.dart';
import 'package:quax/ui/reader_chrome.dart';
import 'package:quax/ui/x_look_theme.dart';
import 'package:quax/user.dart';
import 'package:quax/utils/rich_text.dart';
import 'package:quax/utils/urls.dart';
import 'package:share_plus/share_plus.dart';

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
    (c) => L10n.of(c).tweets,
    Icons.article_outlined,
  ),
  NavigationTab(
    ProfileTabs.postsAndReplies,
    (c) => L10n.of(c).tweets_and_replies,
    Icons.mode_comment_outlined,
  ),
  NavigationTab(
    ProfileTabs.media,
    (c) => L10n.of(c).media,
    Icons.perm_media_outlined,
  ),
  NavigationTab(
    ProfileTabs.saved,
    (c) => L10n.of(c).saved,
    Icons.bookmark_border,
  ),
];

class ProfileScreenArguments {
  final String? id;
  final String? screenName;
  final int? tabIndex;

  ProfileScreenArguments(this.id, this.screenName, this.tabIndex);

  factory ProfileScreenArguments.fromId(String id, int? tabIndex) =>
      ProfileScreenArguments(id, null, tabIndex);

  factory ProfileScreenArguments.fromScreenName(
    String screenName,
    int? tabIndex,
  ) => ProfileScreenArguments(null, screenName, tabIndex);
}

class ProfileScreen extends StatelessWidget {
  const ProfileScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final args =
        ModalRoute.of(context)!.settings.arguments as ProfileScreenArguments;
    return Provider(
      create: (_) {
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
      child: ScopedBuilder<ProfileModel, Profile>.transition(
        store: context.read<ProfileModel>(),
        onError: (_, error) => Scaffold(
          body: SafeArea(
            child: FullPageErrorWidget(
              error: error,
              stackTrace: null,
              prefix: L10n.of(context).unable_to_load_the_profile,
              onRetry: () => _reload(context),
            ),
          ),
        ),
        onLoading: (_) => const ProfileLoadingSkeleton(),
        onState: (_, state) =>
            ProfileScreenBody(profile: state, defaultTabIndex: tabIndex),
      ),
    );
  }

  Future<void> _reload(BuildContext context) {
    if (id != null) return context.read<ProfileModel>().loadProfileById(id!);
    return context.read<ProfileModel>().loadProfileByScreenName(screenName!);
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
    with SingleTickerProviderStateMixin {
  final GlobalKey<NestedScrollViewState> _scrollViewKey = GlobalKey();
  final NumberFormat _numberFormat = NumberFormat.compact();
  TabController? _tabController;
  ProfileViewStore? _viewStore;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_tabController != null) return;
    final defaultTab = ProfileTabs.values.byName(
      PrefService.of(context).get(optionDefaultProfileTab),
    );
    final preferredIndex = profileTabs.indexWhere(
      (tab) => tab.id == defaultTab,
    );
    final initialIndex = (widget.defaultTabIndex ?? preferredIndex)
        .clamp(0, profileTabs.length - 1)
        .toInt();
    _viewStore = ProfileViewStore(initialIndex);
    _tabController = TabController(
      length: profileTabs.length,
      vsync: this,
      initialIndex: initialIndex,
    )..addListener(_onTabChanged);
    WidgetsBinding.instance.addPostFrameCallback(
      (_) => _attachScrollListener(),
    );
  }

  @override
  void dispose() {
    _scrollViewKey.currentState?.innerController.removeListener(
      _onScrollChanged,
    );
    _tabController
      ?..removeListener(_onTabChanged)
      ..dispose();
    _viewStore?.destroy();
    super.dispose();
  }

  void _attachScrollListener() => _scrollViewKey.currentState?.innerController
      .addListener(_onScrollChanged);

  void _onTabChanged() => _viewStore?.selectTab(_tabController!.index);

  void _onScrollChanged() {
    final controller = _scrollViewKey.currentState?.innerController;
    if (controller == null || !controller.hasClients) return;
    final show = controller.positions.any((position) => position.pixels >= 400);
    _viewStore?.setBackToTopVisible(show);
  }

  void _scrollToTop() {
    final controller = _scrollViewKey.currentState?.outerController;
    if (controller == null || !controller.hasClients) return;
    final animationsDisabled =
        PrefService.of(context, listen: false).get(optionDisableAnimations) ==
            true ||
        MediaQuery.disableAnimationsOf(context);
    if (animationsDisabled) {
      controller.jumpTo(0);
    } else {
      controller.animateTo(
        0,
        duration: const Duration(milliseconds: 240),
        curve: Curves.easeOutCubic,
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final user = widget.profile.user;
    if (user.idStr?.isNotEmpty != true || user.screenName?.isNotEmpty != true) {
      return Scaffold(
        appBar: AppBar(),
        body: TweetEmptyState(message: L10n.of(context).user_not_found),
      );
    }
    return ScopedBuilder<ProfileViewStore, ProfileViewState>(
      store: _viewStore!,
      onState: (_, viewState) => _buildProfile(context, user, viewState),
    );
  }

  Widget _buildProfile(
    BuildContext context,
    UserWithExtra user,
    ProfileViewState viewState,
  ) {
    final prefs = PrefService.of(context, listen: false);
    return Scaffold(
      body: ExtendedNestedScrollView(
        key: _scrollViewKey,
        onlyOneScrollInBody: true,
        headerSliverBuilder: (context, innerBoxIsScrolled) => [
          SliverAppBar(
            pinned: true,
            title: Text(
              _displayName(user),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          ),
          SliverToBoxAdapter(child: _buildBanner(context, user)),
          SliverToBoxAdapter(child: _buildIdentity(context, user)),
          SliverPersistentHeader(
            pinned: true,
            delegate: ProfileTabsDelegate(
              ProfileTabsBar(
                controller: _tabController!,
                tabs: profileTabs
                    .map((tab) => _ProfileTabLabel(tab: tab))
                    .toList(growable: false),
              ),
            ),
          ),
        ],
        body: TweetContextScope(
          child: TabBarView(
            controller: _tabController,
            children: [
              ProfileTweets(
                user: user,
                type: 'profile',
                includeReplies: false,
                pinnedTweets: widget.profile.pinnedTweets,
                pref: prefs,
              ),
              ProfileTweets(
                user: user,
                type: 'profile',
                includeReplies: true,
                pinnedTweets: widget.profile.pinnedTweets,
                pref: prefs,
              ),
              _ProfileMediaSection(
                user: user,
                pref: prefs,
                filter: viewState.mediaFilter,
                onChanged: _viewStore!.selectMediaFilter,
              ),
              ProfileSaved(user: user),
            ],
          ),
        ),
      ),
      floatingActionButton: viewState.showBackToTop
          ? FloatingActionButton.small(
              onPressed: _scrollToTop,
              tooltip: MaterialLocalizations.of(context).reorderItemToStart,
              child: const Icon(Icons.arrow_upward),
            )
          : null,
    );
  }

  Widget _buildBanner(BuildContext context, UserWithExtra user) {
    return SizedBox(
      height: kProfileBannerHeight,
      child: ProfileBanner(
        uri: user.profileBannerUrl,
        semanticLabel: _displayName(user),
        onTap: user.profileBannerUrl == null
            ? null
            : () => _openMedia(
                context,
                user,
                user.profileBannerUrl!,
                kProfileBannerHeight,
              ),
      ),
    );
  }

  Widget _buildIdentity(BuildContext context, UserWithExtra user) {
    final description = user.description?.trim();
    final parts = description == null || description.isEmpty
        ? const <RichTextPart>[]
        : buildRichText(context, description, user.entities?.description);
    final background =
        XLookTokens.maybeOf(context)?.background ??
        Theme.of(context).scaffoldBackgroundColor;

    return ColoredBox(
      color: background,
      child: Stack(
        clipBehavior: Clip.none,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(
              kTweetHorizontalPadding,
              52,
              kTweetHorizontalPadding,
              kTweetSpace3,
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _buildName(context, user),
                const SizedBox(height: kTweetSpace1),
                Text('@${user.screenName}', style: tweetMetadataStyle(context)),
                if (parts.isNotEmpty) ...[
                  const SizedBox(height: kTweetSpace3),
                  SelectableText.rich(
                    TextSpan(
                      style: tweetBodyStyle(context).copyWith(height: 1.45),
                      children: displayRichText(parts),
                    ),
                  ),
                ],
                const SizedBox(height: kTweetSpace3),
                _buildMetadata(context, user),
                _buildCounts(context, user),
              ],
            ),
          ),
          PositionedDirectional(
            top: -kProfileAvatarSize / 2,
            start: kTweetHorizontalPadding,
            child: ProfileAvatar(
              uri: user.profileImageUrlHttps,
              semanticLabel: _displayName(user),
              onTap: user.profileImageUrlHttps == null
                  ? null
                  : () => _openMedia(
                      context,
                      user,
                      user.profileImageUrlHttps!.replaceAll(
                        '_normal',
                        '_400x400',
                      ),
                      null,
                    ),
            ),
          ),
          PositionedDirectional(
            top: kTweetSpace1,
            end: kTweetSpace2,
            child: _buildActions(context, user),
          ),
        ],
      ),
    );
  }

  Widget _buildName(BuildContext context, UserWithExtra user) {
    return Row(
      children: [
        Flexible(
          child: Text(
            _displayName(user),
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(
              context,
            ).textTheme.titleLarge?.copyWith(height: 1.18),
          ),
        ),
        if (user.verified ?? false) ...[
          const SizedBox(width: kTweetSpace1),
          Icon(Icons.verified, size: 18, color: tweetAccentColor(context)),
        ],
        if (user.protected ?? false) ...[
          const SizedBox(width: kTweetSpace1),
          Icon(
            Icons.lock_outline,
            size: 18,
            color: tweetSecondaryColor(context),
          ),
        ],
      ],
    );
  }

  Widget _buildMetadata(BuildContext context, UserWithExtra user) {
    final url = _profileUrl(user);
    return Wrap(
      children: [
        if (user.location?.isNotEmpty == true)
          ProfileMetadataItem(
            icon: Icons.location_on_outlined,
            child: Text(user.location!),
          ),
        if (url != null)
          ProfileMetadataItem(
            icon: Icons.link,
            child: InkWell(
              onTap: () => openUri(context, url.expanded),
              child: Text(
                url.display,
                style: tweetMetadataStyle(
                  context,
                ).copyWith(color: tweetAccentColor(context)),
              ),
            ),
          ),
        if (user.createdAt != null)
          ProfileMetadataItem(
            icon: Icons.calendar_today_outlined,
            child: Text(
              L10n.of(context).joined(
                DateFormat.yMMMM(
                  Localizations.localeOf(context).toString(),
                ).format(user.createdAt!),
              ),
            ),
          ),
      ],
    );
  }

  Widget _buildCounts(BuildContext context, UserWithExtra user) {
    return Wrap(
      children: [
        if (user.friendsCount != null)
          ProfileCountButton(
            count: _numberFormat.format(user.friendsCount),
            label: L10n.of(context).following,
            onTap: () => _openFollows(context, user, 'following'),
          ),
        if (user.followersCount != null)
          ProfileCountButton(
            count: _numberFormat.format(user.followersCount),
            label: L10n.of(context).followers,
            onTap: () => _openFollows(context, user, 'followers'),
          ),
      ],
    );
  }

  Widget _buildActions(BuildContext context, UserWithExtra user) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        FollowButton(user: UserSubscription.fromUser(user)),
        ProfileFeedSettingsButton(user: user),
        IconButton(
          tooltip: L10n.of(context).search,
          icon: const Icon(Icons.search),
          onPressed: () => Navigator.pushNamed(
            context,
            routeSearch,
            arguments: SearchArguments(
              1,
              focusInputOnOpen: true,
              query: 'from:@${user.screenName} ',
            ),
          ),
        ),
        IconButton(
          tooltip: L10n.of(context).share_link,
          icon: const Icon(Icons.share_outlined),
          onPressed: () =>
              Share.share('${_shareBaseUrl(context)}/${user.screenName}'),
        ),
      ],
    );
  }

  String _displayName(UserWithExtra user) {
    final name = user.name?.trim();
    return name == null || name.isEmpty ? '@${user.screenName}' : name;
  }

  ({String display, String expanded})? _profileUrl(UserWithExtra user) {
    final urls = user.entities?.url?.urls;
    if (urls == null) return null;
    for (final candidate in urls) {
      if (candidate.url != user.url) continue;
      final display = candidate.displayUrl ?? candidate.url;
      final expanded = candidate.expandedUrl ?? candidate.url;
      if (display != null && expanded != null)
        return (display: display, expanded: expanded);
    }
    return null;
  }

  String _shareBaseUrl(BuildContext context) {
    final configured = PrefService.of(
      context,
      listen: false,
    ).get(optionShareBaseUrl);
    return configured != null && configured.isNotEmpty
        ? configured
        : 'https://x.com';
  }

  void _openFollows(BuildContext context, UserWithExtra user, String type) {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => ProfileFollows(user: user, type: type),
      ),
    );
  }

  void _openMedia(
    BuildContext context,
    UserWithExtra user,
    String uri,
    double? height,
  ) {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => TweetMediaView(
          initialIndex: 0,
          media: [createMediaFromUrl(uri, height)],
          username: user.screenName!,
          tweetMedia: false,
        ),
      ),
    );
  }
}

class _ProfileTabLabel extends StatelessWidget {
  final NavigationTab tab;

  const _ProfileTabLabel({required this.tab});

  @override
  Widget build(BuildContext context) {
    return Tab(
      height: kProfileTabHeight,
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(tab.icon, size: kTweetActionIconSize),
          const SizedBox(width: kTweetSpace2),
          Text(tab.titleBuilder(context)),
        ],
      ),
    );
  }
}

class _ProfileMediaSection extends StatelessWidget {
  final UserWithExtra user;
  final BasePrefService pref;
  final MediaFilter filter;
  final ValueChanged<MediaFilter> onChanged;

  const _ProfileMediaSection({
    required this.user,
    required this.pref,
    required this.filter,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    String label(MediaFilter value) => switch (value) {
      MediaFilter.all => L10n.of(context).all,
      MediaFilter.photos => L10n.of(context).photos,
      MediaFilter.videos => L10n.of(context).videos,
    };
    return Column(
      children: [
        Align(
          alignment: AlignmentDirectional.centerStart,
          child: Padding(
            padding: const EdgeInsets.symmetric(
              horizontal: kTweetHorizontalPadding,
            ),
            child: HomeFeedSwitcher<MediaFilter>(
              selected: filter,
              options: MediaFilter.values
                  .map(
                    (value) =>
                        HomeSwitcherOption(value: value, label: label(value)),
                  )
                  .toList(growable: false),
              onSelected: onChanged,
            ),
          ),
        ),
        tweetHairlineDivider(context),
        Expanded(
          child: ProfileMediaGrid(user: user, pref: pref, filter: filter),
        ),
      ],
    );
  }
}

/// Legacy context state consumed by Tweet media in several existing modules.
/// Profile uses [TweetContextScope] rather than constructing this notifier.
class TweetContextState extends ChangeNotifier {
  bool hideSensitive;

  TweetContextState(this.hideSensitive);

  void setHideSensitive(bool value) {
    hideSensitive = value;
    notifyListeners();
  }
}
