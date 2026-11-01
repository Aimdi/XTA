import 'dart:io';

import 'package:xta/downloads/download_entry.dart';
import 'package:xta/downloads/download_transfer.dart';
import 'package:xta/speech/voice_archive.dart';

/// Where a download for reading aloud (a voice, or the engine that runs voices) stands on this device.
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
  double? get fraction => total == null || total == 0 ? null : (received / total!).clamp(0.0, 1.0).toDouble();
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

/// Downloads the file at [entry]'s URL to a staging file and hands it to
/// [save]; the shape of [DownloadTransfer.call].
typedef VoiceTransfer =
    Future<String?> Function(
      DownloadEntry entry,
      DownloadCancellation cancellation,
      DownloadProgress progress,
      DownloadPhase phase,
    );

/// Downloads [entry] and installs it with [install], reporting each step to [report], and answers where it ended:
/// ready, absent when cancelled, or failed. What a voice and the voice engine share.
///
/// The staged download is removed whatever happens, so a mismatched file is never resumed. [discard] removes what
/// [install] put in place when the download was cancelled while it ran.
Future<VoiceInstall> downloadArchive({
  required VoiceTransfer Function(SaveStagedDownload save) transfer,
  required DownloadEntry entry,
  required int expectedBytes,
  required DownloadCancellation cancellation,
  required Future<VoiceReady?> Function(String archive) install,
  required Future<void> Function() discard,
  required void Function(VoiceInstall install) report,
}) async {
  VoiceReady? ready;
  Future<String?> save(DownloadEntry entry, File file, DownloadCancellation cancellation) async {
    report(const VoiceInstalling());
    try {
      ready = await install(file.path);
    } finally {
      if (await file.exists()) await file.delete();
    }
    if (cancellation.cancelled) {
      await discard();
      throw const DownloadCancelled();
    }
    return ready?.path;
  }

  try {
    await transfer(save)(entry, cancellation, _wholePercents(report, expectedBytes, cancellation), (_) {});
    return ready ?? const VoiceFailed(VoiceFailure.archive);
  } on DownloadCancelled {
    return const VoiceAbsent();
  } on VoiceChecksumMismatch {
    return const VoiceFailed(VoiceFailure.checksum);
  } on VoiceArchiveInvalid {
    return const VoiceFailed(VoiceFailure.archive);
  } catch (_) {
    return cancellation.cancelled ? const VoiceAbsent() : const VoiceFailed(VoiceFailure.network);
  }
}

/// Progress, but only when the whole percent changes: a 100 MB voice would otherwise rebuild the settings list
/// thousands of times.
DownloadProgress _wholePercents(
  void Function(VoiceInstall install) report,
  int expectedBytes,
  DownloadCancellation cancellation,
) {
  var shown = -1;
  return (received, total) {
    final percent = total == null || total == 0 ? received >> 20 : received * 100 ~/ total;
    if (percent == shown || cancellation.cancelled) return;
    shown = percent;
    report(VoiceDownloading(received, total ?? expectedBytes));
  };
}
