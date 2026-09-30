import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/substack/substack_html.dart';
import 'package:xta/reading/reader_translation_config.dart';
import 'package:xta/reading/reader_translation_service.dart';
import 'package:xta/reading/reader_translation_settings.dart';
import 'package:xta/reading/reader_translation_store.dart';

/// An article's text with its paragraphs, for translation outside its WebView.
String readerArticlePlainText(String html) => substackHtmlToPlainText(html);

/// Lets tests and previews substitute the provider transport and cache.
class ReaderTranslationServiceScope extends InheritedWidget {
  final ReaderTranslationService service;
  final ReaderTranslationCache? cache;
  const ReaderTranslationServiceScope({super.key, required this.service, this.cache, required super.child});

  static ReaderTranslationServiceScope? maybeOf(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<ReaderTranslationServiceScope>();

  @override
  bool updateShouldNotify(ReaderTranslationServiceScope oldWidget) =>
      service != oldWidget.service || cache != oldWidget.cache;
}

/// Looked up without depending on [PrefService]: cards must not rebuild on every preference write.
ReaderTranslationConfigStore? readerTranslationConfigOf(BuildContext context) {
  final prefs = context.findAncestorWidgetOfExactType<PrefService>()?.service;
  return prefs == null ? null : ReaderTranslationConfigStore.forPrefs(prefs);
}

/// The configured target as a locale, when the reader chose one other than the app language.
Locale? readerTranslationTargetLocale(BuildContext context) {
  final config = readerTranslationConfigOf(context)?.state;
  if (config == null || !config.enabled || config.target.isEmpty) return null;
  return Locale(config.target.split(RegExp('[_-]')).first);
}

ReaderTranslationService readerTranslationServiceOf(BuildContext context) =>
    ReaderTranslationServiceScope.maybeOf(context)?.service ?? ReaderTranslationService.shared;

ReaderTranslationCache readerTranslationCacheOf(BuildContext context) =>
    ReaderTranslationServiceScope.maybeOf(context)?.cache ?? ReaderTranslationCache.shared;

/// The app language as a translation target, e.g. `pt_BR` or `zh_Hant`.
String readerAppLanguage(BuildContext context) => Localizations.maybeLocaleOf(context)?.toString() ?? 'en';

/// Whether an explicit translate action should be offered for this reader.
bool readerTranslationEnabled(BuildContext context) => readerTranslationConfigOf(context)?.state.enabled ?? false;

ReaderTranslationStore _storeFor(
  BuildContext context,
  ReaderTranslationConfigStore config,
  String text, {
  required bool shared,
}) => ReaderTranslationStore(
  text: text,
  config: config,
  service: readerTranslationServiceOf(context),
  cache: readerTranslationCacheOf(context),
  appLanguage: readerAppLanguage(context),
  shared: shared,
);

/// Whether [text] currently reads translated wherever it is shown in place.
bool readerTranslationRequested(BuildContext context, String text) => readerTranslationCacheOf(context).requested(text);

/// A post action: translates in place when the text is on screen, otherwise in a sheet. Asking again restores it.
///
/// [inPlaceOnly] suits sources whose content warnings may still hide the text: it reads translated once revealed.
Future<void> toggleReaderTranslation(BuildContext context, String text, {String? title, bool inPlaceOnly = false}) =>
    toggleReaderTranslations(context, [text], title: title, inPlaceOnly: inPlaceOnly);

/// [toggleReaderTranslation] for a post shown in several parts, such as a title and a body.
Future<void> toggleReaderTranslations(
  BuildContext context,
  List<String> texts, {
  String? title,
  bool inPlaceOnly = false,
}) async {
  final parts = texts.where((text) => text.trim().isNotEmpty).toList();
  if (parts.isEmpty) return;
  final cache = readerTranslationCacheOf(context);
  if (parts.any(cache.requested)) {
    parts.forEach(cache.release);
  } else if (inPlaceOnly || parts.any(cache.shownInPlace)) {
    parts.forEach(cache.request);
  } else {
    await showReaderTranslationSheet(context, parts.join('\n\n'), title: title);
  }
}

/// Translates [text] once with the reader's service, sharing its cache. Null when no service is set up.
///
/// Throws [ReaderTranslationException] when the service cannot translate it.
Future<String?> translateWithReaderProvider(BuildContext context, String text) async {
  final config = readerTranslationConfigOf(context);
  if (config == null || !config.state.enabled) return null;
  final store = _storeFor(context, config, text, shared: false);
  try {
    await store.translate();
    final state = store.state;
    if (state.showsTranslation) return state.translated;
    throw ReaderTranslationException(state.failure ?? ReaderTranslationFailure.network);
  } finally {
    await store.destroy();
  }
}

/// Shows [text] through [builder], translated in place once the reader asks. The original is kept untouched.
///
/// [builder] receives the text to render with the source's own rich-text widget.
class ReaderTranslation extends StatefulWidget {
  final String text;
  final Widget Function(BuildContext context, String text) builder;

  /// Offers an inline Translate control; otherwise translation starts from a post's actions.
  final bool offer;

  const ReaderTranslation({super.key, required this.text, required this.builder, this.offer = false});

  @override
  State<ReaderTranslation> createState() => _ReaderTranslationState();
}

class _ReaderTranslationState extends State<ReaderTranslation> {
  ReaderTranslationConfigStore? _config;
  ReaderTranslationStore? _store;

  void _replaceStore() {
    final previous = _store;
    final config = _config;
    _store = config == null || widget.text.trim().isEmpty
        ? null
        : _storeFor(context, config, widget.text, shared: true);
    _store?.attach();
    previous?.detach();
    previous?.destroy();
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final config = readerTranslationConfigOf(context);
    if (!identical(config, _config)) {
      _config = config;
      _replaceStore();
    } else {
      _store?.appLanguage = readerAppLanguage(context);
    }
  }

  @override
  void didUpdateWidget(ReaderTranslation oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.text != widget.text) _replaceStore();
  }

  @override
  void dispose() {
    _store?.detach();
    _store?.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final config = _config;
    final store = _store;
    if (config == null || store == null) return widget.builder(context, widget.text);
    return ScopedBuilder<ReaderTranslationConfigStore, ReaderTranslationConfig>(
      store: config,
      onState: (context, settings) {
        if (!settings.enabled) return widget.builder(context, widget.text);
        return ScopedBuilder<ReaderTranslationStore, ReaderTranslationState>(
          store: store,
          onState: (context, state) {
            final showRow = widget.offer || state.status != ReaderTranslationStatus.original;
            final body = widget.builder(context, state.showsTranslation ? state.translated! : widget.text);
            if (!showRow) return body;
            return Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                body,
                ReaderTranslationStatusRow(
                  state: state,
                  onTranslate: store.translate,
                  onShowOriginal: store.showOriginal,
                ),
              ],
            );
          },
        );
      },
    );
  }
}

String readerTranslationFailureText(L10n l10n, ReaderTranslationFailure? failure) => switch (failure) {
  ReaderTranslationFailure.notConfigured => l10n.translation_not_configured,
  ReaderTranslationFailure.tooLong => l10n.translation_too_long,
  _ => l10n.translation_failed,
};

/// Translate, progress, Show original and Retry, each at least 48dp tall.
class ReaderTranslationStatusRow extends StatelessWidget {
  final ReaderTranslationState state;
  final VoidCallback onTranslate;
  final VoidCallback onShowOriginal;
  const ReaderTranslationStatusRow({
    super.key,
    required this.state,
    required this.onTranslate,
    required this.onShowOriginal,
  });

  Widget _button(String label, VoidCallback onPressed, {IconData? icon, Key? key}) => TextButton.icon(
    key: key,
    style: TextButton.styleFrom(minimumSize: const Size(48, 48)),
    onPressed: onPressed,
    icon: Icon(icon ?? Icons.translate, size: 18),
    label: Text(label),
  );

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final muted = Theme.of(
      context,
    ).textTheme.bodySmall?.copyWith(color: Theme.of(context).colorScheme.onSurfaceVariant);
    final children = switch (state.status) {
      ReaderTranslationStatus.original => [
        _button(l10n.translation_translate, onTranslate, key: const ValueKey('reader-translate')),
      ],
      ReaderTranslationStatus.loading => [
        const SizedBox.square(dimension: 16, child: CircularProgressIndicator(strokeWidth: 2)),
        Text(l10n.translation_translating, style: muted),
        _button(l10n.cancel, onShowOriginal, icon: Icons.close, key: const ValueKey('reader-translate-cancel')),
      ],
      ReaderTranslationStatus.translated => [
        Text(l10n.translation_translated, style: muted),
        _button(l10n.translation_show_original, onShowOriginal, key: const ValueKey('reader-translate-original')),
      ],
      ReaderTranslationStatus.failed => [
        Text(readerTranslationFailureText(l10n, state.failure), style: muted),
        if (state.failure == ReaderTranslationFailure.notConfigured)
          _button(l10n.translation_setup, () => openReaderTranslationSettings(context), icon: Icons.settings_outlined)
        else
          _button(l10n.retry, onTranslate, icon: Icons.refresh, key: const ValueKey('reader-translate-retry')),
        _button(l10n.translation_show_original, onShowOriginal, icon: Icons.close),
      ],
    };
    return Semantics(
      liveRegion: true,
      child: Wrap(spacing: 8, crossAxisAlignment: WrapCrossAlignment.center, children: children),
    );
  }
}

/// Translates [text] for any source, in a sheet over the post. Used where a source has no in-place translation.
Future<void> showReaderTranslationSheet(BuildContext context, String text, {String? title}) {
  final config = readerTranslationConfigOf(context);
  if (config == null) return Future.value();
  final store = _storeFor(context, config, text, shared: false);
  store.translate();
  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    showDragHandle: true,
    constraints: BoxConstraints(maxHeight: MediaQuery.sizeOf(context).height * .85),
    builder: (context) => ReaderTranslatedView(store: store, title: title ?? L10n.of(context).translation_title),
  ).whenComplete(store.destroy);
}

/// A translated copy of an article, shown natively beside the untouched original.
Future<void> openReaderArticleTranslation(BuildContext context, {required String title, required String text}) {
  final config = readerTranslationConfigOf(context);
  if (config == null) return Future.value();
  final store = _storeFor(context, config, text, shared: false);
  store.translate();
  return Navigator.push<void>(
    context,
    MaterialPageRoute(
      builder: (context) => Scaffold(
        appBar: AppBar(title: Text(title, maxLines: 2, overflow: TextOverflow.ellipsis)),
        body: SafeArea(child: ReaderTranslatedView(store: store)),
      ),
    ),
  ).whenComplete(store.destroy);
}

class ReaderTranslatedView extends StatelessWidget {
  final ReaderTranslationStore store;
  final String? title;
  const ReaderTranslatedView({super.key, required this.store, this.title});

  @override
  Widget build(BuildContext context) => ScopedBuilder<ReaderTranslationStore, ReaderTranslationState>(
    store: store,
    onState: (context, state) {
      final theme = Theme.of(context);
      return ListView(
        shrinkWrap: true,
        padding: const EdgeInsets.fromLTRB(16, 0, 16, 24),
        children: [
          if (title != null) Text(title!, style: theme.textTheme.titleMedium),
          if (state.loading)
            const Padding(
              padding: EdgeInsets.all(24),
              child: Center(child: CircularProgressIndicator()),
            ),
          if (state.showsTranslation)
            SelectableText(state.translated!, style: theme.textTheme.bodyLarge?.copyWith(height: 1.5)),
          if (state.status != ReaderTranslationStatus.loading)
            ReaderTranslationStatusRow(
              state: state,
              onTranslate: store.translate,
              onShowOriginal: () => Navigator.maybePop(context),
            ),
        ],
      );
    },
  );
}
