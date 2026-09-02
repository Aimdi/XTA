import 'dart:ui' show FontFeature;

import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/tweet/tweet_skeleton.dart';
import 'package:xta/ui/motion.dart';
import 'package:xta/ui/reader_chrome.dart';
import 'package:xta/ui/x_look_theme.dart';
import 'package:xta/user.dart';

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
  final bool protected;
  final String protectedLabel;
  final Widget? bio;
  final List<Widget> metadata;
  final List<Widget> counts;
  final Widget? note;

  const ProfileIdentityHeader({
    super.key,
    required this.banner,
    required this.avatar,
    required this.actions,
    required this.name,
    required this.handle,
    required this.verified,
    required this.protected,
    required this.protectedLabel,
    this.bio,
    this.metadata = const [],
    this.counts = const [],
    this.note,
  });

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(color: tweetSurfaceColor(context)),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Stack(
            clipBehavior: Clip.none,
            children: [
              banner,
              PositionedDirectional(
                start: kTweetHorizontalPadding,
                bottom: -(kProfileAvatarSize / 2),
                child: avatar,
              ),
            ],
          ),
          SizedBox(
            height: kProfileAvatarSize / 2 + kTweetSpace1,
            child: Align(
              alignment: AlignmentDirectional.centerEnd,
              child: Padding(
                padding: const EdgeInsetsDirectional.only(
                  end: kTweetHorizontalPadding,
                ),
                child: actions,
              ),
            ),
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
                    if (verified)
                      Icon(
                        Icons.verified,
                        size: 18,
                        color: tweetReadableAccentColor(context),
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
                if (counts.isNotEmpty)
                  SingleChildScrollView(
                    scrollDirection: Axis.horizontal,
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: counts,
                    ),
                  ),
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

/// One filter convention for Posts, Media and the local Archive. The current
/// value is repeated in the tooltip and checked in the menu, so colour is
/// never the only active-state signal.
class ProfileFilterMenu<T> extends StatelessWidget {
  final T selected;
  final T defaultValue;
  final List<ProfileFilterOption<T>> options;
  final ValueChanged<T> onSelected;

  const ProfileFilterMenu({
    super.key,
    required this.selected,
    required this.defaultValue,
    required this.options,
    required this.onSelected,
  });

  @override
  Widget build(BuildContext context) {
    final selectedOption = options.firstWhere(
      (option) => option.value == selected,
    );
    final active = selected != defaultValue;

    return Semantics(
      button: true,
      selected: active,
      label: selectedOption.label,
      child: PopupMenuButton<T>(
        initialValue: selected,
        onSelected: onSelected,
        tooltip: selectedOption.label,
        position: PopupMenuPosition.under,
        icon: XtaAnimatedSwitcher(
          duration: kXtaMotionFast,
          child: Icon(
            active ? Icons.filter_alt : Icons.filter_alt_outlined,
            key: ValueKey(active),
            color: active
                ? tweetReadableAccentColor(context)
                : tweetSecondaryColor(context),
          ),
        ),
        itemBuilder: (context) => [
          for (final option in options)
            PopupMenuItem<T>(
              value: option.value,
              height: kTweetTouchTarget,
              child: Semantics(
                selected: option.value == selected,
                child: Row(
                  children: [
                    SizedBox(
                      width: kTweetSpace6,
                      child: option.value == selected
                          ? Icon(
                              Icons.check,
                              size: kTweetActionIconSize,
                              color: tweetReadableAccentColor(context),
                            )
                          : Icon(
                              option.icon,
                              size: kTweetActionIconSize,
                              color: tweetSecondaryColor(context),
                            ),
                    ),
                    const SizedBox(width: kTweetSpace2),
                    Expanded(
                      child: Text(
                        option.label,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  ],
                ),
              ),
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
