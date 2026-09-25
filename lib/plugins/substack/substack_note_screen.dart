import 'package:flutter/material.dart';
import 'package:share_plus/share_plus.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/substack/substack_discussion_text.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/plugins/substack/substack_note_content.dart';
import 'package:xta/utils/urls.dart';

class SubstackNoteScreen extends StatelessWidget {
  final SubstackNote note;
  const SubstackNoteScreen({super.key, required this.note});
  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final url = substackDiscussionUrl(note.url);
    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.plugin_substack_tab_notes),
        actions: [
          if (url != null) ...[
            IconButton(
              tooltip: l10n.share_link,
              icon: const Icon(Icons.share_outlined),
              onPressed: () => SharePlus.instance.share(ShareParams(text: url)),
            ),
            IconButton(
              tooltip: l10n.open_in_browser,
              icon: const Icon(Icons.open_in_new),
              onPressed: () => openUri(context, url),
            ),
            IconButton(
              tooltip: MaterialLocalizations.of(context).moreButtonTooltip,
              icon: const Icon(Icons.more_horiz),
              onPressed: () => showSubstackNoteActions(context, note),
            ),
          ],
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(20, 16, 20, 32),
        children: [SubstackNoteContent(note: note, detail: true)],
      ),
    );
  }
}
