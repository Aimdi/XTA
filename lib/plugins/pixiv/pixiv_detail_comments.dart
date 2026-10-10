import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_comment_models.dart';
import 'package:xta/plugins/pixiv/pixiv_comments_screen.dart';
import 'package:xta/plugins/plugin_counts.dart';

/// "View comments (N)" for a work or novel whose own total is [count]; one
/// listed without a total shows no number rather than a misleading zero.
String pixivCommentsLabel(L10n l10n, int count) =>
    count > 0 ? l10n.plugin_pixiv_comments_view_count(compactCount(count)) : l10n.plugin_pixiv_comments_view;

/// "View comments (N)" under a work, opening what readers wrote about it.
class PixivCommentsLink extends StatelessWidget {
  final PixivCommentTarget target;
  final int count;

  const PixivCommentsLink({super.key, required this.target, required this.count});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return ListTile(
      key: const ValueKey('pixiv-comments-link'),
      contentPadding: EdgeInsets.zero,
      leading: const Icon(Icons.forum_outlined),
      title: Text(pixivCommentsLabel(l10n, count)),
      trailing: const Icon(Icons.chevron_right),
      onTap: () => openPixivComments(context, target),
    );
  }
}
