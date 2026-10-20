import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/booru/booru_client.dart';
import 'package:xta/plugins/booru/booru_load_store.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_post_lookups.dart';
import 'package:xta/plugins/plugin_view_store.dart';

/// [post]'s translation notes over its picture, which fills this widget the
/// way `BoxFit.contain` fits it. A chip says how many there are and shows
/// or hides them; a tapped note shows its text. Nothing when there are none.
class BooruNotesOverlay extends StatefulWidget {
  final BooruPost post;

  const BooruNotesOverlay({super.key, required this.post});

  @override
  State<BooruNotesOverlay> createState() => _BooruNotesOverlayState();
}

class _BooruNotesOverlayState extends State<BooruNotesOverlay> {
  late final BooruLoadStore<List<BooruNote>> _notes;
  final _shown = PluginViewStore<bool>(false);

  @override
  void initState() {
    super.initState();
    final client = context.read<BooruClient>();
    final lookups = context.read<BooruPostLookups?>();
    _notes = BooruLoadStore(() => booruPostLookup(lookups, 'notes', widget.post, () => client.notes(widget.post)));
    unawaited(_notes.ensure());
  }

  @override
  void dispose() {
    unawaited(_notes.destroy());
    unawaited(_shown.destroy());
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => ScopedBuilder<BooruLoadStore<List<BooruNote>>, List<BooruNote>?>(
    store: _notes,
    onState: (context, notes) => notes == null || notes.isEmpty || widget.post.width <= 0 || widget.post.height <= 0
        ? const SizedBox.shrink()
        : ScopedBuilder<PluginViewStore<bool>, bool>(
            store: _shown,
            onState: (context, shown) => LayoutBuilder(
              builder: (context, constraints) => Stack(
                children: [
                  if (shown) ..._boxes(notes, constraints.biggest),
                  Positioned(top: 8, right: 8, child: _toggle(context, notes.length, shown)),
                ],
              ),
            ),
          ),
  );

  Widget _toggle(BuildContext context, int count, bool shown) => FilterChip(
    avatar: const Icon(Icons.translate, size: 18),
    label: Text(L10n.of(context).plugin_booru_notes(count)),
    selected: shown,
    showCheckmark: false,
    materialTapTargetSize: MaterialTapTargetSize.padded,
    onSelected: _shown.select,
  );

  /// Each note's box, scaled and placed where the picture is drawn.
  List<Widget> _boxes(List<BooruNote> notes, Size area) {
    final picture = Size(widget.post.width.toDouble(), widget.post.height.toDouble());
    final drawn = applyBoxFit(BoxFit.contain, picture, area).destination;
    final origin = Alignment.center.inscribe(drawn, Offset.zero & area).topLeft;
    final scale = drawn.width / picture.width;
    return [
      for (final note in notes)
        Positioned(
          left: origin.dx + note.x * scale,
          top: origin.dy + note.y * scale,
          width: note.width * scale,
          height: note.height * scale,
          child: Tooltip(
            message: note.text,
            triggerMode: TooltipTriggerMode.tap,
            showDuration: const Duration(seconds: 6),
            child: DecoratedBox(
              decoration: BoxDecoration(
                color: Colors.yellow.withValues(alpha: 0.18),
                border: Border.all(color: Colors.black54),
              ),
            ),
          ),
        ),
    ];
  }
}
