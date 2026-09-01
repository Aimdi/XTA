part of 'media_grid_item.dart';

class BroadcastGridItem extends MediaGridItem {
  /// Id from `x.com/i/broadcasts/{id}`. Null when this is a Space or we only
  /// know the video is live (UserMedia often drops the URL).
  final String? broadcastId;

  /// Id from `x.com/i/spaces/{id}`.
  final String? spaceId;

  const BroadcastGridItem({
    super.tweet,
    required super.tweetId,
    required super.username,
    required super.thumbnailUrl,
    required super.aspectRatio,
    required super.mediaIndex,
    required super.media,
    this.broadcastId,
    this.spaceId,
  });

  String? get broadcastUrl =>
      broadcastId == null ? null : broadcastUrlFor(broadcastId!);

  String? get watchUrl {
    if (spaceId != null) {
      return spaceUrlFor(spaceId!);
    }
    if (broadcastId != null) {
      return broadcastUrlFor(broadcastId!);
    }
    return null;
  }

  bool get isSpace => spaceId != null;

  /// Whether this tile already has MP4/HLS variants on the tweet media.
  ///
  /// Those play in the media lightbox. Live rooms without variants still
  /// play in-app: [LivePlayerScreen] fetches HLS from the broadcast/Space id.
  bool get hasVodVariants {
    final variants = media.videoInfo?.variants;
    return media.type == 'video' &&
        variants != null &&
        variants.any((v) => v.url != null && v.url!.isNotEmpty);
  }

  /// Whether tapping this tile can start an in-app player (lightbox or live).
  bool get canPlayInApp {
    if (hasVodVariants) {
      return true;
    }
    if (tweet != null &&
        (broadcastMediaKeyOf(tweet!) != null ||
            spaceMediaKeyOf(tweet!) != null)) {
      return true;
    }
    return (broadcastId != null && broadcastId!.isNotEmpty) ||
        (spaceId != null && spaceId!.isNotEmpty);
  }

  @override
  Widget toWidget(BuildContext context) {
    return Stack(
      fit: StackFit.expand,
      alignment: Alignment.center,
      children: [
        ColoredBox(
          color: Colors.black,
          child: thumbnailUrl.isEmpty
              ? const SizedBox.expand()
              : CappedNetworkImage(url: thumbnailUrl),
        ),
        const FritterCenterPlayButton(
          backgroundColor: Colors.black54,
          iconColor: Colors.white,
          show: true,
          isPlaying: false,
          isFinished: false,
          size: 40,
        ),
        PositionedDirectional(
          start: kTweetSpace2,
          bottom: kTweetSpace2,
          child: TweetMediaBadge(
            icon: isSpace ? Icons.graphic_eq : Icons.live_tv_outlined,
            label: isSpace
                ? L10n.of(context).spaces
                : L10n.of(context).broadcasts,
          ),
        ),
      ],
    );
  }
}
