import 'dart:ui' show FontFeature;

import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/tweet/tweet_skeleton.dart';
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

  const ProfileMetadataItem({
    super.key,
    required this.icon,
    required this.child,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsetsDirectional.only(
        end: kTweetSpace3,
        bottom: kTweetSpace1,
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 16, color: tweetSecondaryColor(context)),
          const SizedBox(width: kTweetSpace1),
          DefaultTextStyle.merge(
            style: tweetMetadataStyle(context),
            child: child,
          ),
        ],
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
          child: Center(
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

class ProfileLoadingSkeleton extends StatelessWidget {
  const ProfileLoadingSkeleton({super.key});

  @override
  Widget build(BuildContext context) {
    final bone =
        XLookTokens.maybeOf(context)?.border ??
        Theme.of(context).colorScheme.surfaceContainerHighest;
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
    final bone =
        XLookTokens.maybeOf(context)?.border ??
        Theme.of(context).colorScheme.surfaceContainerHighest;
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
