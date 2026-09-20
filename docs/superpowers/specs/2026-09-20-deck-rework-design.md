# Deck rework: limitations, results, less theory

**Date:** 2026-09-20
**Target:** `slides/slides.md`
**Status:** approved in brainstorm, pending spec review

## Why

Three notes from the speaker after the last delivery of *The Illusion Grinder*:

1. Add the limitations of the libraries — what they cannot find, and when not to use them.
2. Summarise the example results. Focus on the use case rather than the theory.
3. Cut the theory. Less of it on the slides.

The slot has room to spare, so notes 1 and 2 are *additions* rather than trades against
note 3. The theory cut is about attention, not the clock.

## Decisions taken

| Question | Decision |
|---|---|
| Where the results summary lives | Per-circle, folded into the four existing verdict slides — not a new epilogue scoreboard |
| How far limitations go | Per-circle only. One "structurally cannot" line per tool. No new slide, no cross-cutting "when not to bother" slide |
| Housekeeping asides | Deleted outright. No speaker-notes channel introduced |
| The fifth-buffer note (`:497`) | Kept — it travels with its slide |
| Spurious-wakeup slide | Moves to a new **Appendix** section after `Thank You` |
| POS vs PCT | Deleted. Not crucial to this deck |
| Pyramid | Stays in the main line, in the epilogue, where it is (see note below) |
| `When to Reach for Which Tool` | Stays out of the main line — moves into the Appendix |

**One reading to confirm.** The instruction was to keep the pyramid "within the main plot
before the thank you slide". It already sits before `Thank You` — as the epilogue's opening
slide, ahead of the two cost slides, the golden rules and resources. This spec leaves it in
that position. If the intent was *immediately* before `Thank You`, it moves after
`Resources` instead; one line changes.

Explicitly **not** doing: no epilogue scoreboard slide; no "don't reach for it when…"
column on the decision table. Both remain available later.

## A. The four circle-closing slides

The four slides carrying `<div class="verdict">` are rebuilt from theory bullets into a
tally. Same count, same position, same verdict line at the bottom. In each case the
existing "X proves / X cannot prove" callout is **kept** and carries the limitation — the
bullet lists above it are what gets replaced. Keeping the callout rather than inventing a
new block also limits the overflow risk.

### Circle I — replaces the bullets in `Why JUnit Cannot See This` (`slides.md:333`)

Retitled `Circle I Closes — JUnit`.

```
CAUGHT                        WALKED FREE
nothing                       all four. 68/68 green.
(it was never going to)       Volatile · NonVolatile
                              LockBased · FastPath

STRUCTURALLY CANNOT  start a second thread.
                     No race, no failure — by construction.

Score: 0 of 4
```

Keeps the existing blue callout ("Write contract tests first. Keep them always. But do not
mistake a green suite for a concurrency correctness proof.") — that is the use-case
guidance, and it survives untouched. Verdict line unchanged.

### Circle II — replaces the bullets in `Why Fray Cannot See This` (`slides.md:626`)

Retitled `Circle II Closes — Fray`.

```
CAUGHT                             WALKED FREE
FastPath — NPE at iteration 4      NonVolatile — 2000 schedules,
  of 1000, replayable under a        all green, still broken
  debugger
Conditional take() — null from     Volatile @ 2 producers —
  a spurious wakeup, iteration 1     not a scheduling bug
  → appendix

Score so far: 2 of 4
```

Keeps the purple `Fray proves / Fray cannot prove` callout as the limitation block, and the
verdict line. The `→ appendix` marker is deliberate: it credits Fray with a find whose slide
now lives behind the thank-you, and doubles as a Q&A hook.

### Circle III — replaces the bullets in `Why jcstress Cannot Promise Coverage` (`slides.md:866`)

Retitled `Circle III Closes — jcstress`.

```
CAUGHT                              WALKED FREE
NonVolatile — 35,222 of 655M        nothing.
  runs, 0.005%. The bug Fray        Every bug in the lineup
  structurally cannot see           is now visible.
Volatile @ 2 producers —
  ~1.5% lost updates
FastPath — 1.1%

STRUCTURALLY CANNOT  find an outcome you never declared
                     with @Outcome. Shorten the run and the
                     0.005% row disappears — same code,
                     green report.

Score: 4 of 4
```

Of the four bullets on the current slide, two are theory ("Fray controls one thing",
"jcstress controls nothing") and are cut; the other two are a result and a limitation, and
are absorbed above. Keeps the orange proves/cannot-prove callout and the verdict.

### Circle IV — rebuilds `Lock-Free vs. Lock-Based` (`slides.md:954`)

Circle IV cannot use "caught / walked free", so it inverts into the same shape. The JMH
console table stays — it is the evidence. The green callout is replaced by:

```
MEASURED                      REFUSED THE SCALE
Volatile   12.8M ops/s        NonVolatile — broken
  SPSC only. Add a second     FastPath — broken
  producer and it silently
  loses ~1.5% of writes
LockBased   3.1M ops/s

3–4×, and only for SPSC.

STRUCTURALLY CANNOT  tell you whether the code is correct.
                     It measures whatever you hand it.
```

Only two of four implementations ever earn a number — which is this circle's whole
argument, currently spread over two slides of prose. Verdict line unchanged.

**Overflow contingency:** this is the tightest of the four (console table + recap + verdict).
If `npm run shots` shows it clipped, drop the four `capacity 1024` rows from the console
table and keep the `64` rows; the 3–4× claim holds on either pair.

## B. Per-slide disposition

44 slides in the file today, 42 visible. Sixteen entries change:

| # | Slide | `slides.md` | Call | Why |
|---|---|---|---|---|
| 6+7 | `The Algorithm` + `— Pseudocode` | 117, 152 | merge | Two slides, one idea. Keep the buffer picture and `poll()` pseudocode — that is where the NPE lands. `offer()` is symmetric and gets a one-line mention |
| 13 | `Why JUnit Cannot See This` | 333 | rebuild | → Circle I recap (section A) |
| 15 | `Fray's Core Idea` | 367 | compress | 4 bullets → 2. The big quote does the work |
| 16 | `Every Run — A Different Schedule` | 384 | **delete** | POS vs PCT is paper-level detail. Papers stay in Resources |
| 17 | `A Fray Test` | 405 | trim | Delete the `PCTScheduler` note at `:427` — it dies with slide 16 anyway |
| 19 | `What Fray Finds — Spurious Wakeup` | 492 | → appendix | Keeps its fifth-buffer note at `:497` |
| 20 | `Deterministic Replay` | 549 | fix + rehook | See section D |
| 21 | `A Footgun in the Config` | 582 | → appendix | Currently `hide: true`; unhide there. Its content, XML included, is already in `demos/05-fray/README.md:155-174`, so nothing is lost |
| 23 | `Why Fray Cannot See This` | 626 | rebuild | → Circle II recap |
| 25 | `jcstress's Core Idea` | 663 | compress | 5 bullets → 2 |
| 27 | `Why volatile Is Non-Negotiable` | 711 | keep, drop `:716` | Cache diagrams stay — the on-ramp for attendees who rarely write concurrent code. The transition line goes |
| 32 | `Why jcstress Cannot Promise Coverage` | 866 | rebuild | → Circle III recap |
| 34 | `Only Benchmark Correct Code` | 901 | **fold into 35** | JMH 101 becomes a caption under the benchmark code |
| 35 | `A JMH Benchmark` | 926 | trim + absorb | Delete the `-f 2 -wi 5 -i 5` / `Control.stopMeasurement` note at `:947`; gains the caption from 34 |
| 36 | `Lock-Free vs. Lock-Based` | 954 | rebuild | → Circle IV recap |
| 39 | `When to Reach for Which Tool` | 1022 | → appendix | Stays off the main line; unhidden inside the appendix |

Untouched: cover, all five section headers, the cold open, the tools grid, the four
implementations, `The Catch`, `The Foundation`, `The False Negative`, the fast-path find,
`Fray's Blind Spot`, the jcstress test / result / screenshot, TOCTOU, `Fray vs. jcstress`,
**the pyramid**, both cost slides, the golden rules, resources, thank-you.

### Slide arithmetic

```
42 visible today
 −1  merge 6+7
 −1  delete 16 (POS/PCT)
 −1  19 → appendix
 −1  34 folded into 35
 ───
 38  main line

  +  appendix: section header + 3 parked slides = 4
 ───
 42  slides in the file (38 main + 4 appendix)
```

### Asides: the full list

Deleted: `:427` (PCTScheduler), `:716` (transition line), `:947` (JMH CLI flags and
`Control.stopMeasurement`).
Kept and moved: `:497` (fifth buffer), travelling with slide 19.
Kept in place: `:793` (TOCTOU definition), `:809` (why the arbiter counts), `:1037` and
`:1050` (the timing anchor and its "your suite, your machine" caveat — this is a results
summary, not an aside).

## C. The appendix

A new `layout: section` slide after `Thank You`, titled **Appendix**, styled like the five
existing section headers. Behind it, in this order:

1. `When to Reach for Which Tool` — most likely Q&A hit, so it goes first
2. `What Fray Finds — Spurious Wakeup`
3. `A Footgun in the Config`

Order is trivially changeable. Slidev keeps these reachable by slide number during Q&A
without their appearing in the main flow.

## D. Data corrections

The recorded Fray logs under `demos/results/fray/fray-report/` do not match the deck:

| Test | Deck says | Log says |
|---|---|---|
| `ConditionalLamportBufferFrayTest` (spurious wakeup) | `iter: 721, step: 1850` (`slides.md:552`) | `iter: 1, step: 1850` — same timestamp, same elapsed time |
| `FastTrackLamportBufferFrayTest` (FastPath NPE) | not quoted | `iter: 4, step: 26, 32 ms` |

`README.md` states that every claim in the presentation is reproducible from this
repository, so the deck should carry the recorded numbers.

This breaks the `Deterministic Replay` slide's hook, which currently reads *"the test fails
on iteration 721. Which schedule caused it?"* — a question that does not work at iteration
1. Replacement hook, which is both true and a stronger argument for replay:

> It failed on iteration 4 of 1000. Re-run it and it passes — the next run samples
> different schedules. Without a recording, the bug is gone.

**Decided (2026-09-20): use the real iteration numbers.**

### Two constraints on applying that

**1. The recordings are stale.** The logs name `FastTrackLamportBufferFrayTest` and
`FastTrackLamportBuffer`; the repo today has `FastPathLamportBuffer` and no `FastTrack`
file anywhere. `demos/results/fray/` was committed once, in `a9d6e9f`, and the class was
renamed afterwards. The recorded *numbers* are genuine; the surrounding transcript is from
before the rename. Re-recording is not a quick fix either — every `fray/edgecase/` test
carries `@Disabled`, so an ordinary `mvn test` does not regenerate these logs, and POS
sampling is randomised, so a fresh run would land on a different iteration anyway.

**Consequence:** the console block on the slide stays a lightly edited transcript either
way — class names updated to match today's code. What changes is that the *number* is now
the recorded one instead of an invented one.

**2. The replay slide quotes a test the audience will no longer have seen.** It currently
shows the `ConditionalLamportBufferFrayTest` stack trace, and that bug's slide moves to the
appendix (section B, slide 19). So the replay slide switches to the FastPath NPE — a bug
the audience saw two slides earlier, whose stack trace is the literal callback to the cold
open on slide 3:

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

`Optional.of(null)` is where the NPE actually comes from — a detail the current slide does
not show.

New hook, replacing *"the test fails on iteration 721. Which schedule caused it?"*:

> It failed on iteration 4 of 1000. Re-run it and it passes — the next run samples
> different schedules. Without a recording, the bug is gone.

### Follow-up, not part of this rework

`demos/results/fray/` should be re-recorded against current class names, which means
temporarily enabling the `@Disabled` edgecase tests. Tracked here so it is not forgotten;
`README.md`'s reproducibility promise is weaker than it reads until that happens.

### Numbers not re-verified

The recap slides reuse the jcstress and JMH figures verbatim from the existing slides —
`35,222 / 655M / 0.005%`, `~1.5%`, `1.1%`, `12.8M` and `3.1M` ops/s — which were reconciled
against the demos in commit `8476ba3`. The HTML reports under `demos/results/jcstress/`
break results down per compilation mode and scheduling class rather than carrying the
aggregate, so they neither confirm nor contradict those totals; the totals come from the
console run. No independent audit of them was performed for this rework.

## Verification

1. `cd slides && npm run shots` — renders every slide to PNG in ~25 s.
2. Inspect the four rebuilt recap slides and the merged algorithm slide directly. These are
   the overflow risks: each recap packs two columns, a limitation line and a verdict into
   one 16:9 frame.
3. Confirm the appendix slides render and that the main line ends at `Thank You`.
4. Confirm nothing references the deleted POS/PCT slide.

## Out of scope

- Any change to `demos/`. The Fray Surefire config is already documented at
  `demos/05-fray/README.md:155-174`, so the Footgun slide needs no new home in the repo.
- Re-recording Fray, jcstress or JMH results.
- The Polish deck or the abstracts.
