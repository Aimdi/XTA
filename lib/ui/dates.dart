import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:timeago/timeago.dart' as timeago;

final absoluteDateFormat = DateFormat.yMMMd().add_Hms();

/// Relative dates the app has already spelled out this minute.
///
/// Every card in every feed formats its own timestamp on every build, and
/// `timeago.format` walks its rule list and builds a string each time. The
/// answer only changes as the clock moves, so it is worth keeping — but only
/// until the wording would change, hence the per-minute bucket.
final Map<String, String> _relativeCache = {};
int _relativeCacheMinute = -1;

String _cachedRelative(
  DateTime dateTime,
  String key,
  String Function() format,
) {
  final minute = DateTime.now().millisecondsSinceEpoch ~/ 60000;
  // A long-lived feed would otherwise hold a string per post it ever showed.
  if (minute != _relativeCacheMinute || _relativeCache.length > 2048) {
    _relativeCacheMinute = minute;
    _relativeCache.clear();
  }
  return _relativeCache.putIfAbsent(
    '$key|${dateTime.microsecondsSinceEpoch}',
    format,
  );
}

String createRelativeDate(DateTime dateTime) {
  final locale = Intl.shortLocale(Intl.getCurrentLocale());
  return _cachedRelative(
    dateTime,
    locale,
    () => timeago.format(dateTime, locale: locale),
  );
}

/// The locales whose `<locale>_short` messages main.dart registered — timeago
/// only ships short forms for some languages, and asking it for an
/// unregistered locale falls back to English rather than to the language's
/// long form.
final Set<String> compactDateLocales = {};

/// X-style timeline stamp: "5m" where the full form says "5 minutes ago".
///
/// Languages without short forms (and CJK, whose long forms are already this
/// size) keep the full wording.
String createCompactDate(DateTime dateTime) {
  final locale = Intl.shortLocale(Intl.getCurrentLocale());
  final tag = compactDateLocales.contains(locale) ? '${locale}_short' : locale;
  return _cachedRelative(
    dateTime,
    tag,
    () => timeago.format(dateTime, locale: tag),
  );
}

class Timestamp extends StatefulWidget {
  final DateTime? timestamp;
  final bool absoluteTimestamp;

  /// Timeline cards use the X-style short form; an opened post keeps the full
  /// wording. Tapping still toggles to the absolute date either way.
  final bool compact;

  const Timestamp({
    super.key,
    required this.timestamp,
    this.absoluteTimestamp = false,
    this.compact = false,
  });

  @override
  State<Timestamp> createState() =>
      _TimestampState(useRelativeTimestamp: !absoluteTimestamp);
}

class _TimestampState extends State<Timestamp> {
  bool _useRelativeTimestamp;

  _TimestampState({useRelativeTimestamp = true})
    : _useRelativeTimestamp = useRelativeTimestamp;

  String formattedTime = '';

  String _relative(DateTime timestamp) => widget.compact
      ? createCompactDate(timestamp)
      : createRelativeDate(timestamp);

  @override
  void initState() {
    super.initState();

    var timestamp = widget.timestamp;
    if (timestamp != null) {
      if (_useRelativeTimestamp) {
        formattedTime = _relative(timestamp);
      } else {
        formattedTime = absoluteDateFormat.format(timestamp.toLocal());
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    var timestamp = widget.timestamp;
    if (timestamp == null) {
      return Container();
    }

    return GestureDetector(
      child: Text(formattedTime),
      onTap: () {
        // Flip first, then format the mode being flipped to — formatting the
        // mode being left is why the first tap appeared to do nothing.
        setState(() {
          _useRelativeTimestamp = !_useRelativeTimestamp;
          formattedTime = _useRelativeTimestamp
              ? _relative(timestamp)
              : absoluteDateFormat.format(timestamp.toLocal());
        });
      },
    );
  }
}
