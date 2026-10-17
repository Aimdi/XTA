import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/substack/podcast_store.dart';
import 'package:xta/utils/number_locale.dart';

/// The reader screen's controls for a podcast post's episode.
///
/// The playback itself lives in [PodcastStore] and its media session, so
/// leaving the article — or the app — changes nothing about what you hear;
/// this row is just the episode's face while its page is open.
class SubstackAudioPlayer extends StatelessWidget {
  final String url;
  final String title;

  const SubstackAudioPlayer({super.key, required this.url, required this.title});

  String _clock(Duration d) {
    final minutes = d.inMinutes.remainder(60).toString().padLeft(2, '0');
    final seconds = d.inSeconds.remainder(60).toString().padLeft(2, '0');
    return d.inHours > 0 ? '${d.inHours}:$minutes:$seconds' : '$minutes:$seconds';
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final store = context.read<PodcastStore>();

    return Material(
      color: theme.colorScheme.surfaceContainerHighest,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
        child: ScopedBuilder<PodcastStore, PodcastPlayback>(
          store: store,
          onState: (context, playback) {
            final thisEpisode = playback.url == url;
            final playing = thisEpisode && playback.playing;
            final loading = thisEpisode && playback.loading;
            final failed = thisEpisode && playback.failed;
            final hasLength = thisEpisode && playback.duration > Duration.zero;

            return Row(
              children: [
                IconButton(
                  tooltip: failed ? L10n.of(context).retry : title,
                  icon: loading
                      ? const SizedBox(width: 28, height: 28, child: CircularProgressIndicator(strokeWidth: 2))
                      : Icon(
                          failed
                              ? Icons.refresh
                              : playing
                              ? Icons.pause_circle
                              : Icons.play_circle,
                          size: 34,
                        ),
                  color: theme.colorScheme.primary,
                  onPressed: loading ? null : () => store.toggle(url: url, title: title),
                ),
                const SizedBox(width: 4),
                Expanded(
                  child: hasLength
                      ? Slider(
                          value: playback.position.inMilliseconds.clamp(0, playback.duration.inMilliseconds).toDouble(),
                          max: playback.duration.inMilliseconds.toDouble(),
                          onChanged: loading ? null : (value) => store.seek(Duration(milliseconds: value.round())),
                        )
                      : Text(
                          failed ? L10n.of(context).plugin_substack_load_error : title,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: theme.textTheme.bodySmall,
                        ),
                ),
                if (hasLength)
                  Padding(
                    padding: const EdgeInsets.only(right: 8),
                    child: Text(
                      '${_clock(playback.position)} / ${_clock(playback.duration)}',
                      style: theme.textTheme.bodySmall,
                    ),
                  ),
                _SpeedMenu(speed: playback.speed, onSelected: store.setSpeed),
              ],
            );
          },
        ),
      ),
    );
  }
}

/// Picks the playback speed, shown as its own label ("1.5×").
class _SpeedMenu extends StatelessWidget {
  final double speed;
  final ValueChanged<double> onSelected;

  const _SpeedMenu({required this.speed, required this.onSelected});

  @override
  Widget build(BuildContext context) {
    final format = NumberFormat.decimalPattern(numberFormatLocale(context));
    String label(double value) => '${format.format(value)}×';
    return PopupMenuButton<double>(
      tooltip: L10n.of(context).playback_speed,
      initialValue: speed,
      onSelected: onSelected,
      itemBuilder: (context) => [
        for (final value in podcastSpeeds)
          CheckedPopupMenuItem(value: value, checked: value == speed, child: Text(label(value))),
      ],
      child: SizedBox(
        height: 48,
        child: Center(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 8),
            child: Text(label(speed), style: Theme.of(context).textTheme.labelLarge),
          ),
        ),
      ),
    );
  }
}
