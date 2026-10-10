import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_ugoira.dart';

/// An ugoira's still [poster] with a play button; tapping it plays the animation in place.
class PixivUgoiraView extends StatefulWidget {
  final PixivIllust illust;
  final Widget poster;
  final PixivFrameDecoder? decode;

  const PixivUgoiraView({super.key, required this.illust, required this.poster, this.decode});

  @override
  State<PixivUgoiraView> createState() => _PixivUgoiraViewState();
}

class _PixivUgoiraViewState extends State<PixivUgoiraView> {
  late final PixivUgoiraStore _store;

  @override
  void initState() {
    super.initState();
    final client = context.read<PixivClient>();
    _store = PixivUgoiraStore(
      metadata: () => client.ugoiraMetadata(widget.illust.id),
      archive: client.ugoiraArchive,
      decode: widget.decode,
    );
  }

  /// A route pushed over this one or a page scrolled off stops ticking; the animation follows.
  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (TickerMode.valuesOf(context).enabled) {
      _store.resume();
    } else {
      _store.hold();
    }
  }

  @override
  void dispose() {
    _store.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => TripleBuilder<PixivUgoiraStore, PixivUgoiraState>(
    store: _store,
    builder: (context, triple) {
      final frame = triple.state.frame;
      return Stack(
        fit: StackFit.expand,
        children: [
          if (frame == null) widget.poster else RawImage(image: frame, fit: BoxFit.contain),
          _control(context, triple.state.phase, failed: triple.error != null),
        ],
      );
    },
  );

  Widget _control(BuildContext context, PixivUgoiraPhase phase, {required bool failed}) {
    final l10n = L10n.of(context);
    return switch (phase) {
      PixivUgoiraPhase.loading => const Center(
        child: _Scrim(
          child: Padding(
            padding: EdgeInsets.all(14),
            child: CircularProgressIndicator(strokeWidth: 3, color: Colors.white),
          ),
        ),
      ),
      PixivUgoiraPhase.playing => PositionedDirectional(
        end: 8,
        bottom: 8,
        child: _button('pause', Icons.pause, l10n.plugin_pixiv_ugoira_pause, _store.pause),
      ),
      _ => Center(
        child: _button(
          failed ? 'retry' : 'play',
          failed ? Icons.refresh : Icons.play_arrow,
          failed ? l10n.retry : l10n.plugin_pixiv_ugoira_play,
          _store.play,
          size: 32,
        ),
      ),
    };
  }

  Widget _button(String name, IconData icon, String tooltip, VoidCallback onPressed, {double size = 24}) => IconButton(
    key: ValueKey('pixiv-ugoira-$name'),
    tooltip: tooltip,
    onPressed: onPressed,
    iconSize: size,
    style: IconButton.styleFrom(
      backgroundColor: Colors.black.withValues(alpha: 0.55),
      foregroundColor: Colors.white,
      padding: EdgeInsets.all(size / 2),
    ),
    icon: Icon(icon),
  );
}

class _Scrim extends StatelessWidget {
  final Widget child;

  const _Scrim({required this.child});

  @override
  Widget build(BuildContext context) => DecoratedBox(
    decoration: BoxDecoration(color: Colors.black.withValues(alpha: 0.55), shape: BoxShape.circle),
    child: SizedBox.square(dimension: 64, child: child),
  );
}
