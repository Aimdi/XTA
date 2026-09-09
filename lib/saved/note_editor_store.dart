import 'package:flutter/foundation.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/saved/local_post_logic.dart';

@immutable
class NoteEditorState {
  final String body;
  final List<LocalPostMedia> media;
  final bool saving;
  final bool attaching;
  final bool saved;
  final bool saveFailed;
  final bool attachFailed;

  const NoteEditorState({
    required this.body,
    this.media = const [],
    this.saving = false,
    this.attaching = false,
    this.saved = false,
    this.saveFailed = false,
    this.attachFailed = false,
  });

  bool get busy => saving || attaching;
  bool get hasContent => localPostHasContent(body, media);

  NoteEditorState copyWith({
    String? body,
    List<LocalPostMedia>? media,
    bool? saving,
    bool? attaching,
    bool? saved,
    bool? saveFailed,
    bool? attachFailed,
  }) => NoteEditorState(
    body: body ?? this.body,
    media: media ?? this.media,
    saving: saving ?? this.saving,
    attaching: attaching ?? this.attaching,
    saved: saved ?? this.saved,
    saveFailed: saveFailed ?? this.saveFailed,
    attachFailed: attachFailed ?? this.attachFailed,
  );
}

class NoteEditorStore extends Store<NoteEditorState> {
  final String initialBody;
  final List<LocalPostMedia> initialMedia;

  NoteEditorStore({String body = '', List<LocalPostMedia> media = const []})
    : initialBody = body,
      initialMedia = List.unmodifiable(media),
      super(NoteEditorState(body: body, media: List.unmodifiable(media)));

  bool get dirty =>
      state.body != initialBody ||
      !listEquals(state.media.map((item) => item.id).toList(), initialMedia.map((item) => item.id).toList());

  void setBody(String body) {
    if (!state.busy) update(state.copyWith(body: body));
  }

  void removeMedia(String id) {
    if (state.busy) return;
    update(state.copyWith(media: List.unmodifiable(state.media.where((item) => item.id != id))));
  }

  Future<void> attach(Future<LocalPostMedia?> Function() operation) async {
    if (state.busy) return;
    update(state.copyWith(attaching: true, attachFailed: false));
    try {
      final media = await operation();
      update(
        state.copyWith(
          attaching: false,
          media: media == null ? state.media : List.unmodifiable([...state.media, media]),
        ),
      );
    } catch (_) {
      update(state.copyWith(attaching: false, attachFailed: true));
    }
  }

  Future<bool> save(Future<void> Function() operation) async {
    if (state.busy) return false;
    update(state.copyWith(saving: true, saveFailed: false));
    try {
      await operation();
      update(state.copyWith(saving: false, saved: true));
      return true;
    } catch (_) {
      update(state.copyWith(saving: false, saveFailed: true));
      return false;
    }
  }
}
