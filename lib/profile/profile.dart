import 'package:extended_image/extended_image.dart';
import 'package:extended_nested_scroll_view/extended_nested_scroll_view.dart';
import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/profile/_follows.dart';
import 'package:xta/profile/_media_grid.dart';
import 'package:xta/profile/media_grid/media_grid_items/media_grid_item.dart';
import 'package:xta/profile/_saved.dart';
import 'package:xta/profile/_tweets.dart';
import 'package:xta/profile/profile_feed_settings.dart';
import 'package:xta/profile/profile_model.dart';
import 'package:xta/profile/profile_note.dart';
import 'package:xta/search/search.dart';
import 'package:xta/tweet/_media.dart';
import 'package:xta/tweet/sensitive_media_gate.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/user.dart';
import 'package:xta/plugins/plugin_links.dart';
import 'package:xta/utils/rich_text.dart';
import 'package:intl/intl.dart';
import 'package:measure_size/measure_size.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:share_plus/share_plus.dart';

typedef TabTitleBuilder = String Function(BuildContext context);

class NavigationTab {
  final ProfileTabs id;
  final TabTitleBuilder titleBuilder;
  final IconData icon;

  NavigationTab(this.id, this.titleBuilder, this.icon);
}

final List<NavigationTab> profileTabs = [
  NavigationTab(ProfileTabs.posts, (c) => L10n.of(c).tweets, Icons.wysiwyg_outlined),
  NavigationTab(ProfileTabs.postsAndReplies, (c) => L10n.of(c).tweets_and_replies, Icons.mode_comment_outlined),
  NavigationTab(ProfileTabs.media, (c) => L10n.of(c).media, Icons.smart_display_outlined),
  NavigationTab(ProfileTabs.saved, (c) => L10n.of(c).saved, Icons.bookmark_border),
];

class ProfileScreenArguments {
  final String? id;
  final String? screenName;
  final int? tabIndex;

  ProfileScreenArguments(this.id, this.screenName, this.tabIndex);

  factory ProfileScreenArguments.fromId(String id, int? tabIndex) {
    return ProfileScreenArguments(id, null, tabIndex);
  }

  factory ProfileScreenArguments.fromScreenName(String screenName, int? tabIndex) {
    return ProfileScreenArguments(null, screenName, tabIndex);
  }
}

class ProfileScreen extends StatelessWidget {
  const ProfileScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final args = ModalRoute.of(context)!.settings.arguments as ProfileScreenArguments;

    return Provider(
        create: (context) {
          return ProfileModel()..loadProfileByScreenName(args.screenName!);
        },
        child: _ProfileScreen(id: args.id, screenName: args.screenName, tabIndex: args.tabIndex));
  }
}

class _ProfileScreen extends StatelessWidget {
  final String? id;
  final String? screenName;
  final int? tabIndex;

  const _ProfileScreen({required this.id, required this.screenName, required this.tabIndex});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: ScopedBuilder<ProfileModel, Profile>.transition(
        store: context.read<ProfileModel>(),
        onError: (_, error) => FullPageErrorWidget(
          error: error,
          stackTrace: null,
          prefix: L10n.of(context).unable_to_load_the_profile,
          onRetry: () {
            if (id != null) {
              return context.read<ProfileModel>().loadProfileById(id!);
            } else {
              return context.read<ProfileModel>().loadProfileByScreenName(screenName!);
            }
          },
        ),
        onLoading: (_) => const Center(child: CircularProgressIndicator()),
        onState: (_, state) => ProfileScreenBody(profile: state, defaultTabIndex: tabIndex),
      ),
    );
  }
}

class ProfileScreenBody extends StatefulWidget {
  final Profile profile;
  final int? defaultTabIndex;

  const ProfileScreenBody({super.key, required this.profile, required this.defaultTabIndex});

  @override
  State<StatefulWidget> createState() => _ProfileScreenBodyState();
}

class _ProfileScreenBodyState extends State<ProfileScreenBody> with TickerProviderStateMixin {
  static const defaultHeight = 256.12345;

  final GlobalKey<NestedScrollViewState> nestedScrollViewKey = GlobalKey();

  late TabController _tabController;

  MediaFilter _mediaFilter = MediaFilter.all;

  bool _showBackToTopButton = false;

  double descriptionHeight = defaultHeight;
  double metadataHeight = defaultHeight;

  bool descriptionResized = false;
  bool metadataResized = false;

  NumberFormat numberFormat = NumberFormat.compact();

  @override
  void initState() {
    super.initState();

    WidgetsBinding.instance.addPostFrameCallback((timeStamp) {
      var nestedScrollViewState = nestedScrollViewKey.currentState;
      if (nestedScrollViewState == null) {
        return;
      }

      nestedScrollViewState.innerController.addListener(_listen);
    });


    var description = widget.profile.user.description;
    if (description == null || description.isEmpty) {
      descriptionHeight = 0;
      descriptionResized = true;
    }
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();

    ProfileTabs defaultProfileTab = ProfileTabs.values.byName(PrefService.of(context).get(optionDefaultProfileTab));
    final int initialTabIdx = widget.defaultTabIndex ?? profileTabs.indexWhere((e) => e.id == defaultProfileTab);

    _tabController = TabController(length: 4, vsync: this, initialIndex: initialTabIdx);
  }

  @override
  void dispose() {
    nestedScrollViewKey.currentState?.innerController.removeListener(_listen);

    super.dispose();
  }

  void _listen() {
    var nestedScrollViewState = nestedScrollViewKey.currentState;
    if (nestedScrollViewState == null) {
      return;
    }

    if (!nestedScrollViewState.innerController.hasClients) {
      return;
    }

    // Show the "scroll to top" button if we scroll down a bit, and hide it if we go back above
    if (nestedScrollViewState.innerController.positions.any((element) => element.pixels >= 400)) {
      if (!_showBackToTopButton) {
        setState(() {
          _showBackToTopButton = true;
        });
      }
    } else {
      if (_showBackToTopButton) {
        setState(() {
          _showBackToTopButton = false;
        });
      }
    }
  }

  void _scrollToTop() {
    // We scroll the outer controller (the whole nested scroll view and children) to the top
    // TODO: No animation due to Flutter crashing on huge lists (https://github.com/flutter/flutter/issues/52207) (#607)
    nestedScrollViewKey.currentState?.outerController.jumpTo(0);
  }

  @override
  Widget build(BuildContext context) {
    // TODO: This shouldn't happen before the profile is loaded
    var user = widget.profile.user;
    if (user.idStr == null) {
      return Container();
    }

    // Make the app bar height the correct aspect ratio based on the header image size (1500x500)
    var mediaQuery = MediaQuery.of(context);
    var deviceSize = mediaQuery.size;
    var bannerHeight = deviceSize.width * (500 / 1500);
    var avatarHeight = 80;

    var profileImageTop = bannerHeight + 16 - 36 - mediaQuery.padding.top;
    var profileStuffTop = bannerHeight + 36;

    var theme = Theme.of(context);

    var banner = user.profileBannerUrl;
    var bannerImage = banner == null
        ? Container(height: bannerHeight, color: Colors.white)
        : GestureDetector(
      child: ExtendedImage.network(banner,
          fit: BoxFit.fitWidth,
          height: bannerHeight,
          cacheWidth: (deviceSize.width * mediaQuery.devicePixelRatio).ceil()),
      onTap: () {
        Navigator.push(
          context,
          MaterialPageRoute(
            builder: (context) =>
                TweetMediaView(
                    initialIndex: 0,
                    media: [createMediaFromUrl(user.profileBannerUrl, bannerHeight)],
                    username: user.screenName ?? "Unknown",
                    tweetMedia: false),
          ),
        );
      },
    );

    // The height of the app bar should be all the inner components, plus any margins
    var appBarHeight = profileStuffTop + avatarHeight + metadataHeight + 8 + descriptionHeight;

    var metadataTextStyle = const TextStyle(fontSize: 12.5);
    var prefs = PrefService.of(context, listen: false);

    var shareBaseUrlOption = prefs.get(optionShareBaseUrl);
    var shareBaseUrl =
        shareBaseUrlOption != null && shareBaseUrlOption.isNotEmpty ? shareBaseUrlOption : 'https://x.com';

    List<RichTextPart> descParts = [];
    if (user.description != null && user.description!.isNotEmpty) {
      descParts = buildRichText(context, user.description!, user.entities!.description!);
    }

    return Scaffold(
      body: Stack(children: [
        ExtendedNestedScrollView(
          key: nestedScrollViewKey,
          onlyOneScrollInBody: true,
          headerSliverBuilder: (context, innerBoxIsScrolled) {
            return [
              SliverAppBar(
                  expandedHeight: appBarHeight,
                  floating: true,
                  pinned: true,
                  snap: false,
                  forceElevated: innerBoxIsScrolled,
                  automaticallyImplyLeading: false,
                  bottom: AppBar(
                      automaticallyImplyLeading: false,
                      backgroundColor: theme.colorScheme.surface,
                      flexibleSpace: AnimatedBuilder(
                        animation: _tabController,
                        builder: (context, _) => TabBar(
                          controller: _tabController,
                          indicator: UnderlineTabIndicator(
                            borderRadius: const BorderRadius.vertical(top: Radius.circular(2)),
                            borderSide: BorderSide(width: 3, color: theme.colorScheme.onSurface),
                          ),
                          indicatorSize: TabBarIndicatorSize.label,
                          labelColor: theme.colorScheme.onSurface,
                          unselectedLabelColor: theme.colorScheme.onSurfaceVariant,
                          tabs: [
                            for (final (i, t) in profileTabs.indexed)
                              Tab(
                                  child: _ProfileTabLabel(
                                tab: t,
                                selected: _tabController.index == i,
                                trailing: t.id == ProfileTabs.media
                                    ? _MediaFilterButton(
                                        value: _mediaFilter,
                                        onChanged: (filter) => setState(() => _mediaFilter = filter),
                                      )
                                    : null,
                              )),
                          ],
                          dividerColor: theme.colorScheme.surfaceBright.withAlpha(150),
                        ),
                      )),
                  flexibleSpace: FlexibleSpaceBar(
                    centerTitle: true,
                    background: SafeArea(
                      top: false,
                      child: Stack(children: <Widget>[
                        Container(alignment: Alignment.topCenter, child: bannerImage),
                        DecoratedBox(
                          decoration: BoxDecoration(
                            gradient: LinearGradient(
                              begin: Alignment.bottomCenter,
                              end: Alignment.topCenter,
                              colors: <Color>[
                                theme.colorScheme.surface,
                                Color.fromARGB(
                                    100,
                                    (theme.colorScheme.surface.r * 255.0).round(),
                                    (theme.colorScheme.surface.g * 255.0).round(),
                                    (theme.colorScheme.surface.b * 255.0).round())
                              ],
                            ),
                          ),
                        ),
                        Container(
                          padding: const EdgeInsets.symmetric(vertical: 16, horizontal: 0),
                          child: Row(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Flexible(
                                child: Container(
                                  margin: EdgeInsets.fromLTRB(16, profileStuffTop, 16, 0),
                                  child: Column(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      Row(
                                        children: [
                                          Flexible(
                                            child: Text(user.name!,
                                                maxLines: 1,
                                                overflow: TextOverflow.ellipsis,
                                                style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w700)),
                                          ),
                                          if (user.verified ?? false) const SizedBox(width: 6),
                                          if (user.verified ?? false)
                                            Icon(Icons.verified, size: 24, color: theme.colorScheme.primary),
                                          if (user.protected ?? false) const SizedBox(width: 6),
                                          if (user.protected ?? false)
                                            Icon(Icons.lock, size: 24, color: theme.colorScheme.primary)
                                        ],
                                      ),
                                      Container(
                                        margin: const EdgeInsets.only(bottom: 8),
                                        child: Text('@${(user.screenName!)}',
                                            style: TextStyle(
                                                fontSize: 14,
                                                color: theme.brightness == Brightness.dark
                                                    ? Colors.white70
                                                    : Colors.black54)),
                                      ),
                                      if (user.description != null && user.description!.isNotEmpty)
                                        MeasureSize(
                                          onChange: (size) {
                                            setState(() {
                                              descriptionHeight = size.height;
                                              descriptionResized = true;
                                            });
                                          },
                                          child: Container(
                                              margin: const EdgeInsets.only(bottom: 8),
                                              child: SelectableText.rich(
                                                  minLines: 1,
                                                  maxLines: 5,
                                                  TextSpan(
                                                      style: TextStyle(
                                                          height: 1.4,
                                                          color: theme.brightness == Brightness.dark
                                                              ? Colors.white
                                                              : Colors.black),
                                                      children: displayRichText(descParts)
                                                  ))),
                                        ),
                                      MeasureSize(
                                          onChange: (size) {
                                            setState(() {
                                              metadataHeight = size.height;
                                              metadataResized = true;
                                            });
                                          },
                                          child: Padding(
                                            padding: const EdgeInsets.only(bottom: 8.0),
                                            child: Column(
                                                crossAxisAlignment: CrossAxisAlignment.start,
                                                mainAxisAlignment: MainAxisAlignment.end,
                                                children: [
                                                  Scrollbar(
                                                      child: SingleChildScrollView(
                                                          scrollDirection: Axis.horizontal,
                                                          child: Row(children: [
                                                            if (user.location != null && user.location!.isNotEmpty)
                                                              Padding(
                                                                padding: const EdgeInsets.symmetric(
                                                                    vertical: 2, horizontal: 0),
                                                                child: Row(
                                                                  crossAxisAlignment: CrossAxisAlignment.center,
                                                                  children: [
                                                                    Icon(Icons.location_on_outlined,
                                                                        size: 14, color: theme.hintColor),
                                                                    const SizedBox(width: 4),
                                                                    Text(user.location!, style: metadataTextStyle),
                                                                    const SizedBox(
                                                                      width: 8,
                                                                    ),
                                                                  ],
                                                                ),
                                                              ),
                                                            if (user.url != null && user.url!.isNotEmpty)
                                                              Padding(
                                                                  padding: const EdgeInsets.symmetric(
                                                                      vertical: 2, horizontal: 0),
                                                                  child: Row(
                                                                    crossAxisAlignment: CrossAxisAlignment.center,
                                                                    children: [
                                                                      Icon(Icons.link,
                                                                          size: 14, color: theme.hintColor),
                                                                      const SizedBox(width: 4),
                                                                      Builder(builder: (context) {
                                                                        var url = user.entities?.url?.urls?.firstWhere(
                                                                            (element) => element.url == user.url);

                                                                        if (url == null) {
                                                                          return Container();
                                                                        }

                                                                        var displayUrl = url.displayUrl ?? url.url;
                                                                        var expandedUrl = url.expandedUrl ?? url.url;

                                                                        var textStyle = metadataTextStyle;
                                                                        if (displayUrl == null || expandedUrl == null) {
                                                                          return Text(L10n.current.unsupported_url,
                                                                              style: textStyle.copyWith(
                                                                                  color: theme.hintColor));
                                                                        }

                                                                        return InkWell(
                                                                          child: Text(displayUrl,
                                                                              style: textStyle.copyWith(
                                                                                  color: Theme.of(context)
                                                                                      .colorScheme
                                                                                      .primary)),
                                                                          onTap: () => openLink(context, expandedUrl),
                                                                        );
                                                                      }),
                                                                      const SizedBox(
                                                                        width: 8,
                                                                      ),
                                                                    ],
                                                                  )),
                                                            if (user.createdAt != null)
                                                              Padding(
                                                                padding: const EdgeInsets.symmetric(
                                                                    vertical: 2, horizontal: 0),
                                                                child: Row(
                                                                  crossAxisAlignment: CrossAxisAlignment.center,
                                                                  children: [
                                                                    Icon(Icons.calendar_today_outlined,
                                                                        size: 14, color: theme.hintColor),
                                                                    const SizedBox(width: 4),
                                                                    Text(
                                                                        L10n.of(context).joined(DateFormat('MMMM yyyy')
                                                                            .format(user.createdAt!)),
                                                                        style: metadataTextStyle),
                                                                  ],
                                                                ),
                                                              ),
                                                          ]))),
                                                  Scrollbar(
                                                      child: SingleChildScrollView(
                                                          scrollDirection: Axis.horizontal,
                                                          child: Row(
                                                            children: [
                                                              if (user.friendsCount != null)
                                                                InkWell(
                                                                    onTap: () => Navigator.of(context).push(
                                                                        MaterialPageRoute(
                                                                            builder: ((context) => ProfileFollows(
                                                                                user: user, type: 'following')))),
                                                                    child: Padding(
                                                                      padding: const EdgeInsets.symmetric(
                                                                          vertical: 2, horizontal: 0),
                                                                      child: Row(
                                                                        crossAxisAlignment: CrossAxisAlignment.center,
                                                                        children: [
                                                                          Text.rich(TextSpan(children: [
                                                                            TextSpan(
                                                                                text: numberFormat.format(
                                                                                    widget.profile.user.friendsCount),
                                                                                style: metadataTextStyle.copyWith(
                                                                                    fontWeight: FontWeight.w700)),
                                                                            TextSpan(
                                                                                text:
                                                                                    ' ${L10n.current.following.toLowerCase()}',
                                                                                style: metadataTextStyle)
                                                                          ])),
                                                                          const SizedBox(
                                                                            width: 8,
                                                                          ),
                                                                        ],
                                                                      ),
                                                                    )),
                                                              if (user.followersCount != null)
                                                                InkWell(
                                                                    onTap: () => Navigator.of(context).push(
                                                                        MaterialPageRoute(
                                                                            builder: ((context) => ProfileFollows(
                                                                                user: user, type: 'followers')))),
                                                                    child: Padding(
                                                                      padding: const EdgeInsets.symmetric(
                                                                          vertical: 2, horizontal: 0),
                                                                      child: Row(
                                                                        crossAxisAlignment: CrossAxisAlignment.center,
                                                                        children: [
                                                                          Text.rich(TextSpan(children: [
                                                                            TextSpan(
                                                                                text: numberFormat.format(
                                                                                    widget.profile.user.followersCount),
                                                                                style: metadataTextStyle.copyWith(
                                                                                    fontWeight: FontWeight.w700)),
                                                                            TextSpan(
                                                                                text:
                                                                                    ' ${L10n.current.followers.toLowerCase()}',
                                                                                style: metadataTextStyle)
                                                                          ])),
                                                                        ],
                                                                      ),
                                                                    )),
                                                            ],
                                                          )))
                                                ]),
                                          )),
                                      if (user.idStr != null) ProfileNoteCard(userId: user.idStr!),
                                    ],
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),
                        // The follow / feed-settings controls sit by the
                        // avatar, where X keeps its profile actions.
                        Container(
                          alignment: Alignment.topRight,
                          margin: EdgeInsets.fromLTRB(128, profileImageTop + 64, 16, 16),
                          child: Row(mainAxisAlignment: MainAxisAlignment.end, children: [
                            ProfileFeedSettingsButton(
                              user: user,
                              color: theme.colorScheme.primary,
                            ),
                            FollowButton(
                              user: UserSubscription.fromUser(user),
                              color: theme.colorScheme.primary,
                            ),
                          ]),
                        ),
                        // Circular translucent buttons floating over the banner,
                        // as on X: a back affordance a pushed profile otherwise
                        // lacked on screen, then search and share.
                        Positioned(
                          top: 0,
                          left: 4,
                          right: 4,
                          child: SafeArea(
                            bottom: false,
                            child: Row(
                              children: [
                                _BannerButton(
                                  icon: Icons.arrow_back,
                                  tooltip: MaterialLocalizations.of(context).backButtonTooltip,
                                  onPressed: () => Navigator.maybePop(context),
                                ),
                                const Spacer(),
                                _BannerButton(
                                  icon: Icons.search,
                                  tooltip: L10n.of(context).search,
                                  onPressed: () => Navigator.pushNamed(context, routeSearch,
                                      arguments: SearchArguments(1,
                                          focusInputOnOpen: true, query: 'from:@${(user.screenName!)} ')),
                                ),
                                const SizedBox(width: 8),
                                _BannerButton(
                                  icon: Icons.share,
                                  tooltip: L10n.of(context).share_link,
                                  onPressed: () => Share.share("$shareBaseUrl/${user.screenName}"),
                                ),
                              ],
                            ),
                          ),
                        ),
                        Container(
                          alignment: Alignment.topLeft,
                          margin: EdgeInsets.fromLTRB(16, profileImageTop, 16, 16),
                          child: CircleAvatar(
                            radius: 50,
                            backgroundColor: Colors.white,
                            child: GestureDetector(
                              child: UserAvatar(uri: user.profileImageUrlHttps, size: 96),
                              onTap: () {
                                Navigator.push(
                                  context,
                                  MaterialPageRoute(
                                    builder: (context) =>
                                        TweetMediaView(
                                            initialIndex: 0,
                                            media: [createMediaFromUrl(user.profileImageUrlHttps?.replaceAll("_normal", "_400x400"), null)],
                                            username: user.screenName ?? "Unknown",
                                            tweetMedia: false),
                                  ),
                                );
                              },
                            ),
                          ),
                        )
                      ]),
                    ),
                  ))
            ];
          },
          body: MultiProvider(
            providers: [
              ChangeNotifierProvider<TweetContextState>(
                  create: (_) => TweetContextState.fromPrefs(prefs)),
            ],
            child: TabBarView(
              controller: _tabController,
              children: [
                ProfileTweets(
                    user: user,
                    type: 'profile',
                    includeReplies: false,
                    pinnedTweets: widget.profile.pinnedTweets,
                    pref: prefs),
                ProfileTweets(
                    user: user,
                    type: 'profile',
                    includeReplies: true,
                    pinnedTweets: widget.profile.pinnedTweets,
                    pref: prefs),
                ProfileMediaGrid(user: user, pref: prefs, filter: _mediaFilter),
                ProfileSaved(user: user),
              ],
            ),
          ),
        ),

        // If we haven't resized the description widget yet, display an overlay container so we don't see the resize
        // TODO: This flickers
        AnimatedSwitcher(
          duration: const Duration(milliseconds: 150),
          child: descriptionResized == true && metadataResized == true
              ? Container(key: const Key('loaded'))
              : Container(
                  key: const Key('waiting'),
                  height: double.infinity,
                  color: theme.colorScheme.surface,
                ),
        )
      ]),
      floatingActionButton: _showBackToTopButton == false
          ? null
          : FloatingActionButton(
              onPressed: _scrollToTop,
              child: const Icon(Icons.arrow_upward),
            ),
    );
  }
}

/// A circular translucent button floating over the profile banner, the way X
/// draws the back / search / more controls there — legible over any banner
/// because it carries its own scrim rather than relying on the image behind it.
class _BannerButton extends StatelessWidget {
  final IconData icon;
  final String tooltip;
  final VoidCallback onPressed;

  const _BannerButton({required this.icon, required this.tooltip, required this.onPressed});

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.black38,
      shape: const CircleBorder(),
      clipBehavior: Clip.antiAlias,
      child: IconButton(
        icon: Icon(icon, size: 20, color: Colors.white),
        tooltip: tooltip,
        onPressed: onPressed,
      ),
    );
  }
}

/// X-style profile tab: always shows its symbol, and expands with the
/// localized label while selected.
class _ProfileTabLabel extends StatelessWidget {
  final NavigationTab tab;
  final bool selected;
  final Widget? trailing;

  const _ProfileTabLabel({required this.tab, required this.selected, this.trailing});

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(tab.icon, size: 22),
        if (selected) ...[
          const SizedBox(width: 6),
          Flexible(
            child: Text(tab.titleBuilder(context),
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(fontWeight: FontWeight.w700)),
          ),
          if (trailing != null) trailing!,
        ],
      ],
    );
  }
}

/// The chevron on the selected Media tab, which opens the photos/videos filter.
///
/// It is its own tap target rather than a second meaning for the tab itself:
/// tapping the tab still returns the grid to the top, as every other tab does.
class _MediaFilterButton extends StatelessWidget {
  final MediaFilter value;
  final ValueChanged<MediaFilter> onChanged;

  const _MediaFilterButton({required this.value, required this.onChanged});

  String _labelFor(BuildContext context, MediaFilter filter) => switch (filter) {
        MediaFilter.all => L10n.of(context).all,
        MediaFilter.photos => L10n.of(context).photos,
        MediaFilter.videos => L10n.of(context).videos,
      };

  IconData _iconFor(MediaFilter filter) => switch (filter) {
        MediaFilter.all => Icons.perm_media_outlined,
        MediaFilter.photos => Icons.photo_library_outlined,
        MediaFilter.videos => Icons.video_library_outlined,
      };

  @override
  Widget build(BuildContext context) {
    return PopupMenuButton<MediaFilter>(
      initialValue: value,
      onSelected: onChanged,
      tooltip: L10n.of(context).media,
      padding: EdgeInsets.zero,
      itemBuilder: (context) => [
        for (final filter in MediaFilter.values)
          PopupMenuItem(
            value: filter,
            child: Row(
              children: [
                Icon(_iconFor(filter), size: 20),
                const SizedBox(width: 12),
                Text(_labelFor(context, filter)),
              ],
            ),
          ),
      ],
      child: Padding(
        padding: const EdgeInsets.only(left: 2),
        child: Icon(
          Icons.expand_more,
          size: 18,
          color: value == MediaFilter.all ? null : Theme.of(context).colorScheme.primary,
        ),
      ),
    );
  }
}

class TweetContextState extends ChangeNotifier {
  bool hideSensitive;

  TweetContextState(this.hideSensitive);

  factory TweetContextState.fromPrefs(BasePrefService prefs) => TweetContextState(initialHideSensitive(prefs));

  void setHideSensitive(bool value) {
    hideSensitive = value;
    notifyListeners();
  }

  Future<void> alwaysShowSensitive(BasePrefService prefs) async {
    await prefs.set(optionAlwaysShowSensitiveMedia, true);
    setHideSensitive(false);
  }
}
