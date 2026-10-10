import 'dart:io';
import 'dart:typed_data';

import 'package:file_picker/file_picker.dart';
import 'package:flutter_file_dialog/flutter_file_dialog.dart';
import 'package:xta/utils/desktop.dart';

/// Saves [data], or the file at [sourceFilePath], where the reader chooses:
/// Android's document picker, or the desktop portal's file chooser. Returns
/// where it went, or null when the reader backed out.
Future<String?> saveWithDialog({
  required String fileName,
  Uint8List? data,
  String? sourceFilePath,
  List<String>? mimeTypes,
}) async {
  if (!isDesktop) {
    return FlutterFileDialog.saveFile(
      params: SaveFileDialogParams(
        fileName: fileName,
        data: data,
        sourceFilePath: sourceFilePath,
        mimeTypesFilter: mimeTypes,
      ),
    );
  }

  // The portal writes the bytes it is given; a staged download is copied over
  // them instead of being read into memory whole.
  final path = await FilePicker.saveFile(fileName: fileName, bytes: data ?? Uint8List(0));
  if (path != null && data == null && sourceFilePath != null) {
    await File(sourceFilePath).copy(path);
  }
  return path;
}

/// The path of a file the reader picks, or null when they backed out.
Future<String?> pickFileWithDialog() async {
  if (!isDesktop) {
    return FlutterFileDialog.pickFile(params: const OpenFileDialogParams());
  }
  return (await FilePicker.pickFile())?.path;
}
