# Rewrites the theme credits table in NOTICE.md from the theme files themselves
# (name, author and website attributes of each themes/*.xml).
# Run:  python -I tools/gen_theme_credits.py   (from the repository root)
import os
import xml.etree.ElementTree as ET

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
THEMES = os.path.join(ROOT, "io.github.jollygood1.syntaxthemes", "themes")
NOTICE = os.path.join(ROOT, "NOTICE.md")
BEGIN, END = "<!-- theme credits: begin -->", "<!-- theme credits: end -->"

rows = []
for name in os.listdir(THEMES):
    if not name.endswith(".xml"):
        continue
    root = ET.parse(os.path.join(THEMES, name)).getroot()
    rows.append((root.get("name") or name, root.get("author") or "", root.get("website") or "", name))
rows.sort(key=lambda r: r[0].lower())

lines = [BEGIN, "", "| Theme | Author | Website | File |", "|---|---|---|---|"]
for theme, author, website, name in rows:
    lines.append("| %s | %s | %s | `%s` |" % (theme, author.replace("|", "/"), website, name))
lines += ["", END]
block = "\n".join(lines)

text = open(NOTICE, encoding="utf-8").read()
if BEGIN in text:
    text = text[:text.index(BEGIN)] + block + text[text.index(END) + len(END):]
else:
    text = text.rstrip() + "\n\n## Theme authors\n\nThe themes are the work of the people below. This table is generated from the theme files by `tools/gen_theme_credits.py`.\n\n" + block + "\n"
open(NOTICE, "w", encoding="utf-8", newline="\n").write(text)
print("credited %d themes" % len(rows))
