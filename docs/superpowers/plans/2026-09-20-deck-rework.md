# Deck Rework Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rework `slides/slides.md` so the deck carries per-circle result tallies and tool limitations, and less theory — from 42 visible slides to 38 on the main line plus a 4-slide appendix.

**Architecture:** All edits are to a single file, `slides/slides.md`. Four existing "why tool X cannot see this" slides are rebuilt into caught/walked-free tallies that reuse the deck's existing `.vs-table` and `.callout` styles, so `style.css` is not touched. Three slides move behind a new Appendix section after `Thank You`. One slide is deleted, one pair is merged, one is folded into its neighbour.

**Tech Stack:** Slidev 0.49 (Markdown + Vue + MDC), `@iconify-json/mdi` icons, `npm run shots` (Playwright PNG export) for visual verification.

**Spec:** `docs/superpowers/specs/2026-09-20-deck-rework-design.md`

## Global Constraints

- **Locate slides by their `# Heading` text, never by line number.** Every task shifts the line numbers of everything below it. Line numbers in this plan describe the file *as it was when the plan was written* and are for orientation only.
- **`npm run shots -- --range N` takes the `range#` column from the helper, not `pos`.** Hidden slides do not render, so after the first `hide: true` the two diverge. Until Task 11 removes them, `range#` is one lower than `pos` after slide 21, and two lower after slide 39.
- **Do not edit `slides/style.css`.** Every class used here already exists: `.vs-table`, `.callout` (+ `.blue` `.purple` `.orange` `.green` `.yellow` `.red` `.big-callout`), `.subtle-note`, `.slide-subtitle`, `.verdict`, `.verdict-label`, `.big-quote`, `.buffer-viz`, `.two-col-code`, `.section-eyebrow`, `.ico-*`, `.inline-ico`.
- **Icon names must exist in `@iconify-json/mdi`.** Every icon used in this plan already appears elsewhere in `slides.md` except `mdi-run`, `mdi-cancel` and `mdi-bookmark-multiple`; Task 1 verifies those three render.
- **Verdict lines are preserved verbatim** on all four rebuilt slides. Their wording is not part of this rework.
- **Figures are copied exactly as written in this plan.** `35,222 of 655M` / `0.005%` / `~1.5%` / `1.1%` / `12.8M ops/s` / `3.1M ops/s` come from the existing slides. `iteration 4 of 1000` and `iteration 1` come from the recorded Fray logs under `demos/results/fray/fray-report/`. Do not round, restate or recompute any of them.
- **Class naming in console output:** the recorded Fray logs say `FastTrackLamportBuffer`; the repo and the deck say `FastPathLamportBuffer`. Use `FastPath` — the recordings predate the rename (see spec section D).
- **Commit after every task.** The repo commits straight to `main`; do not create a branch.
- **Every commit message ends with:**
  ```
  Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
  ```

---

### Task 1: Slide-numbering helper and baseline render

Without stable slide numbers, `npm run shots -- --range N` targets the wrong slide after the first deletion. This task installs a helper that prints the current numbering, and captures a baseline render to compare against.

**Files:**
- Create: `slides/tools/slidelist.py`
- Test: `slides/.shots/` (git-ignored render output)

**Interfaces:**
- Produces: `python3 slides/tools/slidelist.py slides/slides.md` → one line per slide: `<number>  L<line>  [HIDDEN]  <heading>`, then a `TOTAL n slides; m hidden` summary. Every later task calls this before rendering.

- [ ] **Step 1: Create the helper**

````python
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
````

- [ ] **Step 2: Run it and confirm the baseline numbering**

Run: `python3 slides/tools/slidelist.py slides/slides.md`

Expected last line, exactly:

```
TOTAL 44 slides; 2 hidden; 42 exported by `npm run shots`
```

Spot-check these rows (`pos` / `range#` / heading):

```
  13      13  # Why JUnit Cannot See This
  19      19  # What Fray Finds — Spurious Wakeup
  21      --  # A Footgun in the Config          <- hidden
  23      22  # Why Fray Cannot See This
  32      31  # Why jcstress Cannot Promise Coverage
  36      35  # Lock-Free vs. Lock-Based
  39      --  # When to Reach for Which Tool     <- hidden
  44      42  # Thank You
```

If the totals differ, **stop** — the file has changed since this plan was written and the task list needs re-deriving.

- [ ] **Step 3: Capture the baseline render**

Run:
```bash
cd slides && npm run shots
```
Expected: 42 PNGs in `slides/.shots/` (hidden slides are not exported), no errors. Takes ~25 s.

- [ ] **Step 4: Verify the three new icons render**

Append a scratch slide to the end of `slides/slides.md`:

```markdown
---

# Icon check

<mdi-run class="ico-red inline-ico" /> <mdi-cancel class="ico-red inline-ico" /> <mdi-bookmark-multiple class="ico-blue inline-ico" />
```

The scratch slide is `pos` 45 but `range#` 43, because two slides are still hidden. Run: `cd slides && npm run shots -- --range 43` and open `slides/.shots/43.png`.
Expected: three distinct glyphs, no empty boxes.
Then **delete the scratch slide** and confirm the helper reports `TOTAL 44 slides; 2 hidden; 42 exported` again.

If any icon renders as an empty box, substitute one already used in the deck (`mdi-close-circle` for `mdi-run`, `mdi-close` for `mdi-cancel`, `mdi-book-open-page-variant` for `mdi-bookmark-multiple`) and apply that substitution everywhere this plan uses the icon — `mdi-run` appears in Tasks 3, 6 and 8, `mdi-cancel` in Task 10, `mdi-bookmark-multiple` in Task 11. Note it in the commit message of the first task that uses it.

- [ ] **Step 5: Commit**

```bash
git add slides/tools/slidelist.py
git commit -m "$(cat <<'EOF'
Add a helper that prints current Slidev slide numbering

slidev export --range takes slide numbers, which shift on every add or
delete. This prints the current mapping so a render can target the right
slide.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Merge the two algorithm slides

Slides 6 and 7 (`The Algorithm`, `The Algorithm — Pseudocode`) cover one idea across two frames. Merging keeps the buffer picture and the `poll()` pseudocode — `poll()` is where the NPE lands — and drops the `offer()` listing to a one-line note.

**Files:**
- Modify: `slides/slides.md` (slides 6–7, around L117–L197)

**Interfaces:**
- Consumes: `python3 slides/tools/slidelist.py` from Task 1.
- Produces: 43 slides in the file, 41 visible. Every later task's slide numbers shift down by one from here.

- [ ] **Step 1: Replace both slides with one**

Find the slide whose heading is `# The Algorithm` and delete everything from its `---`/`layout: two-cols` frontmatter through the end of the `# The Algorithm — Pseudocode` slide (the line before the `---` that precedes `# Four Implementations — One Interface`). Replace with:

````markdown
---
layout: two-cols
---

# The Algorithm

<mdi-account-tie class="ico-blue inline-ico" /> **Leslie Lamport's wait-free SPSC (Single Producer, Single Consumer) queue (1983)**

- Bounded circular array of capacity `N`
- Two cursors: `writePos` (producer) and `readPos` (consumer)
- **No locks. No CAS. No blocking.** <mdi-flash class="ico-yellow inline-ico" />
- O(1) offer and poll · low allocation pressure

<div class="buffer-viz">

```
  capacity = 4

   ┌─────┬─────┬─────┬─────┐
   │  A  │  B  │     │     │
   └─────┴─────┴─────┴─────┘
      ↑           ↑
   readPos    writePos
   (consumer)  (producer)
```

</div>

::right::

<v-click>

**Consumer side** <mdi-arrow-left-bold class="ico-orange inline-ico" />

```
function poll():
  r ← readPos
  if buffer[r] == null:
    return empty
  element ← buffer[r]
  buffer[r] ← null                 // free slot
  readPos ← (r + 1) mod capacity   // publish
  return element
```

</v-click>

<div class="subtle-note"><code>offer()</code> is the mirror image — check the slot, write, advance <code>writePos</code>.</div>

<v-click>
<div class="callout purple">
<mdi-magnify /> &nbsp;Spot the bug? Neither did I. Neither did my unit tests.
</div>
</v-click>
````

- [ ] **Step 2: Confirm the slide count dropped by one**

Run: `python3 slides/tools/slidelist.py slides/slides.md`
Expected: `TOTAL 43 slides; 2 hidden; 41 exported`, and `# The Algorithm — Pseudocode` no longer appears.

- [ ] **Step 3: Render and inspect**

Run: `cd slides && npm run shots -- --range 6 --with-clicks`
Open the PNGs for slide 6. Expected: left column shows the four bullets **and** the buffer diagram without the diagram being cut off; right column shows the `poll()` listing, the `offer()` note and the purple callout. Nothing crosses the bottom edge of the frame.

If the left column overflows, delete the fourth bullet (`O(1) offer and poll · low allocation pressure`) and re-render.

- [ ] **Step 4: Commit**

```bash
git add slides/slides.md
git commit -m "$(cat <<'EOF'
Merge the two algorithm slides into one

Two frames for one idea. Keeps the buffer diagram and the poll()
pseudocode, where the NPE actually lands; offer() becomes a one-line
note since it is the mirror image.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

> **Deviation applied during execution.** The planned merge kept the ASCII buffer diagram
> plus `poll()`. That could not be rendered: whenever the buffer-diagram fence shares a slide
> with a second fence, Slidev leaks its injected `<CodeBlockWrapper …>` / `</CodeBlockWrapper>`
> tags into the rendered code, and in one arrangement fails to compile outright with
> `Invalid end tag`. The trigger was isolated against scratch slides — two plain fences, with or
> without `<v-click>`, render fine, so it is specific to that diagram's content. The merge was
> therefore built on the *pseudocode* slide's structure, which is proven to render: the surviving
> slide keeps `offer()` and `poll()` and the "Spot the bug?" hook, gains a one-line Lamport
> intro, and drops the ASCII buffer picture.

### Task 3: Rebuild the Circle I close

`Why JUnit Cannot See This` becomes a tally. Its three theory bullets go; its blue guidance callout is absorbed into a single callout that also carries the limitation. The verdict line is kept verbatim.

**Files:**
- Modify: `slides/slides.md` (slide `# Why JUnit Cannot See This`, around L332 pre-Task-2)

- [ ] **Step 1: Replace the slide body**

Find the slide headed `# Why JUnit Cannot See This` and replace everything from that heading to the line before the next `---` with:

````markdown
# Circle I Closes — JUnit

<div class="vs-table">

| <mdi-magnify class="ico-blue inline-ico" /> Caught | <mdi-run class="ico-red inline-ico" /> Walked free |
|---|---|
| **Nothing.** It was never going to. | **All four.** 68 / 68 green. |
| A single-threaded test cannot construct a race. | `Volatile` · `NonVolatile` · `LockBased` · `FastPath` |

</div>

<v-click>
<div class="callout blue">
<mdi-eye-off class="ico-blue" />&nbsp;
<strong>Structurally cannot</strong>&nbsp; start a second thread — no race, no failure, by construction.<br>&nbsp;
Write contract tests first and keep them always. Just never mistake a green suite for a concurrency proof.
</div>
</v-click>

<div class="subtle-note">Score: 0 of 4 bugs found</div>

<v-click>
<div class="verdict">
<span class="verdict-label">Verdict:</span> <em>Necessary. Insufficient. On to the next circle.</em> <mdi-arrow-down-bold-circle class="ico-purple inline-ico" />
</div>
</v-click>
````

- [ ] **Step 2: Render and inspect**

Run the helper to find the `range#` of `# Circle I Closes — JUnit` (expected: 12 — it sits before the first hidden slide, so `pos` and `range#` agree), then `cd slides && npm run shots -- --range 12 --with-clicks`.
Expected: two-column table, callout, score line and verdict all inside the frame. The table's second column must not wrap the four implementation names onto more than two lines.

- [ ] **Step 3: Commit**

```bash
git add slides/slides.md
git commit -m "$(cat <<'EOF'
Rebuild the Circle I close as a result tally

Replaces three theory bullets with what JUnit caught and what walked
free, and folds the limitation into the guidance callout. Score: 0 of 4.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: Compress Fray's Core Idea, delete the POS/PCT slide, trim the scheduler note

Three edits in the Fray section's opening. They are one task because deleting the POS/PCT slide strands the "1000 iterations by default" fact, which has to land on the Core Idea slide in the same change or the deck loses it.

**Files:**
- Modify: `slides/slides.md` (slides `# Fray's Core Idea`, `# Every Run — A Different Schedule`, `# A Fray Test`)

- [ ] **Step 1: Compress `Fray's Core Idea` from four bullets to two**

Replace the `<v-clicks>` block on that slide (leave the `# Fray's Core Idea` heading and the `<blockquote class="big-quote">` exactly as they are) with:

````markdown
<v-clicks>

- <mdi-controller class="ico-purple inline-ico" /> &nbsp;Fray controls the **scheduler** — it decides which thread runs next, so every run explores a **different interleaving**
- <mdi-dice-multiple class="ico-purple inline-ico" /> &nbsp;`@ConcurrencyTest` samples **1000 schedules by default** — no reliance on the OS scheduler *"getting lucky"*

</v-clicks>
````

- [ ] **Step 2: Delete the POS/PCT slide entirely**

Delete the whole slide headed `# Every Run — A Different Schedule`, including its leading `---` separator. Nothing from it survives; the two scheduler papers remain linked on the `Resources` slide.

- [ ] **Step 3: Delete the scheduler note on `A Fray Test`**

On the slide headed `# A Fray Test`, delete this block in full:

````markdown
<v-clicks>
<div class="subtle-note">
Switch with <code>@ConcurrencyTest(scheduler = PCTScheduler.class)</code> — <code>POSScheduler</code> is the default.
</div>
</v-clicks>
````

- [ ] **Step 4: Confirm nothing still references the deleted slide**

Run:
```bash
grep -n "PCTScheduler\|POSScheduler\|Partial Order Sampling\|Probabilistic Concurrency Testing" slides/slides.md
```
Expected: no matches outside the `Resources` slide's paper links. If `Fray vs. jcstress` or any other slide names a scheduler, remove that reference too.

- [ ] **Step 5: Render and inspect**

Run the helper (expected `TOTAL 42 slides; 2 hidden; 40 exported`), then render the Fray core-idea and test slides with `--with-clicks`, using their `range#`.
Expected: the quote still dominates the Core Idea slide, two bullets beneath it, plenty of clear space. `A Fray Test` ends at its purple callout.

- [ ] **Step 6: Commit**

```bash
git add slides/slides.md
git commit -m "$(cat <<'EOF'
Cut the scheduler theory from the Fray section

Deletes the POS vs PCT slide and the PCTScheduler aside, and moves the
one fact worth keeping -- 1000 schedules per test by default -- onto the
Core Idea slide, which drops from four bullets to two.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: Fix the Deterministic Replay slide

The slide quotes `iter: 721`; the recording it came from logs `iter: 1`. It also quotes the spurious-wakeup test, whose slide moves to the appendix in Task 11 — so it switches to the FastPath NPE, which the audience saw two slides earlier and whose stack trace is the callback to the cold open.

**Files:**
- Modify: `slides/slides.md` (slide `# Deterministic Replay`)
- Reference: `demos/results/fray/fray-report/pl.wsztajerowski.demo.lamport.fray.edgecase.FastTrackLamportBufferFrayTest/twoConsumersOnSingleElementMustNotCrash/fray.log`

- [ ] **Step 1: Replace the slide body**

Keep the heading line `# Deterministic Replay <mdi-replay class="ico-purple inline-ico" />` exactly as it is. Replace everything after it, up to the line before the next `---`, with:

````markdown
**Classic torture:** the test fails on iteration 4 of 1000. *Re-run it and it passes.*

```
2026-05-27 00:10:01 [INFO]: Error found at iter: 4, step: 26, Elapsed time: 32ms
2026-05-27 00:10:01 [INFO]: Error: java.lang.NullPointerException
Thread: Thread[#8048,consumer-1,5,main]
java.lang.NullPointerException
    at java.base/java.util.Objects.requireNonNull(Objects.java:220)
    at java.base/java.util.Optional.of(Optional.java:113)
    at pl.wsztajerowski.demo.lamport.mpmc.FastPathLamportBuffer.poll(FastPathLamportBuffer.java:46)
    at pl.wsztajerowski.demo.lamport.fray.edgecase.FastPathLamportBufferFrayTest
        .twoConsumersOnSingleElementMustNotCrash(FastPathLamportBufferFrayTest.java:37)

2026-05-27 00:10:01 [INFO]: The recording is saved to
    demos/05-fray/target/fray/fray-report/.../recording
```

<div class="subtle-note">The NPE is <code>Optional.of(null)</code> — the same line 46 as the exception we opened with.</div>

<v-click>

**Fray records & replays the exact schedule:** <mdi-record-rec class="ico-red inline-ico" />

```java
@ConcurrencyTest(
    replay = "PATH_TO_FRAY_REPORT/recording"
)
```

The next run samples different schedules. Without the recording, the bug is gone.
*With it: attach a debugger, step through the exact thread switches, watch it bloom in slow motion.*

</v-click>
````

- [ ] **Step 2: Verify the figures against the recorded log**

Run:
```bash
head -2 "demos/results/fray/fray-report/pl.wsztajerowski.demo.lamport.fray.edgecase.FastTrackLamportBufferFrayTest/twoConsumersOnSingleElementMustNotCrash/fray.log"
```
Expected: `Error found at iter: 4, step: 26, Elapsed time: 32ms` and the `NullPointerException` line. The slide must match these values. The class name on the slide is deliberately `FastPath`, not the log's `FastTrack` — see the Global Constraints.

- [ ] **Step 3: Confirm the old number is gone**

Run: `grep -n "721" slides/slides.md`
Expected: no matches.

- [ ] **Step 4: Render and inspect**

Find the slide number with the helper and render it with `--with-clicks`.
Expected: the console block is not clipped on the right — the longest line is the `FastPathLamportBuffer.poll` frame. If it wraps, drop the two `java.base/…` frames from the listing.

- [ ] **Step 5: Commit**

```bash
git add slides/slides.md
git commit -m "$(cat <<'EOF'
Use the recorded Fray iteration number on the replay slide

The slide showed iter 721; the recording it came from logs iter 4 for
the FastPath NPE. Switches the example from the spurious-wakeup test,
whose slide moves to the appendix, to the FastPath NPE the audience has
just seen -- same line 46 as the cold open.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: Rebuild the Circle II close

**Files:**
- Modify: `slides/slides.md` (slide `# Why Fray Cannot See This`)

- [ ] **Step 1: Replace the slide body**

Replace everything from the `# Why Fray Cannot See This` heading to the line before the next `---` with:

````markdown
# Circle II Closes — Fray

<div class="vs-table">

| <mdi-magnify class="ico-purple inline-ico" /> Caught | <mdi-run class="ico-red inline-ico" /> Walked free |
|---|---|
| `FastPath` — NPE at **iteration 4 of 1000**, replayable under a debugger | `NonVolatile` — 2000 schedules, all green, **still broken** |
| `Conditional take()` — null from a spurious wakeup, **iteration 1** · *appendix* | `Volatile` with two producers — not a scheduling bug |

</div>

<v-click>
<div class="callout purple">

**Fray proves:** "No thread ordering breaks my logic." <mdi-check class="ico-green inline-ico" /><br>
**Fray cannot prove:** "The JVM / CPU will not reorder my memory accesses." <mdi-close class="ico-red inline-ico" /><br>
Every schedule it explores is sequentially consistent — memory visibility is **structurally invisible** to it.

</div>
</v-click>

<div class="subtle-note">Score so far: 2 of 4 bugs found</div>

<v-click>
<div class="verdict">
<span class="verdict-label">Verdict:</span> <em>Logic, yes. Hardware reality, no. Descend further.</em> <mdi-arrow-down-bold-circle class="ico-orange inline-ico" />
</div>
</v-click>
````

- [ ] **Step 2: Render and inspect**

Find the number with the helper, render with `--with-clicks`.
Expected: both table rows fit on two lines each; the purple callout's three lines, the score and the verdict all inside the frame. This is the second-tightest slide in the deck — if it clips, shorten the first Caught cell to `` `FastPath` — NPE at **iteration 4 of 1000**, replayable ``.

- [ ] **Step 3: Commit**

```bash
git add slides/slides.md
git commit -m "$(cat <<'EOF'
Rebuild the Circle II close as a result tally

Replaces three theory bullets with what Fray caught and what walked
free, keeping the proves/cannot-prove callout as the limitation. Credits
the spurious-wakeup find with a pointer to the appendix. Score: 2 of 4.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 7: Compress jcstress's Core Idea and drop the volatile transition line

**Files:**
- Modify: `slides/slides.md` (slides `# jcstress's Core Idea`, `# Why \`volatile\` Is Non-Negotiable`)

- [ ] **Step 1: Compress the Core Idea from five bullets to two**

Leave the heading and the `<blockquote class="big-quote">` untouched. Replace the `<v-clicks>` block with:

````markdown
<v-clicks>

- <mdi-flask class="ico-orange inline-ico" /> &nbsp;OpenJDK's laboratory for the Java Memory Model — **millions** of iterations on real hardware
- <mdi-dice-6 class="ico-orange inline-ico" /> &nbsp;Controls **nothing**: the OS, JVM and CPU pick the order and jcstress just *observes outcomes* — pinning actors to cores to stress cache coherence

</v-clicks>
````

- [ ] **Step 2: Delete the transition line**

On the slide headed ``# Why `volatile` Is Non-Negotiable``, delete this line in full:

````markdown
<div class="slide-subtitle">Before we look at the result — remember why missing <code>volatile</code> matters.</div>
````

Everything else on that slide — both `.mem-diagram` blocks and the four bullets — stays. The cache diagrams are the on-ramp for attendees who rarely write concurrent code.

- [ ] **Step 3: Render and inspect**

Render both slides with `--with-clicks`. Expected: the Core Idea slide is mostly whitespace under the quote; the volatile slide's left column now starts at the `mdi-memory` line with no orphaned gap.

- [ ] **Step 4: Commit**

```bash
git add slides/slides.md
git commit -m "$(cat <<'EOF'
Trim the jcstress section opening

Core Idea drops from five bullets to two, and the volatile slide loses
its transition line. The cache diagrams stay -- they are the on-ramp for
the part of the room that rarely writes concurrent code.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 8: Rebuild the Circle III close

**Files:**
- Modify: `slides/slides.md` (slide `# Why jcstress Cannot Promise Coverage`)

- [ ] **Step 1: Replace the slide body**

Replace everything from the `# Why jcstress Cannot Promise Coverage` heading to the line before the next `---` with:

````markdown
# Circle III Closes — jcstress

<div class="vs-table">

| <mdi-magnify class="ico-orange inline-ico" /> Caught | <mdi-run class="ico-red inline-ico" /> Walked free |
|---|---|
| `NonVolatile` — 35,222 of 655M runs, **0.005%**. The bug Fray structurally cannot see | **Nothing.** |
| `Volatile` with two producers — **~1.5%** lost updates | Every bug in the lineup is now visible. |
| `FastPath` — **1.1%**, and only with a two-consumer test | |

</div>

<v-click>
<div class="callout orange">

**jcstress proves:** "This outcome really happens on real hardware." <mdi-check class="ico-green inline-ico" /><br>
**jcstress cannot prove:** "That outcome never happens." <mdi-close class="ico-red inline-ico" /><br>
It cannot find an outcome you never declared with <code>@Outcome</code>. Shorten the run and the 0.005% row disappears — same code, green report.

</div>
</v-click>

<div class="subtle-note">Score: 4 of 4 bugs found</div>

<v-click>
<div class="verdict">
<span class="verdict-label">Verdict:</span> <em>Hardware reality, yes. Coverage, never. Two tools, not one.</em> <mdi-arrow-down-bold-circle class="ico-green inline-ico" />
</div>
</v-click>
````

- [ ] **Step 2: Render and inspect**

Render with `--with-clicks`. Expected: three table rows, the orange callout, score and verdict inside the frame. If it clips, merge the second and third Caught rows into one cell reading `` `Volatile` @ 2 producers — **~1.5%** lost · `FastPath` — **1.1%** ``.

- [ ] **Step 3: Commit**

```bash
git add slides/slides.md
git commit -m "$(cat <<'EOF'
Rebuild the Circle III close as a result tally

Two of the four bullets were theory and are cut; the other two were a
result and a limitation and are absorbed into the tally and the
proves/cannot-prove callout. Score: 4 of 4.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 9: Fold the JMH pitfalls slide into the benchmark slide

`Only Benchmark Correct Code` is JMH 101. Its two substantive points and its punchline become a caption under the benchmark listing, replacing the reproducibility aside that is being deleted.

**Files:**
- Modify: `slides/slides.md` (slides `# Only Benchmark Correct Code`, `# A JMH Benchmark`)

- [ ] **Step 1: Delete the pitfalls slide**

Delete the whole slide headed `# Only Benchmark Correct Code <mdi-scale-balance class="ico-green inline-ico" />`, including its leading `---` separator.

- [ ] **Step 2: Replace the aside on the benchmark slide**

On the slide headed `# A JMH Benchmark`, replace this block:

````markdown
<div class="subtle-note">
Run with <code>-f 2 -wi 5 -i 5</code> — the CLI overrides <code>@Fork(1)</code>, hence <code>Cnt 10</code> overleaf.
<code>Control.stopMeasurement</code> is what stops a spinning benchmark from hanging at the end of an iteration.
</div>
````

with:

````markdown
<div class="subtle-note">
<code>Blackhole</code> stops the JIT deleting "dead" work; warmup and forks stop you measuring a cold JVM.
Without them you are measuring nothing — and accurate numbers for broken code are worse than no numbers.
</div>
````

- [ ] **Step 3: Render and inspect**

Run the helper (expected `TOTAL 41 slides; 2 hidden; 39 exported`), then render the JMH benchmark slide by its `range#`.
Expected: the code listing and the two-line caption fit; the caption reads as a lesson, not as build instructions.

- [ ] **Step 4: Commit**

```bash
git add slides/slides.md
git commit -m "$(cat <<'EOF'
Fold the JMH pitfalls slide into a caption

Blackhole and warmup are the two points worth keeping from a slide of
JMH 101; they become a caption under the benchmark listing, replacing
the CLI-flag aside. The punchline survives as golden rule three.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 10: Rebuild the Circle IV close

Circle IV cannot use caught/walked-free — nothing is caught here — so it inverts: which implementations earned a number, and which never got on the scale.

**Files:**
- Modify: `slides/slides.md` (slide `# Lock-Free vs. Lock-Based`)

- [ ] **Step 1: Replace the green callout with the tally**

Keep the heading, the `**1 producer + 1 consumer…**` line and the whole JMH console code block exactly as they are — that block is the evidence. Replace only this:

````markdown
<v-click>
<div class="callout green big-callout">
<mdi-rocket class="ico-green" />&nbsp;
Lock-free volatile:&nbsp;<strong>3–4× higher throughput</strong>&nbsp; — for SPSC.
And&nbsp;<em>only</em>&nbsp;for SPSC: add a second producer and it silently loses&nbsp;<strong>~1.5%</strong>&nbsp;of writes.
</div>
</v-click>
````

with:

````markdown
<div class="vs-table">

| <mdi-speedometer class="ico-green inline-ico" /> Measured | <mdi-cancel class="ico-red inline-ico" /> Refused the scale |
|---|---|
| `Volatile` — **12.8M ops/s**, SPSC only. Add a second producer and it silently loses **~1.5%** of writes | `NonVolatile` — broken |
| `LockBased` — **3.1M ops/s** | `FastPath` — broken |
| **3–4×** — and only for SPSC | Two of four never earned a number |

</div>

<v-click>
<div class="callout green">
<mdi-scale-balance class="ico-green" />&nbsp;
<strong>Structurally cannot</strong>&nbsp; tell you whether the code is correct. JMH measures whatever you hand it — broken or not.
</div>
</v-click>
````

The verdict block below stays exactly as it is.

- [ ] **Step 2: Render and inspect — this is the tightest slide in the deck**

Render with `--with-clicks`.
Expected: console block, three-row table, green callout and verdict all inside the frame.

If it clips, apply the documented contingency: delete these four rows from the console block, keeping the `64` rows and the two totals:

```
JmhBenchmark.applesToApples:consumer          64          VOLATILE  thrpt   10   6388150,897 ±   69540,893  ops/s
JmhBenchmark.applesToApples:producer          64          VOLATILE  thrpt   10   6418009,816 ±   68750,521  ops/s
JmhBenchmark.applesToApples:consumer          64              LOCK  thrpt   10   1559657,794 ±  102857,340  ops/s
JmhBenchmark.applesToApples:producer          64              LOCK  thrpt   10   1559658,019 ±  102850,405  ops/s
```

The 3–4× claim holds on the remaining rows. Re-render and confirm.

- [ ] **Step 3: Commit**

```bash
git add slides/slides.md
git commit -m "$(cat <<'EOF'
Rebuild the Circle IV close as a measured/refused tally

Only two of four implementations ever earn a number, which is this
circle's whole argument. Adds the limitation JMH has and the deck never
stated: it cannot tell you whether the code is correct.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 11: Build the appendix

Three slides move behind a new section header after `Thank You`: the decision table and the Footgun slide (both currently `hide: true`, so they become visible but off the main line), and the spurious-wakeup slide.

**Files:**
- Modify: `slides/slides.md` (slides `# What Fray Finds — Spurious Wakeup`, `# A Footgun in the Config`, `# When to Reach for Which Tool`, and the end of the file)

- [ ] **Step 1: Cut the three slides**

Cut — do not retype — these three slides in full, each including its leading `---` separator and any frontmatter block:

1. `# What Fray Finds — Spurious Wakeup` (a `layout: two-cols` slide; keep its `<div class="slide-subtitle">A fifth buffer — off the interface on purpose…` note, which travels with it)
2. `# A Footgun in the Config <mdi-foot-print class="ico-yellow inline-ico" />` (frontmatter is `hide: true`)
3. `# When to Reach for Which Tool` (frontmatter is `hide: true`)

- [ ] **Step 2: Append the appendix at the end of the file**

After the final `Thank You` slide — that is, after its closing `</div>` — append:

````markdown
---
layout: section
---

<div class="section-eyebrow"><mdi-bookmark-multiple /> Appendix</div>

# Appendix
## *The slides that did not make the cut. Ask, and we'll jump to one.*
````

Then paste the three cut slides after it, in this order, each keeping its own `---` separator:

1. `# When to Reach for Which Tool` — most likely Q&A hit
2. `# What Fray Finds — Spurious Wakeup`
3. `# A Footgun in the Config`

- [ ] **Step 3: Remove both `hide: true` lines**

The decision table and the Footgun slide must now render. For each, delete the `hide: true` line from its frontmatter. If that leaves an empty frontmatter block (a `---` immediately followed by `---`), delete the empty block too, leaving a single separator.

- [ ] **Step 4: Verify the structure**

Run: `python3 slides/tools/slidelist.py slides/slides.md`

Expected, exactly:
- `TOTAL 42 slides; 0 hidden; 42 exported by `npm run shots``
- With nothing hidden, `pos` and `range#` now agree for every slide
- 38 is `# Thank You …` — the last slide of the main line
- 39 is `# Appendix`
- 40, 41, 42 are the decision table, the spurious wakeup and the Footgun, in that order

Also run: `grep -n "hide: true" slides/slides.md` — expected: no matches.

- [ ] **Step 5: Render and inspect**

Run: `cd slides && npm run shots -- --range 38-42`
Expected: five PNGs. The appendix header matches the five existing section headers in style. The spurious-wakeup slide still shows its fifth-buffer note, which now does more work than it did mid-deck — this is the first time the audience meets that buffer.

- [ ] **Step 6: Commit**

```bash
git add slides/slides.md
git commit -m "$(cat <<'EOF'
Add an appendix behind the thank-you slide

Parks the decision table, the spurious-wakeup find and the Fray config
footgun behind a section header, reachable by slide number during Q&A
without sitting in the main line. The two previously hidden slides
become visible there.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 12: Full-deck overflow sweep

Individual slides were checked as they changed. This is the pass that catches what moved on slides nobody edited, and confirms the final shape against the spec.

**Files:**
- Modify: `slides/slides.md` (only if the sweep finds clipping)

- [ ] **Step 1: Render the whole deck**

Run: `cd slides && npm run shots`
Expected: 42 PNGs, no export errors.

- [ ] **Step 2: Confirm the final shape matches the spec**

Run: `python3 slides/tools/slidelist.py slides/slides.md`

Expected: `TOTAL 42 slides; 0 hidden; 42 exported by `npm run shots`` — 38 on the main line, 4 in the appendix. This matches the spec's arithmetic: 42 visible − merge − POS/PCT − spurious-wakeup − JMH-101 = 38, plus a 4-slide appendix.

Confirm these headings are **gone**: `The Algorithm — Pseudocode`, `Every Run — A Different Schedule`, `Only Benchmark Correct Code`, `Why JUnit Cannot See This`, `Why Fray Cannot See This`, `Why jcstress Cannot Promise Coverage`.

Confirm these are **present**: `Circle I Closes — JUnit`, `Circle II Closes — Fray`, `Circle III Closes — jcstress`, `Appendix`.

Confirm the pyramid, `Each Layer Answers a Different Question`, is still in the main line in the epilogue, and that both `What Each Circle Costs` slides, `Three Golden Rules` and `Resources` are untouched.

- [ ] **Step 3: Inspect every PNG for clipping**

Open all 42 images in `slides/.shots/`. Look for any content crossing the bottom or right edge of the 16:9 frame. The four rebuilt closes and the merged algorithm slide are the known risks, but deleting slides reflows nothing — check the rest anyway, since it costs one pass.

Fix any clipping with the contingency documented in that slide's task, or by trimming the lowest-value line on the slide.

- [ ] **Step 4: Confirm no stale references remain**

Run:
```bash
grep -n "721\|PCTScheduler\|POSScheduler\|Control.stopMeasurement\|FastTrack" slides/slides.md
```
Expected: no matches.

- [ ] **Step 5: Commit any fixes**

```bash
git add slides/slides.md
git commit -m "$(cat <<'EOF'
Fix clipping found in the full-deck render

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

If Step 3 found nothing, skip this commit and say so.

---

## Not in this plan

Carried over from the spec as explicit non-goals:

- No epilogue scoreboard slide, and no "don't reach for it when…" column on the decision table. Both were considered and declined during brainstorming.
- No changes to `demos/`. The Fray Surefire config is already documented at `demos/05-fray/README.md:155-174`.
- Re-recording `demos/results/fray/` against current class names — the recordings predate the `FastTrack` → `FastPath` rename, and every `fray/edgecase/` test carries `@Disabled`, so regenerating them is its own task.
- The Polish deck and the abstracts under `docs/`.
