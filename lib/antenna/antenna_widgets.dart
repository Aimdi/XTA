import 'package:flutter/material.dart';

const double kAntennaContentWidth = 720;
const double kAntennaScopeBreakpoint = 480;

enum AntennaTileAction { settings, delete }

class AntennaManagementList extends StatelessWidget {
  final int itemCount;
  final IndexedWidgetBuilder itemBuilder;

  const AntennaManagementList({super.key, required this.itemCount, required this.itemBuilder});

  @override
  Widget build(BuildContext context) {
    return Align(
      alignment: Alignment.topCenter,
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: kAntennaContentWidth),
        child: ListView.separated(
          padding: const EdgeInsets.only(bottom: 96),
          itemCount: itemCount,
          separatorBuilder: (_, _) => const Divider(height: 1),
          itemBuilder: itemBuilder,
        ),
      ),
    );
  }
}

class AntennaEmptyState extends StatelessWidget {
  final String label;
  final String actionLabel;
  final VoidCallback onAction;

  const AntennaEmptyState({super.key, required this.label, required this.actionLabel, required this.onAction});

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    return Center(
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(32),
        child: Semantics(
          liveRegion: true,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(Icons.radar, size: 48, color: colors.onSurfaceVariant),
              const SizedBox(height: 16),
              Text(label, textAlign: TextAlign.center),
              const SizedBox(height: 20),
              FilledButton.icon(
                style: FilledButton.styleFrom(minimumSize: const Size(0, 48)),
                onPressed: onAction,
                icon: const Icon(Icons.add),
                label: Text(actionLabel),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class AntennaTile extends StatelessWidget {
  final String name;
  final String terms;
  final String scopeLabel;
  final String settingsLabel;
  final String deleteLabel;
  final VoidCallback onOpen;
  final VoidCallback onSettings;
  final VoidCallback onDelete;

  const AntennaTile({
    super.key,
    required this.name,
    required this.terms,
    required this.scopeLabel,
    required this.settingsLabel,
    required this.deleteLabel,
    required this.onOpen,
    required this.onSettings,
    required this.onDelete,
  });

  @override
  Widget build(BuildContext context) {
    return ListTile(
      minVerticalPadding: 12,
      leading: const Icon(Icons.radar),
      title: Text(name, maxLines: 2, overflow: TextOverflow.ellipsis),
      subtitle: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(scopeLabel, maxLines: 2, overflow: TextOverflow.ellipsis),
          const SizedBox(height: 2),
          Text(terms, maxLines: 2, overflow: TextOverflow.ellipsis),
        ],
      ),
      trailing: PopupMenuButton<AntennaTileAction>(
        tooltip: MaterialLocalizations.of(context).showMenuTooltip,
        onSelected: (action) => switch (action) {
          AntennaTileAction.settings => onSettings(),
          AntennaTileAction.delete => onDelete(),
        },
        itemBuilder: (context) {
          final errorColor = Theme.of(context).colorScheme.error;
          return [
            PopupMenuItem(
              value: AntennaTileAction.settings,
              child: ListTile(
                contentPadding: EdgeInsets.zero,
                leading: const Icon(Icons.tune),
                title: Text(settingsLabel),
              ),
            ),
            PopupMenuItem(
              value: AntennaTileAction.delete,
              child: ListTile(
                contentPadding: EdgeInsets.zero,
                leading: Icon(Icons.delete_outline, color: errorColor),
                title: Text(deleteLabel, style: TextStyle(color: errorColor)),
              ),
            ),
          ];
        },
      ),
      onTap: onOpen,
    );
  }
}

class AntennaEditorContent extends StatelessWidget {
  final String title;
  final String nameLabel;
  final String includeLabel;
  final String excludeLabel;
  final String searchScopeLabel;
  final String followingScopeLabel;
  final String saveLabel;
  final TextEditingController nameController;
  final TextEditingController includeController;
  final TextEditingController excludeController;
  final String scope;
  final ValueChanged<String> onScopeChanged;
  final VoidCallback? onSave;

  const AntennaEditorContent({
    super.key,
    required this.title,
    required this.nameLabel,
    required this.includeLabel,
    required this.excludeLabel,
    required this.searchScopeLabel,
    required this.followingScopeLabel,
    required this.saveLabel,
    required this.nameController,
    required this.includeController,
    required this.excludeController,
    required this.scope,
    required this.onScopeChanged,
    required this.onSave,
  });

  @override
  Widget build(BuildContext context) {
    final bottomInset = MediaQuery.viewInsetsOf(context).bottom;
    final duration = MediaQuery.disableAnimationsOf(context) ? Duration.zero : const Duration(milliseconds: 200);

    return AnimatedPadding(
      duration: duration,
      curve: Curves.easeOut,
      padding: EdgeInsets.only(bottom: bottomInset),
      child: LayoutBuilder(
        builder: (context, constraints) => Align(
          alignment: Alignment.topCenter,
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: kAntennaContentWidth),
            child: SingleChildScrollView(
              key: const Key('antenna-editor-scroll'),
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text(title, style: Theme.of(context).textTheme.titleLarge),
                  const SizedBox(height: 12),
                  TextField(
                    controller: nameController,
                    textInputAction: TextInputAction.next,
                    decoration: InputDecoration(labelText: nameLabel),
                  ),
                  const SizedBox(height: 8),
                  TextField(
                    controller: includeController,
                    textInputAction: TextInputAction.next,
                    decoration: InputDecoration(labelText: includeLabel),
                  ),
                  const SizedBox(height: 8),
                  TextField(
                    controller: excludeController,
                    textInputAction: TextInputAction.done,
                    decoration: InputDecoration(labelText: excludeLabel),
                    onSubmitted: (_) => onSave?.call(),
                  ),
                  const SizedBox(height: 12),
                  _scopeSelector(context),
                  const SizedBox(height: 16),
                  FilledButton(
                    style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(48)),
                    onPressed: onSave,
                    child: Text(saveLabel),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _scopeSelector(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final textScale = MediaQuery.textScalerOf(context).scale(1);
        final segmented = constraints.maxWidth >= kAntennaScopeBreakpoint && textScale < 1.3;
        if (segmented) {
          return SegmentedButton<String>(
            key: const Key('antenna-scope-segmented'),
            segments: [
              ButtonSegment(value: 'search', label: Text(searchScopeLabel)),
              ButtonSegment(value: 'following', label: Text(followingScopeLabel)),
            ],
            selected: {scope},
            onSelectionChanged: (value) => onScopeChanged(value.first),
          );
        }

        return RadioGroup<String>(
          groupValue: scope,
          onChanged: (value) {
            if (value != null) onScopeChanged(value);
          },
          child: Column(
            key: const Key('antenna-scope-stacked'),
            children: [
              RadioListTile<String>(contentPadding: EdgeInsets.zero, value: 'search', title: Text(searchScopeLabel)),
              RadioListTile<String>(
                contentPadding: EdgeInsets.zero,
                value: 'following',
                title: Text(followingScopeLabel),
              ),
            ],
          ),
        );
      },
    );
  }
}
