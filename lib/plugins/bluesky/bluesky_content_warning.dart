import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';

bool blueskyContentUnavailable(BlueskyPost post) =>
    post.labels.contains('!hide') || post.labels.contains('!no-unauthenticated');

bool blueskyWarnsContent(BlueskyPost post) => blueskyContentUnavailable(post) || post.labels.contains('!warn');

Object blueskyWarningIdentity(BlueskyPost post) =>
    (post.uri, post.cid, post.labels.join('\u0000'), post.text, post.images.join('\u0000'), post.quotedPost?.cid);

class _RevealedStore extends Store<bool> {
  _RevealedStore() : super(false);
  void toggle() => update(!state);
  void hide() {
    if (state) update(false);
  }
}

/// Covers never mount protected content until the current revision is revealed.
class BlueskyContentWarning extends StatefulWidget {
  final Widget child;
  final Object? identity;
  final bool unavailable;
  const BlueskyContentWarning({super.key, required this.child, this.identity, this.unavailable = false});
  @override
  State<BlueskyContentWarning> createState() => _BlueskyContentWarningState();
}

class _BlueskyContentWarningState extends State<BlueskyContentWarning> {
  final _store = _RevealedStore();
  @override
  void didUpdateWidget(BlueskyContentWarning oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.identity != oldWidget.identity || widget.unavailable != oldWidget.unavailable) _store.hide();
  }

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
      if (widget.unavailable) {
        return Padding(
          padding: const EdgeInsets.symmetric(vertical: 12),
          child: Text(l10n.bluesky_content_unavailable),
        );
      }
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
