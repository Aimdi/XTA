import 'dart:developer';

import 'package:audio_service/audio_service.dart';
import 'package:flutter/foundation.dart';

/// What the current audio session can do. Read-aloud offers only stop; a
/// podcast offers the lot.
typedef AudioSessionBinding = ({
  VoidCallback? onPlay,
  VoidCallback? onPause,
  VoidCallback onStop,
  void Function(Duration position)? onSeek,
});

/// The one media session behind everything XTA plays without a screen.
///
/// This is what keeps read-aloud and a podcast episode alive when the app
/// leaves the foreground: the platform's media service holds the process, and
/// the notification and lockscreen carry the controls. Whoever is playing
/// binds itself here; the callbacks fan the system's buttons back out to it.
/// One session at a time — a new binding simply replaces the last, the same
/// way starting one thing while another plays replaces what you are hearing.
class XtaAudioHandler extends BaseAudioHandler {
  AudioSessionBinding? _binding;

  void bindSession({required String title, String? artist, required AudioSessionBinding binding}) {
    _binding = binding;
    mediaItem.add(MediaItem(id: title, title: title, artist: artist));
  }

  void updateSession({required bool playing, Duration position = Duration.zero, Duration? duration}) {
    final binding = _binding;
    if (binding == null) {
      return;
    }
    final item = mediaItem.value;
    if (duration != null && item != null && item.duration != duration) {
      mediaItem.add(item.copyWith(duration: duration));
    }
    playbackState.add(playbackState.value.copyWith(
      controls: [
        if (binding.onPlay != null && binding.onPause != null)
          playing ? MediaControl.pause : MediaControl.play,
        MediaControl.stop,
      ],
      systemActions: {if (binding.onSeek != null) MediaAction.seek},
      processingState: AudioProcessingState.ready,
      playing: playing,
      updatePosition: position,
    ));
  }

  void clearSession() {
    _binding = null;
    mediaItem.add(null);
    playbackState.add(PlaybackState(processingState: AudioProcessingState.idle, playing: false));
  }

  @override
  Future<void> play() async => _binding?.onPlay?.call();

  @override
  Future<void> pause() async => _binding?.onPause?.call();

  @override
  Future<void> stop() async {
    _binding?.onStop.call();
    clearSession();
    await super.stop();
  }

  @override
  Future<void> seek(Duration position) async => _binding?.onSeek?.call(position);
}

/// Null where the platform has no media service (tests, desktop dev runs) —
/// every caller treats the handler as optional and audio simply plays without
/// a session, exactly as it did before the service existed.
XtaAudioHandler? audioHandler;

Future<void> initXtaAudio() async {
  try {
    audioHandler = await AudioService.init(
      builder: () => XtaAudioHandler(),
      config: const AudioServiceConfig(
        androidNotificationChannelId: 'com.aimdi.xta.playback',
        // The channel's name in Android's own settings; the app's name is the
        // one word that needs no translation.
        androidNotificationChannelName: 'XTA',
        androidStopForegroundOnPause: true,
      ),
    );
  } catch (e) {
    log('Audio service unavailable, playback stays foreground-only: $e');
  }
}
