import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:share_plus/share_plus.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_counts.dart';
import 'package:xta/plugins/reddit/reddit_archive.dart';
import 'package:xta/plugins/reddit/reddit_avatar.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_media_frame.dart';
import 'package:xta/plugins/reddit/reddit_post_media.dart';
import 'package:xta/plugins/reddit/reddit_post_sheet.dart' show redditPostUrl;
import 'package:xta/plugins/reddit/reddit_store.dart';
import 'package:xta/plugins/reddit/reddit_text.dart';
import 'package:xta/plugins/reddit/reddit_thread_chrome.dart';
import 'package:xta/saved/saved_tweet_model.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/capped_network_image.dart';
import 'package:xta/ui/dates.dart';
import 'package:xta/ui/x_look_theme.dart';
import 'package:xta/utils/urls.dart';

/// A thread's body text: 15 on 22.
TextStyle redditThreadBodyStyle(BuildContext context) =>
    Theme.of(context).textTheme.bodyMedium!.copyWith(fontSize: 15, height: 22 / 15, color: tweetPrimaryColor(context));

/// The post at the head of its thread: who wrote it and when, its title, its
/// media and its words.
class RedditThreadPost extends StatelessWidget {
  final RedditPost post;

  /// The post's words, already stripped of what its media shows.
  final String? selfText;

  const RedditThreadPost({super.key, required this.post, this.selfText});

  @override
  Widget build(BuildContext context) {
    final selfText = this.selfText;
    return Padding(
      padding: const EdgeInsets.fromLTRB(kTweetHorizontalPadding, 14, kTweetHorizontalPadding, 0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _AuthorRow(post: post),
          if (post.showsTitle)
            Padding(
              padding: const EdgeInsets.only(top: 10),
              child: Text(
                post.displayTitle,
                style: Theme.of(context).textTheme.titleLarge!.copyWith(
                  fontSize: 21,
                  height: 27 / 21,
                  fontWeight: FontWeight.w600,
                  letterSpacing: -0.2,
                  color: tweetPrimaryColor(context),
                ),
              ),
            ),
          RedditPostMedia(
            post: post,
            padding: const EdgeInsets.only(top: 14),
            linkCard: (post, url) => RedditThreadLinkCard(post: post, url: url),
          ),
          if (selfText != null)
            Padding(
              padding: const EdgeInsets.only(top: 12),
              child: RedditRichText(text: selfText, style: redditThreadBodyStyle(context)),
            ),
        ],
      ),
    );
  }
}

class _AuthorRow extends StatelessWidget {
  final RedditPost post;

  const _AuthorRow({required this.post});

  @override
  Widget build(BuildContext context) {
    final author = post.author;
    final date = post.createdAt;
    final meta = TextStyle(fontSize: 13, height: 1.3, color: tweetSecondaryColor(context));
    return Row(
      children: [
        if (author != null) ...[
          RedditAvatar(name: author, size: 22, round: true),
          const SizedBox(width: 8),
          Flexible(
            child: Text(
              'u/$author',
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: meta.copyWith(fontWeight: FontWeight.w500, color: tweetPrimaryColor(context)),
            ),
          ),
          const SizedBox(width: 8),
        ],
        if (date != null) Text(createCompactDate(date), style: meta),
      ],
    );
  }
}

/// A link post's card: Reddit's preview 168 dp tall over the domain and the
/// open-in-browser arrow, or, with no preview worth stretching, one row with
/// the domain's initial, the domain and "Open in browser".
class RedditThreadLinkCard extends StatelessWidget {
  final RedditPost post;
  final String url;

  const RedditThreadLinkCard({super.key, required this.post, required this.url});

  static const double pictureHeight = 168;

  String get _domain {
    final domain = post.domain ?? Uri.tryParse(url)?.host ?? url;
    return domain.startsWith('www.') ? domain.substring(4) : domain;
  }

  @override
  Widget build(BuildContext context) {
    final preview = post.previewImage;
    return Semantics(
      button: true,
      tooltip: L10n.of(context).open_in_browser,
      child: Material(
        color: xLookInsetSurfaceOf(context),
        clipBehavior: Clip.antiAlias,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(14),
          side: BorderSide(color: tweetDividerColor(context)),
        ),
        child: InkWell(
          onTap: () => openUri(context, url),
          child: preview == null
              ? _fallback(context)
              : Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [_picture(context, preview), _domainRow(context)],
                ),
        ),
      ),
    );
  }

  Widget _picture(BuildContext context, String preview) {
    final prefs = PrefService.of(context);
    final picture = SizedBox(
      height: pictureHeight,
      child: Stack(
        fit: StackFit.expand,
        children: [
          Semantics(
            image: true,
            label: L10n.of(context).media,
            child: CappedNetworkImage(url: preview),
          ),
          if (post.isVideo)
            const Center(
              child: CircleAvatar(
                radius: 24,
                backgroundColor: Colors.black54,
                child: Icon(Icons.play_arrow, color: Colors.white, size: 30),
              ),
            ),
        ],
      ),
    );
    return LayoutBuilder(
      builder: (context, constraints) => RedditSensitiveGate(
        sensitive: redditMediaShouldGate(post, prefs),
        revealKey: post.id,
        kind: redditMediaGateKind(post, prefs),
        aspectRatio: constraints.maxWidth / pictureHeight,
        child: picture,
      ),
    );
  }

  Widget _domainRow(BuildContext context) {
    final muted = tweetSecondaryColor(context);
    return ConstrainedBox(
      constraints: const BoxConstraints(minHeight: 44),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
        child: Row(
          children: [
            Expanded(
              child: Text(
                _domain,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(fontSize: 13, color: muted),
              ),
            ),
            Icon(Icons.open_in_new, size: 18, color: muted),
          ],
        ),
      ),
    );
  }

  Widget _fallback(BuildContext context) {
    final muted = tweetSecondaryColor(context);
    return ConstrainedBox(
      constraints: const BoxConstraints(minHeight: 56),
      child: Padding(
        padding: const EdgeInsetsDirectional.fromSTEB(10, 8, 14, 8),
        child: Row(
          children: [
            _Initial(letter: _domain.isEmpty ? '' : _domain.characters.first),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    _domain,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: tweetPrimaryColor(context)),
                  ),
                  const SizedBox(height: 2),
                  Text(L10n.of(context).open_in_browser, style: TextStyle(fontSize: 12.5, color: muted)),
                ],
              ),
            ),
            Icon(Icons.open_in_new, size: 18, color: muted),
          ],
        ),
      ),
    );
  }
}

class _Initial extends StatelessWidget {
  final String letter;

  const _Initial({required this.letter});

  @override
  Widget build(BuildContext context) {
    return ExcludeSemantics(
      child: Container(
        width: 36,
        height: 36,
        alignment: Alignment.center,
        decoration: BoxDecoration(color: xLookFloatingSurfaceOf(context), borderRadius: BorderRadius.circular(9)),
        child: Text(
          letter,
          style: TextStyle(fontSize: 15, fontWeight: FontWeight.w700, color: tweetPrimaryColor(context)),
        ),
      ),
    );
  }
}

/// Under the post: the vote pill, the comments pill, and at the end share and
/// save. Where the two ends do not fit beside each other they take a line each.
class RedditThreadPostActions extends StatelessWidget {
  final RedditPost post;
  final int commentCount;
  final VoidCallback onComments;

  const RedditThreadPostActions({super.key, required this.post, required this.commentCount, required this.onComments});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(kTweetHorizontalPadding, 12, 12, 14),
      child: Wrap(
        alignment: WrapAlignment.spaceBetween,
        crossAxisAlignment: WrapCrossAlignment.center,
        runSpacing: 8,
        children: [
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              _PostUpvote(post: post),
              _CommentsPill(count: commentCount, onTap: onComments),
            ],
          ),
          Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              IconButton(
                tooltip: L10n.of(context).share_link,
                icon: Icon(Icons.share_outlined, size: 20, color: tweetSecondaryColor(context)),
                onPressed: () => SharePlus.instance.share(ShareParams(text: redditPostUrl(post))),
              ),
              RedditThreadSaveButton(post: post),
            ],
          ),
        ],
      ),
    );
  }
}

class _PostUpvote extends StatelessWidget {
  final RedditPost post;

  const _PostUpvote({required this.post});

  @override
  Widget build(BuildContext context) {
    return RedditUpvoteScope(
      id: post.id,
      builder: (context, upvoted, votes) => RedditUpvoteButton(
        filled: true,
        upvoted: upvoted,
        score: post.score,
        onTap: () async {
          await votes.toggle(post.id);
          if (!context.mounted) {
            return;
          }
          await syncRedditLikeToArchive(context, post, upvoted: votes.isUpvoted(post.id));
        },
      ),
    );
  }
}

class _CommentsPill extends StatelessWidget {
  final int count;
  final VoidCallback onTap;

  const _CommentsPill({required this.count, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return MergeSemantics(
      child: Semantics(
        button: true,
        label: L10n.of(context).plugin_reddit_comment_count(count),
        child: RedditThreadPill(
          onTap: onTap,
          child: ExcludeSemantics(
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(Icons.mode_comment_outlined, size: 19, color: tweetSecondaryColor(context)),
                const SizedBox(width: 8),
                Text(
                  compactCount(count),
                  style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: tweetPrimaryColor(context)),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// Saves the post on this device: filed in the archive when there is one, a
/// long press choosing the folder; otherwise in the plugin's own list.
class RedditThreadSaveButton extends StatelessWidget {
  final RedditPost post;

  const RedditThreadSaveButton({super.key, required this.post});

  @override
  Widget build(BuildContext context) {
    final archive = context.read<SavedTweetModel?>();
    if (archive != null) {
      return ScopedBuilder<SavedTweetModel, List<SavedTweet>>(
        store: archive,
        distinct: (_) => archive.isSaved(redditArchiveId(post.id)),
        onState: (context, _) {
          final saved = archive.isSaved(redditArchiveId(post.id));
          return GestureDetector(
            onLongPress: () => pickRedditPostFolder(context, post),
            child: _button(
              context,
              saved: saved,
              tooltip: saved ? L10n.of(context).unsave_from_this_device : L10n.of(context).save_on_this_device,
              onPressed: () => saved ? unfileRedditPost(context, post) : fileRedditPost(context, post),
            ),
          );
        },
      );
    }
    final store = context.read<RedditSavedStore>();
    return ScopedBuilder<RedditSavedStore, List<RedditPost>>(
      store: store,
      onState: (context, _) {
        final saved = store.isSaved(post);
        return _button(
          context,
          saved: saved,
          tooltip: saved ? L10n.of(context).action_unsave_post : L10n.of(context).action_save_post,
          onPressed: () => store.toggle(post),
        );
      },
    );
  }

  Widget _button(
    BuildContext context, {
    required bool saved,
    required String tooltip,
    required VoidCallback onPressed,
  }) {
    return IconButton(
      tooltip: tooltip,
      isSelected: saved,
      icon: Icon(
        saved ? Icons.bookmark : Icons.bookmark_border,
        size: 20,
        color: saved ? redditThreadAccent(context) : tweetSecondaryColor(context),
      ),
      onPressed: onPressed,
    );
  }
}
