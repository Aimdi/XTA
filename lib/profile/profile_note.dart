import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:sqflite/sqflite.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/errors.dart';

typedef ProfileNoteLoader = Future<ProfileNote?> Function(String userId);
typedef ProfileNoteSaver = Future<void> Function(String userId, String note);

Future<ProfileNote?> loadProfileNote(String userId) async {
  final database = await Repository.readOnly();
  final rows = await database.query(
    tableProfileNote,
    where: 'id = ?',
    whereArgs: [userId],
    limit: 1,
  );
  if (rows.isEmpty) {
    return null;
  }
  return ProfileNote.fromMap(rows.first);
}

Future<void> saveProfileNote(String userId, String note) async {
  final trimmed = note.trim();
  final database = await Repository.writable();

  if (trimmed.isEmpty) {
    await database.delete(
      tableProfileNote,
      where: 'id = ?',
      whereArgs: [userId],
    );
    return;
  }

  await database.insert(
    tableProfileNote,
    ProfileNote(id: userId, note: trimmed, updatedAt: DateTime.now()).toMap(),
    conflictAlgorithm: ConflictAlgorithm.replace,
  );
}

@immutable
class ProfileNoteState {
  final String note;
  final bool loading;

  const ProfileNoteState({this.note = '', this.loading = true});
}

class ProfileNoteStore extends Store<ProfileNoteState> {
  final String userId;
  final ProfileNoteLoader loader;
  bool _active = true;

  ProfileNoteStore({required this.userId, required this.loader})
    : super(const ProfileNoteState());

  Future<void> load() async {
    try {
      final note = await loader(userId);
      if (_active) {
        update(ProfileNoteState(note: note?.note ?? '', loading: false));
      }
    } catch (_) {
      if (_active) update(const ProfileNoteState(loading: false));
    }
  }

  void setNote(String value) {
    update(ProfileNoteState(note: value, loading: false));
  }

  @override
  Future<void> destroy() {
    _active = false;
    return super.destroy();
  }
}

/// Compact private-note row on a profile — local only, never synced.
///
/// The old inline TextField lived in the collapsing [SliverAppBar] without
/// being counted in `expandedHeight`, so the tab bar ate the field, clipped
/// the label, and hid the save button. A one-line chip plus a sheet keeps the
/// header small and lets typing/saving happen outside the nested scroll view.
class ProfileNoteCard extends StatefulWidget {
  final String userId;
  final ProfileNoteLoader? loader;
  final ProfileNoteSaver? saver;

  const ProfileNoteCard({
    super.key,
    required this.userId,
    this.loader,
    this.saver,
  });

  @override
  State<ProfileNoteCard> createState() => _ProfileNoteCardState();
}

class _ProfileNoteCardState extends State<ProfileNoteCard> {
  late ProfileNoteStore _store;

  ProfileNoteLoader get _loader => widget.loader ?? loadProfileNote;
  ProfileNoteSaver get _saver => widget.saver ?? saveProfileNote;

  @override
  void initState() {
    super.initState();
    _createStore();
  }

  @override
  void didUpdateWidget(covariant ProfileNoteCard oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.userId != widget.userId) {
      _store.destroy();
      _createStore();
    }
  }

  void _createStore() {
    _store = ProfileNoteStore(userId: widget.userId, loader: _loader);
    _store.load();
  }

  @override
  void dispose() {
    _store.destroy();
    super.dispose();
  }

  Future<void> _openEditor() async {
    final state = _store.state;
    if (state.loading) return;
    final saved = await showModalBottomSheet<String>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      showDragHandle: true,
      builder: (sheetContext) => ProfileNoteEditorSheet(
        userId: widget.userId,
        initial: state.note,
        saver: _saver,
      ),
    );
    if (!mounted || saved == null) return;
    _store.setNote(saved);
  }

  @override
  Widget build(BuildContext context) {
    return ScopedBuilder<ProfileNoteStore, ProfileNoteState>(
      store: _store,
      onState: (context, state) {
        final empty = state.note.isEmpty;
        final label = empty ? L10n.of(context).profile_note_title : state.note;
        return Padding(
          padding: const EdgeInsets.only(top: kTweetSpace1),
          child: Material(
            color: Color.alphaBlend(
              tweetSecondaryColor(context).withValues(alpha: 0.06),
              tweetSurfaceColor(context),
            ),
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(8),
              side: BorderSide(
                color: tweetDividerColor(context),
                width: kTweetDividerThickness,
              ),
            ),
            clipBehavior: Clip.antiAlias,
            child: InkWell(
              key: const Key('profile_note_chip'),
              onTap: state.loading ? null : _openEditor,
              borderRadius: BorderRadius.circular(8),
              child: ConstrainedBox(
                constraints: const BoxConstraints(
                  minHeight: kTweetTouchTarget,
                ),
                child: Padding(
                  padding: const EdgeInsets.symmetric(
                    horizontal: kTweetSpace3,
                  ),
                  child: Row(
                    children: [
                      Icon(
                        Icons.sticky_note_2_outlined,
                        size: 18,
                        color: tweetSecondaryColor(context),
                      ),
                      const SizedBox(width: kTweetSpace2),
                      Expanded(
                        child: Text(
                          label,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: empty
                              ? tweetMetadataStyle(context)
                              : tweetBodyStyle(context).copyWith(fontSize: 13),
                        ),
                      ),
                      if (state.loading)
                        const SizedBox.square(
                          dimension: 16,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      else
                        Icon(
                          Icons.edit_outlined,
                          size: 18,
                          color: tweetSecondaryColor(context),
                        ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        );
      },
    );
  }
}

/// Editor sheet for a profile's private note. Pops the trimmed text on save
/// (empty string if the note was cleared).
class ProfileNoteEditorSheet extends StatefulWidget {
  final String userId;
  final String initial;
  final ProfileNoteSaver saver;

  const ProfileNoteEditorSheet({
    super.key,
    required this.userId,
    required this.initial,
    required this.saver,
  });

  @override
  State<ProfileNoteEditorSheet> createState() => _ProfileNoteEditorSheetState();
}

class _ProfileNoteEditorSheetState extends State<ProfileNoteEditorSheet> {
  late final TextEditingController _controller;
  late final _ProfileNoteSavingStore _savingStore;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: widget.initial);
    _savingStore = _ProfileNoteSavingStore();
  }

  @override
  void dispose() {
    _controller.dispose();
    _savingStore.destroy();
    super.dispose();
  }

  Future<void> _save() async {
    if (_savingStore.state) return;
    _savingStore.setSaving(true);
    final trimmed = _controller.text.trim();
    try {
      await widget.saver(widget.userId, trimmed);
      if (mounted) {
        Navigator.pop(context, trimmed);
      }
    } catch (_) {
      if (!mounted) {
        return;
      }
      _savingStore.setSaving(false);
      showSnackBar(
        context,
        icon: '❌',
        message: L10n.of(context).oops_something_went_wrong,
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);

    return Padding(
      padding: EdgeInsetsDirectional.fromSTEB(
        kTweetHorizontalPadding,
        0,
        kTweetHorizontalPadding,
        MediaQuery.viewInsetsOf(context).bottom + kTweetSpace4,
      ),
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(l10n.profile_note_title, style: theme.textTheme.titleMedium),
            const SizedBox(height: kTweetSpace1),
            Text(
              l10n.profile_note_hint,
              style: theme.textTheme.bodySmall?.copyWith(
                color: theme.hintColor,
              ),
            ),
            const SizedBox(height: kTweetSpace3),
            TextField(
              key: const Key('profile_note_field'),
              controller: _controller,
              autofocus: true,
              minLines: 3,
              maxLines: 8,
              textCapitalization: TextCapitalization.sentences,
              textInputAction: TextInputAction.done,
              onSubmitted: (_) => _save(),
              decoration: InputDecoration(
                hintText: l10n.profile_note_hint,
                isDense: true,
                border: const OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: kTweetSpace3),
            ScopedBuilder<_ProfileNoteSavingStore, bool>(
              store: _savingStore,
              onState: (_, saving) => Align(
                alignment: Alignment.centerRight,
                child: FilledButton.icon(
                  key: const Key('profile_note_save'),
                  onPressed: saving ? null : _save,
                  icon: saving
                      ? const SizedBox(
                          width: 18,
                          height: 18,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Icon(Icons.save_outlined, size: 20),
                  label: Text(l10n.profile_note_save),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _ProfileNoteSavingStore extends Store<bool> {
  _ProfileNoteSavingStore() : super(false);

  void setSaving(bool value) => update(value);
}
