import 'dart:io';

import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:share_plus/share_plus.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/home/_account_avatar.dart';
import 'package:xta/home/chrome_avatar.dart';
import 'package:xta/saved/local_post_files.dart';
import 'package:xta/saved/local_post_logic.dart';
import 'package:xta/tweet/tweet.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/tweet/tweet_footer.dart';
import 'package:xta/ui/dates.dart';

class LocalPostTile extends StatelessWidget {
  final LocalPost post;
  final VoidCallback onEdit;
  final VoidCallback onDelete;
  final VoidCallback? onReply;
  final VoidCallback? onOpen;
  final int replyCount;

  const LocalPostTile({
    super.key,
    required this.post,
    required this.onEdit,
    required this.onDelete,
    this.onReply,
    this.onOpen,
    this.replyCount = 0,
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
          FutureBuilder<Account?>(
            future: primaryAccount(),
            builder: (context, snapshot) {
              final account = snapshot.data;
              final name =
                  (account?.screenName != null &&
                      account!.screenName!.isNotEmpty)
                  ? account.screenName!
                  : l10n.local_note_author;
              final handle = account?.screenName;
              return ListTile(
                onTap: open,
                leading: ChromeAvatarMark(account: account, size: 48),
                title: Text(
                  name,
                  overflow: TextOverflow.ellipsis,
                  style: theme.textTheme.bodyMedium?.copyWith(
                    fontWeight: FontWeight.w600,
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
                subtitle: Row(
                  children: [
                    Flexible(
                      child: Text(
                        '@${handle ?? l10n.local_note_handle}',
                        overflow: TextOverflow.ellipsis,
                        style: theme.textTheme.labelMedium?.copyWith(
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                      ),
                    ),
                    const SizedBox(width: 4),
                    DefaultTextStyle(
                      style:
                          theme.textTheme.labelMedium?.copyWith(
                            color: theme.colorScheme.onSurfaceVariant,
                          ) ??
                          theme.textTheme.bodySmall!,
                      child: Timestamp(
                        timestamp: post.createdAt,
                        absoluteTimestamp: prefs.get(
                          optionUseAbsoluteTimestamp,
                        ),
                        compact: true,
                      ),
                    ),
                  ],
                ),
                trailing: PopupMenuButton<String>(
                  onSelected: (value) {
                    if (value == 'reply') {
                      onReply?.call();
                    } else if (value == 'edit') {
                      onEdit();
                    } else if (value == 'delete') {
                      onDelete();
                    }
                  },
                  itemBuilder: (context) => [
                    if (onReply != null)
                      PopupMenuItem(
                        value: 'reply',
                        child: Text(l10n.local_note_reply_action),
                      ),
                    PopupMenuItem(
                      value: 'edit',
                      child: Text(l10n.local_note_edit_title),
                    ),
                    PopupMenuItem(value: 'delete', child: Text(l10n.delete)),
                  ],
                ),
              );
            },
          ),
          if (post.body.isNotEmpty)
            InkWell(
              onTap: open,
              child: Padding(
                padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
                child: Text(post.body, style: theme.textTheme.bodyLarge),
              ),
            ),
          if (post.media.isNotEmpty)
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
              child: LocalPostMediaBlock(postId: post.id, media: post.media),
            ),
          if (quoted != null)
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

  const _NoteFooter({required this.replyCount, required this.onReply});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final tint = tweetFooterButtonsColorOf(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(8, 0, 8, 4),
      child: Align(
        alignment: Alignment.centerLeft,
        child: Tooltip(
          message: l10n.local_note_reply_action,
          child: tweetFooterTextButton(
            Icons.chat_bubble_outline,
            replyCount > 0 ? '$replyCount' : '',
            tint,
            onReply,
          ),
        ),
      ),
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
