/// The pictures of a gallery post the listing did not carry.
///
/// A reader with no Reddit client id reads listings off old.reddit's HTML, and
/// that page holds a gallery as a link and a 70px thumbnail — the pictures are
/// in `media_metadata`, which the HTML does not contain. So a gallery arrived
/// as a link card back to Reddit, which is not what anybody opened the app for.
///
/// The post's own public JSON does carry them, and needs no account. One
/// request per gallery post, remembered for the session so scrolling back past
/// a card does not ask again, and so two cards for the same post share one.
library;

import 'package:flutter/material.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';

/// How many posts' answers are kept before the oldest is let go. A session's
/// worth of scrolling, not the app's lifetime: this map used to grow without
/// bound.
const int kRedditGalleryCacheCap = 200;

/// How many times an empty answer is re-asked before it is taken as final.
///
/// An empty answer is also what every refusal looks like — fetchGalleryImages
/// never throws — so taking the first one as final meant one rate-limited
/// moment hid that gallery for the rest of the app's life. A couple of retries
/// lets it recover; a bound keeps a genuine non-gallery from being asked again
/// on every scroll-past.
const int kRedditGalleryEmptyRetries = 3;

class RedditGalleryLoader {
  final RedditClient client;

  RedditGalleryLoader(this.client);

  /// Answers by permalink, insertion-ordered so eviction drops the oldest.
  final Map<String, List<String>> _known = {};

  /// In-flight fetches, so two cards for the same post share one request.
  final Map<String, Future<List<String>>> _inFlight = {};

  /// How often a permalink has answered empty, for the retry bound.
  final Map<String, int> _emptyAnswers = {};

  int get size => _known.length;

  /// The gallery's pictures — from memory, from a fetch already in flight, or
  /// freshly asked for.
  Future<List<String>> images(String permalink) {
    final known = _known[permalink];
    if (known != null && (known.isNotEmpty || (_emptyAnswers[permalink] ?? 0) >= kRedditGalleryEmptyRetries)) {
      return Future.value(known);
    }

    // Cleanup rides on `.then` with an onError twin: both arms drop the key,
    // so neither a settled nor an errored future stays pinned in the map.
    return _inFlight[permalink] ??= _fetch(permalink).then(
      (images) {
        _inFlight.remove(permalink);
        return images;
      },
      onError: (Object e, StackTrace st) {
        // fetchGalleryImages answers [] rather than throwing today, but an
        // errored future pinned in the map would refuse this permalink forever.
        _inFlight.remove(permalink);
        Error.throwWithStackTrace(e, st);
      },
    );
  }

  Future<List<String>> _fetch(String permalink) async {
    final images = await client.fetchGalleryImages(permalink);
    if (images.isEmpty) {
      final asked = (_emptyAnswers.remove(permalink) ?? 0) + 1;
      _emptyAnswers[permalink] = asked;
      while (_emptyAnswers.length > kRedditGalleryCacheCap) {
        _emptyAnswers.remove(_emptyAnswers.keys.first);
      }
    } else {
      _emptyAnswers.remove(permalink);
    }

    _known.remove(permalink);
    _known[permalink] = images;
    while (_known.length > kRedditGalleryCacheCap) {
      _known.remove(_known.keys.first);
    }

    return images;
  }

  /// What is already known, without asking for anything.
  ///
  /// Lets a rebuild draw the pictures it already has instead of flashing the
  /// link card again while the same future resolves a second time.
  List<String>? known(String permalink) => _known[permalink];
}

/// Draws [whenLoaded] once the gallery's pictures arrive, and [placeholder]
/// until then — or for good, when Reddit will not say.
class RedditGalleryImages extends StatefulWidget {
  final RedditGalleryLoader loader;
  final String permalink;
  final Widget Function(List<String> images) whenLoaded;
  final Widget placeholder;

  const RedditGalleryImages({
    super.key,
    required this.loader,
    required this.permalink,
    required this.whenLoaded,
    required this.placeholder,
  });

  @override
  State<RedditGalleryImages> createState() => _RedditGalleryImagesState();
}

class _RedditGalleryImagesState extends State<RedditGalleryImages> {
  List<String>? _images;

  @override
  void initState() {
    super.initState();
    _start();
  }

  /// A list without keys reuses this element for a different post: without
  /// this, the card kept drawing the previous post's album under the new one.
  @override
  void didUpdateWidget(RedditGalleryImages oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.permalink != widget.permalink) {
      _images = null;
      _start();
    }
  }

  void _start() {
    _images = widget.loader.known(widget.permalink);
    if (_images == null || _images!.isEmpty) {
      _load();
    }
  }

  Future<void> _load() async {
    final asked = widget.permalink;
    final images = await widget.loader.images(asked);
    // The answer may arrive after the element moved on to another post.
    if (mounted && widget.permalink == asked) {
      setState(() => _images = images);
    }
  }

  @override
  Widget build(BuildContext context) {
    final images = _images;
    // Fewer than two is not a gallery worth paging through, and is also what a
    // refusal looks like — either way the link back to Reddit is the honest
    // thing to show.
    return images == null || images.length < 2 ? widget.placeholder : widget.whenLoaded(images);
  }
}
