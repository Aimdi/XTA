import 'dart:async';
import 'dart:isolate';
import 'dart:typed_data';

import 'package:flutter/widgets.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:image/image.dart' as img;
import 'package:provider/provider.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_ugoira.dart';

enum PixivUgoiraFormat {
  gif('.gif'),
  zip('.zip');

  final String extension;

  const PixivUgoiraFormat(this.extension);
}

/// GIF delays count hundredths of a second, and most viewers speed up anything under two.
int pixivGifCentiseconds(Duration delay) => (delay.inMilliseconds / 10).round().clamp(2, 65535);

/// A looping GIF of [frames], each frame up for its own delay; [onFrame] hears
/// how many frames are done.
Uint8List encodePixivGif(List<PixivFrameBytes> frames, {void Function(int done)? onFrame}) {
  final encoder = img.GifEncoder(repeat: 0);
  for (final (index, frame) in frames.indexed) {
    encoder.addFrame(_decodeFrame(frame.bytes), duration: pixivGifCentiseconds(frame.delay));
    onFrame?.call(index + 1);
  }
  final bytes = encoder.finish();
  if (bytes == null) throw const FormatException('No ugoira frames');
  return bytes;
}

img.Image _decodeFrame(Uint8List bytes) {
  try {
    if (img.decodeImage(bytes) case final image?) return image;
  } catch (_) {
    // The decoders throw range errors on truncated data; it is the same broken frame.
  }
  throw const FormatException('Unreadable ugoira frame');
}

class PixivExportCancelled implements Exception {
  const PixivExportCancelled();
}

/// Encodes GIFs on a background isolate, so a long animation never stalls the
/// screen and Cancel stops the work itself.
class PixivGifEncoder {
  const PixivGifEncoder();

  static PixivGifEncoder of(BuildContext context) => context.read<PixivGifEncoder?>() ?? const PixivGifEncoder();

  Future<Uint8List> encode(
    List<PixivFrameBytes> frames, {
    required void Function(int done) onFrame,
    required Future<void> cancelled,
  }) async {
    final port = ReceivePort();
    final result = Completer<Uint8List>();
    port.listen((message) => _receive(message, result, onFrame));
    try {
      final isolate = await Isolate.spawn(
        _encodeInBackground,
        (port.sendPort, frames),
        onError: port.sendPort,
        onExit: port.sendPort,
      );
      unawaited(
        cancelled.then((_) {
          isolate.kill(priority: Isolate.immediate);
          if (!result.isCompleted) result.completeError(const PixivExportCancelled());
        }),
      );
      return await result.future;
    } finally {
      port.close();
    }
  }

  static void _receive(Object? message, Completer<Uint8List> result, void Function(int done) onFrame) {
    if (result.isCompleted) return;
    if (message is int) {
      onFrame(message);
    } else if (message is TransferableTypedData) {
      result.complete(message.materialize().asUint8List());
    } else {
      // An uncaught error arrives as [error, stack]; an exit without a result as null.
      result.completeError(StateError('GIF encoding stopped: $message'));
    }
  }
}

void _encodeInBackground((SendPort, List<PixivFrameBytes>) job) {
  final (port, frames) = job;
  final bytes = encodePixivGif(frames, onFrame: port.send);
  Isolate.exit(port, TransferableTypedData.fromList([bytes]));
}

/// What an export needs: the archive as Pixiv sent it and its frames in order.
class PixivUgoiraSource {
  final Uint8List archive;
  final List<PixivFrameBytes> frames;

  const PixivUgoiraSource({required this.archive, required this.frames});
}

Future<PixivUgoiraSource> loadPixivUgoiraSource(PixivClient client, int illustId) async {
  final meta = await client.ugoiraMetadata(illustId);
  final archive = await client.ugoiraArchive(meta.zipUrl);
  return PixivUgoiraSource(archive: archive, frames: pixivUgoiraFrames(meta, archive));
}

enum PixivExportPhase { fetching, encoding, saving, saved, failed, cancelled }

class PixivExportProgress {
  final PixivExportPhase phase;
  final int frame;
  final int frames;

  const PixivExportProgress(this.phase, {this.frame = 0, this.frames = 0});

  bool get finished => switch (phase) {
    PixivExportPhase.saved || PixivExportPhase.failed || PixivExportPhase.cancelled => true,
    _ => false,
  };
}

typedef PixivGifEncode =
    Future<Uint8List> Function(
      List<PixivFrameBytes> frames, {
      required void Function(int done) onFrame,
      required Future<void> cancelled,
    });

/// One ugoira export: fetch, encode when a GIF is asked for, then save.
class PixivUgoiraExportStore extends Store<PixivExportProgress> {
  final Future<PixivUgoiraSource> Function() load;
  final PixivGifEncode encodeGif;
  final Future<bool> Function(Uint8List bytes) save;
  final _cancel = Completer<void>();

  PixivUgoiraExportStore({required this.load, required this.encodeGif, required this.save})
    : super(const PixivExportProgress(PixivExportPhase.fetching));

  bool get _cancelled => _cancel.isCompleted;

  /// True once the file is saved.
  Future<bool> run(PixivUgoiraFormat format) async {
    try {
      // Cancel ends the wait at once; a late response is then ignored.
      final source = await Future.any([
        load(),
        _cancel.future.then<PixivUgoiraSource>((_) => throw const PixivExportCancelled()),
      ]);
      if (_cancelled) throw const PixivExportCancelled();
      final bytes = format == PixivUgoiraFormat.zip ? source.archive : await _encode(source.frames);
      if (_cancelled) throw const PixivExportCancelled();
      update(const PixivExportProgress(PixivExportPhase.saving));
      final saved = await save(bytes);
      update(PixivExportProgress(saved ? PixivExportPhase.saved : PixivExportPhase.failed));
      return saved;
    } catch (_) {
      update(PixivExportProgress(_cancelled ? PixivExportPhase.cancelled : PixivExportPhase.failed));
      return false;
    }
  }

  Future<Uint8List> _encode(List<PixivFrameBytes> frames) {
    update(PixivExportProgress(PixivExportPhase.encoding, frames: frames.length));
    return encodeGif(
      frames,
      cancelled: _cancel.future,
      onFrame: (done) {
        if (!_cancelled) update(PixivExportProgress(PixivExportPhase.encoding, frame: done, frames: frames.length));
      },
    );
  }

  /// Stops before the file is written; once saving started it runs to the end.
  void cancel() {
    if (_cancelled || state.finished || state.phase == PixivExportPhase.saving) return;
    _cancel.complete();
    update(const PixivExportProgress(PixivExportPhase.cancelled));
  }
}
