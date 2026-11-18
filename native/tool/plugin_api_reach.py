#!/usr/bin/env python3
"""How far the plugin API reaches into the app, as a step towards :plugin-api.

The plugin API is `ui.plugins` plus the parts of `:ui` plugins use besides
(PluginSurfaceTest's list, read here from the plugins' own imports). For it
to stand in a module of its own, what it needs must not reach the app's
screens: Settings, Home, the plugin store, the app graph. This prints, from
the sources alone (no build):

- how much of `:ui` the plugin API drags in (its transitive closure);
- how many files of the API-like packages could move to a module today,
  their whole closure staying inside those packages and using none of
  `:ui`'s string resources;
- the direct links from those packages into the app, the ones to cut next.

A file depends on another when it imports one of its top-level names, or
names one in the same package (comments and strings left out). It is a
sketch of the compiler's view, good enough to steer by.

    python3 -I native/tool/plugin_api_reach.py [--edges N] [--graph] [--why File.kt]
"""
import collections
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
NATIVE = os.path.dirname(HERE)
UI = os.path.join(NATIVE, "ui", "src")
PLUGINS = os.path.join(NATIVE, "plugins")
BASE = "com.aimdi.xtanative."
API_LIKE = {BASE + "ui." + p for p in ["plugins", "common", "post", "media", "speech", "links", "theme"]}

DECL = re.compile(
    r"^(?:@[\w.()\" ,=:]+\s+)*(?:(?:public|internal|protected|expect|actual|data|sealed|abstract|open|enum|inline|value|"
    r"annotation|fun interface|operator|infix|suspend|const|lateinit|tailrec|override)\s+)*"
    r"(?:class|object|interface|fun|val|var|typealias)\s+(?:<[^>]*>\s*)?(?:[\w.<>?, *]+\.)?`?(\w+)`?",
    re.M,
)


def strip(text):
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    text = re.sub(r"//[^\n]*", "", text)
    return re.sub(r'"(?:\\.|[^"\\])*"', '""', text)


def kotlin_files(root, skip_tests):
    for path, _, names in os.walk(root):
        if skip_tests and "Test" in path:
            continue
        for name in names:
            if name.endswith(".kt"):
                yield os.path.join(path, name)


def main():
    edges_shown = int(sys.argv[sys.argv.index("--edges") + 1]) if "--edges" in sys.argv else 25
    files = [f for s in ("commonMain", "desktopMain", "androidMain") for f in kotlin_files(os.path.join(UI, s, "kotlin"), False)]
    info, declared = {}, collections.defaultdict(lambda: collections.defaultdict(set))
    for f in files:
        text = open(f, encoding="utf-8").read()
        pkg = re.search(r"^package ([\w.]+)", text, re.M).group(1)
        names = {m.group(1) for m in DECL.finditer(text) if m.start() == text.rfind("\n", 0, m.start()) + 1}
        code = strip(text)
        info[f] = (pkg, set(re.findall(r"^import ([\w.]+)", text, re.M)), set(re.findall(r"\b[A-Za-z_]\w*\b", code)),
                   len(text.splitlines()), "generated.resources" in text)
        for n in names:
            declared[pkg][n].add(f)

    def deps(f):
        pkg, imports, idents, _, _ = info[f]
        out = set()
        for n, where in declared[pkg].items():
            if n in idents:
                out |= where
        for imp in imports:
            p, _, n = imp.rpartition(".")
            out |= declared.get(p, {}).get(n, set())
        out.discard(f)
        return out

    def closure(start):
        seen, stack = set(start), list(start)
        while stack:
            for d in deps(stack.pop()):
                if d not in seen:
                    seen.add(d)
                    stack.append(d)
        return seen

    used = set()
    for f in kotlin_files(PLUGINS, True):
        used |= set(re.findall(r"^import (" + re.escape(BASE) + r"(?:ui\.[a-z]+|data(?:\.[a-z]+)?)\.[A-Za-z]+)$", open(f, encoding="utf-8").read(), re.M))
    roots = {f for f in files if info[f][0] == BASE + "ui.plugins"}
    for name in used:
        p, _, n = name.rpartition(".")
        roots |= declared.get(p, {}).get(n, set())

    reach = closure(roots)
    total = sum(info[f][3] for f in files)
    lines = sum(info[f][3] for f in reach)
    print(f"The plugin API ({len(roots)} files) reaches {len(reach)} of {len(files)} files in :ui, {lines} of {total} lines ({100 * lines // total}%).")

    # The API-like packages, and any other file declaring something plugins import (data.Problem).
    api = sorted({f for f in files if info[f][0] in API_LIKE} | {f for f in roots if info[f][0] != BASE + "ui.plugins"})
    api_set = set(api)
    movable = [f for f in api if all((x in api_set or info[x][0] in API_LIKE) and not info[x][4] for x in closure({f}))]
    api_lines = sum(info[f][3] for f in api)
    moved = sum(info[f][3] for f in movable)
    print(f"Movable to a module today: {len(movable)} of {len(api)} API-like files, {moved} of {api_lines} lines ({100 * moved // api_lines}%).")

    # The sharper test: nothing in a file's closure holds the app graph, sits in a package only the
    # app's own screens use, or reads :ui's strings, whatever package the file itself is in.
    graph_bound = {f for f in files if {"AppGraph", "LocalGraph"} & info[f][2]}
    screens_only = {BASE + "ui." + p for p in ["settings", "home", "pluginstore", "chrome", "intro", "signin"]}
    free = [f for f in api if not any(x in graph_bound or info[x][0] in screens_only or info[x][4] for x in closure({f}))]
    freed = sum(info[f][3] for f in free)
    print(f"Free of the app graph and the app's screens: {len(free)} of {len(api)} API-like files, {freed} of {api_lines} lines ({100 * freed // api_lines}%).")
    print(f"Files that still hold the app graph: {len(graph_bound)}; the API reaches {len(graph_bound & reach)} of them.")
    if "--graph" in sys.argv:
        for f in sorted(graph_bound & reach):
            print("  " + os.path.relpath(f, UI))
    if "--why" in sys.argv:
        # The shortest chain from a root to the named file: the link to cut.
        target = sys.argv[sys.argv.index("--why") + 1]
        parent, queue = {r: None for r in roots}, collections.deque(roots)
        while queue:
            f = queue.popleft()
            for d in deps(f):
                if d not in parent:
                    parent[d] = f
                    queue.append(d)
        end = next((f for f in parent if f.endswith("/" + target)), None)
        chain = []
        while end:
            chain.append(os.path.basename(end))
            end = parent[end]
        print("\n" + (" <- ".join(chain) if chain else target + " is not reached"))

    links, examples = collections.Counter(), collections.defaultdict(set)
    for f in api:
        pkg, imports, _, _, _ = info[f]
        for imp in imports:
            p, _, n = imp.rpartition(".")
            if p.startswith(BASE) and p not in API_LIKE and n in declared.get(p, {}) and not declared[p][n] <= api_set:
                key = (pkg.removeprefix(BASE), p.removeprefix(BASE))
                links[key] += 1
                examples[key].add(n)
    print("\nDirect links from the API-like packages into the app, to cut next:")
    for (a, b), count in links.most_common(edges_shown):
        print(f"  {count:3}  {a} -> {b}: {' '.join(sorted(examples[(a, b)])[:8])}")


if __name__ == "__main__":
    main()
