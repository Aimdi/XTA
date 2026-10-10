import 'package:flutter/widgets.dart';
import 'package:intl/intl.dart';

/// The reader's locale as intl's number formats know it. A few app languages,
/// such as Esperanto, have no number symbols there and would throw; they get
/// English digits instead.
String numberFormatLocale(BuildContext context) =>
    Intl.verifiedLocale(Localizations.localeOf(context).toString(), NumberFormat.localeExists, onFailure: (_) => 'en')!;
