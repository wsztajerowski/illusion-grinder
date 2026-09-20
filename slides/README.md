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

```bash
cd slides

# Development server with hot reload, at http://localhost:3030
npm run dev

# Static site into slides/dist/
npm run build

# PDF export (installs Playwright's Chromium on first run)
npm run export

# One PNG per slide into slides/.shots/ — for visually checking the deck
npm run shots
npm run shots -- --range 12-18     # just a few slides
npm run shots -- --with-clicks     # one image per click step, not per slide
```

`npm run shots` renders the whole deck (42 slides) in about 25 seconds and
writes `.shots/1.png` … `.shots/42.png`. The numbering follows slide order, so
`--range` takes the same numbers the presenter view shows. It is the quickest
way to catch overflowing content, clipped code blocks and broken layout without
clicking through the deck by hand.

Slidev extras once `npm run dev` is up:

* `http://localhost:3030/presenter` — presenter view with notes and timer
* `http://localhost:3030/overview` — all slides at a glance
* `f` fullscreen, `o` overview, `d` toggle dark mode, `g` go to slide

## Deck structure

| Section | Covers |
|---|---|
| Prologue | Green CI, production NPE — the problem statement |
| Cast of characters | Lamport's circular buffer, four implementations, one interface |
| Circle I — JUnit | Contract tests, and why they all pass |
| Circle II — Fray | Schedule sampling (POS/PCT), two bugs found, deterministic replay, the blind spot |
| Circle III — jcstress | Actors/outcomes, the missing-`volatile` result, TOCTOU, Fray vs. jcstress, why coverage is never proven |
| Circle IV — JMH | Lock-free vs. lock-based throughput |
| Epilogue | The testing pyramid, when to reach for which tool, what each circle costs, three golden rules |

The code and output shown on the slides come from
[`../demos`](../demos) — the recorded runs in `demos/results/` are the source
for the jcstress tables and JMH numbers.

## Notes

* `slides/dist/`, `slides/node_modules/` and `slides/.shots/` are git-ignored,
  as are exported PDFs (`*.pdf`).
* The Polish-language talk abstract is in [`../docs/abstract-pl.md`](../docs/abstract-pl.md).
