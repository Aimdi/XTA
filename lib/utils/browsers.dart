import 'package:android_intent_plus/android_intent.dart';
import 'package:flutter/services.dart';
import 'package:url_launcher/url_launcher_string.dart';

/// The platform side that knows which apps can open a link.
const browserChannel = MethodChannel('browser_resolver');

/// An app that has said it can open an https link.
typedef InstalledBrowser = ({String package, String label});

/// Stands for "whatever Android would have picked".
const String systemDefaultBrowser = '';

/// Every browser installed, for the reader to choose one from.
///
/// Empty when the platform cannot say — an older Android, or a query the
/// manifest does not declare — which the caller should read as "offer only the
/// system default" rather than "there are no browsers".
Future<List<InstalledBrowser>> installedBrowsers() async {
  try {
    final raw = await browserChannel.invokeMethod<List<Object?>>('listBrowsers');
    if (raw == null) {
      return const [];
    }

    return [
      for (final entry in raw)
        if (entry is Map && entry['package'] is String)
          (package: entry['package'] as String, label: (entry['label'] as String?) ?? entry['package'] as String),
    ];
  } on PlatformException {
    return const [];
  } on MissingPluginException {
    return const [];
  }
}

/// Opens [url] outside the app, in [package] when one was chosen.
///
/// Falls back to the system default whenever the named browser cannot take it:
/// the app it names can be uninstalled at any time, and a link that silently
/// does nothing is worse than one that opens in the wrong browser.
Future<void> openExternally(String url, {String package = systemDefaultBrowser}) async {
  if (package.isNotEmpty) {
    try {
      await AndroidIntent(action: 'android.intent.action.VIEW', data: url, package: package).launch();
      return;
    } catch (_) {
      // Through to the default below.
    }
  }

  await launchUrlString(url, mode: LaunchMode.externalApplication);
}
