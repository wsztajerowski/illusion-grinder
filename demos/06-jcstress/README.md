# 06-jcstress — Circle III: the actual hardware

[jcstress](https://openjdk.org/projects/code-tools/jcstress/) (OpenJDK) is the
Java Concurrency Stress test harness. It runs a tiny piece of code billions of
times on real CPUs, in tight loops designed to defeat the JIT's ability to hide
reordering, and tabulates every observed outcome against what you declared
legal.

Where [Fray](../05-fray) asks *"does any schedule break it?"*, jcstress asks
*"does the CPU, JVM and JIT break it?"* — and that is a different question.

## How a jcstress test is written

```java
@JCStressTest
@Outcome(id = "1, 1, 0", expect = ACCEPTABLE,  desc = "Producer offers, consumer polls it")
@Outcome(id = "1, 0, 1", expect = ACCEPTABLE,  desc = "Consumer polls before producer offers")
@Outcome(id = "1, 0, 0", expect = FORBIDDEN,   desc = "Element vanished — visibility bug")
@State
public class VolatileSpscLamportBufferJcstressTest {

    private final LamportBuffer<Integer> queue = VolatileLamportBuffer.createBuffer(Integer.class, 2);

    @Actor   public void producer(III_Result r) { r.r1 = queue.offer(1) ? 1 : 0; }
    @Actor   public void consumer(III_Result r) { r.r2 = queue.poll().orElse(0); }
    @Arbiter public void arbiter (III_Result r) { r.r3 = queue.poll().orElse(0); }
}
```

* `@State` — the object under test, freshly allocated per invocation.
* `@Actor` — a method run concurrently on its own thread, exactly once per state.
* `@Arbiter` — runs after all actors have finished; observes the final state.
* `@Outcome` — a result tuple with a verdict: `ACCEPTABLE`,
  `ACCEPTABLE_INTERESTING` (legal but noteworthy — reported separately),
  `FORBIDDEN` (fails the run), or `UNKNOWN`.

## Tests in this module

| Class | Subject | Looking for |
|---|---|---|
| `VolatileSpscLamportBufferJcstressTest` | `VolatileLamportBuffer` | SPSC baseline — must never lose an element |
| `TwoProducersVolatileBufferStress` | `VolatileLamportBuffer` | TOCTOU lost update when an SPSC buffer gets two producers |
| `SingleThreadLamportBufferMultiOpStress` | `NonVolatileLamportBuffer` | duplicate reads, lost writes, FIFO violations from missing `volatile` |
| `NonVolatileSpscLamportBufferJcstressTest` | `NonVolatileLamportBuffer` | the headline visibility bug — fails by design, so it runs in its own tolerated execution (see *How the module is built*) |
| `LockBasedLamportBufferOfferPollStress` | `LockBasedLamportBuffer` | control sample — should be clean |
| `FastPathLamportBufferStress` | `FastPathLamportBuffer` | the unlocked fast path under real contention |
| `SpuriousWakeupLamportBufferStress` | `ConditionalLamportBuffer` | `if`-instead-of-`while` around `Condition.await()` |
| `TwoConsumersFastPathBufferStress` | `FastPathLamportBuffer` | the same fast-path race Fray finds — two consumers, one element |

Eight tests, all of them run.

## What a full run actually reports

From a complete `mvn -pl 06-jcstress -am verify` on this repository (macOS,
x86_64, JDK 25):

| Test | Verdicts observed |
|---|---|
| `TwoProducersVolatileBufferStress` | **Interesting** — lost update in 8,627,481 of 582,627,974 samples (1.48%), plus 3,015,531 spurious "full" rejections (0.51%) |
| `SingleThreadLamportBufferMultiOpStress` | **Interesting** — `-1, 2`: first write invisible, second visible |
| `VolatileSpscLamportBufferJcstressTest` | Acceptable only |
| `LockBasedLamportBufferOfferPollStress` | Acceptable only |
| `FastPathLamportBufferStress` | Acceptable only — one consumer, so the race cannot occur |
| `SpuriousWakeupLamportBufferStress` | Acceptable only |
| `TwoConsumersFastPathBufferStress` | **Interesting** — NPE in 6,362,471 of 579,207,814 samples (1.10%) |

Compare the two fast-path rows. `FastPathLamportBufferStress` runs a single
consumer, so the race it is named after is unreachable — jcstress reported
"acceptable" for a scenario that cannot fail. `TwoConsumersFastPathBufferStress`
models the actual race and jcstress hits it at **1.10%**.

So jcstress is not blind to this bug. But [Fray](../05-fray) found it on its
**4th** iteration from an ordinary two-threaded test with an assertion, and
handed back a replayable recording; jcstress needed a test shaped around the bug
before it could see anything. The spurious wakeup has not been observed by any
jcstress test here — hedged deliberately, since no test well-shaped for it has
been written.

Systematic exploration is not a slower version of stress testing. It asks less of
you. Run both.

## The headline result

`NonVolatileSpscLamportBufferJcstressTest` runs on every `verify`:

```
   RESULT      SAMPLES     FREQ       EXPECT  DESCRIPTION
  1, 0, 0       35.222   <0,01%    Forbidden  Producer offered, consumer never saw it
  1, 0, 1  308.860.121   47,12%   Acceptable  Consumer polls before producer offers
  1, 1, 0  346.641.431   52,88%   Acceptable  Producer offers, consumer polls it
```

~35,000 executions out of 655 million in which the producer's write never
reached the consumer. That is "works on my machine" at 0.01% frequency — and it
is exactly the bug Fray structurally cannot see.

The archived HTML report for that run is checked in at
[`../results/jcstress/results/`](../results/jcstress/results) as
`pl.wsztajerowski.demo.lamport.jcstress.NonVolatileSpscLamportBufferJcstressTest.html`
— status `FAILED`, with the forbidden `1, 0, 0` state observed under several
compilation modes (Interpreter, C1, C2, and C2 with `-XX:+StressLCM`/`StressGCM`).

> That filename has no `edgecase` segment: the run predates the split of these
> tests into `passing`/`edgecase` packages. It is kept under its original name
> because it is the exact run the 35,222-sample figure and the slide screenshot
> come from — a later run gives different counts. Every other report in that
> directory carries the current package. (`05-fray` keeps a `FastTrack…`
> recording for the same reason.)

## How the module is built

* Tests live in **`src/main/java`**, not `src/test/java` — jcstress compiles them
  with its own annotation processor (`JCStressTestProcessor`, wired in via
  `annotationProcessorPaths`), which generates the runner infrastructure.
* `maven-shade-plugin` packages everything into a self-contained
  `target/jcstress.jar` with `org.openjdk.jcstress.Main` as its entry point.
* `exec-maven-plugin` runs that jar in the **`integration-test`** phase, with the
  `--add-opens` flags jcstress needs on modern JDKs — in **two** executions, split by package:
  * `run-jcstress` — everything *not* under `jcstress.edgecase.` (`-t '^(?!.*\.edgecase\.).*'`).
    Strict: a non-zero exit fails the build.
  * `run-jcstress-expected-failures` — `jcstress.edgecase.` only (`-t '\.jcstress\.edgecase\.'`).
    jcstress exits 1 when it observes a FORBIDDEN state, which for these tests *is* the expected
    result, so `successCodes` tolerates it — here and nowhere else.

  So the source tree is the configuration:

  ```
  jcstress/passing/    must pass — a failure here breaks the build
  jcstress/edgecase/   intentionally broken — exit 1 is expected
  ```

  **Add a test by choosing its package. The pom never needs editing.** A test left in neither
  package is picked up by the strict run, so it fails loudly instead of silently not running.

Because the run is bound to `integration-test`, `verify` — not `test` — is the
lifecycle goal that executes the stress tests.

## Run

```bash
# Build the fat jar and run the full suite (this is the long one — expect minutes)
mvn -f demos/pom.xml -pl 06-jcstress -am verify

# Build the fat jar only, no stress run
mvn -f demos/pom.xml -pl 06-jcstress -am package
```

Then drive the jar directly for anything finer-grained:

```bash
# List the available tests
java -jar demos/06-jcstress/target/jcstress.jar -l

# Run one test (substring match on the class name)
java -jar demos/06-jcstress/target/jcstress.jar -t TwoProducers

# Fast feedback while iterating: sanity or quick mode
java -jar demos/06-jcstress/target/jcstress.jar -m sanity
java -jar demos/06-jcstress/target/jcstress.jar -t FastPath -m quick

# Crank it up when hunting a rare outcome
java -jar demos/06-jcstress/target/jcstress.jar -t NonVolatile -m stress

# Limit CPUs (also limits memory footprint) and choose a report directory
java -jar demos/06-jcstress/target/jcstress.jar -c 4 -r target/jcstress-results

# Print all results, including the boring ones
java -jar demos/06-jcstress/target/jcstress.jar -v
```

Useful flags: `-m <sanity|quick|default|tough|stress>` (test mode preset),
`-t <substring>` (filter), `-c <N>` (CPU count), `-iters <N>`, `-f <count>`
(forks), `-r <dir>` (report directory), `-l` (list), `-v` (verbose).

## Reports

An HTML report is written to `target/jcstress-results/index.html` (open
`index.html` for the summary, one page per test class). A curated copy of a
previous run is checked in at
[`../results/jcstress/results`](../results/jcstress/results).

## Gotcha: `mvn install` runs the whole suite

`-DskipTests` skips Surefire, **not** `exec-maven-plugin`. A plain
`mvn -f demos/pom.xml install -DskipTests` from the reactor root still runs the
entire jcstress suite and takes many minutes. To skip it:

```bash
mvn -f demos/pom.xml clean install -DskipTests -Dexec.skip=true
```
