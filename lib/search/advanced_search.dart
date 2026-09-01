import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:intl/intl.dart';
import 'package:quax/generated/l10n.dart';
import 'package:quax/search/advanced_search_model.dart';
import 'package:quax/search/search_chrome.dart';
import 'package:quax/tweet/tweet_chrome.dart';

export 'package:quax/search/advanced_search_model.dart'
    show AdvancedSearchFilter, AdvancedSearchState, buildAdvancedSearchQuery;

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
  late final AdvancedSearchStore _store = AdvancedSearchStore(
    widget.initialState,
  );
  late final Map<AdvancedSearchFilter, TextEditingController> _controllers = {
    AdvancedSearchFilter.allWords: TextEditingController(
      text: widget.initialState.allWords,
    ),
    AdvancedSearchFilter.exactPhrase: TextEditingController(
      text: widget.initialState.exactPhrase,
    ),
    AdvancedSearchFilter.anyWords: TextEditingController(
      text: widget.initialState.anyWords,
    ),
    AdvancedSearchFilter.noneWords: TextEditingController(
      text: widget.initialState.noneWords,
    ),
    AdvancedSearchFilter.hashtags: TextEditingController(
      text: widget.initialState.hashtags,
    ),
    AdvancedSearchFilter.fromAccounts: TextEditingController(
      text: widget.initialState.fromAccounts,
    ),
    AdvancedSearchFilter.toAccounts: TextEditingController(
      text: widget.initialState.toAccounts,
    ),
    AdvancedSearchFilter.mentioningAccounts: TextEditingController(
      text: widget.initialState.mentioningAccounts,
    ),
    AdvancedSearchFilter.minReplies: TextEditingController(
      text: widget.initialState.minReplies,
    ),
    AdvancedSearchFilter.minLikes: TextEditingController(
      text: widget.initialState.minLikes,
    ),
    AdvancedSearchFilter.minRetweets: TextEditingController(
      text: widget.initialState.minRetweets,
    ),
  };

  @override
  void dispose() {
    for (final controller in _controllers.values) {
      controller.dispose();
    }
    _store.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return SearchSystemBars(
      child: ScopedBuilder<AdvancedSearchStore, AdvancedSearchState>(
        store: _store,
        onState: (context, state) {
          _syncControllers(state);
          return Scaffold(
            appBar: _buildAppBar(context, state),
            body: _buildForm(context, state),
            bottomNavigationBar: _buildSubmit(context, state),
          );
        },
      ),
    );
  }

  AppBar _buildAppBar(BuildContext context, AdvancedSearchState state) {
    return AppBar(
      title: Text(L10n.of(context).advanced_search),
      actions: [
        if (state.activeFilters.isNotEmpty)
          IconButton(
            tooltip: L10n.of(context).delete,
            icon: const Icon(Icons.delete_sweep_outlined),
            onPressed: _store.reset,
          ),
      ],
    );
  }

  Widget _buildForm(BuildContext context, AdvancedSearchState state) {
    return ListView(
      keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
      padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(context).bottom),
      children: [
        if (state.activeFilters.isNotEmpty)
          SearchFilterStrip(chips: _filterChips(context, state)),
        AdvancedFilterSection(
          title: L10n.of(context).search_term,
          children: [
            _field(context, AdvancedSearchFilter.allWords),
            _field(context, AdvancedSearchFilter.exactPhrase),
            _field(context, AdvancedSearchFilter.anyWords),
            _field(context, AdvancedSearchFilter.noneWords),
            _field(context, AdvancedSearchFilter.hashtags),
          ],
        ),
        AdvancedFilterSection(
          title: L10n.of(context).account,
          children: [
            _field(context, AdvancedSearchFilter.fromAccounts),
            _field(context, AdvancedSearchFilter.toAccounts),
            _field(context, AdvancedSearchFilter.mentioningAccounts),
          ],
        ),
        AdvancedFilterSection(
          title: L10n.of(context).custom_feed_engagement,
          children: [
            _field(context, AdvancedSearchFilter.minReplies, number: true),
            _field(context, AdvancedSearchFilter.minLikes, number: true),
            _field(context, AdvancedSearchFilter.minRetweets, number: true),
          ],
        ),
        AdvancedFilterSection(
          title: L10n.of(context).media,
          children: [
            SwitchListTile(
              contentPadding: const EdgeInsets.symmetric(
                horizontal: kTweetHorizontalPadding,
              ),
              title: Text(L10n.of(context).only_show_posts_with_media),
              value: state.onlyMedia,
              onChanged: _store.setOnlyMedia,
            ),
          ],
        ),
        AdvancedFilterSection(
          title: L10n.of(context).date_created,
          children: [
            _dateTile(context, state, AdvancedSearchFilter.since),
            _dateTile(context, state, AdvancedSearchFilter.until),
          ],
        ),
        if (state.query.isNotEmpty) SearchQueryPreview(query: state.query),
      ],
    );
  }

  Widget _field(
    BuildContext context,
    AdvancedSearchFilter filter, {
    bool number = false,
  }) {
    return AdvancedSearchField(
      controller: _controllers[filter]!,
      label: advancedFilterLabel(context, filter),
      number: number,
      onChanged: (value) => _store.updateText(filter, value),
      onClear: () => _store.clear(filter),
    );
  }

  Widget _dateTile(
    BuildContext context,
    AdvancedSearchState state,
    AdvancedSearchFilter filter,
  ) {
    final value = filter == AdvancedSearchFilter.since
        ? state.since
        : state.until;
    final label = advancedFilterLabel(context, filter);
    return ListTile(
      minVerticalPadding: kTweetSpace2,
      contentPadding: const EdgeInsets.symmetric(
        horizontal: kTweetHorizontalPadding,
      ),
      title: Text(label),
      subtitle: value == null
          ? null
          : Text(
              DateFormat.yMMMd(
                Localizations.localeOf(context).toString(),
              ).format(value),
            ),
      trailing: value == null
          ? const Icon(
              Icons.calendar_today_outlined,
              size: kTweetActionIconSize,
            )
          : IconButton(
              tooltip: L10n.of(context).delete,
              icon: const Icon(Icons.close),
              onPressed: () => _store.clear(filter),
            ),
      onTap: () => _pickDate(filter, value),
    );
  }

  Widget _buildSubmit(BuildContext context, AdvancedSearchState state) {
    return SafeArea(
      top: false,
      child: Padding(
        padding: const EdgeInsets.all(kTweetSpace3),
        child: FilledButton.icon(
          onPressed: state.query.isEmpty
              ? null
              : () => Navigator.pop(context, state),
          icon: const Icon(Icons.search),
          label: Text(L10n.of(context).search),
        ),
      ),
    );
  }

  Future<void> _pickDate(AdvancedSearchFilter filter, DateTime? value) async {
    final picked = await showDatePicker(
      context: context,
      initialDate: value ?? DateTime.now(),
      firstDate: DateTime(2006),
      lastDate: DateTime.now(),
    );
    if (picked == null) return;
    if (filter == AdvancedSearchFilter.since) {
      _store.setSince(picked);
    } else {
      _store.setUntil(picked);
    }
  }

  List<Widget> _filterChips(BuildContext context, AdvancedSearchState state) {
    return state.activeFilters
        .map(
          (filter) => SearchActiveFilterChip(
            label: _chipLabel(context, state, filter),
            onDeleted: () => _store.clear(filter),
          ),
        )
        .toList(growable: false);
  }

  String _chipLabel(
    BuildContext context,
    AdvancedSearchState state,
    AdvancedSearchFilter filter,
  ) {
    final label = advancedFilterLabel(context, filter);
    final value = state.valueOf(filter);
    return value.isEmpty ? label : '$label: $value';
  }

  void _syncControllers(AdvancedSearchState state) {
    for (final entry in _controllers.entries) {
      final value = state.valueOf(entry.key);
      if (entry.value.text == value) continue;
      entry.value.value = TextEditingValue(
        text: value,
        selection: TextSelection.collapsed(offset: value.length),
      );
    }
  }
}
