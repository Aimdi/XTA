import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:intl/intl.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/search/advanced_search_model.dart';
import 'package:xta/search/search_chrome.dart';
import 'package:xta/tweet/tweet_chrome.dart';

/// Full-screen progressive form that returns the structured filters used to
/// compose an X search query. Dismissing it leaves the existing search intact.
class AdvancedSearchScreen extends StatefulWidget {
  final AdvancedSearchState initialState;

  const AdvancedSearchScreen({
    super.key,
    this.initialState = const AdvancedSearchState(),
  });

  @override
  State<AdvancedSearchScreen> createState() => _AdvancedSearchScreenState();
}

class _AdvancedSearchScreenState extends State<AdvancedSearchScreen> {
  late final AdvancedSearchStore _store;
  late final Map<AdvancedSearchFilter, TextEditingController> _controllers;

  @override
  void initState() {
    super.initState();
    _store = AdvancedSearchStore(widget.initialState);
    _controllers = {
      AdvancedSearchFilter.allWords: _controller(widget.initialState.allWords),
      AdvancedSearchFilter.exactPhrase: _controller(
        widget.initialState.exactPhrase,
      ),
      AdvancedSearchFilter.anyWords: _controller(widget.initialState.anyWords),
      AdvancedSearchFilter.noneWords: _controller(
        widget.initialState.noneWords,
      ),
      AdvancedSearchFilter.hashtags: _controller(widget.initialState.hashtags),
      AdvancedSearchFilter.fromAccounts: _controller(
        widget.initialState.fromAccounts,
      ),
      AdvancedSearchFilter.toAccounts: _controller(
        widget.initialState.toAccounts,
      ),
      AdvancedSearchFilter.mentioningAccounts: _controller(
        widget.initialState.mentioningAccounts,
      ),
      AdvancedSearchFilter.minReplies: _controller(
        widget.initialState.minReplies,
      ),
      AdvancedSearchFilter.minLikes: _controller(
        widget.initialState.minLikes,
      ),
      AdvancedSearchFilter.minRetweets: _controller(
        widget.initialState.minRetweets,
      ),
    };
  }

  TextEditingController _controller(String value) =>
      TextEditingController(text: value);

  @override
  void dispose() {
    for (final controller in _controllers.values) {
      controller.dispose();
    }
    _store.destroy();
    super.dispose();
  }

  TextEditingController _controllerFor(AdvancedSearchFilter filter) =>
      _controllers[filter]!;

  void _apply() => Navigator.pop(context, _store.state);

  void _reset() {
    for (final controller in _controllers.values) {
      controller.clear();
    }
    _store.reset();
  }

  Widget _field(
    AdvancedSearchFilter filter,
    String label, {
    bool number = false,
  }) {
    final controller = _controllerFor(filter);
    return AdvancedSearchField(
      controller: controller,
      label: label,
      number: number,
      onChanged: (value) => _store.updateText(filter, value),
      onClear: () {
        controller.clear();
        _store.clear(filter);
      },
    );
  }

  Future<void> _pickDate({
    required DateTime? current,
    required ValueChanged<DateTime?> onChanged,
  }) async {
    final picked = await showDatePicker(
      context: context,
      initialDate: current ?? DateTime.now(),
      firstDate: DateTime(2006),
      lastDate: DateTime.now(),
    );
    if (picked != null && mounted) onChanged(picked);
  }

  AdvancedSearchDateRow _dateRow({
    required String label,
    required DateTime? value,
    required ValueChanged<DateTime?> onChanged,
  }) {
    return AdvancedSearchDateRow(
      label: label,
      value: value == null
          ? L10n.of(context).not_set
          : DateFormat('yyyy-MM-dd').format(value),
      selected: value != null,
      onTap: () => _pickDate(current: value, onChanged: onChanged),
      onClear: () => onChanged(null),
    );
  }

  List<Widget> _wordFields(BuildContext context) {
    final l10n = L10n.of(context);
    return [
      _field(AdvancedSearchFilter.allWords, l10n.all_of_these_words),
      _field(AdvancedSearchFilter.exactPhrase, l10n.this_exact_phrase),
      _field(AdvancedSearchFilter.anyWords, l10n.any_of_these_words),
      _field(AdvancedSearchFilter.noneWords, l10n.none_of_these_words),
      _field(AdvancedSearchFilter.hashtags, l10n.these_hashtags),
    ];
  }

  List<Widget> _accountFields(BuildContext context) {
    final l10n = L10n.of(context);
    return [
      _field(AdvancedSearchFilter.fromAccounts, l10n.from_these_accounts),
      _field(AdvancedSearchFilter.toAccounts, l10n.to_these_accounts),
      _field(
        AdvancedSearchFilter.mentioningAccounts,
        l10n.mentioning_these_accounts,
      ),
    ];
  }

  List<Widget> _filterFields(
    BuildContext context,
    AdvancedSearchState state,
  ) {
    final l10n = L10n.of(context);
    return [
      _field(
        AdvancedSearchFilter.minReplies,
        l10n.minimum_replies,
        number: true,
      ),
      _field(
        AdvancedSearchFilter.minLikes,
        l10n.minimum_likes,
        number: true,
      ),
      _field(
        AdvancedSearchFilter.minRetweets,
        l10n.minimum_reposts,
        number: true,
      ),
      AdvancedSearchToggleRow(
        label: l10n.only_show_posts_with_media,
        value: state.onlyMedia,
        onChanged: _store.setOnlyMedia,
      ),
      _dateRow(
        label: l10n.since_date,
        value: state.since,
        onChanged: _store.setSince,
      ),
      _dateRow(
        label: l10n.until_date,
        value: state.until,
        onChanged: _store.setUntil,
      ),
    ];
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return ScopedBuilder<AdvancedSearchStore, AdvancedSearchState>(
      store: _store,
      onState: (_, state) => SearchSystemBars(
        child: Scaffold(
          appBar: AppBar(
            title: Text(l10n.advanced_search),
            actions: [
              IconButton(
                tooltip: l10n.group_combine_clear,
                icon: const Icon(Icons.restart_alt),
                onPressed: state.activeFilters.isEmpty ? null : _reset,
              ),
            ],
          ),
          body: ListView(
            keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
            children: [
              AdvancedFilterSection(
                icon: Icons.text_fields,
                title: l10n.search_term,
                children: _wordFields(context),
              ),
              AdvancedFilterSection(
                icon: Icons.alternate_email,
                title: l10n.account,
                children: _accountFields(context),
              ),
              AdvancedFilterSection(
                icon: Icons.tune,
                title: l10n.filters,
                children: _filterFields(context, state),
              ),
              SearchQueryPreview(query: state.query),
              const SizedBox(height: kTweetSpace2),
            ],
          ),
          bottomNavigationBar: SearchApplyBar(
            enabled: true,
            onPressed: _apply,
          ),
        ),
      ),
    );
  }
}
