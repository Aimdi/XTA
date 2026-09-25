import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/substack/substack_article_navigation.dart';
import 'package:xta/plugins/substack/substack_reader_store.dart';

Future<String?> showSubstackArticleNavigation(
  BuildContext context,
  SubstackArticleDocument document, {
  bool find = false,
}) => showModalBottomSheet<String>(
  context: context,
  isScrollControlled: true,
  useSafeArea: true,
  builder: (_) => FractionallySizedBox(
    heightFactor: 0.88,
    child: SubstackArticleNavigationSheet(document: document, autofocus: find),
  ),
);

class SubstackArticleNavigationSheet extends StatefulWidget {
  final SubstackArticleDocument document;
  final bool autofocus;

  const SubstackArticleNavigationSheet({super.key, required this.document, this.autofocus = false});

  @override
  State<SubstackArticleNavigationSheet> createState() => _SubstackArticleNavigationSheetState();
}

class _SubstackArticleNavigationSheetState extends State<SubstackArticleNavigationSheet> {
  late final _store = SubstackArticleNavigationStore(widget.document);
  final _query = TextEditingController();

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(context).bottom),
      child: Column(
        children: [
          Padding(
            padding: const EdgeInsetsDirectional.fromSTEB(20, 12, 4, 4),
            child: Row(
              children: [
                Expanded(child: Text(l10n.article_reader_contents, style: Theme.of(context).textTheme.titleLarge)),
                IconButton(tooltip: l10n.close, onPressed: () => Navigator.pop(context), icon: const Icon(Icons.close)),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(l10n.article_reader_find),
                const SizedBox(height: 8),
                TextField(
                  controller: _query,
                  autofocus: widget.autofocus,
                  maxLength: 200,
                  textInputAction: TextInputAction.search,
                  onChanged: _store.search,
                  decoration: InputDecoration(
                    hintText: l10n.search,
                    counterText: '',
                    prefixIcon: const Icon(Icons.search),
                    suffixIcon: IconButton(
                      tooltip: l10n.plugin_mastodon_clear_search,
                      icon: const Icon(Icons.clear),
                      onPressed: () {
                        _query.clear();
                        _store.search('');
                      },
                    ),
                    border: const OutlineInputBorder(),
                  ),
                ),
              ],
            ),
          ),
          Expanded(
            child: ScopedBuilder<SubstackArticleNavigationStore, String>(
              store: _store,
              onState: (context, query) {
                final results = _store.results;
                final searching = query.trim().isNotEmpty;
                if (results.isEmpty) {
                  return Center(
                    child: Padding(
                      padding: const EdgeInsets.all(24),
                      child: Text(searching ? l10n.article_reader_no_matches : l10n.article_reader_no_headings),
                    ),
                  );
                }
                return ListView.separated(
                  keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
                  itemCount: results.length,
                  separatorBuilder: (_, _) => const Divider(height: 1),
                  itemBuilder: (context, index) {
                    final passage = results[index];
                    return ListTile(
                      key: ValueKey(passage.anchor),
                      contentPadding: EdgeInsetsDirectional.fromSTEB(
                        20 + (searching ? 0 : (passage.headingLevel - 1).clamp(0, 3) * 12),
                        8,
                        16,
                        8,
                      ),
                      title: Text(searching ? passage.excerpt(query) : passage.text),
                      trailing: const Icon(Icons.chevron_right),
                      onTap: () => Navigator.pop(context, passage.anchor),
                    );
                  },
                );
              },
            ),
          ),
        ],
      ),
    );
  }

  @override
  void dispose() {
    _query.dispose();
    _store.destroy();
    super.dispose();
  }
}
