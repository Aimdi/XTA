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

class RedditGalleryLoader {
  final RedditClient client;

  RedditGalleryLoader(this.client);

  final Map<String, Future<List<String>>> _byPermalink = {};

  /// The gallery's pictures, fetched at most once per post.
  Future<List<String>> images(String permalink) => _byPermalink[permalink] ??= client.fetchGalleryImages(permalink);

  /// What is already known, without asking for anything.
  ///
  /// Lets a rebuild draw the pictures it already has instead of flashing the
  /// link card again while the same future resolves a second time.
  List<String>? known(String permalink) => _known[permalink];

  final Map<String, List<String>> _known = {};

  /// Remembers a resolved answer so [known] can serve it.
  void remember(String permalink, List<String> images) => _known[permalink] = images;
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
    _images = widget.loader.known(widget.permalink);
    if (_images == null) {
      _load();
    }
  }

  Future<void> _load() async {
    final images = await widget.loader.images(widget.permalink);
    widget.loader.remember(widget.permalink, images);
    if (mounted) {
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
