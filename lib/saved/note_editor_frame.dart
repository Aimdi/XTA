import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/saved/note_editor_store.dart';
import 'package:xta/saved/note_post_chrome.dart';
import 'package:xta/saved/local_post_logic.dart';
import 'package:xta/ui/motion.dart';
import 'package:xta/ui/reader_chrome.dart';

Future<T?> showNoteEditor<T>(BuildContext context, Widget child) {
  return Navigator.of(context).push<T>(
    PageRouteBuilder<T>(
      fullscreenDialog: true,
      transitionDuration: xtaMotionDuration(context, kXtaMotionNavigation),
      reverseTransitionDuration: xtaMotionDuration(context, kXtaMotionStandard),
      pageBuilder: (_, _, _) => child,
      transitionsBuilder: (_, animation, _, child) => SlideTransition(
        position: animation.drive(
          Tween(begin: const Offset(0, 1), end: Offset.zero).chain(CurveTween(curve: Curves.easeOutCubic)),
        ),
        child: child,
      ),
    ),
  );
}

Future<void> closeNoteEditor(BuildContext context, NoteEditorStore store) async {
  if (store.state.busy) return;
  if (!store.dirty || store.state.saved || store.state.discarded) {
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
        TextButton(onPressed: () => Navigator.pop(context, false), child: Text(l10n.cancel)),
        TextButton(onPressed: () => Navigator.pop(context, true), child: Text(l10n.local_note_discard)),
      ],
    ),
  );
  if (discard == true && await store.discard() && context.mounted) Navigator.pop(context);
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
      canPop: !state.busy && (!store.dirty || state.saved || state.discarded),
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) closeNoteEditor(context, store);
      },
      child: XtaSystemBars(
        child: Scaffold(
          backgroundColor: theme.colorScheme.surface,
          body: SafeArea(
            child: Align(
              alignment: Alignment.topCenter,
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 720),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Padding(
                      padding: const EdgeInsetsDirectional.fromSTEB(8, 8, 16, 8),
                      child: Row(
                        children: [
                          IconButton(
                            tooltip: l10n.close,
                            onPressed: state.busy ? null : () => closeNoteEditor(context, store),
                            icon: const Icon(Icons.close),
                          ),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              title,
                              style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700),
                              maxLines: 2,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                          const SizedBox(width: 8),
                          FilledButton(
                            onPressed: state.busy || !canSave ? null : onSave,
                            style: FilledButton.styleFrom(
                              shape: const StadiumBorder(),
                              minimumSize: const Size(76, 44),
                              padding: const EdgeInsets.symmetric(horizontal: 20),
                            ),
                            child: state.saving
                                ? SizedBox.square(
                                    dimension: 18,
                                    child: CircularProgressIndicator(strokeWidth: 2, semanticsLabel: saveLabel),
                                  )
                                : Text(saveLabel, style: const TextStyle(fontWeight: FontWeight.w700)),
                          ),
                        ],
                      ),
                    ),
                    const Divider(height: 1),
                    if (state.restoring) const LinearProgressIndicator(),
                    if (state.recovered)
                      Padding(padding: const EdgeInsets.all(8), child: Text(l10n.reader_draft_restored)),
                    Expanded(
                      child: SingleChildScrollView(
                        padding: const EdgeInsets.all(16),
                        keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
                        child: Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const NoteAvatar(),
                            const SizedBox(width: 12),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.stretch,
                                children: [const NoteLocalIdentity(), const SizedBox(height: 12), body],
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                    if (state.saveFailed || state.attachFailed || state.draftFailed)
                      Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                        child: Text(
                          (state.saveFailed || state.draftFailed)
                              ? l10n.local_note_save_error
                              : l10n.local_note_attach_error,
                          style: theme.textTheme.bodyMedium?.copyWith(color: theme.colorScheme.error),
                        ),
                      ),
                    const Divider(height: 1),
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                      child: Row(
                        children: [
                          if (leadingAction != null) leadingAction!,
                          const SizedBox(width: 8),
                          Tooltip(
                            message: l10n.local_note_device_notice,
                            child: Icon(Icons.lock_outline, size: 16, color: theme.colorScheme.primary),
                          ),
                          const SizedBox(width: 6),
                          Expanded(
                            child: Text(
                              l10n.local_note_save,
                              style: theme.textTheme.labelMedium?.copyWith(color: theme.colorScheme.primary),
                            ),
                          ),
                          const SizedBox(width: 12),
                          SizedBox.square(
                            dimension: 22,
                            child: CircularProgressIndicator(
                              value: state.body.characters.length / localPostMaxLength,
                              strokeWidth: 2,
                              backgroundColor: theme.colorScheme.outlineVariant,
                              semanticsLabel:
                                  '${l10n.local_note_compose_title}: ${state.body.characters.length} / $localPostMaxLength',
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
