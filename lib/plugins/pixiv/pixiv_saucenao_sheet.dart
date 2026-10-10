import 'dart:typed_data';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_fetch_store.dart';
import 'package:xta/plugins/pixiv/pixiv_image.dart';
import 'package:xta/plugins/pixiv/pixiv_link_open.dart';
import 'package:xta/plugins/pixiv/pixiv_links.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_saucenao.dart';
import 'package:xta/plugins/pixiv/pixiv_search_shortcuts.dart';
import 'package:xta/plugins/pixiv/pixiv_settings.dart';
import 'package:xta/utils/number_locale.dart';

typedef PixivImagePicker = Future<Uint8List?> Function();

Future<Uint8List?> _pickFromDevice() async => (await FilePicker.pickFile(type: FileType.image))?.readAsBytes();

/// Opens the reverse image search over the current screen.
Future<void> showPixivSauceNaoSheet(BuildContext context) => showModalBottomSheet<void>(
  context: context,
  isScrollControlled: true,
  showDragHandle: true,
  builder: (_) => const PixivSauceNaoSheet(),
);

/// A SauceNAO search: null before an image was chosen, then its Pixiv matches.
class PixivSauceNaoStore extends Store<List<PixivSauceMatch>?> {
  final PixivSauceNaoApi api;
  final PixivImagePicker pickImage;
  final Future<Uint8List> Function(Uint8List bytes) prepare;
  Uint8List? _image;

  PixivSauceNaoStore(this.api, {this.pickImage = _pickFromDevice, this.prepare = pixivSauceImage}) : super(null);

  /// Lets the reader choose an image and searches it; cancelling changes nothing.
  Future<void> pickAndSearch() async {
    final image = await pickImage();
    if (image == null) return;
    _image = image;
    await _search(image);
  }

  Future<void> retry() {
    final image = _image;
    return image == null ? pickAndSearch() : _search(image);
  }

  Future<void> _search(Uint8List image) => execute(() async => api.search(await prepare(image)));
}

class PixivSauceNaoSheet extends StatefulWidget {
  /// Stands in for the device's image picker in tests.
  final PixivImagePicker? pickImage;

  /// Stands in for the downscale in tests.
  final Future<Uint8List> Function(Uint8List bytes)? prepare;

  const PixivSauceNaoSheet({super.key, this.pickImage, this.prepare});

  @override
  State<PixivSauceNaoSheet> createState() => _PixivSauceNaoSheetState();
}

class _PixivSauceNaoSheetState extends State<PixivSauceNaoSheet> {
  late final PixivSauceNaoStore _store;

  @override
  void initState() {
    super.initState();
    _store = PixivSauceNaoStore(
      PixivSauceNaoApi.of(context),
      pickImage: widget.pickImage ?? _pickFromDevice,
      prepare: widget.prepare ?? pixivSauceImage,
    );
  }

  @override
  void dispose() {
    _store.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    return SafeArea(
      child: ConstrainedBox(
        constraints: BoxConstraints(maxHeight: MediaQuery.sizeOf(context).height * 0.85),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(l10n.plugin_pixiv_saucenao_title, style: theme.textTheme.titleLarge),
              const SizedBox(height: 8),
              Text(
                l10n.plugin_pixiv_saucenao_privacy,
                style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
              ),
              const SizedBox(height: 16),
              Flexible(
                child: ScopedBuilder<PixivSauceNaoStore, List<PixivSauceMatch>?>(
                  store: _store,
                  onLoading: (context) => _searching(context),
                  onError: (context, error) => _failed(context, error),
                  onState: (context, matches) => matches == null ? _pickButton(l10n, first: true) : _results(matches),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _pickButton(L10n l10n, {required bool first}) => Center(
    child: FilledButton.icon(
      key: const ValueKey('pixiv-saucenao-pick'),
      onPressed: _store.pickAndSearch,
      icon: const Icon(Icons.add_photo_alternate_outlined),
      label: Text(first ? l10n.plugin_pixiv_saucenao_pick : l10n.plugin_pixiv_saucenao_another),
    ),
  );

  Widget _searching(BuildContext context) => Row(
    mainAxisAlignment: MainAxisAlignment.center,
    children: [
      const SizedBox.square(dimension: 20, child: CircularProgressIndicator(strokeWidth: 2)),
      const SizedBox(width: 12),
      Flexible(child: Text(L10n.of(context).plugin_pixiv_saucenao_searching)),
    ],
  );

  Widget _failed(BuildContext context, Object? error) {
    final l10n = L10n.of(context);
    final reason = error is PixivException ? pixivErrorMessage(l10n, error) : l10n.plugin_pixiv_saucenao_failed;
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(reason, textAlign: TextAlign.center),
        const SizedBox(height: 12),
        Wrap(
          alignment: WrapAlignment.center,
          spacing: 8,
          children: [
            TextButton(onPressed: _store.retry, child: Text(l10n.retry)),
            _pickButton(l10n, first: false),
          ],
        ),
      ],
    );
  }

  Widget _results(List<PixivSauceMatch> matches) {
    final l10n = L10n.of(context);
    if (matches.isEmpty) {
      return Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(l10n.plugin_pixiv_saucenao_empty, textAlign: TextAlign.center),
          const SizedBox(height: 12),
          _pickButton(l10n, first: false),
        ],
      );
    }
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Flexible(
          child: ListView.builder(
            shrinkWrap: true,
            itemCount: matches.length,
            itemBuilder: (context, index) =>
                _PixivSauceRow(key: ValueKey('pixiv-saucenao-${matches[index].illustId}'), match: matches[index]),
          ),
        ),
        const SizedBox(height: 12),
        _pickButton(l10n, first: false),
      ],
    );
  }
}

/// A match: its thumbnail once Pixiv describes the work, how alike SauceNAO
/// found it, title and artist. A tap opens the work.
class _PixivSauceRow extends StatefulWidget {
  final PixivSauceMatch match;

  const _PixivSauceRow({super.key, required this.match});

  @override
  State<_PixivSauceRow> createState() => _PixivSauceRowState();
}

class _PixivSauceRowState extends State<_PixivSauceRow> {
  /// The match's work as Pixiv describes it, fetched when the row first shows.
  late final PixivFetchStore<PixivIllust?> _preview;

  @override
  void initState() {
    super.initState();
    final client = context.read<PixivClient>();
    _preview = PixivFetchStore<PixivIllust?>(() => client.illustDetail(widget.match.illustId), null)..load();
  }

  @override
  void dispose() {
    _preview.destroy();
    super.dispose();
  }

  void _open() {
    final illust = _preview.state;
    if (illust != null) {
      openPixivIllust(context, illust);
    } else {
      openPixivLinkOrSay(context, PixivLinkRef.artwork(widget.match.illustId));
    }
  }

  @override
  Widget build(BuildContext context) => ScopedBuilder<PixivFetchStore<PixivIllust?>, PixivIllust?>(
    store: _preview,
    onLoading: (context) => _row(context, null),
    onError: (context, error) => _row(context, null, error: error),
    onState: (context, illust) => _row(context, illust),
  );

  Widget _row(BuildContext context, PixivIllust? illust, {Object? error}) {
    final l10n = L10n.of(context);
    final match = widget.match;
    final authBroken =
        error is PixivException &&
        (error.kind == PixivErrorKind.unauthorized || error.kind == PixivErrorKind.notConfigured);
    final title = match.title.isNotEmpty ? match.title : (illust?.title ?? '#${match.illustId}');
    final details = [
      if (match.similarity case final similarity?) l10n.plugin_pixiv_saucenao_similarity(_percent(context, similarity)),
      if ((match.author.isNotEmpty ? match.author : illust?.userName ?? '') case final author when author.isNotEmpty)
        author,
    ];
    return ListTile(
      contentPadding: EdgeInsets.zero,
      leading: _thumb(context, illust),
      title: Text(title, maxLines: 2, overflow: TextOverflow.ellipsis),
      subtitle: Text(
        authBroken ? l10n.plugin_pixiv_saucenao_preview_unauthorized : details.join(' · '),
        style: authBroken ? TextStyle(color: Theme.of(context).colorScheme.error) : null,
      ),
      onTap: _open,
    );
  }

  String _percent(BuildContext context, double similarity) => NumberFormat.decimalPercentPattern(
    locale: numberFormatLocale(context),
    decimalDigits: 1,
  ).format(similarity / 100);

  /// The work's picture, unless the reader's filters or mutes keep it out of their feeds.
  Widget _thumb(BuildContext context, PixivIllust? illust) {
    final client = context.read<PixivClient>();
    final shown =
        illust != null &&
        !context.read<PixivMuteStore>().isMuted(illust) &&
        pixivContentAllowed(illust, includeR18: client.showR18, includeAi: !client.hideAi);
    return ClipRRect(
      borderRadius: BorderRadius.circular(8),
      child: SizedBox.square(
        dimension: 56,
        child: shown
            ? PixivNetworkImage(url: illust.thumbnailUrl, fit: BoxFit.cover)
            : ColoredBox(
                color: Theme.of(context).colorScheme.surfaceContainerHighest,
                child: const Icon(Icons.image_outlined),
              ),
      ),
    );
  }
}
