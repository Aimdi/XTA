import 'dart:io';

import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:share_plus/share_plus.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/saved/local_post_files.dart';
import 'package:xta/saved/local_post_logic.dart';
import 'package:xta/tweet/tweet.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/dates.dart';

class LocalPostTile extends StatelessWidget {
  final LocalPost post;
  final VoidCallback onEdit;
  final VoidCallback onDelete;
  final VoidCallback? onReply;
  final VoidCallback? onOpen;
  final int replyCount;
  final bool compact;

  const LocalPostTile({
    super.key,
    required this.post,
    required this.onEdit,
    required this.onDelete,
    this.onReply,
    this.onOpen,
    this.replyCount = 0,
    this.compact = false,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = L10n.of(context);
    final quoted = parseQuotedTweet(post.quotedTweetJson);
    final prefs = PrefService.of(context, listen: false);
    final open = onOpen;

    return tweetFlatCard(
      color: theme.cardTheme.color ?? theme.colorScheme.surface,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: const EdgeInsetsDirectional.fromSTEB(16, 6, 4, 4),
            child: Row(children: [
              Icon(Icons.edit_note_outlined, size: 20,
                  color: theme.colorScheme.onSurfaceVariant),
              const SizedBox(width: 8),
              Expanded(child: Text(l10n.local_note_thread_title,
                style: theme.textTheme.labelLarge)),
              Flexible(flex: 2, child: DefaultTextStyle(
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.labelMedium!.copyWith(
                  color: theme.colorScheme.onSurfaceVariant),
                child: Timestamp(key: ValueKey(post.updatedAt), timestamp: post.updatedAt,
                  absoluteTimestamp: prefs.get(optionUseAbsoluteTimestamp), compact: true),
              )),
              PopupMenuButton<String>(
                onSelected: (value) {
                  if (value == 'reply') onReply?.call();
                  if (value == 'edit') onEdit();
                  if (value == 'delete') onDelete();
                },
                itemBuilder: (context) => [
                  PopupMenuItem(value: 'edit', child: Text(l10n.local_note_edit_title)),
                  if (onReply != null)
                    PopupMenuItem(value: 'reply', child: Text(l10n.local_note_reply_action)),
                  PopupMenuItem(value: 'delete', child: Text(l10n.delete)),
                ],
              ),
            ]),
          ),
          if (post.body.isNotEmpty)
            InkWell(
              onTap: open,
              child: Padding(
                padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
                child: Text(post.body,
                  maxLines: compact ? 7 : null,
                  overflow: compact ? TextOverflow.ellipsis : TextOverflow.visible,
                  style: theme.textTheme.bodyLarge?.copyWith(height: 1.5)),
              ),
            ),
          if (post.media.isNotEmpty)
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
              child: LocalPostMediaBlock(postId: post.id, media: post.media),
            ),
          if (quoted != null && !compact)
            Padding(
              padding: const EdgeInsets.fromLTRB(8, 0, 8, 8),
              child: Container(
                decoration: quoteCardDecoration(context),
                clipBehavior: Clip.antiAlias,
                child: TweetTile(
                  clickable: true,
                  tweet: quoted,
                  addSeparator: false,
                  isQuotedTweet: true,
                ),
              ),
            )
          else if (quoted != null)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
              child: InkWell(
                onTap: open,
                child: Container(
                  padding: const EdgeInsets.all(12),
                  decoration: quoteCardDecoration(context),
                  child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    const Icon(Icons.format_quote, size: 18),
                    const SizedBox(width: 8),
                    Expanded(child: Text(quoted.fullText ?? l10n.clickToShowMore,
                      maxLines: 2, overflow: TextOverflow.ellipsis,
                      style: theme.textTheme.bodyMedium)),
                  ]),
                ),
              ),
            )
          else if (post.quotedTweetId != null)
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
              child: Text(
                l10n.local_note_quoted_unavailable,
                style: theme.textTheme.bodySmall?.copyWith(
                  fontStyle: FontStyle.italic,
                  color: theme.hintColor,
                ),
              ),
            ),
          _NoteFooter(
            replyCount: replyCount,
            onReply: onReply,
            onOpen: compact ? onOpen : null,
            onEdit: onEdit,
          ),
          tweetHairlineDivider(context),
        ],
      ),
    );
  }
}

class _NoteFooter extends StatelessWidget {
  final int replyCount;
  final VoidCallback? onReply;
  final VoidCallback? onOpen;
  final VoidCallback onEdit;

  const _NoteFooter({required this.replyCount, required this.onReply,
    required this.onOpen, required this.onEdit});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Padding(
      padding: const EdgeInsetsDirectional.fromSTEB(8, 0, 8, 6),
      child: Row(children: [
        if (onOpen != null)
          Flexible(child: TextButton.icon(
            onPressed: onOpen,
            icon: const Icon(Icons.notes_outlined, size: 18),
            label: Text(l10n.clickToShowMore),
          )),
        const Spacer(),
        if (onReply != null)
          Tooltip(message: l10n.local_note_reply_action,
            child: TextButton.icon(
              onPressed: replyCount > 0 ? onOpen ?? onReply : onReply,
              icon: const Icon(Icons.chat_bubble_outline, size: 18),
              label: Text(replyCount > 0 ? '$replyCount' : l10n.local_note_reply_action),
            ),
          ),
        IconButton(
          tooltip: l10n.local_note_edit_title,
          onPressed: onEdit,
          icon: const Icon(Icons.edit_outlined, size: 20),
        ),
      ]),
    );
  }
}

class LocalPostMediaBlock extends StatelessWidget {
  final String postId;
  final List<LocalPostMedia> media;

  const LocalPostMediaBlock({
    super.key,
    required this.postId,
    required this.media,
  });

  @override
  Widget build(BuildContext context) {
    final images = media.where((item) => item.isImage).toList();
    final rest = media.where((item) => !item.isImage).toList();
    final radius = tweetMediaRadiusOf(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (images.isNotEmpty)
          ClipRRect(
            borderRadius: BorderRadius.circular(radius),
            child: _ImageGrid(postId: postId, images: images),
          ),
        for (final item in rest) _FileRow(postId: postId, media: item),
      ],
    );
  }
}

class _ImageGrid extends StatelessWidget {
  final String postId;
  final List<LocalPostMedia> images;

  const _ImageGrid({required this.postId, required this.images});

  @override
  Widget build(BuildContext context) {
    if (images.length == 1) {
      return _NoteImage(postId: postId, media: images.first, height: 220);
    }
    return GridView.builder(
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      itemCount: images.length.clamp(0, 4),
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 2,
        mainAxisSpacing: 4,
        crossAxisSpacing: 4,
      ),
      itemBuilder: (context, index) =>
          _NoteImage(postId: postId, media: images[index], height: 140),
    );
  }
}

class _NoteImage extends StatelessWidget {
  final String postId;
  final LocalPostMedia media;
  final double height;

  const _NoteImage({
    required this.postId,
    required this.media,
    required this.height,
  });

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<File>(
      future: localPostMediaFile(postId, media.id),
      builder: (context, snapshot) {
        final file = snapshot.data;
        if (file == null) {
          return SizedBox(
            height: height,
            child: const Center(child: Icon(Icons.image)),
          );
        }
        return GestureDetector(
          onTap: () => _openImage(context, file),
          child: Image.file(
            file,
            height: height,
            width: double.infinity,
            fit: BoxFit.cover,
            errorBuilder: (context, error, stackTrace) => SizedBox(
              height: height,
              child: const Center(child: Icon(Icons.broken_image)),
            ),
          ),
        );
      },
    );
  }

  void _openImage(BuildContext context, File file) {
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => Scaffold(
          backgroundColor: Colors.black,
          appBar: AppBar(backgroundColor: Colors.black),
          body: Center(child: InteractiveViewer(child: Image.file(file))),
        ),
      ),
    );
  }
}

class _FileRow extends StatelessWidget {
  final String postId;
  final LocalPostMedia media;

  const _FileRow({required this.postId, required this.media});

  @override
  Widget build(BuildContext context) {
    return ListTile(
      dense: true,
      contentPadding: EdgeInsets.zero,
      leading: Icon(media.isVideo ? Icons.videocam : Icons.attach_file),
      title: Text(media.name, maxLines: 1, overflow: TextOverflow.ellipsis),
      onTap: () async {
        final file = await localPostMediaFile(postId, media.id);
        if (!await file.exists()) {
          return;
        }
        await Share.shareXFiles([
          XFile(file.path, mimeType: media.mime, name: media.name),
        ]);
      },
    );
  }
}
