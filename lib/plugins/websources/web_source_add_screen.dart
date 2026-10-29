import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/websources/web_source_catalogue.dart';
import 'package:xta/plugins/websources/web_source_client.dart';
import 'package:xta/plugins/websources/web_source_definition.dart';
import 'package:xta/plugins/websources/web_source_dialogs.dart';
import 'package:xta/plugins/websources/web_sources_store.dart';

/// The sources this repository publishes, read when the Add source screen
/// opens.
class WebSourceCatalogueStore extends Store<List<WebSourceListing>> {
  final WebSourceClient client;
  final Uri url;

  WebSourceCatalogueStore(this.client, {Uri? url})
    : url = url ?? Uri.parse(defaultWebSourceCatalogueUrl),
      super(const []);

  Future<void> load() => execute(() async => parseWebSourceCatalogue(await client.file(url), url));
}

/// Reads the source file at [uri], asks the reader to agree to it and its
/// sites, and installs it. Whether it was installed.
Future<bool> installWebSourceFrom(BuildContext context, Uri uri) async {
  final client = context.read<WebSourceClient>();
  final sources = context.read<WebSourcesStore>();
  final file = await withWebSourceProgress(context, () => _fetchFile(client, uri));
  if (!context.mounted) return false;
  switch (file) {
    case null:
      await showWebSourceUnreachable(context);
      return false;
    case RefusedWebSource():
      await showWebSourceRefusal(context, file);
      return false;
    case ReadWebSource(:final source):
      if (!await confirmWebSource(context, source, origin: uri)) return false;
      await sources.install(source, origin: uri);
      return true;
  }
}

Future<WebSourceFile> _fetchFile(WebSourceClient client, Uri uri) async {
  try {
    return readWebSource(await client.file(uri));
  } on WebSourceFailure catch (failure) {
    if (failure.kind == WebSourceFailureKind.tooLarge) {
      return const RefusedWebSource(WebSourceRefusal.tooLarge, '64 KB');
    }
    rethrow;
  }
}

class WebSourceAddScreen extends StatefulWidget {
  const WebSourceAddScreen({super.key});

  @override
  State<WebSourceAddScreen> createState() => _WebSourceAddScreenState();
}

class _WebSourceAddScreenState extends State<WebSourceAddScreen> {
  late final WebSourceCatalogueStore _catalogue;
  final _link = TextEditingController();

  @override
  void initState() {
    super.initState();
    _catalogue = WebSourceCatalogueStore(context.read<WebSourceClient>())..load();
  }

  @override
  void dispose() {
    _catalogue.destroy();
    _link.dispose();
    super.dispose();
  }

  Future<void> _addLink() async {
    final text = _link.text.trim();
    final uri = Uri.tryParse(text);
    if (uri == null || !uri.isScheme('https') || uri.host.isEmpty) {
      await showWebSourceRefusal(context, RefusedWebSource(WebSourceRefusal.insecure, text));
      return;
    }
    final navigator = Navigator.of(context);
    if (await installWebSourceFrom(context, uri)) navigator.pop();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(l10n.plugin_websources_add)),
      body: ListView(
        padding: const EdgeInsets.only(bottom: 24),
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
            child: TextField(
              controller: _link,
              keyboardType: TextInputType.url,
              textInputAction: TextInputAction.go,
              decoration: InputDecoration(
                labelText: l10n.plugin_websources_link,
                hintText: l10n.plugin_websources_link_placeholder,
                border: const OutlineInputBorder(),
              ),
              onSubmitted: (_) => _addLink(),
            ),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Align(
              alignment: AlignmentDirectional.centerEnd,
              child: FilledButton.tonal(onPressed: _addLink, child: Text(l10n.plugin_websources_add_link)),
            ),
          ),
          const Divider(height: 32),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Text(l10n.plugin_websources_published, style: Theme.of(context).textTheme.titleSmall),
          ),
          _Published(catalogue: _catalogue),
        ],
      ),
    );
  }
}

class _Published extends StatelessWidget {
  final WebSourceCatalogueStore catalogue;

  const _Published({required this.catalogue});

  @override
  Widget build(BuildContext context) => ScopedBuilder<WebSourceCatalogueStore, List<WebSourceListing>>(
    store: catalogue,
    onLoading: (_) => const Padding(padding: EdgeInsets.all(16), child: LinearProgressIndicator()),
    onError: (context, _) => ListTile(
      title: Text(L10n.of(context).plugin_websources_published_unreachable),
      trailing: TextButton(onPressed: catalogue.load, child: Text(L10n.of(context).retry)),
    ),
    onState: (_, listings) => Column(children: [for (final listing in listings) _ListingTile(listing: listing)]),
  );
}

class _ListingTile extends StatelessWidget {
  final WebSourceListing listing;

  const _ListingTile({required this.listing});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final sources = context.read<WebSourcesStore>();
    return ScopedBuilder<WebSourcesStore, List<InstalledWebSource>>(
      store: sources,
      onState: (context, _) {
        final installed = sources.installed(listing.id) != null;
        return ListTile(
          title: Text(listing.name),
          subtitle: listing.description.isEmpty ? null : Text(listing.description),
          trailing: installed
              ? Text(l10n.plugin_websources_installed)
              : FilledButton.tonal(
                  onPressed: () => installWebSourceFrom(context, listing.url),
                  child: Text(l10n.plugin_websources_install),
                ),
        );
      },
    );
  }
}
