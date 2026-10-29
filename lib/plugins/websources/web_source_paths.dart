/// The rules a web source reads a page with: paths into JSON, CSS selectors
/// into HTML, and templates that put several values together.
///
/// Nothing here throws on a page that has changed shape: a path that leads
/// nowhere, a selector that matches nothing, or one this HTML parser cannot
/// evaluate all give no value.
library;

import 'package:html/dom.dart';
import 'package:xta/utils/json.dart';

/// One step of a JSON path.
sealed class JsonPathStep {
  const JsonPathStep();
}

class JsonPathKey extends JsonPathStep {
  final String key;

  const JsonPathKey(this.key);
}

class JsonPathIndex extends JsonPathStep {
  final int index;

  const JsonPathIndex(this.index);
}

/// `[*]`: every element of a list.
class JsonPathEvery extends JsonPathStep {
  const JsonPathEvery();
}

// Each step after the first starts with `.` or `[`, so a path can be split
// into steps only one way: a rule written to fail slowly cannot hang the
// app while its file is read.
final _jsonPath = RegExp(r'^\$?[^.\[\]{}@\s]*(?:\.[^.\[\]{}@\s]+|\[(?:\d+|\*)\][^.\[\]{}@\s]*)*$');
final _jsonStep = RegExp(r'([^.\[\]{}@\s]+)|\[(\d+|\*)\]');

/// Whether [path] is a JSON path: keys, `[n]`, `[*]`, and `$` for the page.
bool isJsonPath(String path) => path.isNotEmpty && _jsonPath.hasMatch(path);

/// The steps of [path], which must be a [isJsonPath].
List<JsonPathStep> jsonPathSteps(String path) => [
  for (final match in _jsonStep.allMatches(path.startsWith(r'$') ? path.substring(1) : path))
    if (match.group(1) case final key?)
      JsonPathKey(key)
    else if (match.group(2) == '*')
      const JsonPathEvery()
    else
      JsonPathIndex(int.parse(match.group(2)!)),
];

/// Every value at [path] in [root], skipping missing ones: one value for a
/// plain path, one per element for each `[*]`.
List<Object?> jsonValuesAt(Object? root, String path) => jsonPathSteps(
  path,
).fold<List<Object?>>([root], (values, step) => [for (final value in values) ..._stepInto(value, step)]);

Iterable<Object?> _stepInto(Object? value, JsonPathStep step) => switch (step) {
  JsonPathEvery() => Json(value).list.map((element) => element.raw).where((element) => element != null),
  JsonPathKey(:final key) => [Json(value)[key].raw].where((element) => element != null),
  JsonPathIndex(:final index) => [Json(value)[index].raw].where((element) => element != null),
};

/// A value as text: strings as they are, numbers and booleans written out,
/// anything else (a map, a list) none.
String? jsonText(Object? value) => switch (value) {
  final String text => text,
  num() || bool() => '$value',
  _ => null,
};

final _placeholder = RegExp(r'\{([^{}]+)\}');

/// Whether [rule] is a template: text with `{…}` placeholders.
bool isTemplate(String rule) => _placeholder.hasMatch(rule);

/// The placeholders of [template], in order.
List<String> templatePlaceholders(String template) => [
  for (final match in _placeholder.allMatches(template)) match.group(1)!,
];

/// [template] with each placeholder replaced by [valueOf] it; null when any
/// placeholder has no value (null, not empty), so a link is never built from
/// half its parts.
///
/// With [encode], values are escaped for an address, except when a single
/// placeholder is the whole template (`{link}`): that value is the address.
String? fillTemplate(String template, String? Function(String placeholder) valueOf, {bool encode = false}) {
  final whole = _placeholder.matchAsPrefix(template)?.end == template.length;
  var missing = false;
  final filled = template.replaceAllMapped(_placeholder, (match) {
    final value = valueOf(match.group(1)!);
    if (value == null) missing = true;
    return encode && !whole ? Uri.encodeComponent(value ?? '') : value ?? '';
  });
  return missing ? null : filled;
}

/// What an HTML rule reads: a selector inside the item (none: the item
/// itself) and the attribute to take (none: its text; `html`: its markup).
typedef HtmlRule = ({String selector, String? attribute});

final _attribute = RegExp(r'^[A-Za-z_:][-A-Za-z0-9_:.]*$');

/// [rule] as `selector`, `selector@attribute`, `@attribute` or
/// `selector@html`.
HtmlRule htmlRule(String rule) {
  final at = rule.lastIndexOf('@');
  if (at < 0 || !_attribute.hasMatch(rule.substring(at + 1))) {
    return (selector: rule.trim(), attribute: null);
  }
  return (selector: rule.substring(0, at).trim(), attribute: rule.substring(at + 1));
}

/// Whether package:html can read [selector]. Empty means the item itself.
bool isCssSelector(String selector) {
  if (selector.isEmpty) return true;
  try {
    Document.html('<html><body><p><a href="x">x</a></p></body></html>').querySelectorAll(selector);
    return true;
  } catch (_) {
    return false;
  }
}

/// Elements matching [selector] under [root]; none when it cannot be read.
List<Element> htmlSelectAll(Element root, String selector) {
  try {
    return root.querySelectorAll(selector);
  } catch (_) {
    return const [];
  }
}

/// Every value [rule] reads inside [item], in page order.
List<String> htmlValuesAt(Element item, String rule) {
  final (:selector, :attribute) = htmlRule(rule);
  final elements = selector.isEmpty ? [item] : htmlSelectAll(item, selector);
  return [
    for (final element in elements)
      if (_htmlValue(element, attribute) case final value? when value.isNotEmpty) value,
  ];
}

final _spaces = RegExp(r'\s+');

String? _htmlValue(Element element, String? attribute) => switch (attribute) {
  null => element.text.replaceAll(_spaces, ' ').trim(),
  'html' => element.innerHtml.trim(),
  _ => element.attributes[attribute]?.trim(),
};
