import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/ehviewer/eh_client.dart';
import 'package:xta/plugins/ehviewer/eh_errors.dart';
import 'package:xta/plugins/ehviewer/eh_grid.dart';
import 'package:xta/plugins/ehviewer/eh_models.dart';
import 'package:xta/plugins/ehviewer/eh_parse.dart';
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

  @override
  void initState() {
    super.initState();
    _current = widget.initialPreview;
    _previews = List.of(widget.previews);
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final page = await context.read<EhClient>().imagePage(
        gid: widget.gallery.gid,
        pageToken: _current.pageToken,
        page: _current.page,
      );
      if (!mounted) return;
      setState(() {
        _page = page;
        _loading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e;
        _loading = false;
      });
    }
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
              child: InteractiveViewer(
                child: Center(
                  child: EhNetworkImage(
                    url: _page!.imageUrl,
                    fit: BoxFit.contain,
                  ),
                ),
              ),
            ),
      bottomNavigationBar: SafeArea(
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceEvenly,
          children: [
            IconButton(
              color: Colors.white,
              onPressed: !_canPrev || _loading ? null : _goPrev,
              icon: const Icon(Icons.chevron_left),
            ),
            Text(
              l10n.plugin_eh_page_of(_current.page, total),
              style: const TextStyle(color: Colors.white),
            ),
            IconButton(
              color: Colors.white,
              onPressed: !_canNext || _loading ? null : _goNext,
              icon: const Icon(Icons.chevron_right),
            ),
          ],
        ),
      ),
    );
  }
}
