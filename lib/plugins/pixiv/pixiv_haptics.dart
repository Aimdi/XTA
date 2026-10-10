import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';

/// How firmly an action answers: light when a bookmark or follow lands,
/// medium when a long press opens a sheet.
enum PixivHaptic { light, medium }

/// The shortest gap between two buzzes; a burst of taps then reads as taps
/// rather than one long rattle.
const pixivHapticGap = Duration(milliseconds: 100);

/// Whether a buzz at [now] is far enough from the [last] one.
bool pixivHapticDue(DateTime? last, DateTime now) => last == null || now.difference(last) >= pixivHapticGap;

Future<void> _platformHaptic(PixivHaptic kind) => switch (kind) {
  PixivHaptic.light => HapticFeedback.lightImpact(),
  PixivHaptic.medium => HapticFeedback.mediumImpact(),
};

final _shared = PixivHaptics();

/// Touch feedback for Pixiv actions behind the plugin's own switch. Android
/// still drops it when the system's touch feedback is off.
class PixivHaptics {
  final DateTime Function() _clock;
  final Future<void> Function(PixivHaptic kind) _play;
  DateTime? _last;

  PixivHaptics({DateTime Function()? clock, Future<void> Function(PixivHaptic kind)? play})
    : _clock = clock ?? DateTime.now,
      _play = play ?? _platformHaptic;

  /// The app's one instance, so every button shares the spacing, unless a
  /// test provides its own.
  static PixivHaptics of(BuildContext context) => context.read<PixivHaptics?>() ?? _shared;

  /// Buzzes [kind] when the reader left haptics on and the last buzz was
  /// long enough ago.
  void play(BasePrefService prefs, PixivHaptic kind) {
    if (prefs.get<bool>(optionPluginPixivHaptics) != true) return;
    final now = _clock();
    if (!pixivHapticDue(_last, now)) return;
    _last = now;
    unawaited(_play(kind));
  }
}

/// Plays [kind] for an action taken in [context]; a screen mounted without
/// preferences, as some tests mount one, stays silent.
void playPixivHaptic(BuildContext context, PixivHaptic kind) {
  final prefs = context.findAncestorWidgetOfExactType<PrefService>()?.service;
  if (prefs != null) PixivHaptics.of(context).play(prefs, kind);
}
