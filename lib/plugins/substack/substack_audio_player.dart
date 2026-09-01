import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/plugins/substack/podcast_store.dart';

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
            final hasLength = thisEpisode && playback.duration > Duration.zero;

            return Row(
              children: [
                IconButton(
                  icon: Icon(playing ? Icons.pause_circle : Icons.play_circle, size: 34),
                  color: theme.colorScheme.primary,
                  onPressed: () => store.toggle(url: url, title: title),
                ),
                const SizedBox(width: 4),
                Expanded(
                  child: hasLength
                      ? Slider(
                          value: playback.position.inMilliseconds
                              .clamp(0, playback.duration.inMilliseconds)
                              .toDouble(),
                          max: playback.duration.inMilliseconds.toDouble(),
                          onChanged: (value) => store.seek(Duration(milliseconds: value.round())),
                        )
                      : Text(
                          title,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: theme.textTheme.bodySmall,
                        ),
                ),
                if (hasLength)
                  Padding(
                    padding: const EdgeInsets.only(right: 8),
                    child: Text('${_clock(playback.position)} / ${_clock(playback.duration)}',
                        style: theme.textTheme.bodySmall),
                  ),
              ],
            );
          },
        ),
      ),
    );
  }
}
