import 'package:flutter/foundation.dart';

/// Texts this long are read off the UI thread; shorter ones take less than a frame.
const backgroundParseLength = 20000;

/// [parse] of [input], in a background isolate once [input] is long enough to stall a frame.
Future<R> parseInBackground<R>(R Function(String input) parse, String input) =>
    input.length < backgroundParseLength ? Future.value(parse(input)) : compute(parse, input);
