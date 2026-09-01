import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/speech/speech_store.dart';

/// Puts a "reading aloud" bar under the whole app while something is playing.
///
/// Under the app rather than inside the reader: the point of playback outliving
/// the article is that you can be somewhere else, and a control you can only
/// reach by going back to the article you left is no control at all.
///
/// The bar takes its own room instead of floating, so it covers no tab bar; the
/// system inset moves onto the bar with it, or the screen above would reserve
/// space for a gap that is no longer at the bottom.
class SpeechBarScaffold extends StatelessWidget {
  final Widget child;

  const SpeechBarScaffold({super.key, required this.child});

  @override
  Widget build(BuildContext context) {
    return ScopedBuilder<SpeechStore, SpeechPlayback>(
      store: context.read<SpeechStore>(),
      onState: (context, playback) {
        if (!playback.speaking) {
          return child;
        }

        return Column(
          children: [
            Expanded(
              child: MediaQuery.removePadding(context: context, removeBottom: true, child: child),
            ),
            _SpeechBar(playback: playback),
          ],
        );
      },
    );
  }
}

class _SpeechBar extends StatelessWidget {
  final SpeechPlayback playback;

  const _SpeechBar({required this.playback});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = L10n.of(context);
    final stop = context.read<SpeechStore>().stop;

    return Material(
      color: theme.colorScheme.surfaceContainerHighest,
      child: SafeArea(
        top: false,
        child: InkWell(
          onTap: stop,
          child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 6, 8, 6),
            child: Row(
              children: [
                Icon(Icons.record_voice_over, size: 20, color: theme.colorScheme.primary),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(
                        l10n.speech_playing,
                        style: theme.textTheme.labelSmall!.copyWith(color: theme.colorScheme.primary),
                      ),
                      if (playback.title != null)
                        Text(
                          playback.title!,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: theme.textTheme.bodyMedium,
                        ),
                    ],
                  ),
                ),
                IconButton(
                  tooltip: l10n.speech_stop,
                  icon: const Icon(Icons.stop_circle_outlined),
                  onPressed: stop,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
