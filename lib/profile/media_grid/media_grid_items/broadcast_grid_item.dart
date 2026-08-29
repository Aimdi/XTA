part of 'media_grid_item.dart';

class BroadcastGridItem extends MediaGridItem {
  /// Id from `x.com/i/broadcasts/{id}`. Null when we only know the video is
  /// a broadcast (UserMedia often drops the URL).
  final String? broadcastId;

  const BroadcastGridItem({
    super.tweet,
    required super.tweetId,
    required super.username,
    required super.thumbnailUrl,
    required super.aspectRatio,
    required super.mediaIndex,
    required super.media,
    this.broadcastId,
  });

  String? get broadcastUrl =>
      broadcastId == null ? null : broadcastUrlFor(broadcastId!);

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
        Positioned(
          left: 6,
          bottom: 6,
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 2),
            decoration: BoxDecoration(
              color: Colors.black87,
              borderRadius: BorderRadius.circular(4),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(Icons.live_tv, color: Colors.white, size: 12),
                const SizedBox(width: 4),
                Text(
                  L10n.of(context).broadcasts,
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 10,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}
