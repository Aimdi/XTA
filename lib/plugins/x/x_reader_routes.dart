import 'package:flutter/material.dart';
import 'package:xta/constants.dart';
import 'package:xta/saved/saved_screen.dart';
import 'package:xta/search/search.dart';
import 'package:xta/settings/_account.dart';
import 'package:xta/subscriptions/subscriptions.dart';
import 'package:xta/ui/reader_chrome.dart';

enum XReaderDestination { subscriptions, saved, accounts }

Future<void> openXSearch(BuildContext context, {String? initialQuery}) async {
  final query = initialQuery?.trim();
  await Navigator.pushNamed<void>(
    context,
    routeSearch,
    arguments: SearchArguments(0, query: query, focusInputOnOpen: query == null || query.isEmpty),
  );
}

Future<void> openXReaderDestination(BuildContext context, XReaderDestination destination) => Navigator.push<void>(
  context,
  MaterialPageRoute<void>(
    settings: RouteSettings(name: '/x/${destination.name}'),
    builder: (_) => _XReaderDestinationPage(destination: destination),
  ),
);

class _XReaderDestinationPage extends StatefulWidget {
  final XReaderDestination destination;

  const _XReaderDestinationPage({required this.destination});

  @override
  State<_XReaderDestinationPage> createState() => _XReaderDestinationPageState();
}

class _XReaderDestinationPageState extends State<_XReaderDestinationPage> {
  final _scroll = ScrollController();

  @override
  void dispose() {
    _scroll.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => XtaSystemBars(
    child: switch (widget.destination) {
      XReaderDestination.subscriptions => SubscriptionsScreen(scrollController: _scroll),
      XReaderDestination.saved => SavedScreen(scrollController: _scroll, showTitle: true),
      XReaderDestination.accounts => const SettingsAccountFragment(),
    },
  );
}
