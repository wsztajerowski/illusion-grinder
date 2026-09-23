# slides/tools/slidelist.py
"""Print the current slide numbering of a Slidev deck.

Two numbers matter and they are not the same. `pos` is the slide's position
in the file. `range#` is what `slidev export --range` takes, which counts
only slides that actually render -- a `hide: true` slide is skipped, so every
slide after one is off by one.

Usage: python3 slides/tools/slidelist.py slides/slides.md
"""
import re
import sys

path = sys.argv[1] if len(sys.argv) > 1 else "slides.md"
lines = open(path, encoding="utf-8").read().split("\n")

seps = [i for i, line in enumerate(lines) if line == "---"]
segments, last = [], 0
for s in seps:
    segments.append((last, s))
    last = s + 1
segments.append((last, len(lines)))

parsed = []
for a, b in segments:
    body = lines[a:b]
    is_yaml = (
        bool(body)
        and all(re.match(r"^[a-zA-Z_][\w.-]*\s*:|^\s+|^$|^#", l) for l in body)
        and any(":" in l for l in body)
    )
    parsed.append((a, b, is_yaml, body))

out, pos, vis, j = [], 0, 0, 1  # segment 0 is the deck frontmatter
while j < len(parsed):
    a, b, is_yaml, body = parsed[j]
    if is_yaml and j + 1 < len(parsed):
        hidden = any(l.strip() == "hide: true" for l in body)
        body = parsed[j + 1][3]
        j += 2
    else:
        hidden = False
        j += 1
    pos += 1
    if not hidden:
        vis += 1
    title = next((l for l in body if l.startswith("# ")), "(no heading)")
    out.append((pos, "--" if hidden else str(vis), a + 1, title.strip(), hidden))

print(f"{'pos':>4} {'range#':>7}  {'line':<7} slide")
for p, v, line_no, title, hidden in out:
    print(f"{p:>4} {v:>7}  L{line_no:<6} {title[:64]}")
print(
    f"\nTOTAL {len(out)} slides; {sum(1 for o in out if o[4])} hidden; "
    f"{sum(1 for o in out if not o[4])} exported by `npm run shots`"
)
