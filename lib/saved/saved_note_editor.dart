import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/saved/local_post_logic.dart';
import 'package:xta/saved/note_editor_frame.dart';
import 'package:xta/saved/note_editor_store.dart';

Future<void> openSavedNoteEditor(
  BuildContext context, {
  required String? note,
  required Future<void> Function(String?) onSave,
}) async {
  await showNoteEditor<void>(context, _SavedNoteEditor(note: note, onSave: onSave));
}

class _SavedNoteEditor extends StatefulWidget {
  final String? note;
  final Future<void> Function(String?) onSave;

  const _SavedNoteEditor({required this.note, required this.onSave});

  @override
  State<_SavedNoteEditor> createState() => _SavedNoteEditorState();
}

class _SavedNoteEditorState extends State<_SavedNoteEditor> {
  late final _store = NoteEditorStore(body: widget.note ?? '');
  late final _controller = TextEditingController(text: widget.note ?? '');

  @override
  void dispose() {
    _controller.dispose();
    _store.destroy();
    super.dispose();
  }

  Future<void> _save() async {
    final body = _store.state.body.trim();
    final saved = await _store.save(() => widget.onSave(body.isEmpty ? null : body));
    if (saved && mounted) Navigator.pop(context);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return ScopedBuilder<NoteEditorStore, NoteEditorState>(
      store: _store,
      onState: (context, state) => NoteEditorFrame(
        store: _store,
        title: widget.note?.isNotEmpty == true ? l10n.local_note_edit_title : l10n.local_note_compose_title,
        saveLabel: l10n.save,
        canSave: _store.dirty,
        onSave: _save,
        body: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(l10n.saves_stay_on_device_notice, style: Theme.of(context).textTheme.bodySmall),
            const SizedBox(height: 16),
            TextField(
              controller: _controller,
              autofocus: true,
              readOnly: state.busy,
              minLines: 6,
              maxLines: null,
              maxLength: localPostMaxLength,
              textCapitalization: TextCapitalization.sentences,
              onChanged: _store.setBody,
              decoration: InputDecoration(
                hintText: l10n.clip_note_hint,
                border: const OutlineInputBorder(),
                alignLabelWithHint: true,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
