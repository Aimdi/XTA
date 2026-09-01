import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:timeago/timeago.dart' as timeago;

final absoluteDateFormat = DateFormat.yMMMd().add_Hms();

String createRelativeDate(DateTime dateTime) {
  return timeago.format(dateTime, locale: Intl.shortLocale(Intl.getCurrentLocale()));
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
  return timeago.format(dateTime, locale: compactDateLocales.contains(locale) ? '${locale}_short' : locale);
}

class Timestamp extends StatefulWidget {
  final DateTime? timestamp;
  final bool absoluteTimestamp;

  /// Timeline cards use the X-style short form; an opened post keeps the full
  /// wording. Tapping still toggles to the absolute date either way.
  final bool compact;

  const Timestamp({super.key, required this.timestamp, this.absoluteTimestamp = false, this.compact = false});

  @override
  State<Timestamp> createState() => _TimestampState(useRelativeTimestamp: !absoluteTimestamp);
}

class _TimestampState extends State<Timestamp> {
  bool _useRelativeTimestamp;

  _TimestampState({useRelativeTimestamp = true}) : _useRelativeTimestamp = useRelativeTimestamp;

  String formattedTime = '';

  String _relative(DateTime timestamp) =>
      widget.compact ? createCompactDate(timestamp) : createRelativeDate(timestamp);

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
