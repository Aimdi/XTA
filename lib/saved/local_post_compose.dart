import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:path/path.dart' as p;
import 'package:provider/provider.dart';
import 'package:uuid/uuid.dart';
import 'package:xta/client/client.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/saved/local_post_files.dart';
import 'package:xta/saved/local_post_logic.dart';
import 'package:xta/saved/local_post_model.dart';
import 'package:xta/tweet/tweet.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/tweet/tweet_context_scope.dart';

Future<LocalPost?> openLocalPostComposer(
  BuildContext context, {
  LocalPost? existing,
  TweetWithCard? quotedTweet,
}) {
  return showModalBottomSheet<LocalPost>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    showDragHandle: true,
    builder: (sheetContext) =>
        LocalPostComposeSheet(existing: existing, quotedTweet: quotedTweet),
  );
}

class LocalPostComposeSheet extends StatefulWidget {
  final LocalPost? existing;
  final TweetWithCard? quotedTweet;

  const LocalPostComposeSheet({super.key, this.existing, this.quotedTweet});

  @override
  State<LocalPostComposeSheet> createState() => _LocalPostComposeSheetState();
}

class _LocalPostComposeSheetState extends State<LocalPostComposeSheet> {
  late final TextEditingController _controller;
  late final String _id;
  late List<LocalPostMedia> _media;
  bool _saving = false;
  bool _saved = false;

  TweetWithCard? get _quoted =>
      widget.quotedTweet ?? parseQuotedTweet(widget.existing?.quotedTweetJson);

  @override
  void initState() {
    super.initState();
    _id = widget.existing?.id ?? const Uuid().v4();
    _media = List<LocalPostMedia>.from(widget.existing?.media ?? const []);
    _controller = TextEditingController(text: widget.existing?.body ?? '');
  }

  @override
  void dispose() {
    _controller.dispose();
    if (!_saved && widget.existing == null) {
      deleteLocalPostMediaDir(_id);
    }
    super.dispose();
  }

  bool get _canSave => localPostHasContent(_controller.text, _media);

  Future<void> _attach() async {
    final picked = await FilePicker.pickFile(type: FileType.media);
    if (picked == null || !mounted) {
      return;
    }
    final bytes = await picked.readAsBytes();
    if (bytes.isEmpty || !mounted) {
      return;
    }
    final name = _pickedName(picked);
    final mime = inferLocalPostMime(name, _pickedMime(picked));
    final mediaId = const Uuid().v4();
    await writeLocalPostMediaBytes(postId: _id, mediaId: mediaId, bytes: bytes);
    if (!mounted) {
      return;
    }
    setState(() {
      _media = [..._media, LocalPostMedia(id: mediaId, name: name, mime: mime)];
    });
  }

  String _pickedName(Object picked) {
    try {
      final name = (picked as dynamic).name as String?;
      if (name != null && name.isNotEmpty) {
        return p.basename(name);
      }
    } catch (_) {}
    try {
      final path = (picked as dynamic).path as String?;
      if (path != null && path.isNotEmpty) {
        return p.basename(path);
      }
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
    if (!localPostHasContent(_controller.text, _media)) {
      return;
    }
    setState(() => _saving = true);
    try {
      final quoted = _quoted;
      final post = await context.read<LocalPostModel>().saveLocalPost(
        id: _id,
        body: _controller.text,
        media: _media,
        quotedTweetId: quoted?.idStr ?? widget.existing?.quotedTweetId,
        quotedTweetJson: quoted != null
            ? encodeQuotedTweet(quoted)
            : widget.existing?.quotedTweetJson,
      );
      if (!mounted) {
        return;
      }
      _saved = true;
      Navigator.pop(context, post);
    } finally {
      if (mounted) {
        setState(() => _saving = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    final editing = widget.existing != null;
    final quoted = _quoted;

    return Padding(
      padding: EdgeInsets.only(
        left: 16,
        right: 16,
        bottom: MediaQuery.viewInsetsOf(context).bottom + 16,
      ),
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              editing
                  ? l10n.local_note_edit_title
                  : l10n.local_note_compose_title,
              style: theme.textTheme.titleLarge,
            ),
            const SizedBox(height: 8),
            Text(
              l10n.local_note_device_notice,
              style: theme.textTheme.bodySmall?.copyWith(
                color: theme.hintColor,
              ),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: _controller,
              autofocus: quoted == null && _media.isEmpty,
              minLines: 4,
              maxLines: 12,
              textCapitalization: TextCapitalization.sentences,
              onChanged: (_) => setState(() {}),
              decoration: InputDecoration(
                hintText: l10n.local_note_hint,
                border: const OutlineInputBorder(),
              ),
            ),
            if (_media.isNotEmpty) ...[
              const SizedBox(height: 12),
              _ComposeMediaStrip(
                postId: _id,
                media: _media,
                onRemove: (id) {
                  setState(() {
                    _media = _media.where((item) => item.id != id).toList();
                  });
                },
              ),
            ],
            if (quoted != null) ...[
              const SizedBox(height: 12),
              IgnorePointer(
                child: TweetContextScope(
                  child: Container(
                    decoration: quoteCardDecoration(context),
                    clipBehavior: Clip.antiAlias,
                    child: TweetTile(
                      clickable: false,
                      tweet: quoted,
                      addSeparator: false,
                      isQuotedTweet: true,
                    ),
                  ),
                ),
              ),
            ],
            const SizedBox(height: 12),
            Row(
              children: [
                IconButton(
                  tooltip: l10n.local_note_attach,
                  onPressed: _saving ? null : _attach,
                  icon: const Icon(Icons.perm_media_outlined),
                ),
                const Spacer(),
                FilledButton.icon(
                  onPressed: _saving || !_canSave ? null : _save,
                  icon: _saving
                      ? const SizedBox(
                          width: 16,
                          height: 16,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Icon(Icons.save_outlined),
                  label: Text(l10n.local_note_save),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _ComposeMediaStrip extends StatelessWidget {
  final String postId;
  final List<LocalPostMedia> media;
  final ValueChanged<String> onRemove;

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
              _ComposeMediaThumb(postId: postId, media: item),
              Positioned(
                top: 0,
                right: 0,
                child: IconButton.filledTonal(
                  tooltip: l10n.local_note_remove_media,
                  visualDensity: VisualDensity.compact,
                  iconSize: 16,
                  onPressed: () => onRemove(item.id),
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

class _ComposeMediaThumb extends StatelessWidget {
  final String postId;
  final LocalPostMedia media;

  const _ComposeMediaThumb({required this.postId, required this.media});

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<File>(
      future: localPostMediaFile(postId, media.id),
      builder: (context, snapshot) {
        final file = snapshot.data;
        final exists = file != null;
        Widget child;
        if (exists && media.isImage) {
          child = Image.file(file, width: 96, height: 96, fit: BoxFit.cover);
        } else {
          child = SizedBox(
            width: 96,
            height: 96,
            child: ColoredBox(
              color: Theme.of(context).colorScheme.surfaceContainerHighest,
              child: Icon(
                media.isVideo ? Icons.videocam : Icons.insert_drive_file,
              ),
            ),
          );
        }
        return ClipRRect(borderRadius: BorderRadius.circular(12), child: child);
      },
    );
  }
}
