# Systematic Concurrency Testing

Demo repository for the talk **"The Illusion Grinder: Four Circles of Testing
Hell for Concurrent Java"** — four implementations of
Lamport's circular buffer, two of them deliberately broken, all four passing
CI, put through four progressively more brutal layers of testing until each bug
is dragged into the light.

Every claim in the presentation is reproducible from this repository. The
commands below are the ones used on stage.

---

## The premise

A bounded queue: simple, elegant, "battle-tested". Thousands of green unit
tests. Deployed to production. Then:

```
Exception in thread "consumer-3" java.lang.NullPointerException
    at pl.wsztajerowski.demo.lamport.poll(...)
```

Green unit tests are not a concurrency proof. A single-threaded test cannot
construct an interleaving, cannot delay a write past a cache boundary, and
cannot force a spurious wakeup — so it cannot see the bugs that actually take
production down.

## Four questions, four tools

| # | Layer | Asks | Finds | Cannot see |
|---|---|---|---|---|
| I | **JUnit** ([`04-contract-tests`](demos/04-contract-tests)) | Does the algorithm work at all? | logic errors, regressions | anything concurrent |
| II | **Fray** ([`05-fray`](demos/05-fray)) | Does any thread schedule break it? | races, deadlocks, missing re-checks, spurious wakeups | memory visibility — every schedule it explores is sequentially consistent |
| III | **jcstress** ([`06-jcstress`](demos/06-jcstress)) | Does the CPU / JVM / JIT break it? | JMM bugs: visibility, reordering, atomicity | rare schedules it never happens to hit |
| IV | **JMH** ([`07-jmh`](demos/07-jmh)) | What does correctness cost? | throughput regressions | correctness — it measures whatever you give it, broken or not |

Each layer catches bugs the previous one structurally cannot. Skipping one
leaves a class of bug permanently invisible.

## The four implementations

All four implement the same interface, all four pass the same 68 contract
tests, and two of them are badly broken.

```java
public interface LamportBuffer<E> {
    boolean     offer(E element);
    Optional<E> poll();
    boolean     isEmpty();
    int         size();
}
```

| Implementation | Module | Design | Verdict |
|---|---|---|---|
| `VolatileLamportBuffer` | [`01-lamport-volatile`](demos/01-lamport-volatile) | `volatile` cursors, no lock | correct **for SPSC only** — check-then-act breaks with a second producer |
| `NonVolatileLamportBuffer` | [`02-lamport-single-thread`](demos/02-lamport-single-thread) | identical code, `volatile` removed | **broken** — no happens-before edge; writes never become visible |
| `LockBasedLamportBuffer` | [`03-lamport-lock`](demos/03-lamport-lock) | `ReentrantLock` around every method | correct MPMC — though *composing* its methods still is not atomic |
| `FastPathLamportBuffer` | [`03-lamport-lock`](demos/03-lamport-lock) | lock + unlocked fast-path check | **broken** — no re-check after acquiring the lock → `NullPointerException` |

Plus `ConditionalLamportBuffer` (also in `03-lamport-lock`): a blocking
`put`/`take` queue that uses `if` instead of `while` around
`Condition.await()`, so a spurious wakeup makes it read from an empty buffer.

## Which tool catches which bug

| Bug | JUnit | Fray | jcstress |
|---|---|---|---|
| Unlocked fast path, no re-check (`FastPathLamportBuffer`) | passes | **catches** — NPE at iteration 2 | **catches** — NPE in 1.1% of 580m samples, but only with a two-consumer test |
| `if` instead of `while` around `await()` (`ConditionalLamportBuffer`) | passes | **catches** — assertion failure at iteration 1 | not observed by the current test |
| TOCTOU in client code over a thread-safe object | passes | **catches** | no test at this layer |
| Two producers on an SPSC buffer (`VolatileLamportBuffer`) | passes | catches | **catches** — lost update in 1.48% of 583m samples, plus 0.51% spurious "full" |
| Missing `volatile` (`NonVolatileLamportBuffer`) | passes | **passes** | **catches** — ~35k lost writes in 655m samples |

Read the two middle columns against each other. Fray finds the fast-path NPE
*by construction*, within the first handful of schedules it tries, from an
ordinary two-consumer test, and hands back a recording that replays it deterministically.
jcstress finds the same NPE at 1.1% — but only once you write a test shaped like
the bug: two consumers, one pre-filled element, and the NPE caught so it becomes
an outcome instead of an aborted run. The original single-consumer test could
never have found it, and did not. Conversely, the missing-`volatile` bug is
invisible to Fray — it samples schedules, and every schedule it produces is
sequentially consistent — while jcstress finds it at 0.01% frequency.

The difference is not what each tool *can* see. It is how much you have to know
in advance.

Fray explores schedules, jcstress explores hardware. Neither subsumes the
other; both rows above are reproducible from this repository.

---

## Repository layout

```
demos/          multi-module Maven project (Java 25) — see demos/README.md
  00-lamport                 interface + custom AssertJ assertions (test-jar)
  01-lamport-volatile        VolatileLamportBuffer
  02-lamport-single-thread   NonVolatileLamportBuffer
  03-lamport-lock            LockBased / FastPath / Conditional buffers
  04-contract-tests          JUnit 5 parameterized contract suite
  05-fray                    Fray systematic concurrency tests
  06-jcstress                jcstress stress tests
  07-jmh                     JMH throughput benchmarks
  results/                   recorded fray / jcstress / jmh output, kept in git
slides/         Slidev presentation — see slides/README.md
  slides.md                  the whole deck, 42 slides
  style.css                  custom styling
  tools/slidelist.py         prints slide numbering for `--range`
docs/           talk abstracts (en / pl) and post-talk poll templates
```

## Prerequisites

| Tool | Version | Needed for |
|---|---|---|
| JDK | **25** | everything (`maven.compiler.source/target` is 25) |
| Maven | 3.9+ | the `demos` build |
| Node.js | 18+ | the Slidev deck only |

```bash
java -version   # openjdk 25
mvn -v
```

## Quick start

```bash
git clone git@github.com:wsztajerowski/illusion-grinder.git
cd illusion-grinder

# Build everything, fast (skips unit tests AND the long jcstress run)
mvn -f demos/pom.xml clean install -DskipTests -Dexec.skip=true

# Circle I — the comfortable lie: 68 green tests, two broken implementations
mvn -f demos/pom.xml -pl 04-contract-tests -am test
```

> **Do not run a plain `mvn install`** unless you have half an hour.
> `-DskipTests` skips Surefire but *not* `exec-maven-plugin`, which runs the
> full jcstress suite during `06-jcstress`'s `integration-test` phase. Add
> `-Dexec.skip=true`.

---

## Running each circle

### I. JUnit contract tests

```bash
mvn -f demos/pom.xml -pl 04-contract-tests -am test
```

17 test methods × 4 implementations = 68 cases. Expect
`Tests run: 68, Failures: 0, Errors: 0, Skipped: 0` — that is the point.
Details: [`demos/04-contract-tests/README.md`](demos/04-contract-tests).

### II. Fray — systematic interleavings

```bash
# All active Fray tests
mvn -f demos/pom.xml -pl 05-fray -am test

# One class / one method
mvn -f demos/pom.xml -pl 05-fray -am test -Dtest=LockBasedLamportBufferFrayTest -Dsurefire.failIfNoSpecifiedTests=false
mvn -f demos/pom.xml -pl 05-fray -am test -Dtest='VolatileLamportBufferFrayTest#fifoOrderUnderConcurrency' -Dsurefire.failIfNoSpecifiedTests=false

# Send reports somewhere else (default: demos/05-fray/build/fray/fray-report)
mvn -f demos/pom.xml -pl 05-fray -am test -Dfray.workDir=target/fray-report
```

> `-Dsurefire.failIfNoSpecifiedTests=false` is needed alongside `-Dtest=` whenever
> `-am` is present: the filter is applied to every module in the reactor, and the
> upstream modules have no matching test, which would otherwise fail the build.

Iteration count and scheduler are **annotation attributes, not CLI flags**:
`@ConcurrencyTest(iterations = 5000, scheduler = PCTScheduler.class)`. Defaults
are 1000 iterations and `POSScheduler`.

Three tests under `edgecase/` are `@Disabled` on purpose — they are the failing
demos (fast-path NPE, spurious wakeup, TOCTOU). Remove the annotation to watch
Fray find them, then replay the recorded schedule under a debugger with
`@ConcurrencyTest(replay = "...")`.

Budget time for this module: `prepare-fray` builds an instrumented ~186 MB JDK
image into `target/fray/fray-java` on a first run (cached until `clean`), and
each `@ConcurrencyTest` replays its body 1000 times.

Details: [`demos/05-fray/README.md`](demos/05-fray).

### III. jcstress — real hardware

```bash
# Build the fat jar and run the full suite (minutes, not seconds)
mvn -f demos/pom.xml -pl 06-jcstress -am verify

# Build only, then drive the jar directly
mvn -f demos/pom.xml -pl 06-jcstress -am package
java -jar demos/06-jcstress/target/jcstress.jar -l                 # list tests
java -jar demos/06-jcstress/target/jcstress.jar -t TwoProducers    # one test
java -jar demos/06-jcstress/target/jcstress.jar -m quick           # faster preset
java -jar demos/06-jcstress/target/jcstress.jar -m stress          # hunt rare outcomes
```

Note `verify`, not `test` — the run is bound to the `integration-test` phase.
The HTML report lands in `demos/06-jcstress/target/jcstress-results/index.html`.

The headline result (`NonVolatileLamportBuffer`, with its `@JCStressTest`
annotation re-enabled):

```
   RESULT      SAMPLES     FREQ       EXPECT  DESCRIPTION
  1, 0, 0       35.222   <0,01%    Forbidden  Producer offered, consumer never saw it
  1, 0, 1  308.860.121   47,12%   Acceptable  Consumer polls before producer offers
  1, 1, 0  346.641.431   52,88%   Acceptable  Producer offers, consumer polls it
```

~35,000 lost writes out of 655 million executions — the bug Fray reported as
clean. The archived report for that run is checked in at
`demos/results/jcstress/results/pl.wsztajerowski.demo.lamport.jcstress.NonVolatileSpscLamportBufferJcstressTest.html`
(status `FAILED`, forbidden state `1, 0, 0`). Details:
[`demos/06-jcstress/README.md`](demos/06-jcstress).

### IV. JMH — the bill

```bash
mvn -f demos/pom.xml -pl 07-jmh -am package
java -jar demos/07-jmh/target/benchmarks.jar ".*ApplesToApplesLamportBufferBenchmark.*"
```

Same topology (1 producer + 1 consumer), two implementations:

| Capacity | `VolatileLamportBuffer` | `LockBasedLamportBuffer` | Ratio |
|---|---|---|---|
| 64 | 16,814,467 ops/s | 4,277,676 ops/s | 3.9× |
| 1024 | 18,750,040 ops/s | 5,942,622 ops/s | 3.2× |

Lock-free wins by ~3× on an SPSC workload — a number that is only worth having
because circles II and III established the implementation is correct there.
Details: [`demos/07-jmh/README.md`](demos/07-jmh).

### Everything, in order

```bash
mvn -f demos/pom.xml clean install -DskipTests -Dexec.skip=true   # build
mvn -f demos/pom.xml -pl 04-contract-tests -am test               # I.   JUnit
mvn -f demos/pom.xml -pl 05-fray           -am test               # II.  Fray
mvn -f demos/pom.xml -pl 06-jcstress       -am verify             # III. jcstress
mvn -f demos/pom.xml -pl 07-jmh            -am package && \
  java -jar demos/07-jmh/target/benchmarks.jar                    # IV.  JMH
```

---

## The slides

```bash
cd slides
npm install

npm run dev      # authoring loop, hot reload, http://localhost:3030
npm run shots    # one PNG per slide into .shots/ — how you check the deck
npm run export   # slides.pdf, 42 pages — handout and stage backup
npm run build    # static site into slides/dist/ — for hosting
```

`npm run shots` is the one worth knowing about. Content that reads fine in the
Markdown routinely overflows the 16:9 frame, and neither the dev server nor the
build will tell you: the whole deck renders to PNG in about 25 seconds, and you
look. Narrow it with `-- --range 12-18`, or `-- --with-clicks` for one image per
click step.

```bash
python3 tools/slidelist.py slides.md   # slide numbering, before using --range
```

`--range` counts only slides that render, so a single `hide: true` slide shifts
every number after it. This prints both numbers side by side.

`npm run export` produces a PDF that opens on any machine when the venue's
projector, network or your Node install does not — worth generating before
every talk. It exports from a copy of the deck with the feedback-poll QR
removed, so a PDF downloaded weeks later carries no dead link. Click steps are
flattened one page per slide; pass `-- --with-clicks` if the reveals matter.

**Published on every push to `main`** that touches `slides/`:

| URL | What |
|---|---|
| <https://wsztajerowski.github.io/illusion-grinder/> | the deck, navigable in a browser |
| <https://wsztajerowski.github.io/illusion-grinder/slides.pdf> | the PDF, for download |

Details, including the two editing traps that render without an error:
[`slides/README.md`](slides). Talk abstracts and the post-talk poll templates
are in [`docs/`](docs).

## Recorded results

Build directories are git-ignored, so the runs worth keeping were copied into
`demos/results/`:

| Path | Contents |
|---|---|
| [`demos/results/fray/fray-report/`](demos/results/fray) | recorded Fray schedules — replayed by `@ConcurrencyTest(replay = ...)` |
| [`demos/results/jcstress/results/`](demos/results/jcstress) | HTML report from a full jcstress run |
| [`demos/results/jmh/jmh-output.json`](demos/results/jmh) | JMH throughput numbers |

## Three golden rules

1. **Never trust green unit tests as a concurrency proof.** All four
   implementations passed; two were broken.
2. **Fray and jcstress are complementary, not interchangeable.** Fray finds
   scheduling bugs; jcstress finds memory-model bugs. Run both.
3. **Only benchmark code you have already proven correct.** Accurate numbers
   for broken code are worse than no numbers.

## Further reading

**Tools**

* [Fray](https://github.com/cmu-pasta/fray) — CMU PASTA Lab
* [jcstress](https://openjdk.org/projects/code-tools/jcstress/) — OpenJDK
* [JMH](https://github.com/openjdk/jmh) — OpenJDK

**Papers and specs**

* [A Randomized Scheduler with Probabilistic Guarantees of Finding Bugs](https://www.microsoft.com/en-us/research/wp-content/uploads/2016/02/asplos277-pct.pdf) (PCT)
* [Partial Order Aware Concurrency Sampling](https://www.cs.columbia.edu/~junfeng/papers/pos-cav18.pdf) (POS)
* [JSR-133: Java Memory Model and Thread Specification](https://jcp.org/en/jsr/detail?id=133)

## License

[MIT](LICENSE)
