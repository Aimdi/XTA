import 'package:xta/utils/reader_value_store.dart';
import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/generated/l10n.dart';

class LinkStack extends StatefulWidget {
  final List<WidgetBuilder> posts;
  const LinkStack({super.key, required this.posts});
  @override
  State<LinkStack> createState() => _LinkStackState();
}

class _LinkStackState extends State<LinkStack> {
  final _expanded = ReaderValueStore<bool>(false);
  @override
  void dispose() {
    _expanded.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => ScopedBuilder<Store<bool>, bool>(
    store: _expanded,
    onState: (context, expanded) => Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        widget.posts.first(context),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: TextButton.icon(
            onPressed: () => _expanded.update(!expanded),
            icon: Icon(expanded ? Icons.unfold_less : Icons.link),
            label: Text(
              expanded
                  ? L10n.of(context).collapse_reposts
                  : L10n.of(context).reader_link_posts(widget.posts.length - 1),
            ),
          ),
        ),
        if (expanded)
          for (final post in widget.posts.skip(1)) post(context),
      ],
    ),
  );
}
