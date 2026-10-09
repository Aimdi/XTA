import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/offline/offline_ui.dart';
import 'package:xta/speech/offline_voice_catalog.dart';
import 'package:xta/speech/voice_download_store.dart';
import 'package:xta/utils/native_locale_names.dart';

/// "Downloaded voices" in the read-aloud settings: the catalog, what is on
/// the device, and the switch that lets reading aloud use them.
class OfflineVoicesSection extends StatelessWidget {
  final VoiceDownloadStore store;

  const OfflineVoicesSection({super.key, required this.store});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 4),
          child: Text(l10n.tts_offline_voices, style: theme.textTheme.labelLarge),
        ),
        PrefSwitch(
          pref: optionTtsOfflineVoice,
          title: Text(l10n.tts_offline_voices_use),
          subtitle: Text(l10n.tts_offline_voices_use_description),
        ),
        ScopedBuilder<VoiceDownloadStore, Map<String, VoiceInstall>>(
          store: store,
          onState: (context, _) => Column(
            children: [
              for (final voice in store.catalog)
                OfflineVoiceTile(voice: voice, install: store.installOf(voice.id), store: store),
            ],
          ),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 4, 16, 8),
          child: Text(
            l10n.tts_offline_voices_note,
            style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
          ),
        ),
      ],
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
    final status = _status(context);
    final progress = switch (install) {
      VoiceDownloading(:final fraction) => fraction,
      _ => null,
    };
    return ListTile(
      title: Text(voice.name),
      subtitle: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(_details(context)),
          if (status != null) Text(status),
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

  String _details(BuildContext context) => [
    nativeLocaleNameOf(voice.locale) ?? voice.locale,
    offlineBytes(context, voice.archiveBytes),
    L10n.of(context).tts_offline_voice_licence(voice.licence),
  ].join(' · ');

  String? _status(BuildContext context) {
    final l10n = L10n.of(context);
    return switch (install) {
      VoiceAbsent() => null,
      VoiceDownloading(:final received, :final total) => l10n.downloads_progress(
        offlineBytes(context, received),
        offlineBytes(context, total ?? voice.archiveBytes),
      ),
      VoiceInstalling() => l10n.tts_offline_voice_installing,
      VoiceReady(:final bytes) => l10n.tts_offline_voice_ready(offlineBytes(context, bytes)),
      VoiceFailed(reason: VoiceFailure.network) => l10n.tts_offline_voice_failed_network,
      VoiceFailed(reason: VoiceFailure.checksum) => l10n.tts_offline_voice_failed_checksum,
      VoiceFailed(reason: VoiceFailure.archive) => l10n.tts_offline_voice_failed_archive,
    };
  }

  Widget _action(BuildContext context) {
    final l10n = L10n.of(context);
    return switch (install) {
      VoiceAbsent() => IconButton(
        tooltip: l10n.download,
        icon: const Icon(Icons.download_outlined),
        onPressed: () => store.download(voice),
      ),
      VoiceFailed() => IconButton(
        tooltip: l10n.retry,
        icon: const Icon(Icons.refresh),
        onPressed: () => store.download(voice),
      ),
      VoiceDownloading() => IconButton(
        tooltip: l10n.cancel,
        icon: const Icon(Icons.close),
        onPressed: () => store.cancel(voice.id),
      ),
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
        title: Text(l10n.tts_offline_voice_delete_question(voice.name)),
        content: Text(l10n.tts_offline_voice_delete_description),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: Text(l10n.cancel)),
          TextButton(onPressed: () => Navigator.pop(context, true), child: Text(l10n.delete)),
        ],
      ),
    );
    if (confirmed == true) await store.delete(voice);
  }
}
