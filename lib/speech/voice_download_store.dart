import 'dart:async';
import 'dart:io';
import 'dart:isolate';

import 'package:flutter_triple/flutter_triple.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:xta/downloads/download_entry.dart';
import 'package:xta/downloads/download_transfer.dart';
import 'package:xta/speech/offline_voice_catalog.dart';
import 'package:xta/speech/voice_archive.dart';

/// Where one catalog voice stands on this device.
sealed class VoiceInstall {
  const VoiceInstall();
}

class VoiceAbsent extends VoiceInstall {
  const VoiceAbsent();
}

class VoiceDownloading extends VoiceInstall {
  final int received;
  final int? total;

  const VoiceDownloading(this.received, this.total);

  /// 0–1, or null while the size is unknown.
  double? get fraction => total == null || total == 0
      ? null
      : (received / total!).clamp(0.0, 1.0).toDouble();
}

/// Downloaded; being checked against its checksum and unpacked.
class VoiceInstalling extends VoiceInstall {
  const VoiceInstalling();
}

class VoiceReady extends VoiceInstall {
  final String path;
  final int bytes;

  const VoiceReady(this.path, this.bytes);
}

enum VoiceFailure { network, checksum, archive }

class VoiceFailed extends VoiceInstall {
  final VoiceFailure reason;

  const VoiceFailed(this.reason);
}

/// An installed voice and the folder it loads from.
typedef InstalledVoice = ({OfflineVoice voice, String path});

/// Downloads the file at [entry]'s URL to a staging file and hands it to
/// [save]; the shape of [DownloadTransfer.call].
typedef VoiceTransfer =
    Future<String?> Function(
      DownloadEntry entry,
      DownloadCancellation cancellation,
      DownloadProgress progress,
      DownloadPhase phase,
    );

/// Verifies and unpacks a downloaded archive into the voices folder.
typedef VoiceInstaller =
    Future<void> Function(String archive, OfflineVoice voice, String root);

Future<void> _installInBackground(
  String archive,
  OfflineVoice voice,
  String root,
) => Isolate.run(() => installVoiceArchive(archive, voice, root));

Future<Directory> _defaultRoot() async =>
    Directory(p.join((await getApplicationSupportDirectory()).path, 'voices'));

/// The downloadable voices and what state each is in, keyed by voice id.
///
/// Nothing touches the network until [download] — which only a tap on
/// Download calls. There are no background or automatic downloads.
class VoiceDownloadStore extends Store<Map<String, VoiceInstall>> {
  final List<OfflineVoice> catalog;
  final Future<Directory> Function() _root;
  final VoiceTransfer Function(SaveStagedDownload save) _transfer;
  final VoiceInstaller _install;
  final _cancellations = <String, DownloadCancellation>{};
  Future<void>? _loading;

  VoiceDownloadStore({
    List<OfflineVoice>? catalog,
    Future<Directory> Function()? root,
    VoiceTransfer Function(SaveStagedDownload save)? transfer,
    VoiceInstaller? install,
  }) : catalog = catalog ?? offlineVoiceCatalog,
       _root = root ?? _defaultRoot,
       _transfer = transfer ?? ((save) => DownloadTransfer(save: save).call),
       _install = install ?? _installInBackground,
       super(const {});

  VoiceInstall installOf(String id) => state[id] ?? const VoiceAbsent();

  /// Reads which voices are already on the device. Local files only.
  Future<void> ensureLoaded() => _loading ??= _scan();

  Future<void> _scan() async {
    final root = await _root();
    final found = <String, VoiceInstall>{};
    for (final voice in catalog) {
      final install = await _installedState(root.path, voice);
      if (install != null) found[voice.id] = install;
    }
    update({...found, ...state});
  }

  Future<VoiceReady?> _installedState(String root, OfflineVoice voice) async {
    final path = voiceDirectory(root, voice);
    if (!voiceFilesPresent(path, voice)) return null;
    return VoiceReady(path, directoryBytes(Directory(path)));
  }

  /// The installed voice that should read [language], if any.
  Future<InstalledVoice?> installedFor(String? language) async {
    await ensureLoaded();
    final ready = {
      for (final voice in catalog)
        if (installOf(voice.id) case VoiceReady(:final path)) voice: path,
    };
    final voice = pickOfflineVoice(ready.keys, language);
    return voice == null ? null : (voice: voice, path: ready[voice]!);
  }

  void _set(String id, VoiceInstall install) => update({...state, id: install});

  /// Downloads, verifies and installs [voice]. Only ever called from a tap.
  Future<void> download(OfflineVoice voice) async {
    if (_cancellations.containsKey(voice.id)) return;
    await ensureLoaded();
    final cancellation = _cancellations[voice.id] = DownloadCancellation();
    _set(voice.id, const VoiceDownloading(0, null));
    try {
      final root = await _root();
      await root.create(recursive: true);
      final run = _transfer(_saver(voice, root.path));
      await run(_entry(voice), cancellation, _progress(voice), (_) {});
      _set(
        voice.id,
        await _installedState(root.path, voice) ??
            const VoiceFailed(VoiceFailure.archive),
      );
    } on DownloadCancelled {
      _set(voice.id, const VoiceAbsent());
    } on VoiceChecksumMismatch {
      _set(voice.id, const VoiceFailed(VoiceFailure.checksum));
    } on VoiceArchiveInvalid {
      _set(voice.id, const VoiceFailed(VoiceFailure.archive));
    } catch (_) {
      _set(
        voice.id,
        cancellation.cancelled
            ? const VoiceAbsent()
            : const VoiceFailed(VoiceFailure.network),
      );
    } finally {
      _cancellations.remove(voice.id);
    }
  }

  DownloadEntry _entry(OfflineVoice voice) => DownloadEntry(
    id: 'voice-${voice.id}',
    uri: voice.url,
    fileName: '${voice.archiveRoot}.tar.bz2',
    createdAt: DateTime.now(),
  );

  /// Progress, but only when the whole percent changes: a 100 MB voice
  /// would otherwise rebuild the settings list thousands of times.
  DownloadProgress _progress(OfflineVoice voice) {
    var shown = -1;
    return (received, total) {
      final percent = total == null || total == 0
          ? received >> 20
          : received * 100 ~/ total;
      if (percent == shown || !_cancellations.containsKey(voice.id)) return;
      shown = percent;
      _set(voice.id, VoiceDownloading(received, total ?? voice.archiveBytes));
    };
  }

  /// Takes the downloaded archive from the transfer and installs it. The
  /// staged file is removed whatever happens, so a mismatched download is
  /// never resumed.
  SaveStagedDownload _saver(OfflineVoice voice, String root) =>
      (entry, file, cancellation) async {
        _set(voice.id, const VoiceInstalling());
        try {
          await _install(file.path, voice, root);
        } finally {
          if (await file.exists()) await file.delete();
        }
        if (cancellation.cancelled) {
          await _deleteFolder(voiceDirectory(root, voice));
          throw const DownloadCancelled();
        }
        return voiceDirectory(root, voice);
      };

  /// Stops a running download; whatever it fetched so far is thrown away.
  void cancel(String id) => _cancellations[id]?.cancel();

  Future<void> delete(OfflineVoice voice) async {
    cancel(voice.id);
    final root = await _root();
    await _deleteFolder(voiceDirectory(root.path, voice));
    _set(voice.id, const VoiceAbsent());
  }

  Future<void> _deleteFolder(String path) async {
    final directory = Directory(path);
    if (await directory.exists()) await directory.delete(recursive: true);
  }
}
