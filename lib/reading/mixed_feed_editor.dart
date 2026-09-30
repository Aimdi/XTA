import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/reading/mixed_feed_definition.dart';
import 'package:xta/reading/mixed_feed_source.dart';
import 'package:xta/reading/mixed_feed_source_picker.dart';
import 'package:xta/reading/mixed_feed_sources.dart';
import 'package:xta/reading/mixed_feed_store.dart';
import 'package:xta/reading/reader_preference_list.dart';
import 'package:xta/settings/settings_chrome.dart';

/// Opens the editor for [mix], or for a new mix; returns the mix as saved, or null.
Future<MixedFeedDefinition?> openMixedFeedEditor(BuildContext context, {MixedFeedDefinition? mix}) =>
    Navigator.push<MixedFeedDefinition>(context, MaterialPageRoute(builder: (_) => MixedFeedEditor(mix: mix)));

class _Draft {
  final MixedFeedDefinition mix;
  final MixedFeedProblem? shownProblem;
  const _Draft(this.mix, [this.shownProblem]);
}

class _DraftStore extends Store<_Draft> {
  _DraftStore(MixedFeedDefinition mix) : super(_Draft(mix));

  MixedFeedDefinition get mix => state.mix;

  void change(MixedFeedDefinition mix) => update(_Draft(mix, state.shownProblem == null ? null : mix.problem));

  void explain() => update(_Draft(state.mix, state.mix.problem));
}

class MixedFeedEditor extends StatefulWidget {
  final MixedFeedDefinition? mix;

  /// The kinds sources are picked from; the registered kinds unless a test provides its own.
  final List<MixedSourceKind>? kinds;
  const MixedFeedEditor({super.key, this.mix, this.kinds});

  @override
  State<MixedFeedEditor> createState() => _MixedFeedEditorState();
}

class _MixedFeedEditorState extends State<MixedFeedEditor> {
  late final _draft = _DraftStore(widget.mix ?? MixedFeedDefinition(id: newReaderItemId(), name: ''));
  late final _name = TextEditingController(text: widget.mix?.name ?? '');

  MixedFeedStore get _store => MixedFeedStore.forPrefs(PrefService.of(context, listen: false));

  @override
  void dispose() {
    _draft.destroy();
    _name.dispose();
    super.dispose();
  }

  Future<void> _addSource() async {
    final source = await showMixedSourcePicker(context, kinds: widget.kinds);
    if (source == null || !mounted) return;
    final sources = _draft.mix.sources;
    if (sources.any((other) => other.key == source.key) || sources.length >= mixedFeedMaxSources) return;
    _draft.change(_draft.mix.copyWith(sources: [...sources, source]));
  }

  void _reorder(int from, int to) {
    final sources = [..._draft.mix.sources];
    sources.insert(to.clamp(0, sources.length - 1), sources.removeAt(from));
    _draft.change(_draft.mix.copyWith(sources: sources));
  }

  Future<void> _save() async {
    final mix = _draft.mix.copyWith(name: _name.text.trim());
    _draft.change(mix);
    if (mix.problem != null) {
      _draft.explain();
      return;
    }
    final messenger = ScaffoldMessenger.of(context);
    final failed = L10n.of(context).mixed_feed_save_failed;
    if (!await _store.save(mix)) {
      messenger.showSnackBar(SnackBar(content: Text(failed)));
      return;
    }
    if (mounted) Navigator.pop(context, mix);
  }

  Future<void> _delete() async {
    final l10n = L10n.of(context);
    final navigator = Navigator.of(context);
    final messenger = ScaffoldMessenger.of(context);
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        content: Text(l10n.mixed_feed_delete_confirm),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: Text(l10n.cancel)),
          TextButton(
            key: const ValueKey('mix-delete-confirm'),
            onPressed: () => Navigator.pop(context, true),
            child: Text(l10n.delete),
          ),
        ],
      ),
    );
    if (confirmed != true) return;
    if (await _store.remove(_draft.mix.id)) {
      navigator.pop();
    } else {
      messenger.showSnackBar(SnackBar(content: Text(l10n.mixed_feed_save_failed)));
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return SettingsPageScaffold(
      title: widget.mix == null ? l10n.mixed_feed_new : l10n.mixed_feed_edit,
      actions: [
        if (widget.mix != null)
          IconButton(
            key: const ValueKey('mix-delete'),
            tooltip: l10n.delete,
            icon: const Icon(Icons.delete_outline),
            onPressed: _delete,
          ),
      ],
      body: ScopedBuilder<_DraftStore, _Draft>(store: _draft, onState: _form),
    );
  }

  Widget _form(BuildContext context, _Draft draft) {
    final l10n = L10n.of(context);
    final sources = draft.mix.sources;
    return ReorderableListView.builder(
      buildDefaultDragHandles: false,
      padding: const EdgeInsets.only(bottom: 24),
      header: _MixedFeedHeader(
        name: _name,
        order: draft.mix.order,
        problem: draft.shownProblem,
        onOrder: (order) => _draft.change(_draft.mix.copyWith(order: order)),
      ),
      footer: _MixedFeedActions(canAdd: sources.length < mixedFeedMaxSources, onAdd: _addSource, onSave: _save),
      itemCount: sources.length,
      onReorderItem: _reorder,
      itemBuilder: (context, index) {
        final source = sources[index];
        final kind = mixedSourceKindById(source.kind, kinds: widget.kinds);
        return ListTile(
          key: ValueKey(source.key),
          leading: Icon(kind?.icon ?? Icons.help_outline),
          title: Text(source.label, maxLines: 2, overflow: TextOverflow.ellipsis),
          subtitle: kind == null ? null : Text(kind.title(context)),
          trailing: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              IconButton(
                key: ValueKey('mix-remove-${source.key}'),
                tooltip: l10n.mixed_feed_remove_source,
                icon: const Icon(Icons.remove_circle_outline),
                onPressed: () => _draft.change(_draft.mix.copyWith(sources: [...sources]..removeAt(index))),
              ),
              ReorderableDragStartListener(
                index: index,
                child: const SizedBox.square(dimension: 48, child: Icon(Icons.drag_handle)),
              ),
            ],
          ),
        );
      },
    );
  }
}

String? _problemText(L10n l10n, MixedFeedProblem? problem) => switch (problem) {
  MixedFeedProblem.emptyName => l10n.mixed_feed_error_name,
  MixedFeedProblem.noSources => l10n.mixed_feed_error_sources,
  MixedFeedProblem.tooManySources => l10n.mixed_feed_error_too_many(mixedFeedMaxSources),
  null => null,
};

class _MixedFeedHeader extends StatelessWidget {
  final TextEditingController name;
  final MixedFeedOrder order;
  final MixedFeedProblem? problem;
  final ValueChanged<MixedFeedOrder> onOrder;

  const _MixedFeedHeader({required this.name, required this.order, required this.problem, required this.onOrder});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          TextField(
            key: const ValueKey('mix-name'),
            controller: name,
            maxLength: mixedFeedMaxName,
            textCapitalization: TextCapitalization.sentences,
            decoration: InputDecoration(border: const OutlineInputBorder(), labelText: l10n.name),
          ),
          const SizedBox(height: 8),
          Text(l10n.mixed_feed_order, style: theme.textTheme.titleSmall),
          const SizedBox(height: 8),
          SegmentedButton<MixedFeedOrder>(
            segments: [
              ButtonSegment(
                value: MixedFeedOrder.chronological,
                label: Text(l10n.mixed_feed_order_chronological, key: const ValueKey('mix-order-chronological')),
              ),
              ButtonSegment(
                value: MixedFeedOrder.alternating,
                label: Text(l10n.mixed_feed_order_alternating, key: const ValueKey('mix-order-alternating')),
              ),
            ],
            selected: {order},
            onSelectionChanged: (selected) => onOrder(selected.single),
          ),
          const SizedBox(height: 4),
          Text(
            order == MixedFeedOrder.chronological
                ? l10n.mixed_feed_order_chronological_description
                : l10n.mixed_feed_order_alternating_description,
            style: theme.textTheme.bodySmall,
          ),
          const SizedBox(height: 16),
          Text(l10n.mixed_feed_sources, style: theme.textTheme.titleSmall),
          if (_problemText(l10n, problem) case final text?)
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: Text(text, style: TextStyle(color: theme.colorScheme.error)),
            ),
        ],
      ),
    );
  }
}

class _MixedFeedActions extends StatelessWidget {
  final bool canAdd;
  final VoidCallback onAdd;
  final VoidCallback onSave;

  const _MixedFeedActions({required this.canAdd, required this.onAdd, required this.onSave});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          OutlinedButton.icon(
            key: const ValueKey('mix-add-source'),
            style: OutlinedButton.styleFrom(minimumSize: const Size(48, 48)),
            onPressed: canAdd ? onAdd : null,
            icon: const Icon(Icons.add),
            label: Text(l10n.mixed_feed_add_source),
          ),
          const SizedBox(height: 12),
          FilledButton(
            key: const ValueKey('mix-save'),
            style: FilledButton.styleFrom(minimumSize: const Size(48, 48)),
            onPressed: onSave,
            child: Text(l10n.save),
          ),
        ],
      ),
    );
  }
}
