import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/profile/media_grid/media_grid_items/media_grid_item.dart';
import 'package:xta/status.dart';
import 'package:xta/tweet/tweet_chrome.dart';

/// Broadcast metadata sits outside the preview so even audio-only rooms remain
/// identifiable, and long titles do not compete with playback controls.
class BroadcastMediaCard extends StatelessWidget {
  final BroadcastGridItem item;
  final Widget preview;

  const BroadcastMediaCard({super.key, required this.item, required this.preview});

  String get _excerpt {
    for (final text in [item.tweet?.noteText, item.tweet?.fullText, item.tweet?.text]) {
      final excerpt = (text ?? '').replaceAll(RegExp(r'https?://\S+'), '').replaceAll(RegExp(r'\s+'), ' ').trim();
      if (excerpt.isNotEmpty) return excerpt;
    }
    return '';
  }

  void _openPost(BuildContext context) => Navigator.pushNamed(
    context,
    routeStatus,
    arguments: StatusScreenArguments(
      id: item.tweetId,
      username: item.username,
      tweetOpened: true,
      initialTweet: item.tweet,
    ),
  );

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = L10n.of(context);
    final date = item.tweet?.createdAt;
    final kind = item.isSpace ? l10n.spaces : l10n.broadcasts;
    final title = _excerpt.isEmpty ? kind : _excerpt;
    return Align(
      alignment: Alignment.topCenter,
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 720),
        child: Container(
          margin: const EdgeInsets.only(bottom: 16),
          decoration: BoxDecoration(
            color: tweetSurfaceColor(context),
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: tweetDividerColor(context)),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              preview,
              Padding(
                padding: const EdgeInsets.fromLTRB(14, 12, 14, 4),
                child: Text(
                  title,
                  maxLines: 3,
                  overflow: TextOverflow.ellipsis,
                  style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700, height: 1.25),
                ),
              ),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 14),
                child: Wrap(
                  spacing: 10,
                  runSpacing: 4,
                  children: [
                    if (item.username.isNotEmpty)
                      Text(
                        '@${item.username}',
                        style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                      ),
                    if (date != null)
                      Text(
                        DateFormat.yMMMd().format(date.toLocal()),
                        style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                      ),
                  ],
                ),
              ),
              Padding(
                padding: const EdgeInsetsDirectional.fromSTEB(14, 4, 6, 4),
                child: Row(
                  children: [
                    Icon(
                      item.isSpace ? Icons.graphic_eq : Icons.live_tv_outlined,
                      size: 18,
                      color: theme.colorScheme.primary,
                    ),
                    const SizedBox(width: 8),
                    Expanded(child: Text(kind, style: theme.textTheme.labelMedium)),
                    TextButton.icon(
                      onPressed: () => _openPost(context),
                      icon: const Icon(Icons.article_outlined, size: 18),
                      label: Text(l10n.open_post),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
