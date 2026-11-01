import 'dart:ffi' show Abi;
import 'dart:io';
import 'dart:isolate';

import 'package:flutter_triple/flutter_triple.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:xta/downloads/download_entry.dart';
import 'package:xta/downloads/download_transfer.dart';
import 'package:xta/speech/voice_engine.dart';
import 'package:xta/speech/voice_engine_archive.dart';
import 'package:xta/speech/voice_install.dart';

/// Verifies and unpacks a downloaded engine archive under the engine folder.
typedef VoiceEngineInstaller = Future<void> Function(String archive, VoiceEnginePackage package, String root);

Future<void> _installInBackground(String archive, VoiceEnginePackage package, String root) =>
    Isolate.run(() => installVoiceEngineArchive(archive, package, root));

Future<Directory> _defaultRoot() async =>
    Directory(p.join((await getApplicationSupportDirectory()).path, 'voice-engine'));

/// The engine that runs downloaded voices, on this device (docs/specs/downloadable-voice-engine.md).
///
/// On Android it is downloaded, and nothing touches the network until [download], which only a tap calls: on the
/// engine's own row, or on a voice that needs it. The Linux build carries it ([bundled]).
class VoiceEngineStore extends Store<VoiceInstall> {
  /// What this device downloads; null when the engine came with the app, or none is published for its processor.
  final VoiceEnginePackage? package;

  /// The engine came with the app, so there is nothing to download.
  final bool bundled;

  final Future<Directory> Function() _root;
  final VoiceTransfer Function(SaveStagedDownload save) _transfer;
  final VoiceEngineInstaller _install;
  final List<Uri> Function(VoiceEnginePackage package) _urls;
  DownloadCancellation? _cancellation;
  Future<bool>? _downloading;
  Future<void>? _loading;

  VoiceEngineStore({
    this.package,
    this.bundled = false,
    Future<Directory> Function()? root,
    VoiceTransfer Function(SaveStagedDownload save)? transfer,
    VoiceEngineInstaller? install,
    List<Uri> Function(VoiceEnginePackage package)? urls,
  }) : _root = root ?? _defaultRoot,
       _transfer = transfer ?? ((save) => DownloadTransfer(save: save).call),
       _install = install ?? _installInBackground,
       _urls = urls ?? voiceEngineUrls,
       super(const VoiceAbsent());

  /// As the app runs it: downloaded on Android, carried by the app elsewhere.
  factory VoiceEngineStore.forThisDevice() => Platform.isAndroid
      ? VoiceEngineStore(package: voiceEngineForAbi(Abi.current()))
      : VoiceEngineStore(bundled: true);

  /// Whether this device can have the engine at all.
  bool get available => bundled || package != null;

  /// Whether a downloaded voice can read now.
  bool get usable => bundled || state is VoiceReady;

  /// The folder to load the engine from; null to load the one that came with the app.
  String? get loadFrom => switch (state) {
    VoiceReady(:final path) when !bundled => path,
    _ => null,
  };

  /// Reads whether the engine is already on the device. Local files only.
  Future<void> ensureLoaded() => _loading ??= _scan();

  Future<void> _scan() async {
    final package = this.package;
    if (bundled || package == null) return;
    final root = (await _root()).path;
    if (state is VoiceAbsent && voiceEngineFilesPresent(root, package)) {
      update(VoiceReady(voiceEngineDirectory(root, package), package.installedBytes));
    }
  }

  /// Downloads, checks and installs the engine, from each of its URLs in turn until one installs, and answers whether
  /// it is ready afterwards. A call while one runs waits for that one. Cancelling [cancellation] (a voice's) cancels
  /// this download too.
  Future<bool> download({DownloadCancellation? cancellation}) async {
    final package = this.package;
    if (bundled) return true;
    if (package == null) return false;
    await ensureLoaded();
    if (state is VoiceReady) return true;
    return _downloading ??= _download(package, cancellation).whenComplete(() => _downloading = null);
  }

  Future<bool> _download(VoiceEnginePackage package, DownloadCancellation? linked) async {
    final cancellation = _cancellation = DownloadCancellation();
    linked?.onCancel(cancellation.cancel);
    update(const VoiceDownloading(0, null));
    try {
      final root = await _root();
      await root.create(recursive: true);
      VoiceInstall outcome = const VoiceFailed(VoiceFailure.network);
      for (final url in _urls(package)) {
        outcome = await _downloadFrom(url, package, root.path, cancellation);
        if (outcome is VoiceReady || outcome is VoiceAbsent) break;
      }
      update(outcome);
      return outcome is VoiceReady;
    } finally {
      _cancellation = null;
    }
  }

  Future<VoiceInstall> _downloadFrom(
    Uri url,
    VoiceEnginePackage package,
    String root,
    DownloadCancellation cancellation,
  ) => downloadArchive(
    transfer: _transfer,
    entry: DownloadEntry(
      id: 'voice-engine-${package.abi}',
      uri: url,
      fileName: package.archiveName,
      createdAt: DateTime.now(),
    ),
    expectedBytes: package.archiveBytes,
    cancellation: cancellation,
    install: (archive) async {
      await _install(archive, package, root);
      return _installed(root, package);
    },
    discard: () => _deleteFolder(voiceEngineDirectory(root, package)),
    report: update,
  );

  VoiceReady? _installed(String root, VoiceEnginePackage package) => voiceEngineFilesPresent(root, package)
      ? VoiceReady(voiceEngineDirectory(root, package), package.installedBytes)
      : null;

  /// Stops a running download; whatever it fetched so far is thrown away.
  void cancel() => _cancellation?.cancel();

  Future<void> delete() async {
    final package = this.package;
    cancel();
    if (package == null) return;
    await _deleteFolder(voiceEngineDirectory((await _root()).path, package));
    update(const VoiceAbsent());
  }
}

Future<void> _deleteFolder(String path) async {
  final directory = Directory(path);
  if (await directory.exists()) await directory.delete(recursive: true);
}
