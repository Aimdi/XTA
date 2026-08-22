import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/antenna/antenna_feed_screen.dart';
import 'package:xta/antenna/antenna_model.dart';
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
      await Navigator.pushNamed(
        context,
        routeAntennaFeed,
        arguments: AntennaFeedArguments(saved),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final model = context.read<AntennaModel>();

    return Scaffold(
      appBar: AppBar(title: Text(l10n.antenna_title)),
      floatingActionButton: FloatingActionButton(
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
            return Center(child: Text(l10n.antenna_empty));
          }

          return ListView.builder(
            itemCount: antennas.length,
            itemBuilder: (context, index) {
              final antenna = antennas[index];
              return ListTile(
                title: Text(antenna.name),
                subtitle: Text(antenna.includeTerms.join(', ')),
                trailing: IconButton(
                  icon: const Icon(Icons.edit_outlined),
                  onPressed: () => _openEditor(antenna),
                ),
                onTap: () => Navigator.pushNamed(
                  context,
                  routeAntennaFeed,
                  arguments: AntennaFeedArguments(antenna),
                ),
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
  late String _scope;

  @override
  void initState() {
    super.initState();
    final existing = widget.existing;
    _name = TextEditingController(text: existing?.name ?? '');
    _include = TextEditingController(
      text: existing?.includeTerms.join(', ') ?? '',
    );
    _exclude = TextEditingController(
      text: existing?.excludeTerms.join(', ') ?? '',
    );
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

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);

    return Padding(
      padding: EdgeInsets.fromLTRB(
        16,
        0,
        16,
        MediaQuery.of(context).viewInsets.bottom + 16,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            widget.existing == null ? l10n.antenna_new : l10n.antenna_title,
            style: Theme.of(context).textTheme.titleLarge,
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _name,
            decoration: InputDecoration(labelText: l10n.antenna_name),
          ),
          const SizedBox(height: 8),
          TextField(
            controller: _include,
            decoration: InputDecoration(labelText: l10n.antenna_include),
          ),
          const SizedBox(height: 8),
          TextField(
            controller: _exclude,
            decoration: InputDecoration(labelText: l10n.antenna_exclude),
          ),
          const SizedBox(height: 8),
          SegmentedButton<String>(
            segments: [
              ButtonSegment(
                value: 'search',
                label: Text(l10n.antenna_scope_search),
              ),
              ButtonSegment(
                value: 'following',
                label: Text(l10n.antenna_scope_following),
              ),
            ],
            selected: {_scope},
            onSelectionChanged: (value) => setState(() => _scope = value.first),
          ),
          const SizedBox(height: 12),
          FilledButton(onPressed: _save, child: Text(l10n.ok)),
        ],
      ),
    );
  }
}
