import 'package:extended_image/extended_image.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/substack/substack_blocks.dart';
import 'package:xta/plugins/substack/substack_models.dart';

/// A post read without a web view: its blocks in a lazy list that can be
/// selected and copied (plan item 7, behind a setting while in preview).
class SubstackNativeArticle extends StatelessWidget {
  final SubstackPost post;
  final List<SubstackBlock> blocks;
  final ValueChanged<String> onLink;
  final double fontSize;
  final double lineHeight;

  const SubstackNativeArticle({
    super.key,
    required this.post,
    required this.blocks,
    required this.onLink,
    this.fontSize = 18,
    this.lineHeight = 1.7,
  });

  @override
  Widget build(BuildContext context) {
    final body = Theme.of(context).textTheme.bodyLarge!.copyWith(fontSize: fontSize, height: lineHeight);
    final scope = _Scope(style: body, onLink: onLink, postUrl: post.canonicalUrl);
    return SelectionArea(
      child: CustomScrollView(
        slivers: [
          SliverPadding(
            padding: const EdgeInsetsDirectional.fromSTEB(20, 16, 20, 48),
            sliver: SliverList.builder(
              itemCount: blocks.length + 2,
              itemBuilder: (context, index) {
                if (index == 0) return _Header(post: post);
                if (index == blocks.length + 1) return _Footer(post: post, scope: scope);
                return Padding(
                  padding: const EdgeInsets.only(bottom: 16),
                  child: _BlockView(block: blocks[index - 1], scope: scope),
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}

class _Scope {
  final TextStyle style;
  final ValueChanged<String> onLink;
  final String? postUrl;

  const _Scope({required this.style, required this.onLink, this.postUrl});
}

class _Header extends StatelessWidget {
  final SubstackPost post;

  const _Header({required this.post});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final meta = [post.publicationName, ?post.authorName].where((part) => part.trim().isNotEmpty).join(' · ');
    return Padding(
      padding: const EdgeInsets.only(bottom: 24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Semantics(
            header: true,
            child: Text(post.title, style: theme.textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w800)),
          ),
          if (meta.isNotEmpty) ...[
            const SizedBox(height: 8),
            Text(meta, style: theme.textTheme.bodyMedium?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
          ],
          if (post.excerpt case final excerpt?) ...[
            const SizedBox(height: 12),
            Text(excerpt, style: theme.textTheme.titleMedium?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
          ],
        ],
      ),
    );
  }
}

/// Says where the free part of a paid post stops.
class _Footer extends StatelessWidget {
  final SubstackPost post;
  final _Scope scope;

  const _Footer({required this.post, required this.scope});

  @override
  Widget build(BuildContext context) {
    if (!post.isPaywalled) return const SizedBox.shrink();
    final l10n = L10n.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Divider(height: 32),
        Text(l10n.plugin_substack_preview_ends, style: scope.style.copyWith(fontStyle: FontStyle.italic)),
        if (post.canonicalUrl case final url?)
          TextButton(onPressed: () => scope.onLink(url), child: Text(l10n.plugin_substack_continue_on_site)),
      ],
    );
  }
}

/// One block, drawn the way its kind reads.
class _BlockView extends StatelessWidget {
  final SubstackBlock block;
  final _Scope scope;

  const _BlockView({required this.block, required this.scope});

  @override
  Widget build(BuildContext context) => switch (block) {
    SubstackTextBlock text => _text(context, text),
    SubstackImageBlock image => _SubstackImage(image: image, scope: scope),
    SubstackEmbedBlock(:final host, :final src) => OutlinedButton.icon(
      onPressed: () => scope.onLink(src),
      icon: const Icon(Icons.open_in_new),
      label: Text([L10n.of(context).plugin_substack_load_embed, if (host.isNotEmpty) host].join(' · ')),
    ),
    SubstackWebOnlyBlock() => _webOnly(context),
    SubstackDividerBlock() => const Divider(),
    SubstackTableBlock(:final rows) => _table(context, rows),
  };

  Widget _text(BuildContext context, SubstackTextBlock block) {
    final theme = Theme.of(context);
    final runs = _SubstackRuns(runs: block.runs, scope: scope);
    return switch (block.kind) {
      SubstackTextKind.paragraph => runs,
      SubstackTextKind.heading => Semantics(
        header: true,
        child: _SubstackRuns(
          runs: block.runs,
          scope: scope,
          style: scope.style.copyWith(
            fontSize: (scope.style.fontSize ?? 18) * (block.level <= 2 ? 1.35 : 1.15),
            fontWeight: FontWeight.w700,
            height: 1.35,
          ),
        ),
      ),
      SubstackTextKind.quote => Container(
        padding: const EdgeInsetsDirectional.only(start: 16),
        decoration: BoxDecoration(
          border: BorderDirectional(start: BorderSide(color: theme.colorScheme.primary, width: 3)),
        ),
        child: _SubstackRuns(
          runs: block.runs,
          scope: scope,
          style: scope.style.copyWith(fontStyle: FontStyle.italic),
        ),
      ),
      SubstackTextKind.listItem || SubstackTextKind.footnote => Padding(
        padding: EdgeInsetsDirectional.only(start: 20.0 * block.level),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            SizedBox(width: 28, child: Text(block.marker ?? '', style: scope.style)),
            Expanded(child: runs),
          ],
        ),
      ),
      SubstackTextKind.code => Container(
        width: double.infinity,
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: theme.colorScheme.surfaceContainerHighest,
          borderRadius: BorderRadius.circular(8),
        ),
        child: SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          child: Text(block.text, style: scope.style.copyWith(fontFamily: 'monospace', fontSize: 14, height: 1.4)),
        ),
      ),
    };
  }

  Widget _webOnly(BuildContext context) {
    final l10n = L10n.of(context);
    return Wrap(
      crossAxisAlignment: WrapCrossAlignment.center,
      children: [
        Text(l10n.plugin_substack_web_only_block, style: scope.style.copyWith(fontStyle: FontStyle.italic)),
        if (scope.postUrl case final url?)
          TextButton(onPressed: () => scope.onLink(url), child: Text(l10n.open_in_browser)),
      ],
    );
  }

  Widget _table(BuildContext context, List<List<String>> rows) {
    final columns = rows.map((row) => row.length).reduce((a, b) => a > b ? a : b);
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: Table(
        defaultColumnWidth: const IntrinsicColumnWidth(),
        border: TableBorder.all(color: Theme.of(context).colorScheme.outlineVariant),
        children: [
          for (final row in rows)
            TableRow(
              children: [
                for (var i = 0; i < columns; i++)
                  Padding(padding: const EdgeInsets.all(8), child: Text(i < row.length ? row[i] : '')),
              ],
            ),
        ],
      ),
    );
  }
}

class _SubstackImage extends StatelessWidget {
  final SubstackImageBlock image;
  final _Scope scope;

  const _SubstackImage({required this.image, required this.scope});

  @override
  Widget build(BuildContext context) {
    final width = MediaQuery.sizeOf(context).width;
    final ratio = image.width != null && image.height != null && image.height! > 0
        ? image.width! / image.height!
        : 16 / 9;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        AspectRatio(
          aspectRatio: ratio,
          child: ExtendedImage.network(
            image.src,
            fit: BoxFit.contain,
            cacheWidth: (width * MediaQuery.devicePixelRatioOf(context)).ceil(),
            semanticLabel: image.alt.isEmpty ? null : image.alt,
          ),
        ),
        if (image.caption.isNotEmpty) ...[
          const SizedBox(height: 8),
          _SubstackRuns(
            runs: image.caption,
            scope: scope,
            style: scope.style.copyWith(
              fontSize: (scope.style.fontSize ?? 18) * 0.85,
              color: Theme.of(context).colorScheme.onSurfaceVariant,
            ),
          ),
        ],
      ],
    );
  }
}

/// Styled runs as one paragraph; links are tappable.
class _SubstackRuns extends StatefulWidget {
  final List<SubstackRun> runs;
  final _Scope scope;
  final TextStyle? style;

  const _SubstackRuns({required this.runs, required this.scope, this.style});

  @override
  State<_SubstackRuns> createState() => _SubstackRunsState();
}

class _SubstackRunsState extends State<_SubstackRuns> {
  var _taps = <TapGestureRecognizer?>[];

  @override
  void initState() {
    super.initState();
    _taps = _recognizers();
  }

  @override
  void didUpdateWidget(_SubstackRuns oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.runs != widget.runs) {
      _dispose();
      _taps = _recognizers();
    }
  }

  List<TapGestureRecognizer?> _recognizers() => [
    for (final run in widget.runs)
      if (run.href case final href? when run.footnote == null)
        TapGestureRecognizer()..onTap = () => widget.scope.onLink(href)
      else
        null,
  ];

  void _dispose() {
    for (final tap in _taps) {
      tap?.dispose();
    }
  }

  @override
  void dispose() {
    _dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final base = widget.style ?? widget.scope.style;
    final link = Theme.of(context).colorScheme.primary;
    return Text.rich(
      TextSpan(
        style: base,
        children: [
          for (var i = 0; i < widget.runs.length; i++)
            TextSpan(text: widget.runs[i].text, recognizer: _taps[i], style: _styleOf(widget.runs[i], link)),
        ],
      ),
    );
  }

  TextStyle _styleOf(SubstackRun run, Color link) => TextStyle(
    fontWeight: run.bold ? FontWeight.w700 : null,
    fontStyle: run.italic ? FontStyle.italic : null,
    fontFamily: run.code ? 'monospace' : null,
    color: run.href != null || run.footnote != null ? link : null,
    decoration: run.href != null && run.footnote == null ? TextDecoration.underline : null,
    fontFeatures: run.footnote != null ? const [FontFeature.superscripts()] : null,
  );
}
