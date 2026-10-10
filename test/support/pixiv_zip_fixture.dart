import 'dart:convert';
import 'dart:io' show ZLibEncoder;
import 'dart:typed_data';

List<int> _u16(int value) => [value & 0xff, value >> 8 & 0xff];
List<int> _u32(int value) => [..._u16(value & 0xffff), ..._u16(value >> 16)];

/// A minimal ZIP archive, stored or deflated, as Pixiv serves ugoira frames.
Uint8List pixivZipFixture(Map<String, List<int>> files, {bool deflate = false}) {
  final out = BytesBuilder();
  final directory = BytesBuilder();
  for (final MapEntry(key: file, value: content) in files.entries) {
    final name = utf8.encode(file);
    final data = deflate ? ZLibEncoder(raw: true).convert(content) : content;
    final method = deflate ? 8 : 0;
    final sizes = [..._u32(0), ..._u32(data.length), ..._u32(content.length)];
    directory.add([..._u32(0x02014b50), ..._u16(20), ..._u16(20), ..._u16(0), ..._u16(method), ..._u32(0)]);
    directory.add([...sizes, ..._u16(name.length), ..._u16(0), ..._u16(0), ..._u16(0), ..._u16(0), ..._u32(0)]);
    directory.add([..._u32(out.length), ...name]);
    out.add([..._u32(0x04034b50), ..._u16(20), ..._u16(0), ..._u16(method), ..._u32(0), ...sizes]);
    out.add([..._u16(name.length), ..._u16(0), ...name, ...data]);
  }
  final start = out.length;
  final central = directory.takeBytes();
  out.add(central);
  out.add([..._u32(0x06054b50), ..._u16(0), ..._u16(0), ..._u16(files.length), ..._u16(files.length)]);
  out.add([..._u32(central.length), ..._u32(start), ..._u16(0)]);
  return out.takeBytes();
}
