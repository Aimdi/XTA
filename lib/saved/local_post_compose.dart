import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:path/path.dart' as p;
import 'package:provider/provider.dart';
import 'package:uuid/uuid.dart';
import 'package:xta/client/client.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/saved/local_post_files.dart';
import 'package:xta/saved/local_post_logic.dart';
import 'package:xta/saved/local_post_model.dart';
import 'package:xta/saved/note_editor_frame.dart';
import 'package:xta/saved/note_editor_store.dart';
import 'package:xta/tweet/tweet.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/tweet/tweet_context_scope.dart';

Future<LocalPost?> openLocalPostComposer(
  BuildContext context, {
  LocalPost? existing,
  TweetWithCard? quotedTweet,
  LocalPost? replyTo,
}) {
  return showNoteEditor<LocalPost>(context, LocalPostComposeSheet(
    existing: existing,
    quotedTweet: quotedTweet,
    replyTo: replyTo,
  ));
}

class LocalPostComposeSheet extends StatefulWidget {
  final LocalPost? existing;
  final TweetWithCard? quotedTweet;
  final LocalPost? replyTo;

  const LocalPostComposeSheet({
    super.key,
    this.existing,
    this.quotedTweet,
    this.replyTo,
  });

  @override
  State<LocalPostComposeSheet> createState() => _LocalPostComposeSheetState();
}

class _LocalPostComposeSheetState extends State<LocalPostComposeSheet> {
  late final _id = widget.existing?.id ?? const Uuid().v4();
  late final _store = NoteEditorStore(
    body: widget.existing?.body ?? '',
    media: widget.existing?.media ?? const [],
  );
  late final _controller = TextEditingController(text: _store.state.body);
  late final _quoted = widget.quotedTweet ??
      parseQuotedTweet(widget.existing?.quotedTweetJson);
  late final Widget? _quotedPreview = _quoted == null ? null :
      _ComposeQuotedPreview(tweet: _quoted!);

  @override
  void dispose() {
    _controller.dispose();
    if (!_store.state.saved) {
      // An abandoned edit only removes its new attachments, never saved files.
      deleteRemovedLocalPostMedia(_id, _store.initialMedia).catchError(
        (Object error, StackTrace stackTrace) {
          LocalPostModel.log.warning('Unable to clean abandoned note attachments', error, stackTrace);
        },
      );
    }
    _store.destroy();
    super.dispose();
  }

  Future<void> _attach() => _store.attach(() async {
    final picked = await FilePicker.pickFile(type: FileType.media);
    if (picked == null || !mounted) return null;
    final bytes = await picked.readAsBytes();
    if (bytes.isEmpty || !mounted) return null;
    final name = _pickedName(picked);
    final mime = inferLocalPostMime(name, _pickedMime(picked));
    final mediaId = const Uuid().v4();
    await writeLocalPostMediaBytes(postId: _id, mediaId: mediaId, bytes: bytes);
    return LocalPostMedia(id: mediaId, name: name, mime: mime);
  });

  String _pickedName(Object picked) {
    try {
      final name = (picked as dynamic).name as String?;
      if (name != null && name.isNotEmpty) return p.basename(name);
    } catch (_) {}
    try {
      final path = (picked as dynamic).path as String?;
      if (path != null && path.isNotEmpty) return p.basename(path);
    } catch (_) {}
    return 'media';
  }

  String? _pickedMime(Object picked) {
    try {
      return (picked as dynamic).mimeType as String?;
    } catch (_) {
      return null;
    }
  }

  Future<void> _save() async {
    if (!_store.state.hasContent) return;
    LocalPost? post;
    final model = context.read<LocalPostModel>();
    final saved = await _store.save(() async {
      post = await model.saveLocalPost(
        id: _id,
        body: _store.state.body,
        media: _store.state.media,
        quotedTweetId: _quoted?.idStr ?? widget.existing?.quotedTweetId,
        quotedTweetJson: _quoted != null ? encodeQuotedTweet(_quoted!) : widget.existing?.quotedTweetJson,
        inReplyToId: widget.replyTo?.id ?? widget.existing?.inReplyToId,
      );
    });
    if (saved && mounted) Navigator.pop(context, post);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final title = widget.existing != null ? l10n.local_note_edit_title :
        widget.replyTo != null ? l10n.local_note_reply_title : l10n.local_note_compose_title;
    return ScopedBuilder<NoteEditorStore, NoteEditorState>(
      store: _store,
      onState: (context, state) => NoteEditorFrame(
        store: _store,
        title: title,
        saveLabel: l10n.local_note_save,
        canSave: state.hasContent && (widget.existing == null || _store.dirty),
        onSave: _save,
        leadingAction: IconButton(
          tooltip: l10n.local_note_attach,
          onPressed: state.busy ? null : _attach,
          icon: state.attaching ? const SizedBox.square(dimension: 20,
              child: CircularProgressIndicator(strokeWidth: 2)) :
              const Icon(Icons.perm_media_outlined),
        ),
        body: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(l10n.local_note_device_notice,
                style: Theme.of(context).textTheme.bodySmall),
            const SizedBox(height: 16),
            if (widget.replyTo != null) ...[
              _ReplyParentPreview(parent: widget.replyTo!),
              const SizedBox(height: 12),
            ],
            TextField(
              controller: _controller,
              autofocus: true,
              readOnly: state.busy,
              minLines: 5,
              maxLines: null,
              maxLength: localPostMaxLength,
              textCapitalization: TextCapitalization.sentences,
              onChanged: _store.setBody,
              decoration: InputDecoration(
                hintText: l10n.local_note_hint,
                border: const OutlineInputBorder(),
              ),
            ),
            if (state.media.isNotEmpty) ...[
              const SizedBox(height: 12),
              _ComposeMediaStrip(
                postId: _id,
                media: state.media,
                onRemove: state.busy ? null : _store.removeMedia,
              ),
            ],
            if (_quotedPreview != null) ...[
              const SizedBox(height: 12),
              _quotedPreview!,
            ],
          ],
        ),
      ),
    );
  }
}

class _ComposeQuotedPreview extends StatelessWidget {
  final TweetWithCard tweet;

  const _ComposeQuotedPreview({required this.tweet});

  @override
  Widget build(BuildContext context) => IgnorePointer(
    child: TweetContextScope(
      child: Container(
        decoration: quoteCardDecoration(context),
        clipBehavior: Clip.antiAlias,
        child: TweetTile(clickable: false, tweet: tweet,
            addSeparator: false, isQuotedTweet: true),
      ),
    ),
  );
}

class _ComposeMediaStrip extends StatelessWidget {
  final String postId;
  final List<LocalPostMedia> media;
  final ValueChanged<String>? onRemove;

  const _ComposeMediaStrip({
    required this.postId,
    required this.media,
    required this.onRemove,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return SizedBox(
      height: 96,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        itemCount: media.length,
        separatorBuilder: (context, index) => const SizedBox(width: 8),
        itemBuilder: (context, index) {
          final item = media[index];
          return Stack(
            children: [
              _ComposeMediaThumb(key: ValueKey(item.id), postId: postId, media: item),
              PositionedDirectional(
                top: 0,
                end: 0,
                child: IconButton.filledTonal(
                  tooltip: l10n.local_note_remove_media,
                  iconSize: 16,
                  onPressed: onRemove == null ? null : () => onRemove!(item.id),
                  icon: const Icon(Icons.close),
                ),
              ),
            ],
          );
        },
      ),
    );
  }
}

class _ComposeMediaThumb extends StatefulWidget {
  final String postId;
  final LocalPostMedia media;

  const _ComposeMediaThumb({super.key, required this.postId, required this.media});

  @override
  State<_ComposeMediaThumb> createState() => _ComposeMediaThumbState();
}

class _ComposeMediaThumbState extends State<_ComposeMediaThumb> {
  late final _file = localPostMediaFile(widget.postId, widget.media.id);

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<File>(
      future: _file,
      builder: (context, snapshot) {
        final file = snapshot.data;
        final exists = file != null;
        Widget child;
        if (exists && widget.media.isImage) {
          child = Image.file(file, width: 96, height: 96, fit: BoxFit.cover,
            errorBuilder: (_, __, ___) => const SizedBox.square(dimension: 96,
                child: Icon(Icons.broken_image_outlined)));
        } else {
          child = SizedBox(
            width: 96,
            height: 96,
            child: ColoredBox(
              color: Theme.of(context).colorScheme.surfaceContainerHighest,
              child: Icon(
                widget.media.isVideo ? Icons.videocam : Icons.insert_drive_file,
              ),
            ),
          );
        }
        return ClipRRect(borderRadius: BorderRadius.circular(12), child: child);
      },
    );
  }
}

class _ReplyParentPreview extends StatelessWidget {
  final LocalPost parent;

  const _ReplyParentPreview({required this.parent});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    final snippet = parent.body.isNotEmpty
        ? parent.body
        : l10n.local_note_attach;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(12),
      decoration: quoteCardDecoration(context),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            l10n.local_note_replying_to,
            style: theme.textTheme.labelMedium?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            snippet,
            maxLines: 4,
            overflow: TextOverflow.ellipsis,
            style: theme.textTheme.bodyMedium,
          ),
        ],
      ),
    );
  }
}
