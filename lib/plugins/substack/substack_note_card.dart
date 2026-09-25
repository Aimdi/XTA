import 'package:flutter/material.dart';
import 'package:xta/plugins/substack/substack_discussion_text.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/plugins/substack/substack_note_content.dart';
import 'package:xta/plugins/substack/substack_note_screen.dart';
import 'package:xta/tweet/tweet_chrome.dart';

/// Public Notes keep text, media and local publication actions in one reader.
class SubstackNoteCard extends StatelessWidget {
  final SubstackNote note;
  const SubstackNoteCard({super.key, required this.note});
  @override
  Widget build(BuildContext context) => RepaintBoundary(
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        tweetFlatCard(
          color: Theme.of(context).cardColor,
          child: InkWell(
            onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => SubstackNoteScreen(note: note))),
            onLongPress: substackDiscussionUrl(note.url) == null ? null : () => showSubstackNoteActions(context, note),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              child: SubstackNoteContent(note: note),
            ),
          ),
        ),
        tweetHairlineDivider(context),
      ],
    ),
  );
}
