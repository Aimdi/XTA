import 'dart:async';

import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:wakelock_plus/wakelock_plus.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/ehviewer/eh_client.dart';
import 'package:xta/plugins/ehviewer/eh_errors.dart';
import 'package:xta/plugins/ehviewer/eh_grid.dart';
import 'package:xta/plugins/ehviewer/eh_models.dart';
import 'package:xta/plugins/ehviewer/eh_parse.dart';
import 'package:xta/plugins/ehviewer/eh_store.dart';
import 'package:xta/ui/errors.dart';

class EhReaderScreen extends StatefulWidget {
  final EhGallery gallery;
  final EhPreview initialPreview;
  final List<EhPreview> previews;

  const EhReaderScreen({
    super.key,
    required this.gallery,
    required this.initialPreview,
    required this.previews,
  });

  @override
  State<EhReaderScreen> createState() => _EhReaderScreenState();
}

class _EhReaderScreenState extends State<EhReaderScreen> {
  late EhPreview _current;
  late List<EhPreview> _previews;
  EhImagePage? _page;
  Object? _error;
  var _loading = true;
  var _jumping = false;
  var _keepAwake = false;

  @override
  void initState() {
    super.initState();
    _current = widget.initialPreview;
    _previews = List.of(widget.previews);
    _keepAwake =
        PrefService.of(
          context,
          listen: false,
        ).get<bool>(optionPluginEhKeepScreenOn) !=
        false;
    if (_keepAwake) unawaited(WakelockPlus.enable());
    _load();
  }

  @override
  void dispose() {
    if (_keepAwake) unawaited(WakelockPlus.disable());
    super.dispose();
  }

  Future<void> _load({String? reloadKey}) async {
    setState(() {
      _loading = true;
      _error = null;
    });
    final client = context.read<EhClient>();
    final history = context.read<EhHistoryStore>();
    try {
      final page = await client.imagePage(
        gid: widget.gallery.gid,
        pageToken: _current.pageToken,
        page: _current.page,
        reloadKey: reloadKey,
      );
      if (!mounted) return;
      setState(() {
        _page = page;
        _loading = false;
      });
      unawaited(history.remember(widget.gallery, page: _current.page));
      _prefetchNext(page);
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e;
        _loading = false;
      });
    }
  }

  Future<void> _reloadBroken() async {
    final key = _page?.reloadKey;
    if (key == null || key.isEmpty) {
      await _load();
      return;
    }
    await _load(reloadKey: key);
  }

  void _prefetchNext(EhImagePage page) {
    final link = parseEhPageLink(page.nextPageUrl);
    if (link == null) return;
    final client = context.read<EhClient>();
    unawaited(() async {
      try {
        final next = await client.imagePage(
          gid: widget.gallery.gid,
          pageToken: link.pageToken,
          page: link.page,
        );
        if (!mounted) return;
        await precacheImage(
          ExtendedNetworkImageProvider(
            next.displayUrl(signedIn: client.hasCookies),
            cache: true,
            headers: client.imageHeaders,
          ),
          context,
        );
      } catch (_) {}
    }());
  }

  Future<void> _go(EhPreview preview) async {
    _remember(preview);
    setState(() => _current = preview);
    await _load();
  }

  void _remember(EhPreview preview) {
    if (_previews.any((p) => p.page == preview.page)) return;
    _previews = [..._previews, preview]
      ..sort((a, b) => a.page.compareTo(b.page));
  }

  EhPreview? get _prevFromList {
    final index = _previews.indexWhere((p) => p.page == _current.page);
    if (index <= 0) return null;
    return _previews[index - 1];
  }

  EhPreview? get _nextFromList {
    final index = _previews.indexWhere((p) => p.page == _current.page);
    if (index < 0 || index >= _previews.length - 1) return null;
    return _previews[index + 1];
  }

  bool get _canPrev =>
      _prevFromList != null || (_page?.prevPageUrl?.isNotEmpty ?? false);

  bool get _canNext =>
      _nextFromList != null || (_page?.nextPageUrl?.isNotEmpty ?? false);

  Future<void> _goPrev() async {
    final listed = _prevFromList;
    if (listed != null) {
      await _go(listed);
      return;
    }
    final link = parseEhPageLink(_page?.prevPageUrl);
    if (link == null) return;
    await _go(EhPreview(pageToken: link.pageToken, page: link.page));
  }

  Future<void> _goNext() async {
    final listed = _nextFromList;
    if (listed != null) {
      await _go(listed);
      return;
    }
    final link = parseEhPageLink(_page?.nextPageUrl);
    if (link == null) return;
    await _go(EhPreview(pageToken: link.pageToken, page: link.page));
  }

  Future<void> _jumpDialog() async {
    final total = widget.gallery.pageCount ?? _previews.length;
    final page = await showEhJumpDialog(
      context,
      current: _current.page,
      total: total,
    );
    if (page == null || !mounted) return;
    await _jumpTo(page);
  }

  Future<void> _jumpTo(int page) async {
    final l10n = L10n.of(context);
    final total = widget.gallery.pageCount ?? _previews.length;
    if (page < 1 || page > total) {
      showSnackBar(
        context,
        icon: '⚠️',
        message: l10n.plugin_eh_jump_invalid(total),
      );
      return;
    }
    final existing = _previews.where((p) => p.page == page).firstOrNull;
    if (existing != null) {
      await _go(existing);
      return;
    }
    setState(() => _jumping = true);
    try {
      final preview = await context.read<EhClient>().previewForPage(
        gid: widget.gallery.gid,
        token: widget.gallery.token,
        page: page,
      );
      if (!mounted) return;
      if (preview == null) {
        showSnackBar(
          context,
          icon: '⚠️',
          message: l10n.plugin_eh_jump_invalid(total),
        );
        return;
      }
      await _go(preview);
    } catch (e) {
      if (!mounted) return;
      showSnackBar(context, icon: '⚠️', message: ehErrorMessage(l10n, e));
    } finally {
      if (mounted) setState(() => _jumping = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final total = widget.gallery.pageCount ?? _previews.length;

    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        backgroundColor: Colors.black,
        foregroundColor: Colors.white,
        title: Text(l10n.plugin_eh_page_of(_current.page, total)),
        actions: [
          IconButton(
            tooltip: l10n.plugin_eh_reload_image,
            onPressed: _loading ? null : _reloadBroken,
            icon: const Icon(Icons.refresh),
          ),
          IconButton(
            tooltip: l10n.plugin_eh_jump_to_page,
            onPressed: _loading || _jumping ? null : _jumpDialog,
            icon: _jumping
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.shortcut),
          ),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
          ? FullPageErrorWidget(
              error: _error,
              stackTrace: null,
              prefix: ehErrorMessage(l10n, _error),
              onRetry: _load,
            )
          : GestureDetector(
              onTapUp: (details) {
                final width = MediaQuery.sizeOf(context).width;
                if (details.localPosition.dx < width / 3) {
                  if (_canPrev) _goPrev();
                } else if (details.localPosition.dx > width * 2 / 3) {
                  if (_canNext) _goNext();
                }
              },
              onLongPress: _reloadBroken,
              child: InteractiveViewer(
                child: Center(
                  child: EhNetworkImage(
                    url: _page!.displayUrl(
                      signedIn: context.read<EhClient>().hasCookies,
                    ),
                    fallbackUrl: _page!.imageUrl,
                    fit: BoxFit.contain,
                    filterQuality: FilterQuality.high,
                  ),
                ),
              ),
            ),
      bottomNavigationBar: SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (total > 1)
              Slider(
                min: 1,
                max: total.toDouble(),
                divisions: total - 1,
                value: _current.page.clamp(1, total).toDouble(),
                onChanged: _loading || _jumping
                    ? null
                    : (value) => _jumpTo(value.round()),
              ),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: [
                IconButton(
                  color: Colors.white,
                  onPressed: !_canPrev || _loading ? null : _goPrev,
                  icon: const Icon(Icons.chevron_left),
                ),
                TextButton(
                  onPressed: _loading || _jumping ? null : _jumpDialog,
                  child: Text(
                    l10n.plugin_eh_page_of(_current.page, total),
                    style: const TextStyle(color: Colors.white),
                  ),
                ),
                IconButton(
                  color: Colors.white,
                  onPressed: !_canNext || _loading ? null : _goNext,
                  icon: const Icon(Icons.chevron_right),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

/// Owns the field so cancel does not dispose it while the route is animating.
Future<int?> showEhJumpDialog(
  BuildContext context, {
  required int current,
  required int total,
}) {
  return showDialog<int>(
    context: context,
    builder: (_) => _EhJumpDialog(current: current, total: total),
  );
}

class _EhJumpDialog extends StatefulWidget {
  final int current;
  final int total;

  const _EhJumpDialog({required this.current, required this.total});

  @override
  State<_EhJumpDialog> createState() => _EhJumpDialogState();
}

class _EhJumpDialogState extends State<_EhJumpDialog> {
  late final TextEditingController _controller;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: '${widget.current}');
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return AlertDialog(
      title: Text(l10n.plugin_eh_jump_to_page),
      content: TextField(
        controller: _controller,
        autofocus: true,
        keyboardType: TextInputType.number,
        inputFormatters: [FilteringTextInputFormatter.digitsOnly],
        decoration: InputDecoration(
          hintText: l10n.plugin_eh_jump_hint(widget.total),
        ),
        onSubmitted: (value) {
          final n = int.tryParse(value);
          Navigator.pop(context, n);
        },
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: Text(l10n.cancel),
        ),
        FilledButton(
          onPressed: () =>
              Navigator.pop(context, int.tryParse(_controller.text)),
          child: Text(l10n.plugin_eh_jump_go),
        ),
      ],
    );
  }
}
