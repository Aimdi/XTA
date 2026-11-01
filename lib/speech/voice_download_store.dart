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
import 'package:xta/speech/voice_engine_store.dart';
import 'package:xta/speech/voice_install.dart';

export 'package:xta/speech/voice_install.dart';

/// An installed voice and the folder it loads from.
typedef InstalledVoice = ({OfflineVoice voice, String path});

/// Verifies and unpacks a downloaded archive into the voices folder.
typedef VoiceInstaller = Future<void> Function(String archive, OfflineVoice voice, String root);

Future<void> _installInBackground(String archive, OfflineVoice voice, String root) =>
    Isolate.run(() => installVoiceArchive(archive, voice, root));

Future<Directory> _defaultRoot() async => Directory(p.join((await getApplicationSupportDirectory()).path, 'voices'));

/// The downloadable voices and what state each is in, keyed by voice id.
///
/// Nothing touches the network until [download] — which only a tap on
/// Download calls. There are no background or automatic downloads. On
/// Android a voice needs the [engine] too, which its download fetches first
/// when it is not on the device.
class VoiceDownloadStore extends Store<Map<String, VoiceInstall>> {
  final List<OfflineVoice> catalog;
  final VoiceEngineStore engine;
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
    VoiceEngineStore? engine,
  }) : catalog = catalog ?? offlineVoiceCatalog,
       engine = engine ?? VoiceEngineStore.forThisDevice(),
       _root = root ?? _defaultRoot,
       _transfer = transfer ?? ((save) => DownloadTransfer(save: save).call),
       _install = install ?? _installInBackground,
       super(const {});

  VoiceInstall installOf(String id) => state[id] ?? const VoiceAbsent();

  /// Reads which voices, and whether the engine, are already on the device.
  /// Local files only.
  Future<void> ensureLoaded() => _loading ??= Future.wait([_scan(), engine.ensureLoaded()]);

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

  /// The installed voice that should read [language], if any. None reads
  /// while the engine it needs is not on the device.
  Future<InstalledVoice?> installedFor(String? language) async {
    await ensureLoaded();
    if (!engine.usable) return null;
    final ready = {
      for (final voice in catalog)
        if (installOf(voice.id) case VoiceReady(:final path)) voice: path,
    };
    final voice = pickOfflineVoice(ready.keys, language);
    return voice == null ? null : (voice: voice, path: ready[voice]!);
  }

  void _set(String id, VoiceInstall install) => update({...state, id: install});

  /// Downloads, verifies and installs [voice], and the engine first when it
  /// is needed and not on the device. Only ever called from a tap.
  Future<void> download(OfflineVoice voice) async {
    if (_cancellations.containsKey(voice.id)) return;
    await ensureLoaded();
    final cancellation = _cancellations[voice.id] = DownloadCancellation();
    _set(voice.id, const VoiceDownloading(0, null));
    try {
      if (!await engine.download(cancellation: cancellation)) {
        _set(voice.id, _withoutEngine());
        return;
      }
      final root = await _root();
      await root.create(recursive: true);
      _set(
        voice.id,
        await downloadArchive(
          transfer: _transfer,
          entry: _entry(voice),
          expectedBytes: voice.archiveBytes,
          cancellation: cancellation,
          install: (archive) async {
            await _install(archive, voice, root.path);
            return _installedState(root.path, voice);
          },
          discard: () => _deleteFolder(voiceDirectory(root.path, voice)),
          report: (install) => _set(voice.id, install),
        ),
      );
    } finally {
      _cancellations.remove(voice.id);
    }
  }

  /// Where a voice stands when the engine it needs could not be had: failed
  /// as the engine did, or not downloaded when that was cancelled.
  VoiceInstall _withoutEngine() => switch (engine.state) {
    VoiceFailed(:final reason) => VoiceFailed(reason),
    _ => const VoiceAbsent(),
  };

  DownloadEntry _entry(OfflineVoice voice) => DownloadEntry(
    id: 'voice-${voice.id}',
    uri: voice.url,
    fileName: '${voice.archiveRoot}.tar.bz2',
    createdAt: DateTime.now(),
  );

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
