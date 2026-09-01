/// The stored vocabulary of the image and video quality settings.
///
/// The pref itself holds a string, so this is the parse at the edge: an
/// exhaustive switch on [MediaQuality] cannot silently fall through a
/// mis-spelt arm the way a switch on the raw string can — the compiler
/// insists every quality is answered. A stored value the vocabulary does not
/// know — including the legacy `disabled`, which a migration retired — reads
/// as the caller's fallback.
enum MediaQuality {
  thumb('thumb'),
  small('small'),
  medium('medium'),
  large('large');

  const MediaQuality(this.stored);

  /// What the pref actually holds for this choice.
  final String stored;

  static MediaQuality fromStored(String? value, {required MediaQuality fallback}) {
    for (final quality in values) {
      if (quality.stored == value) {
        return quality;
      }
    }
    return fallback;
  }
}
