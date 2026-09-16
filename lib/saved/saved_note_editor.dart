import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/saved/note_editor_frame.dart';
import 'package:xta/saved/note_editor_store.dart';
import 'package:xta/saved/note_post_chrome.dart';

Future<void> openSavedNoteEditor(
  BuildContext context, {
  required String? note,
  String? draftKey,
  bool allowUnchanged = false,
  required Future<void> Function(String?) onSave,
}) async {
  await showNoteEditor<void>(context, _SavedNoteEditor(note: note, onSave: onSave, draftKey: draftKey, allowUnchanged: allowUnchanged));
}

class _SavedNoteEditor extends StatefulWidget {
  final String? note;
  final String? draftKey;
  final bool allowUnchanged;
  final Future<void> Function(String?) onSave;

  const _SavedNoteEditor({required this.note, required this.onSave, this.draftKey, this.allowUnchanged = false});

  @override
  State<_SavedNoteEditor> createState() => _SavedNoteEditorState();
}

class _SavedNoteEditorState extends State<_SavedNoteEditor> {
  late final _store = NoteEditorStore(body: widget.note ?? '', draftKey: widget.draftKey);
  late final _controller = TextEditingController(text: widget.note ?? '');

  @override
  void initState() {
    super.initState();
    _store.restoreDraft().then((_) {
      if (mounted) _controller.text = _store.state.body;
    });
  }

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
        canSave: _store.dirty || widget.allowUnchanged,
        onSave: _save,
        body: NoteComposeField(
          controller: _controller,
          readOnly: state.busy,
          onChanged: _store.setBody,
          hint: l10n.clip_note_hint,
        ),
      ),
    );
  }
}
