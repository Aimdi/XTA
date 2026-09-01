import 'package:flutter/material.dart';
import 'package:infinite_scroll_pagination/infinite_scroll_pagination.dart';
import 'package:xta/constants.dart';
import 'package:xta/profile/media_grid/media_grid_items/media_grid_item.dart';
import 'package:xta/status.dart';
import 'package:xta/tweet/_media.dart';
import 'package:xta/tweet/broadcasts.dart';
import 'package:xta/tweet/live_player_screen.dart';

/// Fullscreen swipeable viewer paging across all loaded items of a media
/// grid, with an "open post" escape hatch to the tweet a page belongs to.
///
/// For paginated grids it listens to the [PagingController] so pages fetched
/// while swiping (via [TweetMediaView.onNearEnd]) appear seamlessly; static
/// grids pass their fixed item list instead.
class MediaGridLightbox extends StatefulWidget {
  final PagingController<int, MediaGridItem>? controller;
  final List<MediaGridItem> staticItems;
  final int initialIndex;

  const MediaGridLightbox({
    super.key,
    this.controller,
    this.staticItems = const [],
    required this.initialIndex,
  });

  @override
  State<MediaGridLightbox> createState() => _MediaGridLightboxState();
}

class _MediaGridLightboxState extends State<MediaGridLightbox> {
  List<MediaGridItem> get _items =>
      widget.controller?.value.items ?? widget.staticItems;

  @override
  void initState() {
    super.initState();
    widget.controller?.addListener(_onPagingChanged);
  }

  @override
  void dispose() {
    widget.controller?.removeListener(_onPagingChanged);
    super.dispose();
  }

  void _onPagingChanged() {
    if (mounted) {
      setState(() {});
    }
  }

  void _openPost(MediaViewEntry entry) {
    // Hand the source tweet over as the instant preview, so the post shows
    // immediately (same path as opening from a tweet card) instead of relying
    // on the conversation fetch alone.
    MediaGridItem? item;
    for (final candidate in _items) {
      if (candidate.tweetId == entry.tweetId &&
          candidate.mediaIndex == entry.mediaIndex) {
        item = candidate;
        break;
      }
    }

    Navigator.pushNamed(
      context,
      routeStatus,
      arguments: StatusScreenArguments(
        id: entry.tweetId!,
        username: entry.username,
        tweetOpened: true,
        initialMediaIndex: entry.mediaIndex,
        initialTweet: item?.tweet,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return TweetMediaView.entries(
      initialIndex: widget.initialIndex,
      entries: [
        for (final item in _items)
          (
            media: item.media,
            username: item.username,
            tweetId: item.tweetId,
            mediaIndex: item.mediaIndex,
          ),
      ],
      onOpenPost: _openPost,
      onNearEnd: widget.controller?.fetchNextPage,
    );
  }
}

void openMediaLightbox(
  BuildContext context, {
  PagingController<int, MediaGridItem>? controller,
  List<MediaGridItem> staticItems = const [],
  required int initialIndex,
}) {
  pushTweetMediaViewer<void>(
    context,
    MediaGridLightbox(
      controller: controller,
      staticItems: staticItems,
      initialIndex: initialIndex,
    ),
  );
}

/// Broadcasts with a playable VOD open in the in-app lightbox. Live rooms
/// and Spaces open the in-app HLS player (same stream the tweet card uses).
void openMediaGridItem(
  BuildContext context, {
  required MediaGridItem item,
  required int index,
  PagingController<int, MediaGridItem>? controller,
  List<MediaGridItem> staticItems = const [],
}) {
  if (item is BroadcastGridItem) {
    if (item.hasVodVariants) {
      openMediaLightbox(
        context,
        controller: controller,
        staticItems: staticItems,
        initialIndex: index,
      );
      return;
    }
    final request = _liveRequestFor(item);
    if (request.canResolve) {
      openLivePlayer(context, request);
      return;
    }
    Navigator.pushNamed(
      context,
      routeStatus,
      arguments: StatusScreenArguments(
        id: item.tweetId,
        username: item.username,
        tweetOpened: true,
        initialTweet: item.tweet,
      ),
    );
    return;
  }
  openMediaLightbox(
    context,
    controller: controller,
    staticItems: staticItems,
    initialIndex: index,
  );
}

LivePlayRequest _liveRequestFor(BroadcastGridItem item) {
  final thumb = item.thumbnailUrl.isEmpty ? null : item.thumbnailUrl;
  if (item.tweet != null) {
    return LivePlayRequest.fromTweet(item.tweet!).copyWith(
      imageUrl: thumb,
      aspectRatio: item.aspectRatio,
    );
  }
  return LivePlayRequest(
    broadcastId: item.broadcastId,
    spaceId: item.spaceId,
    imageUrl: thumb,
    aspectRatio: item.aspectRatio,
  );
}
