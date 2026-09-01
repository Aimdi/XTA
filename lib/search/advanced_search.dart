import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/search/search_chrome.dart';

List<String> _tokens(String input) =>
    input.split(RegExp(r'[,\s]+')).where((e) => e.isNotEmpty).toList();

String _orGroup(Iterable<String> items) {
  final list = items.toList();
  return list.length == 1 ? list.first : '(${list.join(' OR ')})';
}

void _addPrefixedGroup(
  List<String> parts,
  String input,
  String Function(String) toOperator,
) {
  final items = _tokens(input).map(toOperator).toList();
  if (items.isNotEmpty) {
    parts.add(_orGroup(items));
  }
}

void _addMinimum(List<String> parts, String input, String operator) {
  final n = int.tryParse(input.trim());
  if (n != null && n > 0) {
    parts.add('$operator:$n');
  }
}

/// Composes an X search query from the advanced-search form fields, using the
/// same operators as x.com/search-advanced.
String buildAdvancedSearchQuery({
  required String allWords,
  required String exactPhrase,
  required String anyWords,
  required String noneWords,
  required String hashtags,
  required String fromAccounts,
  required String toAccounts,
  required String mentioningAccounts,
  required String minReplies,
  required String minLikes,
  required String minRetweets,
  DateTime? since,
  DateTime? until,
  required bool onlyMedia,
}) {
  final parts = <String>[];

  if (allWords.trim().isNotEmpty) parts.add(allWords.trim());
  if (exactPhrase.trim().isNotEmpty) parts.add('"${exactPhrase.trim()}"');
  final any = _tokens(anyWords);
  if (any.isNotEmpty) parts.add(_orGroup(any));
  parts.addAll(_tokens(noneWords).map((w) => '-$w'));
  _addPrefixedGroup(parts, hashtags, (t) => t.startsWith('#') ? t : '#$t');
  _addPrefixedGroup(
    parts,
    fromAccounts,
    (u) => 'from:${u.replaceAll('@', '')}',
  );
  _addPrefixedGroup(parts, toAccounts, (u) => 'to:${u.replaceAll('@', '')}');
  _addPrefixedGroup(
    parts,
    mentioningAccounts,
    (u) => '@${u.replaceAll('@', '')}',
  );
  _addMinimum(parts, minReplies, 'min_replies');
  _addMinimum(parts, minLikes, 'min_faves');
  _addMinimum(parts, minRetweets, 'min_retweets');

  final dateFormat = DateFormat('yyyy-MM-dd');
  if (since != null) parts.add('since:${dateFormat.format(since)}');
  if (until != null) parts.add('until:${dateFormat.format(until)}');
  if (onlyMedia) parts.add('filter:media');

  return parts.join(' ');
}

/// Full-screen form that builds an advanced search query. Pops with the
/// composed query string, or null when dismissed.
class AdvancedSearchScreen extends StatefulWidget {
  const AdvancedSearchScreen({super.key});

  @override
  State<AdvancedSearchScreen> createState() => _AdvancedSearchScreenState();
}

class _AdvancedSearchScreenState extends State<AdvancedSearchScreen> {
  final _allWords = TextEditingController();
  final _exactPhrase = TextEditingController();
  final _anyWords = TextEditingController();
  final _noneWords = TextEditingController();
  final _hashtags = TextEditingController();
  final _fromAccounts = TextEditingController();
  final _toAccounts = TextEditingController();
  final _mentioningAccounts = TextEditingController();
  final _minReplies = TextEditingController();
  final _minLikes = TextEditingController();
  final _minRetweets = TextEditingController();
  DateTime? _since;
  DateTime? _until;
  bool _onlyMedia = false;

  @override
  void dispose() {
    for (final controller in [
      _allWords,
      _exactPhrase,
      _anyWords,
      _noneWords,
      _hashtags,
      _fromAccounts,
      _toAccounts,
      _mentioningAccounts,
      _minReplies,
      _minLikes,
      _minRetweets,
    ]) {
      controller.dispose();
    }
    super.dispose();
  }

  void _apply() {
    Navigator.pop(context, _query());
  }

  String _query() => buildAdvancedSearchQuery(
    allWords: _allWords.text,
    exactPhrase: _exactPhrase.text,
    anyWords: _anyWords.text,
    noneWords: _noneWords.text,
    hashtags: _hashtags.text,
    fromAccounts: _fromAccounts.text,
    toAccounts: _toAccounts.text,
    mentioningAccounts: _mentioningAccounts.text,
    minReplies: _minReplies.text,
    minLikes: _minLikes.text,
    minRetweets: _minRetweets.text,
    since: _since,
    until: _until,
    onlyMedia: _onlyMedia,
  );

  void _reset() {
    for (final controller in [
      _allWords,
      _exactPhrase,
      _anyWords,
      _noneWords,
      _hashtags,
      _fromAccounts,
      _toAccounts,
      _mentioningAccounts,
      _minReplies,
      _minLikes,
      _minRetweets,
    ]) {
      controller.clear();
    }
    setState(() {
      _since = null;
      _until = null;
      _onlyMedia = false;
    });
  }

  Widget _field(
    TextEditingController controller,
    String label, {
    bool number = false,
  }) {
    return AdvancedSearchField(
      controller: controller,
      label: label,
      number: number,
      onChanged: (_) => setState(() {}),
      onClear: () => setState(controller.clear),
    );
  }

  Widget _dateTile(
    String label,
    DateTime? value,
    void Function(DateTime?) onChanged,
  ) {
    return ListTile(
      title: Text(label),
      subtitle: Text(
        value == null ? '—' : DateFormat('yyyy-MM-dd').format(value),
      ),
      trailing: value == null
          ? const Icon(Icons.calendar_today)
          : IconButton(
              icon: const Icon(Icons.clear),
              onPressed: () => setState(() => onChanged(null)),
            ),
      onTap: () async {
        final picked = await showDatePicker(
          context: context,
          initialDate: value ?? DateTime.now(),
          firstDate: DateTime(2006),
          lastDate: DateTime.now(),
        );
        if (picked != null) {
          setState(() => onChanged(picked));
        }
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return SearchSystemBars(
      child: Scaffold(
        appBar: AppBar(
          title: Text(l10n.advanced_search),
          surfaceTintColor: Colors.transparent,
          scrolledUnderElevation: 0,
          actions: [
            IconButton(
              tooltip: l10n.delete,
              icon: const Icon(Icons.restart_alt),
              onPressed: _reset,
            ),
            IconButton(
              tooltip: l10n.search,
              icon: const Icon(Icons.check),
              onPressed: _apply,
            ),
          ],
        ),
        body: ListView(
          keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
          children: [
            AdvancedFilterSection(
              title: l10n.search_term,
              children: [
                _field(_allWords, l10n.all_of_these_words),
                _field(_exactPhrase, l10n.this_exact_phrase),
                _field(_anyWords, l10n.any_of_these_words),
                _field(_noneWords, l10n.none_of_these_words),
                _field(_hashtags, l10n.these_hashtags),
              ],
            ),
            AdvancedFilterSection(
              title: l10n.account,
              children: [
                _field(_fromAccounts, l10n.from_these_accounts),
                _field(_toAccounts, l10n.to_these_accounts),
                _field(_mentioningAccounts, l10n.mentioning_these_accounts),
              ],
            ),
            AdvancedFilterSection(
              title: l10n.filters,
              children: [
                _field(_minReplies, l10n.minimum_replies, number: true),
                _field(_minLikes, l10n.minimum_likes, number: true),
                _field(_minRetweets, l10n.minimum_reposts, number: true),
                CheckboxListTile(
                  title: Text(l10n.only_show_posts_with_media),
                  value: _onlyMedia,
                  onChanged: (v) => setState(() => _onlyMedia = v ?? false),
                ),
                _dateTile(l10n.since_date, _since, (v) => _since = v),
                _dateTile(l10n.until_date, _until, (v) => _until = v),
              ],
            ),
            SearchQueryPreview(query: _query()),
            const SizedBox(height: 24),
          ],
        ),
      ),
    );
  }
}
