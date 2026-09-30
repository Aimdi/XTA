import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:intl/intl.dart';
import 'package:pref/pref.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/reading/reader_preference_list.dart';
import 'package:xta/reading/shared_filter_engine.dart';
import 'package:xta/reading/shared_filter_rule.dart';
import 'package:xta/settings/settings_chrome.dart';

String sharedFilterProblemText(L10n l10n, SharedFilterProblem problem) => switch (problem) {
  SharedFilterProblem.empty => l10n.filter_error_empty,
  SharedFilterProblem.tooLong => l10n.filter_error_too_long,
  SharedFilterProblem.invalidPattern => l10n.filter_error_pattern,
  SharedFilterProblem.noScope => l10n.filter_error_scope,
};

String _summary(BuildContext context, SharedFilterRule rule) {
  final l10n = L10n.of(context);
  final until = rule.until;
  return [
    rule.action == SharedFilterAction.hide ? l10n.filter_action_hide : l10n.filter_action_fold,
    if (rule.regex) l10n.filter_regex,
    if (rule.caseSensitive) l10n.filter_case_sensitive,
    for (final scope in SharedFilterScope.values)
      if (rule.scopes.contains(scope))
        scope == SharedFilterScope.timelines ? l10n.filter_scope_timelines : l10n.filter_scope_search,
    if (until != null)
      rule.expiredAt(DateTime.now())
          ? l10n.filter_expired
          : l10n.filter_until(DateFormat.yMMMd(Localizations.localeOf(context).toString()).format(until)),
  ].join(' · ');
}

/// Every shared filter, where they can be switched off, edited or added.
class SharedFilterSettings extends StatelessWidget {
  final SharedFilterEngine? engine;
  const SharedFilterSettings({super.key, this.engine});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final engine = this.engine ?? SharedFilterEngine.forPrefs(PrefService.of(context, listen: false));
    final store = engine.rules;
    return SettingsPageScaffold(
      title: l10n.filters,
      floatingActionButton: FloatingActionButton.extended(
        key: const ValueKey('filter-add'),
        onPressed: () => showSharedFilterEditor(context, store),
        icon: const Icon(Icons.add),
        label: Text(l10n.filter_add),
      ),
      body: ScopedBuilder<SharedFilterEngine, int>(
        store: engine,
        onState: (context, _) => SettingsList(
          padding: const EdgeInsets.only(bottom: 96),
          children: [
            if (engine.patternsPaused)
              Card(
                margin: const EdgeInsets.all(16),
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(16, 12, 8, 4),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      Text(l10n.filter_patterns_paused),
                      Align(
                        alignment: AlignmentDirectional.centerEnd,
                        child: TextButton(onPressed: engine.resumePatterns, child: Text(l10n.retry)),
                      ),
                    ],
                  ),
                ),
              ),
            SettingsSection(
              description: store.state.isEmpty
                  ? '${l10n.filter_description}\n\n${l10n.filter_empty}'
                  : l10n.filter_description,
              children: [
                for (final rule in store.state)
                  SettingsRow(
                    key: ValueKey('filter-${rule.id}'),
                    title: rule.pattern,
                    description: _summary(context, rule),
                    enabled: true,
                    onTap: () => showSharedFilterEditor(context, store, rule: rule),
                    trailing: Switch(
                      value: rule.enabled,
                      onChanged: (enabled) => _save(context, () => store.setEnabled(rule.id, enabled)),
                    ),
                  ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

Future<void> _save(BuildContext context, Future<bool> Function() write) async {
  final message = L10n.of(context).filter_save_failed;
  final messenger = ScaffoldMessenger.of(context);
  if (!await write()) {
    messenger
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }
}

enum _Expiry { keep, never, day, week, month }

class _Draft {
  final bool regex;
  final bool caseSensitive;
  final SharedFilterAction action;
  final Set<SharedFilterScope> scopes;
  final _Expiry expiry;
  final SharedFilterProblem? problem;
  final bool saving;
  const _Draft({
    required this.regex,
    required this.caseSensitive,
    required this.action,
    required this.scopes,
    required this.expiry,
    this.problem,
    this.saving = false,
  });

  _Draft copyWith({
    bool? regex,
    bool? caseSensitive,
    SharedFilterAction? action,
    Set<SharedFilterScope>? scopes,
    _Expiry? expiry,
    SharedFilterProblem? Function()? problem,
    bool? saving,
  }) => _Draft(
    regex: regex ?? this.regex,
    caseSensitive: caseSensitive ?? this.caseSensitive,
    action: action ?? this.action,
    scopes: scopes ?? this.scopes,
    expiry: expiry ?? this.expiry,
    problem: problem == null ? this.problem : problem(),
    saving: saving ?? this.saving,
  );
}

class _DraftStore extends Store<_Draft> {
  _DraftStore(super.initialState);
  void change(_Draft Function(_Draft draft) edit) => update(edit(state));
}

Future<void> showSharedFilterEditor(BuildContext context, SharedFilterStore store, {SharedFilterRule? rule}) =>
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      showDragHandle: true,
      builder: (_) => _SharedFilterEditor(store: store, rule: rule),
    );

class _SharedFilterEditor extends StatefulWidget {
  final SharedFilterStore store;
  final SharedFilterRule? rule;
  const _SharedFilterEditor({required this.store, this.rule});

  @override
  State<_SharedFilterEditor> createState() => _SharedFilterEditorState();
}

class _SharedFilterEditorState extends State<_SharedFilterEditor> {
  late final _pattern = TextEditingController(text: widget.rule?.pattern ?? '');
  late final _draft = _DraftStore(
    _Draft(
      regex: widget.rule?.regex ?? false,
      caseSensitive: widget.rule?.caseSensitive ?? false,
      action: widget.rule?.action ?? SharedFilterAction.hide,
      scopes: widget.rule?.scopes ?? const {SharedFilterScope.timelines, SharedFilterScope.search},
      expiry: widget.rule?.until == null ? _Expiry.never : _Expiry.keep,
    ),
  );

  @override
  void dispose() {
    _pattern.dispose();
    _draft.destroy();
    super.dispose();
  }

  DateTime? _until(_Draft draft) {
    final now = DateTime.now();
    return switch (draft.expiry) {
      _Expiry.keep => widget.rule?.until,
      _Expiry.never => null,
      _Expiry.day => now.add(const Duration(days: 1)),
      _Expiry.week => now.add(const Duration(days: 7)),
      _Expiry.month => DateTime(now.year, now.month + 1, now.day, now.hour, now.minute),
    };
  }

  SharedFilterRule _rule(_Draft draft) => SharedFilterRule(
    id: widget.rule?.id ?? newReaderItemId(),
    pattern: _pattern.text.trim(),
    regex: draft.regex,
    caseSensitive: draft.caseSensitive,
    action: draft.action,
    scopes: draft.scopes,
    until: _until(draft),
    enabled: widget.rule?.enabled ?? true,
  );

  Future<void> _save() async {
    final rule = _rule(_draft.state);
    final problem = rule.problem;
    if (problem != null) {
      _draft.change((draft) => draft.copyWith(problem: () => problem));
      return;
    }
    final l10n = L10n.of(context);
    final messenger = ScaffoldMessenger.of(context);
    final navigator = Navigator.of(context);
    final full = widget.rule == null && widget.store.state.length >= sharedFilterMaxRules;
    _draft.change((draft) => draft.copyWith(saving: true, problem: () => null));
    final saved = await widget.store.save(rule);
    if (!mounted) return;
    _draft.change((draft) => draft.copyWith(saving: false));
    if (saved) {
      navigator.pop();
    } else {
      messenger
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text(full ? l10n.filter_limit_reached : l10n.filter_save_failed)));
    }
  }

  Future<void> _delete() async {
    final navigator = Navigator.of(context);
    final message = L10n.of(context).filter_save_failed;
    final messenger = ScaffoldMessenger.of(context);
    if (await widget.store.remove(widget.rule!.id)) {
      navigator.pop();
    } else {
      messenger.showSnackBar(SnackBar(content: Text(message)));
    }
  }

  @override
  Widget build(BuildContext context) => ScopedBuilder<_DraftStore, _Draft>(
    store: _draft,
    onState: (context, draft) => SingleChildScrollView(
      padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(context).bottom + 16),
      child: _form(context, draft),
    ),
  );

  Widget _form(BuildContext context, _Draft draft) {
    final l10n = L10n.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
          child: Text(
            widget.rule == null ? l10n.filter_add : l10n.filter_edit,
            style: Theme.of(context).textTheme.titleLarge,
          ),
        ),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: TextField(
            key: const ValueKey('filter-pattern'),
            controller: _pattern,
            autofocus: widget.rule == null,
            maxLength: sharedFilterMaxPattern,
            autocorrect: false,
            style: draft.regex ? const TextStyle(fontFamily: 'monospace') : null,
            decoration: InputDecoration(
              labelText: l10n.filter_pattern,
              errorText: draft.problem == null || draft.problem == SharedFilterProblem.noScope
                  ? null
                  : sharedFilterProblemText(l10n, draft.problem!),
            ),
          ),
        ),
        SwitchListTile(
          key: const ValueKey('filter-regex'),
          title: Text(l10n.filter_regex),
          value: draft.regex,
          onChanged: (value) => _draft.change((draft) => draft.copyWith(regex: value, problem: () => null)),
        ),
        SwitchListTile(
          title: Text(l10n.filter_case_sensitive),
          value: draft.caseSensitive,
          onChanged: (value) => _draft.change((draft) => draft.copyWith(caseSensitive: value)),
        ),
        _heading(context, l10n.filter_action),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: Wrap(
            spacing: 8,
            children: [
              for (final action in SharedFilterAction.values)
                ChoiceChip(
                  key: ValueKey('filter-action-${action.name}'),
                  materialTapTargetSize: MaterialTapTargetSize.padded,
                  label: Text(action == SharedFilterAction.hide ? l10n.filter_action_hide : l10n.filter_action_fold),
                  selected: draft.action == action,
                  onSelected: (_) => _draft.change((draft) => draft.copyWith(action: action)),
                ),
            ],
          ),
        ),
        _heading(context, l10n.filter_scope),
        for (final scope in SharedFilterScope.values)
          CheckboxListTile(
            key: ValueKey('filter-scope-${scope.name}'),
            title: Text(scope == SharedFilterScope.timelines ? l10n.filter_scope_timelines : l10n.filter_scope_search),
            value: draft.scopes.contains(scope),
            onChanged: (value) => _draft.change(
              (draft) => draft.copyWith(
                scopes: value == true ? {...draft.scopes, scope} : ({...draft.scopes}..remove(scope)),
                problem: () => null,
              ),
            ),
          ),
        if (draft.problem == SharedFilterProblem.noScope)
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Text(l10n.filter_error_scope, style: TextStyle(color: Theme.of(context).colorScheme.error)),
          ),
        _heading(context, l10n.filter_expires),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: DropdownButton<_Expiry>(
            key: const ValueKey('filter-expiry'),
            isExpanded: true,
            value: draft.expiry,
            items: [
              if (widget.rule?.until != null)
                DropdownMenuItem(
                  value: _Expiry.keep,
                  child: Text(
                    l10n.filter_until(
                      DateFormat.yMMMd(Localizations.localeOf(context).toString()).format(widget.rule!.until!),
                    ),
                  ),
                ),
              DropdownMenuItem(value: _Expiry.never, child: Text(l10n.filter_expire_never)),
              DropdownMenuItem(value: _Expiry.day, child: Text(l10n.filter_expire_1_day)),
              DropdownMenuItem(value: _Expiry.week, child: Text(l10n.filter_expire_1_week)),
              DropdownMenuItem(value: _Expiry.month, child: Text(l10n.filter_expire_1_month)),
            ],
            onChanged: (value) => _draft.change((draft) => draft.copyWith(expiry: value)),
          ),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
          child: Row(
            children: [
              if (widget.rule != null)
                TextButton(
                  key: const ValueKey('filter-delete'),
                  style: TextButton.styleFrom(
                    foregroundColor: Theme.of(context).colorScheme.error,
                    minimumSize: const Size(48, 48),
                  ),
                  onPressed: draft.saving ? null : _delete,
                  child: Text(l10n.delete),
                ),
              const Spacer(),
              FilledButton(
                key: const ValueKey('filter-save'),
                style: FilledButton.styleFrom(minimumSize: const Size(96, 48)),
                onPressed: draft.saving ? null : _save,
                child: Text(l10n.save),
              ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _heading(BuildContext context, String text) => Padding(
    padding: const EdgeInsets.fromLTRB(16, 16, 16, 4),
    child: Text(text, style: Theme.of(context).textTheme.titleSmall),
  );
}
