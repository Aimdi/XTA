import 'dart:io';

import 'package:flutter/foundation.dart';

/// The desktop build (Linux; see docs/desktop.md) has no platform web view and
/// none of Android's services: no share intents, no MediaStore or document
/// trees, no intents naming another app. Every screen built on one of those
/// checks this and takes the desktop route instead.
bool get isDesktop => debugDesktopOverride ?? (!_underFlutterTest && _desktopPlatform);

/// `flutter test` runs on a desktop host, but the suite exercises the Android
/// paths, mocking their platform channels; a test of a desktop path sets
/// [debugDesktopOverride].
final bool _underFlutterTest = Platform.environment.containsKey('FLUTTER_TEST');

final bool _desktopPlatform = Platform.isLinux || Platform.isWindows || Platform.isMacOS;

@visibleForTesting
bool? debugDesktopOverride;
