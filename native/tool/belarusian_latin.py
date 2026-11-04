#!/usr/bin/env python3
"""Fills XTA Native's Belarusian in Latin script, XTA's intl_be_Latn.arb.

Compose Multiplatform's resources cannot name a script (values-b+be+Latn is
refused), so the app reads Latin Belarusian from values-be-rXL, XL being one
of ISO 3166's private region codes (SettingsChoices.localeOf hands the
platforms be_XL), and each plugin keeps it in a folder of the same name.

For each string of a values/ file missing from its values-be-rXL copy, in the
app's resources and every plugin's strings, this writes:

- XTA's own Latin text, where XTA has the string (its English in intl_en.arb
  is the native English, and its Cyrillic in intl_be.arb the native
  values-be's) and every word of it is written from that Cyrillic, is a name,
  or is one of XTA_OWN_WORDS. Each word is spelt as XTA spells most of that
  word's forms, so its slips (a letter of another alphabet, a dropped
  diacritic) go;
- otherwise the values-be text in Latin letters, spelt the same way: where
  XTA's file writes a letter either way (ł or l, sierver or server, ś where it
  sounds), as it writes the word's forms most, else as it writes most of all
  (ł, ia/ie/io/iu after a consonant, la/le/lu after l).

It only adds: a string already in values-be-rXL stays as it is, so correcting
the result by hand is safe. It lists what a person should read: strings kept
as they are (values-be has no Cyrillic there), values-be's Russian, which it
leaves out, and XTA's Latin it set aside.

    python3 -I native/tool/belarusian_latin.py [--dry-run]
"""
import argparse
import collections
import difflib
import glob
import itertools
import json
import os
import re
import sys
import xml.etree.ElementTree as ET

LATIN_FOLDER = 'values-be-rXL'
NATIVE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REPO = os.path.dirname(NATIVE)

# Cyrillic to Łacinka, as XTA's intl_be_Latn.arb writes it.
VOWELS = 'аеёіоуыэюяи'
IOTATED = {'е': 'e', 'ё': 'o', 'ю': 'u', 'я': 'a'}
LETTERS = {
    'а': 'a', 'б': 'b', 'в': 'v', 'г': 'h', 'ґ': 'g', 'д': 'd', 'ж': 'ž', 'з': 'z', 'і': 'i', 'и': 'i',
    'й': 'j', 'к': 'k', 'м': 'm', 'н': 'n', 'о': 'o', 'п': 'p', 'р': 'r', 'с': 's', 'т': 't', 'у': 'u',
    'ў': 'ŭ', 'ф': 'f', 'х': 'ch', 'ц': 'c', 'ч': 'č', 'ш': 'š', 'щ': 'šč', 'ы': 'y', 'э': 'e',
}
BEFORE_SOFT_SIGN = {'ц': 'ć', 'з': 'ź', 'с': 'ś', 'н': 'ń', 'л': 'l'}
SOFTENING = 'ьеёюяі'
HARD = set('рчшжтд')
# Letters Belarusian has not and Russian has, and Russian's adjective endings (новый, белый; not Belarusian's
# каментарый or публікацый): a values-be text with them is Russian, to translate.
RUSSIAN = re.compile(r'[ищъ]|(?<=[бвдзлмнпст])ый\b', re.I)
APOSTROPHES = "'’ʼ"
CYRILLIC_WORD = re.compile(r"[\u0400-\u04ff]+(?:\\?['’ʼ][\u0400-\u04ff]+)*")
LATIN_WORD = re.compile(r"[^\W\d_]+(?:['’ʼ][^\W\d_]+)*")
ICU_PLACEHOLDER = re.compile(r'\{(\w+)\}')
NATIVE_PLACEHOLDER = re.compile(r'%\d+\$[sd]')
LACINKA = set('abcćčdefghijklłmnńoprsśštuŭvyzźž')
# Letters the Cyrillic decides (ч ш ж ў ы), and the soft ones a word may add to
# its Cyrillic's (ць сь зь нь, or as it sounds) but never drop.
DECIDED = 'čšžŭy'
SOFT = 'ćśźń'
# Letters of other alphabets XTA's file has where it means these.
STRAYS = str.maketrans({'ĺ': 'l', 'Ĺ': 'L', 'ė': 'e', 'Ė': 'E', 'ĉ': 'ć', 'Ĉ': 'Ć'})
SKELETON = str.maketrans('łĺćčśšźžńŭėĉęąów', 'llccsszznueceaov')


def has_cyrillic(text):
    return any('\u0400' <= c <= '\u04ff' for c in text)


def cased(source, latin):
    """[latin] in the case of the word it was written from: ВЫКЛ, Выкл, выкл."""
    letters = [c for c in source if c.isalpha()]
    if len(letters) > 1 and all(c.isupper() for c in letters):
        return latin.upper()
    return latin[:1].upper() + latin[1:] if source[:1].isupper() else latin


def iotated(vowel, before, other):
    """я, е, ё, ю: ja at a word's start and after a vowel, la after l (or lia), ia after another consonant (or a,
    as in server); after a consonant that is never soft (р, ч, ш, ж, т, д, as a Russian spelling has them) a."""
    if before == 'л':
        return 'i' + vowel if other() else vowel
    if before in HARD:
        return vowel
    if before and before not in VOWELS and before not in "ўйьъ'":
        return vowel if other() else 'i' + vowel
    return 'j' + vowel


def softened_by_next(word, i):
    """Whether the consonant after [i] is soft, as с is before ці in знайсці: ś where XTA's file writes it as it sounds."""
    j = i + 1
    if j >= len(word) or word[j] in VOWELS or word[j] in "ьйўъ'":
        return False
    j += 2 if word[j:j + 2] == 'дз' else 1
    return word[j:j + 1] in SOFTENING if j < len(word) else False


def consonant(word, i, other):
    """The Latin of the consonant at [i] of [word], and how many letters it took."""
    c, after = word[i], word[i + 1:i + 2]
    if c == 'д' and after == 'з':
        return ('dź', 3) if word[i + 2:i + 3] == 'ь' else ('dz', 2)
    if c == 'л':
        soft = bool(after) and after in SOFTENING
        return ('l' if soft or other() else 'ł'), 1
    if after == 'ь' and c in BEFORE_SOFT_SIGN:
        return BEFORE_SOFT_SIGN[c], 2
    if c in 'сзцн' and softened_by_next(word, i) and other():
        return BEFORE_SOFT_SIGN[c], 1
    return LETTERS.get(c, c), 1


def letters_of(word, choices=None, points=None):
    """A lowercase Cyrillic word in Łacinka, letter by letter. Where XTA's file spells a letter either way,
    the way it writes most is taken, or the other where [choices] says so by the letter's position;
    [points] collects those positions."""
    out, i, before = [], 0, ''
    while i < len(word):
        c, at = word[i], i

        def other():
            if points is not None:
                points.append(at)
            return bool(choices and choices.get(at))

        step = 1
        if c in IOTATED:
            out.append(iotated(IOTATED[c], before, other))
        elif c == 'і' and before == "'":
            out.append('ji')
        elif c not in "ьъ'":
            latin, step = consonant(word, i, other)
            out.append(latin)
        before = word[i + step - 1]
        i += step
    return ''.join(out)


def spellings(word, form):
    """Which way XTA's [form] spells each letter of [word] that is written either way, by position, or None."""
    points = []
    letters_of(word, points=points)
    if len(points) > 10:
        return None
    for ways in itertools.product((False, True), repeat=len(points)):
        choices = dict(zip(points, ways))
        if letters_of(word, choices) == form:
            return choices
    return None


ENDINGS = sorted(set("""
    а у ы і е ю я ам ах амі аў ям ях ямі яў ом ём ой ёй ей ою ёю ая ае ыя ага ай ым ую ымі ых аму яя яе ія яга яй ім юю
    імі іх яму ць аць яць іць ыць уць юць ае яе аеш аем аеце аюць ую ыце іце айце ыла іла ала ла лі ло ўся ўшы цца ецца
    юцца ацца іцца ыцца ліся лася""".split()), key=len, reverse=True)


def stem(word):
    """[word] without its ending, roughly: what the forms of one word share."""
    for ending in ENDINGS:
        if word.endswith(ending) and len(word) - len(ending) >= 4:
            return word[:-len(ending)]
    return word


def plain(word):
    """A Cyrillic word as it is looked up: lowercase, every apostrophe the same."""
    word = word.replace("\\'", "'")
    return ''.join("'" if c in APOSTROPHES else c for c in word.lower())


def skeleton(word):
    """What a Latin word keeps whatever its diacritics, an i softening a vowel and other alphabets' spellings: matches it to its Cyrillic."""
    s = word.lower()
    for spelt, letter in (('cz', 'c'), ('sz', 's'), ('sh', 's'), ('zh', 'z'), ('ch', 'c')):
        s = s.replace(spelt, letter)
    return re.sub(r"i(?=[aeou])|['’ʼ]", '', s.translate(SKELETON))


def misspelt(word):
    """A Latin word Łacinka does not spell so: another alphabet's letter or digraph, an apostrophe."""
    low = word.lower()
    return any(c.isalpha() and c not in LACINKA for c in low) or bool(re.search(r"cz|sz|['’ʼ]", low))


def strays(word, latin):
    """Whether [word] writes the letters its Cyrillic decides otherwise than [latin], or drops a soft one."""
    low, want = word.lower(), latin.lower()
    return any(low.count(c) != want.count(c) for c in DECIDED) or any(low.count(c) < want.count(c) for c in SOFT)


def closest(found, candidates, spelt=lambda c: c):
    return max(candidates, key=lambda c: difflib.SequenceMatcher(None, spelt(c).lower(), found.lower()).ratio())


class Spelling:
    """How XTA's file spells each word's family where it spells a letter either way (ł or l, sierver or server):
    the way it writes the family's words most, else the way it writes most of all."""

    def __init__(self, pairs):
        votes = collections.defaultdict(collections.Counter)
        for cyrillic, latin in pairs:
            for word, form in self.aligned(cyrillic, latin):
                base = stem(word)
                for position, other in (spellings(word, form.lower()) or {}).items():
                    if position < len(base):
                        votes[(base, position)][other] += 1
        self.choices = collections.defaultdict(dict)
        for (base, position), counts in votes.items():
            if counts[True] > counts[False]:
                self.choices[base][position] = True

    @staticmethod
    def aligned(cyrillic, latin):
        """Each word of [cyrillic] with the word of [latin] written from it, where XTA wrote it rightly."""
        targets = collections.defaultdict(list)
        for found in LATIN_WORD.findall(latin):
            targets[skeleton(found)].append(found)
        for word in CYRILLIC_WORD.findall(cyrillic):
            key = plain(word)
            letters = letters_of(key)
            candidates = targets.get(skeleton(letters))
            if candidates:
                form = closest(letters, candidates)
                if not misspelt(form) and not strays(form, letters):
                    yield key, form

    def spelt(self, key):
        return letters_of(key, self.choices.get(stem(key)))

    def word(self, source):
        if source[:1] == 'Ў' and not source[1:2].isupper():
            source = 'У' + source[1:]
        return cased(source, self.spelt(plain(source)))

    def text(self, raw):
        """Every Cyrillic word of [raw] in Latin; escapes, entities and placeholders as they are."""
        return CYRILLIC_WORD.sub(lambda m: self.word(m.group()), raw)

    def repaired(self, latin, cyrillic, english):
        """XTA's Latin [latin], each word written from one of [cyrillic] spelt as its family is; names stay."""
        targets = collections.defaultdict(list)
        for word in CYRILLIC_WORD.findall(cyrillic):
            key = plain(word)
            targets[skeleton(letters_of(key))].append(key)
        kept = english_words(english + ' ' + cyrillic)

        def fix(m):
            found = m.group()
            if found.lower() in kept:
                return found
            found = respelt(found)
            keys = targets.get(skeleton(found))
            return cased(found, self.spelt(closest(found, keys, letters_of))) if keys else found

        return LATIN_WORD.sub(fix, latin)

    def unchecked(self, latin, cyrillic, english):
        """The words of XTA's [latin] written from no word of [cyrillic], nor names [english] or [cyrillic] have."""
        known = {skeleton(self.word(w)) for w in CYRILLIC_WORD.findall(cyrillic)}
        kept = english_words(english + ' ' + cyrillic) | XTA_OWN_WORDS
        return [w for w in LATIN_WORD.findall(NATIVE_PLACEHOLDER.sub(' ', latin))
                if w.lower() not in kept and (misspelt(w) or skeleton(w) not in known)]


# Words of XTA's Latin that are not its Cyrillic's yet good Belarusian, read and
# kept: its own wording, as Akaunty for Уліковыя запісы.
XTA_OWN_WORDS = set('''
    adčynić adkliučyć adpisacca akaunty albo bolš danyja dałučyŭsia hetaja infarmacyji kataloh lepšym łasia medyja niama
    nić padpisacca patrabuje pierakliučyć pierazapusku pravieryć sćahnuć svaje usio uznaŭleńnie vyniku zachavana začynić
    zakončyłasia złučennie znojdzieny
'''.split())


def english_words(text):
    """The words of a text in Latin letters, the names a translation keeps."""
    return {w.lower() for w in re.findall(r'[^\W\d_]+', ICU_PLACEHOLDER.sub(' ', text)) if not has_cyrillic(w)}


def respelt(word):
    """A word of XTA's Latin in Łacinka's letters where it has another alphabet's: ĺ, ė, ĉ, w, cz, an apostrophe."""
    word = word.translate(STRAYS)
    for spelt, letter in (('cz', 'č'), ('Cz', 'Č'), ('w', 'v'), ('W', 'V')):
        word = word.replace(spelt, letter)
    return ''.join(c for c in word if c not in APOSTROPHES)


# XTA's ARB files.
def icu_forms(message):
    """An ARB message as {'': text}, or a plural's texts by category."""
    m = re.match(r'^\{(\w+),\s*plural,\s*(.*)\}$', message.strip(), re.S)
    if not m:
        return {'': message}
    forms, body, i = {}, m.group(2), 0
    while True:
        head = re.match(r'\s*(=?\w+)\s*\{', body[i:])
        if not head:
            return forms
        start = end = i + head.end()
        depth = 1
        while depth:
            depth += {'{': 1, '}': -1}.get(body[end], 0)
            end += 1
        forms[head.group(1)] = body[start:end - 1]
        i = end


def main_form(forms):
    return forms.get('other', forms.get('', ''))


def normalized(text):
    """A text as two files are compared: placeholders, quotes and spaces alike."""
    text = NATIVE_PLACEHOLDER.sub('{}', ICU_PLACEHOLDER.sub('{}', text))
    for curly, straight in (('’', "'"), ('‘', "'"), ('“', '"'), ('”', '"'), ('…', '...')):
        text = text.replace(curly, straight)
    return ' '.join(text.split())


def placeholders_fit(english, forms):
    """As the strings tests ask: every form says the English placeholders, a zero, one or two may say one in words."""
    wanted = set(NATIVE_PLACEHOLDER.findall(main_form(english)))
    for quantity, text in forms.items():
        found = set(NATIVE_PLACEHOLDER.findall(text))
        in_words = quantity in ('zero', 'one', 'two') and found <= wanted and len(wanted - found) <= 1
        if found != wanted and not in_words:
            return False
    return True


class Xta:
    """XTA's English, Cyrillic and Latin Belarusian by key, and the keys of each English text."""

    def __init__(self, l10n):
        def read(lang):
            with open(os.path.join(l10n, f'intl_{lang}.arb'), encoding='utf-8') as f:
                return {k: icu_forms(v) for k, v in json.load(f).items() if not k.startswith('@') and isinstance(v, str)}

        self.en, self.be, self.latin = read('en'), read('be'), read('be_Latn')
        self.by_english = collections.defaultdict(list)
        for key, forms in self.en.items():
            self.by_english[('' not in forms, normalized(main_form(forms)))].append(key)
        self.spelling = Spelling(self.pairs())

    def pairs(self):
        """XTA's Cyrillic and Latin texts of a form, where its Latin is Latin."""
        for key, latin in self.latin.items():
            cyrillic, english = self.be.get(key, {}), self.en.get(key, {})
            for form, text in latin.items():
                source = cyrillic.get(form, '')
                if has_cyrillic(source) and not has_cyrillic(text) and text != english.get(form):
                    yield source, text

    def match(self, english, cyrillic):
        """XTA's key for a native string: the same English and, in every form both have, the same Cyrillic."""
        plural = '' not in english
        for key in self.by_english.get((plural, normalized(main_form(english))), []):
            theirs = self.be.get(key, {})
            common = [q for q in cyrillic if q in theirs]
            if common and all(normalized(theirs[q]) == normalized(cyrillic[q]) for q in common):
                return key
        return None

    def latin_of(self, key, english, cyrillic):
        """XTA's Latin for [key] in the native string's forms and placeholders, or None where it has none to give."""
        latin, theirs, their_english = self.latin.get(key, {}), self.be.get(key, {}), self.en[key]
        names = placeholder_names(main_form(their_english), main_form(english))
        untranslated = {normalized(t) for t in their_english.values()}
        if names is None or not all(q in latin for q in cyrillic):
            return None
        forms = {}
        for quantity in cyrillic:
            text = fill(latin[quantity], names)
            if text is None or has_cyrillic(text) or normalized(text) in untranslated:
                return None
            forms[quantity] = self.spelling.repaired(text, theirs.get(quantity, main_form(theirs)), ' '.join(their_english.values()))
        return forms if placeholders_fit(english, forms) else None


def placeholder_names(xta_english, english):
    """XTA's placeholder names, mapped to the native placeholders they stand for in the same English, or None."""
    theirs, ours = ICU_PLACEHOLDER.findall(xta_english), NATIVE_PLACEHOLDER.findall(english)
    if len(theirs) != len(ours):
        return None
    names = {}
    for name, native in zip(theirs, ours):
        if names.setdefault(name, native) != native:
            return None
    return names


def fill(text, names):
    if any(name not in names for name in ICU_PLACEHOLDER.findall(text)):
        return None
    return ICU_PLACEHOLDER.sub(lambda m: names[m.group(1)], text)


# The strings files.
ELEMENT = re.compile(r'(?s)[ \t]*<(?P<tag>string|plurals) name="(?P<name>[^"]+)"[^>]*>(?P<body>.*?)</(?P=tag)>[ \t]*\n?')
ITEM = re.compile(r'(?s)(<item quantity="(\w+)"[^>]*>)(.*?)(</item>)')


def displayed(raw):
    """A text as the app shows it: XML's entities, Android's quoting and its \\n and \\' escapes read."""
    text = (ET.fromstring(f'<t>{raw}</t>').text or '').strip()
    if len(text) >= 2 and text[0] == text[-1] == '"':
        text = text[1:-1]
    else:
        text = ' '.join(text.split())
    return re.sub(r'\\(.)', lambda m: {'n': '\n', 't': '\t'}.get(m.group(1), m.group(1)), text)


def read_entries(path):
    """A strings file's entries in order: name -> (its element as written, its texts by quantity, '' for a string)."""
    if not os.path.exists(path):
        return {}
    with open(path, encoding='utf-8') as f:
        raw = f.read()
    ET.fromstring(raw)
    entries = {}
    for m in ELEMENT.finditer(raw):
        body = m.group('body')
        items = ITEM.findall(body) if m.group('tag') == 'plurals' else [('', '', body, '')]
        entries[m.group('name')] = (m.group(0), {q: displayed(text) for _, q, text, _ in items})
    return entries


def encoded(text, android):
    """[text] as a strings file writes it; the plugins' files ([android]) escape quotes as Android does."""
    text = text.replace('\\', '\\\\').replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;').replace('\n', '\\n')
    if android:
        text = text.replace("'", "\\'").replace('"', '\\"')
        text = '\\' + text if text[:1] in '@?' else text
    return text


def with_texts(element, texts):
    """[element] with its text, or each plural item's, replaced by [texts] (by quantity)."""
    if '' in texts:
        return element[:element.index('>') + 1] + texts[''] + element[element.rindex('</string>'):]
    return ITEM.sub(lambda m: m.group(1) + texts.get(m.group(2), m.group(3)) + m.group(4), element)


class Filler:
    """One run: what it adds to each file, and what a person should read."""

    def __init__(self, xta, dry_run):
        self.xta, self.dry_run = xta, dry_run
        self.counts = collections.Counter()
        self.notes = collections.defaultdict(list)

    def latin(self, where, name, english, cyrillic, android):
        """The Latin element for one string, made from its values-be element."""
        element, forms = cyrillic
        if any(RUSSIAN.search(t) for t in forms.values()):
            self.notes['left out: values-be has Russian there, which a person translates'].append(f'{where} {name}')
            return None
        key = self.xta.match(english, forms)
        texts = self.xta.latin_of(key, english, forms) if key else None
        if texts is not None and self.checked(where, name, key, texts):
            self.counts['xta'] += 1
            return with_texts(element, {q: encoded(t, android) for q, t in texts.items()})
        if not any(has_cyrillic(t) for t in forms.values()):
            self.counts['kept'] += 1
            self.notes['kept as in values-be, which has no Cyrillic there (a name, or English to translate)'].append(f'{where} {name}')
            return element
        self.counts['written'] += 1
        start, end = element.index('>') + 1, element.rindex('</')
        return element[:start] + self.xta.spelling.text(element[start:end]) + element[end:]

    def checked(self, where, name, key, texts):
        """Whether every word of XTA's Latin is written from its Cyrillic, a name, or one of XTA_OWN_WORDS."""
        cyrillic = ' '.join(self.xta.be.get(key, {}).values())
        odd = self.xta.spelling.unchecked(' '.join(texts.values()), cyrillic, ' '.join(self.xta.en[key].values()))
        if odd and has_cyrillic(cyrillic):
            self.notes["XTA's Latin set aside for values-be's, for words its Cyrillic does not have (XTA_OWN_WORDS keeps a good one)"].append(
                f'{where} {name} ({key}): {" ".join(odd)}')
        return not odd or not has_cyrillic(cyrillic)

    def fill(self, folder, android):
        for path in sorted(glob.glob(os.path.join(folder, 'values', '*.xml'))):
            self.fill_file(folder, os.path.basename(path), android)

    def fill_file(self, folder, file, android):
        english = read_entries(os.path.join(folder, 'values', file))
        cyrillic_path = os.path.join(folder, 'values-be', file)
        cyrillic = read_entries(cyrillic_path)
        target = os.path.join(folder, LATIN_FOLDER, file)
        latin = read_entries(target)
        where = os.path.relpath(target, NATIVE)
        missing = [n for n in english if n not in latin]
        left_out = [n for n in missing if n not in cyrillic]
        if left_out:
            self.notes['left out: values-be has no text for them'].append(f'{where} {" ".join(left_out)}')
        made = {n: self.latin(where, n, english[n][1], cyrillic[n], android) for n in missing if n in cyrillic}
        added = {n: element for n, element in made.items() if element is not None}
        if not added:
            return
        self.counts['files'] += 1
        if self.dry_run:
            return
        if latin:
            with open(target, encoding='utf-8') as f:
                text = inserted(f.read(), added, list(cyrillic))
        else:
            text = new_file(cyrillic_path, added, self.xta.spelling)
        write(target, text)

    def report(self):
        c = self.counts
        print(f"{LATIN_FOLDER}: {c['xta']} strings from XTA's Latin, {c['written']} written from values-be, "
              f"{c['kept']} kept as they are, in {c['files']} files" + (' (a dry run: nothing written)' if self.dry_run else ''))
        for title, lines in self.notes.items():
            print(f'\n{title}:')
            for line in lines:
                print(f'  {line}')


def new_file(cyrillic_path, added, spelling):
    """values-be's file in Latin: its comments and layout, each string in [added] where values-be has it."""
    with open(cyrillic_path, encoding='utf-8') as f:
        text = ELEMENT.sub(lambda m: added.get(m.group('name'), ''), f.read())
    text = re.sub(r'<!--.*?-->', lambda m: spelling.text(m.group()), text, flags=re.S)
    return re.sub(r'\n{3,}', '\n\n', re.sub(r'(<resources>\n)\n+', r'\1', text))


def inserted(text, added, order):
    """[text] with each element of [added] after the one before it in values-be's [order], else first."""
    for name in [n for n in order if n in added]:
        anchor = None
        for before in reversed(order[:order.index(name)]):
            m = re.search(rf'(?s)<(string|plurals) name="{re.escape(before)}"[^>]*>.*?</\1>[ \t]*\n?', text)
            if m:
                anchor = m.end()
                break
        if anchor is None:
            anchor = first_slot(text)
        element = added[name] if added[name].endswith('\n') else added[name] + '\n'
        text = text[:anchor] + element + text[anchor:]
    return text


def first_slot(text):
    """Where an element goes when none comes before it: after <resources> and the comments that open it."""
    start = text.index('<resources>') + len('<resources>')
    m = re.match(r'[ \t]*\n(?:[ \t]*<!--.*?-->[ \t]*\n|[ \t]*\n)*', text[start:], re.S)
    return start + (m.end() if m else 0)


def write(path, text):
    ET.fromstring(text)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as out:
        out.write(text)


def string_folders():
    """The app's resources, then each plugin's strings: whether its files are a plugin's, with Android's escapes."""
    yield os.path.join(NATIVE, 'ui', 'src', 'commonMain', 'composeResources'), False
    for folder in sorted(glob.glob(os.path.join(NATIVE, 'plugins', '*', 'strings'))):
        yield folder, True


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    parser.add_argument('--dry-run', action='store_true', help='say what would be added, write nothing')
    args = parser.parse_args(argv)
    filler = Filler(Xta(os.path.join(REPO, 'lib', 'l10n')), args.dry_run)
    for folder, android in string_folders():
        filler.fill(folder, android)
    filler.report()


if __name__ == '__main__':
    main(sys.argv[1:])
