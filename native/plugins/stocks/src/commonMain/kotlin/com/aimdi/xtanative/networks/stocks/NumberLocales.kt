package com.aimdi.xtanative.networks.stocks

/**
 * How XTA's languages write numbers: intl 0.20.3's data (from CLDR) for the
 * locales XTA ships, the same numbers the Flutter app shows. Generated from
 * that package's number_symbols_data.dart; Esperanto, which it lacks, is
 * written in English as in XTA.
 */
internal object NumberLocales {
    val BY_LOCALE: Map<String, NumberSymbols> = mapOf(
        "ar" to NumberSymbols(
            decimal = ".", group = ",", minus = "\u200e-", primaryGrouping = 3, secondaryGrouping = 3,
            compact = mapOf(3 to "0\u00a0\u0623\u0644\u0641", 4 to "00\u00a0\u0623\u0644\u0641", 6 to "0\u00a0\u0645\u0644\u064a\u0648\u0646", 9 to "0\u00a0\u0645\u0644\u064a\u0627\u0631", 12 to "0\u00a0\u062a\u0631\u0644\u064a\u0648\u0646"),
            compactCurrency = mapOf(3 to "\u200f0\u00a0\u0623\u0644\u0641\u00a0\u00a4", 4 to "\u200f00\u00a0\u0623\u0644\u0641\u00a0\u00a4", 6 to "\u200f0\u00a0\u0645\u0644\u064a\u0648\u0646\u00a0\u00a4", 9 to "\u200f0\u00a0\u0645\u0644\u064a\u0627\u0631\u00a0\u00a4", 12 to "\u200f0\u00a0\u062a\u0631\u0644\u064a\u0648\u0646\u00a0\u00a4"),
            currencyPattern = "\u200f#,##0.00\u00a0\u00a4",
            compactFew = mapOf(3 to "0\u00a0\u0622\u0644\u0627\u0641"),
            compactCurrencyFew = mapOf(3 to "\u200f0\u00a0\u0622\u0644\u0627\u0641\u00a0\u00a4"),
        ),
        "be" to NumberSymbols(
            decimal = ",", group = "\u00a0", minus = "-", primaryGrouping = 3, secondaryGrouping = 3,
            compact = mapOf(3 to "0\u00a0\u0442\u044b\u0441.", 6 to "0\u00a0\u043c\u043b\u043d", 9 to "0\u00a0\u043c\u043b\u0440\u0434", 12 to "0\u00a0\u0442\u0440\u043b\u043d"),
            compactCurrency = mapOf(3 to "0\u00a0\u0442\u044b\u0441.\u00a0\u00a4", 6 to "0\u00a0\u043c\u043b\u043d\u00a0\u00a4", 9 to "0\u00a0\u043c\u043b\u0440\u0434\u00a0\u00a4", 12 to "0\u00a0\u0442\u0440\u043b\u043d\u00a0\u00a4"),
            currencyPattern = "#,##0.00\u00a0\u00a4",
        ),
        "ca" to NumberSymbols(
            decimal = ",", group = ".", minus = "-", primaryGrouping = 3, secondaryGrouping = 3,
            compact = mapOf(3 to "0\u00a0k", 6 to "0\u00a0M", 10 to "00\u00a0kM", 12 to "0\u00a0B"),
            compactCurrency = mapOf(3 to "0\u00a0k\u00a0\u00a4", 6 to "0\u00a0M\u00a0\u00a4", 10 to "00\u00a0kM\u00a0\u00a4", 12 to "0\u00a0B\u00a0\u00a4"),
            currencyPattern = "#,##0.00\u00a0\u00a4",
        ),
        "cs" to NumberSymbols(
            decimal = ",", group = "\u00a0", minus = "-", primaryGrouping = 3, secondaryGrouping = 3,
            compact = mapOf(3 to "0\u00a0tis.", 6 to "0\u00a0mil.", 9 to "0\u00a0mld.", 12 to "0\u00a0bil."),
            compactCurrency = mapOf(3 to "0\u00a0tis.\u00a0\u00a4", 6 to "0\u00a0mil.\u00a0\u00a4", 9 to "0\u00a0mld.\u00a0\u00a4", 12 to "0\u00a0bil.\u00a0\u00a4"),
            currencyPattern = "#,##0.00\u00a0\u00a4",
        ),
        "de" to NumberSymbols(
            decimal = ",", group = ".", minus = "-", primaryGrouping = 3, secondaryGrouping = 3,
            compact = mapOf(3 to "0", 4 to "0", 5 to "0", 6 to "0\u00a0Mio.", 9 to "0\u00a0Mrd.", 12 to "0\u00a0Bio."),
            compactCurrency = mapOf(3 to "0", 4 to "0", 5 to "0", 6 to "0\u00a0Mio.\u00a0\u00a4", 9 to "0\u00a0Mrd.\u00a0\u00a4", 12 to "0\u00a0Bio.\u00a0\u00a4"),
            currencyPattern = "#,##0.00\u00a0\u00a4",
        ),
        "en" to NumberSymbols(
            decimal = ".", group = ",", minus = "-", primaryGrouping = 3, secondaryGrouping = 3,
            compact = mapOf(3 to "0K", 6 to "0M", 9 to "0B", 12 to "0T"),
            compactCurrency = mapOf(3 to "\u00a40K", 6 to "\u00a40M", 9 to "\u00a40B", 12 to "\u00a40T"),
            currencyPattern = "\u00a4#,##0.00",
        ),
        "es" to NumberSymbols(
            decimal = ",", group = ".", minus = "-", primaryGrouping = 3, secondaryGrouping = 3,
            compact = mapOf(3 to "0\u00a0mil", 6 to "0\u00a0M", 10 to "00\u00a0mil\u00a0M", 12 to "0\u00a0B"),
            compactCurrency = mapOf(3 to "0\u00a0mil\u00a0\u00a4", 6 to "0\u00a0M\u00a0\u00a4", 10 to "00\u00a0mil\u00a0M\u00a0\u00a4", 12 to "0\u00a0B\u00a0\u00a4"),
            currencyPattern = "#,##0.00\u00a0\u00a4",
        ),
        "et" to NumberSymbols(
            decimal = ",", group = "\u00a0", minus = "\u2212", primaryGrouping = 3, secondaryGrouping = 3,
            compact = mapOf(3 to "0\u00a0tuh", 6 to "0\u00a0mln", 9 to "0\u00a0mld", 12 to "0\u00a0trln"),
            compactCurrency = mapOf(3 to "0\u00a0tuh\u00a0\u00a4", 6 to "0\u00a0mln\u00a0\u00a4", 9 to "0\u00a0mld\u00a0\u00a4", 12 to "0\u00a0trln\u00a0\u00a4"),
            currencyPattern = "#,##0.00\u00a0\u00a4",
        ),
        "eu" to NumberSymbols(
            decimal = ",", group = ".", minus = "\u2212", primaryGrouping = 3, secondaryGrouping = 3,
            compact = mapOf(3 to "0", 4 to "0", 5 to "0", 6 to "0\u00a0M", 12 to "0\u00a0B"),
            compactCurrency = mapOf(3 to "0", 4 to "0", 5 to "0", 6 to "0\u00a0M\u00a0\u00a4", 12 to "0\u00a0B\u00a0\u00a4"),
            currencyPattern = "#,##0.00\u00a0\u00a4",
        ),
        "fr" to NumberSymbols(
            decimal = ",", group = "\u202f", minus = "-", primaryGrouping = 3, secondaryGrouping = 3,
            compact = mapOf(3 to "0\u00a0k", 6 to "0\u00a0M", 9 to "0\u00a0Md", 12 to "0\u00a0Bn"),
            compactCurrency = mapOf(3 to "0\u00a0k\u00a0\u00a4", 6 to "0\u00a0M\u00a0\u00a4", 9 to "0\u00a0Md\u00a0\u00a4", 12 to "0\u00a0Bn\u00a0\u00a4"),
            currencyPattern = "#,##0.00\u00a0\u00a4",
        ),
        "hi" to NumberSymbols(
            decimal = ".", group = ",", minus = "-", primaryGrouping = 3, secondaryGrouping = 2,
            compact = mapOf(3 to "0\u00a0\u0939\u091c\u093c\u093e\u0930", 5 to "0\u00a0\u0932\u093e\u0916", 7 to "0\u00a0\u0915\u0970", 9 to "0\u00a0\u0905\u0970", 11 to "0\u00a0\u0916\u0970", 13 to "0\u00a0\u0928\u0940\u0932"),
            compactCurrency = mapOf(3 to "\u00a40\u00a0\u0939\u091c\u093c\u093e\u0930", 5 to "\u00a40\u00a0\u0932\u093e\u0916", 7 to "\u00a40\u00a0\u0915\u0970", 9 to "\u00a40\u00a0\u0905\u0970", 11 to "\u00a40\u00a0\u0916\u0970", 13 to "\u00a40\u00a0\u0928\u0940\u0932"),
            currencyPattern = "\u00a4#,##,##0.00",
        ),
        "id" to NumberSymbols(
            decimal = ",", group = ".", minus = "-", primaryGrouping = 3, secondaryGrouping = 3,
            compact = mapOf(3 to "0\u00a0rb", 6 to "0\u00a0jt", 9 to "0\u00a0M", 12 to "0\u00a0T"),
            compactCurrency = mapOf(3 to "\u00a40\u00a0rb", 6 to "\u00a40\u00a0jt", 9 to "\u00a40\u00a0M", 12 to "\u00a40\u00a0T"),
            currencyPattern = "\u00a4#,##0.00",
        ),
        "it" to NumberSymbols(
            decimal = ",", group = ".", minus = "-", primaryGrouping = 3, secondaryGrouping = 3,
            compact = mapOf(3 to "0K", 6 to "0\u00a0Mln", 9 to "0\u00a0Mld", 12 to "0\u00a0Bln"),
            compactCurrency = mapOf(3 to "0K\u00a0\u00a4", 6 to "0\u00a0Mln\u00a0\u00a4", 9 to "0\u00a0Mld\u00a0\u00a4", 12 to "0\u00a0Bln\u00a0\u00a4"),
            currencyPattern = "#,##0.00\u00a0\u00a4",
        ),
        "ja" to NumberSymbols(
            decimal = ".", group = ",", minus = "-", primaryGrouping = 3, secondaryGrouping = 3,
            compact = mapOf(3 to "0", 4 to "0\u4e07", 8 to "0\u5104", 12 to "0\u5146", 16 to "0\u4eac"),
            compactCurrency = mapOf(3 to "0", 4 to "\u00a40\u4e07", 8 to "\u00a40\u5104", 12 to "\u00a40\u5146", 16 to "\u00a40\u4eac"),
            currencyPattern = "\u00a4#,##0.00",
        ),
        "ko" to NumberSymbols(
            decimal = ".", group = ",", minus = "-", primaryGrouping = 3, secondaryGrouping = 3,
            compact = mapOf(3 to "0\ucc9c", 4 to "0\ub9cc", 8 to "0\uc5b5", 12 to "0\uc870"),
            compactCurrency = mapOf(3 to "\u00a40\ucc9c", 4 to "\u00a40\ub9cc", 8 to "\u00a40\uc5b5", 12 to "\u00a40\uc870"),
            currencyPattern = "\u00a4#,##0.00",
        ),
        "nb" to NumberSymbols(
            decimal = ",", group = "\u00a0", minus = "\u2212", primaryGrouping = 3, secondaryGrouping = 3,
            compact = mapOf(3 to "0k", 6 to "0\u00a0mill.", 9 to "0\u00a0mrd.", 12 to "0\u00a0bill."),
            compactCurrency = mapOf(3 to "0k\u00a0\u00a4", 6 to "0\u00a0mill.\u00a0\u00a4", 9 to "0\u00a0mrd.\u00a0\u00a4", 12 to "0\u00a0bill.\u00a0\u00a4"),
            currencyPattern = "#,##0.00\u00a0\u00a4",
        ),
        "nl" to NumberSymbols(
            decimal = ",", group = ".", minus = "-", primaryGrouping = 3, secondaryGrouping = 3,
            compact = mapOf(3 to "0K", 6 to "0\u00a0mln.", 9 to "0\u00a0mld.", 12 to "0\u00a0bln."),
            compactCurrency = mapOf(3 to "\u00a4\u00a00K", 6 to "\u00a4\u00a00\u00a0mln.", 9 to "\u00a4\u00a00\u00a0mld.", 12 to "\u00a4\u00a00\u00a0bln."),
            currencyPattern = "\u00a4\u00a0#,##0.00",
        ),
        "pl" to NumberSymbols(
            decimal = ",", group = "\u00a0", minus = "-", primaryGrouping = 3, secondaryGrouping = 3,
            compact = mapOf(3 to "0\u00a0tys.", 6 to "0\u00a0mln", 9 to "0\u00a0mld", 12 to "0\u00a0bln"),
            compactCurrency = mapOf(3 to "0\u00a0tys.\u00a0\u00a4", 6 to "0\u00a0mln\u00a0\u00a4", 9 to "0\u00a0mld\u00a0\u00a4", 12 to "0\u00a0bln\u00a0\u00a4"),
            currencyPattern = "#,##0.00\u00a0\u00a4",
        ),
        "pt" to NumberSymbols(
            decimal = ",", group = ".", minus = "-", primaryGrouping = 3, secondaryGrouping = 3,
            compact = mapOf(3 to "0\u00a0mil", 6 to "0\u00a0mi", 9 to "0\u00a0bi", 12 to "0\u00a0tri"),
            compactCurrency = mapOf(3 to "\u00a4\u00a00\u00a0mil", 6 to "\u00a4\u00a00\u00a0mi", 9 to "\u00a4\u00a00\u00a0bi", 12 to "\u00a4\u00a00\u00a0tri"),
            currencyPattern = "\u00a4\u00a0#,##0.00",
        ),
        "pt_BR" to NumberSymbols(
            decimal = ",", group = ".", minus = "-", primaryGrouping = 3, secondaryGrouping = 3,
            compact = mapOf(3 to "0\u00a0mil", 6 to "0\u00a0mi", 9 to "0\u00a0bi", 12 to "0\u00a0tri"),
            compactCurrency = mapOf(3 to "\u00a4\u00a00\u00a0mil", 6 to "\u00a4\u00a00\u00a0mi", 9 to "\u00a4\u00a00\u00a0bi", 12 to "\u00a4\u00a00\u00a0tri"),
            currencyPattern = "\u00a4\u00a0#,##0.00",
        ),
        "ro" to NumberSymbols(
            decimal = ",", group = ".", minus = "-", primaryGrouping = 3, secondaryGrouping = 3,
            compact = mapOf(3 to "0\u00a0K", 6 to "0\u00a0mil.", 9 to "0\u00a0mld.", 12 to "0\u00a0tril."),
            compactCurrency = mapOf(3 to "0\u00a0K\u00a0\u00a4", 6 to "0\u00a0mil.\u00a0\u00a4", 9 to "0\u00a0mld.\u00a0\u00a4", 12 to "0\u00a0tril.\u00a0\u00a4"),
            currencyPattern = "#,##0.00\u00a0\u00a4",
        ),
        "ru" to NumberSymbols(
            decimal = ",", group = "\u00a0", minus = "-", primaryGrouping = 3, secondaryGrouping = 3,
            compact = mapOf(3 to "0\u00a0\u0442\u044b\u0441.", 6 to "0\u00a0\u043c\u043b\u043d", 9 to "0\u00a0\u043c\u043b\u0440\u0434", 12 to "0\u00a0\u0442\u0440\u043b\u043d"),
            compactCurrency = mapOf(3 to "0\u00a0\u0442\u044b\u0441.\u00a0\u00a4", 6 to "0\u00a0\u043c\u043b\u043d\u00a0\u00a4", 9 to "0\u00a0\u043c\u043b\u0440\u0434\u00a0\u00a4", 12 to "0\u00a0\u0442\u0440\u043b\u043d\u00a0\u00a4"),
            currencyPattern = "#,##0.00\u00a0\u00a4",
        ),
        "tr" to NumberSymbols(
            decimal = ",", group = ".", minus = "-", primaryGrouping = 3, secondaryGrouping = 3,
            compact = mapOf(3 to "0\u00a0B", 6 to "0\u00a0Mn", 9 to "0\u00a0Mr", 12 to "0\u00a0Tn"),
            compactCurrency = mapOf(3 to "\u00a40\u00a0B", 6 to "\u00a40\u00a0Mn", 9 to "\u00a40\u00a0Mr", 12 to "\u00a40\u00a0Tn"),
            currencyPattern = "\u00a4#,##0.00",
        ),
        "uk" to NumberSymbols(
            decimal = ",", group = "\u00a0", minus = "-", primaryGrouping = 3, secondaryGrouping = 3,
            compact = mapOf(3 to "0\u00a0\u0442\u0438\u0441.", 6 to "0\u00a0\u043c\u043b\u043d", 9 to "0\u00a0\u043c\u043b\u0440\u0434", 12 to "0\u00a0\u0442\u0440\u043b\u043d"),
            compactCurrency = mapOf(3 to "0\u00a0\u0442\u0438\u0441.\u00a0\u00a4", 6 to "0\u00a0\u043c\u043b\u043d\u00a0\u00a4", 9 to "0\u00a0\u043c\u043b\u0440\u0434\u00a0\u00a4", 12 to "0\u00a0\u0442\u0440\u043b\u043d\u00a0\u00a4"),
            currencyPattern = "#,##0.00\u00a0\u00a4",
        ),
        "vi" to NumberSymbols(
            decimal = ",", group = ".", minus = "-", primaryGrouping = 3, secondaryGrouping = 3,
            compact = mapOf(3 to "0\u00a0N", 6 to "0\u00a0Tr", 9 to "0\u00a0T", 12 to "0\u00a0NT"),
            compactCurrency = mapOf(3 to "0\u00a0N\u00a0\u00a4", 6 to "0\u00a0Tr\u00a0\u00a4", 9 to "0\u00a0T\u00a0\u00a4", 12 to "0\u00a0NT\u00a0\u00a4"),
            currencyPattern = "#,##0.00\u00a0\u00a4",
        ),
        "zh" to NumberSymbols(
            decimal = ".", group = ",", minus = "-", primaryGrouping = 3, secondaryGrouping = 3,
            compact = mapOf(3 to "0", 4 to "0\u4e07", 8 to "0\u4ebf", 12 to "0\u4e07\u4ebf"),
            compactCurrency = mapOf(3 to "0", 4 to "\u00a40\u4e07", 8 to "\u00a40\u4ebf", 12 to "\u00a40\u4e07\u4ebf"),
            currencyPattern = "\u00a4#,##0.00",
        ),
    )
}
