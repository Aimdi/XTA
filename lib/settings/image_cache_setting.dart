import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/utils/byte_size.dart';
import 'package:xta/utils/image_disk_cache.dart';

/// How much the image disk cache holds, and a way to empty it.
class ImageCacheSetting extends StatefulWidget {
  /// The cache to show; the app's own when null.
  final ImageDiskCacheStore? store;

  const ImageCacheSetting({super.key, this.store});

  @override
  State<ImageCacheSetting> createState() => _ImageCacheSettingState();
}

class _ImageCacheSettingState extends State<ImageCacheSetting> {
  late final _store = widget.store ?? ImageDiskCacheStore();

  @override
  void initState() {
    super.initState();
    unawaited(_store.load());
  }

  @override
  void dispose() {
    if (widget.store == null) unawaited(_store.destroy());
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return ScopedBuilder<ImageDiskCacheStore, int?>(
      store: _store,
      onState: (context, bytes) => PrefLabel(
        leading: const Icon(Icons.image_outlined),
        title: Text(l10n.image_cache_title),
        subtitle: bytes == null
            ? null
            : Text(l10n.image_cache_size(formatByteSize(bytes), imageDiskCacheMaxAge.inDays)),
        onTap: () => _clear(context),
      ),
    );
  }

  Future<void> _clear(BuildContext context) async {
    final l10n = L10n.of(context);
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(l10n.image_cache_clear_question),
        content: Text(l10n.image_cache_clear_description),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: Text(l10n.cancel)),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: Text(l10n.image_cache_clear)),
        ],
      ),
    );
    if (confirmed == true) await _store.clear();
  }
}
