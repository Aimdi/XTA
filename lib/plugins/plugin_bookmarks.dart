import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/saved/saved_screen.dart';
import 'package:xta/saved/saved_source_filter.dart';
import 'package:xta/saved/saved_tweet_model.dart';

Future<void> openPluginBookmarks(BuildContext context, SavedSource source) => Navigator.push<void>(context,
  MaterialPageRoute(builder: (_) => _PluginBookmarks(source: source)));

class _PluginBookmarks extends StatelessWidget {
  final SavedSource source;
  const _PluginBookmarks({required this.source});
  @override
  Widget build(BuildContext context) {
    final model = context.read<SavedTweetModel>();
    final l10n = L10n.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(l10n.saved)),
      body: ScopedBuilder<SavedTweetModel, List<SavedTweet>>(store: model, onState: (context, state) {
        final rows = state.where((row) => matchesSavedSource(model.contentOf(row.id), source)).toList();
        return ListView.builder(itemCount: rows.length + 1, itemBuilder: (context, index) {
          if (index == 0) return Padding(padding: const EdgeInsets.all(16), child: Column(children: [
            Text(l10n.saves_stay_on_device_notice),
            if (rows.isEmpty) Padding(padding: const EdgeInsets.only(top: 24), child: Text(l10n.no_results)),
          ]));
          final row = rows[index - 1];
          return SavedClipTile(key: ValueKey(row.id), saved: row, onNoteChanged: (note) => model.setNote(row.id, note));
        });
      }),
    );
  }
}
