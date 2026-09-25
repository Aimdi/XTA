import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/home/alt_microblogging.dart';

class AltMicrobloggingSetting extends StatelessWidget {
  final AltMicrobloggingStore store;

  const AltMicrobloggingSetting({super.key, required this.store});

  @override
  Widget build(BuildContext context) => ScopedBuilder<AltMicrobloggingStore, bool>(
    store: store,
    onState: (context, grouped) => SwitchListTile.adaptive(
      key: const ValueKey('plugin-store-group-microblogging'),
      title: Text(L10n.of(context).alt_microblogging_group),
      subtitle: Text(L10n.of(context).alt_microblogging_group_detail),
      value: grouped,
      onChanged: store.setGrouped,
    ),
  );
}
