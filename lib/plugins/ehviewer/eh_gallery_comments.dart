import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/ehviewer/eh_gallery_header.dart';
import 'package:xta/plugins/ehviewer/eh_models.dart';

/// How many comments the gallery page shows before "show all".
const ehCommentPreviewCount = 3;
const _ehCommentPreviewLines = 4;

/// The first few comments, and a button that opens all of them in a sheet.
class EhGalleryComments extends StatelessWidget {
  final List<EhComment> comments;

  const EhGalleryComments({super.key, required this.comments});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        EhSectionTitle(l10n.plugin_eh_comments),
        if (comments.isEmpty)
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 4, 16, 0),
            child: Text(l10n.plugin_eh_empty_comments, style: Theme.of(context).textTheme.bodyMedium),
          ),
        for (final comment in comments.take(ehCommentPreviewCount))
          EhCommentTile(comment: comment, maxLines: _ehCommentPreviewLines),
        if (comments.length > ehCommentPreviewCount)
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 8),
            child: Align(
              alignment: AlignmentDirectional.centerStart,
              child: TextButton(
                key: const ValueKey('eh-gallery-all-comments'),
                style: TextButton.styleFrom(minimumSize: const Size(48, 48)),
                onPressed: () => showEhCommentsSheet(context, comments),
                child: Text(l10n.plugin_eh_gallery_all_comments(comments.length)),
              ),
            ),
          ),
      ],
    );
  }
}

Future<void> showEhCommentsSheet(BuildContext context, List<EhComment> comments) {
  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    showDragHandle: true,
    builder: (context) => DraggableScrollableSheet(
      expand: false,
      initialChildSize: 0.75,
      maxChildSize: 1,
      builder: (context, controller) => ListView(
        key: const ValueKey('eh-gallery-comments-sheet'),
        controller: controller,
        padding: const EdgeInsets.only(bottom: 24),
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
            child: Semantics(
              header: true,
              child: Text(L10n.of(context).plugin_eh_comments, style: Theme.of(context).textTheme.titleMedium),
            ),
          ),
          for (final comment in comments) EhCommentTile(comment: comment),
        ],
      ),
    ),
  );
}

/// One comment: who wrote it (marked when it is the uploader), when, its
/// score, then the text.
class EhCommentTile extends StatelessWidget {
  final EhComment comment;
  final int? maxLines;

  const EhCommentTile({super.key, required this.comment, this.maxLines});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _byline(context),
          const SizedBox(height: 4),
          Text(
            comment.body,
            maxLines: maxLines,
            overflow: maxLines == null ? null : TextOverflow.ellipsis,
            style: theme.textTheme.bodyMedium,
          ),
        ],
      ),
    );
  }

  Widget _byline(BuildContext context) {
    final theme = Theme.of(context);
    final muted = theme.textTheme.labelMedium?.copyWith(color: theme.colorScheme.onSurfaceVariant);
    final score = comment.score;
    return Wrap(
      spacing: 8,
      runSpacing: 4,
      crossAxisAlignment: WrapCrossAlignment.center,
      children: [
        Text(comment.author, style: theme.textTheme.labelLarge?.copyWith(fontWeight: FontWeight.w600)),
        if (comment.uploader) const _EhUploaderBadge(),
        if (comment.posted.isNotEmpty) Text(comment.posted, style: muted),
        if (score != null && score.isNotEmpty)
          Text(score, style: muted, semanticsLabel: L10n.of(context).plugin_eh_gallery_comment_score(score)),
      ],
    );
  }
}

class _EhUploaderBadge extends StatelessWidget {
  const _EhUploaderBadge();

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return DecoratedBox(
      key: const ValueKey('eh-comment-uploader'),
      decoration: BoxDecoration(color: scheme.primaryContainer, borderRadius: BorderRadius.circular(4)),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 1),
        child: Text(
          L10n.of(context).plugin_eh_gallery_uploader_comment,
          style: Theme.of(context).textTheme.labelSmall?.copyWith(color: scheme.onPrimaryContainer),
        ),
      ),
    );
  }
}
