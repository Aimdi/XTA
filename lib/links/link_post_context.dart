import 'package:flutter/widgets.dart';

/// The post a link was opened from, carried into the in-app browser so the
/// reader can see — and get back to — where the article came from.
///
/// Counts are what the network reported; null means it reported nothing, and
/// the bar then shows the icon without inventing a zero. Nothing here acts on
/// the network: XTA reads, it does not post.
class LinkPostContext {
  /// Plugin id of the network the post lives on (`x`, `threads`, …).
  final String sourceId;
  final String author;
  final String? avatarUrl;
  final int? replies;
  final int? reposts;
  final int? likes;

  /// Where the post itself can be shared from.
  final String? postUrl;

  /// Opens the post. Null when the post is the screen under the browser, in
  /// which case going back is closing the browser.
  final VoidCallback? openPost;

  const LinkPostContext({
    required this.sourceId,
    required this.author,
    this.avatarUrl,
    this.replies,
    this.reposts,
    this.likes,
    this.postUrl,
    this.openPost,
  });
}
