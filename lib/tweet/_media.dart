import 'dart:io';
import 'dart:math' as math;

import 'package:async_button_builder/async_button_builder.dart';
import 'package:dart_twitter_api/twitter_api.dart';
import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:path_provider/path_provider.dart';
import 'package:xta/client/client.dart';
import 'package:xta/constants.dart';
import 'package:xta/home/edge_swipe.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/tweet/_photo.dart';
import 'package:xta/tweet/media_strip.dart';
import 'package:xta/tweet/_video.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/tweet/sensitive_media_gate.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/ui/x_look_theme.dart';
import 'package:xta/utils/downloads.dart';
import 'package:xta/utils/media_quality.dart';
import 'package:path/path.dart' as path;
import 'package:pref/pref.dart';
import 'package:share_plus/share_plus.dart';
import 'package:uuid/uuid.dart';

class _TweetMediaItem extends StatefulWidget {
  final int index;
  final int mediaIndex;
  final int total;
  final Media media;
  final String username;
  final String? tweetId;
  final TweetWithCard? tweet;

  /// How a photo fills its box. Cards in the strip are sized for it; the older
  /// full-width pager shows the whole picture instead.
  final BoxFit fit;

  /// Whether to write "2 / 4" in the corner. Pointless in a strip, where the
  /// rest of them are right there.
  final bool showCounter;

  const _TweetMediaItem(
      {required this.index,
      required this.mediaIndex,
      required this.total,
      required this.media,
      required this.username,
      this.tweetId,
      this.tweet,
      this.fit = BoxFit.contain,
      this.showCounter = true});

  @override
  State<_TweetMediaItem> createState() => _TweetMediaItemState();
}

class _TweetMediaItemState extends State<_TweetMediaItem> {
  bool _showMedia = false;

  @override
  void initState() {
    super.initState();

    var disableAutoload = PrefService.of(context, listen: false).get<bool>(optionMediaDisableAutoload) ?? false;
    if (disableAutoload) {
      // If the image is cached already, show the media
      cachedImageExists(widget.media.mediaUrlHttps!).then((value) {
        if (mounted) {
          setState(() {
            _showMedia = value;
          });
        }
      });
    } else {
      setState(() {
        _showMedia = true;
      });
    }
  }

  String getMediaType(String? type) {
    switch (type) {
      case 'animated_gif':
        return 'GIF';
      case 'photo':
        return 'photo';
      case 'video':
        return 'video';
      default:
        return 'media';
    }
  }

  void _showAltTextDialog(BuildContext context, String description) {
    showDialog<void>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(L10n.of(dialogContext).alt_text_title),
        content: Text(description),
        actions: [
          TextButton(onPressed: () => Navigator.pop(dialogContext), child: Text(L10n.of(dialogContext).ok)),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    var prefs = PrefService.of(context, listen: false);
    var size = prefs.get(optionImageQuality);

    Widget media;

    var item = widget.media;

    if (_showMedia) {
      media = _TweetMediaThing(
          item: item,
          username: widget.username,
          size: size,
          pullToClose: false,
          inPageView: false,
          tweetId: widget.tweetId,
          mediaIndex: widget.mediaIndex,
          fit: widget.fit);
    } else {
      media = GestureDetector(
        child: Container(
          color: Colors.black26,
          child: Center(
            child: Text(
              L10n.of(context).tap_to_show_getMediaType_item_type(getMediaType(item.type)),
            ),
          ),
        ),
        onTap: () => setState(() {
          _showMedia = true;
        }),
      );
    }

    final altText = widget.tweet?.altTextForMedia(widget.media);

    Widget content;
    // If there's only one item in this media collection, don't show the page counter
    if (widget.total == 1 || !widget.showCounter) {
      content = media;
    } else {
      content = Stack(
        children: [
          Center(child: media),
          Positioned(
            right: 0,
            child: Container(
              alignment: Alignment.topRight,
              color: Colors.black38,
              margin: const EdgeInsets.all(8),
              padding: const EdgeInsets.all(8),
              child: Text('${widget.index} / ${widget.total}'),
            ),
          )
        ],
      );
    }

    if (altText == null) {
      return content;
    }

    return Stack(
      children: [
        content,
        Positioned(
          left: 8,
          bottom: 8,
          child: GestureDetector(
            onLongPress: () => _showAltTextDialog(context, altText),
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
              decoration: BoxDecoration(
                color: Colors.black54,
                borderRadius: BorderRadius.circular(4),
              ),
              child: Text(
                L10n.of(context).alt_text_badge,
                style: const TextStyle(color: Colors.white, fontSize: 11, fontWeight: FontWeight.w600),
              ),
            ),
          ),
        ),
      ],
    );
  }
}

/// Downloads a media item's original file, with the same progress snackbars
/// as the fullscreen viewer's download button.
Future<void> downloadMediaItem(BuildContext context, Media media, String username) async {
  final mediaUrl = media.mediaUrlHttps;
  if (mediaUrl == null) {
    return;
  }

  final fileName = '$username-${path.basename(mediaUrl)}';

  await downloadUriToPickedFile(
    context,
    Uri.parse('$mediaUrl:orig'),
    fileName,
    prefs: PrefService.of(context, listen: false),
    onStart: () {
      showWorkingSnackBar(context, L10n.of(context).downloading_media);
    },
    onSuccess: () {
      ScaffoldMessenger.of(context).hideCurrentSnackBar(reason: SnackBarClosedReason.hide);
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text(L10n.of(context).successfully_saved_the_media),
      ));
    },
  );
}

class TweetMedia extends StatefulWidget {
  final bool? sensitive;
  final List<Media> media;
  final String username;
  final int initialMediaIndex;
  // Used (with the media index) to cache/reuse video controllers across screens.
  final String? tweetId;
  final TweetWithCard? tweet;

  const TweetMedia(
      {super.key,
      required this.sensitive,
      required this.media,
      required this.username,
      this.initialMediaIndex = 0,
      this.tweetId,
      this.tweet});

  @override
  State<TweetMedia> createState() => _TweetMediaState();
}

class _TweetMediaState extends State<TweetMedia> {
  final ScrollController _controller = ScrollController();

  /// The row is only moved to [TweetMedia.initialMediaIndex] once. Doing it on
  /// every layout would drag the row back under the reader's finger.
  bool _placed = false;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  /// Puts the picture the post was opened at into view.
  void _placeAt(MediaStripLayout layout) {
    if (_placed || widget.initialMediaIndex <= 0) {
      return;
    }
    _placed = true;

    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || !_controller.hasClients) {
        return;
      }
      final offset = mediaStripOffsetOf(widget.initialMediaIndex, layout.widths);
      _controller.jumpTo(math.min(offset, _controller.position.maxScrollExtent));
    });
  }

  @override
  Widget build(BuildContext context) {
    return SensitiveMediaGate(
      sensitive: widget.sensitive ?? false,
      errorMessage: L10n.current.possibly_sensitive_tweet,
      child: _buildMedia(context),
    );
  }

  Widget _buildMedia(BuildContext context) {
    final tokens = XLookTokens.maybeOf(context);
    final radius = tokens?.mediaRadius ?? kTweetMediaRadius;

    if (widget.media.length == 1) {
      return RepaintBoundary(
        child: Container(
          margin: const EdgeInsets.only(top: 8, left: 16, right: 16),
          clipBehavior: Clip.antiAlias,
          decoration: BoxDecoration(borderRadius: BorderRadius.circular(radius)),
          child: AspectRatio(
            aspectRatio: singleMediaAspect(_aspects().single),
            child: ColoredBox(
              color: Colors.black,
              child: _card(context, 0, fit: BoxFit.contain, showCounter: false),
            ),
          ),
        ),
      );
    }

    return RepaintBoundary(
      // No right margin: the row runs off the edge of the screen, which is
      // what says there is more of it than fits.
      child: Container(
        margin: const EdgeInsets.only(top: 8, left: 16),
        child: LayoutBuilder(
          builder: (context, constraints) {
            final layout = mediaStripLayout(width: constraints.maxWidth, aspects: _aspects());
            _placeAt(layout);

            return SizedBox(
              height: layout.height,
              // The row owns horizontal drags that start on it, so without
              // this a swipe over a post's media could not reach the home
              // page view.
              child: edgeSwipeToChangeHomePage(
                context,
                ListView.separated(
                  controller: _controller,
                  scrollDirection: Axis.horizontal,
                  padding: const EdgeInsets.only(right: 16),
                  itemCount: widget.media.length,
                  separatorBuilder: (_, _) => const SizedBox(width: kMediaCardGap),
                  itemBuilder: (context, index) => SizedBox(
                    width: layout.widths[index],
                    child: ClipRRect(
                      borderRadius: BorderRadius.circular(radius),
                      child: _card(context, index, fit: BoxFit.cover, showCounter: false),
                    ),
                  ),
                ),
              ),
            );
          },
        ),
      ),
    );
  }

  /// The aspect ratio of every item, for the row to size itself from.
  List<double> _aspects() => widget.media
      .map((e) => mediaItemAspect(
            type: e.type,
            videoAspect: e.videoInfo?.aspectRatio,
            thumbW: e.sizes?.large?.w,
            thumbH: e.sizes?.large?.h,
          ))
      .toList(growable: false);

  /// One piece of media, with the taps that belong to it.
  Widget _card(BuildContext context, int index, {required BoxFit fit, required bool showCounter}) {
    final item = widget.media[index];

    // A video has its own tap controls and must never open the fullscreen
    // media viewer. Photos and GIFs still open it.
    final isVideo = item.type == 'video';

    return GestureDetector(
      onTap: isVideo
          ? null
          : () => Navigator.push(
              context,
              MaterialPageRoute(
                  builder: (context) => TweetMediaView(
                      initialIndex: index,
                      media: widget.media,
                      username: widget.username,
                      tweetId: widget.tweetId))),
      onLongPress: item.type == 'photo'
          ? () {
              final alt = widget.tweet?.altTextForMedia(item);
              if (alt != null) {
                showDialog<void>(
                  context: context,
                  builder: (dialogContext) => AlertDialog(
                    title: Text(L10n.of(dialogContext).alt_text_title),
                    content: Text(alt),
                    actions: [
                      TextButton(
                          onPressed: () => Navigator.pop(dialogContext), child: Text(L10n.of(dialogContext).ok)),
                    ],
                  ),
                );
              } else {
                downloadMediaItem(context, item, widget.username);
              }
            }
          : null,
      child: _TweetMediaItem(
        media: item,
        index: index + 1,
        mediaIndex: index,
        total: widget.media.length,
        username: widget.username,
        tweetId: widget.tweetId,
        tweet: widget.tweet,
        fit: fit,
        showCounter: showCounter,
      ),
    );
  }
}

/// One page of the fullscreen viewer, carrying the tweet context its media
/// belongs to — pages can span different tweets in the grid lightbox.
typedef MediaViewEntry = ({Media media, String username, String? tweetId, int mediaIndex});

class TweetMediaView extends StatefulWidget {
  final int initialIndex;
  final List<MediaViewEntry> entries;
  final bool tweetMedia; // True if the media comes from a tweet
  // Shown as an app-bar action when set; used by the grid lightbox to jump
  // to the post the current page belongs to.
  final void Function(MediaViewEntry entry)? onOpenPost;
  // Called when swiping close to the end of [entries], so a paginated caller
  // can fetch more items.
  final VoidCallback? onNearEnd;

  /// Single-tweet viewer: all pages share one username/tweetId.
  TweetMediaView(
      {super.key,
      required this.initialIndex,
      required List<Media> media,
      required String username,
      this.tweetMedia = true,
      String? tweetId})
      : entries = [
          for (var i = 0; i < media.length; i++) (media: media[i], username: username, tweetId: tweetId, mediaIndex: i)
        ],
        onOpenPost = null,
        onNearEnd = null;

  const TweetMediaView.entries(
      {super.key,
      required this.initialIndex,
      required this.entries,
      this.tweetMedia = true,
      this.onOpenPost,
      this.onNearEnd});

  @override
  State<TweetMediaView> createState() => _TweetMediaViewState();
}

/// Wraps a bare image URL in the [Media] shape the media viewer expects.
///
/// [height] is unused: it only ever fed an [ExtendedImage] that was built and
/// then dropped. The parameter stays for the callers that still pass one.
Media createMediaFromUrl(String? url, double? height) {
  Media media = Media();
  if (url != null) {
    media.url = url;
    media.mediaUrlHttps = url;
    media.displayUrl = url;
    media.expandedUrl = url;
    media.type = 'photo';
  }
  return media;
}

class _TweetMediaViewState extends State<TweetMediaView> {
  // How many pages from the end of the loaded entries [onNearEnd] fires.
  static const _fetchAhead = 3;

  late int _currentIndex;
  late final ExtendedPageController _pageController = ExtendedPageController(initialPage: widget.initialIndex);

  MediaViewEntry get _current => widget.entries[_currentIndex];

  @override
  void initState() {
    super.initState();

    _currentIndex = widget.initialIndex;
  }

  @override
  void didUpdateWidget(TweetMediaView oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.entries.isNotEmpty) {
      _currentIndex = math.min(_currentIndex, widget.entries.length - 1);
    }
  }

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }

  String originalMediaUrl() {
    final media = _current.media;
    return (widget.tweetMedia ? '${media.mediaUrlHttps}:orig' : media.mediaUrlHttps) ?? "";
  }

  @override
  Widget build(BuildContext context) {
    String? size;
    var prefs = PrefService.of(context, listen: false);
    if (widget.tweetMedia) {
      // `var size` here used to declare a shadow, so the outer one stayed null
      // and the image-quality setting never reached the photo URL.
      size = MediaQuality.fromStored(prefs.get<String>(optionImageQuality), fallback: MediaQuality.medium).stored;
    }

    return Scaffold(
      appBar: AppBar(
        actions: [
          if (widget.onOpenPost != null)
            IconButton(
              icon: const Icon(Icons.open_in_new),
              tooltip: L10n.of(context).open_post,
              onPressed: () => widget.onOpenPost!(_current),
            ),
          AsyncButtonBuilder(
            child: const Icon(Icons.download),
            builder: (context, child, callback, buttonState) {
              return IconButton(onPressed: callback, icon: child);
            },
            onPressed: () async {
              var url = path.basename(_current.media.mediaUrlHttps!);
              var fileName = '${_current.username}-$url';
              var uri = Uri.parse(originalMediaUrl());

              await downloadUriToPickedFile(
                context,
                uri,
                fileName,
                prefs: prefs,
                onStart: () {
                  showWorkingSnackBar(context, L10n.of(context).downloading_media);
                },
                onSuccess: () {
                  ScaffoldMessenger.of(context).hideCurrentSnackBar(reason: SnackBarClosedReason.hide);
                  ScaffoldMessenger.of(context).showSnackBar(SnackBar(
                    content: Text(L10n.of(context).successfully_saved_the_media),
                  ));
                },
              );
            },
          ),
          AsyncButtonBuilder(
            showSuccess: false,
            builder: (context, child, callback, buttonState) {
              return IconButton(onPressed: callback, icon: child);
            },
            onPressed: () async {
              var uri = Uri.parse(originalMediaUrl());

              var fileBytes = await downloadFile(context, uri);

              // The following is a workaround because of an issue with the share_plus package which uses the faulty mime_type library.
              // When the issue is resolved (the PR https://github.com/dart-lang/mime/pull/81 is merged),
              // then it should be replaced by the original code:
              // Share.shareXFiles([XFile.fromData(fileBytes, mimeType: 'image/jpeg')]);
              const uuid = Uuid();

              final String tempPath = (await getTemporaryDirectory()).path;
              final name = uuid.v4();
              final path = '$tempPath/$name.jpg';

              final file = File(path);
              await file.writeAsBytes(fileBytes);

              final xfile = XFile(path, mimeType: 'image/jpeg');

              Share.shareXFiles([xfile]).then((value) => file.delete());
            },
            child: const Icon(Icons.share),
          ),
        ],
      ),
      body: ExtendedImageGesturePageView.builder(
        scrollDirection: Axis.horizontal,
        itemCount: widget.entries.length,
        itemBuilder: (BuildContext context, int index) {
          var entry = widget.entries[index];

          // mediaIndex is the index *within the entry's tweet* (it keys the
          // pooled video controllers), not the page index.
          return _TweetMediaThing(
              item: entry.media,
              username: entry.username,
              size: size,
              pullToClose: true,
              inPageView: true,
              tweetId: entry.tweetId,
              mediaIndex: entry.mediaIndex);
        },
        controller: _pageController,
        onPageChanged: (index) {
          setState(() => _currentIndex = index);
          if (widget.onNearEnd != null && index >= widget.entries.length - _fetchAhead) {
            widget.onNearEnd!();
          }
        },
      ),
    );
  }
}

class _TweetMediaThing extends StatelessWidget {
  final Media item;
  final String username;
  final String? size;
  final bool pullToClose;
  final bool inPageView;
  final String? tweetId;
  final int mediaIndex;

  /// How a photo sits in the space it is given. A card in the strip has a size
  /// of its own to fill, so it covers; anywhere else the whole picture matters
  /// more than the shape of its box.
  final BoxFit fit;

  const _TweetMediaThing(
      {required this.item,
      required this.username,
      required this.size,
      required this.pullToClose,
      required this.inPageView,
      this.tweetId,
      this.mediaIndex = 0,
      this.fit = BoxFit.contain});

  @override
  Widget build(BuildContext context) {
    Widget media;
    if (item.type == 'animated_gif') {
      media = TweetVideo(
          metadata: TweetVideoMetadata.fromMedia(item),
          loop: true,
          username: username,
          alwaysPlay: true,
          disableControls: true,
          tweetId: tweetId,
          mediaIndex: mediaIndex);
    } else if (item.type == 'video') {
      media = TweetVideo(
          metadata: TweetVideoMetadata.fromMedia(item),
          loop: false,
          username: username,
          tweetId: tweetId,
          mediaIndex: mediaIndex);
    } else if (item.type == 'photo') {
      media = TweetPhoto(size: size, uri: item.mediaUrlHttps!, fit: fit, pullToClose: pullToClose, inPageView: inPageView);
    } else {
      media = Text(L10n.of(context).unknown);
    }

    return RepaintBoundary(child: media);
  }
}
