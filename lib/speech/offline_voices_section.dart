import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/ui/labeled_pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/offline/offline_ui.dart';
import 'package:xta/speech/offline_voice_catalog.dart';
import 'package:xta/speech/voice_download_store.dart';
import 'package:xta/speech/voice_engine.dart';
import 'package:xta/speech/voice_engine_store.dart';
import 'package:xta/utils/native_locale_names.dart';

/// "Downloaded voices" in the read-aloud settings: the catalog, what is on
/// the device, the engine that runs the voices where it is downloaded, and the
/// switch that lets reading aloud use them.
class OfflineVoicesSection extends StatelessWidget {
  final VoiceDownloadStore store;

  const OfflineVoicesSection({super.key, required this.store});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final engine = store.engine;
    // What is already on the device, read once: local files only.
    unawaited(store.ensureLoaded());
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 4),
          child: Text(l10n.tts_offline_voices, style: Theme.of(context).textTheme.labelLarge),
        ),
        LabeledPref(
          child: PrefSwitch(
            pref: optionTtsOfflineVoice,
            title: Text(l10n.tts_offline_voices_use),
            subtitle: Text(l10n.tts_offline_voices_use_description),
          ),
        ),
        ScopedBuilder<VoiceEngineStore, VoiceInstall>(
          store: engine,
          onState: (context, install) => ScopedBuilder<VoiceDownloadStore, Map<String, VoiceInstall>>(
            store: store,
            onState: (context, _) => Column(
              children: [
                if (!engine.bundled) VoiceEngineTile(engine: engine, install: install),
                for (final voice in store.catalog)
                  OfflineVoiceTile(voice: voice, install: store.installOf(voice.id), store: store),
              ],
            ),
          ),
        ),
        _note(context, l10n.tts_offline_voices_note),
        if (!engine.bundled) _note(context, l10n.tts_offline_engine_note),
      ],
    );
  }

  Widget _note(BuildContext context, String text) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 4, 16, 8),
      child: Text(text, style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
    );
  }
}

/// One downloadable voice with what can be done with it right now.
class OfflineVoiceTile extends StatelessWidget {
  final OfflineVoice voice;
  final VoiceInstall install;
  final VoiceDownloadStore store;

  const OfflineVoiceTile({super.key, required this.voice, required this.install, required this.store});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final engine = store.engine;
    return OfflineDownloadTile(
      title: voice.name,
      details: [
        nativeLocaleNameOf(voice.locale) ?? voice.locale,
        offlineBytes(context, voice.archiveBytes),
        l10n.tts_offline_voice_licence(voice.licence),
      ].join(' · '),
      install: install,
      expectedBytes: voice.archiveBytes,
      note: install is VoiceReady && !engine.usable ? l10n.tts_offline_voice_needs_engine : null,
      archiveFailure: l10n.tts_offline_voice_failed_archive,
      deleteQuestion: l10n.tts_offline_voice_delete_question(voice.name),
      deleteDescription: l10n.tts_offline_voice_delete_description,
      onDownload: engine.available ? () => store.download(voice) : null,
      onCancel: () => store.cancel(voice.id),
      onDelete: () => store.delete(voice),
    );
  }
}

/// The engine that runs the voices, where it is downloaded rather than in the
/// app (docs/specs/downloadable-voice-engine.md).
class VoiceEngineTile extends StatelessWidget {
  final VoiceEngineStore engine;
  final VoiceInstall install;

  const VoiceEngineTile({super.key, required this.engine, required this.install});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final package = engine.package;
    return OfflineDownloadTile(
      title: l10n.tts_offline_engine,
      details: [
        'sherpa-onnx $voiceEngineVersion',
        if (package != null) offlineBytes(context, package.archiveBytes),
        l10n.tts_offline_voice_licence(voiceEngineLicences),
      ].join(' · '),
      install: install,
      expectedBytes: package?.archiveBytes ?? 0,
      note: engine.available ? null : l10n.tts_offline_engine_unavailable,
      archiveFailure: l10n.tts_offline_engine_failed_archive,
      deleteQuestion: l10n.tts_offline_engine_delete_question,
      deleteDescription: l10n.tts_offline_engine_delete_description,
      onDownload: engine.available ? () => engine.download() : null,
      onCancel: engine.cancel,
      onDelete: engine.delete,
    );
  }
}

/// A download for reading aloud, a voice or the engine, with its details, where
/// it stands and what can be done with it right now.
class OfflineDownloadTile extends StatelessWidget {
  final String title;
  final String details;
  final VoiceInstall install;

  /// The download's size, for progress while the server has not said.
  final int expectedBytes;

  /// A line under where it stands, when it cannot be used as it is.
  final String? note;

  final String archiveFailure;
  final String deleteQuestion;
  final String deleteDescription;

  /// Null when it cannot be downloaded on this device.
  final VoidCallback? onDownload;
  final VoidCallback onCancel;
  final Future<void> Function() onDelete;

  const OfflineDownloadTile({
    super.key,
    required this.title,
    required this.details,
    required this.install,
    required this.expectedBytes,
    required this.archiveFailure,
    required this.deleteQuestion,
    required this.deleteDescription,
    required this.onDownload,
    required this.onCancel,
    required this.onDelete,
    this.note,
  });

  @override
  Widget build(BuildContext context) {
    final status = _status(context);
    final note = this.note;
    final progress = switch (install) {
      VoiceDownloading(:final fraction) => fraction,
      _ => null,
    };
    return ListTile(
      title: Text(title),
      subtitle: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(details),
          if (status != null) Text(status),
          if (note != null) Text(note),
          if (install is VoiceDownloading)
            Padding(
              padding: const EdgeInsets.only(top: 6),
              child: LinearProgressIndicator(value: progress),
            ),
        ],
      ),
      trailing: _action(context),
    );
  }

  String? _status(BuildContext context) {
    final l10n = L10n.of(context);
    return switch (install) {
      VoiceAbsent() => null,
      VoiceDownloading(:final received, :final total) => l10n.downloads_progress(
        offlineBytes(context, received),
        offlineBytes(context, total ?? expectedBytes),
      ),
      VoiceInstalling() => l10n.tts_offline_voice_installing,
      VoiceReady(:final bytes) => l10n.tts_offline_voice_ready(offlineBytes(context, bytes)),
      VoiceFailed(reason: VoiceFailure.network) => l10n.tts_offline_voice_failed_network,
      VoiceFailed(reason: VoiceFailure.checksum) => l10n.tts_offline_voice_failed_checksum,
      VoiceFailed(reason: VoiceFailure.archive) => archiveFailure,
    };
  }

  Widget _action(BuildContext context) {
    final l10n = L10n.of(context);
    return switch (install) {
      VoiceAbsent() => IconButton(
        tooltip: l10n.download,
        icon: const Icon(Icons.download_outlined),
        onPressed: onDownload,
      ),
      VoiceFailed() => IconButton(tooltip: l10n.retry, icon: const Icon(Icons.refresh), onPressed: onDownload),
      VoiceDownloading() => IconButton(tooltip: l10n.cancel, icon: const Icon(Icons.close), onPressed: onCancel),
      VoiceInstalling() => const SizedBox.square(
        dimension: 48,
        child: Center(child: SizedBox.square(dimension: 24, child: CircularProgressIndicator(strokeWidth: 2))),
      ),
      VoiceReady() => IconButton(
        tooltip: l10n.delete,
        icon: const Icon(Icons.delete_outline),
        onPressed: () => _confirmDelete(context),
      ),
    };
  }

  Future<void> _confirmDelete(BuildContext context) async {
    final l10n = L10n.of(context);
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(deleteQuestion),
        content: Text(deleteDescription),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: Text(l10n.cancel)),
          TextButton(onPressed: () => Navigator.pop(context, true), child: Text(l10n.delete)),
        ],
      ),
    );
    if (confirmed == true) await onDelete();
  }
}
