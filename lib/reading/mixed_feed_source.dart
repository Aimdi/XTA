import 'package:flutter/widgets.dart';
import 'package:xta/reading/mixed_feed_definition.dart';
import 'package:xta/reading/mixed_feed_merge.dart';

/// One page a source returned. [next] continues it; null means the source has nothing more.
class MixedPage {
  final List<MixedEntry> entries;
  final Object? next;
  const MixedPage(this.entries, {this.next});
}

/// Reads one source of a mix, page by page, with the plugin's own client.
abstract class MixedSourceReader {
  Future<MixedPage> read(Object? cursor);
}

/// A reader whose pages come from one function.
class MixedFunctionReader implements MixedSourceReader {
  final Future<MixedPage> Function(Object? cursor) _read;
  MixedFunctionReader(this._read);

  @override
  Future<MixedPage> read(Object? cursor) => _read(cursor);
}

/// How the reader names a source when adding it: nothing (the kind says it all), one of a list, or typed text.
enum MixedSourceInput { none, choice, text }

/// A kind of source a mix can hold, such as one RSS feed or a Mastodon hashtag. It reads with the plugin's own
/// client and shows each post with the plugin's own card, so navigation, content warnings, history and translation
/// behave as they do in the plugin.
abstract class MixedSourceKind {
  const MixedSourceKind();

  /// Stable id saved in definitions, such as `rss.feed`.
  String get id;

  /// The plugin the kind belongs to; a mix skips kinds whose plugin is turned off.
  String get pluginId;

  IconData get icon;

  String title(BuildContext context);

  MixedSourceInput get input => MixedSourceInput.none;

  /// Hint for typed input, such as a hashtag without the #.
  String? inputHint(BuildContext context) => null;

  /// The sources of this kind the reader can pick from now, such as the feeds they follow.
  Future<List<MixedFeedSource>> choices(BuildContext context) async => const [];

  /// The source for typed [text], or null when it cannot be read.
  Future<MixedFeedSource?> fromText(BuildContext context, String text) async => null;

  /// A reader for [source] with the plugin's services from [context], or null when the plugin cannot read it now.
  MixedSourceReader? reader(BuildContext context, MixedFeedSource source);

  /// What [source]'s posts depend on besides the source itself, such as the configured server or the accounts
  /// read; when it changes, what was loaded belongs to another context and the source is read again. Never secrets.
  String signature(BuildContext context, MixedFeedSource source) => '';

  /// The plugin's own card for [entry].
  Widget card(BuildContext context, MixedEntry entry);

  /// What the shared filters match for [entry].
  String filterText(MixedEntry entry);
}
