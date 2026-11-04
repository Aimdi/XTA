#!/usr/bin/env bash
# Fetches the desktop (JVM) jars of Compose Multiplatform and the libraries
# XTA Native's :ui module uses, with all their transitive runtime dependencies,
# from Maven Central: what uicheck.sh compiles against on machines where
# Gradle's parallel downloads get HTTP 429. One request at a time; the pause
# between requests doubles on 429 and 5xx and shrinks again on success.
#
#   KOTLINC=<kotlinc home> COMPOSE_LIBS_HOME=<dir> bash native/tool/compose-libs.sh \
#       --scan native/gradle/libs.versions.toml [options] [group:artifact:version ...]
#
# It reads Gradle module metadata the way Gradle does: the desktop JVM variant
# of each library, the highest version anything asks for. The jars land in
# $COMPOSE_LIBS_HOME/<compose version>/ (rebuilt on every run, with
# classpath.txt, compile-classpath.txt and resolved.txt); downloads are kept in
# $COMPOSE_LIBS_HOME/.store,
# so a re-run fetches only what changed. Gradle's and Maven's local caches are
# read before the network.
#
# classpath.txt lists every jar, as the module runs; compile-classpath.txt only
# those a module that declares the roots sees when it compiles, as Gradle builds
# its compile classpath: each library's API variant (a POM's compile scope), so
# a library another one needs only at run time is left out of it.
#
# For the desktop, lifecycle, savedstate, collection and annotation come from
# Google Maven alone. With KOTLINC set they are built instead from the sources
# JetBrains publishes on Maven Central for the same androidx code (see
# "Google Maven only" below); they are good to compile against, and close to,
# not the same as, Google's jars.
#
# Options (all optional):
#   --scan FILE        take versions and coordinates from a Gradle build file or
#                      version catalog (repeatable)
#   --compose V        Compose Multiplatform version (default 1.8.2)
#   --lifecycle V      org.jetbrains.androidx.lifecycle version
#   --navigation V     org.jetbrains.androidx.navigation version
#   --coil V           Coil 3 version
#   --icons V          material-icons version (1.7.3 from Compose 1.8 on)
#   --icons-extended   also fetch material-icons-extended (one large jar)
#   --skiko-runtime OS also fetch Compose for desktop with Skiko's native runtime
#                      (e.g. linux-x64), to run a window
#   --no-core          leave out :core's own dependencies (okhttp, coroutines, ...)
#   --label NAME       name of the output directory (default: the Compose version)
#   --delay SECONDS    least pause between requests (default 1)
#   --dry-run          resolve and list, download no jars
# Lifecycle, navigation and Coil default to the versions made for the chosen
# Compose version where it is in the table below; otherwise pass them.
# Exits 3 when something stays missing.
set -euo pipefail
exec python3 -I - "$@" <<'PY'
import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
import xml.etree.ElementTree as ET
from collections import defaultdict, deque
from functools import cmp_to_key

# Compose Multiplatform version -> lifecycle, navigation, Coil versions built for it
# (Coil 3.2.0 is the last one built with Kotlin 2.1, as native/gradle/libs.versions.toml says).
COMPANIONS = {
    "1.8.2": ("2.9.1", "2.9.0-beta03", "3.2.0"),
}
# What :core, :networks, :store and :services compile against (native/*/build.gradle.kts).
COROUTINES = "1.10.2"
CORE_DEPS = [
    "org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1",
    f"org.jetbrains.kotlinx:kotlinx-coroutines-core:{COROUTINES}",
    "com.squareup.okhttp3:okhttp:4.12.0",
    "org.jsoup:jsoup:1.18.3",
]
# What :core and the modules after it declare with implementation(...): theirs alone,
# so not on the compile classpath of a module that depends on them.
IMPLEMENTATION_ONLY = {"org.jsoup:jsoup"}
# kotlinc brings its own standard library; a second one on the classpath only conflicts.
SKIP = re.compile(r"^org\.jetbrains\.kotlin:kotlin-stdlib(-common|-jdk7|-jdk8)?$")
# Where Kotlin Multiplatform libraries publish their desktop JVM module.
PLATFORM_SUFFIX = [
    ("org.jetbrains.compose", "-desktop"),
    ("org.jetbrains.androidx", "-desktop"),
    ("org.jetbrains.kotlinx", "-jvm"),
    ("io.coil-kt.coil3", "-jvm"),
    ("com.squareup.okio", "-jvm"),
]
# Groups Google publishes only on its own Maven repository, never on Maven Central.
GOOGLE_ONLY = ("androidx.", "com.google.android.", "com.android.")
# Metadata is downloaded data: only plain names become paths.
SAFE = re.compile(r"[A-Za-z0-9_][A-Za-z0-9_.+-]*")


def log(*parts):
    print(*parts, file=sys.stderr, flush=True)


# ---------------------------------------------------------------- versions
QUALIFIERS = {"dev": 0, "rc": 2, "snapshot": 3, "final": 4, "ga": 5, "release": 6, "sp": 7}


def _cmp_part(a, b):
    if a.isdigit() and b.isdigit():
        return (int(a) > int(b)) - (int(a) < int(b))
    if a.isdigit() or b.isdigit():
        return 1 if a.isdigit() else -1
    ra, rb = QUALIFIERS.get(a.lower(), 1), QUALIFIERS.get(b.lower(), 1)
    if ra != rb:
        return (ra > rb) - (ra < rb)
    return (a.lower() > b.lower()) - (a.lower() < b.lower())


def vcmp(x, y):
    """Gradle's version order: 1.0-beta < 1.0-rc < 1.0 < 1.0.1."""
    px, py = re.findall(r"\d+|[A-Za-z]+", x), re.findall(r"\d+|[A-Za-z]+", y)
    for a, b in zip(px, py):
        c = _cmp_part(a, b)
        if c:
            return c
    if len(px) == len(py):
        return 0
    longer, sign = (px, 1) if len(px) > len(py) else (py, -1)
    return sign if longer[min(len(px), len(py))].isdigit() else -sign


def vmax(versions):
    return max(versions, key=cmp_to_key(vcmp))


def version_of(spec):
    """The version a Gradle-metadata dependency asks for, or None."""
    if not spec:
        return None
    for key in ("strictly", "requires", "prefers"):
        v = spec.get(key)
        if v:
            return range_floor(v)
    return None


def range_floor(v):
    m = re.match(r"^[\[\(]([^,]*),", v)
    if m:
        log(f"  note: version range {v}: taking {m.group(1) or '?'}")
        return m.group(1) or None
    return v.strip("[]")


# ---------------------------------------------------------------- fetching
class Repo:
    def __init__(self, base, store, delay, tries, refresh):
        self.base, self.store, self.delay, self.tries, self.refresh = base, store, delay, tries, refresh
        self.last = 0.0
        self.pace = delay
        self.requests = 0
        self.throttled = 0
        home = os.path.expanduser("~")
        gradle = os.environ.get("GRADLE_USER_HOME", os.path.join(home, ".gradle"))
        self.gradle_cache = os.path.join(gradle, "caches", "modules-2", "files-2.1")
        self.m2 = os.path.join(home, ".m2", "repository")
        self.tmp = os.path.join(store, ".tmp")
        self.cookies = os.path.join(self.tmp, "cookies")
        os.makedirs(self.tmp, exist_ok=True)

    def fetch(self, g, m, v, name, sha256=None, sha1=None, cached_as=None):
        """Local path of a repository file, downloading it if needed; None when absent."""
        if not all(SAFE.fullmatch(part) for part in (g, m, v, name)):
            log(f"  warning: skipping unsafe coordinates {g}:{m}:{v} {name}")
            return None
        rel = f"{g.replace('.', '/')}/{m}/{v}/{name}"
        dest = os.path.join(self.store, rel)
        if os.path.isfile(dest):
            return dest
        if os.path.exists(dest + ".missing") and not self.refresh:
            return None
        local = self._local(g, m, v, (name, cached_as or name), rel)
        if local and self._matches(local, sha256, sha1):
            self._place(local, dest, copy=True)
            return dest
        got = self._download(rel)
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        if got is None:
            open(dest + ".missing", "w").close()
            return None
        tmp, header_sha1 = got
        if not self._matches(tmp, sha256, sha1 or header_sha1):
            os.remove(tmp)
            sys.exit(f"checksum mismatch for {self.base}/{rel}")
        self._place(tmp, dest, copy=False)
        return dest

    def has(self, g, m, v, name):
        """Whether a file is at hand without a request."""
        rel = f"{g.replace('.', '/')}/{m}/{v}/{name}"
        return os.path.isfile(os.path.join(self.store, rel)) or self._local(g, m, v, (name,), rel) is not None

    def _local(self, g, m, v, names, rel):
        d = os.path.join(self.gradle_cache, g, m, v)
        if os.path.isdir(d):
            for h in sorted(os.listdir(d)):
                for name in names:
                    p = os.path.join(d, h, name)
                    if os.path.isfile(p):
                        return p
        p = os.path.join(self.m2, rel)
        return p if os.path.isfile(p) else None

    @staticmethod
    def _matches(path, sha256, sha1):
        if not sha256 and not sha1:
            return True
        algo, want = ("sha256", sha256) if sha256 else ("sha1", sha1)
        h = hashlib.new(algo)
        with open(path, "rb") as f:
            for chunk in iter(lambda: f.read(1 << 20), b""):
                h.update(chunk)
        return h.hexdigest() == want.lower()

    @staticmethod
    def _place(src, dest, copy):
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        part = dest + ".part"
        (shutil.copyfile if copy else shutil.move)(src, part)
        os.replace(part, dest)

    def _download(self, rel):
        """One GET at a time. The pause between requests doubles on 429 and shrinks
        slowly on success, so the pace settles where the server stops refusing."""
        url = f"{self.base}/{rel}"
        for attempt in range(self.tries):
            pause = self.pace - (time.monotonic() - self.last)
            if pause > 0:
                time.sleep(pause)
            fd, tmp = tempfile.mkstemp(dir=self.tmp)
            os.close(fd)
            run = subprocess.run(
                ["curl", "-sS", "-L", "-o", tmp, "-D", tmp + ".h", "-w", "%{http_code}",
                 "-b", self.cookies, "-c", self.cookies,
                 "--connect-timeout", "30", "--max-time", "1200", url],
                capture_output=True, text=True)
            self.last = time.monotonic()
            self.requests += 1
            code = int(run.stdout.strip()) if run.stdout.strip().isdigit() else 0
            headers = self._headers(tmp + ".h")
            if code in (200, 404, 410):
                self.pace = max(self.delay, self.pace * 0.8)
            if code == 200:
                log(f"  got {rel}")
                return tmp, headers.get("x-checksum-sha1")
            os.remove(tmp)
            if code in (404, 410):
                return None
            if code not in (0, 408, 429, 500, 502, 503, 504):
                sys.exit(f"HTTP {code} for {url}: not retrying (a proxy policy denial is final)")
            self.throttled += 1
            self.pace = min(240, max(15, self.pace * 2))
            retry_after = headers.get("retry-after", "")
            wait = max(self.pace, int(retry_after) if retry_after.isdigit() else 0)
            reason = f"HTTP {code}" if code else (run.stderr.strip() or "no response")
            log(f"  {reason} for {rel}; waiting {wait:.0f}s (try {attempt + 1}/{self.tries})")
            time.sleep(max(0, wait - self.pace))
        sys.exit(f"giving up on {url} after {self.tries} tries")

    @staticmethod
    def _headers(path):
        try:
            with open(path, encoding="latin-1") as f:
                blocks = f.read().replace("\r", "").strip().split("\n\n")
            os.remove(path)
        except OSError:
            return {}
        lines = blocks[-1].split("\n")[1:] if blocks else []
        return {k.strip().lower(): val.strip() for k, _, val in (l.partition(":") for l in lines)}


# ---------------------------------------------------------------- metadata
class Node:
    """One module version as the desktop JVM sees it."""

    def __init__(self, deps=(), constraints=(), files=(), redirect=None, missing=None, platform=False):
        self.deps = list(deps)                # (group, module, version or None, is_platform, excludes)
        self.constraints = list(constraints)  # (group, module, version)
        self.files = list(files)              # (url, name, sha256, sha1)
        self.redirect = redirect              # (group, module, version) for available-at
        self.missing = missing                # why there is nothing for the desktop JVM, or None
        self.platform = platform


def jvm_runtime(variant):
    a = variant.get("attributes", {})
    return (a.get("org.gradle.category", "library") == "library"
            and a.get("org.jetbrains.kotlin.platform.type", "jvm") == "jvm"
            and a.get("org.gradle.jvm.environment", "standard-jvm") == "standard-jvm"
            and a.get("org.gradle.libraryelements", "jar") in ("jar", "classes")
            and not a.get("org.gradle.docstype")
            and not any(k.startswith(("org.jetbrains.kotlin.native", "org.jetbrains.kotlin.js",
                                      "org.jetbrains.kotlin.wasm")) for k in a))


def variant_rank(variant):
    """Like Gradle: a variant with attributes the desktop does not ask for (Skiko's
    ui=android) loses to one without."""
    a = variant.get("attributes", {})
    usage = a.get("org.gradle.usage", "")
    name = variant.get("name", "").lower()
    jvm_version = int(a.get("org.gradle.jvm.version", 0))
    unasked = sum(1 for k in a if not k.startswith(("org.gradle.", "org.jetbrains.kotlin.")))
    return ("runtime" in usage, -unasked, "android" not in name, "desktop" in name, "jvm" in name,
            -abs(jvm_version - 17) if jvm_version else 0)


def gradle_node(js, platform):
    variants = js.get("variants", [])
    if platform:
        picked = [v for v in variants if v.get("attributes", {}).get("org.gradle.category") in ("platform", "enforced-platform")]
    else:
        picked = [v for v in variants if jvm_runtime(v)]
    if not picked:
        return Node(missing=None if platform else "has no desktop/JVM variant", platform=platform)
    best = max(picked, key=variant_rank)
    at = best.get("available-at")
    if at:
        return Node(redirect=(at["group"], at["module"], at["version"]), platform=platform)
    deps = [(d["group"], d["module"], version_of(d.get("version")),
             d.get("attributes", {}).get("org.gradle.category") in ("platform", "enforced-platform"),
             frozenset((e.get("group", "*"), e.get("module", "*")) for e in d.get("excludes", [])))
            for d in best.get("dependencies", [])]
    constraints = [(c["group"], c["module"], version_of(c.get("version")))
                   for c in best.get("dependencyConstraints", [])]
    files = [(f["url"], f.get("name"), f.get("sha256"), f.get("sha1")) for f in best.get("files", [])
             if f["url"].endswith(".jar")]
    return Node(deps, constraints, files, platform=platform)


class Poms:
    """Effective POMs: parents, properties, dependencyManagement and imported BOMs."""

    def __init__(self, repo):
        self.repo = repo
        self.cache = {}

    @staticmethod
    def _children(el, tag):
        return [c for c in el if c.tag.split("}")[-1] == tag] if el is not None else []

    def _text(self, el, tag):
        found = self._children(el, tag)
        return found[0].text.strip() if found and found[0].text else None

    def effective(self, g, m, v):
        key = (g, m, v)
        if key in self.cache:
            return self.cache[key]
        path = self.repo.fetch(g, m, v, f"{m}-{v}.pom")
        if not path:
            self.cache[key] = None
            return None
        root = ET.parse(path).getroot()
        parent_el = self._children(root, "parent")
        parent = None
        if parent_el:
            p = parent_el[0]
            parent = self.effective(self._text(p, "groupId"), self._text(p, "artifactId"), self._text(p, "version"))
        props = dict(parent["props"]) if parent else {}
        for el in self._children(root, "properties"):
            props.update({c.tag.split("}")[-1]: (c.text or "").strip() for c in el})
        props.update({"project.groupId": g, "groupId": g, "project.artifactId": m,
                      "project.version": v, "version": v, "pom.version": v})
        if parent:
            props["project.parent.version"] = parent["version"]
        managed = dict(parent["managed"]) if parent else {}
        for dm in self._children(root, "dependencyManagement"):
            for d in self._deps(dm, props):
                if d["scope"] == "import":
                    bom = self.effective(d["group"], d["module"], d["version"])
                    for k, val in (bom["managed"].items() if bom else []):
                        managed.setdefault(k, val)
                else:
                    managed[(d["group"], d["module"])] = d
        deps = self._deps(root, props)
        packaging = self._text(root, "packaging") or "jar"
        result = {"props": props, "managed": managed, "deps": deps, "packaging": packaging, "version": v}
        self.cache[key] = result
        return result

    def _deps(self, el, props):
        out = []
        for holder in self._children(el, "dependencies"):
            for d in self._children(holder, "dependency"):
                get = lambda tag: self._interpolate(self._text(d, tag), props)
                excludes = frozenset((self._text(e, "groupId") or "*", self._text(e, "artifactId") or "*")
                                     for x in self._children(d, "exclusions") for e in self._children(x, "exclusion"))
                out.append({"group": get("groupId"), "module": get("artifactId"), "version": get("version"),
                            "scope": get("scope"), "optional": get("optional") == "true",
                            "classifier": get("classifier"), "type": get("type"), "excludes": excludes})
        return out

    @staticmethod
    def _interpolate(text, props):
        for _ in range(10):
            if not text or "${" not in text:
                break
            text = re.sub(r"\$\{([^}]+)\}", lambda mt: props.get(mt.group(1), mt.group(0)), text)
        return text

    def node(self, g, m, v, platform):
        pom = self.effective(g, m, v)
        if pom is None:
            return Node(missing="is not on the repository (Google Maven only?)", platform=platform)
        if platform:
            return Node(constraints=[(dg, dm, d["version"]) for (dg, dm), d in pom["managed"].items()], platform=True)
        deps = []
        for d in pom["deps"]:
            managed = pom["managed"].get((d["group"], d["module"]), {})
            scope = d["scope"] or managed.get("scope") or "compile"
            if scope not in ("compile", "runtime") or d["optional"] or d["classifier"] or d["type"] not in (None, "jar", "bundle"):
                continue
            deps.append((d["group"], d["module"], range_floor(d["version"] or managed.get("version") or "") or None,
                         False, d["excludes"] | managed.get("excludes", frozenset())))
        files = [(f"{m}-{v}.jar", None, None, None)] if pom["packaging"] in ("jar", "bundle") else []
        return Node(deps, files=files)


class Resolver:
    def __init__(self, repo):
        self.repo = repo
        self.poms = Poms(repo)
        self.nodes = {}
        self.central = re.search(r"//(repo1\.maven\.org|repo\.maven\.apache\.org)/", repo.base + "/") is not None

    def node(self, g, m, v, platform=False):
        key = (g, m, v, platform)
        if key not in self.nodes:
            self.nodes[key] = (None if platform else self._platform_module(g, m, v)) \
                or self._module_node(g, m, v, platform)
        return self.nodes[key]

    def _module_node(self, g, m, v, platform):
        if self.central and g.startswith(GOOGLE_ONLY):
            return Node(missing="is on Google Maven only", platform=platform)
        js = self._module_json(g, m, v)
        return gradle_node(js, platform) if js else self.poms.node(g, m, v, platform)

    def _module_json(self, g, m, v):
        path = self.repo.fetch(g, m, v, f"{m}-{v}.module")
        if not path:
            return None
        with open(path, encoding="utf-8") as f:
            return json.load(f)

    def _platform_module(self, g, m, v):
        """For a Kotlin Multiplatform library, its desktop module directly: the root
        module would only point there, and every request counts against the rate limit.
        The guess holds only if that module names this one as its root."""
        suffix = next((s for prefix, s in PLATFORM_SUFFIX if g.startswith(prefix)), None)
        if not suffix or m.endswith(("-desktop", "-jvm")) or self.repo.has(g, m, v, f"{m}-{v}.module"):
            return None
        js = self._module_json(g, m + suffix, v)
        component = (js or {}).get("component", {})
        if component.get("group") != g or component.get("module") != m or gradle_node(js, False).missing:
            return None
        return Node(redirect=(g, m + suffix, v))

    def api_edges(self, g, m, v, node):
        """The modules this one puts on the compile classpath of its users: the
        dependencies of its API variant, or a POM's compile scope."""
        if node.redirect:
            return [node.redirect[:2]]
        if node.missing or node.platform:
            return []
        js = self._module_json(g, m, v) if self.repo.has(g, m, v, f"{m}-{v}.module") else None
        if js is None:
            pom = self.poms.effective(g, m, v)
            return [(d["group"], d["module"]) for d in (pom or {}).get("deps", [])
                    if (d["scope"] or pom["managed"].get((d["group"], d["module"]), {}).get("scope") or "compile") == "compile"
                    and not d["optional"]]
        api = [x for x in js.get("variants", []) if jvm_runtime(x) and "api" in x.get("attributes", {}).get("org.gradle.usage", "")]
        if not api:
            return [(dg, dm) for dg, dm, *_ in node.deps]
        best = max(api, key=variant_rank)
        at = best.get("available-at")
        if at:
            return [(at["group"], at["module"])]
        return [(d["group"], d["module"]) for d in best.get("dependencies", [])
                if d.get("attributes", {}).get("org.gradle.category") not in ("platform", "enforced-platform")]

    def compile_closure(self, roots, selected):
        """The modules a module declaring `roots` compiles against."""
        seen, queue = set(), deque((g, m) for g, m, _ in roots)
        while queue:
            ga = queue.popleft()
            if ga in seen or ga not in selected:
                continue
            seen.add(ga)
            v, node = selected[ga]
            queue.extend(e for e in self.api_edges(ga[0], ga[1], v, node) if not SKIP.match(f"{e[0]}:{e[1]}"))
        return seen

    def resolve(self, roots):
        """Gradle-like: the highest version any reached module or constraint asks for wins.

        Versions only grow, so walking the graph again until nothing changes terminates.
        Excludes apply below the edge that declares them; a module reached by several
        paths keeps only the excludes they share.
        """
        wanted = defaultdict(set)
        for g, m, v in roots:
            wanted[(g, m)].add(v)
        while True:
            before = {k: frozenset(vs) for k, vs in wanted.items() if vs}
            constraints = defaultdict(set)
            selected, order, missing, reached = {}, [], {}, set()
            walked = {}
            queue = deque(((g, m), False, frozenset()) for g, m, _ in roots)
            while queue:
                ga, platform, excludes = queue.popleft()
                if not platform:
                    reached.add(ga)
                versions = wanted.get(ga, set()) | constraints.get(ga, set())
                if not versions:
                    continue
                v = vmax(versions)
                prior = walked.get((ga, platform))
                if prior and prior[1] == v:
                    if prior[0] <= excludes:
                        continue
                    excludes &= prior[0]
                walked[(ga, platform)] = (excludes, v)
                node = self.node(ga[0], ga[1], v, platform)
                if not platform:
                    if ga not in selected:
                        order.append(ga)
                    selected[ga] = (v, node)
                if node.missing:
                    missing[f"{ga[0]}:{ga[1]}:{v}"] = node.missing
                    continue
                for cg, cm, cv in node.constraints:
                    if cv:
                        constraints[(cg, cm)].add(cv)
                if node.redirect:
                    rg, rm, rv = node.redirect
                    wanted[(rg, rm)].add(rv)
                    queue.append(((rg, rm), False, excludes))
                for dg, dm, dv, dplatform, dex in node.deps:
                    if SKIP.match(f"{dg}:{dm}") or any(excluded(dg, dm, e) for e in excludes):
                        continue
                    if dv:
                        wanted[(dg, dm)].add(dv)
                    queue.append(((dg, dm), dplatform, excludes | dex))
            for ga in reached:
                wanted[ga] |= constraints.get(ga, set())
            if {k: frozenset(vs) for k, vs in wanted.items() if vs} == before:
                order = [ga for ga in order if ga in selected]
                unversioned = sorted(ga for ga in reached if ga not in selected)
                return selected, order, missing, unversioned


def excluded(g, m, pattern):
    pg, pm = pattern
    return pg in ("*", g) and pm in ("*", m)


# ---------------------------------------------------------------- Google Maven only
# For the desktop, JetBrains' lifecycle, savedstate, collection and annotation
# modules carry no classes: they depend on Google's own desktop builds, which are
# on Google Maven alone. JetBrains builds the same androidx code for the targets
# Google does not publish and puts its sources on Maven Central; compiled for the
# JVM, they stand in for Google's jars.
NOT_JVM = re.compile(r"(?i)native|web|js|wasm|ios|apple|darwin|macos|tvos|watchos|linux|mingw|unix|posix|android|test")
# Where JetBrains' module is only a placeholder (the code is on Google Maven for
# every target), the last JetBrains build that still had the code.
OLDER_SOURCES = {
    "androidx.annotation:annotation": ("org.jetbrains.compose.annotation-internal", "annotation-js", "1.7.3"),
    "androidx.collection:collection": ("org.jetbrains.compose.collection-internal", "collection-js", "1.7.3"),
}
# The JVM side of what the published source sets expect and only native and web
# provide: where jvmMain does not compile (lifecycle-runtime's needs
# androidx.arch.core) or is not published (collection's).
JVM_ACTUALS = {
    "collection": """package androidx.collection.internal

@PublishedApi
@Suppress("NOTHING_TO_INLINE")
internal actual inline fun floatFromBits(bits: Int): Float = java.lang.Float.intBitsToFloat(bits)
""",
    "lifecycle-runtime": """package androidx.lifecycle

internal actual class WeakReference<T : Any> actual constructor(reference: T) {
    private val reference = java.lang.ref.WeakReference(reference)
    actual fun get(): T? = reference.get()
}

// Compose for desktop runs its UI on the AWT event thread.
internal actual fun isMainThread(): Boolean = java.awt.EventQueue.isDispatchThread()
""",
}


def wrappers(selected, missing):
    """For each Google-only module, the JetBrains module that stands for it."""
    found = {}
    for (g, m), (v, node) in selected.items():
        if node.files or node.redirect or not g.startswith("org.jetbrains."):
            continue
        for dg, dm, dv, _, _ in node.deps:
            chosen = selected.get((dg, dm))
            key = f"{dg}:{dm}:{chosen[0] if chosen else dv}"
            if key in missing and dm == m:
                found.setdefault(key, (g, m, v))
    return found


def build_order(found, selected):
    """Wrappers before the wrappers that depend on them."""
    keys = {w[:2]: key for key, w in found.items()}
    done, ordered = set(), []

    def visit(ga, trail=()):
        if ga in done or ga in trail:
            return
        for dg, dm, *_ in selected[ga][1].deps:
            if (dg, dm) in keys:
                visit((dg, dm), trail + (ga,))
        done.add(ga)
        ordered.append(keys[ga])

    for ga in keys:
        visit(ga)
    return ordered


def sources_jar(repo, resolver, g, m, v):
    """A module's published sources jar, or None."""
    for variant in (resolver._module_json(g, m, v) or {}).get("variants", []):
        if variant.get("attributes", {}).get("org.gradle.docstype") == "sources":
            for f in variant.get("files", []):
                return repo.fetch(g, m, v, f["url"], f.get("sha256"), f.get("sha1"))
    return None


def kotlin_sources(jar):
    import zipfile
    with zipfile.ZipFile(jar) as z:
        return [n for n in z.namelist() if n.endswith(".kt") and not n.endswith("/EmptyFile.kt")]


def sources_for(repo, resolver, key, wrapper):
    """What to build a Google-only module from, and whose sources those are."""
    jar = sources_jar(repo, resolver, *wrapper)
    if jar and kotlin_sources(jar):
        return jar, wrapper
    older = OLDER_SOURCES.get(key.rsplit(":", 1)[0])
    jar = older and sources_jar(repo, resolver, *older)
    return (jar, older) if jar and kotlin_sources(jar) else (None, wrapper)


def set_choices(sets):
    """The source sets the JVM compiles, most general first, to try in turn.
    jvmMain is usually there. Where it does not compile (it needs another
    Google-only library) or lacks actuals, nonJvmMain, the native and web half,
    brings them, alone or beside jvmMain."""
    usable = [s for s in sets if s == "commonMain" or s.startswith("non") or "jvm" in s.lower() or not NOT_JVM.search(s)]
    platform = ("jvmMain", "nonJvmMain", "nonJvmRest", "jvmActuals")
    base = sorted((s for s in usable if s not in platform), key=lambda s: (s != "commonMain", s))
    actuals = ["jvmActuals"] if "jvmActuals" in usable else []
    choices = [base + ["jvmMain"]] if "jvmMain" in usable else []
    if "nonJvmMain" in usable:
        choices.append(base + ["nonJvmMain"] + actuals)
    if "nonJvmRest" in sets:
        choices.append(base + ["jvmMain", "nonJvmRest"] + actuals)
    return choices or [base + actuals]


def strip_expect_jvm_annotations(src):
    """@JvmField and @JvmSynthetic inside expect classes, which the JVM rejects there
    without the jvmMain actual they were written for (androidx.collection's)."""
    for r, _, fs in os.walk(src):
        for f in fs:
            path = os.path.join(r, f)
            with open(path, encoding="utf-8") as fin:
                lines = fin.read().split("\n")
            depth, inside, changed = 0, False, False
            for i, line in enumerate(lines):
                code = not line.lstrip().startswith(("*", "/"))
                if not inside and code and re.search(r"\bexpect\b.*\b(class|object|interface)\b", line):
                    inside, depth = True, 0
                if inside:
                    stripped = re.sub(r"@(JvmField|JvmSynthetic)\b\s*(//.*)?", "", line)
                    changed |= stripped != line
                    lines[i] = stripped
                    depth += line.count("{") - line.count("}")
                    if depth <= 0 and "}" in line:
                        inside = False
            if changed:
                with open(path, "w", encoding="utf-8") as fout:
                    fout.write("\n".join(lines))


def split_non_jvm(src):
    """nonJvmRest: the nonJvmMain files with no jvmMain counterpart. androidx names
    an actual's file Name.platform.kt, so Name tells which actuals both halves have."""
    jvm, non_jvm = os.path.join(src, "jvmMain"), os.path.join(src, "nonJvmMain")
    if not (os.path.isdir(jvm) and os.path.isdir(non_jvm)):
        return
    stem = lambda path: os.path.relpath(path, os.path.dirname(path)).split(".")[0]
    have = {(os.path.relpath(r, jvm), stem(os.path.join(r, f))) for r, _, fs in os.walk(jvm) for f in fs}
    for r, _, fs in os.walk(non_jvm):
        for f in fs:
            if (os.path.relpath(r, non_jvm), stem(os.path.join(r, f))) not in have:
                dest = os.path.join(src, "nonJvmRest", os.path.relpath(r, non_jvm), f)
                os.makedirs(os.path.dirname(dest), exist_ok=True)
                shutil.copyfile(os.path.join(r, f), dest)


def build_from_sources(kotlinc, key, sources, classpath, work):
    """Compiles one module's JVM source sets: the jar, the sets, and errors if none."""
    import zipfile
    g, m, v = key.split(":")
    src = os.path.join(work, m, "src")
    with zipfile.ZipFile(sources) as z:
        for info in z.infolist():
            name = os.path.normpath(info.filename)
            if info.is_dir() or name.startswith(("..", "/")) or not name.endswith(".kt"):
                continue
            dest = os.path.join(src, name)
            os.makedirs(os.path.dirname(dest), exist_ok=True)
            with z.open(info) as fin, open(dest, "wb") as fout:
                shutil.copyfileobj(fin, fout)
    if m in JVM_ACTUALS:
        os.makedirs(os.path.join(src, "jvmActuals"), exist_ok=True)
        with open(os.path.join(src, "jvmActuals", "Actuals.kt"), "w") as f:
            f.write(JVM_ACTUALS[m])
    split_non_jvm(src)
    strip_expect_jvm_annotations(src)
    first_errors, tried = [], []
    for sets in set_choices(sorted(os.listdir(src))):
        tried.append(sets)
        classes = os.path.join(work, m, "classes-" + sets[-1])
        errors = compile_sets(kotlinc, m, src, sets, classpath, classes)
        if not errors:
            jar = os.path.join(work, f"{m}-{v}-from-sources.jar")
            with zipfile.ZipFile(jar, "w", zipfile.ZIP_DEFLATED) as z:
                for r, _, fs in os.walk(classes):
                    for f in fs:
                        z.write(os.path.join(r, f), os.path.relpath(os.path.join(r, f), classes))
            return jar, sets, []
        first_errors = first_errors or errors
    return None, tried[0] if tried else [], first_errors


def compile_sets(kotlinc, module, src, sets, classpath, classes):
    """One multiplatform compilation, each set refining the one before; its errors."""
    files = {s: sorted(os.path.join(r, f) for r, _, fs in os.walk(os.path.join(src, s)) for f in fs) for s in sets}
    # The opt-ins androidx compiles with.
    args = ["-Xmulti-platform", "-Xexpect-actual-classes", "-Xfragments=" + ",".join(sets),
            "-opt-in=kotlin.contracts.ExperimentalContracts", "-opt-in=kotlin.ExperimentalStdlibApi",
            "-jvm-target", "17", "-nowarn", "-module-name", module, "-cp", ":".join(classpath), "-d", classes]
    args += [f"-Xfragment-refines={b}:{a}" for a, b in zip(sets, sets[1:])]
    args += [f"-Xfragment-sources={s}:{f}" for s in sets for f in files[s]]
    args += [f for s in sets for f in files[s]]
    argfile = classes + ".args"
    with open(argfile, "w") as f:
        f.write("\n".join('"' + a.replace("\\", "\\\\").replace('"', '\\"') + '"' for a in args))
    run = subprocess.run([os.path.join(kotlinc, "bin", "kotlinc"), "@" + argfile], capture_output=True, text=True)
    errors = [l for l in (run.stdout + run.stderr).splitlines() if ": error:" in l or l.startswith("error:")]
    produced = any(f.endswith(".class") for _, _, fs in os.walk(classes) for f in fs)
    if run.returncode == 0 and not produced:
        errors = ["no classes came out"]
    return errors if run.returncode != 0 or not produced else []


# ---------------------------------------------------------------- inputs
def scan(paths):
    """Coordinates and versions named in Gradle build files and version catalogs."""
    coords, versions = [], {}
    for path in paths:
        with open(path, encoding="utf-8") as f:
            text = f.read()
        if path.endswith(".toml"):
            import tomllib
            data = tomllib.loads(text)
            refs = {k: str(v) for k, v in data.get("versions", {}).items() if isinstance(v, (str, int, float))}
            for lib in data.get("libraries", {}).values():
                if isinstance(lib, str):
                    coords.append(lib)
                    continue
                module = lib.get("module") or f"{lib.get('group')}:{lib.get('name')}"
                version = lib.get("version")
                if isinstance(version, dict):
                    version = refs.get(version.get("ref")) or version.get("strictly") or version.get("require")
                elif version is None and lib.get("version.ref"):
                    version = refs.get(lib["version.ref"])
                if version:
                    coords.append(f"{module}:{version}")
            for plugin in data.get("plugins", {}).values():
                if isinstance(plugin, dict) and plugin.get("id") == "org.jetbrains.compose":
                    v = plugin.get("version")
                    v = refs.get(v.get("ref")) if isinstance(v, dict) else v
                    if v:
                        versions["compose"] = v
        else:
            coords += re.findall(r'"([\w.\-]+:[\w.\-]+:[\w.\-+\[\](),]+)"', text)
            m = re.search(r'id\("org\.jetbrains\.compose"\)\s*version\s*"([^"]+)"', text) \
                or re.search(r'org\.jetbrains\.compose:compose-gradle-plugin:([^"]+)"', text)
            if m:
                versions["compose"] = m.group(1)
    kept = []
    for c in coords:
        g, a, v = c.split(":")[:3]
        if f"{g}:{a}" == "org.jetbrains.compose:compose-gradle-plugin":
            versions.setdefault("compose", v)
        # Gradle plugins, and what only Android uses (Google Maven, or Android classes).
        if a.endswith(("gradle-plugin", "-android")) or g.startswith(("androidx.", "com.android", "com.google.android")):
            continue
        kept.append(c)
        family = {"org.jetbrains.androidx.lifecycle": "lifecycle", "org.jetbrains.androidx.navigation": "navigation",
                  "io.coil-kt.coil3": "coil"}.get(g)
        if a.startswith("material-icons-"):
            family = "icons"
        if family:
            versions.setdefault(family, v)
    return kept, versions


def roots_for(args):
    compose = args.compose
    lifecycle, navigation, coil = COMPANIONS.get(compose, (None, None, None))
    lifecycle, navigation, coil = args.lifecycle or lifecycle, args.navigation or navigation, args.coil or coil
    if not (lifecycle and navigation and coil):
        sys.exit(f"no lifecycle/navigation/Coil versions known for Compose {compose}: pass --lifecycle, --navigation and --coil")
    icons = args.icons or ("1.7.3" if vcmp(compose, "1.8.0") >= 0 else compose)
    roots = [
        f"org.jetbrains.compose.runtime:runtime:{compose}",
        f"org.jetbrains.compose.foundation:foundation:{compose}",
        f"org.jetbrains.compose.ui:ui:{compose}",
        f"org.jetbrains.compose.material3:material3:{compose}",
        f"org.jetbrains.compose.components:components-resources:{compose}",
        f"org.jetbrains.compose.material:material-icons-core:{icons}",
        f"org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:{lifecycle}",
        f"org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:{lifecycle}",
        f"org.jetbrains.androidx.navigation:navigation-compose:{navigation}",
        f"io.coil-kt.coil3:coil-compose:{coil}",
        f"io.coil-kt.coil3:coil-network-okhttp:{coil}",
        # Dispatchers.Main on the desktop.
        f"org.jetbrains.kotlinx:kotlinx-coroutines-swing:{COROUTINES}",
    ]
    if args.icons_extended:
        roots.append(f"org.jetbrains.compose.material:material-icons-extended:{icons}")
    if args.skiko_runtime:
        roots.append(f"org.jetbrains.compose.desktop:desktop-jvm-{args.skiko_runtime}:{compose}")
    if not args.no_core:
        roots += CORE_DEPS
    return roots + args.coordinates, dict(compose=compose, lifecycle=lifecycle, navigation=navigation, coil=coil, icons=icons)


def main():
    p = argparse.ArgumentParser(prog="compose-libs.sh")
    p.add_argument("--compose")
    p.add_argument("--lifecycle")
    p.add_argument("--navigation")
    p.add_argument("--coil")
    p.add_argument("--icons")
    p.add_argument("--icons-extended", action="store_true")
    p.add_argument("--skiko-runtime", metavar="OS")
    p.add_argument("--scan", action="append", default=[])
    p.add_argument("--no-core", action="store_true")
    p.add_argument("--label")
    p.add_argument("--delay", type=float, default=float(os.environ.get("COMPOSE_LIBS_DELAY", "1")))
    p.add_argument("--tries", type=int, default=9)
    p.add_argument("--refresh", action="store_true", help="ask again for files found missing before")
    p.add_argument("--dry-run", action="store_true")
    p.add_argument("--repo", default=os.environ.get("MAVEN_REPO", "https://repo1.maven.org/maven2"))
    p.add_argument("coordinates", nargs="*")
    args = p.parse_args()

    scanned, scanned_versions = scan(args.scan)
    for family, version in scanned_versions.items():
        if getattr(args, family) is None:
            setattr(args, family, version)
    args.compose = args.compose or "1.8.2"
    args.coordinates += scanned
    roots, versions = roots_for(args)
    roots = [tuple(c.split(":")[:3]) for c in dict.fromkeys(roots)]

    home = os.path.abspath(os.path.expanduser(os.environ.get("COMPOSE_LIBS_HOME", "~/.cache/xta-compose-libs")))
    store = os.path.join(home, ".store")
    out = os.path.join(home, args.label or args.compose)
    repo = Repo(args.repo.rstrip("/"), store, args.delay, args.tries, args.refresh)
    log("compose-libs: " + ", ".join(f"{k} {v}" for k, v in versions.items()) + f" -> {out}")

    resolver = Resolver(repo)
    selected, order, missing, unversioned = resolver.resolve(roots)
    jars, lines, owners = [], [], {}
    for ga in order:
        v, node = selected[ga]
        for url, name, sha256, sha1 in node.files:
            if args.dry_run:
                lines.append(f"{ga[0]}:{ga[1]}:{v}  {url}")
                continue
            path = repo.fetch(ga[0], ga[1], v, url, sha256, sha1, cached_as=name)
            if path is None:
                missing[f"{ga[0]}:{ga[1]}:{v}"] = f"lists {url}, which is not on the repository"
                continue
            jars.append(path)
            owners[os.path.basename(path)] = ga
            lines.append(f"{ga[0]}:{ga[1]}:{v}  {os.path.basename(path)}")

    if not args.dry_run:
        if os.path.isdir(out):
            shutil.rmtree(out)
        os.makedirs(out)
        placed = []
        for path in jars:
            dest = os.path.join(out, os.path.basename(path))
            try:
                os.link(path, dest)
            except OSError:
                shutil.copyfile(path, dest)
            placed.append(dest)
        placed += stand_ins(repo, resolver, selected, missing, placed, lines, out, owners)
        with open(os.path.join(out, "classpath.txt"), "w") as f:
            f.write("\n".join(placed) + "\n")
        visible = resolver.compile_closure([r for r in roots if f"{r[0]}:{r[1]}" not in IMPLEMENTATION_ONLY], selected)
        with open(os.path.join(out, "compile-classpath.txt"), "w") as f:
            f.write("".join(p + "\n" for p in placed if owners.get(os.path.basename(p)) in visible))
        with open(os.path.join(out, "resolved.txt"), "w") as f:
            f.write("# " + " ".join(f"{k}={v}" for k, v in versions.items()) + "\n" + "\n".join(sorted(lines)) + "\n")
    else:
        print("\n".join(sorted(lines)))
    for ga in unversioned:
        log(f"  warning: no version asked for {ga[0]}:{ga[1]}; left out")
    for coordinates, why in sorted(missing.items()):
        log(f"  missing: {coordinates} {why}")
    log(f"compose-libs: {len(lines)} jars from {len(order)} modules, {repo.requests} requests"
        f" ({repo.throttled} answered 429 or failed)"
        + ("" if args.dry_run else f"; classpath in {out}/classpath.txt"))
    sys.exit(3 if missing else 0)


def stand_ins(repo, resolver, selected, missing, classpath, lines, out, owners):
    """Builds what only Google Maven has from JetBrains' sources of the same code.
    Takes what it builds off `missing`; returns the jars, placed in `out`, and
    notes in `owners` the module each stands in for."""
    google = {k for k in missing if k.startswith(GOOGLE_ONLY)}
    found = wrappers(selected, google)
    for key in sorted(google - set(found)):
        missing.pop(key)
        log(f"  left out: {key} (Google Maven only, and nothing on the desktop compiles against it)")
    if not found:
        return []
    kotlinc = os.environ.get("KOTLINC")
    if not kotlinc:
        log("  set KOTLINC to a kotlinc home to build " + ", ".join(sorted(found)) + " from JetBrains' sources")
        return []
    with open(os.path.join(kotlinc, "build.txt")) as f:
        compiler = f.read().strip()
    built = []
    work = tempfile.mkdtemp(dir=repo.tmp)
    try:
        for key in build_order(found, selected):
            sources, (g, m, v) = sources_for(repo, resolver, key, found[key])
            if not sources:
                missing[key] += f"; {g}:{m}:{v} publishes no sources to build it from"
                continue
            with open(sources, "rb") as f:
                digest = hashlib.sha256(f.read() + compiler.encode()
                                        + " ".join(sorted(map(os.path.basename, classpath + built))).encode()).hexdigest()[:16]
            cached = os.path.join(repo.store, ".built", f"{key.split(':')[1]}-{key.split(':')[2]}-{digest}.jar")
            if not os.path.isfile(cached):
                log(f"  building {key} from the sources of {g}:{m}:{v}")
                jar, sets, errors = build_from_sources(kotlinc, key, sources, classpath + built, work)
                if not jar:
                    log(f"  could not build {key} ({', '.join(sets)}):\n    " + "\n    ".join(errors[:20]))
                    continue
                Repo._place(jar, cached, copy=False)
            dest = os.path.join(out, f"{key.split(':')[1]}-{key.split(':')[2]}-from-sources.jar")
            shutil.copyfile(cached, dest)
            built.append(dest)
            owners[os.path.basename(dest)] = tuple(key.split(":")[:2])
            missing.pop(key)
            lines.append(f"{key}  {os.path.basename(dest)} (built from the sources of {g}:{m}:{v})")
    finally:
        shutil.rmtree(work, ignore_errors=True)
    return built


main()
PY
