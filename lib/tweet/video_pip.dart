import 'dart:io';

import 'package:flutter/services.dart';

/// Shrinking the app into a floating window while a video keeps playing.
///
/// Android only, and only from Oreo onwards — the platform side answers false
/// rather than throwing on anything older, on a device that refuses it, and on
/// an install where the reader has switched picture-in-picture off for this app.
class VideoPictureInPicture {
  static const _channel = MethodChannel('browser_resolver');

  /// Whether the button is worth showing at all.
  static bool get isSupported => Platform.isAndroid;

  /// Asks Android to enter picture-in-picture, shaped like the video.
  ///
  /// Returns whether it happened, so a caller can say nothing rather than
  /// leaving the reader looking at a button that did not appear to do anything.
  static Future<bool> enter({required double aspectRatio}) async {
    if (!isSupported) {
      return false;
    }

    try {
      final entered = await _channel.invokeMethod<bool>(
        'enterPictureInPicture',
        {'aspectRatio': aspectRatio.isFinite && aspectRatio > 0 ? aspectRatio : 16 / 9},
      );
      return entered ?? false;
    } on PlatformException {
      return false;
    } on MissingPluginException {
      return false;
    }
  }
}
