# slides — the Slidev presentation

"The Illusion Grinder: Four Circles of Testing Hell for Concurrent Java" —
built with [Slidev](https://sli.dev).

Everything lives in a single deck, `slides.md`, with custom styling in
`style.css` and images under `src/resources/`. Icons come from
`@iconify-json/mdi` and are used inline as components (`<mdi-fire-alert />`).

## Prerequisites

Node.js 18+ and npm.

```bash
cd slides
npm install
```

## Commands

The four npm scripts are the same Slidev binary with different flags, and they
serve different moments. `npm run shots` is the one you will reach for most —
it is how you check that what you wrote actually fits on the slide.

### `npm run dev` — while you are writing

```bash
npm run dev          # http://localhost:3030
```

The authoring loop. Hot-reloads on every save of `slides.md` or `style.css`, so
it is what you leave running while editing. Also the only way to reach:

* `http://localhost:3030/presenter` — presenter view, notes and timer
* `http://localhost:3030/overview` — all slides at a glance
* `f` fullscreen · `o` overview · `d` dark mode · `g` go to slide

**Use it to write. Do not use it to check the deck** — you will click through
42 slides and still miss the one whose last line sits two pixels below the
frame. That is what `npm run shots` is for.

### `npm run shots` — after every edit

```bash
npm run shots                      # all 42 slides into .shots/
npm run shots -- --range 12-18     # just a few
npm run shots -- --range 12,19,28  # or a specific set
npm run shots -- --with-clicks     # one image per click step
```

Renders one PNG per slide into `.shots/` in about 25 seconds. **This is the
verification step, and it is not optional.** Content that looks fine in the
Markdown routinely overflows the 16:9 frame, and a slide that is clipped on
stage is clipped in front of a room. Several defects in this deck's history —
clipped verdict lines, a table running past the right edge, two callouts that
silently lost a line break — were invisible in the source and obvious in the
PNG.

Use `--with-clicks` when a slide's *build-up* matters, not just its final
state: it writes `012-01.png`, `012-02.png` … one per `v-click` step.

### `python3 tools/slidelist.py slides.md` — before `--range`

```bash
python3 tools/slidelist.py slides.md
```

Prints every slide with two numbers and its heading:

```
 pos  range#  line    slide
  12      12  L307    # Circle I Closes — JUnit
  21      --  L581    # A Footgun in the Config     <- hidden
  23      22  L625    # Why Fray Cannot See This
```

`pos` is the slide's position in the file; **`range#` is what `--range` takes**,
because Slidev exports only slides that render. A single `hide: true` slide
shifts every number after it, so without this you will render the wrong frame
and not notice. Adding or deleting a slide renumbers everything below it too —
run this after any structural edit.

### `npm run export` — for handouts and backups

```bash
npm run export                     # slides-export.pdf, one page per slide
npm run export -- --with-clicks    # one page per click step
```

Produces `slides/slides-export.pdf` (42 pages, ~1 MB). Installs Playwright's
Chromium on first run.

Worth doing before every talk, for two reasons: a PDF opens on any machine when
the venue's projector, network or your Node install does not, and it is the
format conferences and attendees ask for afterwards. Note that click steps are
flattened — each slide is one page in its final state — so pass `--with-clicks`
if the reveals matter in a handout.

### `npm run build` — for hosting

```bash
npm run build        # static site into slides/dist/
```

A self-contained static site. Use it when publishing the deck somewhere people
browse it themselves (GitHub Pages, any static host); they need no Node and no
checkout. Not needed for presenting.

## Editing gotchas

* **`.callout` is `display: flex`.** Whitespace between its children collapses
  and `<br>` does nothing. Write `&nbsp;` before an inline element, and wrap
  content in a `<div>` if you need a line break inside a callout.
* **ASCII diagrams go in `<pre class="buffer-viz">`, not a code fence.** A
  fenced diagram sharing a slide with another fence makes Slidev leak
  `<CodeBlockWrapper …>` tags into the rendered output, and in some
  arrangements fail to compile with `Invalid end tag`.
* **Run `npm run shots` afterwards.** Both of the above render without an error
  and are only visible in the image.

## Deck structure

| Section | Covers |
|---|---|
| Prologue | Green CI, production NPE — the problem statement |
| Cast of characters | Lamport's circular buffer, four implementations, one interface |
| Circle I — JUnit | Contract tests, and why they all pass |
| Circle II — Fray | A Fray test and how schedules vary, the fast-path NPE, deterministic replay, the blind spot, and the circle's close |
| Circle III — jcstress | Actors/outcomes, the missing-`volatile` result, TOCTOU, Fray vs. jcstress, the circle's close and the raw HTML report |
| Circle IV — JMH | A benchmark, lock-free vs. lock-based throughput, and the circle's close |
| Epilogue | The testing pyramid, what each circle costs, three golden rules, resources |
| Appendix | Behind the thank-you slide: the decision table, the spurious-wakeup find, the Fray config footgun |

The code and output shown on the slides come from
[`../demos`](../demos) — the recorded runs in `demos/results/` are the source
for the jcstress tables and JMH numbers.

## Notes

* `slides/dist/`, `slides/node_modules/` and `slides/.shots/` are git-ignored,
  as are exported PDFs (`*.pdf`).
* The Polish-language talk abstract is in [`../docs/abstract-pl.md`](../docs/abstract-pl.md).
