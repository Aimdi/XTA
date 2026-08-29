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
                Icon(
                  isSpace ? Icons.graphic_eq : Icons.live_tv,
                  color: Colors.white,
                  size: 12,
                ),
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
