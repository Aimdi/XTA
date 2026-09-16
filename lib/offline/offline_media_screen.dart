import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:media_kit/media_kit.dart';
import 'package:media_kit_video/media_kit_video.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/offline/offline_store.dart';

class OfflineMediaScreen extends StatefulWidget {
  final OfflineEntry entry;
  final OfflineStore store;
  const OfflineMediaScreen({super.key, required this.entry, required this.store});

  @override
  State<OfflineMediaScreen> createState() => _OfflineMediaScreenState();
}

class _OfflinePageStore extends Store<int> {
  _OfflinePageStore() : super(0);
  void select(int index) => update(index);
}

class _OfflineMediaScreenState extends State<OfflineMediaScreen> {
  final _page = _OfflinePageStore();
  @override
  void dispose() {
    _page.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: Text(widget.entry.title, maxLines: 1, overflow: TextOverflow.ellipsis)),
    body: SafeArea(
      top: false,
      child: ScopedBuilder<_OfflinePageStore, int>(
        store: _page,
        onState: (context, page) => PageView.builder(
          itemCount: widget.entry.files.length,
          onPageChanged: _page.select,
          itemBuilder: (context, index) => Semantics(
            label: L10n.of(context).offline_media_label(index + 1),
            child: _OfflineMediaPage(
              key: ValueKey(widget.entry.files[index].name),
              active: index == page,
              file: widget.entry.files[index],
              entry: widget.entry,
              store: widget.store,
            ),
          ),
        ),
      ),
    ),
  );
}

class _OfflineMediaPage extends StatefulWidget {
  final OfflineFile file;
  final OfflineEntry entry;
  final OfflineStore store;
  final bool active;
  const _OfflineMediaPage({
    super.key,
    required this.file,
    required this.entry,
    required this.store,
    required this.active,
  });
  @override
  State<_OfflineMediaPage> createState() => _OfflineMediaPageState();
}

class _OfflineMediaPageState extends State<_OfflineMediaPage> with WidgetsBindingObserver {
  late final Future<File?> _file;
  Player? _player;
  VideoController? _video;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    if (widget.file.video) {
      MediaKit.ensureInitialized();
      _player = Player();
      _video = VideoController(_player!);
    }
    _file = _open();
  }

  Future<File?> _open() async {
    final file = await widget.store.mediaFile(widget.entry, widget.file);
    if (!mounted || file == null) return null;
    // Explicit play prevents neighboring pages from starting hidden video.
    await _player?.open(Media(file.path), play: false);
    return file;
  }

  @override
  void didUpdateWidget(covariant _OfflineMediaPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!widget.active) _player?.pause();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state != AppLifecycleState.resumed) _player?.pause();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    unawaited(_player?.dispose());
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => FutureBuilder<File?>(
    future: _file,
    builder: (context, snapshot) {
      if (snapshot.connectionState != ConnectionState.done) return const Center(child: CircularProgressIndicator());
      final file = snapshot.data;
      if (file == null || snapshot.hasError) return Center(child: Text(L10n.of(context).offline_unavailable));
      if (_video != null) return Video(controller: _video!);
      return InteractiveViewer(
        minScale: 1,
        maxScale: 5,
        child: Center(
          child: Image.file(
            file,
            fit: BoxFit.contain,
            errorBuilder: (context, _, _) => Text(L10n.of(context).offline_unavailable),
          ),
        ),
      );
    },
  );
}
