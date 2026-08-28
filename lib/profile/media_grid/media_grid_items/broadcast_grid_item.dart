part of 'media_grid_item.dart';

class BroadcastGridItem extends MediaGridItem {
  const BroadcastGridItem({
    super.tweet,
    required super.tweetId,
    required super.username,
    required super.thumbnailUrl,
    required super.aspectRatio,
    required super.mediaIndex,
    required super.media,
  });

  @override
  Widget toWidget(BuildContext context) {
    return Stack(
      fit: StackFit.expand,
      alignment: Alignment.center,
      children: [
        ColoredBox(
          color: Colors.black,
          child: CappedNetworkImage(url: thumbnailUrl),
        ),
        const FritterCenterPlayButton(
          backgroundColor: Colors.black54,
          iconColor: Colors.white,
          show: true,
          isPlaying: false,
          isFinished: false,
          size: 40,
        ),
        const Positioned(
          left: 6,
          bottom: 6,
          child: Icon(Icons.live_tv, color: Colors.white, size: 16),
        ),
      ],
    );
  }
}
