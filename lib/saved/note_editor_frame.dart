import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/saved/note_editor_store.dart';
import 'package:xta/ui/motion.dart';

Future<T?> showNoteEditor<T>(BuildContext context, Widget child) {
  return showModalBottomSheet<T>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    isDismissible: false,
    enableDrag: false,
    showDragHandle: false,
    constraints: const BoxConstraints(maxWidth: 720),
    sheetAnimationStyle: AnimationStyle(
      duration: xtaMotionDuration(context, kXtaMotionStandard),
      reverseDuration: xtaMotionDuration(context, kXtaMotionFast),
    ),
    builder: (_) => child,
  );
}

Future<void> closeNoteEditor(BuildContext context, NoteEditorStore store) async {
  if (store.state.busy) return;
  if (!store.dirty || store.state.saved) {
    Navigator.pop(context);
    return;
  }
  final l10n = L10n.of(context);
  final discard = await showDialog<bool>(
    context: context,
    builder: (context) => AlertDialog(
      title: Text(l10n.local_note_discard_title),
      content: Text(l10n.local_note_discard_message),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context, false),
          child: Text(l10n.cancel)),
        TextButton(onPressed: () => Navigator.pop(context, true),
          child: Text(l10n.local_note_discard)),
      ],
    ),
  );
  if (discard == true && context.mounted) Navigator.pop(context);
}

class NoteEditorFrame extends StatelessWidget {
  final NoteEditorStore store;
  final String title;
  final String saveLabel;
  final bool canSave;
  final VoidCallback onSave;
  final Widget body;
  final Widget? leadingAction;

  const NoteEditorFrame({
    super.key,
    required this.store,
    required this.title,
    required this.saveLabel,
    required this.canSave,
    required this.onSave,
    required this.body,
    this.leadingAction,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    final state = store.state;
    return PopScope(
      canPop: !state.busy && (!store.dirty || state.saved),
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) closeNoteEditor(context, store);
      },
      child: LayoutBuilder(builder: (context, constraints) {
        final keyboard = MediaQuery.viewInsetsOf(context).bottom;
        final available = (constraints.maxHeight - keyboard).clamp(0.0, double.infinity).toDouble();
        return Padding(
          padding: EdgeInsets.only(bottom: keyboard),
          child: SizedBox(
            height: available * .94,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Padding(
                  padding: const EdgeInsetsDirectional.fromSTEB(8, 8, 16, 8),
                  child: Row(children: [
                    IconButton(
                      tooltip: l10n.close,
                      onPressed: state.busy ? null : () => closeNoteEditor(context, store),
                      icon: const Icon(Icons.close),
                    ),
                    const SizedBox(width: 8),
                    Expanded(child: Text(title, style: theme.textTheme.titleLarge,
                      maxLines: 2, overflow: TextOverflow.ellipsis)),
                  ]),
                ),
                const Divider(height: 1),
                Expanded(child: SingleChildScrollView(
                  padding: const EdgeInsets.all(16),
                  keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
                  child: body,
                )),
                if (state.saveFailed || state.attachFailed)
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                    child: Text(
                      state.saveFailed ? l10n.local_note_save_error : l10n.local_note_attach_error,
                      style: theme.textTheme.bodyMedium?.copyWith(color: theme.colorScheme.error),
                    ),
                  ),
                const Divider(height: 1),
                SafeArea(top: false, child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
                  child: Row(children: [
                    if (leadingAction != null) leadingAction!,
                    const Spacer(),
                    Flexible(flex: 3, child: FilledButton.icon(
                      onPressed: state.busy || !canSave ? null : onSave,
                      icon: state.saving
                        ? const SizedBox.square(dimension: 18,
                            child: CircularProgressIndicator(strokeWidth: 2))
                        : const Icon(Icons.check),
                      label: Text(saveLabel),
                    )),
                  ]),
                )),
              ],
            ),
          ),
        );
      }),
    );
  }
}
