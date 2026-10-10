import 'package:flutter/material.dart';
import 'package:xta/plugins/threads/threads_image.dart';
import 'package:xta/plugins/threads/threads_media.dart';
import 'package:xta/plugins/threads/threads_models.dart';
import 'package:xta/plugins/threads/threads_rich_text.dart';
import 'package:xta/plugins/threads/threads_thread_screen.dart';
import 'package:xta/subscriptions/widgets/fallback_avatar.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/dates.dart';

/// The post a Threads post quotes, boxed under it the way the app shows one.
/// Tapping it opens that post's own conversation.
class ThreadsQuotedPost extends StatelessWidget {
  final ThreadsPost quote;

  const ThreadsQuotedPost({super.key, required this.quote});

  void _open(BuildContext context) =>
      Navigator.push(context, MaterialPageRoute(builder: (_) => ThreadsThreadScreen(post: quote)));

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final radius = tweetMediaRadiusOf(context);

    return Semantics(
      button: true,
      label: '${quote.authorName}: ${quote.text}',
      excludeSemantics: true,
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: () => _open(context),
          borderRadius: BorderRadius.circular(radius),
          child: Container(
            decoration: quoteCardDecoration(context),
            clipBehavior: Clip.antiAlias,
            padding: const EdgeInsets.all(12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                _author(context),
                if (quote.text.isNotEmpty) ...[
                  const SizedBox(height: 6),
                  ThreadsCaption(
                    text: quote.text,
                    fragments: quote.fragments,
                    maxLines: 6,
                    style: theme.textTheme.bodyMedium!.copyWith(height: 1.3),
                  ),
                ],
                if (quote.hasMedia) ...[
                  const SizedBox(height: 8),
                  ThreadsPostMedia(post: quote, onOpenPost: () => _open(context)),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _author(BuildContext context) {
    final theme = Theme.of(context);
    final muted = theme.textTheme.bodySmall!.copyWith(color: theme.colorScheme.onSurfaceVariant);
    final date = quote.publishedAt;
    const size = 20.0;
    final avatar = quote.avatarUrl;

    return Row(
      children: [
        ClipOval(
          child: avatar == null || avatar.isEmpty
              ? FallbackAvatar(
                  seed: quote.handle,
                  displayName: quote.authorName,
                  size: size,
                  accent: theme.colorScheme.primary,
                )
              : ThreadsNetworkImage(
                  avatar,
                  width: size,
                  height: size,
                  cacheWidth: (size * MediaQuery.devicePixelRatioOf(context)).ceil(),
                ),
        ),
        const SizedBox(width: 8),
        Flexible(
          child: Text(
            quote.authorName,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: theme.textTheme.titleSmall!.copyWith(fontWeight: FontWeight.w700),
          ),
        ),
        if (quote.isVerified) ...[
          const SizedBox(width: 4),
          Icon(Icons.verified, size: 14, color: theme.colorScheme.primary),
        ],
        const SizedBox(width: 6),
        Flexible(
          child: Text(
            date == null ? '@${quote.handle}' : '@${quote.handle} · ${createCompactDate(date)}',
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: muted,
          ),
        ),
      ],
    );
  }
}
