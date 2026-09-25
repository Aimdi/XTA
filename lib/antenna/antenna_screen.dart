import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/antenna/antenna_feed_screen.dart';
import 'package:xta/antenna/antenna_model.dart';
import 'package:xta/antenna/antenna_widgets.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/muted_keyword.dart';
import 'package:xta/ui/errors.dart';

class AntennaScreen extends StatefulWidget {
  const AntennaScreen({super.key});

  @override
  State<AntennaScreen> createState() => _AntennaScreenState();
}

class _AntennaScreenState extends State<AntennaScreen> {
  @override
  void initState() {
    super.initState();
    context.read<AntennaModel>().listAntennas();
  }

  Future<void> _openEditor([Antenna? existing]) async {
    final saved = await showModalBottomSheet<Antenna>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      showDragHandle: true,
      builder: (sheetContext) => _AntennaEditorSheet(existing: existing),
    );
    if (saved != null && mounted) {
      await Navigator.pushNamed(context, routeAntennaFeed, arguments: AntennaFeedArguments(saved));
    }
  }

  Future<void> _confirmDelete(Antenna antenna) async {
    final model = context.read<AntennaModel>();
    final l10n = L10n.of(context);
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(l10n.are_you_sure),
        content: Text(antenna.name),
        actions: [
          TextButton(onPressed: () => Navigator.pop(dialogContext, false), child: Text(l10n.cancel)),
          FilledButton(
            style: FilledButton.styleFrom(
              backgroundColor: Theme.of(dialogContext).colorScheme.error,
              foregroundColor: Theme.of(dialogContext).colorScheme.onError,
            ),
            onPressed: () => Navigator.pop(dialogContext, true),
            child: Text(l10n.delete),
          ),
        ],
      ),
    );
    if (confirmed == true && mounted) {
      await model.deleteAntenna(antenna.id);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final model = context.read<AntennaModel>();

    return Scaffold(
      appBar: AppBar(title: Text(l10n.antenna_title)),
      floatingActionButton: FloatingActionButton(
        tooltip: l10n.antenna_new,
        onPressed: () => _openEditor(),
        child: const Icon(Icons.add),
      ),
      body: ScopedBuilder<AntennaModel, List<Antenna>>(
        store: model,
        onError: (_, error) => FullPageErrorWidget(
          error: error,
          stackTrace: null,
          prefix: l10n.unable_to_load_the_tweets,
          onRetry: () => model.listAntennas(),
        ),
        onLoading: (_) => const Center(child: CircularProgressIndicator()),
        onState: (_, antennas) {
          if (antennas.isEmpty) {
            return AntennaEmptyState(
              label: l10n.antenna_empty,
              actionLabel: l10n.antenna_new,
              onAction: () => _openEditor(),
            );
          }

          return AntennaManagementList(
            itemCount: antennas.length,
            itemBuilder: (context, index) {
              final antenna = antennas[index];
              return AntennaTile(
                key: ValueKey('antenna-${antenna.id}'),
                name: antenna.name,
                terms: antenna.includeTerms.join(', '),
                scopeLabel: antenna.scope == 'following' ? l10n.antenna_scope_following : l10n.antenna_scope_search,
                settingsLabel: l10n.settings,
                deleteLabel: l10n.delete,
                onOpen: () => Navigator.pushNamed(context, routeAntennaFeed, arguments: AntennaFeedArguments(antenna)),
                onSettings: () => _openEditor(antenna),
                onDelete: () => _confirmDelete(antenna),
              );
            },
          );
        },
      ),
    );
  }
}

class _AntennaEditorSheet extends StatefulWidget {
  final Antenna? existing;

  const _AntennaEditorSheet({this.existing});

  @override
  State<_AntennaEditorSheet> createState() => _AntennaEditorSheetState();
}

class _AntennaEditorSheetState extends State<_AntennaEditorSheet> {
  late final TextEditingController _name;
  late final TextEditingController _include;
  late final TextEditingController _exclude;
  late final Listenable _fields;
  late String _scope;

  @override
  void initState() {
    super.initState();
    final existing = widget.existing;
    _name = TextEditingController(text: existing?.name ?? '');
    _include = TextEditingController(text: existing?.includeTerms.join(', ') ?? '');
    _exclude = TextEditingController(text: existing?.excludeTerms.join(', ') ?? '');
    _fields = Listenable.merge([_name, _include]);
    _scope = existing?.scope ?? 'search';
  }

  @override
  void dispose() {
    _name.dispose();
    _include.dispose();
    _exclude.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    final include = parseMutedKeywordTerms(_include.text);
    if (_name.text.trim().isEmpty || include.isEmpty) {
      return;
    }

    final model = context.read<AntennaModel>();
    final saved = await model.saveAntenna(
      id: widget.existing?.id,
      name: _name.text,
      includeTerms: include,
      excludeTerms: parseMutedKeywordTerms(_exclude.text),
      scope: _scope,
      createdAt: widget.existing?.createdAt,
    );

    if (mounted) {
      Navigator.pop(context, saved);
    }
  }

  bool get _canSave => _name.text.trim().isNotEmpty && parseMutedKeywordTerms(_include.text).isNotEmpty;

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);

    return AnimatedBuilder(
      animation: _fields,
      builder: (context, _) => AntennaEditorContent(
        title: widget.existing == null ? l10n.antenna_new : l10n.antenna_title,
        nameLabel: l10n.antenna_name,
        includeLabel: l10n.antenna_include,
        excludeLabel: l10n.antenna_exclude,
        searchScopeLabel: l10n.antenna_scope_search,
        followingScopeLabel: l10n.antenna_scope_following,
        saveLabel: l10n.ok,
        nameController: _name,
        includeController: _include,
        excludeController: _exclude,
        scope: _scope,
        onScopeChanged: (value) => setState(() => _scope = value),
        onSave: _canSave ? _save : null,
      ),
    );
  }
}
