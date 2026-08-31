import 'dart:io';
import 'dart:math' as math;

import 'package:async_button_builder/async_button_builder.dart';
import 'package:dart_twitter_api/twitter_api.dart';
import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:path_provider/path_provider.dart';
import 'package:quax/constants.dart';
import 'package:quax/generated/l10n.dart';
import 'package:quax/profile/profile.dart';
import 'package:quax/tweet/_photo.dart';
import 'package:quax/tweet/_video.dart';
import 'package:quax/tweet/tweet_chrome.dart';
import 'package:quax/utils/downloads.dart';
import 'package:path/path.dart' as path;
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:share_plus/share_plus.dart';
import 'package:uuid/uuid.dart';

class _TweetMediaItem extends StatefulWidget {
  final int mediaIndex;
  final Media media;
  final String username;
  final String? tweetId;

  const _TweetMediaItem({
    required this.mediaIndex,
    required this.media,
    required this.username,
    this.tweetId,
  });

  @override
  State<_TweetMediaItem> createState() => _TweetMediaItemState();
}

class _TweetMediaItemState extends State<_TweetMediaItem> {
  bool _showMedia = false;

  @override
  void initState() {
    super.initState();

    var disableAutoload =
        PrefService.of(
          context,
          listen: false,
        ).get<bool>(optionMediaDisableAutoload) ??
        false;
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
      );
    } else {
      media = GestureDetector(
        child: Container(
          color: Colors.black26,
          child: Center(
            child: Text(
              L10n.of(
                context,
              ).tap_to_show_getMediaType_item_type(getMediaType(item.type)),
            ),
          ),
        ),
        onTap: () => setState(() {
          _showMedia = true;
        }),
      );
    }

    return media;
  }
}

/// Downloads a media item's original file, with the same progress snackbars
/// as the fullscreen viewer's download button.
Future<void> downloadMediaItem(
  BuildContext context,
  Media media,
  String username,
) async {
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
      ScaffoldMessenger.of(
        context,
      ).hideCurrentSnackBar(reason: SnackBarClosedReason.hide);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(L10n.of(context).successfully_saved_the_media)),
      );
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

  const TweetMedia({
    super.key,
    required this.sensitive,
    required this.media,
    required this.username,
    this.initialMediaIndex = 0,
    this.tweetId,
  });

  @override
  State<TweetMedia> createState() => _TweetMediaState();
}

class _TweetMediaState extends State<TweetMedia> {
  double _aspectRatioOf(Media item) {
    final size = item.sizes?.large;
    final width = size?.w;
    final height = size?.h;
    if (width == null || height == null || width <= 0 || height <= 0) {
      return 16 / 9;
    }
    return (width / height).clamp(0.75, 1.9);
  }

  Widget _mediaItem(BuildContext context, int index) {
    final item = widget.media[index];
    final isVideo = item.type == 'video';
    return Semantics(
      button: !isVideo,
      image: !isVideo,
      child: GestureDetector(
        onTap: isVideo
            ? null
            : () => Navigator.push(
                context,
                MaterialPageRoute(
                  builder: (context) => TweetMediaView(
                    initialIndex: index,
                    media: widget.media,
                    username: widget.username,
                    tweetId: widget.tweetId,
                  ),
                ),
              ),
        onLongPress: item.type == 'photo'
            ? () => downloadMediaItem(context, item, widget.username)
            : null,
        child: _TweetMediaItem(
          media: item,
          mediaIndex: index,
          username: widget.username,
          tweetId: widget.tweetId,
        ),
      ),
    );
  }

  Widget _multiMediaGrid(BuildContext context) {
    final items = widget.media.take(4).toList(growable: false);
    Widget cell(int index) => Expanded(child: _mediaItem(context, index));

    if (items.length == 2) {
      return AspectRatio(
        aspectRatio: 2,
        child: Row(
          children: [
            cell(0),
            const SizedBox(width: kTweetMediaGap),
            cell(1),
          ],
        ),
      );
    }

    if (items.length == 3) {
      return AspectRatio(
        aspectRatio: 4 / 3,
        child: Row(
          children: [
            cell(0),
            const SizedBox(width: kTweetMediaGap),
            Expanded(
              child: Column(
                children: [
                  Expanded(child: _mediaItem(context, 1)),
                  const SizedBox(height: kTweetMediaGap),
                  Expanded(child: _mediaItem(context, 2)),
                ],
              ),
            ),
          ],
        ),
      );
    }

    return AspectRatio(
      aspectRatio: 1,
      child: Column(
        children: [
          Expanded(
            child: Row(
              children: [
                cell(0),
                const SizedBox(width: kTweetMediaGap),
                cell(1),
              ],
            ),
          ),
          const SizedBox(height: kTweetMediaGap),
          Expanded(
            child: Row(
              children: [
                cell(2),
                const SizedBox(width: kTweetMediaGap),
                cell(3),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _visibleMedia(BuildContext context) {
    if (widget.media.length > 1) {
      return _multiMediaGrid(context);
    }
    return AspectRatio(
      aspectRatio: _aspectRatioOf(widget.media.first),
      child: _mediaItem(context, 0),
    );
  }

  @override
  Widget build(BuildContext context) {
    if (widget.media.isEmpty) return const SizedBox.shrink();

    return Consumer<TweetContextState>(
      builder: (context, model, child) {
        if (model.hideSensitive && (widget.sensitive ?? false)) {
          return TweetMediaFrame(
            child: AspectRatio(
              aspectRatio: 16 / 9,
              child: Center(
                child: Padding(
                  padding: const EdgeInsets.all(kTweetSpace4),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        Icons.visibility_off_outlined,
                        size: 32,
                        color: tweetSecondaryColor(context),
                      ),
                      const SizedBox(height: kTweetSpace2),
                      Text(
                        L10n.current.possibly_sensitive,
                        style: tweetLabelStyle(context),
                      ),
                      const SizedBox(height: kTweetSpace1),
                      Text(
                        L10n.current.possibly_sensitive_tweet,
                        textAlign: TextAlign.center,
                        style: tweetMetadataStyle(context),
                      ),
                      const SizedBox(height: kTweetSpace2),
                      TextButton(
                        onPressed: () => model.setHideSensitive(false),
                        child: Text(L10n.current.yes_please),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          );
        }

        return TweetMediaFrame(child: _visibleMedia(context));
      },
    );
  }
}

/// One page of the fullscreen viewer, carrying the tweet context its media
/// belongs to — pages can span different tweets in the grid lightbox.
typedef MediaViewEntry = ({
  Media media,
  String username,
  String? tweetId,
  int mediaIndex,
});

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
  TweetMediaView({
    super.key,
    required this.initialIndex,
    required List<Media> media,
    required String username,
    this.tweetMedia = true,
    String? tweetId,
  }) : entries = [
         for (var i = 0; i < media.length; i++)
           (
             media: media[i],
             username: username,
             tweetId: tweetId,
             mediaIndex: i,
           ),
       ],
       onOpenPost = null,
       onNearEnd = null;

  const TweetMediaView.entries({
    super.key,
    required this.initialIndex,
    required this.entries,
    this.tweetMedia = true,
    this.onOpenPost,
    this.onNearEnd,
  });

  @override
  State<TweetMediaView> createState() => _TweetMediaViewState();
}

Media createMediaFromUrl(String? url, double? height) {
  Media media = Media();
  if (url != null) {
    ExtendedImage.network(url, fit: BoxFit.fitWidth, height: height);
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
  late final ExtendedPageController _pageController = ExtendedPageController(
    initialPage: widget.initialIndex,
  );

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
    return (widget.tweetMedia
            ? '${media.mediaUrlHttps}:orig'
            : media.mediaUrlHttps) ??
        "";
  }

  @override
  Widget build(BuildContext context) {
    String? size;
    var prefs = PrefService.of(context, listen: false);
    if (widget.tweetMedia) {
      var size = prefs.get(optionImageQuality);
      if (size == 'disabled') {
        size = 'medium';
      }
    }

    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        backgroundColor: Colors.black,
        foregroundColor: Colors.white,
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
              return IconButton(
                onPressed: callback,
                icon: child,
                tooltip: L10n.of(context).save,
              );
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
                  showWorkingSnackBar(
                    context,
                    L10n.of(context).downloading_media,
                  );
                },
                onSuccess: () {
                  ScaffoldMessenger.of(
                    context,
                  ).hideCurrentSnackBar(reason: SnackBarClosedReason.hide);
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(
                      content: Text(
                        L10n.of(context).successfully_saved_the_media,
                      ),
                    ),
                  );
                },
              );
            },
          ),
          AsyncButtonBuilder(
            showSuccess: false,
            builder: (context, child, callback, buttonState) {
              return IconButton(
                onPressed: callback,
                icon: child,
                tooltip: L10n.of(context).share_link,
              );
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
            mediaIndex: entry.mediaIndex,
          );
        },
        controller: _pageController,
        onPageChanged: (index) {
          setState(() => _currentIndex = index);
          if (widget.onNearEnd != null &&
              index >= widget.entries.length - _fetchAhead) {
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

  const _TweetMediaThing({
    required this.item,
    required this.username,
    required this.size,
    required this.pullToClose,
    required this.inPageView,
    this.tweetId,
    this.mediaIndex = 0,
  });

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
        mediaIndex: mediaIndex,
      );
    } else if (item.type == 'video') {
      media = TweetVideo(
        metadata: TweetVideoMetadata.fromMedia(item),
        loop: false,
        username: username,
        tweetId: tweetId,
        mediaIndex: mediaIndex,
      );
    } else if (item.type == 'photo') {
      media = TweetPhoto(
        size: size,
        uri: item.mediaUrlHttps!,
        fit: inPageView ? BoxFit.contain : BoxFit.cover,
        pullToClose: pullToClose,
        inPageView: inPageView,
      );
    } else {
      media = Text(L10n.of(context).unknown);
    }

    return RepaintBoundary(child: media);
  }
}
