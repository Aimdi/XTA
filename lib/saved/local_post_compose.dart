import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/saved/local_post_logic.dart';
import 'package:xta/saved/local_post_model.dart';

Future<LocalPost?> openLocalPostComposer(
  BuildContext context, {
  LocalPost? existing,
}) {
  return showModalBottomSheet<LocalPost>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    showDragHandle: true,
    builder: (sheetContext) => LocalPostComposeSheet(existing: existing),
  );
}

class LocalPostComposeSheet extends StatefulWidget {
  final LocalPost? existing;

  const LocalPostComposeSheet({super.key, this.existing});

  @override
  State<LocalPostComposeSheet> createState() => _LocalPostComposeSheetState();
}

class _LocalPostComposeSheetState extends State<LocalPostComposeSheet> {
  late final TextEditingController _controller;
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: widget.existing?.body ?? '');
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  bool get _canSave => normalizeLocalPostBody(_controller.text) != null;

  Future<void> _save() async {
    final body = _controller.text;
    if (normalizeLocalPostBody(body) == null) {
      return;
    }
    setState(() => _saving = true);
    try {
      final post = await context.read<LocalPostModel>().saveLocalPost(
        id: widget.existing?.id,
        body: body,
      );
      if (!mounted) {
        return;
      }
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

    return Padding(
      padding: EdgeInsets.only(
        left: 16,
        right: 16,
        bottom: MediaQuery.viewInsetsOf(context).bottom + 16,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            editing ? l10n.local_note_edit_title : l10n.local_note_compose_title,
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
            autofocus: true,
            minLines: 4,
            maxLines: 12,
            textCapitalization: TextCapitalization.sentences,
            onChanged: (_) => setState(() {}),
            decoration: InputDecoration(
              hintText: l10n.local_note_hint,
              border: const OutlineInputBorder(),
            ),
          ),
          const SizedBox(height: 12),
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
    );
  }
}
