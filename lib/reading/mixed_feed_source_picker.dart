import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/reading/mixed_feed_definition.dart';
import 'package:xta/reading/mixed_feed_source.dart';
import 'package:xta/reading/mixed_feed_sources.dart';

/// Lets the reader pick a source for a mix from the plugins that are on; null when they back out.
Future<MixedFeedSource?> showMixedSourcePicker(BuildContext context, {List<MixedSourceKind>? kinds}) async {
  final prefs = PrefService.of(context, listen: false);
  final available = [
    for (final kind in kinds ?? mixedSourceKinds)
      if (mixedSourceKindEnabled(kind, prefs)) kind,
  ];
  final kind = await showModalBottomSheet<MixedSourceKind>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    showDragHandle: true,
    builder: (context) => _KindList(kinds: available),
  );
  if (kind == null || !context.mounted) return null;
  return switch (kind.input) {
    MixedSourceInput.none => MixedFeedSource(kind: kind.id, label: kind.title(context)),
    MixedSourceInput.choice => _pickChoice(context, kind),
    MixedSourceInput.text => _typeSource(context, kind),
  };
}

class _KindList extends StatelessWidget {
  final List<MixedSourceKind> kinds;
  const _KindList({required this.kinds});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    if (kinds.isEmpty) {
      return Padding(padding: const EdgeInsets.all(24), child: Text(l10n.mixed_feed_no_sources_available));
    }
    return ListView(
      shrinkWrap: true,
      children: [
        ListTile(title: Text(l10n.mixed_feed_add_source, style: Theme.of(context).textTheme.titleMedium)),
        for (final kind in kinds)
          ListTile(
            key: ValueKey('mix-kind-${kind.id}'),
            leading: Icon(kind.icon),
            title: Text(kind.title(context)),
            subtitle: Text(pluginById(kind.pluginId)?.title(context) ?? ''),
            trailing: kind.input == MixedSourceInput.none ? null : const Icon(Icons.chevron_right),
            onTap: () => Navigator.pop(context, kind),
          ),
      ],
    );
  }
}

class _ChoicesStore extends Store<List<MixedFeedSource>> {
  _ChoicesStore() : super(const []);

  Future<void> load(Future<List<MixedFeedSource>> Function() choices) => execute(choices, delay: Duration.zero);
}

Future<MixedFeedSource?> _pickChoice(BuildContext context, MixedSourceKind kind) => showModalBottomSheet(
  context: context,
  isScrollControlled: true,
  useSafeArea: true,
  showDragHandle: true,
  builder: (_) => _ChoiceList(kind: kind),
);

class _ChoiceList extends StatefulWidget {
  final MixedSourceKind kind;
  const _ChoiceList({required this.kind});

  @override
  State<_ChoiceList> createState() => _ChoiceListState();
}

class _ChoiceListState extends State<_ChoiceList> {
  final _choices = _ChoicesStore();

  @override
  void initState() {
    super.initState();
    _choices.load(() => widget.kind.choices(context));
  }

  @override
  void dispose() {
    _choices.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return ScopedBuilder<_ChoicesStore, List<MixedFeedSource>>(
      store: _choices,
      onLoading: (_) => const Padding(
        padding: EdgeInsets.all(24),
        child: Center(child: CircularProgressIndicator()),
      ),
      onError: (_, _) => Padding(padding: const EdgeInsets.all(24), child: Text(l10n.mixed_feed_choices_failed)),
      onState: (context, choices) => choices.isEmpty
          ? Padding(padding: const EdgeInsets.all(24), child: Text(l10n.mixed_feed_no_choices))
          : ListView(
              shrinkWrap: true,
              children: [
                ListTile(title: Text(widget.kind.title(context), style: Theme.of(context).textTheme.titleMedium)),
                for (final choice in choices)
                  ListTile(
                    key: ValueKey('mix-choice-${choice.value}'),
                    leading: Icon(widget.kind.icon),
                    title: Text(choice.label, maxLines: 2, overflow: TextOverflow.ellipsis),
                    onTap: () => Navigator.pop(context, choice),
                  ),
              ],
            ),
    );
  }
}

Future<MixedFeedSource?> _typeSource(BuildContext context, MixedSourceKind kind) async {
  final text = await showDialog<String>(
    context: context,
    builder: (_) => _TypedSource(kind: kind),
  );
  if (text == null || text.trim().isEmpty || !context.mounted) return null;
  final messenger = ScaffoldMessenger.maybeOf(context);
  final invalid = L10n.of(context).mixed_feed_source_invalid;
  final source = await kind.fromText(context, text.trim());
  if (source == null) {
    messenger?.showSnackBar(SnackBar(content: Text(invalid)));
  }
  return source;
}

class _TypedSource extends StatefulWidget {
  final MixedSourceKind kind;
  const _TypedSource({required this.kind});

  @override
  State<_TypedSource> createState() => _TypedSourceState();
}

class _TypedSourceState extends State<_TypedSource> {
  final _text = TextEditingController();

  @override
  void dispose() {
    _text.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return AlertDialog(
      title: Text(widget.kind.title(context)),
      content: TextField(
        key: const ValueKey('mix-source-text'),
        controller: _text,
        autofocus: true,
        autocorrect: false,
        decoration: InputDecoration(hintText: widget.kind.inputHint(context)),
        onSubmitted: (value) => Navigator.pop(context, value),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: Text(l10n.cancel)),
        TextButton(
          key: const ValueKey('mix-source-text-add'),
          onPressed: () => Navigator.pop(context, _text.text),
          child: Text(l10n.mixed_feed_add_source),
        ),
      ],
    );
  }
}
