import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:share_plus/share_plus.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/substack/substack_highlights.dart';
import 'package:xta/plugins/substack/substack_models.dart';

/// Lists [post]'s highlights: each can be removed, and all shared as Markdown.
Future<void> showSubstackHighlights(
  BuildContext context,
  SubstackHighlightsStore highlights,
  SubstackPost post, {
  Future<void> Function(String markdown)? share,
}) => showModalBottomSheet<void>(
  context: context,
  showDragHandle: true,
  isScrollControlled: true,
  builder: (context) => ScopedBuilder<SubstackHighlightsStore, Map<String, List<String>>>(
    store: highlights,
    onState: (context, _) => _HighlightsList(
      quotes: highlights.of(post),
      onRemove: (quote) => highlights.remove(post, quote),
      onShare: () => (share ?? _share)(substackHighlightsMarkdown(post, highlights.of(post))),
    ),
  ),
);

Future<void> _share(String markdown) => SharePlus.instance.share(ShareParams(text: markdown));

class _HighlightsList extends StatelessWidget {
  final List<String> quotes;
  final ValueChanged<String> onRemove;
  final VoidCallback onShare;

  const _HighlightsList({required this.quotes, required this.onRemove, required this.onShare});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    return SafeArea(
      child: ConstrainedBox(
        constraints: BoxConstraints(maxHeight: MediaQuery.sizeOf(context).height * 0.75),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 0, 20, 8),
              child: Text(l10n.plugin_substack_highlights, style: theme.textTheme.titleLarge),
            ),
            if (quotes.isEmpty)
              Padding(padding: const EdgeInsets.all(20), child: Text(l10n.plugin_substack_highlights_empty))
            else
              Flexible(
                child: ListView(
                  shrinkWrap: true,
                  children: [
                    for (final quote in quotes)
                      ListTile(
                        title: Text(quote, style: const TextStyle(fontStyle: FontStyle.italic)),
                        trailing: IconButton(
                          tooltip: l10n.plugin_substack_highlight_remove,
                          icon: const Icon(Icons.delete_outline),
                          onPressed: () => onRemove(quote),
                        ),
                      ),
                  ],
                ),
              ),
            if (quotes.isNotEmpty)
              Padding(
                padding: const EdgeInsets.fromLTRB(20, 8, 20, 16),
                child: FilledButton.icon(
                  onPressed: onShare,
                  icon: const Icon(Icons.ios_share),
                  label: Text(l10n.plugin_substack_highlights_share),
                ),
              ),
          ],
        ),
      ),
    );
  }
}
