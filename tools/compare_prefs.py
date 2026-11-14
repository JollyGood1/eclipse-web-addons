# Compares the preferences the addon wrote into a workspace with the theme kit's prefs\*.prefs.
# Run:  python -I tools/compare_prefs.py <kit prefs folder> <workspace folder>
# org.eclipse.ui.editors: the kit's install script overrides the background and adds four
# SystemDefault keys after merging the file, so the same is expected here.
import os, re, sys

KIT, WS = sys.argv[1], sys.argv[2]
SETTINGS = os.path.join(WS, ".metadata", ".plugins", "org.eclipse.core.runtime", ".settings")
EXTRA = {"org.eclipse.ui.editors.prefs": {
    "AbstractTextEditor.Color.Background": "28,31,34",
    "AbstractTextEditor.Color.Background.SystemDefault": "false",
    "AbstractTextEditor.Color.Foreground.SystemDefault": "false",
    "AbstractTextEditor.Color.SelectionBackground.SystemDefault": "false",
    "AbstractTextEditor.Color.SelectionForeground.SystemDefault": "false",
}}


def load(path):
    out = {}
    with open(path, encoding="latin-1") as f:
        for line in f.read().splitlines():
            if not line or line[0] in "#!" or "=" not in line:
                continue
            k, v = line.split("=", 1)
            out[k] = re.sub(r"\\(.)", r"\1", v)
    out.pop("eclipse.preferences.version", None)
    return out


bad = 0
for name in sorted(os.listdir(KIT)):
    want = load(os.path.join(KIT, name))
    want.update(EXTRA.get(name, {}))
    path = os.path.join(SETTINGS, name)
    got = load(path) if os.path.exists(path) else {}
    diffs = [(k, want.get(k), got.get(k)) for k in sorted(set(want) | set(got)) if want.get(k) != got.get(k)]
    print("%-32s %3d keys expected, %3d written, %d differences" % (name, len(want), len(got), len(diffs)))
    for k, w, g in diffs:
        print("    %s: expected %r, got %r" % (k, w, g))
    bad += len(diffs)
print("TOTAL differences:", bad)
sys.exit(1 if bad else 0)
