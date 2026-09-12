# slides — the Slidev presentation

"From False Confidence to Systematic Proof: a story about testing Concurrent
Algorithms in Java" — built with [Slidev](https://sli.dev).

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
```

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
| Circle II — Fray | Systematic interleavings, deterministic replay, the blind spot |
| Circle III — jcstress | Actors/outcomes, the missing-`volatile` result, Fray vs. jcstress |
| Circle IV — JMH | Lock-free vs. lock-based throughput |
| Epilogue | The testing pyramid, when to reach for which tool, three golden rules |

The code and output shown on the slides come from
[`../demos`](../demos) — the recorded runs in `demos/results/` are the source
for the jcstress tables and JMH numbers.

## Notes

* `slides/dist/` and `slides/node_modules/` are git-ignored, as are exported
  PDFs (`*.pdf`).
* The Polish-language talk abstract is in [`../docs/abstract.md`](../docs/abstract.md).
