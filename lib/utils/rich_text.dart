import 'package:dart_twitter_api/twitter_api.dart';
import 'package:html_unescape/html_unescape.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/profile/profile.dart';
import 'package:xta/search/search.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/utils/urls.dart';
import 'package:xta/utils/_entities.dart';
import 'package:xta/tweet/ticker_screen.dart';
import 'package:xta/plugins/plugin_links.dart';
import 'package:xta/plugins/stocks/stocks_store.dart';

/// Turning a post's text and its entity list into spans.
///
/// X positions entities by rune index into the raw text; the text between them
/// is scanned again for the mentions and hashtags that descriptions carry
/// without entities. Every tappable span costs a [TapGestureRecognizer], and a
/// recognizer is a resource: whoever keeps the returned parts must hand them to
/// [disposeRichTextParts] when done — the parts list remembers its own
/// recognizers.

/// One run of a post's text: either plain words or a built span.
class RichTextPart {
  final InlineSpan? entity;
  final String? plainText;

  const RichTextPart(this.entity, this.plainText);

  @override
  String toString() => plainText ?? '';
}

// Compiled once: this runs per text segment of every tweet scrolled in, and
// Dart does not cache RegExp construction.
final _mentionOrHashtag = RegExp(r'(#|(?<=\W|^)@)\w+');
final _unescape = HtmlUnescape();

/// Text as it reads, not as X sends it: `&gt;`, `&lt;` and `&amp;` come back
/// escaped in every post body, and anything showing that body raw shows the
/// entities instead of the characters.
String unescapeHtml(String text) => _unescape.convert(text);

/// The recognizers behind each parts list, attached to the list itself so
/// disposal needs no separate bookkeeping and a list nobody keeps can simply
/// be collected.
final Expando<List<GestureRecognizer>> _recognizersOf = Expando();

/// Releases the recognizers a [buildRichText] result carries. Safe to call on
/// a list that carries none.
void disposeRichTextParts(List<RichTextPart> parts) {
  final recognizers = _recognizersOf[parts];
  if (recognizers == null) {
    return;
  }
  for (final recognizer in recognizers) {
    recognizer.dispose();
  }
  _recognizersOf[parts] = null;
}

String _normalizeHashtag(String tag) => tag.startsWith('#') ? tag : '#$tag';

bool _isFollowingTopic(BuildContext context, String tag) {
  final normalized = _normalizeHashtag(tag);
  return context.read<SubscriptionsModel>().state.any(
    (s) => s is SearchSubscription && s.id == normalized,
  );
}

Future<void> _toggleTopicFollow(BuildContext context, String tag) async {
  final normalized = _normalizeHashtag(tag);
  final model = context.read<SubscriptionsModel>();
  final followed = _isFollowingTopic(context, normalized);
  await model.toggleSubscribe(
    SearchSubscription(id: normalized, createdAt: DateTime.now()),
    followed,
  );
  if (!context.mounted) {
    return;
  }
  final messenger = ScaffoldMessenger.of(context);
  messenger.showSnackBar(
    SnackBar(
      content: Text(
        followed
            ? L10n.of(context).unsubscribe
            : L10n.of(context).topic_follow_done(normalized),
      ),
    ),
  );
}

void _showCashtagSheet(BuildContext context, String symbol) {
  StocksWatchlistStore? store;
  try {
    store = context.read<StocksWatchlistStore>();
  } on ProviderNotFoundException {
    store = null;
  }
  final watched = store?.state.contains(symbol.toUpperCase()) ?? false;

  showModalBottomSheet<void>(
    context: context,
    builder: (sheetContext) => SafeArea(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          ListTile(
            leading: const Icon(Icons.show_chart),
            title: Text('\$${symbol.toUpperCase()}'),
            onTap: () {
              Navigator.pop(sheetContext);
              Navigator.pushNamed(
                context,
                routeTicker,
                arguments: TickerScreenArguments(symbol: symbol),
              );
            },
          ),
          if (store != null)
            ListTile(
              leading: Icon(watched ? Icons.star : Icons.star_outline),
              title: Text(
                watched
                    ? L10n.of(sheetContext).plugin_stocks_unwatch
                    : L10n.of(sheetContext).plugin_stocks_watch,
              ),
              onTap: () {
                Navigator.pop(sheetContext);
                if (watched) {
                  store!.remove(symbol);
                } else {
                  store!.add(symbol);
                }
              },
            ),
        ],
      ),
    ),
  );
}

void _showTopicFollowSheet(BuildContext context, String tag) {
  final normalized = _normalizeHashtag(tag);
  final followed = _isFollowingTopic(context, normalized);

  showModalBottomSheet<void>(
    context: context,
    builder: (sheetContext) => SafeArea(
      child: ListTile(
        leading: Icon(followed ? Icons.check : Icons.tag),
        title: Text(
          followed
              ? L10n.of(sheetContext).topic_following
              : L10n.of(sheetContext).topic_follow,
        ),
        onTap: () {
          Navigator.pop(sheetContext);
          _toggleTopicFollow(context, normalized);
        },
      ),
    ),
  );
}

InlineSpan _hashtagSpan(
  EntitySpanContext context,
  String text,
  VoidCallback onTap,
  VoidCallback onLongPress,
) {
  return WidgetSpan(
    alignment: PlaceholderAlignment.baseline,
    baseline: TextBaseline.alphabetic,
    child: GestureDetector(
      onTap: onTap,
      onLongPress: onLongPress,
      child: Text(text, style: context.linkStyle),
    ),
  );
}

List<InlineSpan> displayRichText(List<RichTextPart> richText) {
  return [
    for (final part in richText)
      if (part.plainText != null)
        TextSpan(text: part.plainText)
      else
        part.entity!,
  ];
}

/// All of a post's runs, in order: the entities where X placed them, and the
/// text between them scanned for what descriptions leave unmarked.
List<RichTextPart> buildRichText(
  BuildContext context,
  String rawText,
  Object? rawEntities,
) {
  final runes = rawText.runes.toList(growable: false);

  final recognizers = <GestureRecognizer>[];
  final spanContext = EntitySpanContext(
    // The same accent every other link in the app wears. It was a hardcoded
    // blue before, so a mention in a post and one in a bio disagreed.
    linkColor: Theme.of(context).colorScheme.secondary,
    recognizer: (onTap) {
      final recognizer = TapGestureRecognizer()..onTap = onTap;
      recognizers.add(recognizer);
      return recognizer;
    },
  );

  final entities = _parseEntities(context, rawEntities);
  final parts = <RichTextPart>[];

  var index = 0;
  for (final entity in entities) {
    _addTextRuns(
      context,
      parts,
      spanContext,
      _runesToText(runes, index, entity.getEntityStart()),
    );
    parts.add(RichTextPart(entity.getContent(spanContext), null));
    index = entity.getEntityEnd();
  }
  _addTextRuns(context, parts, spanContext, _runesToText(runes, index));

  _recognizersOf[parts] = recognizers;
  return parts;
}

/// Splits [text] around the mentions and hashtags descriptions carry without
/// entities, adding a run per piece.
void _addTextRuns(
  BuildContext context,
  List<RichTextPart> parts,
  EntitySpanContext spanContext,
  String? text,
) {
  if (text == null) {
    return;
  }

  text.splitMapJoin(
    _mentionOrHashtag,
    onMatch: (match) {
      final full = match.group(0);
      final kind = match.group(1);
      if (kind == null || full == null) {
        return '';
      }
      if (kind == '#') {
        parts.add(
          RichTextPart(
            _hashtagSpan(
              spanContext,
              full,
              () => Navigator.pushNamed(
                context,
                routeSearch,
                arguments: SearchArguments(
                  1,
                  focusInputOnOpen: false,
                  query: full,
                ),
              ),
              () => _showTopicFollowSheet(context, full),
            ),
            null,
          ),
        );
      } else {
        parts.add(
          RichTextPart(
            TextSpan(
              text: full,
              style: spanContext.linkStyle,
              recognizer: spanContext.recognizer(() {
                Navigator.pushNamed(
                  context,
                  routeProfile,
                  arguments: ProfileScreenArguments.fromScreenName(
                    full.substring(1),
                    null,
                  ),
                );
              }),
            ),
            null,
          ),
        );
      }
      return kind;
    },
    onNonMatch: (piece) {
      if (piece.isNotEmpty) {
        parts.add(RichTextPart(null, piece));
      }
      return piece;
    },
  );
}

String? _runesToText(List<int> runes, int start, [int? end]) {
  // Clamped: the indices come off the wire, and an entity past the end of the
  // text must cost a missing run, not a RangeError in every tile after it.
  final from = start.clamp(0, runes.length);
  final to = (end ?? runes.length).clamp(from, runes.length);
  final string = String.fromCharCodes(runes.getRange(from, to));
  if (string.isEmpty) {
    return null;
  }
  return _unescape.convert(string);
}

List<Entity> _parseEntities(BuildContext context, Object? rawEntities) {
  if (rawEntities == null) {
    return const [];
  }

  // The translation API hands entities back as raw JSON rather than the model.
  final parsed = rawEntities is Map<String, dynamic>
      ? Entities.fromJson(rawEntities)
      : rawEntities;
  if (parsed is! Entities && parsed is! UserEntityUrl) {
    return const [];
  }

  final entities = <Entity>[];

  // In tweets every entity kind can be present; a profile description carries
  // only urls (UserEntityUrl).
  if (parsed is Entities) {
    for (final media in parsed.media ?? const <Media>[]) {
      entities.add(MediaEntity(media));
    }

    for (final hashtag in parsed.hashtags ?? const <Hashtag>[]) {
      entities.add(
        HashtagEntity(
          hashtag,
          () => Navigator.pushNamed(
            context,
            routeSearch,
            arguments: SearchArguments(
              1,
              focusInputOnOpen: false,
              query: '#${hashtag.text}',
            ),
          ),
          () => _showTopicFollowSheet(context, '#${hashtag.text}'),
        ),
      );
    }

    // A ticker opens its own screen: the chart, and the posts about it.
    for (final symbol in parsed.symbols ?? const []) {
      final String? text = symbol.text;
      if (text == null || text.isEmpty) {
        continue;
      }
      entities.add(
        SymbolEntity(
          text: text,
          indices: symbol.indices,
          onTap: () => Navigator.pushNamed(
            context,
            routeTicker,
            arguments: TickerScreenArguments(symbol: text),
          ),
          onLongPress: () => _showCashtagSheet(context, text),
        ),
      );
    }

    for (final mention in parsed.userMentions ?? const <UserMention>[]) {
      entities.add(
        UserMentionEntity(
          mention,
          () => Navigator.pushNamed(
            context,
            routeProfile,
            arguments: ProfileScreenArguments(
              mention.idStr,
              mention.screenName,
              null,
            ),
          ),
        ),
      );
    }
  }

  final urls = parsed is Entities
      ? parsed.urls
      : (parsed as UserEntityUrl).urls;
  for (final url in urls ?? const <Url>[]) {
    entities.add(
      UrlEntity(url, () async {
        final uri = url.expandedUrl;
        if (uri == null ||
            uri.startsWith('https://twitter.com/i/web/status/') ||
            uri.startsWith('https://x.com/i/web/status/')) {
          return;
        }
        // A plugin may be able to read this link in-app (Substack posts); only
        // hand it to the browser when none claims it.
        if (!context.mounted) return;
        if (await openWithPlugins(context, uri)) return;
        if (!context.mounted) return;
        await openUri(context, uri);
      }),
    );
  }

  entities.sort((a, b) => a.getEntityStart().compareTo(b.getEntityStart()));

  return entities;
}
