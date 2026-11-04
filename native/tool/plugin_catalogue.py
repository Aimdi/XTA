#!/usr/bin/env python3
"""Writes plugins.json, the plugin catalogue (native/PLUGINS.md), from what the
plugins build left: each plugin's entry (<id>.json, from :plugins:<id>:pluginEntry)
and its files (the APK from pluginApk, the JAR from pluginJar).

    python3 native/tool/plugin_catalogue.py --base-url <where the files will be> \\
        [--android <dir>] [--desktop <dir>] --out <plugins.json> [--copy-to <dir>]

--android and --desktop are the folders plugins/build/out/android and
plugins/build/out/desktop (or CI artifacts of them); a plugin's file for a
platform is listed when that folder has it. Each file is listed with its URL
(--base-url and its name), its size and its SHA-256, as the app checks it before
anything in it is loaded. --copy-to puts the files beside plugins.json, for a
release or a local server. Plugins come in the store's order of categories,
then by id.
"""
import argparse
import hashlib
import json
import os
import shutil
import sys

CATEGORIES = ["social", "communities", "newsletters", "art", "markets", "bookmarks", "media", "sites"]


def sha256(path):
    digest = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 16), b""):
            digest.update(block)
    return digest.hexdigest()


def entries(folders):
    """Each plugin's entry, from whichever folder has it, by id."""
    found = {}
    for folder in folders:
        for name in sorted(os.listdir(folder)):
            if name.endswith(".json") and name != "plugins.json":
                with open(os.path.join(folder, name), encoding="utf-8") as f:
                    entry = json.load(f)
                found.setdefault(entry["id"], entry)
    return found


def artifact(folder, name, base_url):
    path = os.path.join(folder, name) if folder else None
    if not path or not os.path.isfile(path):
        return None
    return {"url": base_url.rstrip("/") + "/" + name, "sha256": sha256(path), "size": os.path.getsize(path)}, path


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--android")
    parser.add_argument("--desktop")
    parser.add_argument("--out", required=True)
    parser.add_argument("--copy-to")
    args = parser.parse_args()
    folders = [f for f in (args.android, args.desktop) if f]
    if not folders:
        sys.exit("name --android or --desktop, or both")

    plugins, files = [], []
    for plugin_id, entry in entries(folders).items():
        listed = {key: entry[key] for key in ("id", "version", "apiVersion", "available", "private", "category", "name", "description")}
        for platform, folder in (("android", args.android), ("desktop", args.desktop)):
            found = artifact(folder, entry[platform], args.base_url)
            if found:
                listed[platform], path = found
                files.append(path)
        if "android" in listed or "desktop" in listed:
            plugins.append(listed)
        else:
            print(f"{plugin_id}: no file for any platform, left out", file=sys.stderr)

    order = {category: index for index, category in enumerate(CATEGORIES)}
    plugins.sort(key=lambda p: (order.get(p["category"], len(order)), p["id"]))
    # The document's own version is the newest its plugins were built for: what an
    # entry without one is taken as, and what CI compares the next build's against.
    catalogue = {"apiVersion": max((p["apiVersion"] for p in plugins), default=1), "plugins": plugins}

    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(catalogue, f, ensure_ascii=False, indent=2)
        f.write("\n")
    if args.copy_to:
        os.makedirs(args.copy_to, exist_ok=True)
        for path in files:
            shutil.copy2(path, os.path.join(args.copy_to, os.path.basename(path)))
    for plugin in plugins:
        sizes = ", ".join(f"{p} {plugin[p]['size']} bytes" for p in ("android", "desktop") if p in plugin)
        print(f"{plugin['id']} {plugin['version']}: {sizes}")


if __name__ == "__main__":
    main()
