import 'package:flutter/material.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/plugins/substack/substack_note_screen.dart';
import 'package:xta/plugins/substack/substack_reader_screen.dart';
import 'package:xta/reading/reading_history_entry.dart';

ReadingHistoryEntry substackHistoryEntry(SubstackPost post) => ReadingHistoryEntry(
  source: pluginIdSubstack,
  kind: ReadingHistoryKind.article,
  nativeId: '${post.publicationBaseUrl}\n${post.id}',
  url: post.canonicalUrl,
  author: post.authorName ?? post.publicationName,
  title: post.title,
  text: post.excerpt ?? '',
  extra: {'id': post.id, 'slug': post.slug, 'base': post.publicationBaseUrl, 'publication': post.publicationName},
);

ReadingHistoryEntry substackNoteHistoryEntry(SubstackNote note) => ReadingHistoryEntry(
  source: pluginIdSubstack,
  kind: ReadingHistoryKind.post,
  nativeId: 'note:${note.id}',
  url: note.url,
  author: note.authorName ?? note.authorHandle ?? '',
  text: note.body,
  extra: {'note': note.id, 'handle': ?note.authorHandle},
);

Future<void> openSubstackHistoryEntry(BuildContext context, ReadingHistoryEntry entry) => Navigator.push(
  context,
  MaterialPageRoute<void>(
    builder: (_) => entry.extra.containsKey('note')
        ? SubstackNoteScreen(
            note: SubstackNote(
              id: entry.extra['note']!,
              body: entry.text,
              authorName: entry.author,
              authorHandle: entry.extra['handle'],
              url: entry.url,
            ),
          )
        : SubstackReaderScreen(
            post: SubstackPost(
              id: entry.extra['id'] ?? '',
              title: entry.title,
              slug: entry.extra['slug'] ?? '',
              publicationBaseUrl: entry.extra['base'] ?? '',
              publicationName: entry.extra['publication'] ?? entry.author,
              canonicalUrl: entry.url,
              authorName: entry.author,
            ),
          ),
  ),
);
