import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:intl/intl.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/reading/article_reading_store.dart';

class ArticleReaderControls extends StatelessWidget {
  final ArticleReadingStore store;
  final VoidCallback onAppearanceChanged;
  final VoidCallback onStartOver;
  final bool supportsAppearance;
  final bool canComplete;

  const ArticleReaderControls({
    super.key,
    required this.store,
    required this.onAppearanceChanged,
    required this.onStartOver,
    this.supportsAppearance = true,
    this.canComplete = true,
  });

  @override
  Widget build(BuildContext context) => ScopedBuilder<ArticleReadingStore, ArticleReadingState>(
    store: store,
    onState: (context, state) {
      final l10n = L10n.of(context);
      final percent = NumberFormat.percentPattern(
        Localizations.localeOf(context).toString(),
      ).format(state.point.fraction);
      return Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          LinearProgressIndicator(value: state.point.fraction, minHeight: 2),
          Padding(
            padding: const EdgeInsetsDirectional.only(start: 16, end: 4),
            child: Row(
              children: [
                Expanded(
                  child: Text(
                    state.point.completed
                        ? l10n.article_reader_finished
                        : state.resumed
                        ? '${l10n.article_reader_resumed} · $percent'
                        : state.point.fraction > 0.01
                        ? percent
                        : l10n.article_reader_opened,
                    maxLines: 2,
                    style: Theme.of(context).textTheme.labelMedium,
                  ),
                ),
                IconButton(
                  tooltip: l10n.article_reader_start_over,
                  icon: const Icon(Icons.replay),
                  onPressed: supportsAppearance
                      ? () {
                          store.startOver();
                          onStartOver();
                        }
                      : null,
                ),
                IconButton(
                  tooltip: l10n.article_reader_appearance,
                  icon: const Icon(Icons.text_fields),
                  onPressed: supportsAppearance
                      ? () => showModalBottomSheet<void>(
                          context: context,
                          isScrollControlled: true,
                          useSafeArea: true,
                          builder: (_) => ArticleAppearanceSheet(store: store, onChanged: onAppearanceChanged),
                        )
                      : null,
                ),
                IconButton(
                  tooltip: state.point.completed ? l10n.article_reader_finished : l10n.article_reader_finish,
                  icon: Icon(state.point.completed ? Icons.check_circle : Icons.check_circle_outline),
                  color: state.point.completed ? Theme.of(context).colorScheme.primary : null,
                  onPressed: state.point.completed || !canComplete ? null : store.complete,
                ),
              ],
            ),
          ),
        ],
      );
    },
  );
}

class ArticleAppearanceSheet extends StatelessWidget {
  final ArticleReadingStore store;
  final VoidCallback onChanged;
  const ArticleAppearanceSheet({super.key, required this.store, required this.onChanged});

  @override
  Widget build(BuildContext context) => ScopedBuilder<ArticleReadingStore, ArticleReadingState>(
    store: store,
    onState: (context, state) {
      final l10n = L10n.of(context);
      return SingleChildScrollView(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(24, 16, 24, 24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            mainAxisSize: MainAxisSize.min,
            children: [
              Row(
                children: [
                  Expanded(child: Text(l10n.article_reader_appearance, style: Theme.of(context).textTheme.titleLarge)),
                  IconButton(
                    tooltip: l10n.close,
                    icon: const Icon(Icons.close),
                    onPressed: () => Navigator.pop(context),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              Text(l10n.article_reader_text_size),
              Slider(
                value: state.fontSize,
                min: 16,
                max: 28,
                divisions: 12,
                label: state.fontSize.toStringAsFixed(0),
                semanticFormatterCallback: (value) => '${l10n.article_reader_text_size} ${value.round()}',
                onChanged: (value) {
                  store.appearance(fontSize: value);
                  onChanged();
                },
              ),
              Text(l10n.article_reader_line_spacing),
              Slider(
                value: state.lineHeight,
                min: 1.4,
                max: 2.2,
                divisions: 8,
                label: state.lineHeight.toStringAsFixed(1),
                semanticFormatterCallback: (value) => '${l10n.article_reader_line_spacing} ${value.toStringAsFixed(1)}',
                onChanged: (value) {
                  store.appearance(lineHeight: value);
                  onChanged();
                },
              ),
            ],
          ),
        ),
      );
    },
  );
}
