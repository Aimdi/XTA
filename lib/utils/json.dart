/// Non-throwing access into JSON nobody promised us.
///
/// The X API — and every other endpoint this app reads — is reverse-engineered,
/// so the house rule is that no field access may throw. The language makes that
/// possible (`result['data']?['legacy']?['count'] as int?`) but makes the unsafe
/// form shorter, so safety is enforced by review instead of by construction.
/// [Json] flips that: it is an extension type over the decoded value, erased at
/// compile time, whose `[]` cannot throw — a wrong key, a wrong shape or an
/// index past the end all yield `Json(null)`, and the typed getters turn that
/// into a plain `null` at the edge.
///
///     final count = Json(result)['data']['legacy']['favorite_count'].integer ?? 0;
///
/// The deep path is now the safe path. There is nothing shorter to reach for.
library;

extension type const Json(Object? raw) {
  /// Steps into a map by [key] or a list by index. Anything that does not fit —
  /// wrong shape, absent key, index out of range — is [Json] of null, so a
  /// whole path can be written without a single check along the way.
  Json operator [](Object key) {
    final value = raw;
    if (key is String && value is Map) {
      return Json(value[key]);
    }
    if (key is int && value is List && key >= 0 && key < value.length) {
      return Json(value[key]);
    }
    return const Json(null);
  }

  /// True when there is a value here at all. A `false` answers both "the field
  /// is missing" and "the field is null", which JSON cannot tell apart anyway.
  bool get exists => raw != null;

  String? get string => raw is String ? raw as String : null;

  bool? get boolean => raw is bool ? raw as bool : null;

  /// A number, tolerating the string-wrapped digits these endpoints are fond
  /// of — X quotes its 64-bit ids, and chart endpoints quote the odd price.
  double? get number {
    final value = raw;
    if (value is num) {
      return value.toDouble();
    }
    return value is String ? double.tryParse(value) : null;
  }

  int? get integer {
    final value = raw;
    if (value is int) {
      return value;
    }
    if (value is num) {
      return value.toInt();
    }
    return value is String ? int.tryParse(value) : null;
  }

  /// The elements, each wrapped for further stepping. Not a list is an empty
  /// list: iteration over a reshaped response simply visits nothing.
  List<Json> get list {
    final value = raw;
    return value is List ? [for (final element in value) Json(element)] : const [];
  }
}
