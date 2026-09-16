import 'dart:async';
import 'package:xta/utils/local_json_store.dart';
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
  final bool restoring;
  final bool draftFailed;
  final bool recovered;
  final bool discarded;

  const NoteEditorState({
    required this.body,
    this.media = const [],
    this.saving = false,
    this.attaching = false,
    this.saved = false,
    this.saveFailed = false,
    this.attachFailed = false,
    this.restoring = false,
    this.draftFailed = false,
    this.recovered = false,
    this.discarded = false,
  });

  bool get busy => saving || attaching || restoring;
  bool get hasContent => localPostHasContent(body, media);

  NoteEditorState copyWith({
    String? body,
    List<LocalPostMedia>? media,
    bool? saving,
    bool? attaching,
    bool? saved,
    bool? saveFailed,
    bool? attachFailed,
    bool? restoring,
    bool? draftFailed,
    bool? recovered,
    bool? discarded,
  }) => NoteEditorState(
    body: body ?? this.body,
    media: media ?? this.media,
    saving: saving ?? this.saving,
    attaching: attaching ?? this.attaching,
    saved: saved ?? this.saved,
    saveFailed: saveFailed ?? this.saveFailed,
    attachFailed: attachFailed ?? this.attachFailed,
    restoring: restoring ?? this.restoring,
    draftFailed: draftFailed ?? this.draftFailed,
    recovered: recovered ?? this.recovered,
    discarded: discarded ?? this.discarded,
  );
}

class NoteEditorStore extends Store<NoteEditorState> {
  final String? draftKey;
  final JsonStore storage;
  String? postId;
  bool _closed = false;
  final String initialBody;
  final List<LocalPostMedia> initialMedia;

  NoteEditorStore({
    String body = '',
    List<LocalPostMedia> media = const [],
    this.draftKey,
    this.postId,
    JsonStore? storage,
  }) : storage = storage ?? LocalJsonStore.shared,
       initialBody = body,
       initialMedia = List.unmodifiable(media),
       super(NoteEditorState(body: body, media: List.unmodifiable(media), restoring: draftKey != null));

  bool get dirty =>
      state.body != initialBody ||
      !listEquals(state.media.map((item) => item.id).toList(), initialMedia.map((item) => item.id).toList());

  void setBody(String body) {
    if (!state.busy) {
      update(state.copyWith(body: body));
      unawaited(persistDraft());
    }
  }

  void removeMedia(String id) {
    if (state.busy) return;
    update(state.copyWith(media: List.unmodifiable(state.media.where((item) => item.id != id))));
    unawaited(persistDraft());
  }

  Future<void> attach(Future<LocalPostMedia?> Function() operation) async {
    if (state.busy) return;
    update(state.copyWith(attaching: true, attachFailed: false));
    try {
      final media = await operation();
      if (_closed) return;
      update(
        state.copyWith(
          attaching: false,
          media: media == null ? state.media : List.unmodifiable([...state.media, media]),
        ),
      );
      await persistDraft();
    } catch (_) {
      if (_closed) return;
      update(state.copyWith(attaching: false, attachFailed: true));
    }
  }

  Future<bool> save(Future<void> Function() operation) async {
    if (state.busy) return false;
    update(state.copyWith(saving: true, saveFailed: false));
    try {
      await operation();
      if (draftKey != null) await storage.remove('draft:$draftKey');
      if (_closed) return true;
      update(state.copyWith(saving: false, saved: true));
      return true;
    } catch (_) {
      if (_closed) return false;
      update(state.copyWith(saving: false, saveFailed: true));
      return false;
    }
  }

  Future<void> restoreDraft() async {
    if (draftKey == null) return;
    final raw = await storage.read('draft:$draftKey');
    if (_closed) return;
    try {
      if (raw is Map && raw['body'] is String) {
        postId = raw['postId'] is String ? raw['postId'] as String : postId;
        update(
          state.copyWith(
            body: raw['body'] as String,
            media: parseLocalPostMedia(raw['media']),
            restoring: false,
            recovered: true,
          ),
        );
        return;
      }
    } catch (_) {
      /* Ignore an incomplete draft without replacing the saved note. */
    }
    update(state.copyWith(restoring: false));
  }

  Future<void> persistDraft() async {
    if (draftKey == null || state.restoring || state.saved || state.discarded) return;
    final snapshot = {'body': state.body, 'media': state.media.map((e) => e.toJson()).toList(), 'postId': postId};
    try {
      await storage.write('draft:$draftKey', snapshot);
      if (!_closed && state.draftFailed) update(state.copyWith(draftFailed: false));
    } catch (_) {
      if (!_closed) update(state.copyWith(draftFailed: true));
    }
  }

  Future<bool> discard() async {
    try {
      if (draftKey != null) await storage.remove('draft:$draftKey');
      if (!_closed) update(state.copyWith(discarded: true));
      return true;
    } catch (_) {
      if (!_closed) update(state.copyWith(draftFailed: true));
      return false;
    }
  }

  @override
  Future<void> destroy() {
    _closed = true;
    return super.destroy();
  }
}
