#!/usr/bin/env python3
"""Keeps the README current so nobody has to edit it by hand for every update.

Run by CI on every push to main (and safe to run locally: `python3 tools/update_readme.py`).
It rewrites only the blocks between <!-- AUTO:name --> and <!-- /AUTO:name --> markers:
  facts  - version, number of sprite clips, game skill packs, music apps, voices
  files  - every source file the hand-written project map does not mention yet, with the first comment line of the file
It also writes dist/release-notes.md (the newest CHANGELOG section) when given --notes, used as the GitHub Release text.
"""
import json, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "app/src/main/java/app/capri/mobile")
ASSETS = os.path.join(ROOT, "app/src/main/assets")
README = os.path.join(ROOT, "README.md")


def block(text, name, body):
    pat = re.compile(r"(<!-- AUTO:%s -->\n)(.*?)(<!-- /AUTO:%s -->)" % (name, name), re.S)
    if not pat.search(text):
        print("marker %s not found, skipped" % name)
        return text
    return pat.sub(lambda m: m.group(1) + body.rstrip("\n") + "\n" + m.group(3), text)


def first_comment(path):
    try:
        for line in open(path, encoding="utf-8"):
            s = line.strip()
            if s.startswith("//"):
                t = s.lstrip("/ ").strip()
                return t if len(t) <= 110 else t[:107].rstrip() + "..."
            if s.startswith(("class ", "object ", "data class ", "fun ", "private ", "internal ", "abstract ", "open ", "sealed ", "enum ")):
                break
    except Exception:
        pass
    return ""


def skills():
    out = []
    d = os.path.join(ASSETS, "gameskills")
    if os.path.isdir(d):
        for f in sorted(os.listdir(d)):
            t = open(os.path.join(d, f), encoding="utf-8").read()
            name = re.search(r"^name:\s*(.+)$", t, re.M)
            games = re.search(r"^games:\s*(.+)$", t, re.M)
            if name:
                out.append("%s (%s)" % (name.group(1).strip(), games.group(1).strip() if games else ""))
    return out


def music_apps():
    p = os.path.join(SRC, "MusicApps.kt")
    if not os.path.exists(p):
        return []
    return re.findall(r'MusicApp\("[a-z]+", "([^"]+)"', open(p, encoding="utf-8").read())


def version():
    run = os.environ.get("GITHUB_RUN_NUMBER")
    if run:
        return "0.5.%s" % run
    m = re.search(r'versionName\s*=\s*"([^"]+)"', open(os.path.join(ROOT, "app/build.gradle.kts"), encoding="utf-8").read())
    return m.group(1) if m else "?"


def facts():
    clips = len(json.load(open(os.path.join(ASSETS, "sprites.json"))))
    tests = 0
    td = os.path.join(ROOT, "app/src/test")
    for dp, _, fs in os.walk(td):
        for f in fs:
            if f.endswith(".kt"):
                tests += len(re.findall(r"@Test", open(os.path.join(dp, f), encoding="utf-8").read()))
    lines = ["- Latest build: **%s** (see [Releases](../../releases/latest) and the [Changelog](CHANGELOG.md))" % version(),
             "- Animation clips: **%d**; unit tests: **%d**" % (clips, tests)]
    s = skills()
    if s:
        lines.append("- Game skill packs: " + ", ".join(s))
    m = music_apps()
    if m:
        lines.append("- Music apps she can play in: " + ", ".join(m))
    return "\n".join(lines)


def files(readme_without_auto):
    rows = []
    for f in sorted(os.listdir(SRC)):
        if f.endswith(".kt") and f not in readme_without_auto and f[:-3] + "*" not in readme_without_auto:
            stem = f[:-3]
            # a hand-written row like `Chess*.kt` or `Kokoro*.kt` already covers it
            if any(re.search(r"`[^`]*\b%s\*" % re.escape(stem[:k]), readme_without_auto) for k in range(3, len(stem) + 1)):
                continue
            rows.append("| `%s` | %s |" % (f, first_comment(os.path.join(SRC, f)) or "-"))
    if not rows:
        return "_Every source file is described in the table above._"
    return "Other source files (generated):\n\n| File | What it starts with |\n|---|---|\n" + "\n".join(rows)


def main():
    text = open(README, encoding="utf-8").read()
    plain = re.sub(r"<!-- AUTO:.*?<!-- /AUTO:\w+ -->", "", text, flags=re.S)
    text = block(text, "facts", facts())
    text = block(text, "files", files(plain))
    open(README, "w", encoding="utf-8").write(text)
    if "--notes" in sys.argv:
        ch = open(os.path.join(ROOT, "CHANGELOG.md"), encoding="utf-8").read()
        body = ch[ch.index("\n## ") + 1:]
        m = re.match(r"## [^\n]*\n.*?(?=\n## |\Z)", body, re.S)
        os.makedirs(os.path.join(ROOT, "dist"), exist_ok=True)
        open(os.path.join(ROOT, "dist/release-notes.md"), "w", encoding="utf-8").write(m.group(0).strip() + "\n" if m else "")


if __name__ == "__main__":
    main()
