import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/generated/l10n.dart';

class _RevealedStore extends Store<bool> {
  _RevealedStore() : super(false);
  void toggle() => update(!state);
}

/// Keep image widgets outside the tree until the reader explicitly reveals them.
class BlueskyContentWarning extends StatefulWidget {
  final Widget child;
  const BlueskyContentWarning({super.key, required this.child});
  @override
  State<BlueskyContentWarning> createState() => _BlueskyContentWarningState();
}

class _BlueskyContentWarningState extends State<BlueskyContentWarning> {
  final _store = _RevealedStore();
  @override
  void dispose() {
    _store.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => ScopedBuilder<_RevealedStore, bool>(
    store: _store,
    onState: (context, revealed) {
      final l10n = L10n.of(context);
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          OutlinedButton.icon(
            key: const ValueKey('bluesky-content-warning'),
            onPressed: _store.toggle,
            icon: Icon(revealed ? Icons.visibility_off_outlined : Icons.visibility_outlined),
            label: Text('${l10n.content_warning} · ${revealed ? l10n.hide : l10n.show}'),
          ),
          if (revealed) widget.child,
        ],
      );
    },
  );
}
