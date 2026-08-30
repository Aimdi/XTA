import 'package:extended_image/extended_image.dart';
import 'package:ffcache/ffcache.dart';
import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/reddit/reddit_avatar.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_read_session.dart';
import 'package:xta/utils/cache.dart';

/// How long a subreddit's picture is kept before it is looked up again.
/// Community artwork changes about as often as a logo does.
const Duration kRedditIconExpiry = Duration(days: 7);

/// The subreddit pictures known this session.
///
/// Finding one costs a page fetch, so it happens once per subreddit and is
/// remembered — in memory for the session and on disk between them. A miss
/// is not written to disk: a blocked about.json is not "this community has
/// no artwork", and caching that for a week locked the generated tile in.
class RedditIcons {
  final RedditClient client;
  final FFCache _cache = FFCache(name: redditIconsCacheName);
  final Map<String, Future<String?>> _pending = {};

  RedditIcons(this.client);

  Future<String?> iconFor(
    String subreddit, {
    String clientId = '',
    String? userToken,
    bool preferPublic = false,
  }) {
    final key = subreddit.toLowerCase();

    return _pending.putIfAbsent(key, () async {
      try {
        if (await _cache.has(key)) {
          final cached = await _cache.getJSON(key);
          if (cached is String && cached.isNotEmpty) {
            return cached;
          }
        }
      } catch (_) {
        // An emptied cache directory is a miss, not a dead icon.
      }

      final fetched = await client.fetchSubredditIcon(
        subreddit,
        clientId: clientId,
        userToken: userToken,
        preferPublic: preferPublic,
      );
      if (fetched == null || fetched.isEmpty) {
        // Do not persist a miss: a blocked about.json is not "no artwork".
        return null;
      }
      try {
        await _cache.getOrCreateAsJSON(key, kRedditIconExpiry, () async => fetched);
      } catch (_) {}
      return fetched;
    });
  }
}

/// A subreddit's picture, the way Reddit's own apps show it.
///
/// Falls back to the generated tile when the subreddit has no artwork, so the
/// row always has something in it and two subreddits never look alike.
/// Deliberately keyed on the subreddit and not the author: a timeline of
/// communities should be scannable by community.
class RedditSubredditAvatar extends StatefulWidget {
  final String subreddit;
  final double size;

  /// Already-known artwork, so a row that fetched `about` does not look it up
  /// again. Null means ask [RedditIcons].
  final String? url;

  const RedditSubredditAvatar({
    super.key,
    required this.subreddit,
    this.size = 40,
    this.url,
  });

  @override
  State<RedditSubredditAvatar> createState() => _RedditSubredditAvatarState();
}

class _RedditSubredditAvatarState extends State<RedditSubredditAvatar> {
  String? _icon;

  @override
  void initState() {
    super.initState();
    _icon = widget.url;
    WidgetsBinding.instance.addPostFrameCallback((_) => _resolve());
  }

  @override
  void didUpdateWidget(RedditSubredditAvatar old) {
    super.didUpdateWidget(old);
    if (old.subreddit != widget.subreddit || old.url != widget.url) {
      _icon = widget.url;
      _resolve();
    }
  }

  Future<void> _resolve() async {
    if (!mounted || widget.subreddit.isEmpty || widget.url != null) {
      return;
    }

    String clientId = '';
    String? userToken;
    var preferPublic = false;
    try {
      final prefs = PrefService.of(context, listen: false);
      final session = await RedditReadSession.resolve(prefs: prefs);
      clientId = session.clientId;
      userToken = session.userToken;
      preferPublic = session.preferPublic;
    } catch (_) {
      // Tests and a missing prefs ancestor still look the icon up.
    }
    if (!mounted) {
      return;
    }
    final icon = await context.read<RedditIcons>().iconFor(
      widget.subreddit,
      clientId: clientId,
      userToken: userToken,
      preferPublic: preferPublic,
    );
    if (mounted && icon != null) {
      setState(() => _icon = icon);
    }
  }

  @override
  Widget build(BuildContext context) {
    final icon = widget.url ?? _icon;
    if (icon == null || icon.isEmpty) {
      return RedditAvatar(name: 'r/${widget.subreddit}', size: widget.size);
    }

    final decode = (widget.size * MediaQuery.devicePixelRatioOf(context)).ceil();
    // Contained, not cropped, on a filled square. A community logo is usually
    // drawn with margins of its own and is rarely square — cover cut the edges
    // off exactly the part that identifies it.
    return Container(
      width: widget.size,
      height: widget.size,
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surfaceContainerHighest,
        borderRadius: redditAvatarBorder(widget.size),
      ),
      clipBehavior: Clip.antiAlias,
      child: ExtendedImage.network(
        icon,
        fit: BoxFit.contain,
        filterQuality: FilterQuality.high,
        // Decode at least 512px so a 44dp tile stays sharp on dense screens.
        cacheWidth: decode < 512 ? 512 : decode,
        headers: const {'User-Agent': RedditClient.publicUserAgent},
        loadStateChanged: (state) => state.extendedImageLoadState == LoadState.completed
            ? null
            : RedditAvatar(name: 'r/${widget.subreddit}', size: widget.size),
      ),
    );
  }
}
