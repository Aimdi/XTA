import 'dart:async';

import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:visibility_detector/visibility_detector.dart';
import 'package:wakelock_plus/wakelock_plus.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/ehviewer/eh_client.dart';
import 'package:xta/plugins/ehviewer/eh_models.dart';
import 'package:xta/plugins/ehviewer/eh_page_resolver.dart';
import 'package:xta/plugins/ehviewer/eh_reader_chrome.dart';
import 'package:xta/plugins/ehviewer/eh_reader_page.dart';
import 'package:xta/plugins/ehviewer/eh_reader_sheets.dart';
import 'package:xta/plugins/ehviewer/eh_reader_store.dart';
import 'package:xta/plugins/ehviewer/eh_store.dart';
import 'package:xta/plugins/ehviewer/eh_ui.dart';
import 'package:xta/plugins/plugin_post_media.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/ui/motion.dart';
import 'package:xta/utils/urls.dart';

class EhReaderScreen extends StatefulWidget {
  final EhGallery gallery;

  /// 1-based page to open at.
  final int initialPage;

  /// Page tokens already known from the gallery's preview sheets.
  final List<EhPreview> previews;

  const EhReaderScreen({
    super.key,
    required this.gallery,
    required this.initialPage,
    this.previews = const [],
  });

  @override
  State<EhReaderScreen> createState() => _EhReaderScreenState();
}

class _EhReaderScreenState extends State<EhReaderScreen> {
  static const _anchorKey = ValueKey('eh-reader-anchor');

  late final EhClient _client = context.read<EhClient>();
  late final EhHistoryStore _history = context.read<EhHistoryStore>();
  late final EhPageResolver _resolver;
  late final EhReaderStore _store;
  late ExtendedPageController _pages;
  late final bool _keepAwake;

  @override
  void initState() {
    super.initState();
    final prefs = PrefService.of(context, listen: false);
    final total = ehReaderTotal(
      widget.gallery,
      widget.previews,
      widget.initialPage,
    );
    _resolver = EhPageResolver(
      client: _client,
      gallery: widget.gallery,
      total: total,
      previews: widget.previews,
    );
    _store = EhReaderStore(
      total: total,
      initialPage: widget.initialPage,
      prefs: prefs,
      onPageSettled: _settled,
    );
    _pages = ExtendedPageController(initialPage: _store.state.page - 1);
    _keepAwake = prefs.get<bool>(optionPluginEhKeepScreenOn) != false;
    if (_keepAwake) unawaited(_setWakelock(true));
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _settled(_store.state.page);
    });
  }

  @override
  void dispose() {
    if (_keepAwake) unawaited(_setWakelock(false));
    if (!_store.state.chromeVisible) {
      unawaited(_applySystemUi(chromeVisible: true));
    }
    _pages.dispose();
    _store.destroy();
    _resolver.destroy();
    super.dispose();
  }

  /// Remembers the page and fetches the pages around it.
  void _settled(int page) {
    unawaited(_history.remember(widget.gallery, page: page));
    for (final next in ehPreloadPages(page, _resolver.total)) {
      unawaited(_resolver.resolve(next).then(_precache));
    }
  }

  Future<void> _precache(EhImagePage? image) async {
    if (image == null || !mounted) return;
    final images = _imagesFor(_store.state.mode);
    await precacheImage(
      images.provider(images.urlsOf(image).first),
      context,
      onError: (_, _) {},
    );
  }

  EhReaderImages _imagesFor(EhReadingMode mode) => EhReaderImages(
    headers: _client.imageHeaders,
    signedIn: _client.hasCookies,
    cacheWidth: mode.paged
        ? null
        : (MediaQuery.sizeOf(context).width *
                  MediaQuery.devicePixelRatioOf(context))
              .ceil(),
  );

  void _toggleChrome() {
    _store.toggleChrome();
    unawaited(_applySystemUi(chromeVisible: _store.state.chromeVisible));
  }

  void _onTap(TapUpDetails details) {
    final width = MediaQuery.sizeOf(context).width;
    final mode = _store.state.mode;
    switch (ehTapAction(details.localPosition.dx, width, mode)) {
      case EhTapAction.toggleChrome:
        _toggleChrome();
      case EhTapAction.next:
        _store.next();
        _showPage(animate: true);
      case EhTapAction.previous:
        _store.previous();
        _showPage(animate: true);
    }
  }

  void _jumpTo(int page) {
    _store.goTo(page);
    _showPage();
  }

  /// Moves the pager to the store's page. The vertical list follows the
  /// store's anchor instead, so it needs nothing here.
  void _showPage({bool animate = false}) {
    if (!_store.state.mode.paged || !_pages.hasClients) return;
    final index = _store.state.page - 1;
    if (!animate || xtaReduceMotion(context)) {
      _pages.jumpToPage(index);
      return;
    }
    unawaited(
      _pages.animateToPage(
        index,
        duration: kXtaMotionNavigation,
        curve: Curves.easeOutCubic,
      ),
    );
  }

  Future<void> _jumpDialog() async {
    final l10n = L10n.of(context);
    final total = _resolver.total;
    final page = await showEhJumpDialog(
      context,
      current: _store.state.page,
      total: total,
    );
    if (page == null || !mounted) return;
    if (page < 1 || page > total) {
      showSnackBar(
        context,
        icon: '⚠️',
        message: l10n.plugin_eh_jump_invalid(total),
      );
      return;
    }
    _jumpTo(page);
  }

  Future<void> _pickMode() async {
    final mode = await showEhReadingModeSheet(context, _store.state.mode);
    if (mode == null || !mounted || mode == _store.state.mode) return;
    // The pager in place keeps its old controller until the rebuild lands.
    final previous = _pages;
    _pages = ExtendedPageController(initialPage: _store.state.page - 1);
    WidgetsBinding.instance.addPostFrameCallback((_) => previous.dispose());
    await _store.setMode(mode);
  }

  Future<void> _pageActions(int page) async {
    final image = _resolver.imageOf(page);
    final original = _client.hasCookies
        ? image?.originalImageUrl?.trim()
        : null;
    final action = await showEhPageActions(
      context,
      page: page,
      total: _resolver.total,
      available: {
        EhPageAction.reload,
        if (original != null && original.isNotEmpty) EhPageAction.openOriginal,
        if (image != null) EhPageAction.save,
        if (_resolver.previewOf(page) != null) EhPageAction.copyLink,
      },
    );
    if (action == null || !mounted) return;
    await _runPageAction(action, page, image: image, original: original);
  }

  Future<void> _runPageAction(
    EhPageAction action,
    int page, {
    EhImagePage? image,
    String? original,
  }) async {
    switch (action) {
      case EhPageAction.reload:
        await _resolver.reload(page);
      case EhPageAction.openOriginal:
        await openUri(context, original!);
      case EhPageAction.save:
        await downloadPluginMediaItem(
          context,
          PluginMediaItem(url: image!.imageUrl),
          sourceName: '$pluginIdEhViewer-${widget.gallery.gid}',
        );
      case EhPageAction.copyLink:
        await _copyPageLink(page);
    }
  }

  Future<void> _copyPageLink(int page) async {
    final preview = _resolver.previewOf(page);
    if (preview == null) return;
    final message = L10n.of(context).plugin_eh_link_copied;
    final link = widget.gallery.pageUri(
      _client.host,
      pageToken: preview.pageToken,
      page: page,
    );
    await Clipboard.setData(ClipboardData(text: link.toString()));
    if (mounted) showSnackBar(context, icon: '📋', message: message);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: Stack(
        children: [
          Positioned.fill(
            child: ScopedBuilder<EhReaderStore, EhReaderState>(
              store: _store,
              distinct: (state) => [state.mode, state.anchor],
              onState: (context, state) => GestureDetector(
                behavior: HitTestBehavior.opaque,
                onTapUp: _onTap,
                child: state.mode.paged
                    ? _paged(state.mode)
                    : _vertical(state.anchor),
              ),
            ),
          ),
          Positioned.fill(
            child: ScopedBuilder<EhReaderStore, EhReaderState>(
              store: _store,
              distinct: (state) => [
                state.chromeVisible,
                state.shownPage,
                state.mode,
              ],
              onState: (context, state) => EhReaderChrome(
                title: widget.gallery.titleFor(
                  preferJapanese: ehPreferJapaneseOf(context),
                ),
                state: state,
                total: _resolver.total,
                onScrub: _store.scrub,
                onJump: _jumpTo,
                onCounter: _jumpDialog,
                onPickMode: _pickMode,
              ),
            ),
          ),
        ],
      ),
    );
  }

  /// Laid out left to right whatever the app language, so the reading mode
  /// alone decides which way the pages turn.
  Widget _paged(EhReadingMode mode) {
    final ambient = Directionality.of(context);
    final images = _imagesFor(mode);
    return Directionality(
      textDirection: TextDirection.ltr,
      child: ExtendedImageGesturePageView.builder(
        controller: _pages,
        reverse: mode.reversed,
        itemCount: _resolver.total,
        onPageChanged: (index) => _store.observe(index + 1),
        itemBuilder: (context, index) => Directionality(
          textDirection: ambient,
          child: _page(index + 1, images, vertical: false),
        ),
      ),
    );
  }

  /// Pages grow up from the anchor and down from it, so a jump lands at once
  /// and pages loading above never push the one being read.
  Widget _vertical(int anchor) {
    final images = _imagesFor(EhReadingMode.vertical);
    return CustomScrollView(
      key: ValueKey('eh-reader-vertical-$anchor'),
      center: _anchorKey,
      slivers: [
        SliverList.builder(
          itemCount: anchor - 1,
          itemBuilder: (context, i) =>
              _verticalPage(anchor - 1 - i, anchor, images),
        ),
        SliverList.builder(
          key: _anchorKey,
          itemCount: _resolver.total - anchor + 1,
          itemBuilder: (context, i) =>
              _verticalPage(anchor + i, anchor, images),
        ),
      ],
    );
  }

  Widget _verticalPage(int page, int anchor, EhReaderImages images) =>
      VisibilityDetector(
        key: ValueKey('eh-reader-visible-$anchor-$page'),
        onVisibilityChanged: (info) {
          if (!mounted) return;
          _store.pageVisibility(
            page,
            anchor: anchor,
            visible: info.visibleBounds.height,
            height: info.size.height,
          );
        },
        child: _page(page, images, vertical: true),
      );

  Widget _page(int page, EhReaderImages images, {required bool vertical}) {
    return GestureDetector(
      key: ValueKey('eh-reader-page-$page'),
      behavior: HitTestBehavior.opaque,
      onLongPress: () => _pageActions(page),
      child: EhReaderPage(
        resolver: _resolver,
        images: images,
        page: page,
        vertical: vertical,
      ),
    );
  }
}

Future<void> _applySystemUi({required bool chromeVisible}) async {
  try {
    await SystemChrome.setEnabledSystemUIMode(
      chromeVisible ? SystemUiMode.edgeToEdge : SystemUiMode.immersiveSticky,
    );
  } catch (_) {}
}

Future<void> _setWakelock(bool enable) async {
  try {
    await WakelockPlus.toggle(enable: enable);
  } catch (_) {}
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
