import 'package:xta/downloads/download_entry.dart';

/// Characters no file system accepts in a name.
final illegalFileNameCharacters = RegExp(r'[\x00-\x1f/\\:*?"<>|]');

final _token = RegExp(r'\{[a-z_]+\}');

/// [template] with each `{token}` replaced from [values] in one pass, so a
/// value that itself reads like a token stays as written. A token [values]
/// does not name stays too.
String fillNameTemplate(String template, Map<String, String> values) =>
    template.replaceAllMapped(_token, (match) => values[match[0]] ?? match[0]!);

/// [value] as the part of a file name before its extension: characters no
/// file system accepts become `_`, spaces fold, it fits Android's limit, and
/// dots and spaces at either end go. Empty when nothing is left.
String safeFileStem(String value) {
  final stem = value.replaceAll(illegalFileNameCharacters, '_').replaceAll(RegExp(r'\s+'), ' ').trim();
  return fitUtf8Bytes(stem, maxDownloadStemBytes).replaceAll(RegExp(r'^[. ]+|[. ]+$'), '');
}
