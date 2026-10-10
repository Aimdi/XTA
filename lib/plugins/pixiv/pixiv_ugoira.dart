import 'dart:async';
import 'dart:convert';
import 'dart:io' show ZLibDecoder;
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/utils/json.dart';

class PixivUgoiraFrame {
  final String file;
  final Duration delay;

  const PixivUgoiraFrame({required this.file, required this.delay});
}

/// An animated work: the frame archive and how long each frame stays up.
class PixivUgoira {
  final String zipUrl;
  final List<PixivUgoiraFrame> frames;

  const PixivUgoira({required this.zipUrl, required this.frames});
}

/// `/v1/ugoira/metadata` → [PixivUgoira], or null when it carries no playable frames.
PixivUgoira? parsePixivUgoira(Object? json) {
  final meta = Json(json)['ugoira_metadata'];
  final zip = meta['zip_urls']['medium'].string;
  final frames = [
    for (final frame in meta['frames'].list)
      if (frame['file'].string case final file? when file.isNotEmpty)
        PixivUgoiraFrame(
          file: file,
          delay: Duration(milliseconds: (frame['delay'].integer ?? 100).clamp(16, 10000)),
        ),
  ];
  return zip == null || zip.isEmpty || frames.isEmpty ? null : PixivUgoira(zipUrl: zip, frames: frames);
}

/// One frame's encoded image and how long it stays up.
typedef PixivFrameBytes = ({Uint8List bytes, Duration delay});

/// The frames of [archive] in [meta]'s order, each with its delay; frames the
/// archive lacks are skipped. Throws when none are left.
List<PixivFrameBytes> pixivUgoiraFrames(PixivUgoira meta, Uint8List archive) {
  final files = readPixivZip(archive);
  final frames = [
    for (final frame in meta.frames)
      if (files[frame.file] case final bytes?) (bytes: bytes, delay: frame.delay),
  ];
  if (frames.isEmpty) throw const FormatException('No ugoira frames');
  return frames;
}

const _centralEntry = 0x02014b50;
const _localEntry = 0x04034b50;
const _endOfDirectory = 0x06054b50;

/// The files of a ZIP archive by name. Pixiv packs ugoira frames stored or deflated.
Map<String, Uint8List> readPixivZip(Uint8List bytes) {
  final data = ByteData.sublistView(bytes);
  final end = _endOfDirectoryOffset(data);
  final count = data.getUint16(end + 10, Endian.little);
  var offset = data.getUint32(end + 16, Endian.little);
  final files = <String, Uint8List>{};
  for (var i = 0; i < count; i++) {
    if (data.getUint32(offset, Endian.little) != _centralEntry) throw const FormatException('Broken ZIP directory');
    final nameLength = data.getUint16(offset + 28, Endian.little);
    final skip = data.getUint16(offset + 30, Endian.little) + data.getUint16(offset + 32, Endian.little);
    final name = utf8.decode(Uint8List.sublistView(bytes, offset + 46, offset + 46 + nameLength));
    files[name] = _entryBytes(
      bytes,
      local: data.getUint32(offset + 42, Endian.little),
      method: data.getUint16(offset + 10, Endian.little),
      size: data.getUint32(offset + 20, Endian.little),
    );
    offset += 46 + nameLength + skip;
  }
  return files;
}

int _endOfDirectoryOffset(ByteData data) {
  for (var offset = data.lengthInBytes - 22; offset >= 0; offset--) {
    if (data.getUint32(offset, Endian.little) == _endOfDirectory) return offset;
  }
  throw const FormatException('Not a ZIP archive');
}

Uint8List _entryBytes(Uint8List bytes, {required int local, required int method, required int size}) {
  final data = ByteData.sublistView(bytes);
  if (data.getUint32(local, Endian.little) != _localEntry) throw const FormatException('Broken ZIP entry');
  final start = local + 30 + data.getUint16(local + 26, Endian.little) + data.getUint16(local + 28, Endian.little);
  final raw = Uint8List.sublistView(bytes, start, start + size);
  return switch (method) {
    0 => raw,
    8 => Uint8List.fromList(ZLibDecoder(raw: true).convert(raw)),
    _ => throw FormatException('Unsupported ZIP method $method'),
  };
}

enum PixivUgoiraPhase { idle, loading, playing, paused }

class PixivUgoiraState {
  final PixivUgoiraPhase phase;

  /// The frame on screen; null until playback first starts.
  final ui.Image? frame;

  const PixivUgoiraState({this.phase = PixivUgoiraPhase.idle, this.frame});
}

typedef PixivFrameDecoder = Future<ui.Image> Function(Uint8List bytes);

Future<ui.Image> decodePixivFrame(Uint8List bytes) async {
  final codec = await ui.instantiateImageCodec(bytes);
  try {
    return (await codec.getNextFrame()).image;
  } finally {
    codec.dispose();
  }
}

/// Plays an ugoira by decoding one frame ahead, so a long animation never sits in memory whole.
class PixivUgoiraStore extends Store<PixivUgoiraState> {
  final Future<PixivUgoira> Function() metadata;
  final Future<Uint8List> Function(String url) archive;
  final PixivFrameDecoder decode;
  List<PixivFrameBytes> _frames = const [];
  var _index = 0;
  var _run = 0;
  var _closed = false;

  /// Playback the view stopped because it was covered, not the reader's pause.
  var _held = false;

  PixivUgoiraStore({required this.metadata, required this.archive, PixivFrameDecoder? decode})
    : decode = decode ?? decodePixivFrame,
      super(const PixivUgoiraState());

  bool get playing => state.phase == PixivUgoiraPhase.playing;

  Future<void> play() async {
    if (_closed || playing || state.phase == PixivUgoiraPhase.loading) return;
    _held = false;
    if (_frames.isEmpty && !await _loadFrames()) return;
    if (_held) {
      update(PixivUgoiraState(phase: PixivUgoiraPhase.paused, frame: state.frame));
      return;
    }
    update(PixivUgoiraState(phase: PixivUgoiraPhase.playing, frame: state.frame));
    unawaited(_loop(++_run));
  }

  void pause() {
    _held = false;
    _stop();
  }

  void _stop() {
    if (!playing) return;
    _run++;
    update(PixivUgoiraState(phase: PixivUgoiraPhase.paused, frame: state.frame));
  }

  /// Stops while the view is covered, remembering to go on when [resume]d.
  void hold() {
    if (!playing && state.phase != PixivUgoiraPhase.loading) return;
    _held = true;
    _stop();
  }

  /// Plays again what [hold] stopped; a reader's own pause stays paused.
  void resume() {
    if (!_held) return;
    _held = false;
    unawaited(play());
  }

  Future<bool> _loadFrames() async {
    update(PixivUgoiraState(phase: PixivUgoiraPhase.loading, frame: state.frame));
    try {
      final meta = await metadata();
      _frames = pixivUgoiraFrames(meta, await archive(meta.zipUrl));
      return !_closed;
    } catch (error) {
      if (!_closed) {
        update(PixivUgoiraState(frame: state.frame));
        setError(error);
      }
      return false;
    }
  }

  Future<void> _loop(int run) async {
    bool current() => !_closed && run == _run;
    var next = await decode(_frames[_index].bytes);
    while (current()) {
      _show(next);
      final shownFor = _frames[_index].delay;
      final clock = Stopwatch()..start();
      _index = (_index + 1) % _frames.length;
      next = await decode(_frames[_index].bytes);
      final rest = shownFor - clock.elapsed;
      if (current() && rest > Duration.zero) await Future<void>.delayed(rest);
    }
    next.dispose();
  }

  void _show(ui.Image image) {
    final previous = state.frame;
    update(PixivUgoiraState(phase: state.phase, frame: image));
    previous?.dispose();
  }

  @override
  Future<void> destroy() {
    _closed = true;
    _run++;
    state.frame?.dispose();
    return super.destroy();
  }
}
