import 'package:dart_twitter_api/api/media/data/media.dart';
import 'package:flutter/material.dart';
import 'package:xta/client/client.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/tweet/_video.dart';
import 'package:xta/tweet/_video_controls.dart';
import 'package:xta/tweet/broadcasts.dart';
import 'package:xta/tweet/media_strip.dart';
import 'package:xta/ui/capped_network_image.dart';
import 'package:xta/utils/paging.dart';
import 'package:xta/utils/urls.dart';

part 'gif_grid_item.dart';
part 'video_grid_item.dart';
part 'photo_grid_item.dart';
part 'broadcast_grid_item.dart';

sealed class MediaGridItem {
  // The source tweet when known, handed to the status screen as the instant
  // preview so opening a post from the lightbox never waits on a fetch.
  final TweetWithCard? tweet;
  final String tweetId;
  final String username;
  final String thumbnailUrl;
  final double aspectRatio;
  final int mediaIndex;
  final Media media;

  const MediaGridItem({
    this.tweet,
    required this.tweetId,
    required this.username,
    required this.thumbnailUrl,
    required this.aspectRatio,
    required this.mediaIndex,
    required this.media,
  });

  Widget toWidget(BuildContext context);
}

double _aspectRatioFor(Media m) {
  return mediaItemAspect(
    type: m.type,
    videoAspect: m.videoInfo?.aspectRatio,
    thumbW: m.sizes?.large?.w,
    thumbH: m.sizes?.large?.h,
  );
}

MediaGridItem? _itemFor(
  Media m,
  String tweetId,
  String username,
  int mediaIndex, [
  TweetWithCard? tweet,
]) {
  final url = m.mediaUrlHttps;
  if (url == null) return null;
  final ar = _aspectRatioFor(m);
  final broadcastId = tweet != null
      ? broadcastIdOf(tweet)
      : broadcastIdIn(m.expandedUrl) ??
          broadcastIdIn(m.displayUrl) ??
          broadcastIdIn(m.url);
  final broadcast =
      broadcastId != null || (tweet != null && isBroadcastCard(tweet.card));
  switch (m.type) {
    case 'photo':
      return PhotoGridItem(
        tweet: tweet,
        tweetId: tweetId,
        username: username,
        thumbnailUrl: url,
        aspectRatio: ar,
        mediaIndex: mediaIndex,
        media: m,
      );
    case 'animated_gif':
      return GifGridItem(
        tweet: tweet,
        tweetId: tweetId,
        username: username,
        thumbnailUrl: url,
        aspectRatio: ar,
        mediaIndex: mediaIndex,
        media: m,
      );
    case 'video':
      if (broadcast) {
        return BroadcastGridItem(
          tweet: tweet,
          tweetId: tweetId,
          username: username,
          thumbnailUrl: url,
          aspectRatio: ar,
          mediaIndex: mediaIndex,
          media: m,
          broadcastId: broadcastId,
        );
      }
      return VideoGridItem(
        tweet: tweet,
        tweetId: tweetId,
        username: username,
        thumbnailUrl: url,
        aspectRatio: ar,
        mediaIndex: mediaIndex,
        media: m,
      );
    default:
      return null;
  }
}

/// Which media a grid shows.
///
/// Animated GIFs are served as video by X and play like one, so they belong
/// with the videos rather than in a category of their own. Broadcasts /
/// Spaces recordings are their own bucket — they look like videos but the
/// tweet is marked by an `x.com/i/broadcasts/` link.
enum MediaFilter {
  all,
  photos,
  videos,
  broadcasts;

  bool accepts(MediaGridItem item) => switch (this) {
    MediaFilter.all => true,
    MediaFilter.photos => item is PhotoGridItem,
    MediaFilter.videos => item is VideoGridItem || item is GifGridItem,
    MediaFilter.broadcasts => item is BroadcastGridItem,
  };
}

CursorPage<String, MediaGridItem> mediaPageFromStatus(
  TweetStatus status,
  String? cursor,
) {
  final next = status.cursorBottom;
  // X repeats the bottom cursor once a timeline has no more pages. That does
  // mark the end, but the page it arrived with still holds real media —
  // discarding it lost the last screenful of a profile's media.
  final atEnd = next == null || next == cursor;
  return (
    items: mediaItemsFromChains(status.chains),
    nextCursor: atEnd ? null : next,
  );
}

/// A page of tweets and where the next one starts.
typedef ChainPage = ({List<TweetChain> chains, String? nextCursor});

/// Loads media pages until one carries something.
///
/// Media posts are sparse: a page of twenty text posts maps to no media at all,
/// and an empty page is how the paging controller is told a feed has ended. So
/// a few more pages are pulled before giving that answer.
Future<CursorPage<String, T>> mediaPageWithLookahead<T>(
  String? cursor,
  Future<ChainPage> Function(String? cursor) fetch,
  List<T> Function(List<TweetChain> chains) itemsOf, {
  int maxLookahead = 4,
}) async {
  var result = await fetch(cursor);
  var items = itemsOf(result.chains);

  var lookahead = 0;
  // Notably NOT gated on the page carrying chains: UserMedia's first page for
  // some profiles is a cursor and nothing else — every leading entry tombstoned
  // away — and refusing to follow it showed "no tweets" for an account with a
  // grid full of media one page on. The end of the feed is a null cursor
  // (mediaPageFromStatus already turns X's repeated cursor into one), not an
  // empty page.
  while (items.isEmpty &&
      result.nextCursor != null &&
      lookahead < maxLookahead) {
    result = await fetch(result.nextCursor);
    items = itemsOf(result.chains);
    lookahead++;
  }

  return (items: items, nextCursor: result.nextCursor);
}

List<MediaGridItem> mediaItemsFromChains(List<TweetChain> chains) {
  final out = <MediaGridItem>[];
  for (final chain in chains) {
    for (final tweet in chain.tweets) {
      final medias = tweet.extendedEntities?.media;
      final tweetId = tweet.idStr;
      final username = tweet.user?.screenName;
      if (tweetId == null || username == null) continue;
      if (medias == null || medias.isEmpty) {
        final cardOnly = _broadcastItemFromCard(tweet, tweetId, username);
        if (cardOnly != null) out.add(cardOnly);
        continue;
      }
      for (var i = 0; i < medias.length; i++) {
        final item = _itemFor(medias[i], tweetId, username, i, tweet);
        if (item != null) out.add(item);
      }
    }
  }
  return out;
}

/// Live / Spaces posts that carry the broadcast card but no native video.
MediaGridItem? _broadcastItemFromCard(
  TweetWithCard tweet,
  String tweetId,
  String username,
) {
  if (!tweetHasBroadcast(tweet)) {
    return null;
  }
  final thumb = broadcastThumbnailFromCard(tweet.card);
  if (thumb == null || thumb.isEmpty) {
    return null;
  }
  final media = Media.fromJson({
    'id_str': tweetId,
    'type': 'video',
    'media_url_https': thumb,
    'sizes': {
      'large': {'w': 16, 'h': 9},
    },
    'video_info': {
      'aspect_ratio': [16, 9],
    },
  });
  return BroadcastGridItem(
    tweet: tweet,
    tweetId: tweetId,
    username: username,
    thumbnailUrl: thumb,
    aspectRatio: 16 / 9,
    mediaIndex: 0,
    media: media,
    broadcastId: broadcastIdOf(tweet),
  );
}
