import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/reddit/reddit_media_frame.dart';
import 'package:xta/ui/capped_network_image.dart';

/// The pictures of a gallery post, one frame at a time.
///
/// They used to be stacked, each up to 420 tall, so a twenty-picture album was
/// a card several screens long that nothing after it could be scrolled past.
/// One bounded frame you page through is the same album in the height of a
/// single picture.
class RedditGallery extends StatefulWidget {
  final List<String> images;

  /// Opens the tapped page fullscreen. Null leaves paging as the only gesture.
  final ValueChanged<int>? onOpen;

  /// Long-press save of the visible page.
  final ValueChanged<int>? onSave;

  const RedditGallery({
    super.key,
    required this.images,
    this.onOpen,
    this.onSave,
  });

  @override
  State<RedditGallery> createState() => _RedditGalleryState();
}

class _RedditGalleryState extends State<RedditGallery> {
  final PageController _controller = PageController();
  int _page = 0;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  void didUpdateWidget(RedditGallery old) {
    super.didUpdateWidget(old);
    // Recycled onto a different post: page one of the new album, not page four
    // of the old one.
    if (!identical(old.images, widget.images) &&
        _page >= widget.images.length) {
      _page = 0;
      if (_controller.hasClients) {
        _controller.jumpToPage(0);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);

    return RedditMediaFrame(
      aspectRatio: kRedditGalleryAspectRatio,
      child: Stack(
        fit: StackFit.expand,
        children: [
          PageView.builder(
            controller: _controller,
            itemCount: widget.images.length,
            onPageChanged: (page) => setState(() => _page = page),
            itemBuilder: (context, index) => GestureDetector(
              behavior: HitTestBehavior.opaque,
              onTap: widget.onOpen == null ? null : () => widget.onOpen!(index),
              onLongPress: widget.onSave == null
                  ? null
                  : () => widget.onSave!(index),
              child: Semantics(
                image: true,
                button: widget.onOpen != null,
                label: l10n.photos,
                // Contained, not cropped: an album mixes portrait and landscape,
                // and the frame's tint is a better background for the odd one out
                // than a crop through the middle of it.
                child: CappedNetworkImage(
                  url: widget.images[index],
                  fit: BoxFit.contain,
                ),
              ),
            ),
          ),
          Positioned(
            top: 8,
            right: 8,
            child: _RedditGalleryCounter(
              page: _page,
              total: widget.images.length,
            ),
          ),
        ],
      ),
    );
  }
}

class _RedditGalleryCounter extends StatelessWidget {
  final int page;
  final int total;

  const _RedditGalleryCounter({required this.page, required this.total});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: Colors.black54,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Text(
        '${page + 1}/$total',
        // Taken from the theme rather than fixed at 12px, so it grows with the
        // reader's text size like everything else on the card.
        style: Theme.of(context).textTheme.labelMedium!.copyWith(
          color: Colors.white,
          fontWeight: FontWeight.w600,
        ),
      ),
    );
  }
}
