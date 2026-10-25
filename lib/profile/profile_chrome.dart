import 'dart:ui' show FontFeature, ImageFilter;

import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/tweet/tweet_skeleton.dart';
import 'package:xta/ui/motion.dart';
import 'package:xta/ui/reader_chrome.dart';
import 'package:xta/ui/verified_badges.dart';
import 'package:xta/ui/x_look_theme.dart';
import 'package:xta/user.dart';
import 'package:xta/user_verification.dart';

const double kProfileBannerHeight = 168;
const double kProfileAvatarSize = 88;
const double kProfileTabHeight = 52;
const double kProfileControlRadius = 8;

class ProfileBanner extends StatelessWidget {
  final String? uri;
  final VoidCallback? onTap;
  final String? semanticLabel;
  final double height;

  const ProfileBanner({
    super.key,
    required this.uri,
    this.onTap,
    this.semanticLabel,
    this.height = kProfileBannerHeight,
  });

  @override
  Widget build(BuildContext context) {
    final tokens = XLookTokens.maybeOf(context);
    final fallback = Color.alphaBlend(
      tweetPrimaryColor(context).withValues(alpha: 0.06),
      tokens?.background ?? Theme.of(context).scaffoldBackgroundColor,
    );
    final image = SizedBox(
      width: double.infinity,
      height: height,
      child: uri == null
          ? ColoredBox(color: fallback)
          : ExtendedImage.network(
              uri!,
              width: double.infinity,
              height: height,
              fit: BoxFit.cover,
              loadStateChanged: (state) =>
                  switch (state.extendedImageLoadState) {
                    LoadState.failed => ColoredBox(color: fallback),
                    _ => null,
                  },
            ),
    );

    return Semantics(
      label: semanticLabel,
      image: uri != null,
      button: uri != null && onTap != null,
      child: Material(
        color: fallback,
        child: InkWell(onTap: uri == null ? null : onTap, child: image),
      ),
    );
  }
}

/// How dark the banner behind the top bar is made, so white text over any
/// banner keeps at least 4.5:1 against it.
const double kProfileBarBannerScrim = 0.55;

/// The profile's banner behind the top bar once the header has scrolled away,
/// blurred and darkened as X's is, with the name in white over it. The copy
/// the header loaded is reused, so nothing new is downloaded.
class ProfileBarBanner extends StatelessWidget {
  final String uri;

  const ProfileBarBanner({super.key, required this.uri});

  @override
  Widget build(BuildContext context) {
    return ExcludeSemantics(
      child: ClipRect(
        child: Stack(
          fit: StackFit.expand,
          children: [
            ImageFiltered(
              imageFilter: ImageFilter.blur(
                sigmaX: 12,
                sigmaY: 12,
                tileMode: TileMode.clamp,
              ),
              child: ExtendedImage.network(
                uri,
                fit: BoxFit.cover,
                // No spinner or retry button in the bar: until the picture
                // is there, the darkened bar alone carries the name.
                loadStateChanged: (state) =>
                    state.extendedImageLoadState == LoadState.completed
                    ? null
                    : const SizedBox.shrink(),
              ),
            ),
            ColoredBox(
              color: Colors.black.withValues(alpha: kProfileBarBannerScrim),
            ),
          ],
        ),
      ),
    );
  }
}

/// The profile's top bar. Once the header has scrolled away ([scrolled]) the
/// name rises into the bar over the profile's banner, blurred and darkened as
/// X's is, and the bar's text, icons and status bar turn light over it.
class ProfileTopBar extends StatelessWidget {
  final String name;
  final String username;
  final String? bannerUrl;
  final bool scrolled;
  final List<Widget> actions;

  const ProfileTopBar({
    super.key,
    required this.name,
    required this.username,
    required this.bannerUrl,
    required this.scrolled,
    required this.actions,
  });

  @override
  Widget build(BuildContext context) {
    return TweenAnimationBuilder<double>(
      tween: Tween(end: scrolled ? 1 : 0),
      duration: xtaMotionDuration(context, kXtaMotionStandard),
      curve: Curves.easeOutCubic,
      builder: (context, t, _) => _barAt(context, t),
    );
  }

  /// The bar at [t], from the header in view (0) to scrolled away (1).
  SliverAppBar _barAt(BuildContext context, double t) {
    final banner = bannerUrl;
    final onBanner = banner == null ? 0.0 : t;
    final theme = Theme.of(context);
    final foreground = Color.lerp(
      theme.appBarTheme.foregroundColor ?? theme.colorScheme.onSurface,
      Colors.white,
      onBanner,
    );
    return SliverAppBar(
      pinned: true,
      floating: false,
      elevation: 0,
      scrolledUnderElevation: 0,
      surfaceTintColor: Colors.transparent,
      foregroundColor: foreground,
      // Named outright: left to Material, the actions fall back to the
      // theme's grey icon buttons as soon as the colour moves.
      iconTheme: IconThemeData(color: foreground),
      actionsIconTheme: IconThemeData(color: foreground),
      systemOverlayStyle: onBanner > 0.5 ? SystemUiOverlayStyle.light : null,
      flexibleSpace: onBanner == 0
          ? null
          : Opacity(
              opacity: onBanner,
              child: ProfileBarBanner(uri: banner!),
            ),
      titleSpacing: 0,
      title: t == 0
          ? null
          : Opacity(
              opacity: t,
              child: Transform.translate(
                offset: Offset(0, 8 * (1 - t)),
                child: _title(context, onBanner),
              ),
            ),
      actions: actions,
    );
  }

  /// The name and handle, turning white over the banner by [onBanner].
  Widget _title(BuildContext context, double onBanner) {
    final nameStyle = tweetLabelStyle(context);
    final handleStyle = tweetMetadataStyle(context).copyWith(fontSize: 11);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(
          name,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: nameStyle.copyWith(
            color: Color.lerp(nameStyle.color, Colors.white, onBanner),
          ),
        ),
        Text(
          '@$username',
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          textDirection: TextDirection.ltr,
          style: handleStyle.copyWith(
            color: Color.lerp(handleStyle.color, Colors.white70, onBanner),
          ),
        ),
      ],
    );
  }
}

class ProfileAvatar extends StatelessWidget {
  final String? uri;
  final VoidCallback? onTap;
  final String? semanticLabel;

  const ProfileAvatar({
    super.key,
    required this.uri,
    this.onTap,
    this.semanticLabel,
  });

  @override
  Widget build(BuildContext context) {
    final surface =
        XLookTokens.maybeOf(context)?.background ??
        Theme.of(context).scaffoldBackgroundColor;
    final avatar = uri == null
        ? Icon(
            Icons.person_outline,
            size: 40,
            color: tweetSecondaryColor(context),
          )
        : UserAvatar(uri: uri, size: kProfileAvatarSize);

    return Semantics(
      label: semanticLabel,
      image: uri != null,
      button: uri != null && onTap != null,
      child: Material(
        color: surface,
        shape: const CircleBorder(),
        child: InkResponse(
          onTap: uri == null ? null : onTap,
          radius: kProfileAvatarSize / 2,
          containedInkWell: true,
          customBorder: const CircleBorder(),
          child: Container(
            width: kProfileAvatarSize,
            height: kProfileAvatarSize,
            alignment: Alignment.center,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              border: Border.all(color: surface, width: 4),
            ),
            clipBehavior: Clip.antiAlias,
            child: avatar,
          ),
        ),
      ),
    );
  }
}

class ProfileMetadataItem extends StatelessWidget {
  final IconData icon;
  final Widget child;
  final VoidCallback? onTap;

  const ProfileMetadataItem({
    super.key,
    required this.icon,
    required this.child,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final content = ConstrainedBox(
      constraints: BoxConstraints(
        maxWidth:
            MediaQuery.sizeOf(context).width - (kTweetHorizontalPadding * 2),
      ),
      child: Padding(
        padding: const EdgeInsetsDirectional.only(
          end: kTweetSpace3,
          bottom: kTweetSpace1,
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 16, color: tweetSecondaryColor(context)),
            const SizedBox(width: kTweetSpace1),
            Flexible(
              child: DefaultTextStyle.merge(
                style: tweetMetadataStyle(context),
                child: child,
              ),
            ),
          ],
        ),
      ),
    );

    if (onTap == null) return content;
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(kProfileControlRadius),
      child: ConstrainedBox(
        constraints: const BoxConstraints(minHeight: kTweetTouchTarget),
        child: content,
      ),
    );
  }
}

class ProfileCountButton extends StatelessWidget {
  final String count;
  final String label;
  final VoidCallback onTap;

  const ProfileCountButton({
    super.key,
    required this.count,
    required this.label,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(kProfileControlRadius),
      child: ConstrainedBox(
        constraints: const BoxConstraints(minHeight: kTweetTouchTarget),
        child: Padding(
          padding: const EdgeInsetsDirectional.only(end: kTweetSpace4),
          child: Align(
            alignment: AlignmentDirectional.centerStart,
            widthFactor: 1,
            heightFactor: 1,
            child: Text.rich(
              TextSpan(
                children: [
                  TextSpan(
                    text: count,
                    style: tweetLabelStyle(context).copyWith(
                      fontFeatures: const [FontFeature.tabularFigures()],
                    ),
                  ),
                  TextSpan(text: ' $label', style: tweetMetadataStyle(context)),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class ProfileTabsBar extends ReaderTabBar {
  const ProfileTabsBar({
    super.key,
    required super.controller,
    required super.tabs,
  }) : super(height: kProfileTabHeight);
}

class ProfileTabsDelegate extends SliverPersistentHeaderDelegate {
  final Widget child;

  const ProfileTabsDelegate(this.child);

  @override
  double get minExtent => kProfileTabHeight;

  @override
  double get maxExtent => kProfileTabHeight;

  @override
  Widget build(
    BuildContext context,
    double shrinkOffset,
    bool overlapsContent,
  ) => child;

  @override
  bool shouldRebuild(ProfileTabsDelegate oldDelegate) =>
      oldDelegate.child != child;
}

/// Profile-specific composition: media establishes identity, then compact
/// actions, readable profile information and finally the user's content tabs.
class ProfileIdentityHeader extends StatelessWidget {
  final Widget banner;
  final Widget avatar;
  final Widget actions;
  final String name;
  final String handle;
  final bool verified;

  /// The parsed badges; when absent, [verified] alone draws the blue check.
  final UserVerification? verification;
  final bool protected;
  final String protectedLabel;
  final Widget? bio;
  final List<Widget> metadata;
  final List<Widget> counts;
  final Widget? followedBy;
  final Widget? note;

  const ProfileIdentityHeader({
    super.key,
    required this.banner,
    required this.avatar,
    required this.actions,
    required this.name,
    required this.handle,
    required this.verified,
    this.verification,
    required this.protected,
    required this.protectedLabel,
    this.bio,
    this.metadata = const [],
    this.counts = const [],
    this.followedBy,
    this.note,
  });

  @override
  Widget build(BuildContext context) {
    final badges = verification ?? UserVerification.fromFlag(verified);
    return DecoratedBox(
      decoration: BoxDecoration(color: tweetSurfaceColor(context)),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Stack(
            children: [
              Column(
                children: [
                  banner,
                  const SizedBox(height: kProfileAvatarSize / 2 + kTweetSpace1),
                ],
              ),
              PositionedDirectional(
                start: kTweetHorizontalPadding,
                bottom: kTweetSpace1,
                child: avatar,
              ),
              PositionedDirectional(
                end: kTweetHorizontalPadding,
                bottom: 0,
                child: actions,
              ),
            ],
          ),
          Padding(
            padding: const EdgeInsetsDirectional.fromSTEB(
              kTweetHorizontalPadding,
              kTweetSpace2,
              kTweetHorizontalPadding,
              kTweetSpace3,
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Wrap(
                  crossAxisAlignment: WrapCrossAlignment.center,
                  spacing: kTweetSpace1,
                  runSpacing: kTweetSpace1,
                  children: [
                    Text(
                      name,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.titleLarge?.copyWith(
                        color: tweetPrimaryColor(context),
                        fontWeight: FontWeight.w800,
                        height: 1.15,
                      ),
                    ),
                    if (badges.hasBadges)
                      VerifiedBadges(
                        verification: badges,
                        gap: kTweetSpace1,
                        leadingGap: false,
                      ),
                    if (protected)
                      Tooltip(
                        message: protectedLabel,
                        child: Icon(
                          Icons.lock_outline,
                          size: 17,
                          color: tweetSecondaryColor(context),
                        ),
                      ),
                  ],
                ),
                const SizedBox(height: kTweetSpace1),
                Text(
                  handle,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  textDirection: TextDirection.ltr,
                  style: tweetMetadataStyle(context).copyWith(fontSize: 14),
                ),
                if (bio != null) ...[
                  const SizedBox(height: kTweetSpace3),
                  bio!,
                ],
                if (metadata.isNotEmpty) ...[
                  const SizedBox(height: kTweetSpace3),
                  Wrap(children: metadata),
                ],
                if (counts.isNotEmpty) Wrap(children: counts),
                ?followedBy,
                if (note != null) note!,
              ],
            ),
          ),
          tweetHairlineDivider(context),
        ],
      ),
    );
  }
}

class ProfileActionCluster extends StatelessWidget {
  final List<Widget> children;

  const ProfileActionCluster({super.key, required this.children});

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        for (var index = 0; index < children.length; index++) ...[
          if (index > 0) const SizedBox(width: kTweetSpace2),
          ProfileActionSurface(child: children[index]),
        ],
      ],
    );
  }
}

class ProfileActionSurface extends StatelessWidget {
  final Widget child;

  const ProfileActionSurface({super.key, required this.child});

  @override
  Widget build(BuildContext context) {
    return SizedBox.square(
      dimension: kTweetTouchTarget,
      child: DecoratedBox(
        decoration: BoxDecoration(
          border: Border.all(
            color: tweetDividerColor(context),
            width: kTweetDividerThickness,
          ),
          borderRadius: BorderRadius.circular(kTweetTouchTarget / 2),
        ),
        child: Align(child: child),
      ),
    );
  }
}

@immutable
class ProfileFilterOption<T> {
  final T value;
  final String label;
  final IconData icon;

  const ProfileFilterOption({
    required this.value,
    required this.label,
    required this.icon,
  });
}

/// One filter convention for Posts, Media and the local Archive: every choice is a chip in a row directly above
/// the content it filters, one tap away. The selected chip carries a check and a filled background, so colour is
/// never the only active-state signal.
class ProfileFilterChips<T> extends StatelessWidget {
  final T selected;
  final List<ProfileFilterOption<T>> options;
  final ValueChanged<T> onSelected;

  const ProfileFilterChips({
    super.key,
    required this.selected,
    required this.options,
    required this.onSelected,
  });

  @override
  Widget build(BuildContext context) {
    final accent = tweetReadableAccentColor(context);
    final muted = tweetSecondaryColor(context);
    final theme = Theme.of(context);

    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      padding: const EdgeInsets.symmetric(horizontal: kTweetHorizontalPadding - 4),
      child: Row(
        children: [
          for (final option in options)
            Padding(
              padding: const EdgeInsetsDirectional.only(end: kTweetSpace2),
              child: Builder(builder: (context) {
                final isSelected = option.value == selected;
                return ChoiceChip(
                  selected: isSelected,
                  showCheckmark: false,
                  materialTapTargetSize: MaterialTapTargetSize.padded,
                  avatar: Icon(
                    isSelected ? Icons.check : option.icon,
                    size: 18,
                    color: isSelected ? accent : muted,
                  ),
                  label: Text(option.label, maxLines: 1, overflow: TextOverflow.ellipsis),
                  labelStyle: theme.textTheme.labelLarge?.copyWith(
                    color: isSelected ? accent : muted,
                    fontWeight: isSelected ? FontWeight.w700 : FontWeight.w500,
                  ),
                  selectedColor: accent.withValues(alpha: 0.14),
                  backgroundColor: Colors.transparent,
                  side: BorderSide(color: isSelected ? accent.withValues(alpha: 0.5) : tweetDividerColor(context)),
                  shape: const StadiumBorder(),
                  onSelected: (_) => onSelected(option.value),
                );
              }),
            ),
        ],
      ),
    );
  }
}

class ProfileEmptyState extends StatelessWidget {
  final IconData icon;
  final String message;

  const ProfileEmptyState({
    super.key,
    required this.icon,
    required this.message,
  });

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(kTweetSpace6),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 40, color: tweetSecondaryColor(context)),
            const SizedBox(height: kTweetSpace3),
            Text(
              message,
              textAlign: TextAlign.center,
              style: tweetMetadataStyle(context).copyWith(fontSize: 14),
            ),
          ],
        ),
      ),
    );
  }
}

class ProfileLoadingSkeleton extends StatelessWidget {
  const ProfileLoadingSkeleton({super.key});

  @override
  Widget build(BuildContext context) {
    final tokens = XLookTokens.maybeOf(context);
    final bone = tokens == null
        ? Theme.of(context).colorScheme.surfaceContainerHighest
        : xLookSkeletonSurface(tokens);
    return Scaffold(
      appBar: AppBar(),
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: double.infinity,
            height: kProfileBannerHeight - kToolbarHeight,
            child: ColoredBox(color: bone),
          ),
          Padding(
            padding: const EdgeInsets.all(kTweetSpace4),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _bone(bone, kProfileAvatarSize, kProfileAvatarSize, 44),
                const SizedBox(height: kTweetSpace3),
                _bone(bone, 180, 18),
                const SizedBox(height: kTweetSpace2),
                _bone(bone, 112, 13),
                const SizedBox(height: kTweetSpace4),
                _bone(bone, double.infinity, 13),
                const SizedBox(height: kTweetSpace2),
                _bone(bone, 240, 13),
              ],
            ),
          ),
          tweetHairlineDivider(context),
          const Expanded(child: TweetFeedSkeleton(count: 3)),
        ],
      ),
    );
  }

  Widget _bone(Color color, double width, double height, [double radius = 4]) {
    return Container(
      width: width,
      height: height,
      decoration: BoxDecoration(
        color: color,
        borderRadius: BorderRadius.circular(radius),
      ),
    );
  }
}

class ProfileUserListSkeleton extends StatelessWidget {
  final int count;

  const ProfileUserListSkeleton({super.key, this.count = 8});

  @override
  Widget build(BuildContext context) {
    final tokens = XLookTokens.maybeOf(context);
    final bone = tokens == null
        ? Theme.of(context).colorScheme.surfaceContainerHighest
        : xLookSkeletonSurface(tokens);
    return ListView.builder(
      physics: const NeverScrollableScrollPhysics(),
      padding: EdgeInsets.zero,
      itemCount: count,
      itemBuilder: (context, index) => Padding(
        padding: const EdgeInsets.symmetric(
          horizontal: kTweetHorizontalPadding,
          vertical: kTweetSpace3,
        ),
        child: Row(
          children: [
            Container(
              width: kTweetTouchTarget,
              height: kTweetTouchTarget,
              decoration: BoxDecoration(color: bone, shape: BoxShape.circle),
            ),
            const SizedBox(width: kTweetSpace3),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Container(width: 144, height: 13, color: bone),
                  const SizedBox(height: kTweetSpace2),
                  Container(width: 96, height: 12, color: bone),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
